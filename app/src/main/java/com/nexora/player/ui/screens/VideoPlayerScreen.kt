package com.nexora.player.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.getSystemService
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nexora.player.R
import com.nexora.player.data.model.MediaEntry
import com.nexora.player.playback.PlayerEngine
import com.nexora.player.ui.components.GestureControlOverlay
import com.nexora.player.ui.components.PlayerControlsRow
import com.nexora.player.ui.components.PlayerMetadata
import com.nexora.player.ui.components.PlaybackSeekBar
import com.nexora.player.ui.components.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun VideoPlayerScreen(
    modifier: Modifier = Modifier,
    current: MediaEntry?
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context.findActivity()
    val exoPlayer = PlayerEngine.get(context)
    val snapshot by PlayerEngine.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    val audioManager = context.getSystemService<AudioManager>()
    val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1
    val currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
    val volumeFraction = currentVolume.toFloat() / maxVolume.toFloat()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var brightness by remember {
        mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.6f)
    }
    var showControls by remember { mutableStateOf(true) }
    var showRewindIndicator by remember { mutableStateOf(false) }
    var showForwardIndicator by remember { mutableStateOf(false) }

    DisposableEffect(isLandscape) {
        if (isLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            activity?.window?.let { WindowCompat.getInsetsController(it, view) }?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3500)
            showControls = false
        }
    }

    BackHandler {
        if (isLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }
    }

    fun setBrightness(value: Float) {
        brightness = value.coerceIn(0.05f, 1f)
        activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = brightness }
    }

    fun setVolume(value: Float) {
        audioManager?.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            (value.coerceIn(0f, 1f) * maxVolume).roundToInt(),
            0
        )
    }

    if (current == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.video_no_playback), style = MaterialTheme.typography.headlineSmall)
        }
        return
    }

    if (isLandscape) {
        Box(modifier = modifier.fillMaxSize()) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { showControls = !showControls },
                            onDoubleTap = { offset ->
                                if (offset.x < size.width / 2f) {
                                    PlayerEngine.skipPrevious(context)
                                    showRewindIndicator = true
                                    showForwardIndicator = false
                                } else {
                                    PlayerEngine.skipNext(context)
                                    showForwardIndicator = true
                                    showRewindIndicator = false
                                }
                                scope.launch {
                                    delay(350)
                                    showRewindIndicator = false
                                    showForwardIndicator = false
                                }
                            }
                        )
                    }
            )

            AnimatedVisibility(visible = showRewindIndicator) {
                Icon(
                    Icons.Filled.Replay10,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(32.dp)
                        .size(54.dp)
                )
            }
            AnimatedVisibility(visible = showForwardIndicator) {
                Icon(
                    Icons.Filled.Forward10,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(32.dp)
                        .size(54.dp)
                )
            }

            AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(108.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.30f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(onClick = { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT }) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Salir")
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(current.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
                                Text(current.folder?.takeIf { it.isNotBlank() } ?: stringResource(R.string.video_local_label), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                            }

                            FilledTonalIconButton(onClick = { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT }) {
                                Icon(Icons.Filled.FullscreenExit, contentDescription = "Salir de pantalla completa")
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.30f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(formatDuration(exoPlayer.currentPosition), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.88f))
                                PlaybackSeekBar(
                                    positionMs = exoPlayer.currentPosition,
                                    durationMs = exoPlayer.duration.takeIf { it > 0L } ?: current.durationMs,
                                    onSeekTo = { exoPlayer.seekTo(it) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                )
                                Text(formatDuration(exoPlayer.duration.takeIf { it > 0L } ?: current.durationMs), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.88f))
                            }

                            PlayerControlsRow(
                                isPlaying = snapshot.isPlaying,
                                onPrevious = { PlayerEngine.skipPrevious(context) },
                                onTogglePlay = { PlayerEngine.togglePlayPause(context) },
                                onNext = { PlayerEngine.skipNext(context) }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Desliza a la izquierda/derecha para brillo y volumen",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                FilledTonalIconButton(onClick = { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT }) {
                                    Icon(Icons.Filled.Fullscreen, contentDescription = "Salir de pantalla completa")
                                }
                            }
                        }
                    }
                }
            }

            GestureControlOverlay(
                modifier = Modifier.fillMaxSize(),
                brightness = brightness,
                volume = volumeFraction,
                onBrightnessChange = ::setBrightness,
                onVolumeChange = ::setVolume
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PlayerMetadata(
                        title = current.title,
                        subtitle = current.folder?.takeIf { it.isNotBlank() } ?: stringResource(R.string.video_local_label),
                        trailingLabel = formatDuration(current.durationMs)
                    )

                    PlaybackSeekBar(
                        positionMs = exoPlayer.currentPosition,
                        durationMs = exoPlayer.duration.takeIf { it > 0L } ?: current.durationMs,
                        onSeekTo = { exoPlayer.seekTo(it) }
                    )

                    PlayerControlsRow(
                        isPlaying = snapshot.isPlaying,
                        onPrevious = { PlayerEngine.skipPrevious(context) },
                        onTogglePlay = { PlayerEngine.togglePlayPause(context) },
                        onNext = { PlayerEngine.skipNext(context) }
                    )
                }
            }

            FilledTonalIconButton(
                onClick = { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Filled.Fullscreen, contentDescription = "Pantalla completa")
            }
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
