package com.nexora.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nexora.player.data.online.OnlineTrack

@Composable
fun OnlineTrackCard(
    track: OnlineTrack,
    isSaved: Boolean,
    onPlay: () -> Unit,
    onSaveToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                if (!track.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = track.artworkUrl,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth(0.68f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = buildString {
                    if (track.artist.isNotBlank()) append(track.artist)
                    if (track.album.isNotBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(track.album)
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = track.providerLabel,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                FilledTonalIconButton(onClick = onPlay, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                }
                FilledTonalIconButton(onClick = onSaveToggle, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = null
                    )
                }
            }
        }
    }
}
