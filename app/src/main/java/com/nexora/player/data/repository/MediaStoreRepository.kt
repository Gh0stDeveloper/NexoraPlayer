package com.nexora.player.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.nexora.player.data.model.MediaEntry
import com.nexora.player.data.model.MediaKind
import com.nexora.player.data.model.SortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreRepository(private val context: Context) {

    suspend fun loadAudio(sortMode: SortMode): List<MediaEntry> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sort = sortClause(sortMode, mediaKind = MediaKind.AUDIO)

        context.contentResolver.query(collection, projection, selection, null, sort)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val folderCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)

            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    add(
                        MediaEntry(
                            id = id,
                            kind = MediaKind.AUDIO,
                            uri = ContentUris.withAppendedId(collection, id),
                            title = cursor.getString(titleCol).orEmpty(),
                            subtitle = cursor.getString(artistCol).orEmpty(),
                            album = cursor.getString(albumCol).orEmpty(),
                            artist = cursor.getString(artistCol).orEmpty(),
                            durationMs = cursor.getLong(durationCol),
                            dateAdded = cursor.getLong(dateAddedCol) * 1000L,
                            sizeBytes = cursor.getLong(sizeCol),
                            mimeType = cursor.getString(mimeCol),
                            folder = folderCol.takeIf { it >= 0 }?.let { cursor.getString(it) },
                            albumId = albumIdCol.takeIf { it >= 0 }?.let { cursor.getLong(it) }
                        )
                    )
                }
            }
        } ?: emptyList()
    }

    suspend fun loadVideos(sortMode: SortMode): List<MediaEntry> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.RELATIVE_PATH
        )

        val sort = sortClause(sortMode, mediaKind = MediaKind.VIDEO)

        context.contentResolver.query(collection, projection, null, null, sort)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val folderCol = cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)

            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    add(
                        MediaEntry(
                            id = id,
                            kind = MediaKind.VIDEO,
                            uri = ContentUris.withAppendedId(collection, id),
                            title = cursor.getString(titleCol).orEmpty(),
                            durationMs = cursor.getLong(durationCol),
                            dateAdded = cursor.getLong(dateAddedCol) * 1000L,
                            sizeBytes = cursor.getLong(sizeCol),
                            width = cursor.getInt(widthCol).takeIf { it > 0 },
                            height = cursor.getInt(heightCol).takeIf { it > 0 },
                            mimeType = cursor.getString(mimeCol),
                            folder = folderCol.takeIf { it >= 0 }?.let { cursor.getString(it) }
                        )
                    )
                }
            }
        } ?: emptyList()
    }

    private fun sortClause(sortMode: SortMode, mediaKind: MediaKind): String = when (sortMode) {
        SortMode.TITLE_ASC -> "title COLLATE NOCASE ASC"
        SortMode.TITLE_DESC -> "title COLLATE NOCASE DESC"
        SortMode.DURATION_ASC -> "duration ASC"
        SortMode.DURATION_DESC -> "duration DESC"
        SortMode.ARTIST_ASC -> if (mediaKind == MediaKind.AUDIO) "artist COLLATE NOCASE ASC" else "date_added DESC"
        SortMode.ALBUM_ASC -> if (mediaKind == MediaKind.AUDIO) "album COLLATE NOCASE ASC" else "date_added DESC"
        SortMode.SIZE_ASC -> "size ASC"
        SortMode.SIZE_DESC -> "size DESC"
        SortMode.RESOLUTION_ASC -> if (mediaKind == MediaKind.VIDEO) "height ASC" else "date_added DESC"
        SortMode.RESOLUTION_DESC -> if (mediaKind == MediaKind.VIDEO) "height DESC" else "date_added DESC"
        SortMode.DATE_ADDED_ASC -> "date_added ASC"
        SortMode.DATE_ADDED_DESC -> "date_added DESC"
    }
}
