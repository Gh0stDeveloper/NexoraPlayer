package com.nexora.player.data.online

import android.net.Uri
import com.nexora.player.data.model.MediaEntry
import com.nexora.player.data.model.MediaKind
import com.nexora.player.data.model.MediaSource
import java.security.MessageDigest

private const val ONLINE_ID_PREFIX = Long.MIN_VALUE / 4L

data class OnlineSongsResponse(
    val items: List<OnlineSongDto>,
    val total: Int,
    val limit: Int,
    val offset: Int
)

data class OnlineSongDto(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val genre: String?,
    val durationSeconds: Long?,
    val audioUrl: String?,
    val coverUrl: String?,
    val lyricsUrl: String?,
    val lyrics: String?,
    val source: String?,
    val canDownload: Boolean,
    val createdAt: String?
)

data class OnlineLyricsDto(
    val songId: String,
    val lyrics: String?,
    val synchronized: Boolean = false,
    val source: String? = null
)

data class OnlineUserSession(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochSeconds: Long,
    val email: String?,
    val userId: String?,
    val displayName: String? = null,
    val username: String? = null,
    val avatarUrl: String? = null,
    val provider: String? = null
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() / 1000L >= expiresAtEpochSeconds - 60L

    val profileName: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: "Nexora"
}

data class OnlineUploadProgress(
    val running: Boolean = false,
    val currentIndex: Int = 0,
    val total: Int = 0,
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val currentTitle: String? = null,
    val message: String? = null,
    val errors: List<String> = emptyList()
)

data class OnlineUiState(
    val session: OnlineUserSession? = null,
    val restoringSession: Boolean = true,
    val authLoading: Boolean = false,
    val authError: String? = null,
    val songs: List<OnlineSongDto> = emptyList(),
    val searchResults: List<OnlineSongDto> = emptyList(),
    val onlineQuery: String = "",
    val loadingSongs: Boolean = false,
    val searching: Boolean = false,
    val songsError: String? = null,
    val searchError: String? = null,
    val selectedUploadIds: Set<Long> = emptySet(),
    val uploadProgress: OnlineUploadProgress = OnlineUploadProgress()
) {
    val loggedIn: Boolean get() = session != null
}

fun OnlineSongDto.toMediaEntry(apiBaseUrl: String): MediaEntry? {
    val playableUrl = resolvedAudioUrl(apiBaseUrl).takeIf { it.isNotBlank() } ?: return null
    return MediaEntry(
        id = stableOnlineLongId(id),
        kind = MediaKind.AUDIO,
        uri = Uri.parse(playableUrl),
        title = title.ifBlank { "Nexora Online" },
        subtitle = artist.orEmpty(),
        album = album.orEmpty(),
        artist = artist.orEmpty(),
        durationMs = (durationSeconds ?: 0L).coerceAtLeast(0L) * 1000L,
        mimeType = "audio/*",
        folder = "Nexora Online",
        source = MediaSource.ONLINE,
        onlineId = id,
        artworkUrl = coverUrl
    )
}

fun OnlineSongDto.resolvedAudioUrl(apiBaseUrl: String): String {
    val explicit = audioUrl.orEmpty().trim()
    if (explicit.startsWith("http://") || explicit.startsWith("https://")) return explicit
    if (explicit.startsWith("/")) return apiBaseUrl.trimEnd('/') + explicit
    return "${apiBaseUrl.trimEnd('/')}/api/v1/songs/$id/stream"
}

fun stableOnlineLongId(value: String): Long {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    var result = 0L
    for (index in 0 until 8) {
        result = (result shl 8) or (digest[index].toLong() and 0xffL)
    }
    val positive = result and Long.MAX_VALUE
    return ONLINE_ID_PREFIX - (positive % 1_000_000_000_000L)
}
