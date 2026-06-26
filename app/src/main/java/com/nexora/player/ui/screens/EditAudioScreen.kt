package com.nexora.player.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.ImageBitmap
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
import com.nexora.player.data.model.MediaEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

// ── Main edit dialog ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAudioDialog(
    item: MediaEntry,
    customArtworkUri: Uri?,
    onSave: (title: String, artist: String, album: String, artworkUri: Uri?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // Editable fields — initialised from current metadata
    var newTitle  by remember(item.id) { mutableStateOf(item.title) }
    var newArtist by remember(item.id) { mutableStateOf(item.artist) }
    var newAlbum  by remember(item.id) { mutableStateOf(item.album) }

    // Artwork state
    var pendingBitmap    by remember { mutableStateOf<Bitmap?>(null) }   // picked, awaiting crop
    var croppedBitmap    by remember { mutableStateOf<Bitmap?>(null) }   // cropped, ready to save
    var showCropDialog   by remember { mutableStateOf(false) }
    var isSaving         by remember { mutableStateOf(false) }

    // Load current artwork for preview
    val currentArtwork by produceState<ImageBitmap?>(null, item.id, customArtworkUri, croppedBitmap) {
        value = withContext(Dispatchers.IO) {
            croppedBitmap?.asImageBitmap()
                ?: loadCurrentArtwork(context, item, customArtworkUri)?.asImageBitmap()
        }
    }

    // Image picker launcher
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
                pendingBitmap  = bmp
                showCropDialog = true
            }
        }
    }

    // MediaStore write request launcher (Android 11+)
    var pendingSave by remember { mutableStateOf<ContentValues?>(null) }
    val writeRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        runCatching {
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val cv = pendingSave
                if (cv != null) {
                    context.contentResolver.update(item.uri, cv, null, null)
                }
            }
        }
        pendingSave = null
        isSaving    = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color    = MaterialTheme.colorScheme.background
        ) {
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
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancelar")
                        }
                        Text(
                            "Editar canción",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        IconButton(
                            onClick = {
                                isSaving = true
                                scope.launch {
                                    val artworkUri = croppedBitmap?.let { bmp ->
                                        saveBitmapToInternalStorage(context, bmp, item.id)
                                    } ?: customArtworkUri

                                    // Update MediaStore metadata
                                    val cv = ContentValues().apply {
                                        put(MediaStore.Audio.Media.TITLE, newTitle.trim())
                                        put(MediaStore.Audio.Media.ARTIST, newArtist.trim())
                                        put(MediaStore.Audio.Media.ALBUM, newAlbum.trim())
                                    }

                                    val saved = runCatching {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            val req = MediaStore.createWriteRequest(
                                                context.contentResolver, listOf(item.uri)
                                            )
                                            pendingSave = cv
                                            writeRequestLauncher.launch(
                                                IntentSenderRequest.Builder(req.intentSender).build()
                                            )
                                            false // will finish in launcher callback
                                        } else {
                                            context.contentResolver.update(item.uri, cv, null, null)
                                            true
                                        }
                                    }.getOrDefault(false)

                                    if (saved) {
                                        onSave(newTitle.trim(), newArtist.trim(), newAlbum.trim(), artworkUri)
                                        isSaving = false
                                    }
                                }
                            },
                            enabled = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Filled.Check, "Guardar",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Scrollable body
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {

                    // ── Artwork picker ────────────────────────────────────────
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
                            if (currentArtwork != null) {
                                Image(
                                    bitmap       = currentArtwork!!,
                                    contentDescription = "Portada actual",
                                    contentScale = ContentScale.Crop,
                                    modifier     = Modifier.fillMaxSize()
                                )
                                // Camera icon overlay
                                Box(
                                    modifier         = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.30f)),
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
                            if (croppedBitmap != null || customArtworkUri != null) {
                                TextButton(
                                    onClick = {
                                        croppedBitmap = null
                                        onSave(newTitle, newArtist, newAlbum, Uri.EMPTY)
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

                    // ── Metadata fields ───────────────────────────────────────
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
                                "Información",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            OutlinedTextField(
                                value         = newTitle,
                                onValueChange = { newTitle = it },
                                label         = { Text("Título") },
                                modifier      = Modifier.fillMaxWidth(),
                                shape         = RoundedCornerShape(12.dp),
                                singleLine    = true
                            )
                            OutlinedTextField(
                                value         = newArtist,
                                onValueChange = { newArtist = it },
                                label         = { Text("Artista") },
                                modifier      = Modifier.fillMaxWidth(),
                                shape         = RoundedCornerShape(12.dp),
                                singleLine    = true
                            )
                            OutlinedTextField(
                                value         = newAlbum,
                                onValueChange = { newAlbum = it },
                                label         = { Text("Álbum") },
                                modifier      = Modifier.fillMaxWidth(),
                                shape         = RoundedCornerShape(12.dp),
                                singleLine    = true
                            )
                        }
                    }

                    // Save button
                    Button(
                        onClick = {
                            isSaving = true
                            scope.launch {
                                val artworkUri = croppedBitmap?.let { bmp ->
                                    saveBitmapToInternalStorage(context, bmp, item.id)
                                } ?: customArtworkUri
                                onSave(newTitle.trim(), newArtist.trim(), newAlbum.trim(), artworkUri)
                                isSaving = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(14.dp),
                        enabled  = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier   = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color      = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Filled.Check, null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Guardar cambios",
                            style = MaterialTheme.typography.labelLarge)
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    // ── Crop dialog ───────────────────────────────────────────────────────────
    if (showCropDialog && pendingBitmap != null) {
        CropImageDialog(
            sourceBitmap = pendingBitmap!!,
            onConfirm    = { cropped ->
                croppedBitmap  = cropped
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

    // Initialise crop square once we know the container size
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
        Surface(
            modifier = Modifier.fillMaxSize(),
            color    = Color(0xFF0A0A0A)
        ) {
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
                    TextButton(
                        onClick = {
                            if (containerSize.width <= 0 || cropSizePx <= 0) return@TextButton

                            // Calculate where the image actually renders (ContentScale.Fit)
                            val imgAspect  = sourceBitmap.width.toFloat() / sourceBitmap.height
                            val ctnAspect  = containerSize.width.toFloat() / containerSize.height
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

                            val safeX    = bmpX.coerceIn(0, (sourceBitmap.width  - 1).coerceAtLeast(0))
                            val safeY    = bmpY.coerceIn(0, (sourceBitmap.height - 1).coerceAtLeast(0))
                            val safeSize = bmpSize
                                .coerceAtLeast(1)
                                .coerceAtMost(minOf(sourceBitmap.width - safeX, sourceBitmap.height - safeY))

                            if (safeSize > 0) {
                                val cropped = Bitmap.createBitmap(
                                    sourceBitmap, safeX, safeY, safeSize, safeSize
                                )
                                onConfirm(cropped)
                            }
                        }
                    ) {
                        Text("Recortar",
                            color = Color(0xFF007AFF),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ))
                    }
                }

                // Image + interactive crop overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onSizeChanged { containerSize = it },
                    contentAlignment = Alignment.Center
                ) {
                    // Source image
                    Image(
                        bitmap             = sourceBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier.fillMaxSize()
                    )

                    // Dark overlay with crop hole + border
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Punch hole in the dark overlay
                        val holePath = Path().apply {
                            addRect(Rect(cropOffset, Size(cropSizePx, cropSizePx)))
                        }
                        clipPath(holePath, clipOp = ClipOp.Difference) {
                            drawRect(color = Color.Black.copy(alpha = 0.62f))
                        }

                        // White border around crop square
                        drawRect(
                            color   = Color.White,
                            topLeft = cropOffset,
                            size    = Size(cropSizePx, cropSizePx),
                            style   = Stroke(width = 2.dp.toPx())
                        )

                        // Corner handles
                        val handleLen = 18.dp.toPx()
                        val stroke    = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        val corners = listOf(
                            cropOffset to listOf(Offset(1f, 0f), Offset(0f, 1f)),
                            Offset(cropOffset.x + cropSizePx, cropOffset.y) to
                                listOf(Offset(-1f, 0f), Offset(0f, 1f)),
                            Offset(cropOffset.x, cropOffset.y + cropSizePx) to
                                listOf(Offset(1f, 0f), Offset(0f, -1f)),
                            Offset(cropOffset.x + cropSizePx, cropOffset.y + cropSizePx) to
                                listOf(Offset(-1f, 0f), Offset(0f, -1f))
                        )
                        corners.forEach { (corner, dirs) ->
                            dirs.forEach { dir ->
                                drawLine(
                                    color = Color.White,
                                    start = corner,
                                    end   = corner + Offset(dir.x * handleLen, dir.y * handleLen),
                                    strokeWidth = 3.dp.toPx(),
                                    cap   = StrokeCap.Round
                                )
                            }
                        }

                        // Rule-of-thirds grid inside crop
                        val thirdW = cropSizePx / 3f
                        val thirdH = cropSizePx / 3f
                        val gridColor = Color.White.copy(alpha = 0.25f)
                        for (i in 1..2) {
                            drawLine(
                                color = gridColor,
                                start = Offset(cropOffset.x + thirdW * i, cropOffset.y),
                                end   = Offset(cropOffset.x + thirdW * i, cropOffset.y + cropSizePx),
                                strokeWidth = 0.8.dp.toPx()
                            )
                            drawLine(
                                color = gridColor,
                                start = Offset(cropOffset.x, cropOffset.y + thirdH * i),
                                end   = Offset(cropOffset.x + cropSizePx, cropOffset.y + thirdH * i),
                                strokeWidth = 0.8.dp.toPx()
                            )
                        }
                    }

                    // Invisible drag surface on top of the crop square
                    if (containerSize.width > 0 && cropSizePx > 0) {
                        val cropSizeDp = with(density) { cropSizePx.toDp() }
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        cropOffset.x.roundToInt(),
                                        cropOffset.y.roundToInt()
                                    )
                                }
                                .size(cropSizeDp)
                                .pointerInput(containerSize, cropSizePx) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        cropOffset = Offset(
                                            x = (cropOffset.x + drag.x)
                                                .coerceIn(0f, containerSize.width  - cropSizePx),
                                            y = (cropOffset.y + drag.y)
                                                .coerceIn(0f, containerSize.height - cropSizePx)
                                        )
                                    }
                                }
                        )
                    }
                }

                // Bottom hint
                Text(
                    "Arrastra el recuadro para elegir el área de recorte",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = Color.White.copy(alpha = 0.65f),
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
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
    }
    Uri.fromFile(file)
}
