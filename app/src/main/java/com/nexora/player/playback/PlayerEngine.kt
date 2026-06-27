package com.nexora.player.playback

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.nexora.player.data.model.MediaEntry
import com.nexora.player.data.model.PlaybackSnapshot
import com.nexora.player.audio.VolumeBoostSessionManager
import com.nexora.player.equalizer.EqualizerSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlayerEngine {
    private const val SERVICE_CLASS = "com.nexora.player.playback.PlayerService"

    @Volatile
    private var player: ExoPlayer? = null

    private val queueLock = Any()
    private var queue: List<MediaEntry> = emptyList()
    @Volatile private var shuffleEnabled: Boolean = false
    @Volatile private var crossfadeEnabled: Boolean = false
    @Volatile private var crossfadeDurationMs: Int = 1200

    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                maybeAnimateCrossfade(player)
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
                created.repeatMode = Player.REPEAT_MODE_OFF
                created.shuffleModeEnabled = shuffleEnabled
                created.playWhenReady = true
                created.addListener(listener)
                player = created
                updateSnapshot(created)
            }
        }
    }

    fun playQueue(context: Context, items: List<MediaEntry>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        if (items.isEmpty()) return
        ensureService(context)
        val exoPlayer = get(context)
        val index = startIndex.coerceIn(0, items.lastIndex)
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
        exoPlayer.setMediaItems(mediaItems, index, startPositionMs.coerceAtLeast(0L))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        exoPlayer.play()
        if (crossfadeEnabled) {
            exoPlayer.volume = 0f
            animateVolume(exoPlayer, 1f, crossfadeDurationMs.toLong())
        }
        updateSnapshot(exoPlayer)
    }

    fun play(context: Context, item: MediaEntry) {
        playQueue(context, listOf(item), 0)
    }

    fun skipNext(context: Context) {
        val player = get(context)
        if (crossfadeEnabled) player.volume = 0f
        player.seekToNext()
        if (crossfadeEnabled) animateVolume(player, 1f, crossfadeDurationMs.toLong())
        updateSnapshot(player)
    }

    fun skipPrevious(context: Context) {
        val player = get(context)
        if (crossfadeEnabled) player.volume = 0f
        player.seekToPrevious()
        if (crossfadeEnabled) animateVolume(player, 1f, crossfadeDurationMs.toLong())
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

    fun setShuffleEnabled(enabled: Boolean) {
        shuffleEnabled = enabled
        player?.shuffleModeEnabled = enabled
    }

    fun setCrossfadeEnabled(enabled: Boolean, durationMs: Int) {
        crossfadeEnabled = enabled
        crossfadeDurationMs = durationMs.coerceIn(250, 5000)
    }

    fun release() {
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
    private fun maybeAnimateCrossfade(player: Player) {
        if (!crossfadeEnabled) return
        animateVolume(player, 1f, crossfadeDurationMs.toLong())
    }

    private fun animateVolume(player: Player, target: Float, durationMs: Long) {
        val exo = player as? ExoPlayer ?: return
        val start = exo.volume
        ValueAnimator.ofFloat(start, target).apply {
            duration = durationMs.coerceAtLeast(1L)
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                exo.volume = (animator.animatedValue as Float).coerceIn(0f, 1f)
            }
            start()
        }
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
