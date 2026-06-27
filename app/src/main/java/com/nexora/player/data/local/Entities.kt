package com.nexora.player.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val mediaId: Long,
    val mediaKind: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uriString: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_items")
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val playlistId: Long,
    val mediaId: Long,
    val mediaKind: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uriString: String,
    val orderIndex: Int,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "online_saved_tracks", indices = [androidx.room.Index(value = ["providerId", "sourceId"], unique = true)])
data class OnlineSavedTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val providerId: String,
    val sourceId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val streamUrl: String,
    val downloadUrl: String?,
    val durationMs: Long,
    val sourcePageUrl: String?,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val mediaId: Long,
    val mediaKind: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uriString: String,
    val playedAt: Long = System.currentTimeMillis()
)
