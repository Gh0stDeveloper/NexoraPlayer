package com.nexora.player.data.update

import com.nexora.player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class NexoraUpdateClient(
    private val baseUrl: String = BuildConfig.NEXORA_SERVER_URL.trimEnd('/')
) {
    suspend fun checkVersion(currentVersionCode: Int): RemoteUpdateInfo = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/app/version?versionCode=$currentVersionCode"
        parseVersionPayload(getJson(url))
    }

    suspend fun fetchNotifications(currentVersionCode: Int): List<RemoteAppNotification> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/app/notifications?versionCode=$currentVersionCode"
        val root = getJson(url)
        root.optJSONArray("notifications").toRemoteNotifications()
    }

    fun shareUrl(): String = baseUrl

    fun downloadUrl(): String = "$baseUrl/api/download/latest"

    private fun parseVersionPayload(root: JSONObject): RemoteUpdateInfo {
        val latest = root.optJSONObject("latestVersion") ?: JSONObject()
        val update = root.optJSONObject("update") ?: JSONObject()
        val urls = root.optJSONObject("urls") ?: JSONObject()
        return RemoteUpdateInfo(
            packageName = root.optString("packageName", BuildConfig.APPLICATION_ID),
            currentVersionCode = root.optInt("currentVersionCode", BuildConfig.VERSION_CODE),
            latestVersion = RemoteVersionInfo(
                versionName = latest.optString("versionName", BuildConfig.VERSION_NAME),
                versionCode = latest.optInt("versionCode", BuildConfig.VERSION_CODE),
                releaseDate = latest.optString("releaseDate", ""),
                apkSizeBytes = latest.takeLongOrNull("apkSizeBytes"),
                apkSha256 = latest.optString("apkSha256").takeIf { it.isNotBlank() },
                changelog = latest.optJSONArray("changelog").toReleaseNotes()
            ),
            available = update.optBoolean("available", false),
            required = update.optBoolean("required", false),
            urls = RemoteUpdateUrls(
                download = urls.optString("download", downloadUrl()).ifBlank { downloadUrl() },
                landing = urls.optString("landing", shareUrl()).ifBlank { shareUrl() },
                share = urls.optString("share", shareUrl()).ifBlank { shareUrl() }
            ),
            notifications = root.optJSONArray("notifications").toRemoteNotifications()
        )
    }

    private fun getJson(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "NexoraPlayer/${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = BufferedInputStream(stream).use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
            if (code !in 200..299) {
                throw IllegalStateException("Servidor de actualizaciones respondió $code: $text")
            }
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}

private fun JSONObject.takeLongOrNull(key: String): Long? = when {
    !has(key) || isNull(key) -> null
    else -> optLong(key).takeIf { it > 0L }
}

private fun JSONArray?.toReleaseNotes(): List<RemoteReleaseNote> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val obj = optJSONObject(index) ?: continue
            add(
                RemoteReleaseNote(
                    id = obj.optString("id", "note-$index"),
                    title = obj.optString("title", "Mejora"),
                    description = obj.optString("description", "")
                )
            )
        }
    }
}

private fun JSONArray?.toRemoteNotifications(): List<RemoteAppNotification> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val obj = optJSONObject(index) ?: continue
            val id = obj.optString("id", "notice-$index")
            val title = obj.optString("title", "Nexora Player")
            val message = obj.optString("message", "")
            if (message.isNotBlank()) {
                add(
                    RemoteAppNotification(
                        id = id,
                        title = title,
                        message = message,
                        enabled = obj.optBoolean("enabled", true),
                        createdAt = obj.optString("createdAt", "")
                    )
                )
            }
        }
    }
}
