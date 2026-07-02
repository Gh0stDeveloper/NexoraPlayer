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
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class NexoraOnlineApiClient(private val context: Context) {
    private val configuredBaseUrl: String = BuildConfig.NEXORA_ONLINE_API_BASE_URL.trimEnd('/').ifBlank {
        "https://nexoraplayerapi.vercel.app/api/v1"
    }
    val apiBaseUrl: String = configuredBaseUrl.removeSuffix("/api/v1")
    private val apiPrefix: String = if (configuredBaseUrl.endsWith("/api/v1")) configuredBaseUrl else "$configuredBaseUrl/api/v1"
    private val supabaseUrl: String = BuildConfig.NEXORA_SUPABASE_URL.trimEnd('/')
    private val supabaseAnonKey: String = BuildConfig.NEXORA_SUPABASE_ANON_KEY
    private val googleRedirectUrl: String = BuildConfig.NEXORA_SUPABASE_GOOGLE_REDIRECT_URL.ifBlank { "nexoraplayer://auth/callback" }
    private val appClientId: String = BuildConfig.NEXORA_API_APP_ID.ifBlank { "nexora-player-ghost" }
    private val appSecret: String = BuildConfig.NEXORA_API_APP_SHARED_SECRET
    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty().ifBlank { "anonymous-device" }
    }
    private val uploadFileFieldNames = listOf("file", "audio", "song", "music", "track", "audioFile")

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
        val error = params["error_description"] ?: params["error"]
        if (!error.isNullOrBlank()) throw OnlineApiException(error)

        val accessToken = params["access_token"].orEmpty()
        if (accessToken.isBlank()) throw OnlineApiException(context.getString(R.string.online_error_google_callback))

        val expiresIn = params["expires_in"]?.toLongOrNull()?.coerceAtLeast(60L) ?: 3600L
        val claims = decodeJwtPayload(accessToken)
        val profile = extractProfile(claims = claims, fallbackProvider = "google")
        return OnlineUserSession(
            accessToken = accessToken,
            refreshToken = params["refresh_token"]?.takeIf { it.isNotBlank() },
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + expiresIn,
            email = profile.email,
            userId = claims?.optString("sub")?.takeIf { it.isNotBlank() },
            displayName = profile.displayName,
            username = profile.username,
            avatarUrl = profile.avatarUrl,
            provider = profile.provider,
            bio = profile.bio,
            phoneNumber = profile.phoneNumber,
            hasPassword = profile.hasPassword
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
            requiresAuthEnvelope = false,
            appSignature = false
        )
        val session = json.toSession()
        fetchCurrentUser(session).getOrElse { session }
    }

    suspend fun register(email: String, password: String, username: String): OnlineUserSession = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cleanUsername = username.trim()
        val body = JSONObject()
            .put("email", cleanEmail)
            .put("password", password)
            .put("username", cleanUsername)
            .put("display_name", cleanUsername)
            .toString()
            .toByteArray()
        requestJson(
            url = "$apiPrefix/auth/register",
            method = "POST",
            body = body,
            bearerToken = null,
            appSignature = true
        )
        login(cleanEmail, password)
    }

    suspend fun validateSession(session: OnlineUserSession): OnlineUserSession = withContext(Dispatchers.IO) {
        val active = if (session.isExpired) refreshSession(session) else session
        fetchCurrentUser(active).getOrElse { throwable ->
            if (active.refreshToken != null) refreshSession(active) else throw throwable
        }
    }

    suspend fun fetchCurrentUser(session: OnlineUserSession): Result<OnlineUserSession> = withContext(Dispatchers.IO) {
        runCatching {
            val json = requestJson(
                url = "$apiPrefix/users/me",
                method = "GET",
                bearerToken = session.accessToken,
                appSignature = false
            )
            session.mergeProfileFromApi(json)
        }
    }

    suspend fun updateProfile(session: OnlineUserSession, request: OnlineProfileUpdateRequest): OnlineUserSession = withContext(Dispatchers.IO) {
        val bodyJson = JSONObject()
            .put("username", request.username.trim())
            .put("display_name", request.displayName.trim())
            .put("bio", request.bio.trim())
            .put("description", request.bio.trim())
        if (request.phoneNumber.trim().isNotBlank()) bodyJson.put("phone_number", request.phoneNumber.trim())
        val json = requestJson(
            url = "$apiPrefix/users/me",
            method = "PATCH",
            body = bodyJson.toString().toByteArray(),
            bearerToken = session.accessToken,
            appSignature = false
        )
        session.mergeProfileFromApi(json)
    }

    suspend fun changePassword(session: OnlineUserSession, currentPassword: String?, newPassword: String): Unit = withContext(Dispatchers.IO) {
        val isGoogleAccount = session.provider.orEmpty().contains("google", ignoreCase = true) && session.hasPassword != true
        val bodyJson = if (isGoogleAccount) {
            JSONObject()
                .put("password", newPassword)
                .put("confirm_password", newPassword)
        } else {
            JSONObject()
                .put("current_password", currentPassword.orEmpty())
                .put("new_password", newPassword)
                .put("confirm_password", newPassword)
        }
        requestJson(
            url = if (isGoogleAccount) "$apiPrefix/auth/password/google/set" else "$apiPrefix/auth/password/change",
            method = if (isGoogleAccount) "POST" else "PATCH",
            body = bodyJson.toString().toByteArray(),
            bearerToken = session.accessToken,
            appSignature = true
        )
        Unit
    }

    suspend fun updatePassword(session: OnlineUserSession, newPassword: String) = changePassword(session, null, newPassword)

    suspend fun refreshSession(session: OnlineUserSession): OnlineUserSession = withContext(Dispatchers.IO) {
        ensureSupabaseConfigured()
        val refreshToken = session.refreshToken ?: throw OnlineApiException(context.getString(R.string.online_error_missing_refresh_token))
        val body = JSONObject().put("refresh_token", refreshToken).toString().toByteArray()
        val json = requestJson(
            url = "$supabaseUrl/auth/v1/token?grant_type=refresh_token",
            method = "POST",
            body = body,
            extraHeaders = supabaseHeaders(json = true),
            requiresAuthEnvelope = false,
            appSignature = false
        )
        json.toSession(
            fallbackEmail = session.email,
            fallbackUserId = session.userId,
            fallbackDisplayName = session.displayName,
            fallbackUsername = session.username,
            fallbackAvatarUrl = session.avatarUrl,
            fallbackProvider = session.provider,
            fallbackBio = session.bio,
            fallbackPhoneNumber = session.phoneNumber,
            fallbackHasPassword = session.hasPassword
        )
    }

    suspend fun getCatalogHome(session: OnlineUserSession): OnlineCatalogHome = withContext(Dispatchers.IO) {
        val json = requestJson(
            url = "$apiPrefix/catalog/home",
            method = "GET",
            bearerToken = session.accessToken,
            appSignature = true
        )
        parseCatalogHome(json)
    }

    suspend fun getRecommendations(session: OnlineUserSession, limit: Int = 20): List<OnlineSongDto> = withContext(Dispatchers.IO) {
        val json = requestJson(
            url = "$apiPrefix/catalog/recommendations?limit=${limit.coerceIn(1, 50)}",
            method = "GET",
            bearerToken = session.accessToken,
            appSignature = true
        )
        parseSongsArray(json).take(limit)
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
        val json = requestJson(
            url = "$apiPrefix/catalog/search?q=$encoded&limit=${limit.coerceIn(1, 50)}&page=$page",
            method = "GET",
            bearerToken = session.accessToken,
            appSignature = true
        )
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

    suspend fun getShareInfo(session: OnlineUserSession, songId: String): OnlineShareInfo = withContext(Dispatchers.IO) {
        val json = requestJson(
            url = "$apiPrefix/songs/$songId/share",
            method = "GET",
            bearerToken = session.accessToken,
            appSignature = true
        )
        val data = json.optJSONObject("data") ?: json
        val share = data.optJSONObject("share") ?: data
        OnlineShareInfo(
            shareUrl = share.optString("share_url").takeIf { it.isNotBlank() },
            deepLink = share.optString("deep_link").takeIf { it.isNotBlank() },
            androidIntentUrl = share.optString("android_intent_url").takeIf { it.isNotBlank() },
            downloadUrl = share.optString("download_url").takeIf { it.isNotBlank() }
        )
    }

    suspend fun getFavorites(session: OnlineUserSession, limit: Int = 50, offset: Int = 0): List<OnlineSongDto> = withContext(Dispatchers.IO) {
        val json = requestJson(
            url = "$apiPrefix/me/favorites?limit=${limit.coerceIn(1, 100)}&offset=${offset.coerceAtLeast(0)}",
            method = "GET",
            bearerToken = session.accessToken,
            appSignature = true
        )
        parseSongsArray(json)
    }

    suspend fun setFavorite(session: OnlineUserSession, songId: String, favorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        val json = requestJson(
            url = "$apiPrefix/songs/$songId/favorite",
            method = if (favorite) "POST" else "DELETE",
            bearerToken = session.accessToken,
            appSignature = true
        )
        val data = json.optJSONObject("data") ?: json
        data.optBoolean("favorited", favorite)
    }

    suspend fun getPlaylists(session: OnlineUserSession): List<OnlinePlaylistDto> = withContext(Dispatchers.IO) {
        val json = requestJson(
            url = "$apiPrefix/me/playlists",
            method = "GET",
            bearerToken = session.accessToken,
            appSignature = true
        )
        parsePlaylists(json)
    }

    suspend fun createPlaylist(session: OnlineUserSession, name: String, description: String = "", isPublic: Boolean = false): OnlinePlaylistDto = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", name.trim())
            .put("description", description.trim())
            .put("is_public", isPublic)
            .toString()
            .toByteArray()
        val json = requestJson(
            url = "$apiPrefix/me/playlists",
            method = "POST",
            body = body,
            bearerToken = session.accessToken,
            appSignature = true
        )
        parsePlaylist(json.optJSONObject("data") ?: json)
    }

    suspend fun getPlaylistDetail(session: OnlineUserSession, playlistId: String): OnlinePlaylistDto = withContext(Dispatchers.IO) {
        val json = requestJson(
            url = "$apiPrefix/me/playlists/$playlistId",
            method = "GET",
            bearerToken = session.accessToken,
            appSignature = true
        )
        parsePlaylist(json.optJSONObject("data") ?: json)
    }

    suspend fun updatePlaylist(session: OnlineUserSession, playlistId: String, name: String, description: String, isPublic: Boolean): OnlinePlaylistDto = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", name.trim())
            .put("description", description.trim())
            .put("is_public", isPublic)
            .toString()
            .toByteArray()
        val json = requestJson(
            url = "$apiPrefix/me/playlists/$playlistId",
            method = "PATCH",
            body = body,
            bearerToken = session.accessToken,
            appSignature = true
        )
        parsePlaylist(json.optJSONObject("data") ?: json)
    }

    suspend fun deletePlaylist(session: OnlineUserSession, playlistId: String): Unit = withContext(Dispatchers.IO) {
        requestJson(
            url = "$apiPrefix/me/playlists/$playlistId",
            method = "DELETE",
            bearerToken = session.accessToken,
            appSignature = true
        )
        Unit
    }

    suspend fun addSongToPlaylist(session: OnlineUserSession, playlistId: String, songId: String, position: Int? = null): Unit = withContext(Dispatchers.IO) {
        val bodyJson = JSONObject().put("song_id", songId)
        if (position != null) bodyJson.put("position", position)
        requestJson(
            url = "$apiPrefix/me/playlists/$playlistId/items",
            method = "POST",
            body = bodyJson.toString().toByteArray(),
            bearerToken = session.accessToken,
            appSignature = true
        )
        Unit
    }

    suspend fun removeSongFromPlaylist(session: OnlineUserSession, playlistId: String, songId: String): Unit = withContext(Dispatchers.IO) {
        requestJson(
            url = "$apiPrefix/me/playlists/$playlistId/items/$songId",
            method = "DELETE",
            bearerToken = session.accessToken,
            appSignature = true
        )
        Unit
    }

    suspend fun reportSong(session: OnlineUserSession, songId: String, type: String, description: String): Unit = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("songId", songId)
            .put("type", type)
            .put("description", description.trim())
            .toString()
            .toByteArray()
        requestJson(
            url = "$apiPrefix/reports",
            method = "POST",
            body = body,
            bearerToken = session.accessToken,
            appSignature = false
        )
        Unit
    }

    suspend fun usersVersion(): String = withContext(Dispatchers.IO) {
        val json = requestJson(url = "$apiPrefix/users/version", method = "GET", requiresAuthEnvelope = false, appSignature = false)
        json.optString("version").takeIf { it.isNotBlank() }
            ?: json.optJSONObject("data")?.optString("version")?.takeIf { it.isNotBlank() }
            ?: "ok"
    }

    suspend fun healthReady(): String = withContext(Dispatchers.IO) {
        val json = requestJson(url = "$apiPrefix/health/ready", method = "GET", requiresAuthEnvelope = false, appSignature = false)
        json.optString("status").takeIf { it.isNotBlank() } ?: context.getString(R.string.online_health_ok)
    }

    suspend fun uploadSong(session: OnlineUserSession, entry: MediaEntry): OnlineSongDto = withContext(Dispatchers.IO) {
        val audioFile = copyContentUriToTempFile(entry)
        if (!audioFile.exists() || audioFile.length() <= 0L) {
            runCatching { audioFile.delete() }
            throw OnlineApiException(context.getString(R.string.online_error_read_local_file, entry.title))
        }

        try {
            runCatching { uploadSongWithIntent(session, entry, audioFile) }
                .getOrElse { uploadSongDirectMultipart(session, entry, audioFile) }
        } finally {
            runCatching { audioFile.delete() }
        }
    }

    private fun uploadSongWithIntent(session: OnlineUserSession, entry: MediaEntry, audioFile: File): OnlineSongDto {
        val intentBody = JSONObject()
            .put("original_filename", entry.uploadFilename())
            .put("file_size_bytes", audioFile.length())
            .put("mime_type", entry.safeUploadMimeType())
            .put("audio_format", entry.audioFormat())
            .toString()
            .toByteArray()
        val intentJson = requestJson(
            url = "$apiPrefix/songs/upload/intents",
            method = "POST",
            body = intentBody,
            bearerToken = session.accessToken,
            appSignature = true
        )
        val intentData = intentJson.optJSONObject("data") ?: intentJson
        val signedUrl = intentData.optString("signed_url").takeIf { it.isNotBlank() }
            ?: throw OnlineApiException(context.getString(R.string.online_error_upload))
        uploadToSignedUrl(signedUrl, audioFile, entry.safeUploadMimeType())
        val commitBody = JSONObject()
            .put("upload_id", intentData.optString("upload_id"))
            .put("storage_path", intentData.optString("storage_path"))
            .put("storage_bucket", intentData.optString("storage_bucket", "songs"))
            .put("title", entry.title)
            .put("artist", entry.artist.ifBlank { context.getString(R.string.online_unknown_artist) })
            .put("album", entry.album)
            .put("duration_seconds", if (entry.durationMs > 0L) entry.durationMs / 1000L else JSONObject.NULL)
            .put("file_size_bytes", audioFile.length())
            .put("mime_type", entry.safeUploadMimeType())
            .put("audio_format", entry.audioFormat())
            .put("cover_url", entry.artworkUrl.orEmpty())
            .toString()
            .toByteArray()
        val commitJson = requestJson(
            url = "$apiPrefix/songs/upload/commit",
            method = "POST",
            body = commitBody,
            bearerToken = session.accessToken,
            appSignature = true
        )
        val data = commitJson.optJSONObject("data") ?: commitJson
        return parseSong(data.optJSONObject("song") ?: data)
    }

    private fun uploadToSignedUrl(signedUrl: String, audioFile: File, mimeType: String) {
        val methods = listOf("PUT", "POST")
        var lastError: Throwable? = null
        for (method in methods) {
            try {
                val connection = (URL(signedUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 20_000
                    readTimeout = 180_000
                    useCaches = false
                    doOutput = true
                    setFixedLengthStreamingMode(audioFile.length())
                    setRequestProperty("Content-Type", mimeType)
                }
                audioFile.inputStream().use { input ->
                    connection.outputStream.use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
                }
                val code = connection.responseCode
                if (code in 200..299) return
                lastError = OnlineApiException(context.getString(R.string.online_error_http, code))
            } catch (throwable: Throwable) {
                lastError = throwable
            }
        }
        throw lastError ?: OnlineApiException(context.getString(R.string.online_error_upload))
    }

    private fun uploadSongDirectMultipart(session: OnlineUserSession, entry: MediaEntry, audioFile: File): OnlineSongDto {
        var lastFailure: Throwable? = null
        val uploadEndpoints = listOf("$apiPrefix/songs/upload", "$apiPrefix/songs/upload-audio", "$apiPrefix/songs/upload/audio")
        for (endpoint in uploadEndpoints) {
            for (fileFieldName in uploadFileFieldNames) {
                val boundary = "NexoraBoundary${System.currentTimeMillis()}${fileFieldName.hashCode().toString().replace("-", "N")}"
                val multipartFile = createMultipartUploadFile(
                    boundary = boundary,
                    audioFile = audioFile,
                    entry = entry,
                    fileFieldName = fileFieldName
                )
                try {
                    val json = requestMultipartJson(
                        url = endpoint,
                        method = "POST",
                        multipartFile = multipartFile,
                        boundary = boundary,
                        bearerToken = session.accessToken,
                        appSignature = true
                    )
                    val data = json.optJSONObject("data") ?: json
                    return parseSong(data.optJSONObject("song") ?: data)
                } catch (throwable: Throwable) {
                    lastFailure = throwable
                    val msg = throwable.message.orEmpty()
                    val retryable = msg.contains("file", ignoreCase = true) || msg.contains("404") || msg.contains("405")
                    if (!retryable) throw throwable
                } finally {
                    runCatching { multipartFile.delete() }
                }
            }
        }
        throw lastFailure ?: OnlineApiException(context.getString(R.string.online_error_upload))
    }

    suspend fun health(): String = withContext(Dispatchers.IO) {
        val json = requestJson(url = "$apiPrefix/health", method = "GET", requiresAuthEnvelope = false, appSignature = false)
        json.optString("status").takeIf { it.isNotBlank() } ?: context.getString(R.string.online_health_ok)
    }

    fun streamingHeaders(session: OnlineUserSession): Map<String, String> {
        return buildMap {
            put("Authorization", "Bearer ${session.accessToken}")
            put("X-Client-ID", "nexora-player-android")
            put("X-Client-Version", BuildConfig.VERSION_NAME)
            put("X-App-Platform", "android")
            put("X-App-ID", appClientId)
            put("X-Device-ID", deviceId)
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
        val bodyHash = body?.let { sha256Hex(it) }.orEmpty()
        val timestamp = System.currentTimeMillis().toString()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Client-ID", "nexora-player-android")
            setRequestProperty("X-Client-Version", BuildConfig.VERSION_NAME)
            setRequestProperty("X-App-Platform", "android")
            setRequestProperty("X-App-ID", appClientId)
            setRequestProperty("X-Device-ID", deviceId)
            bearerToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            extraHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType)
                setRequestProperty("Content-Length", body.size.toString())
            }
        }

        if (appSignature) {
            signConnection(connection, url, method, timestamp, bodyHash)
        }

        body?.let { bytes -> connection.outputStream.use { it.write(bytes) } }

        return readJsonResponse(connection, requiresAuthEnvelope)
    }

    private fun requestMultipartJson(
        url: String,
        method: String,
        multipartFile: File,
        boundary: String,
        bearerToken: String? = null,
        appSignature: Boolean = appSecret.isNotBlank()
    ): JSONObject {
        val timestamp = System.currentTimeMillis().toString()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 180_000
            useCaches = false
            doOutput = true
            setFixedLengthStreamingMode(multipartFile.length())
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("X-Client-ID", "nexora-player-android")
            setRequestProperty("X-Client-Version", BuildConfig.VERSION_NAME)
            setRequestProperty("X-App-Platform", "android")
            setRequestProperty("X-App-ID", appClientId)
            setRequestProperty("X-Device-ID", deviceId)
            bearerToken?.let { setRequestProperty("Authorization", "Bearer $it") }
        }

        if (appSignature) {
            signConnection(connection, url, method, timestamp, bodyHash = "")
        }

        multipartFile.inputStream().use { input ->
            connection.outputStream.use { output ->
                input.copyTo(output, DEFAULT_BUFFER_SIZE)
                output.flush()
            }
        }

        return readJsonResponse(connection, requiresAuthEnvelope = true)
    }

    private fun signConnection(connection: HttpURLConnection, url: String, method: String, timestamp: String, bodyHash: String) {
        val pathAndQuery = URL(url).file
        val canonical = "${method.uppercase()}\n$pathAndQuery\n$timestamp\n$deviceId\n$bodyHash"
        connection.setRequestProperty("X-App-Timestamp", timestamp)
        connection.setRequestProperty("X-Body-SHA256", bodyHash)
        connection.setRequestProperty("X-App-Signature", hmacSha256Hex(appSecret, canonical))
    }

    private fun readJsonResponse(connection: HttpURLConnection, requiresAuthEnvelope: Boolean): JSONObject {
        val code = connection.responseCode
        val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()
        val json = runCatching { JSONObject(response) }.getOrElse { JSONObject().put("message", response) }
        if (code !in 200..299) {
            throw OnlineApiException(extractErrorMessage(json, code))
        }
        if (requiresAuthEnvelope && json.has("success") && !json.optBoolean("success", false)) {
            throw OnlineApiException(extractErrorMessage(json, code))
        }
        return json
    }

    private fun extractErrorMessage(json: JSONObject, code: Int): String {
        val error = json.optJSONObject("error")
        return error?.optString("message")?.takeIf { it.isNotBlank() }
            ?: error?.optString("msg")?.takeIf { it.isNotBlank() }
            ?: json.optString("message").takeIf { it.isNotBlank() }
            ?: json.optString("msg").takeIf { it.isNotBlank() }
            ?: context.getString(R.string.online_error_http, code)
    }

    private fun parseCatalogHome(json: JSONObject): OnlineCatalogHome {
        val data = json.optJSONObject("data") ?: json
        return OnlineCatalogHome(
            popular = parseSongsArray(data.optJSONArray("popular")),
            recentlyAdded = parseSongsArray(data.optJSONArray("recently_added") ?: data.optJSONArray("recentlyAdded")),
            trendingSearches = parseStringArray(data.optJSONArray("trending_searches") ?: data.optJSONArray("trendingSearches")),
            recommendations = parseSongsArray(data.optJSONArray("recommendations"))
        )
    }

    private fun parseSongsResponse(json: JSONObject, requestedLimit: Int, requestedOffset: Int): OnlineSongsResponse {
        val items = parseSongsArray(json)
        val data = json.opt("data")
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

    private fun parseSongsArray(json: JSONObject): List<OnlineSongDto> {
        val data = json.opt("data")
        val array: JSONArray = when (data) {
            is JSONArray -> data
            is JSONObject -> data.optJSONArray("results") ?: data.optJSONArray("items") ?: data.optJSONArray("songs") ?: data.optJSONArray("favorites") ?: JSONArray()
            else -> json.optJSONArray("items") ?: json.optJSONArray("results") ?: json.optJSONArray("songs") ?: JSONArray()
        }
        return parseSongsArray(array)
    }

    private fun parseSongsArray(array: JSONArray?): List<OnlineSongDto> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val song = item.optJSONObject("song") ?: item.optJSONObject("songs") ?: item
                add(parseSong(song))
            }
        }
    }

    private fun parseStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
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
                ?: json.optString("created_at").takeIf { it.isNotBlank() },
            favorited = json.optBoolean("favorited", json.optBoolean("is_favorite", false))
        )
    }

    private fun parsePlaylists(json: JSONObject): List<OnlinePlaylistDto> {
        val data = json.opt("data")
        val array = when (data) {
            is JSONArray -> data
            is JSONObject -> data.optJSONArray("items") ?: data.optJSONArray("playlists") ?: JSONArray()
            else -> json.optJSONArray("items") ?: json.optJSONArray("playlists") ?: JSONArray()
        }
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { add(parsePlaylist(it)) }
            }
        }
    }

    private fun parsePlaylist(json: JSONObject): OnlinePlaylistDto {
        return OnlinePlaylistDto(
            id = json.optString("id"),
            name = json.optString("name", "Playlist"),
            description = json.optString("description").takeIf { it.isNotBlank() },
            isPublic = json.optBoolean("is_public", json.optBoolean("isPublic", false)),
            isFavorites = json.optBoolean("is_favorites", json.optBoolean("isFavorites", false)) || json.optString("type").equals("favorites", ignoreCase = true),
            songs = parseSongsArray(json.optJSONArray("songs") ?: json.optJSONArray("items"))
        )
    }

    private fun JSONObject.toSession(
        fallbackEmail: String? = null,
        fallbackUserId: String? = null,
        fallbackDisplayName: String? = null,
        fallbackUsername: String? = null,
        fallbackAvatarUrl: String? = null,
        fallbackProvider: String? = null,
        fallbackBio: String? = null,
        fallbackPhoneNumber: String? = null,
        fallbackHasPassword: Boolean? = null
    ): OnlineUserSession {
        val expiresIn = optLong("expires_in", 3600L).coerceAtLeast(60L)
        val user = optJSONObject("user")
        val profile = extractProfile(user = user, fallbackEmail = fallbackEmail, fallbackProvider = fallbackProvider ?: "email")
        return OnlineUserSession(
            accessToken = optString("access_token"),
            refreshToken = optString("refresh_token").takeIf { it.isNotBlank() },
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + expiresIn,
            email = profile.email ?: fallbackEmail,
            userId = user?.optString("id")?.takeIf { it.isNotBlank() } ?: fallbackUserId,
            displayName = profile.displayName ?: fallbackDisplayName,
            username = profile.username ?: fallbackUsername,
            avatarUrl = profile.avatarUrl ?: fallbackAvatarUrl,
            provider = profile.provider ?: fallbackProvider,
            bio = profile.bio ?: fallbackBio,
            phoneNumber = profile.phoneNumber ?: fallbackPhoneNumber,
            hasPassword = profile.hasPassword ?: fallbackHasPassword
        )
    }

    private fun OnlineUserSession.mergeProfileFromApi(json: JSONObject): OnlineUserSession {
        val data = json.optJSONObject("data") ?: json.optJSONObject("user") ?: json
        val profile = extractProfile(user = data, fallbackEmail = email, fallbackProvider = provider)
        return copy(
            email = profile.email ?: email,
            userId = data.optString("id").takeIf { it.isNotBlank() } ?: userId,
            displayName = profile.displayName ?: displayName,
            username = profile.username ?: username,
            avatarUrl = profile.avatarUrl ?: avatarUrl,
            provider = profile.provider ?: provider,
            bio = profile.bio ?: bio,
            phoneNumber = profile.phoneNumber ?: phoneNumber,
            hasPassword = profile.hasPassword ?: hasPassword
        )
    }

    private data class OnlineProfileData(
        val email: String? = null,
        val displayName: String? = null,
        val username: String? = null,
        val avatarUrl: String? = null,
        val provider: String? = null,
        val bio: String? = null,
        val phoneNumber: String? = null,
        val hasPassword: Boolean? = null
    )

    private fun extractProfile(
        user: JSONObject? = null,
        claims: JSONObject? = null,
        fallbackEmail: String? = null,
        fallbackProvider: String? = null
    ): OnlineProfileData {
        val metadata = user?.optJSONObject("user_metadata") ?: claims?.optJSONObject("user_metadata")
        val appMetadata = user?.optJSONObject("app_metadata") ?: claims?.optJSONObject("app_metadata")
        val firstIdentity = user?.optJSONArray("identities")?.optJSONObject(0)
        val identityData = firstIdentity?.optJSONObject("identity_data")

        fun JSONObject?.firstNonBlank(vararg keys: String): String? {
            if (this == null) return null
            return keys.firstNotNullOfOrNull { key -> optString(key).takeIf { it.isNotBlank() } }
        }

        fun JSONObject?.firstBool(vararg keys: String): Boolean? {
            if (this == null) return null
            return keys.firstNotNullOfOrNull { key -> if (has(key) && !isNull(key)) optBoolean(key) else null }
        }

        val email = user.firstNonBlank("email")
            ?: claims.firstNonBlank("email")
            ?: metadata.firstNonBlank("email")
            ?: identityData.firstNonBlank("email")
            ?: fallbackEmail

        val displayName = user.firstNonBlank("display_name", "full_name", "name")
            ?: metadata.firstNonBlank("display_name", "full_name", "name")
            ?: identityData.firstNonBlank("display_name", "full_name", "name")
            ?: claims.firstNonBlank("display_name", "full_name", "name")

        val username = user.firstNonBlank("username", "user_name", "preferred_username")
            ?: metadata.firstNonBlank("username", "user_name", "preferred_username")
            ?: identityData.firstNonBlank("username", "user_name", "preferred_username")
            ?: claims.firstNonBlank("username", "preferred_username")

        val avatarUrl = user.firstNonBlank("avatar_url", "picture", "avatarUrl")
            ?: metadata.firstNonBlank("avatar_url", "picture", "avatarUrl")
            ?: identityData.firstNonBlank("avatar_url", "picture", "avatarUrl")
            ?: claims.firstNonBlank("avatar_url", "picture")

        val provider = user.firstNonBlank("provider")
            ?: appMetadata.firstNonBlank("provider")
            ?: firstIdentity.firstNonBlank("provider")
            ?: metadata.firstNonBlank("provider", "provider_id")
            ?: claims.firstNonBlank("provider")
            ?: fallbackProvider

        val bio = user.firstNonBlank("bio", "description")
            ?: metadata.firstNonBlank("bio", "description")
        val phoneNumber = user.firstNonBlank("phone_number", "phone", "phoneNumber")
            ?: metadata.firstNonBlank("phone_number", "phone", "phoneNumber")
        val hasPassword = user.firstBool("has_password", "hasPassword", "password_enabled")
            ?: appMetadata.firstBool("has_password", "hasPassword")

        return OnlineProfileData(
            email = email,
            displayName = displayName,
            username = username,
            avatarUrl = avatarUrl,
            provider = provider,
            bio = bio,
            phoneNumber = phoneNumber,
            hasPassword = hasPassword
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

    private fun createMultipartUploadFile(
        boundary: String,
        audioFile: File,
        entry: MediaEntry,
        fileFieldName: String
    ): File {
        val multipart = File.createTempFile("nexora-upload-body-", ".multipart", context.cacheDir)
        multipart.outputStream().use { out ->
            fun write(value: String) = out.write(value.toByteArray(StandardCharsets.UTF_8))
            fun field(name: String, value: String) {
                if (value.isBlank()) return
                write("--$boundary\r\n")
                write("Content-Disposition: form-data; name=\"$name\"\r\n")
                write("Content-Type: text/plain; charset=UTF-8\r\n\r\n")
                write(value)
                write("\r\n")
            }

            field("title", entry.title)
            field("artist", entry.artist.ifBlank { context.getString(R.string.online_unknown_artist) })
            field("album", entry.album)
            if (entry.durationMs > 0L) {
                val seconds = (entry.durationMs / 1000L).toString()
                field("duration", seconds)
                field("duration_seconds", seconds)
            }
            field("mime_type", entry.safeUploadMimeType())
            field("file_size_bytes", audioFile.length().toString())
            field("client_metadata_ready", "true")
            field("cover_url", entry.artworkUrl.orEmpty())

            write("--$boundary\r\n")
            write("Content-Disposition: form-data; name=\"$fileFieldName\"; filename=\"${entry.uploadFilename()}\"\r\n")
            write("Content-Type: ${entry.safeUploadMimeType()}\r\n")
            write("Content-Transfer-Encoding: binary\r\n\r\n")
            audioFile.inputStream().use { input -> input.copyTo(out, DEFAULT_BUFFER_SIZE) }
            write("\r\n--$boundary--\r\n")
        }
        return multipart
    }

    private fun MediaEntry.safeUploadMimeType(): String {
        val raw = mimeType.orEmpty().trim()
        return if (raw.isBlank() || raw.endsWith("/*")) "audio/mpeg" else raw
    }

    private fun MediaEntry.audioFormat(): String {
        val filename = uploadFilename().lowercase()
        return filename.substringAfterLast('.', missingDelimiterValue = "mp3").ifBlank { "mp3" }
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

    private fun hmacSha256Hex(secret: String, value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(value.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

class OnlineApiException(message: String) : Exception(message)
