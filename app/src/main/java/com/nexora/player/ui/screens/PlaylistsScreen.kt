package com.nexora.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.player.data.local.PlaylistEntity
import com.nexora.player.data.local.PlaylistItemEntity
import com.nexora.player.ui.components.MediaArtwork
import com.nexora.player.ui.components.formatDuration
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.Flow

@Composable
fun PlaylistsScreen(
    modifier: Modifier = Modifier,
    playlists: List<PlaylistEntity>,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (PlaylistEntity) -> Unit,
    onOpenPlaylist: (PlaylistEntity) -> Unit,
    playlistPreviewItems: (Long) -> Flow<List<PlaylistItemEntity>>
) {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PlaylistHeader(
            playlistCount = playlists.size,
            onCreate = { showDialog = true }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(playlists, key = { it.id }) { playlist ->
                val preview by playlistPreviewItems(playlist.id)
                    .collectAsStateWithLifecycle(initialValue = emptyList())

                PlaylistCard(
                    playlist = playlist,
                    preview = preview,
                    onOpen = { onOpenPlaylist(playlist) },
                    onDelete = { onDeletePlaylist(playlist) }
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Crear playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) onCreatePlaylist(name.trim())
                    name = ""
                    showDialog = false
                }) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun PlaylistHeader(
    playlistCount: Int,
    onCreate: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFF54047), Color(0xFFE64366)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Playlists", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text("$playlistCount listas · tus mezclas y colecciones", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onCreate, shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Nueva")
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: PlaylistEntity,
    preview: List<PlaylistItemEntity>,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val isAuto = playlist.id < 0
    val songCount = preview.size
    val duration = preview.sumOf { it.durationMs }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistPreviewMosaic(
                items = preview,
                modifier = Modifier.size(82.dp)
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        playlist.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isAuto) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(3.dp))
                                Text("Auto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Text(
                    if (isAuto) "Se ordena por reproducciones reales" else DateFormat.getDateInstance().format(Date(playlist.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    buildString {
                        append(if (songCount == 1) "1 canción" else "$songCount canciones")
                        if (duration > 0L) append(" · ${formatDuration(duration)}")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isAuto) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar playlist", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PlaylistPreviewMosaic(
    items: List<PlaylistItemEntity>,
    modifier: Modifier = Modifier
) {
    val preview = items.take(4)

    Surface(
        modifier = modifier.clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(2) { row ->
                Row(modifier = Modifier.weight(1f)) {
                    repeat(2) { col ->
                        val index = row * 2 + col
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        ) {
                            if (index < preview.size) {
                                MediaArtwork(
                                    item = preview[index].toMediaEntry(),
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun PlaylistItemEntity.toMediaEntry() = com.nexora.player.data.model.MediaEntry(
    id = mediaId,
    kind = if (mediaKind == com.nexora.player.data.model.MediaKind.VIDEO.name) {
        com.nexora.player.data.model.MediaKind.VIDEO
    } else {
        com.nexora.player.data.model.MediaKind.AUDIO
    },
    uri = android.net.Uri.parse(uriString),
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs
)
