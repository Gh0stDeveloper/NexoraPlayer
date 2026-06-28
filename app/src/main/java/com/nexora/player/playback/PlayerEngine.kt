package com.nexora.player.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.nexora.player.data.model.NexoraRepeatMode
import com.nexora.player.data.model.MediaEntry
import com.nexora.player.data.model.MediaKind
import com.nexora.player.data.model.PlaybackSnapshot
import com.nexora.player.audio.VolumeBoostSessionManager
import com.nexora.player.equalizer.EqualizerSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlayerEngine {
    private const val SERVICE_CLASS = "com.nexora.player.playback.PlayerService"

    @Volatile
    private var player: ExoPlayer? = null

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val crossfadeController = CrossfadeController(engineScope)

    private val queueLock = Any()
    private var queue: List<MediaEntry> = emptyList()
    @Volatile private var shuffleEnabled: Boolean = false
    @Volatile private var repeatMode: NexoraRepeatMode = NexoraRepeatMode.OFF
    @Volatile private var playbackSpeed: Float = 1.0f
    @Volatile private var playbackVolume: Float = 1.0f
    @Volatile private var crossfadeEnabled: Boolean = false
    @Volatile private var crossfadeDurationMs: Int = 1200

    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                crossfadeController.onManualTransition(player)
            }
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
            ) {
                updateSnapshot(player)
            }
        }
    }

    fun get(context: Context): ExoPlayer {
        return player ?: synchronized(this) {
            player ?: ExoPlayer.Builder(context.applicationContext).build().also { created ->
                created.repeatMode = repeatMode.toPlayerRepeatMode()
                created.shuffleModeEnabled = shuffleEnabled
                created.setPlaybackSpeed(playbackSpeed)
                created.volume = playbackVolume
                created.playWhenReady = false
                created.addListener(listener)
                player = created
                crossfadeController.attach(created)
                updateSnapshot(created)
            }
        }
    }

    fun playQueue(
        context: Context,
        items: List<MediaEntry>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = true
    ) {
        if (items.isEmpty()) return
        val index = startIndex.coerceIn(0, items.lastIndex)

        // El servicio foreground solo debe arrancar para audio.
        // Los videos se reproducen dentro de la Activity; si se llama
        // startForegroundService() para video, Android exige una
        // notificación foreground inmediata y crashea si el servicio
        // no la publica a tiempo.
        if (items[index].kind == MediaKind.AUDIO) {
            ensureService(context)
        }

        val exoPlayer = get(context)
        synchronized(queueLock) {
            queue = items.toList()
        }
        val mediaItems = items.map { item ->
            MediaItem.Builder()
                .setUri(item.uri)
                .setMediaId(item.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setAlbumTitle(item.album)
                        .build()
                )
                .build()
        }
        exoPlayer.shuffleModeEnabled = shuffleEnabled
        exoPlayer.repeatMode = repeatMode.toPlayerRepeatMode()
        exoPlayer.setPlaybackSpeed(playbackSpeed)
        exoPlayer.volume = playbackVolume
        exoPlayer.setMediaItems(mediaItems, index, startPositionMs.coerceAtLeast(0L))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = autoPlay
        if (autoPlay) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
        updateSnapshot(exoPlayer)
    }

    fun play(context: Context, item: MediaEntry) {
        playQueue(context, listOf(item), 0)
    }

    fun skipNext(context: Context) {
        val player = get(context)
        player.seekToNext()
        updateSnapshot(player)
    }

    fun skipPrevious(context: Context) {
        val player = get(context)
        player.seekToPrevious()
        updateSnapshot(player)
    }

    fun jumpTo(context: Context, index: Int) {
        val player = get(context)
        if (index in 0 until player.mediaItemCount) {
            player.seekTo(index, 0L)
            player.playWhenReady = true
            player.play()
            updateSnapshot(player)
        }
    }

    fun seekTo(context: Context, positionMs: Long) {
        val player = get(context)
        val safePosition = positionMs.coerceAtLeast(0L)
        player.seekTo(safePosition)
        updateSnapshot(player)
    }

    fun removeAt(context: Context, index: Int) {
        val player = get(context)
        if (index in 0 until player.mediaItemCount) {
            player.removeMediaItem(index)
            synchronized(queueLock) {
                queue = queue.toMutableList().also { if (index in it.indices) it.removeAt(index) }
            }
            updateSnapshot(player)
        }
    }

    fun clear(context: Context) {
        val player = get(context)
        player.stop()
        player.clearMediaItems()
        synchronized(queueLock) { queue = emptyList() }
        updateSnapshot(player)
    }

    fun togglePlayPause(context: Context) {
        val player = get(context)
        if (player.isPlaying) player.pause() else player.play()
        updateSnapshot(player)
    }

    fun pause(context: Context) {
        val player = get(context)
        player.pause()
        player.playWhenReady = false
        updateSnapshot(player)
    }

    fun setShuffleEnabled(enabled: Boolean) {
        shuffleEnabled = enabled
        player?.shuffleModeEnabled = enabled
    }

    fun setRepeatMode(mode: NexoraRepeatMode) {
        repeatMode = mode
        player?.repeatMode = mode.toPlayerRepeatMode()
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(0.5f, 2.0f)
        player?.setPlaybackSpeed(playbackSpeed)
    }

    fun setPlaybackVolume(volume: Float) {
        playbackVolume = volume.coerceIn(0f, 1f)
        crossfadeController.setTargetVolume(playbackVolume)
        player?.volume = playbackVolume
    }

    fun setCrossfadeEnabled(enabled: Boolean, durationMs: Int) {
        crossfadeEnabled = enabled
        crossfadeDurationMs = durationMs.coerceIn(500, 5000)
        crossfadeController.configure(enabled, crossfadeDurationMs, playbackVolume)
        player?.let { crossfadeController.attach(it) }
    }


    fun setExternalSubtitle(context: Context, subtitleUri: Uri) {
        val exoPlayer = get(context)
        val currentIndex = exoPlayer.currentMediaItemIndex
        if (currentIndex < 0 || currentIndex >= exoPlayer.mediaItemCount) return
        val currentItem = exoPlayer.currentMediaItem ?: return
        val positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        val shouldPlay = exoPlayer.playWhenReady
        val subtitle = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
            .setLanguage("und")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val updatedItem = currentItem.buildUpon()
            .setSubtitleConfigurations(listOf(subtitle))
            .build()
        exoPlayer.replaceMediaItem(currentIndex, updatedItem)
        exoPlayer.prepare()
        exoPlayer.seekTo(currentIndex, positionMs)
        exoPlayer.playWhenReady = shouldPlay
        if (shouldPlay) exoPlayer.play()
        updateSnapshot(exoPlayer)
    }

    fun release() {
        player?.run {
            removeListener(listener)
            release()
        }
        player = null
        crossfadeController.detach()
        synchronized(queueLock) { queue = emptyList() }
        EqualizerSessionManager.release()
        VolumeBoostSessionManager.release()
        _snapshot.value = PlaybackSnapshot()
    }

    private fun updateSnapshot(player: Player) {
        if (player is ExoPlayer) {
            val sessionId = player.audioSessionId
            if (sessionId > 0) {
                EqualizerSessionManager.attach(sessionId)
                VolumeBoostSessionManager.attach(sessionId)
            }
        }

        val currentQueue = synchronized(queueLock) { queue.toList() }
        _snapshot.value = PlaybackSnapshot(
            queue = currentQueue,
            currentIndex = player.currentMediaItemIndex,
            isPlaying = player.isPlaying
        )
    }

    private fun ensureService(context: Context) {
        val intent = Intent().setClassName(context.packageName, SERVICE_CLASS)
        ContextCompat.startForegroundService(context, intent)
    }
    fun setVolumeBoost(enabled: Boolean, gainMillibels: Int) {
        VolumeBoostSessionManager.update(enabled, gainMillibels)
        player?.let { current ->
            if (current.audioSessionId > 0) {
                VolumeBoostSessionManager.attach(current.audioSessionId)
            }
        }
    }
}

private fun NexoraRepeatMode.toPlayerRepeatMode(): Int = when (this) {
    NexoraRepeatMode.OFF -> Player.REPEAT_MODE_OFF
    NexoraRepeatMode.ONE -> Player.REPEAT_MODE_ONE
    NexoraRepeatMode.ALL -> Player.REPEAT_MODE_ALL
}
