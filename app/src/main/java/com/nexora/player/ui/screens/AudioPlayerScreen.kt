package com.nexora.player.ui.screens

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexora.player.R
import com.nexora.player.data.local.FavoriteMediaEntity
import com.nexora.player.data.local.NexoraDatabase
import com.nexora.player.equalizer.EqualizerPreferencesRepository
import com.nexora.player.equalizer.EqualizerSessionManager
import com.nexora.player.equalizer.EqualizerSettings
import com.nexora.player.data.lyrics.LrcParser
import com.nexora.player.data.lyrics.Lyrics
import com.nexora.player.data.lyrics.LyricsRepository
import com.nexora.player.ui.screens.LyricsEditorDialog
import com.nexora.player.data.model.MediaEntry
import com.nexora.player.data.model.MediaKind
import com.nexora.player.playback.PlayerEngine
import com.nexora.player.ui.components.MediaItemRow
import com.nexora.player.ui.components.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

// ---------------------------------------------------------------------------
// Artwork style — persisted in SharedPreferences (no stub extension needed)
// ---------------------------------------------------------------------------

private const val PREFS_NAME = "nexora_player_ui"
private const val KEY_ARTWORK_STYLE = "artwork_style"

private enum class ArtworkStyle {
    DISC, SQUARE, COVER;

    fun next(): ArtworkStyle = when (this) {
        DISC   -> SQUARE
        SQUARE -> COVER
        COVER  -> DISC
    }

    companion object {
        fun fromName(name: String?): ArtworkStyle =
            runCatching { valueOf(name.orEmpty()) }.getOrDefault(DISC)
    }
}

// ---------------------------------------------------------------------------
// Helper: parse LRC timestamps from rawText to get timed lines
// ---------------------------------------------------------------------------

private data class LrcLine(val timestampMs: Long, val text: String)

private fun parseLrcLines(rawText: String?): List<LrcLine> {
    if (rawText.isNullOrBlank()) return emptyList()
    val regex = Regex("\\[(\\d+):(\\d{2}(?:\\.\\d+)?)\\](.*)")
    return rawText.lines()
        .mapNotNull { line ->
            val match = regex.find(line) ?: return@mapNotNull null
            val min  = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val sec  = match.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
            val text = match.groupValues[3].trim()
            LrcLine(
                timestampMs = min * 60_000L + (sec * 1_000).toLong(),
                text = text
            )
        }
        .sortedBy { it.timestampMs }
}

// ---------------------------------------------------------------------------
// Main screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    modifier: Modifier = Modifier,
    current: MediaEntry?,
    onClose: () -> Unit = {}
) {
    val context = LocalContext.current
    val player  = PlayerEngine.get(context)
    val snapshot by PlayerEngine.snapshot.collectAsState()
    val scope   = rememberCoroutineScope()
    val favorites by NexoraDatabase.get(context).favoritesDao()
        .observeAll().collectAsState(initial = emptyList())

    val lyricsRepository    = remember(context) { LyricsRepository(context, NexoraDatabase.get(context)) }
    val equalizerRepository = remember(context) { EqualizerPreferencesRepository(context) }
    val equalizerSettings   by equalizerRepository.settings.collectAsState(initial = EqualizerSettings())

    // ── Artwork style ── persisted in SharedPreferences so it survives song
    //    changes, app restarts, and process death.
    val uiPrefs = remember(context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    var artworkStyle by rememberSaveable {
        mutableStateOf(ArtworkStyle.fromName(uiPrefs.getString(KEY_ARTWORK_STYLE, "DISC")))
    }

    // ── UI state ──
    var lyrics           by remember { mutableStateOf<Lyrics?>(null) }
    var lyricsLoading    by remember { mutableStateOf(false) }
    var allowOnlineLyrics by rememberSaveable(current?.id) { mutableStateOf(true) }
    var showLyricsEditor  by rememberSaveable(current?.id) { mutableStateOf(false) }
    var showLyricsSheet   by rememberSaveable { mutableStateOf(false) }
    var showPlaylistSheet by rememberSaveable { mutableStateOf(false) }
    var showDetailsSheet  by rememberSaveable(current?.id) { mutableStateOf(false) }
    var showEqualizerSheet by rememberSaveable(current?.id) { mutableStateOf(false) }

    // ── Playback position ──
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    // ── Artwork bitmap ──
    val artwork by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = current?.id,
        key2 = current?.uri?.toString()
    ) {
        value = withContext(Dispatchers.IO) {
            current?.let { loadArtworkBitmap(context, it)?.asImageBitmap() }
        }
    }

    // ── Disc rotation animation ──
    val infiniteTransition = rememberInfiniteTransition(label = "disc_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val isFavorite = remember(current?.id, current?.kind, favorites) {
        val item = current
        item != null && favorites.any { it.mediaId == item.id && it.mediaKind == item.kind.name }
    }

    val queue        = snapshot.queue
    val currentIndex = snapshot.currentIndex.coerceAtLeast(0)

    // ── Side effects ──
    LaunchedEffect(player.audioSessionId, equalizerSettings) {
        if (player.audioSessionId > 0) {
            EqualizerSessionManager.sync(player.audioSessionId, equalizerSettings)
        }
    }

    LaunchedEffect(current?.id, snapshot.isPlaying) {
        if (current == null) return@LaunchedEffect
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0L } ?: current.durationMs
            delay(350)
        }
    }

    LaunchedEffect(current?.id, allowOnlineLyrics) {
        val item = current
        if (item == null) { lyrics = null; lyricsLoading = false; return@LaunchedEffect }
        lyricsLoading = true
        lyrics = null
        lyrics = runCatching {
            lyricsRepository.loadLyrics(item, allowOnlineSearch = allowOnlineLyrics)
        }.getOrNull()
        lyricsLoading = false
    }

    BackHandler { onClose() }

    // ── Root container ──
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF04050A))) {

        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.audio_no_playback),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }
            return@Box
        }

        // Blurred artwork background
        if (artwork != null) {
            Image(
                bitmap = artwork!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(42.dp)
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF7C3AED).copy(alpha = 0.40f),
                            Color(0xFF0D0D1A)
                        )
                    )
                )
            )
        }

        // Dark gradient overlay
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.35f),
                        Color.Black.copy(alpha = 0.68f),
                        Color.Black.copy(alpha = 0.94f)
                    )
                )
            )
        )

        // ── Main layout ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            // ── Top bar ──
            PlayerTopBar(
                current      = current,
                artworkStyle = artworkStyle,
                onBack       = onClose,
                onOpenEqualizer = { showEqualizerSheet = true },
                onChangeArtworkStyle = {
                    val next = artworkStyle.next()
                    artworkStyle = next
                    uiPrefs.edit().putString(KEY_ARTWORK_STYLE, next.name).apply()
                }
            )

            // ── Center: artwork + controls ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(8.dp))

                ArtworkDisplay(
                    artwork   = artwork,
                    title     = current.title,
                    isPlaying = snapshot.isPlaying,
                    rotation  = rotation,
                    style     = artworkStyle
                )

                Spacer(Modifier.height(10.dp))

                ArtworkStyleSelector(
                    currentStyle = artworkStyle,
                    onSelect     = { selected ->
                        artworkStyle = selected
                        uiPrefs.edit().putString(KEY_ARTWORK_STYLE, selected.name).apply()
                    }
                )

                Spacer(Modifier.height(22.dp))

                // ── Song info + favorite ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text     = current.title,
                            style    = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize   = 24.sp
                            ),
                            color    = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = listOfNotNull(
                                current.artist.takeIf { it.isNotBlank() },
                                current.album.takeIf  { it.isNotBlank() }
                            ).joinToString(" • ").ifBlank { stringResource(R.string.app_name) },
                            style    = MaterialTheme.typography.bodyLarge,
                            color    = Color.White.copy(alpha = 0.60f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Heart icon — filled red if favorite, outlined if not
                    IconButton(
                        onClick  = { scope.launch { toggleFavorite(context, current) } },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite
                                          else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isFavorite) "Quitar de favoritos"
                                                 else "Agregar a favoritos",
                            tint     = if (isFavorite) Color(0xFFFF3B30)
                                       else Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                // ── Progress bar ──
                PlaybackProgressSection(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onSeek     = { PlayerEngine.seekTo(context, it) },
                    modifier   = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // ── Transport row: [playlist] [prev] [play] [next] [info] ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // Playlist button
                    IconButton(onClick = { showPlaylistSheet = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = "Playlist",
                            tint     = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Skip previous
                    IconButton(
                        onClick  = { PlayerEngine.skipPrevious(context) },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Anterior",
                            tint     = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Play / Pause — big white circle (iPhone-style)
                    Surface(
                        onClick  = { PlayerEngine.togglePlayPause(context) },
                        shape    = CircleShape,
                        color    = Color.White,
                        modifier = Modifier.size(70.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (snapshot.isPlaying) Icons.Filled.Pause
                                              else Icons.Filled.PlayArrow,
                                contentDescription = if (snapshot.isPlaying) "Pausar" else "Reproducir",
                                tint     = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Skip next
                    IconButton(
                        onClick  = { PlayerEngine.skipNext(context) },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Siguiente",
                            tint     = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Details
                    IconButton(onClick = { showDetailsSheet = true }) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Detalles",
                            tint     = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            // ── Compact lyrics preview (bottom) ──
            CompactLyricsCard(
                lyrics        = lyrics,
                lyricsLoading = lyricsLoading,
                positionMs    = positionMs,
                onExpand      = { showLyricsSheet = true },
                onSearchOnline = { allowOnlineLyrics = true },
                modifier      = Modifier.fillMaxWidth()
            )
        }
    }

    // ── Bottom sheets / dialogs ──

    if (showPlaylistSheet) {
        PlaylistSheet(
            current          = current,
            queue            = queue,
            currentIndex     = currentIndex,
            onDismiss        = { showPlaylistSheet = false },
            onJumpToQueueItem = { PlayerEngine.jumpTo(context, it) }
        )
    }

    if (showLyricsSheet && current != null) {
        FullLyricsSheet(
            lyrics         = lyrics,
            lyricsLoading  = lyricsLoading,
            positionMs     = positionMs,
            onDismiss      = { showLyricsSheet = false },
            onEdit         = { showLyricsSheet = false; showLyricsEditor = true },
            onSearchOnline = { allowOnlineLyrics = true }
        )
    }

    if (showEqualizerSheet && current != null) {
        EqualizerSheet(
            audioSessionId = player.audioSessionId,
            onDismiss      = { showEqualizerSheet = false }
        )
    }

    if (showDetailsSheet && current != null) {
        DetailsSheet(
            current      = current,
            queue        = queue,
            currentIndex = currentIndex,
            onDismiss    = { showDetailsSheet = false },
            onShowQueue  = { showDetailsSheet = false; showPlaylistSheet = true },
            onOpenEqualizer = { showDetailsSheet = false; showEqualizerSheet = true }
        )
    }

    if (showLyricsEditor && current != null) {
        LyricsEditorDialog(
            currentPositionMs = positionMs,
            initialText       = lyrics?.rawText.orEmpty(),
            onSave = { rawText, exportToFile ->
                scope.launch {
                    val parsed = LrcParser.parse(
                        rawText  = rawText,
                        mediaId  = current.id,
                        title    = current.title,
                        artist   = current.artist,
                        album    = current.album,
                        source   = com.nexora.player.data.lyrics.LyricsSource.MANUAL
                    )
                    lyricsRepository.saveLyrics(current, parsed, exportToSidecarFile = exportToFile)
                    lyrics = parsed
                    showLyricsEditor = false
                }
            },
            onDismiss = { showLyricsEditor = false }
        )
    }
}

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

@Composable
private fun PlayerTopBar(
    current: MediaEntry,
    artworkStyle: ArtworkStyle,
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onChangeArtworkStyle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Back
        IconButton(onClick = onBack) {
            Icon(
                imageVector    = Icons.Filled.ArrowBack,
                contentDescription = "Volver",
                tint           = Color.White
            )
        }

        // Title
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text  = "Now Playing",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.72f)
            )
            Text(
                text  = if (current.kind == MediaKind.AUDIO) "Audio" else "Media",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.42f)
            )
        }

        // Right actions
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onOpenEqualizer) {
                Icon(
                    imageVector    = Icons.Filled.Equalizer,
                    contentDescription = "Ecualizador",
                    tint           = Color.White
                )
            }
            IconButton(onClick = onChangeArtworkStyle) {
                Icon(
                    imageVector = Icons.Filled.Checkroom,
                    contentDescription = when (artworkStyle) {
                        ArtworkStyle.DISC   -> "Cambiar a cuadrado"
                        ArtworkStyle.SQUARE -> "Cambiar a portada"
                        ArtworkStyle.COVER  -> "Cambiar a disco"
                    },
                    tint = Color.White
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Artwork display
// ---------------------------------------------------------------------------

@Composable
private fun ArtworkDisplay(
    artwork: ImageBitmap?,
    title: String,
    isPlaying: Boolean,
    rotation: Float,
    style: ArtworkStyle,
    modifier: Modifier = Modifier
) {
    val shape     = when (style) {
        ArtworkStyle.DISC   -> CircleShape
        ArtworkStyle.SQUARE -> RoundedCornerShape(34.dp)
        ArtworkStyle.COVER  -> RoundedCornerShape(42.dp)
    }
    val outerSize = when (style) { ArtworkStyle.DISC -> 310.dp; else -> 304.dp }
    val innerSize = when (style) { ArtworkStyle.DISC -> 262.dp; else -> 260.dp }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(outerSize)
            .shadow(30.dp, shape, clip = false)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
    ) {
        Box(
            modifier = Modifier
                .size(innerSize)
                .shadow(18.dp, shape, clip = false)
                .clip(shape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.Black.copy(alpha = 0.45f)
                        )
                    )
                )
        ) {
            if (artwork != null) {
                Image(
                    bitmap        = artwork,
                    contentDescription = title,
                    contentScale  = ContentScale.Crop,
                    modifier      = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .graphicsLayer(
                            rotationZ = if (style == ArtworkStyle.DISC && isPlaying) rotation else 0f
                        )
                )
            } else {
                Icon(
                    imageVector    = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint           = Color.White,
                    modifier       = Modifier.size(92.dp).align(Alignment.Center)
                )
            }

            // Disc hole overlay
            if (style == ArtworkStyle.DISC) {
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.38f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                )
            }

            // Subtle vignette
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f)),
                            radius = 420f
                        )
                    )
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Artwork style selector chips
// ---------------------------------------------------------------------------

@Composable
private fun ArtworkStyleSelector(
    currentStyle: ArtworkStyle,
    onSelect: (ArtworkStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        listOf(ArtworkStyle.DISC, ArtworkStyle.SQUARE, ArtworkStyle.COVER).forEach { style ->
            FilterChip(
                selected = currentStyle == style,
                onClick  = { onSelect(style) },
                label    = {
                    Text(
                        when (style) {
                            ArtworkStyle.DISC   -> "Disco"
                            ArtworkStyle.SQUARE -> "Cuadrado"
                            ArtworkStyle.COVER  -> "Portada"
                        }
                    )
                },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Playback progress
// ---------------------------------------------------------------------------

@Composable
private fun PlaybackProgressSection(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by rememberSaveable { mutableStateOf(false) }
    var progress   by remember(positionMs, durationMs) {
        mutableStateOf(
            if (durationMs > 0L) positionMs.toFloat() / durationMs.toFloat() else 0f
        )
    }

    LaunchedEffect(positionMs, durationMs, isDragging) {
        if (!isDragging) {
            progress = if (durationMs > 0L) positionMs.toFloat() / durationMs.toFloat() else 0f
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Slider(
            value            = progress.coerceIn(0f, 1f),
            onValueChange    = { isDragging = true; progress = it },
            onValueChangeFinished = {
                isDragging = false
                if (durationMs > 0L) {
                    onSeek((progress.coerceIn(0f, 1f) * durationMs.toFloat()).roundToLong())
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors   = SliderDefaults.colors(
                thumbColor        = Color.White,
                activeTrackColor  = Color(0xFF7C3AED),
                inactiveTrackColor = Color.White.copy(alpha = 0.18f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text  = formatDuration(positionMs.coerceAtLeast(0L)),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.60f)
            )
            Text(
                text  = formatDuration(durationMs.coerceAtLeast(0L)),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.60f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Compact lyrics preview card (bottom of screen)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactLyricsCard(
    lyrics: Lyrics?,
    lyricsLoading: Boolean,
    positionMs: Long,
    onExpand: () -> Unit,
    onSearchOnline: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Parse LRC timestamps once per lyrics object
    val lrcLines = remember(lyrics?.rawText) { parseLrcLines(lyrics?.rawText) }

    // Current synced line
    val currentLine = remember(lrcLines, positionMs) {
        lrcLines.lastOrNull { it.timestampMs <= positionMs }?.text
            ?: lrcLines.firstOrNull()?.text
    }

    Card(
        onClick  = onExpand,
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f)),
        shape    = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = "Letra",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.45f)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = when {
                        lyricsLoading       -> "Cargando..."
                        currentLine != null -> currentLine
                        lyrics != null      -> "Ver letra completa"
                        else                -> "Sin letra · Toca para agregar"
                    },
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = when {
                        lyrics != null && !lyricsLoading -> Color.White.copy(alpha = 0.88f)
                        else                             -> Color.White.copy(alpha = 0.40f)
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector    = Icons.Filled.ExpandLess,
                contentDescription = "Ver letra completa",
                tint           = Color.White.copy(alpha = 0.45f),
                modifier       = Modifier.size(20.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Full lyrics sheet (expand)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullLyricsSheet(
    lyrics: Lyrics?,
    lyricsLoading: Boolean,
    positionMs: Long,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSearchOnline: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lrcLines   = remember(lyrics?.rawText) { parseLrcLines(lyrics?.rawText) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 22.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Letra", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (lyrics == null && !lyricsLoading) {
                        TextButton(onClick = onSearchOnline) { Text("Buscar en línea") }
                    }
                    TextButton(onClick = onEdit) {
                        Text(if (lyrics == null) "Agregar" else "Editar")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            when {
                lyricsLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Cargando letra...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                lyrics != null -> {
                    val displayLines = if (lrcLines.isNotEmpty()) {
                        // Use parsed timed lines
                        lrcLines.map { it.text }.filter { it.isNotBlank() }
                    } else {
                        // Plain text fallback: strip any leftover LRC tags
                        lyrics.rawText.lines()
                            .map { it.replace(Regex("\\[\\d+:\\d+\\.\\d+\\]"), "").trim() }
                            .filter { it.isNotBlank() }
                    }

                    // Highlight current line index
                    val currentLineIndex = remember(lrcLines, positionMs) {
                        val idx = lrcLines.indexOfLast { it.timestampMs <= positionMs }
                        if (idx < 0 && lrcLines.isNotEmpty()) 0 else idx
                    }

                    LazyColumn(
                        modifier              = Modifier.fillMaxSize(),
                        verticalArrangement   = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(displayLines) { index, line ->
                            val isCurrentLine = lrcLines.isNotEmpty() && index == currentLineIndex
                            Text(
                                text     = line,
                                style    = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                                    fontSize   = if (isCurrentLine) 18.sp else 16.sp
                                ),
                                color    = if (isCurrentLine) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment   = Alignment.CenterHorizontally,
                            verticalArrangement   = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector    = Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint           = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier       = Modifier.size(48.dp)
                            )
                            Text(
                                text      = "Sin letra disponible",
                                style     = MaterialTheme.typography.bodyLarge,
                                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            FilledTonalButton(onClick = onSearchOnline) { Text("Buscar en línea") }
                            TextButton(onClick = onEdit) { Text("Agregar manualmente") }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Playlist sheet (ALL songs in queue)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistSheet(
    current: MediaEntry,
    queue: List<MediaEntry>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onJumpToQueueItem: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Playlist", style = MaterialTheme.typography.titleLarge)
                Text(
                    text  = "${queue.size} canción${if (queue.size != 1) "es" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text     = current.title,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = "No hay canciones en la playlist.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Show ALL songs — no heightIn cap
                LazyColumn(
                    modifier            = Modifier.fillMaxHeight(0.78f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(queue) { index, item ->
                        MediaItemRow(
                            item    = item,
                            onClick = { onJumpToQueueItem(index) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Details sheet (unchanged logic, updated queue label)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsSheet(
    current: MediaEntry,
    queue: List<MediaEntry>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onShowQueue: () -> Unit,
    onOpenEqualizer: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Detalles", style = MaterialTheme.typography.titleLarge)

            ElevatedCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = current.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = listOfNotNull(
                            current.artist.takeIf { it.isNotBlank() },
                            current.album.takeIf  { it.isNotBlank() }
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent  = { Text("Duración") },
                        supportingContent = { Text(formatDuration(current.durationMs)) },
                        leadingContent   = { Icon(Icons.Filled.MusicNote, null) }
                    )
                    ListItem(
                        headlineContent  = { Text("Artista") },
                        supportingContent = { Text(current.artist.ifBlank { "Desconocido" }) },
                        leadingContent   = { Icon(Icons.Filled.Info, null) }
                    )
                    ListItem(
                        headlineContent  = { Text("Álbum") },
                        supportingContent = { Text(current.album.ifBlank { "Sin álbum" }) },
                        leadingContent   = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null) }
                    )
                    ListItem(
                        headlineContent  = { Text("Carpeta") },
                        supportingContent = { Text(current.folder.orEmpty().ifBlank { "No disponible" }) },
                        leadingContent   = { Icon(Icons.Filled.QueueMusic, null) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenEqualizer) { Text("Ecualizador") }
                TextButton(onClick = onShowQueue)     { Text("Ver playlist") }
            }

            Text(
                text  = "Siguiente: ${queue.drop(currentIndex + 1).size} canciones",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private suspend fun toggleFavorite(context: Context, entry: MediaEntry?) {
    if (entry == null || entry.kind == MediaKind.VIDEO) return
    val db     = NexoraDatabase.get(context)
    val exists = db.favoritesDao().isFavorite(entry.id, entry.kind.name)
    if (exists) {
        db.favoritesDao().delete(entry.id, entry.kind.name)
    } else {
        db.favoritesDao().upsert(
            FavoriteMediaEntity(
                mediaId    = entry.id,
                mediaKind  = entry.kind.name,
                title      = entry.title,
                artist     = entry.artist,
                album      = entry.album,
                durationMs = entry.durationMs,
                uriString  = entry.uri.toString()
            )
        )
    }
}

private fun loadArtworkBitmap(context: Context, item: MediaEntry): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, item.uri)
        retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            ?: loadAlbumArtBitmap(context, item.albumId)
    } catch (_: Throwable) {
        loadAlbumArtBitmap(context, item.albumId)
    } finally {
        runCatching { retriever.release() }
    }
}

private fun loadAlbumArtBitmap(context: Context, albumId: Long?): Bitmap? {
    if (albumId == null || albumId <= 0L) return null
    return runCatching {
        val albumUri = android.content.ContentUris.withAppendedId(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId
        )
        context.contentResolver.query(
            albumUri,
            arrayOf(MediaStore.Audio.Albums.ALBUM_ART),
            null, null, null
        )?.use { cursor ->
            val col = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART)
            if (cursor.moveToFirst() && col >= 0) {
                val path = cursor.getString(col)
                if (!path.isNullOrBlank()) BitmapFactory.decodeFile(path) else null
            } else null
        }
    }.getOrNull()
}

private fun findComponentActivity(context: Context): ComponentActivity? = when (context) {
    is ComponentActivity -> context
    is ContextWrapper    -> findComponentActivity(context.baseContext)
    else                 -> null
}
