package com.nexora.player.ui.screens

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexora.player.data.editor.AudioFileEditor
import com.nexora.player.data.model.MediaEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

// ── Edit dialog ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAudioDialog(
    item: MediaEntry,
    customArtworkUri: Uri?,
    /**
     * Called when save is complete.
     * [artworkUri] = new custom artwork file:// URI, or Uri.EMPTY to clear, or null if unchanged.
     */
    onSave: (title: String, artist: String, album: String, artworkUri: Uri?) -> Unit,
    onDismiss: () -> Unit
) {
    val context       = LocalContext.current
    val scope         = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }

    // Editable metadata fields
    var newTitle  by remember(item.id) { mutableStateOf(item.title) }
    var newArtist by remember(item.id) { mutableStateOf(item.artist) }
    var newAlbum  by remember(item.id) { mutableStateOf(item.album) }

    // Artwork state
    var pendingBitmap  by remember { mutableStateOf<Bitmap?>(null) }  // awaiting crop
    var croppedBitmap  by remember { mutableStateOf<Bitmap?>(null) }  // ready to embed
    var removeArtwork  by remember { mutableStateOf(false) }
    var showCropDialog by remember { mutableStateOf(false) }
    var isSaving       by remember { mutableStateOf(false) }
    var saveError      by remember { mutableStateOf<String?>(null) }

    // Pending edit waiting for write permission (Android 11+)
    var pendingEdit by remember { mutableStateOf<AudioFileEditor.PreparedEdit?>(null) }
    var pendingArtworkUri by remember { mutableStateOf<Uri?>(null) }

    // Current artwork preview
    val currentArtwork by produceState<Bitmap?>(null, item.id, customArtworkUri, croppedBitmap, removeArtwork) {
        value = withContext(Dispatchers.IO) {
            when {
                removeArtwork  -> null
                croppedBitmap != null -> croppedBitmap
                else -> loadCurrentArtwork(context, item, customArtworkUri)
            }
        }
    }

    // Show snackbar on save error
    LaunchedEffect(saveError) {
        saveError?.let {
            snackbarState.showSnackbar(it)
            saveError = null
        }
    }

    // ── Image picker ──────────────────────────────────────────────────────────

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val bmp = runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            }.getOrNull()
            if (bmp != null) {
                withContext(Dispatchers.Main) {
                    pendingBitmap  = bmp
                    showCropDialog = true
                }
            }
        }
    }

    // ── Write permission launcher (Android 11+) ───────────────────────────────

    val writePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val edit = pendingEdit ?: return@rememberLauncherForActivityResult
        val artUri = pendingArtworkUri

        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                val commitResult = AudioFileEditor.commitEdit(context, item.uri, edit)
                isSaving       = false
                pendingEdit    = null
                pendingArtworkUri = null

                when (commitResult) {
                    is AudioFileEditor.EditResult.Success ->
                        onSave(edit.title, edit.artist, edit.album, artUri)

                    is AudioFileEditor.EditResult.Error -> {
                        saveError = "Error al guardar: ${commitResult.message}"
                    }

                    else -> Unit
                }
            }
        } else {
            edit.tempFile.delete()
            pendingEdit = null
            pendingArtworkUri = null
            isSaving    = false
            saveError   = "El usuario canceló la operación de escritura."
        }
    }

    // ── Save handler ──────────────────────────────────────────────────────────

    fun save() {
        isSaving = true
        scope.launch {
            val artBitmap = if (removeArtwork) null else croppedBitmap

            // Save cropped artwork to internal storage first (if any)
            val artworkUri: Uri? = when {
                removeArtwork       -> Uri.EMPTY
                artBitmap != null   -> saveBitmapToInternalStorage(context, artBitmap, item.id)
                else                -> customArtworkUri
            }

            // Ask AudioFileEditor to prepare the edit (copies + tags the file)
            val editResult = AudioFileEditor.prepareAndEdit(
                context       = context,
                uri           = item.uri,
                title         = newTitle.trim(),
                artist        = newArtist.trim(),
                album         = newAlbum.trim(),
                artworkBitmap = artBitmap
            )

            when (editResult) {
                is AudioFileEditor.EditResult.Success -> {
                    isSaving = false
                    onSave(newTitle.trim(), newArtist.trim(), newAlbum.trim(), artworkUri)
                }

                is AudioFileEditor.EditResult.NeedsWritePermission -> {
                    // Store pending data; launch system permission dialog
                    pendingEdit       = editResult.prepared
                    pendingArtworkUri = artworkUri
                    writePermLauncher.launch(
                        IntentSenderRequest.Builder(editResult.intentSender).build()
                    )
                    // isSaving stays true until the launcher callback
                }

                is AudioFileEditor.EditResult.Error -> {
                    isSaving  = false
                    saveError = "No se pudo editar el archivo: ${editResult.message}"
                }
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color    = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                Column(modifier = Modifier.fillMaxSize()) {

                    // Header
                    Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = onDismiss, enabled = !isSaving) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancelar")
                            }
                            Text(
                                "Editar canción",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            IconButton(onClick = { save() }, enabled = !isSaving) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.Check, "Guardar",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Body
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {

                        // Artwork section
                        Column(
                            modifier            = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Portada",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )

                            // Artwork preview — tap to change
                            Box(
                                modifier         = Modifier
                                    .size(180.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { imagePicker.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                val bmp = currentArtwork
                                if (bmp != null) {
                                    Image(
                                        bitmap             = bmp.asImageBitmap(),
                                        contentDescription = "Portada",
                                        contentScale       = ContentScale.Crop,
                                        modifier           = Modifier.fillMaxSize()
                                    )
                                    // Camera overlay hint
                                    Box(
                                        modifier         = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.28f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.CameraAlt, "Cambiar portada",
                                            tint     = Color.White,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.CameraAlt, null,
                                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Text(
                                            "Toca para añadir portada",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { imagePicker.launch("image/*") }) {
                                    Icon(Icons.Filled.CameraAlt, null,
                                        modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Cambiar portada")
                                }
                                if (currentArtwork != null) {
                                    TextButton(
                                        onClick = {
                                            croppedBitmap = null
                                            removeArtwork = true
                                        }
                                    ) {
                                        Icon(Icons.Filled.Delete, null,
                                            modifier = Modifier.size(16.dp),
                                            tint     = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Quitar portada",
                                            color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        // Metadata fields
                        Surface(
                            shape          = RoundedCornerShape(16.dp),
                            color          = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    "Información del archivo",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Text(
                                    "Los cambios se escribirán directamente en el archivo de audio.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value         = newTitle,
                                    onValueChange = { newTitle = it },
                                    label         = { Text("Título") },
                                    modifier      = Modifier.fillMaxWidth(),
                                    shape         = RoundedCornerShape(12.dp),
                                    singleLine    = true,
                                    enabled       = !isSaving
                                )
                                OutlinedTextField(
                                    value         = newArtist,
                                    onValueChange = { newArtist = it },
                                    label         = { Text("Artista") },
                                    modifier      = Modifier.fillMaxWidth(),
                                    shape         = RoundedCornerShape(12.dp),
                                    singleLine    = true,
                                    enabled       = !isSaving
                                )
                                OutlinedTextField(
                                    value         = newAlbum,
                                    onValueChange = { newAlbum = it },
                                    label         = { Text("Álbum") },
                                    modifier      = Modifier.fillMaxWidth(),
                                    shape         = RoundedCornerShape(12.dp),
                                    singleLine    = true,
                                    enabled       = !isSaving
                                )
                            }
                        }

                        Button(
                            onClick  = { save() },
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(14.dp),
                            enabled  = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color       = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Filled.Check, null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (isSaving) "Guardando..." else "Guardar cambios",
                                style = MaterialTheme.typography.labelLarge)
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }

                // Snackbar for errors
                SnackbarHost(
                    hostState = snackbarState,
                    modifier  = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    // Crop dialog
    if (showCropDialog && pendingBitmap != null) {
        CropImageDialog(
            sourceBitmap = pendingBitmap!!,
            onConfirm    = { cropped ->
                croppedBitmap  = cropped
                removeArtwork  = false
                pendingBitmap  = null
                showCropDialog = false
            },
            onDismiss    = {
                pendingBitmap  = null
                showCropDialog = false
            }
        )
    }
}

// ── Crop dialog ───────────────────────────────────────────────────────────────

@Composable
private fun CropImageDialog(
    sourceBitmap: Bitmap,
    onConfirm: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val density       = LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var cropOffset    by remember { mutableStateOf(Offset.Zero) }
    var cropSizePx    by remember { mutableStateOf(0f) }

    LaunchedEffect(containerSize) {
        if (containerSize.width > 0 && containerSize.height > 0) {
            val initial = minOf(containerSize.width, containerSize.height) * 0.72f
            cropSizePx = initial
            cropOffset = Offset(
                x = (containerSize.width  - initial) / 2f,
                y = (containerSize.height - initial) / 2f
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0A0A)) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancelar",
                            tint = Color.White)
                    }
                    Text(
                        "Recortar portada",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.White
                    )
                    TextButton(onClick = {
                        if (containerSize.width <= 0 || cropSizePx <= 0) return@TextButton

                        // Map screen crop square → original bitmap coordinates
                        val imgAspect = sourceBitmap.width.toFloat() / sourceBitmap.height
                        val ctnAspect = containerSize.width.toFloat() / containerSize.height

                        val (imgLeft, imgTop, imgW, imgH) = if (imgAspect > ctnAspect) {
                            val w = containerSize.width.toFloat()
                            val h = w / imgAspect
                            listOf(0f, (containerSize.height - h) / 2f, w, h)
                        } else {
                            val h = containerSize.height.toFloat()
                            val w = h * imgAspect
                            listOf((containerSize.width - w) / 2f, 0f, w, h)
                        }

                        val scaleX = sourceBitmap.width  / imgW
                        val scaleY = sourceBitmap.height / imgH
                        val scale  = (scaleX + scaleY) / 2f

                        val bmpX    = ((cropOffset.x - imgLeft) * scaleX).roundToInt()
                        val bmpY    = ((cropOffset.y - imgTop)  * scaleY).roundToInt()
                        val bmpSize = (cropSizePx * scale).roundToInt()

                        val safeX    = bmpX.coerceIn(0, sourceBitmap.width - 1)
                        val safeY    = bmpY.coerceIn(0, sourceBitmap.height - 1)
                        val safeSize = bmpSize
                            .coerceAtLeast(1)
                            .coerceAtMost(minOf(sourceBitmap.width - safeX,
                                                sourceBitmap.height - safeY))

                        if (safeSize > 0) {
                            onConfirm(Bitmap.createBitmap(sourceBitmap, safeX, safeY, safeSize, safeSize))
                        }
                    }) {
                        Text("Recortar",
                            color = Color(0xFF007AFF),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ))
                    }
                }

                // Image + interactive crop overlay
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onSizeChanged { containerSize = it },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap             = sourceBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier.fillMaxSize()
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Dark overlay with hole for crop area
                        val holePath = Path().apply {
                            addRect(Rect(cropOffset, Size(cropSizePx, cropSizePx)))
                        }
                        clipPath(holePath, clipOp = ClipOp.Difference) {
                            drawRect(color = Color.Black.copy(alpha = 0.62f))
                        }
                        // White border
                        drawRect(
                            color   = Color.White,
                            topLeft = cropOffset,
                            size    = Size(cropSizePx, cropSizePx),
                            style   = Stroke(width = 2.dp.toPx())
                        )
                        // Corner handles
                        val hLen = 18.dp.toPx()
                        listOf(
                            cropOffset to listOf(Offset(1f, 0f), Offset(0f, 1f)),
                            Offset(cropOffset.x + cropSizePx, cropOffset.y) to
                                listOf(Offset(-1f, 0f), Offset(0f, 1f)),
                            Offset(cropOffset.x, cropOffset.y + cropSizePx) to
                                listOf(Offset(1f, 0f), Offset(0f, -1f)),
                            Offset(cropOffset.x + cropSizePx, cropOffset.y + cropSizePx) to
                                listOf(Offset(-1f, 0f), Offset(0f, -1f))
                        ).forEach { (corner, dirs) ->
                            dirs.forEach { dir ->
                                drawLine(Color.White, corner,
                                    corner + Offset(dir.x * hLen, dir.y * hLen),
                                    strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                            }
                        }
                        // Grid of thirds
                        val g = Color.White.copy(alpha = 0.25f)
                        for (i in 1..2) {
                            drawLine(g,
                                Offset(cropOffset.x + cropSizePx / 3f * i, cropOffset.y),
                                Offset(cropOffset.x + cropSizePx / 3f * i, cropOffset.y + cropSizePx),
                                0.8.dp.toPx())
                            drawLine(g,
                                Offset(cropOffset.x, cropOffset.y + cropSizePx / 3f * i),
                                Offset(cropOffset.x + cropSizePx, cropOffset.y + cropSizePx / 3f * i),
                                0.8.dp.toPx())
                        }
                    }

                    // Drag surface
                    if (containerSize.width > 0 && cropSizePx > 0) {
                        val cropDp = with(density) { cropSizePx.toDp() }
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(cropOffset.x.roundToInt(), cropOffset.y.roundToInt())
                                }
                                .size(cropDp)
                                .pointerInput(containerSize, cropSizePx) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        cropOffset = Offset(
                                            (cropOffset.x + drag.x)
                                                .coerceIn(0f, containerSize.width  - cropSizePx),
                                            (cropOffset.y + drag.y)
                                                .coerceIn(0f, containerSize.height - cropSizePx)
                                        )
                                    }
                                }
                        )
                    }
                }

                Text(
                    "Arrastra el recuadro para elegir el área de recorte",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = Color.White.copy(alpha = 0.60f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun loadCurrentArtwork(context: Context, item: MediaEntry, customUri: Uri?): Bitmap? {
    if (customUri != null && customUri != Uri.EMPTY) {
        runCatching {
            context.contentResolver.openInputStream(customUri)?.use {
                return BitmapFactory.decodeStream(it)
            }
        }
    }
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

private suspend fun saveBitmapToInternalStorage(
    context: Context,
    bitmap: Bitmap,
    mediaId: Long
): Uri = withContext(Dispatchers.IO) {
    val dir  = File(context.filesDir, "custom_artwork").also { it.mkdirs() }
    val file = File(dir, "artwork_$mediaId.jpg")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    Uri.fromFile(file)
}
