package com.nexora.player.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexora.player.R
import com.nexora.player.data.local.PlaylistEntity
import com.nexora.player.data.model.MediaEntry
import com.nexora.player.data.model.SortMode
import com.nexora.player.ui.components.MediaItemRow
import com.nexora.player.ui.components.SortSelector
import com.nexora.player.ui.components.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── Main screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    modifier: Modifier = Modifier,
    items: List<MediaEntry>,
    favorites: Set<Long>,
    playlists: List<PlaylistEntity>,
    sortMode: SortMode,
    // mediaId → custom artwork URI chosen by the user
    customArtworks: Map<Long, Uri> = emptyMap(),
    onPlay: (List<MediaEntry>, MediaEntry) -> Unit,
    onToggleFavorite: (MediaEntry) -> Unit,
    onAddToPlaylist: (PlaylistEntity, MediaEntry) -> Unit,
    onHideFromLibrary: (MediaEntry) -> Unit,
    onDeleteFromLibrary: (MediaEntry) -> Unit,
    onRefresh: () -> Unit,
    onSortSelected: (SortMode) -> Unit,
    // New optional callbacks — default no-op so existing callers still compile
    onSetCustomArtwork: (MediaEntry, Uri) -> Unit = { _, _ -> },
    onPlayNext: (MediaEntry) -> Unit = {},
    onAddToQueue: (MediaEntry) -> Unit = {},
    onSaveMetadata: (MediaEntry, String, String, String) -> Unit = { _, _, _, _ -> }
) {
    var selectedItem    by remember { mutableStateOf<MediaEntry?>(null) }
    var deleteCandidate by remember { mutableStateOf<MediaEntry?>(null) }
    var showEditDialog  by remember { mutableStateOf(false) }
    val context         = LocalContext.current
    val sheetState      = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    // ── Launchers ─────────────────────────────────────────────────────────────

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        runCatching {
            val item = deleteCandidate
            if (result.resultCode == Activity.RESULT_OK && item != null) {
                onDeleteFromLibrary(item)
            }
        }
        deleteCandidate = null
        selectedItem    = null
    }

    // ── Screen layout ─────────────────────────────────────────────────────────

    Column(modifier = modifier.fillMaxSize()) {

        // Header bar with title + sort controls
        Surface(
            modifier        = Modifier.fillMaxWidth(),
            tonalElevation  = 2.dp,
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.music_library_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    stringResource(R.string.music_library_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = false,
                        onClick  = onRefresh,
                        label    = { Text(stringResource(R.string.refresh)) }
                    )
                    SortSelector(
                        selected   = sortMode,
                        options    = listOf(
                            SortMode.DATE_ADDED_DESC, SortMode.DATE_ADDED_ASC,
                            SortMode.TITLE_ASC,       SortMode.TITLE_DESC,
                            SortMode.DURATION_ASC,    SortMode.DURATION_DESC,
                            SortMode.ARTIST_ASC,      SortMode.ALBUM_ASC,
                            SortMode.FOLDER_ASC,      SortMode.FOLDER_DESC
                        ),
                        onSelected = onSortSelected
                    )
                }
            }
        }

        // Empty state or song list
        if (items.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier            = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Filled.MusicNote, null,
                        modifier = Modifier.size(56.dp),
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.no_visible_music),
                        style     = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        stringResource(R.string.hidden_tracks_restore_hint),
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier        = Modifier.fillMaxSize(),
                contentPadding  = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    MediaItemRow(
                        item            = item,
                        isFavorite      = favorites.contains(item.id),
                        onClick         = { onPlay(items, item) },
                        onFavoriteClick = { onToggleFavorite(item) },
                        onMoreClick     = { selectedItem = item }
                    )
                }
            }
        }
    }

    // ── iOS-style action sheet ────────────────────────────────────────────────

    selectedItem?.let { item ->
        val customUri = customArtworks[item.id]

        ModalBottomSheet(
            onDismissRequest = { selectedItem = null },
            sheetState       = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                // Song header: thumbnail + title + artist + duration
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    ArtworkThumbnail(
                        item      = item,
                        customUri = customUri,
                        modifier  = Modifier.size(64.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.title,
                            style    = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.artist.isNotBlank()) {
                            Text(
                                item.artist,
                                style    = MaterialTheme.typography.bodySmall,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            formatDuration(item.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }
                }

                // Quick action pills: Favorite · Share · Hide
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickPill(
                        icon     = if (favorites.contains(item.id)) Icons.Filled.Favorite
                                   else Icons.Filled.FavoriteBorder,
                        label    = if (favorites.contains(item.id)) "Favorito" else "Me gusta",
                        tint     = if (favorites.contains(item.id)) Color(0xFFFF3B30)
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        onClick  = { onToggleFavorite(item); selectedItem = null }
                    )
                    QuickPill(
                        icon     = Icons.Filled.Share,
                        label    = "Compartir",
                        tint     = Color(0xFF007AFF),
                        modifier = Modifier.weight(1f),
                        onClick  = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, item.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(intent, "Compartir ${item.title}")
                            )
                            selectedItem = null
                        }
                    )
                    QuickPill(
                        icon     = Icons.Filled.VisibilityOff,
                        label    = "Ocultar",
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        onClick  = { onHideFromLibrary(item); selectedItem = null }
                    )
                }

                // Section: Reproducción
                SheetSection("REPRODUCCIÓN") {
                    SheetRow(
                        icon     = Icons.Filled.SkipNext,
                        title    = "Reproducir siguiente",
                        subtitle = "Se añadirá después de la canción actual",
                        onClick  = { onPlayNext(item); selectedItem = null }
                    )
                    SheetDivider()
                    SheetRow(
                        icon     = Icons.Filled.QueueMusic,
                        title    = "Agregar a la cola",
                        subtitle = "Se pondrá al final de la lista",
                        onClick  = { onAddToQueue(item); selectedItem = null }
                    )
                }

                // Section: Editar — opens full edit dialog
                SheetSection("EDITAR") {
                    SheetRow(
                        icon        = Icons.Filled.Edit,
                        title       = "Editar canción",
                        subtitle    = "Cambia nombre, artista, álbum y portada",
                        showChevron = true,
                        onClick     = { showEditDialog = true }
                    )
                }

                // Section: Playlists
                if (playlists.isNotEmpty()) {
                    SheetSection("AGREGAR A PLAYLIST") {
                        playlists.forEachIndexed { idx, playlist ->
                            SheetRow(
                                icon    = Icons.Filled.PlaylistAdd,
                                title   = playlist.name,
                                trailingContent = {
                                    Icon(
                                        Icons.Filled.PlaylistAdd, null,
                                        tint     = Color(0xFF007AFF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    onAddToPlaylist(playlist, item)
                                    selectedItem = null
                                }
                            )
                            if (idx < playlists.lastIndex) SheetDivider()
                        }
                    }
                }

                // Section: Información inline
                SheetSection("INFORMACIÓN") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        InfoLine("Título",   item.title.ifBlank { "Desconocido" })
                        InfoLine("Artista",  item.artist.ifBlank { "Desconocido" })
                        InfoLine("Álbum",    item.album.ifBlank { "Sin álbum" })
                        InfoLine("Duración", formatDuration(item.durationMs))
                        item.folder?.takeIf { it.isNotBlank() }?.let {
                            InfoLine("Carpeta", it)
                        }
                    }
                }

                // Section: Archivo (destructive zone)
                SheetSection("ARCHIVO") {
                    SheetRow(
                        icon          = Icons.Filled.Delete,
                        title         = "Eliminar del dispositivo",
                        subtitle      = "Esta acción no se puede deshacer",
                        isDestructive = true,
                        onClick       = { deleteCandidate = item }
                    )
                }

                TextButton(
                    onClick  = { selectedItem = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.close),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }

    // ── Delete confirmation dialog ────────────────────────────────────────────

    deleteCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            icon = {
                Box(
                    modifier         = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Delete, null,
                        tint     = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    "Eliminar canción",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Song info preview
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                item.title,
                                style    = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.artist.isNotBlank()) {
                                Text(
                                    item.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                formatDuration(item.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.65f)
                            )
                        }
                    }
                    Text(
                        "El archivo se borrará permanentemente del dispositivo y se eliminarán todos sus datos de la app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            runCatching {
                                val req = MediaStore.createDeleteRequest(
                                    context.contentResolver, listOf(item.uri)
                                )
                                deleteLauncher.launch(
                                    IntentSenderRequest.Builder(req.intentSender).build()
                                )
                            }.onFailure {
                                deleteCandidate = null
                                selectedItem    = null
                            }
                        } else {
                            runCatching {
                                onDeleteFromLibrary(item)
                            }
                            deleteCandidate = null
                            selectedItem    = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape  = RoundedCornerShape(12.dp)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(R.string.close))
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── Edit audio dialog ─────────────────────────────────────────────────────

    if (showEditDialog && selectedItem != null) {
        val editItem = selectedItem!!
        EditAudioDialog(
            item             = editItem,
            customArtworkUri = customArtworks[editItem.id],
            onSave           = { title, artist, album, artworkUri ->
                onSaveMetadata(editItem, title, artist, album)
                when {
                    artworkUri != null && artworkUri != Uri.EMPTY ->
                        onSetCustomArtwork(editItem, artworkUri)
                    artworkUri == Uri.EMPTY ->
                        onSetCustomArtwork(editItem, Uri.EMPTY)
                }
                showEditDialog = false
                selectedItem   = null
            },
            onDismiss = { showEditDialog = false }
        )
    }
}

// ── Artwork thumbnail ─────────────────────────────────────────────────────────

@Composable
private fun ArtworkThumbnail(
    item: MediaEntry,
    customUri: Uri?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, item.id, customUri) {
        value = withContext(Dispatchers.IO) {
            if (customUri != null && customUri != Uri.EMPTY) {
                runCatching {
                    context.contentResolver.openInputStream(customUri)?.use {
                        BitmapFactory.decodeStream(it)?.asImageBitmap()
                    }
                }.getOrNull()
            } else {
                loadArtworkBitmap(context, item)?.asImageBitmap()
            }
        }
    }

    Box(
        modifier         = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap             = bitmap!!,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Filled.MusicNote, null,
                tint     = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ── Sheet building blocks ─────────────────────────────────────────────────────

@Composable
private fun SheetSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style    = MaterialTheme.typography.labelSmall.copy(
                fontWeight    = FontWeight.Medium,
                letterSpacing = 0.7.sp
            ),
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Surface(
            shape    = RoundedCornerShape(14.dp),
            color    = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    isDestructive: Boolean = false,
    showChevron: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val contentColor = if (isDestructive) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null,
            tint     = contentColor,
            modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor)
            if (subtitle != null) {
                Text(subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when {
            trailingContent != null -> trailingContent()
            showChevron             -> Icon(
                Icons.Filled.ChevronRight, null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SheetDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(start = 50.dp),
        thickness = 0.5.dp,
        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)
    )
}

@Composable
private fun QuickPill(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(14.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Text(label,
                style     = MaterialTheme.typography.labelSmall,
                color     = tint,
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "$label:",
            style    = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Text(
            value,
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Artwork loader ────────────────────────────────────────────────────────────

private fun loadArtworkBitmap(context: Context, item: MediaEntry): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, item.uri)
        retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

// ── Edit dialog invocation (called from MusicScreen body) ────────────────────
// EditAudioDialog is defined in EditAudioDialog.kt
