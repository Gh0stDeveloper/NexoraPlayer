package com.nexora.player.data.model

import android.net.Uri

enum class MediaKind {
    AUDIO,
    VIDEO
}

enum class SortMode {
    DATE_ADDED_DESC,
    DATE_ADDED_ASC,
    TITLE_ASC,
    TITLE_DESC,
    DURATION_ASC,
    DURATION_DESC,
    ARTIST_ASC,
    ALBUM_ASC,
    SIZE_ASC,
    SIZE_DESC,
    RESOLUTION_ASC,
    RESOLUTION_DESC
}

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class AppDestination(val label: String) {
    MUSIC("Música"),
    VIDEOS("Videos"),
    QUEUE("Cola"),
    PLAYLISTS("Listas"),
    FAVORITES("Favoritos"),
    HISTORY("Historial"),
    SETTINGS("Ajustes")
}

data class MediaEntry(
    val id: Long,
    val kind: MediaKind,
    val uri: Uri,
    val title: String,
    val subtitle: String = "",
    val album: String = "",
    val artist: String = "",
    val durationMs: Long = 0L,
    val dateAdded: Long = 0L,
    val sizeBytes: Long = 0L,
    val width: Int? = null,
    val height: Int? = null,
    val mimeType: String? = null,
    val folder: String? = null,
    val albumId: Long? = null
) {
    val resolutionLabel: String
        get() = if (width != null && height != null) "${width}x$height" else "—"
}

data class PlaybackSnapshot(
    val queue: List<MediaEntry> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false
) {
    val currentItem: MediaEntry?
        get() = queue.getOrNull(currentIndex)
}
