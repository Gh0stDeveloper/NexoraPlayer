package com.nexora.player.data.online

import android.net.Uri
import com.nexora.player.data.model.MediaEntry
import com.nexora.player.data.model.MediaKind

const val ONLINE_MEDIA_KIND = "ONLINE_AUDIO"

data class OnlineTrack(
    val providerId: String,
    val providerLabel: String,
    val sourceId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val artworkUrl: String? = null,
    val streamUrl: String,
    val downloadUrl: String? = null,
    val durationMs: Long = 0L,
    val sourcePageUrl: String? = null
) {
    val key: String = "$providerId:$sourceId"
    val stableMediaId: Long = key.hashCode().toLong() and Long.MAX_VALUE

    fun toMediaEntry(): MediaEntry = MediaEntry(
        id = stableMediaId,
        kind = MediaKind.AUDIO,
        uri = Uri.parse(streamUrl),
        title = title,
        subtitle = providerLabel,
        album = album,
        artist = artist,
        durationMs = durationMs,
        mimeType = "audio/*"
    )
}
