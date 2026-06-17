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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexora.player.R
import com.nexora.player.data.model.MediaEntry
import com.nexora.player.data.online.OnlineTrack
import com.nexora.player.ui.components.MediaItemRow
import com.nexora.player.ui.components.OnlineTrackCard

@Composable
fun SearchResultsScreen(
    modifier: Modifier = Modifier,
    query: String,
    audio: List<MediaEntry>,
    videos: List<MediaEntry>,
    onlineEnabled: Boolean,
    onlineLoading: Boolean,
    onlineError: String?,
    onlineTracks: List<OnlineTrack>,
    savedOnlineKeys: Set<String>,
    onPlayAudio: (List<MediaEntry>, MediaEntry) -> Unit,
    onPlayVideo: (List<MediaEntry>, MediaEntry) -> Unit,
    onPlayOnline: (List<OnlineTrack>, OnlineTrack) -> Unit,
    onToggleFavorite: (MediaEntry) -> Unit,
    onToggleSaveOnline: (OnlineTrack) -> Unit,
    favoriteIds: Set<Long>
) {
    val totalLocal = audio.size + videos.size
    val totalFound = totalLocal + if (onlineEnabled) onlineTracks.size else 0
    val summaryText = if (totalFound == 0) {
        stringResource(R.string.results_empty, query)
    } else {
        stringResource(R.string.results_count, totalFound)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.results_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (query.isNotBlank()) {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    if (onlineEnabled) stringResource(R.string.online_search_enabled)
                                    else stringResource(R.string.online_search_disabled)
                                )
                            }
                        )
                    }
                }
            }
        }

        if (audio.isNotEmpty()) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.results_audio_chip, audio.size)) })
                }
            }
            items(audio, key = { it.id }) { item ->
                MediaItemRow(
                    item = item,
                    isFavorite = favoriteIds.contains(item.id),
                    onClick = { onPlayAudio(audio, item) },
                    onFavoriteClick = { onToggleFavorite(item) }
                )
            }
        }

        if (audio.isNotEmpty() && videos.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
            }
        }

        if (videos.isNotEmpty()) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.results_video_chip, videos.size)) })
                }
            }
            items(videos, key = { it.id }) { item ->
                MediaItemRow(
                    item = item,
                    onClick = { onPlayVideo(videos, item) }
                )
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
        }

        item {
            ElevatedCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.online_results_title), style = MaterialTheme.typography.titleMedium)
                    when {
                        !onlineEnabled -> Text(
                            stringResource(R.string.online_results_disabled_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        onlineLoading -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                stringResource(R.string.online_search_loading),
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        onlineError != null -> Text(
                            text = onlineError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        onlineTracks.isEmpty() && query.isNotBlank() -> Text(
                            stringResource(R.string.online_results_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        onlineTracks.isEmpty() -> Text(
                            stringResource(R.string.online_results_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        if (onlineEnabled && onlineTracks.isNotEmpty()) {
            items(onlineTracks, key = { it.key }) { track ->
                OnlineTrackCard(
                    track = track,
                    isSaved = savedOnlineKeys.contains(track.key),
                    onPlay = { onPlayOnline(onlineTracks, track) },
                    onSaveToggle = { onToggleSaveOnline(track) }
                )
            }
        }
    }
}
