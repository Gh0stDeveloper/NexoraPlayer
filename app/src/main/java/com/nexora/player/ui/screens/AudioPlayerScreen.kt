package com.nexora.player.ui.screens

import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatDelegate
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import com.nexora.player.data.lyrics.LyricsTranslator
import com.nexora.player.data.preferences.AppPreferences
import com.nexora.player.data.preferences.PreferencesRepository
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
import com.nexora.player.data.model.NexoraRepeatMode
import com.nexora.player.playback.PlayerEngine
import com.nexora.player.ui.components.MediaItemRow
import com.nexora.player.ui.components.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// ---------------------------------------------------------------------------
// Artwork style — persisted in SharedPreferences (no stub extension needed)
// ---------------------------------------------------------------------------

private const val PREFS_NAME = "nexora_player_ui"
private const val KEY_ARTWORK_STYLE = "artwork_style"

private enum class ArtworkStyle {
    DISC, VINYL, SQUARE, COVER, TILE;

    fun next(): ArtworkStyle = when (this) {
        DISC   -> VINYL
        VINYL  -> SQUARE
        SQUARE -> COVER
        COVER  -> TILE
        TILE   -> DISC
    }

    companion object {
        fun fromName(name: String?): ArtworkStyle =
            runCatching { valueOf(name.orEmpty()) }.getOrDefault(DISC)
    }
}

private enum class LyricsDisplayMode(val label: String) {
    ORIGINAL("Original"),
    TRANSLATED("Traducida"),
    BOTH("Ambas")
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

private fun displayLyricsLines(rawText: String?): List<String> {
    if (rawText.isNullOrBlank()) return emptyList()
    return rawText.lines()
        .map { it.replace(Regex("\\[.*?\\]"), "").trim() }
        .filter { it.isNotBlank() }
}

private fun syncedLyricsLines(rawText: String?): List<LrcLine> =
    parseLrcLines(rawText).filter { it.text.isNotBlank() }

private fun safeCurrentLyricsIndex(lines: List<LrcLine>, positionMs: Long, displaySize: Int): Int {
    if (displaySize <= 0) return -1
    if (lines.isEmpty()) return 0
    val rawIndex = lines.indexOfLast { it.timestampMs <= positionMs }
        .let { if (it < 0) 0 else it }
    return rawIndex.coerceIn(0, displaySize - 1)
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
    val preferencesRepository = remember(context) { PreferencesRepository(context) }
    val appPreferences by preferencesRepository.preferences.collectAsState(initial = AppPreferences())
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
    var allowOnlineLyrics by rememberSaveable(current?.id) { mutableStateOf(false) }
    var showLyricsEditor  by rememberSaveable(current?.id) { mutableStateOf(false) }
    var showLyricsSheet   by rememberSaveable { mutableStateOf(false) }
    var showPlaylistSheet by rememberSaveable { mutableStateOf(false) }
    var showDetailsSheet  by rememberSaveable(current?.id) { mutableStateOf(false) }
    var showEqualizerSheet by rememberSaveable(current?.id) { mutableStateOf(false) }

    val targetLanguageTag = remember(appPreferences, current?.id) {
        val locales = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val tag = locales.substringBefore(",").takeIf { it.isNotBlank() } ?: Locale.getDefault().toLanguageTag()
        tag
    }

    val lyricsSnapshot = lyrics
    val translatedRawLyrics by produceState<String?>(
        initialValue = null,
        lyricsSnapshot?.rawText,
        current?.id,
        appPreferences.lyricsTranslationEnabled,
        targetLanguageTag
    ) {
        value = if (lyricsSnapshot == null || !appPreferences.lyricsTranslationEnabled) {
            null
        } else {
            LyricsTranslator.translateRawLyrics(lyricsSnapshot.rawText, targetLanguageTag)
        }
    }

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

    LaunchedEffect(current?.id, current?.kind, allowOnlineLyrics) {
        val item = current
        if (item == null || item.kind != MediaKind.AUDIO) {
            lyrics = null
            lyricsLoading = false
            return@LaunchedEffect
        }
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
                .padding(horizontal = 24.dp)
        ) {

            // ── Top bar (fixed, no scroll) ──
            Spacer(Modifier.height(20.dp))
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
            Spacer(Modifier.height(12.dp))

            // ── Scrollable content ──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Artwork — bigger
                ArtworkDisplay(
                    artwork   = artwork,
                    title     = current.title,
                    isPlaying = snapshot.isPlaying,
                    rotation  = rotation,
                    style     = artworkStyle
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
                    // Heart
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

                Spacer(Modifier.height(20.dp))

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
                    IconButton(onClick = { showPlaylistSheet = true }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, "Playlist",
                            tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = { PlayerEngine.skipPrevious(context) },
                        modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.SkipPrevious, "Anterior",
                            tint = Color.White, modifier = Modifier.size(36.dp))
                    }
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
                                contentDescription = null,
                                tint     = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    IconButton(onClick = { PlayerEngine.skipNext(context) },
                        modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.SkipNext, "Siguiente",
                            tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { showDetailsSheet = true }) {
                        Icon(Icons.Filled.Info, "Detalles",
                            tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(22.dp))
                    }
                }

                Spacer(Modifier.height(10.dp))

                NexoraVolumePanel(
                    preferences = appPreferences,
                    onSystemVolumeChange = { percent ->
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1
                        audioManager?.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            ((percent.coerceIn(0f, 1f)) * maxVolume).roundToInt().coerceIn(0, maxVolume),
                            if (appPreferences.volumeBoostEnabled) 0 else AudioManager.FLAG_SHOW_UI
                        )
                    },
                    onBoostChange = { gainMb ->
                        val safeGain = gainMb.coerceIn(0, 1800)
                        PlayerEngine.setVolumeBoost(appPreferences.volumeBoostEnabled, safeGain)
                        scope.launch { preferencesRepository.setVolumeBoostGainMb(safeGain) }
                    },
                    onToggleBoost = { enabled ->
                        PlayerEngine.setVolumeBoost(enabled, appPreferences.volumeBoostGainMb)
                        scope.launch { preferencesRepository.setVolumeBoostEnabled(enabled) }
                    }
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val next = !appPreferences.shuffleEnabled
                        PlayerEngine.setShuffleEnabled(next)
                        scope.launch { preferencesRepository.setShuffleEnabled(next) }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Aleatorio",
                            tint = if (appPreferences.shuffleEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.62f)
                        )
                    }

                    IconButton(onClick = {
                        val next = appPreferences.repeatMode.next()
                        PlayerEngine.setRepeatMode(next)
                        scope.launch { preferencesRepository.setRepeatMode(next) }
                    }) {
                        Icon(
                            imageVector = if (appPreferences.repeatMode == NexoraRepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            contentDescription = "Repetición",
                            tint = if (appPreferences.repeatMode == NexoraRepeatMode.OFF) Color.White.copy(alpha = 0.62f) else MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Timer, contentDescription = null, tint = Color.White.copy(alpha = 0.62f), modifier = Modifier.size(18.dp))
                        Text(
                            text = if (appPreferences.sleepTimerEnabled) "Timer activo" else "Sin timer",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.62f)
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Spacer(Modifier.height(20.dp))

                // ── Lyrics section: only audio gets lyrics. This prevents video playback
                //    from recomposing the lyric preview with stale audio lyric indexes.
                if (current.kind == MediaKind.AUDIO) {
                    InlineLyricsSection(
                        lyrics            = lyrics,
                        translatedRawText  = translatedRawLyrics,
                        lyricsLoading     = lyricsLoading,
                        positionMs        = positionMs,
                        onSearchOnline    = { allowOnlineLyrics = true },
                        onEdit            = { showLyricsEditor = true },
                        onExpand          = { showLyricsSheet = true },
                        modifier          = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(48.dp))
            }
        }
    }

    // ── Bottom sheets / dialogs ──

    if (showPlaylistSheet && current != null) {
        PlaylistSheet(
            current          = current,
            queue            = queue,
            currentIndex     = currentIndex,
            onDismiss        = { showPlaylistSheet = false },
            onJumpToQueueItem = { PlayerEngine.jumpTo(context, it) }
        )
    }

    if (showLyricsSheet && current?.kind == MediaKind.AUDIO) {
        FullLyricsSheet(
            lyrics            = lyrics,
            translatedRawText  = translatedRawLyrics,
            lyricsLoading      = lyricsLoading,
            positionMs         = positionMs,
            onDismiss          = { showLyricsSheet = false },
            onEdit             = { showLyricsSheet = false; showLyricsEditor = true },
            onSearchOnline     = { allowOnlineLyrics = true }
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

    if (showLyricsEditor && current?.kind == MediaKind.AUDIO) {
        val audioCurrent = current!!
        LyricsEditorDialog(
            currentPositionMs = positionMs,
            initialText       = lyrics?.rawText.orEmpty(),
            onSave = { rawText, exportToFile ->
                scope.launch {
                    val parsed = LrcParser.parse(
                        rawText  = rawText,
                        mediaId  = audioCurrent.id,
                        title    = audioCurrent.title,
                        artist   = audioCurrent.artist,
                        album    = audioCurrent.album,
                        source   = com.nexora.player.data.lyrics.LyricsSource.MANUAL
                    )
                    lyricsRepository.saveLyrics(audioCurrent, parsed, exportToSidecarFile = exportToFile)
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
                text  = "Nexora Player",
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
                        ArtworkStyle.DISC   -> "Cambiar a vinilo"
                        ArtworkStyle.VINYL  -> "Cambiar a cuadrado"
                        ArtworkStyle.SQUARE -> "Cambiar a portada"
                        ArtworkStyle.COVER  -> "Cambiar a tile"
                        ArtworkStyle.TILE   -> "Cambiar a disco"
                    },
                    tint = Color.White
                )
            }
        }
    }
}


@Composable
private fun NexoraVolumePanel(
    preferences: AppPreferences,
    onSystemVolumeChange: (Float) -> Unit,
    onBoostChange: (Int) -> Unit,
    onToggleBoost: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxVolume = remember(audioManager) { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1 }
    var systemVolume by remember {
        mutableStateOf((audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 0f) / maxVolume.toFloat())
    }
    val extraPercent = (preferences.volumeBoostGainMb / 1800f).coerceIn(0f, 1f) * 50f
    val displayPercent = ((systemVolume.coerceIn(0f, 1f) * 100f) + if (preferences.volumeBoostEnabled) extraPercent else 0f).roundToInt()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.07f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Equalizer, contentDescription = null, tint = Color.White.copy(alpha = 0.84f), modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Volumen Nexora", color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        if (preferences.volumeBoostEnabled) "Amplificado: $displayPercent%" else "Sistema: ${(systemVolume * 100).roundToInt()}%",
                        color = Color.White.copy(alpha = 0.56f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                TextButton(onClick = { onToggleBoost(!preferences.volumeBoostEnabled) }) {
                    Text(if (preferences.volumeBoostEnabled) "Boost ON" else "Boost OFF")
                }
            }

            Slider(
                value = systemVolume.coerceIn(0f, 1f),
                onValueChange = { value ->
                    systemVolume = value
                    onSystemVolumeChange(value)
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = if (preferences.volumeBoostEnabled) Color(0xFFF54047) else Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                )
            )

            if (preferences.volumeBoostEnabled) {
                Slider(
                    value = preferences.volumeBoostGainMb.toFloat().coerceIn(0f, 1800f),
                    onValueChange = { onBoostChange(it.roundToInt()) },
                    valueRange = 0f..1800f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFF54047),
                        activeTrackColor = Color(0xFFF54047),
                        inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                    )
                )
                Text(
                    "Extra: +${extraPercent.roundToInt()}% · recomendado usar con cuidado",
                    color = Color.White.copy(alpha = 0.52f),
                    style = MaterialTheme.typography.labelSmall
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
    val shape = when (style) {
        ArtworkStyle.DISC, ArtworkStyle.VINYL -> CircleShape
        ArtworkStyle.SQUARE -> RoundedCornerShape(34.dp)
        ArtworkStyle.COVER  -> RoundedCornerShape(42.dp)
        ArtworkStyle.TILE   -> RoundedCornerShape(8.dp)
    }
    val outerSize = when (style) {
        ArtworkStyle.DISC, ArtworkStyle.VINYL -> 340.dp
        else -> 330.dp
    }
    val innerSize = when (style) {
        ArtworkStyle.DISC, ArtworkStyle.VINYL -> 290.dp
        else -> 284.dp
    }

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
                            rotationZ = if (
                                (style == ArtworkStyle.DISC || style == ArtworkStyle.VINYL) && isPlaying
                            ) rotation else 0f
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

            // VINYL: groove rings drawn over the artwork
            if (style == ArtworkStyle.VINYL) {
                listOf(230.dp, 190.dp, 150.dp, 110.dp).forEach { ringSize ->
                    Box(
                        modifier = Modifier
                            .size(ringSize)
                            .align(Alignment.Center)
                            .border(0.8.dp, Color.Black.copy(alpha = 0.30f), CircleShape)
                    )
                }
            }

            // Center hole for DISC and VINYL
            if (style == ArtworkStyle.DISC || style == ArtworkStyle.VINYL) {
                val holeSize = if (style == ArtworkStyle.VINYL) 58.dp else 46.dp
                Box(
                    modifier = Modifier
                        .size(holeSize)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color(0xFF04050A))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
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
// Inline lyrics section — lives inside the scrollable column
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InlineLyricsSection(
    lyrics: Lyrics?,
    translatedRawText: String?,
    lyricsLoading: Boolean,
    positionMs: Long,
    onSearchOnline: () -> Unit,
    onEdit: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lrcLines = remember(lyrics?.rawText) { syncedLyricsLines(lyrics?.rawText) }
    val translatedLines = remember(translatedRawText) { displayLyricsLines(translatedRawText) }
    val displayLines = remember(lyrics?.rawText, lrcLines) {
        if (lrcLines.isNotEmpty()) lrcLines.map { it.text } else displayLyricsLines(lyrics?.rawText)
    }

    val currentLineIndex = remember(lrcLines, displayLines.size, positionMs) {
        safeCurrentLyricsIndex(lrcLines, positionMs, displayLines.size)
    }

    Column(modifier = modifier) {

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text  = "Letra",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.90f)
            )
            if (lyrics != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onEdit) {
                        Text("Editar", color = Color.White.copy(alpha = 0.60f),
                            style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = onExpand, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.ExpandLess, "Ver completa",
                            tint = Color.White.copy(alpha = 0.60f), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        when {
            lyricsLoading -> {
                Text(
                    text  = "Buscando letra...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.45f)
                )
            }

            lyrics == null -> {
                Text(
                    text  = "Esta canción no tiene letra guardada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.45f)
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        onClick = onSearchOnline,
                        shape   = RoundedCornerShape(50),
                        color   = Color(0xFF7C3AED).copy(alpha = 0.70f)
                    ) {
                        Text(
                            text     = "Buscar en línea",
                            style    = MaterialTheme.typography.labelLarge,
                            color    = Color.White,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                        )
                    }
                    Surface(
                        onClick = onEdit,
                        shape   = RoundedCornerShape(50),
                        color   = Color.White.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text     = "Escribir letra",
                            style    = MaterialTheme.typography.labelLarge,
                            color    = Color.White.copy(alpha = 0.80f),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            else -> {
                // ── 3-line window: previous · current · next ────────────────
                // Make the whole block tappable to expand full lyrics
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (displayLines.isEmpty()) {
                        // No clean lines to show, raw fallback
                        Text(
                            text      = lyrics.rawText.take(120),
                            style     = MaterialTheme.typography.bodyLarge,
                            color     = Color.White.copy(alpha = 0.75f),
                            lineHeight = 26.sp
                        )
                    } else {
                        val hasTiming = lrcLines.isNotEmpty() && currentLineIndex in displayLines.indices
                        val pivotIdx  = if (hasTiming) currentLineIndex else 0

                        val prevIdx = (pivotIdx - 1).takeIf { it in displayLines.indices }
                        val currIdx = pivotIdx.takeIf { it in displayLines.indices }
                        val nextIdx = (pivotIdx + 1).takeIf { it in displayLines.indices }

                        // Previous line
                        if (prevIdx != null) {
                            Text(
                                text     = displayLines[prevIdx],
                                style    = MaterialTheme.typography.bodyMedium,
                                color    = Color.White.copy(alpha = 0.32f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Current line — bright, bold, bigger
                        if (currIdx != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text     = displayLines[currIdx],
                                    style    = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize   = 20.sp,
                                        lineHeight = 28.sp
                                    ),
                                    color    = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                translatedLines.getOrNull(currIdx)?.let { translated ->
                                    Text(
                                        text     = translated,
                                        style    = MaterialTheme.typography.bodySmall,
                                        color    = Color.White.copy(alpha = 0.78f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Next line
                        if (nextIdx != null) {
                            Text(
                                text     = displayLines[nextIdx],
                                style    = MaterialTheme.typography.bodyMedium,
                                color    = Color.White.copy(alpha = 0.45f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Tap to see full lyrics
                Surface(
                    onClick = onExpand,
                    shape   = RoundedCornerShape(50),
                    color   = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text     = "Ver letra completa",
                        style    = MaterialTheme.typography.labelMedium,
                        color    = Color.White.copy(alpha = 0.60f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
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
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Parse LRC timestamps once per lyrics object
    val lrcLines = remember(lyrics?.rawText) { syncedLyricsLines(lyrics?.rawText) }

    // Current synced line — recomputed every time positionMs ticks
    val currentLine = remember(lrcLines, positionMs) {
        if (lrcLines.isEmpty()) null
        else lrcLines.lastOrNull { it.timestampMs <= positionMs }?.text
            ?: lrcLines.firstOrNull()?.text
    }

    // Plain-text fallback: first non-blank line with LRC tags stripped
    val plainPreview = remember(lyrics?.rawText) {
        lyrics?.rawText
            ?.lines()
            ?.map { it.replace(Regex("\\[.*?\\]"), "").trim() }
            ?.firstOrNull { it.isNotBlank() }
    }

    val cardColor = CardDefaults.cardColors(
        containerColor = Color.White.copy(alpha = 0.10f)
    )
    val cardShape = RoundedCornerShape(20.dp)

    when {
        // ── Loading ──────────────────────────────────────────────────────────
        lyricsLoading -> {
            Card(modifier = modifier, colors = cardColor, shape = cardShape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text  = "Buscando letra...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.50f)
                    )
                }
            }
        }

        // ── Has lyrics → tappable preview with current line ──────────────────
        lyrics != null -> {
            Card(
                onClick  = onExpand,
                modifier = modifier,
                colors   = cardColor,
                shape    = cardShape
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
                            // Prefer synced LRC line → plain text preview → generic label
                            text = currentLine
                                ?: plainPreview
                                ?: "Ver letra completa",
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = Color.White.copy(alpha = 0.88f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector        = Icons.Filled.ExpandLess,
                        contentDescription = "Ver letra completa",
                        tint               = Color.White.copy(alpha = 0.50f),
                        modifier           = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ── No lyrics → show search / write buttons ───────────────────────
        else -> {
            Card(modifier = modifier, colors = cardColor, shape = cardShape) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint               = Color.White.copy(alpha = 0.40f),
                            modifier           = Modifier.size(16.dp)
                        )
                        Text(
                            text  = "Sin letra",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.45f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Buscar en línea
                        Surface(
                            onClick = onSearchOnline,
                            shape   = RoundedCornerShape(50),
                            color   = Color.White.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text     = "Buscar en línea",
                                style    = MaterialTheme.typography.labelMedium,
                                color    = Color.White.copy(alpha = 0.90f),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                        // Escribir letra
                        Surface(
                            onClick = onEdit,
                            shape   = RoundedCornerShape(50),
                            color   = Color.White.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text     = "Escribir letra",
                                style    = MaterialTheme.typography.labelMedium,
                                color    = Color.White.copy(alpha = 0.70f),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
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
    translatedRawText: String?,
    lyricsLoading: Boolean,
    positionMs: Long,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSearchOnline: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lrcLines = remember(lyrics?.rawText) { syncedLyricsLines(lyrics?.rawText) }
    val originalLines = remember(lyrics?.rawText, lrcLines) {
        if (lrcLines.isNotEmpty()) lrcLines.map { it.text }
        else displayLyricsLines(lyrics?.rawText)
    }
    val translatedLines = remember(translatedRawText) { displayLyricsLines(translatedRawText) }
    var displayMode by rememberSaveable { mutableStateOf(LyricsDisplayMode.ORIGINAL) }

    val safeDisplayMode = when {
        displayMode == LyricsDisplayMode.TRANSLATED && translatedLines.isEmpty() -> LyricsDisplayMode.ORIGINAL
        displayMode == LyricsDisplayMode.BOTH && translatedLines.isEmpty() -> LyricsDisplayMode.ORIGINAL
        else -> displayMode
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF04050A),
        contentColor = Color.White
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF111126),
                            Color(0xFF07080F),
                            Color(0xFF04050A)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Letras",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = when (safeDisplayMode) {
                                LyricsDisplayMode.ORIGINAL -> "Mostrando letra original"
                                LyricsDisplayMode.TRANSLATED -> "Mostrando letra traducida"
                                LyricsDisplayMode.BOTH -> "Mostrando original y traducción"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.56f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                displayMode = when (safeDisplayMode) {
                                    LyricsDisplayMode.ORIGINAL -> if (translatedLines.isNotEmpty()) LyricsDisplayMode.TRANSLATED else LyricsDisplayMode.ORIGINAL
                                    LyricsDisplayMode.TRANSLATED -> LyricsDisplayMode.BOTH
                                    LyricsDisplayMode.BOTH -> LyricsDisplayMode.ORIGINAL
                                }
                            },
                            enabled = translatedLines.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Filled.Translate,
                                contentDescription = "Cambiar vista de traducción",
                                tint = if (translatedLines.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.28f)
                            )
                        }
                        TextButton(onClick = onEdit) {
                            Text(if (lyrics == null) "Agregar" else "Editar", color = Color.White.copy(alpha = 0.76f))
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                LyricsModeSelector(
                    selected = safeDisplayMode,
                    hasTranslation = translatedLines.isNotEmpty(),
                    onSelected = { displayMode = it }
                )

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(Modifier.height(12.dp))

                when {
                    lyricsLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Cargando letra...", color = Color.White.copy(alpha = 0.62f))
                        }
                    }

                    lyrics != null -> {
                        val currentLineIndex = remember(lrcLines, originalLines.size, positionMs) {
                            safeCurrentLyricsIndex(lrcLines, positionMs, originalLines.size)
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(originalLines) { index, originalLine ->
                                val isCurrentLine = lrcLines.isNotEmpty() && index == currentLineIndex
                                val translatedLine = translatedLines.getOrNull(index).orEmpty()
                                LyricsKaraokeRow(
                                    originalLine = originalLine,
                                    translatedLine = translatedLine,
                                    mode = safeDisplayMode,
                                    isCurrentLine = isCurrentLine
                                )
                            }
                        }
                    }

                    else -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.08f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.70f),
                                        modifier = Modifier.padding(18.dp).size(42.dp)
                                    )
                                }
                                Text(
                                    text = "Sin letra disponible",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.86f),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Puedes buscarla en línea o escribirla manualmente.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.52f),
                                    textAlign = TextAlign.Center
                                )
                                FilledTonalButton(onClick = onSearchOnline) { Text("Buscar en línea") }
                                TextButton(onClick = onEdit) { Text("Agregar manualmente", color = Color.White.copy(alpha = 0.72f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsModeSelector(
    selected: LyricsDisplayMode,
    hasTranslation: Boolean,
    onSelected: (LyricsDisplayMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LyricsModeButton(
            text = "Original",
            selected = selected == LyricsDisplayMode.ORIGINAL,
            enabled = true,
            onClick = { onSelected(LyricsDisplayMode.ORIGINAL) },
            modifier = Modifier.weight(1f)
        )
        LyricsModeButton(
            text = "Traducida",
            selected = selected == LyricsDisplayMode.TRANSLATED,
            enabled = hasTranslation,
            leadingIcon = Icons.Filled.Translate,
            onClick = { onSelected(LyricsDisplayMode.TRANSLATED) },
            modifier = Modifier.weight(1f)
        )
        LyricsModeButton(
            text = "Ambas",
            selected = selected == LyricsDisplayMode.BOTH,
            enabled = hasTranslation,
            leadingIcon = Icons.Filled.Translate,
            onClick = { onSelected(LyricsDisplayMode.BOTH) },
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsModeButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = if (enabled) Color.White.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.28f),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                color = when {
                    !enabled -> Color.White.copy(alpha = 0.28f)
                    selected -> Color.White
                    else -> Color.White.copy(alpha = 0.62f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LyricsKaraokeRow(
    originalLine: String,
    translatedLine: String,
    mode: LyricsDisplayMode,
    isCurrentLine: Boolean
) {
    val accent = Color(0xFF7C3AED)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isCurrentLine) accent.copy(alpha = 0.16f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = if (isCurrentLine) 12.dp else 9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (mode == LyricsDisplayMode.ORIGINAL || mode == LyricsDisplayMode.BOTH) {
            Text(
                text = originalLine,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                    fontSize = if (isCurrentLine) 20.sp else 16.sp,
                    lineHeight = if (isCurrentLine) 28.sp else 24.sp
                ),
                color = if (isCurrentLine) Color.White else Color.White.copy(alpha = 0.64f),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if ((mode == LyricsDisplayMode.TRANSLATED || mode == LyricsDisplayMode.BOTH) && translatedLine.isNotBlank()) {
            Text(
                text = translatedLine,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (mode == LyricsDisplayMode.TRANSLATED && isCurrentLine) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = if (mode == LyricsDisplayMode.TRANSLATED && isCurrentLine) 19.sp else 14.sp,
                    lineHeight = 22.sp
                ),
                color = if (isCurrentLine) Color.White.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.48f),
                modifier = Modifier.fillMaxWidth()
            )
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
