package com.nexora.player.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FavoriteMediaEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        OnlineSavedTrackEntity::class,
        PlaybackHistoryEntity::class,
        LyricsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NexoraDatabase : RoomDatabase() {
    abstract fun favoritesDao(): FavoritesDao
    abstract fun playlistsDao(): PlaylistsDao
    abstract fun historyDao(): HistoryDao
    abstract fun onlineSavedTracksDao(): OnlineSavedTracksDao
    abstract fun lyricsDao(): LyricsDao

    companion object {
        @Volatile private var INSTANCE: NexoraDatabase? = null

        fun get(context: Context): NexoraDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                NexoraDatabase::class.java,
                "nexora.db"
            ).build().also { INSTANCE = it }
        }
    }
}
