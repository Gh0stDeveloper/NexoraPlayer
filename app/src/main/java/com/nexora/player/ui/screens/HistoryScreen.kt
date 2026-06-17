package com.nexora.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexora.player.R
import com.nexora.player.data.local.PlaybackHistoryEntity
import com.nexora.player.data.model.MediaEntry
import com.nexora.player.data.model.MediaKind
import com.nexora.player.ui.components.MediaArtwork
import com.nexora.player.ui.components.formatDuration
import java.text.DateFormat
import java.util.Date

private enum class HistorySortMode {
    MOST_PLAYED,
    RECENT,
    TITLE
}

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    history: List<PlaybackHistoryEntity>,
    onPlayItem: (PlaybackHistoryEntity) -> Unit
) {
    var sortMode by remember { mutableStateOf(HistorySortMode.MOST_PLAYED) }

    val grouped = remember(history) {
        history
            .groupBy { it.mediaId to it.mediaKind }
            .map { (_, entries) ->
                val latest = entries.maxByOrNull { it.playedAt } ?: entries.first()
                HistorySummary(
                    latest = latest,
                    playCount = entries.size,
                    firstPlayedAt = entries.minOf { it.playedAt },
                    lastPlayedAt = latest.playedAt
                )
            }
    }

    val ordered = remember(grouped, sortMode) {
        when (sortMode) {
            HistorySortMode.MOST_PLAYED -> grouped.sortedWith(
                compareByDescending<HistorySummary> { it.playCount }
                    .thenByDescending { it.lastPlayedAt }
                    .thenBy { it.entry.title.lowercase() }
            )
            HistorySortMode.RECENT -> grouped.sortedWith(
                compareByDescending<HistorySummary> { it.lastPlayedAt }
                    .thenByDescending { it.playCount }
                    .thenBy { it.entry.title.lowercase() }
            )
            HistorySortMode.TITLE -> grouped.sortedBy { it.entry.title.lowercase() }
        }
    }

    val totalPlays = history.size
    val uniqueTracks = grouped.size
    val topTrack = ordered.firstOrNull()

    Column(modifier = modifier.fillMaxSize()) {
        ElevatedCard(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.nav_history),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (topTrack != null) {
                                "${topTrack.entry.title} · ${topTrack.playCount} reproducciones"
                            } else {
                                "Tu historial de reproducción"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text("$totalPlays reproducciones") })
                    AssistChip(onClick = {}, label = { Text("$uniqueTracks canciones") })
                }

                HorizontalDivider()

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = sortMode == HistorySortMode.MOST_PLAYED,
                        onClick = { sortMode = HistorySortMode.MOST_PLAYED },
                        label = { Text("Más reproducidas") }
                    )
                    FilterChip(
                        selected = sortMode == HistorySortMode.RECENT,
                        onClick = { sortMode = HistorySortMode.RECENT },
                        label = { Text("Recientes") }
                    )
                    FilterChip(
                        selected = sortMode == HistorySortMode.TITLE,
                        onClick = { sortMode = HistorySortMode.TITLE },
                        label = { Text("Título") }
                    )
                }
            }
        }

        if (ordered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Aún no hay historial",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Las canciones que reproduzcas aparecerán aquí con su portada y contador.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(ordered, key = { it.key }) { summary ->
                    HistoryTrackCard(
                        summary = summary,
                        onPlay = { onPlayItem(summary.latest) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryTrackCard(
    summary: HistorySummary,
    onPlay: () -> Unit
) {
    val item = summary.entry

    ElevatedCard(
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(84.dp)) {
                MediaArtwork(
                    item = item,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.large)
                )
                Surface(
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "x${summary.playCount}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val subtitle = buildString {
                    if (item.artist.isNotBlank()) append(item.artist)
                    if (item.album.isNotBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(item.album)
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = onPlay,
                        label = { Text(formatDuration(item.durationMs)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null
                            )
                        }
                    )
                    AssistChip(
                        onClick = onPlay,
                        label = { Text(DateFormat.getDateInstance().format(Date(summary.lastPlayedAt))) }
                    )
                }
            }

            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Reproducir"
                )
            }
        }
    }
}

private data class HistorySummary(
    val latest: PlaybackHistoryEntity,
    val playCount: Int,
    val firstPlayedAt: Long,
    val lastPlayedAt: Long
) {
    val entry: MediaEntry = latest.toMediaEntry()
    val key: String = "${latest.mediaKind}:${latest.mediaId}"
}

private fun PlaybackHistoryEntity.toMediaEntry() = MediaEntry(
    id = mediaId,
    kind = if (mediaKind == MediaKind.VIDEO.name) MediaKind.VIDEO else MediaKind.AUDIO,
    uri = android.net.Uri.parse(uriString),
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs
)
