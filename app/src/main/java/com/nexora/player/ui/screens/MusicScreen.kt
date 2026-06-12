package com.nexora.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexora.player.R
import com.nexora.player.data.local.PlaylistEntity
import com.nexora.player.data.model.MediaEntry
import com.nexora.player.data.model.SortMode
import com.nexora.player.ui.components.MediaItemRow
import com.nexora.player.ui.components.SortSelector

@Composable
fun MusicScreen(
    modifier: Modifier = Modifier,
    items: List<MediaEntry>,
    favorites: Set<Long>,
    playlists: List<PlaylistEntity>,
    sortMode: SortMode,
    onPlay: (List<MediaEntry>, MediaEntry) -> Unit,
    onToggleFavorite: (MediaEntry) -> Unit,
    onAddToPlaylist: (PlaylistEntity, MediaEntry) -> Unit,
    onHideFromLibrary: (MediaEntry) -> Unit,
    onRefresh: () -> Unit,
    onSortSelected: (SortMode) -> Unit
) {
    var selectedItem by remember { mutableStateOf<MediaEntry?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        ElevatedCard(
            modifier = Modifier.padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.music_library_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.music_library_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = false,
                        onClick = onRefresh,
                        label = { Text(stringResource(R.string.refresh)) }
                    )
                    SortSelector(
                        selected = sortMode,
                        options = listOf(
                            SortMode.DATE_ADDED_DESC,
                            SortMode.DATE_ADDED_ASC,
                            SortMode.TITLE_ASC,
                            SortMode.TITLE_DESC,
                            SortMode.DURATION_ASC,
                            SortMode.DURATION_DESC,
                            SortMode.ARTIST_ASC,
                            SortMode.ALBUM_ASC
                        ),
                        onSelected = onSortSelected
                    )
                }
            }
        }

        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.no_visible_music), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.hidden_tracks_restore_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.id }) { item ->
                MediaItemRow(
                    item = item,
                    isFavorite = favorites.contains(item.id),
                    onClick = { onPlay(items, item) },
                    onFavoriteClick = { onToggleFavorite(item) },
                    onMoreClick = { selectedItem = item }
                )
            }
        }
    }

    if (selectedItem != null) {
        val item = selectedItem!!
        AlertDialog(
            onDismissRequest = { selectedItem = null },
            title = { Text(item.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.choose_action_for_item),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (playlists.isEmpty()) {
                        Text(
                            stringResource(R.string.no_playlists_yet),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            stringResource(R.string.add_to_playlist),
                            style = MaterialTheme.typography.labelLarge
                        )
                        playlists.forEach { playlist ->
                            TextButton(onClick = {
                                onAddToPlaylist(playlist, item)
                                selectedItem = null
                            }) {
                                Text(playlist.name)
                            }
                        }
                    }

                    HorizontalDivider()
                    TextButton(onClick = {
                        onHideFromLibrary(item)
                        selectedItem = null
                    }) {
                        Text(stringResource(R.string.hide_from_library))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedItem = null }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}
