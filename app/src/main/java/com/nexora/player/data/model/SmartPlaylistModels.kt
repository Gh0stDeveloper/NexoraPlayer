package com.nexora.player.data.model

data class MostPlayedTrack(
    val media: MediaEntry,
    val playCount: Int
)

object SmartPlaylists {
    const val MOST_PLAYED_NAME = "Más escuchadas"
}
