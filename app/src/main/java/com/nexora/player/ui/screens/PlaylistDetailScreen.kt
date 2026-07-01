package com.nexora.player.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexora.player.R
import com.nexora.player.NEXORA_LIKED_PLAYLIST_ID
import com.nexora.player.data.local.PlaylistEntity
import com.nexora.player.data.local.PlaylistItemEntity
import com.nexora.player.data.model.MediaEntry
import com.nexora.player.data.model.MediaKind
import com.nexora.player.ui.components.MediaArtwork
import com.nexora.player.ui.components.MediaItemRow
import com.nexora.player.ui.components.formatDuration

@Composable
fun PlaylistDetailScreen(
    modifier: Modifier = Modifier,
    playlist: PlaylistEntity,
    playlistItems: List<PlaylistItemEntity>,
    availableSongs: List<MediaEntry>,
    onBack: () -> Unit,
    onPlayItem: (List<PlaylistItemEntity>, PlaylistItemEntity) -> Unit,
    onRemoveItem: (PlaylistItemEntity) -> Unit,
    onAddSongs: (List<MediaEntry>) -> Unit,
    onRenamePlaylist: (String) -> Unit = {},
    onDuplicatePlaylist: () -> Unit = {},
    onExportPlaylist: () -> Unit = {},
    onMoveItem: (Int, Int) -> Unit = { _, _ -> },
    onPlayShuffle: (List<PlaylistItemEntity>) -> Unit = {},
    isAutoPlaylist: Boolean = false,
    canRemoveItems: Boolean = !isAutoPlaylist
) {
    val existingIds = playlistItems.map { it.mediaId }.toSet()
    val candidates = availableSongs.filterNot { existingIds.contains(it.id) }
    var selectedCandidateIds by remember(playlist.id) { mutableStateOf<Set<Long>>(emptySet()) }
    var renameDialog by remember { mutableStateOf(false) }
    var renameValue by remember(playlist.name) { mutableStateOf(playlist.name) }
    val duration = playlistItems.sumOf { it.durationMs }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            Spacer(Modifier.weight(1f))
            if (!isAutoPlaylist) {
                FilledTonalIconButton(onClick = { renameDialog = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_rename))
                }
                FilledTonalIconButton(onClick = onDuplicatePlaylist) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.action_duplicate))
                }
                FilledTonalIconButton(onClick = onExportPlaylist) {
                    Icon(Icons.Filled.FileUpload, contentDescription = stringResource(R.string.action_export))
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaylistPreviewMosaic(items = playlistItems, modifier = Modifier.size(104.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.playlist_count, playlistItems.size, formatDuration(duration)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when {
                            playlist.id == NEXORA_LIKED_PLAYLIST_ID -> stringResource(R.string.playlist_liked_desc)
                            isAutoPlaylist -> stringResource(R.string.playlist_auto_desc)
                            else -> stringResource(R.string.playlist_regular_desc)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { playlistItems.firstOrNull()?.let { onPlayItem(playlistItems, it) } },
                            enabled = playlistItems.isNotEmpty()
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.action_play))
                        }
                        OutlinedButton(
                            onClick = { onPlayShuffle(playlistItems) },
                            enabled = playlistItems.isNotEmpty()
                        ) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.action_shuffle))
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(stringResource(R.string.songs_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
            }

            if (playlistItems.isEmpty()) {
                item {
                    Text(
                        text = if (isAutoPlaylist) stringResource(R.string.playlist_empty_auto) else stringResource(R.string.playlist_empty_regular),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                itemsIndexed(
                    items = playlistItems,
                    key = { index, item -> if (item.id != 0L) item.id else "${item.mediaKind}:${item.mediaId}:$index" }
                ) { index, item ->
                    PlaylistTrackCompact(
                        item = item,
                        index = index,
                        total = playlistItems.size,
                        isAutoPlaylist = isAutoPlaylist,
                        canRemoveItems = canRemoveItems,
                        onPlay = { onPlayItem(playlistItems, item) },
                        onRemove = { onRemoveItem(item) },
                        onMoveUp = { onMoveItem(index, index - 1) },
                        onMoveDown = { onMoveItem(index, index + 1) }
                    )
                }
            }

            if (!isAutoPlaylist) {
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp)) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.playlist_add_music), style = MaterialTheme.typography.titleMedium)
                        Button(
                            onClick = {
                                val selected = candidates.filter { selectedCandidateIds.contains(it.id) }
                                if (selected.isNotEmpty()) onAddSongs(selected)
                                selectedCandidateIds = emptySet()
                            },
                            enabled = selectedCandidateIds.isNotEmpty()
                        ) {
                            Icon(Icons.Filled.LibraryMusic, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.playlist_add_selected))
                        }
                    }
                }

                if (candidates.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.playlist_no_more_songs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    itemsIndexed(candidates, key = { _, song -> song.id }) { _, song ->
                        val selected = selectedCandidateIds.contains(song.id)
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { checked ->
                                    selectedCandidateIds = if (checked) selectedCandidateIds + song.id else selectedCandidateIds - song.id
                                }
                            )
                            MediaItemRow(
                                item = song,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedCandidateIds = if (selected) selectedCandidateIds - song.id else selectedCandidateIds + song.id
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (renameDialog) {
        AlertDialog(
            onDismissRequest = { renameDialog = false },
            title = { Text(stringResource(R.string.playlist_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.playlists_name_hint)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameValue.isNotBlank()) onRenamePlaylist(renameValue.trim())
                    renameDialog = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { renameDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun PlaylistTrackCompact(
    item: PlaylistItemEntity,
    index: Int,
    total: Int,
    isAutoPlaylist: Boolean,
    canRemoveItems: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MediaItemRow(
            item = item.toMediaEntry(),
            modifier = Modifier.weight(1f),
            onClick = onPlay
        )
        if (!isAutoPlaylist) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FilledTonalIconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.action_move_up))
                }
                FilledTonalIconButton(onClick = onMoveDown, enabled = index < total - 1, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.action_move_down))
                }
            }
        }
        if (canRemoveItems) {
            FilledTonalIconButton(onClick = onRemove, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_remove))
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
    ElevatedCard(modifier = modifier.clip(RoundedCornerShape(4.dp)), shape = RoundedCornerShape(4.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(2) { row ->
                Row(modifier = Modifier.weight(1f)) {
                    repeat(2) { col ->
                        val index = row * 2 + col
                        Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (index < preview.size) {
                                MediaArtwork(item = preview[index].toMediaEntry(), modifier = Modifier.fillMaxSize(), cornerRadius = 0.dp)
                            } else {
                                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun PlaylistItemEntity.toMediaEntry() = MediaEntry(
    id = mediaId,
    kind = if (mediaKind == MediaKind.VIDEO.name) MediaKind.VIDEO else MediaKind.AUDIO,
    uri = Uri.parse(uriString),
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs
)
