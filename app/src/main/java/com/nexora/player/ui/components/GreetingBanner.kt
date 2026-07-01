package com.nexora.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexora.player.R
import com.nexora.player.data.model.SortMode
import java.util.Calendar

@Composable
fun GreetingBanner(
    greeting: String,
    query: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    sortMode: SortMode? = null,
    onSortSelected: (SortMode) -> Unit = {},
    hasUnreadNotifications: Boolean = false,
    onNotificationsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val mood = remember(hour) { GreetingMood.fromHour(hour) }
    val cardBrush = Brush.linearGradient(
        listOf(
            mood.start.copy(alpha = 0.30f),
            mood.end.copy(alpha = 0.14f),
            MaterialTheme.colorScheme.surface
        )
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        shape = RoundedCornerShape(34.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBrush)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(50.dp),
                    shape = CircleShape,
                    color = mood.start.copy(alpha = 0.20f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            mood.icon,
                            contentDescription = null,
                            tint = mood.start,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                if (expanded) {
                    SearchField(
                        query = query,
                        expanded = true,
                        onExpandedChange = onExpandedChange,
                        onQueryChange = onQueryChange,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = {
                        if (expanded) {
                            onQueryChange("")
                            onExpandedChange(false)
                        } else {
                            onExpandedChange(true)
                        }
                    }
                ) {
                    Icon(
                        if (expanded) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = stringResource(if (expanded) R.string.search_close else R.string.search_open)
                    )
                }

                FilledTonalIconButton(onClick = onNotificationsClick) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = stringResource(R.string.notifications_title)
                        )
                        if (hasUnreadNotifications) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(9.dp),
                                shape = CircleShape,
                                color = Color(0xFFFF3B30)
                            ) {}
                        }
                    }
                }

                FilledTonalIconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.nav_settings)
                    )
                }
            }

            AnimatedVisibility(visible = !expanded && sortMode != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(mood.captionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    SortSelector(
                        selected = sortMode ?: SortMode.DATE_ADDED_DESC,
                        options = listOf(
                            SortMode.DATE_ADDED_DESC, SortMode.DATE_ADDED_ASC,
                            SortMode.TITLE_ASC, SortMode.TITLE_DESC,
                            SortMode.DURATION_ASC, SortMode.DURATION_DESC,
                            SortMode.ARTIST_ASC, SortMode.ALBUM_ASC,
                            SortMode.FOLDER_ASC, SortMode.FOLDER_DESC
                        ),
                        onSelected = onSortSelected
                    )
                }
            }
        }
    }
}

private data class GreetingMood(
    val icon: ImageVector,
    val start: Color,
    val end: Color,
    @androidx.annotation.StringRes val captionRes: Int
) {
    companion object {
        fun fromHour(hour: Int): GreetingMood = when (hour) {
            in 5..11 -> GreetingMood(
                icon = Icons.Filled.WbSunny,
                start = Color(0xFFFFB020),
                end = Color(0xFFFFE6A3),
                captionRes = R.string.greeting_caption_morning
            )
            in 12..18 -> GreetingMood(
                icon = Icons.Filled.WbSunny,
                start = Color(0xFFFF6B35),
                end = Color(0xFFFFB199),
                captionRes = R.string.greeting_caption_afternoon
            )
            else -> GreetingMood(
                icon = Icons.Filled.Brightness4,
                start = Color(0xFF7C3AED),
                end = Color(0xFF1D4ED8),
                captionRes = R.string.greeting_caption_evening
            )
        }
    }
}
