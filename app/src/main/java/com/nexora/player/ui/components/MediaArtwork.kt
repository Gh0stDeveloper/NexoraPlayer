package com.nexora.player.ui.components

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nexora.player.R
import com.nexora.player.data.model.MediaEntry
import com.nexora.player.data.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MediaArtwork(
    item: MediaEntry,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val artwork = produceState<ImageBitmap?>(initialValue = null, item.id, item.uri.toString()) {
        value = withContext(Dispatchers.IO) {
            loadArtworkBitmap(context, item)?.asImageBitmap()
        }
    }.value

    val colors = if (item.kind == MediaKind.VIDEO) {
        listOf(Color(0xFF0F172A), Color(0xFF1D4ED8), Color(0xFF22D3EE))
    } else {
        listOf(Color(0xFF111827), Color(0xFF7C3AED), Color(0xFF22D3EE))
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = Brush.linearGradient(colors))
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            if (artwork == null) {
                if (item.kind == MediaKind.AUDIO) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(60.dp)
                            .align(Alignment.Center)
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_video_placeholder),
                        contentDescription = null,
                        modifier = Modifier
                            .size(60.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            Surface(
                color = Color.Black.copy(alpha = 0.22f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = if (item.kind == MediaKind.AUDIO) "Audio" else "Video",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private fun loadArtworkBitmap(context: Context, item: MediaEntry): Bitmap? {
    return when (item.kind) {
        MediaKind.AUDIO -> loadAudioArtworkBitmap(context, item)
        MediaKind.VIDEO -> loadVideoThumbnailBitmap(context, item)
    }
}

private fun loadAudioArtworkBitmap(context: Context, item: MediaEntry): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, item.uri)
        retriever.embeddedPicture?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } ?: loadAlbumArtBitmap(context, item.albumId)
    } catch (_: Throwable) {
        loadAlbumArtBitmap(context, item.albumId)
    } finally {
        runCatching { retriever.release() }
    }
}

private fun loadAlbumArtBitmap(context: Context, albumId: Long?): Bitmap? {
    if (albumId == null || albumId <= 0L) return null

    return runCatching {
        val albumUri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
        context.contentResolver.query(
            albumUri,
            arrayOf(MediaStore.Audio.Albums.ALBUM_ART),
            null,
            null,
            null
        )?.use { cursor ->
            val artCol = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART)
            if (cursor.moveToFirst() && artCol >= 0) {
                val path = cursor.getString(artCol)
                if (!path.isNullOrBlank()) BitmapFactory.decodeFile(path) else null
            } else null
        }
    }.getOrNull()
}

private fun loadVideoThumbnailBitmap(context: Context, item: MediaEntry): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(item.uri, android.util.Size(720, 720), null)
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, item.uri)
                retriever.frameAtTime
            } finally {
                runCatching { retriever.release() }
            }
        }
    } catch (_: Throwable) {
        null
    }
}
