package com.nexora.player.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val mediaId: Long,
    val rawText: String,
    val translatedRawText: String? = null,
    val source: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
