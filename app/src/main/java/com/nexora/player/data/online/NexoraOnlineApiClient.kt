package com.nexora.player.data.online

import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.util.Base64
import com.nexora.player.BuildConfig
import com.nexora.player.R
import com.nexora.player.data.model.MediaEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class NexoraOnlineApiClient(private val context: Context) {
    val apiBaseUrl: String = BuildConfig.NEXORA_ONLINE_API_BASE_URL.trimEnd('/').ifBlank {
        "https://nexoraplayerapi.vercel.app"
    }
    private val apiPrefix: String = if (apiBaseUrl.endsWith("/api/v1")) apiBaseUrl else "$apiBaseUrl/api/v1"
    private val supabaseUrl: String = BuildConfig.NEXORA_SUPABASE_URL.trimEnd('/')
    private val supabaseAnonKey: String = BuildConfig.NEXORA_SUPABASE_ANON_KEY
    private val googleRedirectUrl: String = BuildConfig.NEXORA_SUPABASE_GOOGLE_REDIRECT_URL.ifBlank { "nexoraplayer://auth/callback" }
    private val appClientId: String = BuildConfig.NEXORA_API_APP_ID.ifBlank { "music-mobile-app" }
    private val appSecret: String = BuildConfig.NEXORA_API_APP_SHARED_SECRET
    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty().ifBlank { "anonymous-device" }
    }

    fun googleOAuthUrl(): String {
        ensureSupabaseConfigured()
        val redirect = URLEncoder.encode(googleRedirectUrl, "UTF-8")
        val scopes = URLEncoder.encode("email profile", "UTF-8")
        return "$supabaseUrl/auth/v1/authorize?provider=google&redirect_to=$redirect&scopes=$scopes"
    }

    fun parseGoogleOAuthCallback(uri: Uri?): OnlineUserSession? {
        if (uri == null) return null
        val isExpectedCallback = uri.scheme == "nexoraplayer" && uri.host == "auth" && uri.path.orEmpty().startsWith("/callback")
        if (!isExpectedCallback) return null

        val params = parseUriCallbackParams(uri)
        val callbackError = callbackAuthErrorMessage(params)
        if (!callbackError.isNullOrBlank()) throw OnlineApiException(callbackError)

        val accessToken = params["access_token"].orEmpty()
        if (accessToken.isBlank()) throw OnlineApiException(context.getString(R.string.online_error_google_callback))

        val expiresIn = params["expires_in"]?.toLongOrNull()?.coerceAtLeast(60L) ?: 3600L
        val claims = decodeJwtPayload(accessToken)
        return OnlineUserSession(
            accessToken = accessToken,
            refreshToken = params["refresh_token"]?.takeIf { it.isNotBlank() },
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + expiresIn,
            email = claims?.optString("email")?.takeIf { it.isNotBlank() },
            userId = claims?.optString("sub")?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun login(email: String, password: String): OnlineUserSession = withContext(Dispatchers.IO) {
        ensureSupabaseConfigured()
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .toString()
            .toByteArray()
        val json = requestJson(
            url = "$supabaseUrl/auth/v1/token?grant_type=password",
            method = "POST",
            body = body,
            extraHeaders = supabaseHeaders(json = true),
            requiresAuthEnvelope = false
        )
        json.toSession()
    }

    suspend fun register(email: String, password: String, username: String): OnlineUserSession = withContext(Dispatchers.IO) {
        ensureSupabaseConfigured()
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .put("data", JSONObject().put("username", username.trim()))
            .toString()
            .toByteArray()
        val redirect = URLEncoder.encode(googleRedirectUrl, "UTF-8")
        val json = requestJson(
            url = "$supabaseUrl/auth/v1/signup?redirect_to=$redirect",
            method = "POST",
            body = body,
            extraHeaders = supabaseHeaders(json = true),
            requiresAuthEnvelope = false
        )
        if (json.optString("access_token").isBlank()) {
            throw OnlineApiException(context.getString(R.string.online_error_confirm_email))
        }
        json.toSession()
    }

    suspend fun refreshSession(session: OnlineUserSession): OnlineUserSession = withContext(Dispatchers.IO) {
        ensureSupabaseConfigured()
        val refreshToken = session.refreshToken ?: throw OnlineApiException(context.getString(R.string.online_error_missing_refresh_token))
        val body = JSONObject().put("refresh_token", refreshToken).toString().toByteArray()
        val json = requestJson(
            url = "$supabaseUrl/auth/v1/token?grant_type=refresh_token",
            method = "POST",
            body = body,
            extraHeaders = supabaseHeaders(json = true),
            requiresAuthEnvelope = false
        )
        json.toSession(fallbackEmail = session.email, fallbackUserId = session.userId)
    }

    suspend fun validateSession(session: OnlineUserSession): OnlineUserSession = withContext(Dispatchers.IO) {
        val active = if (session.isExpired) refreshSession(session) else session
        try {
            requestJson(
                url = "$apiPrefix/auth/me",
                method = "GET",
                bearerToken = active.accessToken,
                appSignature = false
            )
            active
        } catch (throwable: Throwable) {
            if (active.refreshToken != null) refreshSession(active) else throw throwable
        }
    }

    suspend fun getSongs(session: OnlineUserSession, limit: Int = 30, offset: Int = 0): OnlineSongsResponse = withContext(Dispatchers.IO) {
        val page = (offset / limit.coerceAtLeast(1)) + 1
        val json = requestJson(
            url = "$apiPrefix/songs?limit=${limit.coerceIn(1, 100)}&page=$page",
            method = "GET",
            bearerToken = session.accessToken,
            appSignature = false
        )
        parseSongsResponse(json, limit, offset)
    }

    suspend fun searchSongs(session: OnlineUserSession, query: String, limit: Int = 30, offset: Int = 0): OnlineSongsResponse = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val page = (offset / limit.coerceAtLeast(1)) + 1
        val catalogUrl = "$apiPrefix/catalog/search?q=$encoded&limit=${limit.coerceIn(1, 50)}&page=$page"
        val json = runCatching {
            requestJson(
                url = catalogUrl,
                method = "GET",
                bearerToken = session.accessToken,
                appSignature = appSecret.isNotBlank()
            )
        }.getOrElse {
            requestJson(
                url = "$apiPrefix/songs?q=$encoded&limit=${limit.coerceIn(1, 100)}&page=$page",
                method = "GET",
                bearerToken = session.accessToken,
                appSignature = false
            )
        }
        parseSongsResponse(json, limit, offset)
    }

    suspend fun getSongDetail(session: OnlineUserSession, id: String): OnlineSongDto = withContext(Dispatchers.IO) {
        val json = requestJson(
            url = "$apiPrefix/songs/$id",
            method = "GET",
            bearerToken = session.accessToken,
            appSignature = false
        )
        val data = json.optJSONObject("data") ?: json
        parseSong(data)
    }

    suspend fun getLyrics(session: OnlineUserSession, id: String): OnlineLyricsDto = withContext(Dispatchers.IO) {
        val song = getSongDetail(session, id)
        OnlineLyricsDto(songId = id, lyrics = song.lyrics, synchronized = false, source = song.source)
    }

    suspend fun uploadSong(session: OnlineUserSession, entry: MediaEntry): OnlineSongDto = withContext(Dispatchers.IO) {
        val audioFile = copyContentUriToTempFile(entry)
        val boundary = "----NexoraBoundary${System.currentTimeMillis()}"
        val multipartFile = createMultipartUploadFile(boundary, audioFile, entry)
        try {
            val json = requestMultipartJson(
                url = "$apiPrefix/songs/upload",
                method = "POST",
                multipartFile = multipartFile,
                boundary = boundary,
                bearerToken = session.accessToken,
                appSignature = appSecret.isNotBlank()
            )
            val data = json.optJSONObject("data") ?: json
            parseSong(data)
        } finally {
            runCatching { audioFile.delete() }
            runCatching { multipartFile.delete() }
        }
    }

    fun streamingHeaders(session: OnlineUserSession): Map<String, String> {
        return buildMap {
            put("Authorization", "Bearer ${session.accessToken}")
            put("x-client-id", "nexora-player-android")
            put("x-client-version", BuildConfig.VERSION_NAME)
            put("x-app-platform", "android")
            put("x-app-id", appClientId)
            put("x-device-id", deviceId)
        }
    }

    private fun ensureSupabaseConfigured() {
        if (supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) {
            throw OnlineApiException(context.getString(R.string.online_error_missing_supabase_config))
        }
    }

    private fun supabaseHeaders(json: Boolean): Map<String, String> = buildMap {
        put("apikey", supabaseAnonKey)
        put("Authorization", "Bearer $supabaseAnonKey")
        if (json) put("Content-Type", "application/json")
    }

    private fun requestJson(
        url: String,
        method: String,
        body: ByteArray? = null,
        contentType: String = "application/json",
        bearerToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        appSignature: Boolean = appSecret.isNotBlank(),
        requiresAuthEnvelope: Boolean = true
    ): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("x-client-id", "nexora-player-android")
            setRequestProperty("x-client-version", BuildConfig.VERSION_NAME)
            setRequestProperty("x-app-platform", "android")
            setRequestProperty("x-app-id", appClientId)
            setRequestProperty("x-device-id", deviceId)
            bearerToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            extraHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType)
                setRequestProperty("Content-Length", body.size.toString())
            }
        }

        if (appSignature) {
            val uri = URL(url)
            val pathAndQuery = uri.file
            val bodyHash = sha256Hex(body ?: ByteArray(0))
            val timestamp = System.currentTimeMillis().toString()
            val canonical = "${method.uppercase()}\n$pathAndQuery\n$timestamp\n$deviceId\n$bodyHash"
            connection.setRequestProperty("x-app-timestamp", timestamp)
            connection.setRequestProperty("x-body-sha256", bodyHash)
            connection.setRequestProperty("x-app-signature", hmacSha256Hex(appSecret, canonical))
        }

        body?.let { bytes -> connection.outputStream.use { it.write(bytes) } }

        val code = connection.responseCode
        val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()
        val json = runCatching { JSONObject(response) }.getOrElse { JSONObject().put("message", response) }
        if (code !in 200..299) {
            throw OnlineApiException(authErrorMessage(json, code))
        }
        if (requiresAuthEnvelope && json.has("success") && !json.optBoolean("success", false)) {
            val message = json.optJSONObject("error")?.optString("message") ?: context.getString(R.string.online_error_server_generic)
            throw OnlineApiException(message)
        }
        return json
    }


    private fun authErrorMessage(json: JSONObject, httpCode: Int): String {
        val error = json.optJSONObject("error")
        val errorCode = error?.optString("code")?.takeIf { it.isNotBlank() }
            ?: error?.optString("error_code")?.takeIf { it.isNotBlank() }
            ?: json.optString("error_code").takeIf { it.isNotBlank() }
            ?: json.optString("code").takeIf { it.isNotBlank() }
        val message = error?.optString("message")?.takeIf { it.isNotBlank() }
            ?: error?.optString("msg")?.takeIf { it.isNotBlank() }
            ?: json.optString("msg").takeIf { it.isNotBlank() }
            ?: json.optString("message").takeIf { it.isNotBlank() }
            ?: json.optString("error_description").takeIf { it.isNotBlank() }
            ?: json.optString("error").takeIf { it.isNotBlank() }
        return friendlyAuthError(errorCode, message, httpCode)
    }

    private fun callbackAuthErrorMessage(params: Map<String, String>): String? {
        val errorCode = params["error_code"] ?: params["code"]
        val message = params["error_description"] ?: params["error"] ?: params["msg"]
        if (errorCode.isNullOrBlank() && message.isNullOrBlank()) return null
        return friendlyAuthError(errorCode, message, null)
    }

    private fun friendlyAuthError(errorCode: String?, message: String?, httpCode: Int?): String {
        val normalizedCode = errorCode.orEmpty().trim().lowercase()
        val normalizedMessage = message.orEmpty().trim()
        val lowerMessage = normalizedMessage.lowercase()
        return when {
            normalizedCode == "signup_disabled" || lowerMessage.contains("signups not allowed") ->
                "Supabase Auth tiene desactivada la creación de cuentas. Activa Signups en Supabase Authentication > Settings para permitir registro manual y Google Auth."
            normalizedCode == "email_provider_disabled" ->
                "El registro con correo y contraseña está desactivado en Supabase Auth. Activa Email provider para crear cuentas manualmente."
            normalizedCode == "provider_disabled" || normalizedCode == "oauth_provider_not_supported" ->
                "Google Auth está desactivado o mal configurado en Supabase. Activa el proveedor Google y revisa Client ID, Client Secret y Redirect URLs."
            normalizedCode == "email_exists" || normalizedCode == "user_already_exists" ->
                "Ese correo ya está registrado. Inicia sesión o usa recuperación de contraseña."
            normalizedCode == "weak_password" ->
                "La contraseña no cumple la seguridad mínima configurada en Supabase. Usa una contraseña más larga y segura."
            normalizedCode == "validation_failed" && normalizedMessage.isNotBlank() -> normalizedMessage
            normalizedMessage.isNotBlank() -> normalizedMessage
            httpCode != null -> context.getString(R.string.online_error_http, httpCode)
            else -> context.getString(R.string.online_error_server_generic)
        }
    }

    private fun requestMultipartJson(
        url: String,
        method: String,
        multipartFile: File,
        boundary: String,
        bearerToken: String? = null,
        appSignature: Boolean = appSecret.isNotBlank()
    ): JSONObject {
        val bodyHash = if (appSignature) sha256FileHex(multipartFile) else ""
        val timestamp = System.currentTimeMillis().toString()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 120_000
            useCaches = false
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Content-Length", multipartFile.length().toString())
            setRequestProperty("x-client-id", "nexora-player-android")
            setRequestProperty("x-client-version", BuildConfig.VERSION_NAME)
            setRequestProperty("x-app-platform", "android")
            setRequestProperty("x-app-id", appClientId)
            setRequestProperty("x-device-id", deviceId)
            bearerToken?.let { setRequestProperty("Authorization", "Bearer $it") }
        }

        if (appSignature) {
            val uri = URL(url)
            val pathAndQuery = uri.file
            val canonical = "${method.uppercase()}\n$pathAndQuery\n$timestamp\n$deviceId\n$bodyHash"
            connection.setRequestProperty("x-app-timestamp", timestamp)
            connection.setRequestProperty("x-body-sha256", bodyHash)
            connection.setRequestProperty("x-app-signature", hmacSha256Hex(appSecret, canonical))
        }

        multipartFile.inputStream().use { input ->
            connection.outputStream.use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
        }

        val code = connection.responseCode
        val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()
        val json = runCatching { JSONObject(response) }.getOrElse { JSONObject().put("message", response) }
        if (code !in 200..299) {
            throw OnlineApiException(authErrorMessage(json, code))
        }
        if (json.has("success") && !json.optBoolean("success", false)) {
            val message = json.optJSONObject("error")?.optString("message") ?: context.getString(R.string.online_error_server_generic)
            throw OnlineApiException(message)
        }
        return json
    }

    private fun parseSongsResponse(json: JSONObject, requestedLimit: Int, requestedOffset: Int): OnlineSongsResponse {
        val data = json.opt("data")
        val itemsJson: JSONArray = when (data) {
            is JSONArray -> data
            is JSONObject -> data.optJSONArray("results") ?: data.optJSONArray("items") ?: data.optJSONArray("songs") ?: JSONArray()
            else -> json.optJSONArray("items") ?: json.optJSONArray("results") ?: JSONArray()
        }
        val items = buildList {
            for (index in 0 until itemsJson.length()) {
                itemsJson.optJSONObject(index)?.let { add(parseSong(it)) }
            }
        }
        val metaPagination = json.optJSONObject("meta")?.optJSONObject("pagination")
            ?: (data as? JSONObject)?.optJSONObject("meta")?.optJSONObject("pagination")
        val total = metaPagination?.optInt("total", items.size)
            ?: (data as? JSONObject)?.optInt("total", items.size)
            ?: json.optInt("total", items.size)
        val limit = metaPagination?.optInt("limit", requestedLimit) ?: requestedLimit
        val page = metaPagination?.optInt("page", requestedOffset / requestedLimit.coerceAtLeast(1) + 1) ?: 1
        val offset = ((page - 1).coerceAtLeast(0)) * limit
        return OnlineSongsResponse(items = items, total = total, limit = limit, offset = offset)
    }

    private fun parseSong(json: JSONObject): OnlineSongDto {
        val id = json.optString("id")
        val streamUrl = json.optString("stream_url").takeIf { it.isNotBlank() }
        val audioUrl = json.optString("audioUrl").takeIf { it.isNotBlank() }
            ?: json.optString("audio_url").takeIf { it.isNotBlank() }
            ?: streamUrl
        return OnlineSongDto(
            id = id,
            title = json.optString("title", "Nexora Online"),
            artist = json.optString("artist").takeIf { it.isNotBlank() },
            album = json.optString("album").takeIf { it.isNotBlank() },
            genre = json.optString("genre").takeIf { it.isNotBlank() },
            durationSeconds = when {
                json.has("durationSeconds") -> json.optLong("durationSeconds")
                json.has("duration_seconds") -> json.optLong("duration_seconds")
                else -> null
            },
            audioUrl = audioUrl,
            coverUrl = json.optString("coverUrl").takeIf { it.isNotBlank() }
                ?: json.optString("cover_url").takeIf { it.isNotBlank() },
            lyricsUrl = json.optString("lyricsUrl").takeIf { it.isNotBlank() }
                ?: json.optString("lyrics_url").takeIf { it.isNotBlank() },
            lyrics = json.optString("lyrics").takeIf { it.isNotBlank() },
            source = json.optString("source").takeIf { it.isNotBlank() }
                ?: json.optString("source_type").takeIf { it.isNotBlank() },
            canDownload = json.optBoolean("canDownload", json.optBoolean("can_download", false)),
            createdAt = json.optString("createdAt").takeIf { it.isNotBlank() }
                ?: json.optString("created_at").takeIf { it.isNotBlank() }
        )
    }

    private fun JSONObject.toSession(fallbackEmail: String? = null, fallbackUserId: String? = null): OnlineUserSession {
        val expiresIn = optLong("expires_in", 3600L).coerceAtLeast(60L)
        val user = optJSONObject("user")
        return OnlineUserSession(
            accessToken = optString("access_token"),
            refreshToken = optString("refresh_token").takeIf { it.isNotBlank() },
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + expiresIn,
            email = user?.optString("email")?.takeIf { it.isNotBlank() } ?: fallbackEmail,
            userId = user?.optString("id")?.takeIf { it.isNotBlank() } ?: fallbackUserId
        )
    }

    private fun parseUriCallbackParams(uri: Uri): Map<String, String> {
        val pairs = mutableMapOf<String, String>()
        uri.queryParameterNames.forEach { key ->
            uri.getQueryParameter(key)?.let { pairs[key] = it }
        }
        uri.encodedFragment.orEmpty()
            .split('&')
            .filter { it.contains('=') }
            .forEach { pair ->
                val key = pair.substringBefore('=')
                val value = pair.substringAfter('=')
                pairs[URLDecoder.decode(key, "UTF-8")] = URLDecoder.decode(value, "UTF-8")
            }
        return pairs
    }

    private fun decodeJwtPayload(jwt: String): JSONObject? = runCatching {
        val payload = jwt.split('.').getOrNull(1) ?: return@runCatching null
        val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        JSONObject(String(decoded))
    }.getOrNull()

    private fun copyContentUriToTempFile(entry: MediaEntry): File {
        val suffix = when {
            entry.mimeType?.contains("mpeg", ignoreCase = true) == true -> ".mp3"
            entry.mimeType?.contains("mp4", ignoreCase = true) == true -> ".m4a"
            entry.mimeType?.contains("flac", ignoreCase = true) == true -> ".flac"
            entry.mimeType?.contains("wav", ignoreCase = true) == true -> ".wav"
            entry.mimeType?.contains("ogg", ignoreCase = true) == true -> ".ogg"
            else -> ".audio"
        }
        val file = File.createTempFile("nexora-upload-", suffix, context.cacheDir)
        context.contentResolver.openInputStream(entry.uri)?.use { input ->
            BufferedInputStream(input).use { buffered ->
                FileOutputStream(file).use { output -> buffered.copyTo(output) }
            }
        } ?: throw OnlineApiException(context.getString(R.string.online_error_read_local_file, entry.title))
        return file
    }

    private fun createMultipartUploadFile(boundary: String, audioFile: File, entry: MediaEntry): File {
        val multipart = File.createTempFile("nexora-upload-body-", ".multipart", context.cacheDir)
        multipart.outputStream().use { out ->
            fun write(value: String) = out.write(value.toByteArray())
            fun field(name: String, value: String) {
                write("--$boundary\r\n")
                write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                write(value)
                write("\r\n")
            }

            field("title", entry.title)
            field("artist", entry.artist.ifBlank { context.getString(R.string.online_unknown_artist) })
            if (entry.album.isNotBlank()) field("album", entry.album)
            if (entry.durationMs > 0L) field("duration", (entry.durationMs / 1000L).toString())
            if (!entry.mimeType.isNullOrBlank()) field("mime_type", entry.mimeType)

            write("--$boundary\r\n")
            write("Content-Disposition: form-data; name=\"file\"; filename=\"${entry.uploadFilename()}\"\r\n")
            write("Content-Type: ${entry.mimeType ?: "audio/mpeg"}\r\n\r\n")
            audioFile.inputStream().use { input -> input.copyTo(out, DEFAULT_BUFFER_SIZE) }
            write("\r\n--$boundary--\r\n")
        }
        return multipart
    }

    private fun MediaEntry.uploadFilename(): String {
        val base = title.safeFilename().ifBlank { "audio" }
        val pathExtension = uri.lastPathSegment
            ?.substringAfterLast('.', missingDelimiterValue = "")
            .orEmpty()
            .takeIf { it.length in 2..5 }
        val extension = when {
            pathExtension != null -> ".$pathExtension"
            mimeType?.contains("mpeg", ignoreCase = true) == true -> ".mp3"
            mimeType?.contains("mp4", ignoreCase = true) == true -> ".m4a"
            mimeType?.contains("flac", ignoreCase = true) == true -> ".flac"
            mimeType?.contains("wav", ignoreCase = true) == true -> ".wav"
            mimeType?.contains("ogg", ignoreCase = true) == true -> ".ogg"
            else -> ".mp3"
        }
        return if (base.contains('.')) base else base + extension
    }

    private fun String.safeFilename(): String = replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "audio" }

    private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun sha256FileHex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun hmacSha256Hex(secret: String, value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(value.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

class OnlineApiException(message: String) : Exception(message)
