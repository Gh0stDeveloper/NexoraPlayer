package com.nexora.player.ui.screens

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexora.player.R
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
    var editTarget by remember { mutableStateOf<MediaEntry?>(null) }
    var pendingVideoRename by remember { mutableStateOf<PendingVideoRename?>(null) }

    fun applyVideoRename(item: MediaEntry, rawTitle: String): Boolean {
        val safeTitle = sanitizeVideoTitle(rawTitle)
        if (safeTitle.isBlank()) return false
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(item.mimeType ?: "video/mp4")
            ?: "mp4"
        val displayName = "$safeTitle.$extension"
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.TITLE, safeTitle)
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
            }
            context.contentResolver.update(item.uri, values, null, null) > 0
        }.getOrDefault(false)
    }

    val editLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val pending = pendingVideoRename
        pendingVideoRename = null
        if (result.resultCode == Activity.RESULT_OK && pending != null) {
            val saved = applyVideoRename(pending.item, pending.title)
            if (saved) {
                onRefresh()
            } else {
                onEditVideo(pending.item)
            }
        }
    }

    fun requestVideoRename(item: MediaEntry, title: String) {
        val safeTitle = sanitizeVideoTitle(title)
        if (safeTitle.isBlank()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val request = MediaStore.createWriteRequest(context.contentResolver, listOf(item.uri))
            pendingVideoRename = PendingVideoRename(item, safeTitle)
            editLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } else {
            val saved = applyVideoRename(item, safeTitle)
            if (saved) {
                onRefresh()
            } else {
                onEditVideo(item)
            }
        }
    }

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
        runCatching { context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_video))) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = false,
                onClick = onRefresh,
                label = { Text(stringResource(R.string.action_refresh)) }
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
                    onEdit = { editTarget = item },
                    onHide = { onHideVideo(item) },
                    onDelete = { requestDelete(item) }
                )
            }
        }
    }

    editTarget?.let { target ->
        VideoRenameDialog(
            item = target,
            onDismiss = { editTarget = null },
            onSave = { title ->
                requestVideoRename(target, title)
                editTarget = null
            }
        )
    }
}

private data class PendingVideoRename(
    val item: MediaEntry,
    val title: String
)

@Composable
private fun VideoRenameDialog(
    item: MediaEntry,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var title by remember(item.id) { mutableStateOf(item.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.video_edit_file)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text(stringResource(R.string.video_title_hint)) }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title) },
                enabled = sanitizeVideoTitle(title).isNotBlank()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

private fun sanitizeVideoTitle(raw: String): String {
    return raw.trim().replace(Regex("""[\\/:*?"<>|]+"""), "_")
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
                            contentDescription = stringResource(R.string.video_options_content),
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.music_share)) },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.video_edit_file)) },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.video_hide)) },
                            leadingIcon = { Icon(Icons.Filled.VisibilityOff, null) },
                            onClick = {
                                menuExpanded = false
                                onHide()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.video_delete_file)) },
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
