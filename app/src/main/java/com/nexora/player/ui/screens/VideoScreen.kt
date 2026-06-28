package com.nexora.player.ui.screens

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexora.player.data.model.MediaEntry
import com.nexora.player.data.model.SortMode
import com.nexora.player.ui.components.MediaArtwork
import com.nexora.player.ui.components.SortSelector
import com.nexora.player.ui.components.formatDuration

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoScreen(
    modifier: Modifier = Modifier,
    items: List<MediaEntry>,
    sortMode: SortMode,
    onPlay: (List<MediaEntry>, MediaEntry) -> Unit,
    onRefresh: () -> Unit,
    onSortSelected: (SortMode) -> Unit,
    onEditVideo: (MediaEntry) -> Unit = {},
    onHideVideo: (MediaEntry) -> Unit = {},
    onDeleteVideo: (MediaEntry) -> Unit = {}
) {
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<MediaEntry?>(null) }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val target = pendingDelete
        pendingDelete = null
        if (result.resultCode == Activity.RESULT_OK && target != null) {
            onDeleteVideo(target)
            onRefresh()
        }
    }

    fun requestDelete(item: MediaEntry) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val request = MediaStore.createDeleteRequest(context.contentResolver, listOf(item.uri))
            pendingDelete = item
            deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } else {
            runCatching { context.contentResolver.delete(item.uri, null, null) }
            onDeleteVideo(item)
            onRefresh()
        }
    }

    fun shareVideo(item: MediaEntry) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType ?: "video/*"
            putExtra(Intent.EXTRA_STREAM, item.uri)
            putExtra(Intent.EXTRA_TEXT, item.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Compartir video")) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = false,
                onClick = onRefresh,
                label = { Text("Actualizar") }
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
                    SortMode.SIZE_ASC,
                    SortMode.SIZE_DESC,
                    SortMode.RESOLUTION_ASC,
                    SortMode.RESOLUTION_DESC
                ),
                onSelected = onSortSelected
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items, key = { it.id }) { item ->
                VideoGridCard(
                    item = item,
                    onPlay = { onPlay(items, item) },
                    onShare = { shareVideo(item) },
                    onEdit = { onEditVideo(item) },
                    onHide = { onHideVideo(item) },
                    onDelete = { requestDelete(item) }
                )
            }
        }
    }
}

@Composable
private fun VideoGridCard(
    item: MediaEntry,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box {
                MediaArtwork(
                    item = item,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Opciones",
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Compartir") },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Editar archivo") },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Ocultar video") },
                            leadingIcon = { Icon(Icons.Filled.VisibilityOff, null) },
                            onClick = {
                                menuExpanded = false
                                onHide()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Borrar archivo") },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2
                )
                Text(
                    text = item.resolutionLabel,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = formatDuration(item.durationMs),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
