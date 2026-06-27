package com.nexora.player.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.nexora.player.audio.VolumeBoostSessionManager
import com.nexora.player.data.model.MediaEntry
import com.nexora.player.data.model.PlaybackSnapshot
import com.nexora.player.equalizer.EqualizerSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

object PlayerEngine {
    private const val SERVICE_CLASS = "com.nexora.player.playback.PlayerService"

    @Volatile
    private var player: ExoPlayer? = null

    private val queueLock = Any()
    private var queue: List<MediaEntry> = emptyList()

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var crossfadeJob: Job? = null
    private var crossfadeEnabled: Boolean = false
    private var crossfadeDurationMs: Int = 2500

    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)
            ) {
                updateSnapshot(player)
                maybeFadeIn(player, events)
            }
        }
    }

    fun get(context: Context): ExoPlayer {
        return player ?: synchronized(this) {
            player ?: ExoPlayer.Builder(context.applicationContext).build().also { created ->
                created.repeatMode = Player.REPEAT_MODE_OFF
                created.playWhenReady = true
                created.addListener(listener)
                player = created
                updateSnapshot(created)
            }
        }
    }

    fun playQueue(context: Context, items: List<MediaEntry>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        ensureService(context)
        val exoPlayer = get(context)
        val index = startIndex.coerceIn(0, items.lastIndex)
        synchronized(queueLock) { queue = items.toList() }
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
        exoPlayer.setMediaItems(mediaItems, index, 0L)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        exoPlayer.play()
        updateSnapshot(exoPlayer)
        maybeFadeIn(exoPlayer, null)
    }

    fun play(context: Context, item: MediaEntry) {
        playQueue(context, listOf(item), 0)
    }

    fun skipNext(context: Context) {
        get(context).seekToNext()
        updateSnapshot(get(context))
    }

    fun skipPrevious(context: Context) {
        get(context).seekToPrevious()
        updateSnapshot(get(context))
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
            synchronized(queueLock) { queue = queue.toMutableList().also { if (index in it.indices) it.removeAt(index) } }
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

    fun setShuffleMode(enabled: Boolean) {
        getCurrentPlayer()?.shuffleModeEnabled = enabled
        updateSnapshot(getCurrentPlayer())
    }

    fun setCrossfade(enabled: Boolean, durationMs: Int) {
        crossfadeEnabled = enabled
        crossfadeDurationMs = durationMs.coerceIn(500, 10000)
    }

    fun release() {
        crossfadeJob?.cancel()
        player?.run {
            removeListener(listener)
            release()
        }
        player = null
        synchronized(queueLock) { queue = emptyList() }
        EqualizerSessionManager.release()
        VolumeBoostSessionManager.release()
        _snapshot.value = PlaybackSnapshot()
    }

    private fun getCurrentPlayer(): ExoPlayer? = player

    private fun updateSnapshot(player: Player?) {
        if (player == null) return
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
            isPlaying = player.isPlaying,
            currentPositionMs = player.currentPosition,
            shuffleEnabled = player.shuffleModeEnabled
        )
    }

    private fun maybeFadeIn(player: Player?, events: Player.Events?) {
        val exoPlayer = player as? ExoPlayer ?: return
        if (!crossfadeEnabled) return
        if (events != null && !events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) return
        crossfadeJob?.cancel()
        exoPlayer.volume = 0f
        crossfadeJob = engineScope.launch {
            val steps = 12
            val duration = crossfadeDurationMs.coerceAtLeast(500)
            val stepDelay = (duration / steps).coerceAtLeast(16).toLong()
            repeat(steps) { step ->
                val progress = (step + 1).toFloat() / steps.toFloat()
                exoPlayer.volume = progress.coerceIn(0f, 1f)
                delay(stepDelay)
            }
            exoPlayer.volume = 1f
        }
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
