package com.nexora.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexora.player.R
import com.nexora.player.data.model.MediaEntry
import com.nexora.player.ui.components.MediaItemRow

@Composable
fun QueueScreen(
    modifier: Modifier = Modifier,
    queue: List<MediaEntry>,
    currentIndex: Int,
    onPlayItem: (Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onClearQueue: () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.queue_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onClearQueue, enabled = queue.isNotEmpty()) { Text(stringResource(R.string.clear_queue)) }
        }

        if (queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Text(stringResource(R.string.queue_empty), style = MaterialTheme.typography.bodyLarge)
            }
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(queue, key = { _, item -> item.id }) { index, item ->
                ElevatedCard {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        if (index == currentIndex) {
                            AssistChip(onClick = { onPlayItem(index) }, label = { Text(stringResource(R.string.queue_playing)) })
                        }
                        MediaItemRow(
                            item = item,
                            onClick = { onPlayItem(index) }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onPlayItem(index) }) { Text(stringResource(R.string.queue_go_to)) }
                            TextButton(onClick = { onRemoveItem(index) }) { Text(stringResource(R.string.queue_remove)) }
                        }
                    }
                }
            }
        }
    }
}
