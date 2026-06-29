package com.nexora.player.data.update

import android.content.Context
import com.nexora.player.BuildConfig
import com.nexora.player.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class NexoraUpdateClient(
    private val context: Context,
    private val baseUrl: String = BuildConfig.NEXORA_SERVER_URL.trimEnd('/')
) {
    suspend fun checkVersion(
        currentVersionCode: Int,
        currentVersionName: String = BuildConfig.VERSION_NAME
    ): RemoteUpdateInfo = withContext(Dispatchers.IO) {
        val encodedVersionName = URLEncoder.encode(currentVersionName, Charsets.UTF_8.name())
        val encodedPackageName = URLEncoder.encode(BuildConfig.APPLICATION_ID, Charsets.UTF_8.name())
        val url = "$baseUrl/api/app/version?versionCode=$currentVersionCode&versionName=$encodedVersionName&packageName=$encodedPackageName"
        parseVersionPayload(getJson(url), currentVersionCode)
    }

    suspend fun fetchNotifications(currentVersionCode: Int): List<RemoteAppNotification> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/app/notifications?versionCode=$currentVersionCode"
        val root = getJson(url)
        root.optJSONArray("notifications").toRemoteNotifications()
    }

    fun shareUrl(): String = baseUrl

    fun downloadUrl(): String = "$baseUrl/api/download/latest"

    private fun parseVersionPayload(
        root: JSONObject,
        installedVersionCode: Int
    ): RemoteUpdateInfo {
        val latest = root.optJSONObject("latestVersion") ?: JSONObject()
        val update = root.optJSONObject("update") ?: JSONObject()
        val urls = root.optJSONObject("urls") ?: JSONObject()
        val packageName = root.optString("packageName", BuildConfig.APPLICATION_ID).ifBlank { BuildConfig.APPLICATION_ID }

        val remoteVersionName = latest.optString("versionName", BuildConfig.VERSION_NAME).ifBlank { BuildConfig.VERSION_NAME }
        val remoteVersionCode = latest.takeIntOrNull("versionCode") ?: BuildConfig.VERSION_CODE
        val serverAvailable = update.takeBooleanOrNull("available") ?: (remoteVersionCode > installedVersionCode)
        val serverRequired = update.takeBooleanOrNull("required") ?: false
        val explicitForceUpdate =
            update.takeBooleanOrNull("forceUpdate")
                ?: root.takeBooleanOrNull("forceUpdate")
                ?: latest.takeBooleanOrNull("forceUpdate")
        val minSupportedVersionCode =
            update.takeIntOrNull("minSupportedVersionCode")
                ?: root.takeIntOrNull("minSupportedVersionCode")
                ?: latest.takeIntOrNull("minSupportedVersionCode")
                ?: 0

        /**
         * Local authority:
         * - Android updates must be governed by versionCode, not only versionName.
         * - Never show an update dialog when the server points to the same/lower build.
         * - Ignore package mismatches so a wrong server config cannot lock the app.
         */
        val samePackage = packageName == BuildConfig.APPLICATION_ID
        val locallyAvailable = samePackage && remoteVersionCode > installedVersionCode
        val locallyRequired = locallyAvailable && when {
            explicitForceUpdate != null || minSupportedVersionCode > 0 -> {
                explicitForceUpdate == true || installedVersionCode < minSupportedVersionCode
            }
            else -> serverRequired
        }

        return RemoteUpdateInfo(
            packageName = packageName,
            currentVersionCode = installedVersionCode,
            latestVersion = RemoteVersionInfo(
                versionName = remoteVersionName,
                versionCode = remoteVersionCode,
                releaseDate = latest.optString("releaseDate", ""),
                apkSizeBytes = latest.takeLongOrNull("apkSizeBytes"),
                apkSha256 = latest.optString("apkSha256").takeIf { it.isNotBlank() },
                changelog = latest.optJSONArray("changelog").toReleaseNotes()
            ),
            available = locallyAvailable,
            required = locallyRequired,
            urls = RemoteUpdateUrls(
                download = urls.optString("download", downloadUrl()).ifBlank { downloadUrl() },
                landing = urls.optString("landing", shareUrl()).ifBlank { shareUrl() },
                share = urls.optString("share", shareUrl()).ifBlank { shareUrl() }
            ),
            notifications = root.optJSONArray("notifications").toRemoteNotifications(),
            forceUpdate = explicitForceUpdate == true,
            minSupportedVersionCode = minSupportedVersionCode,
            serverAvailable = serverAvailable,
            serverRequired = serverRequired
        )
    }

    private fun getJson(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty("User-Agent", "NexoraPlayer/${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = BufferedInputStream(stream).use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
            if (code !in 200..299) {
                throw IllegalStateException(context.getString(R.string.update_server_error, code, text))
            }
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}

private fun JSONObject.takeLongOrNull(key: String): Long? = when {
    !has(key) || isNull(key) -> null
    else -> when (val value = opt(key)) {
        is Number -> value.toLong().takeIf { it > 0L }
        is String -> value.trim().toLongOrNull()?.takeIf { it > 0L }
        else -> null
    }
}

private fun JSONObject.takeIntOrNull(key: String): Int? = when {
    !has(key) || isNull(key) -> null
    else -> when (val value = opt(key)) {
        is Number -> value.toInt()
        is String -> value.trim().toIntOrNull()
        else -> null
    }
}

private fun JSONObject.takeBooleanOrNull(key: String): Boolean? = when {
    !has(key) || isNull(key) -> null
    else -> when (val value = opt(key)) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when (value.trim().lowercase()) {
            "true", "1", "yes", "on", "active", "enabled", "activo", "activado", "forzada", "obligatoria" -> true
            "false", "0", "no", "off", "inactive", "disabled", "inactivo", "desactivado", "opcional" -> false
            else -> null
        }
        else -> null
    }
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
                        enabled = obj.takeBooleanOrNull("enabled") ?: true,
                        createdAt = obj.optString("createdAt", "")
                    )
                )
            }
        }
    }
}
