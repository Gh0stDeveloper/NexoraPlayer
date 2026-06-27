package com.nexora.player

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.player.data.local.FavoriteMediaEntity
import com.nexora.player.data.local.LyricsEntity
import com.nexora.player.data.local.NexoraDatabase
import com.nexora.player.data.local.OnlineSavedTrackEntity
import com.nexora.player.data.local.PlaybackHistoryEntity
import com.nexora.player.data.local.PlaylistEntity
import com.nexora.player.data.local.PlaylistItemEntity
import com.nexora.player.data.model.AppDestination
import com.nexora.player.data.model.AppThemeMode
import com.nexora.player.data.model.MediaEntry
import com.nexora.player.data.model.MediaKind
import com.nexora.player.data.model.SortMode
import com.nexora.player.data.online.OnlineMusicRepository
import com.nexora.player.data.online.OnlineTrack
import com.nexora.player.audio.VolumeBoostSessionManager
import com.nexora.player.data.preferences.AppPreferences
import com.nexora.player.data.preferences.PreferencesRepository
import com.nexora.player.data.repository.MediaStoreRepository
import com.nexora.player.notifications.MediaLibraryNotifier
import com.nexora.player.playback.PlayerEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class AppUiState(
    val audio: List<MediaEntry> = emptyList(),
    val videos: List<MediaEntry> = emptyList(),
    val queue: List<MediaEntry> = emptyList(),
    val queueIndex: Int = -1,
    val selectedDestination: AppDestination = AppDestination.MUSIC,
    val audioSort: SortMode = SortMode.DATE_ADDED_DESC,
    val videoSort: SortMode = SortMode.DATE_ADDED_DESC,
    val search: String = "",
    val currentItem: MediaEntry? = null,
    val isPlaying: Boolean = false,
    val favorites: List<FavoriteMediaEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val history: List<PlaybackHistoryEntity> = emptyList(),
    val onlineSavedTracks: List<OnlineSavedTrackEntity> = emptyList(),
    val onlineTracks: List<OnlineTrack> = emptyList(),
    val onlineLoading: Boolean = false,
    val onlineError: String? = null,
    val preferences: AppPreferences = AppPreferences()
)

private const val AUTO_PLAYLIST_ID = Long.MIN_VALUE + 42L

private data class PersistedPlaybackItem(
    val id: Long,
    val kind: String,
    val uriString: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val folder: String? = null
)

private data class PersistedPlaybackSession(
    val queue: List<PersistedPlaybackItem>,
    val currentIndex: Int,
    val positionMs: Long,
    val isPlaying: Boolean
) {
    companion object
}


private fun PersistedPlaybackSession.toJsonString(): String {
    val items = JSONArray()
    queue.forEach { item ->
        items.put(
            JSONObject()
                .put("id", item.id)
                .put("kind", item.kind)
                .put("uriString", item.uriString)
                .put("title", item.title)
                .put("artist", item.artist)
                .put("album", item.album)
                .put("durationMs", item.durationMs)
                .put("folder", item.folder ?: "")
        )
    }
    return JSONObject()
        .put("queue", items)
        .put("currentIndex", currentIndex)
        .put("positionMs", positionMs)
        .put("isPlaying", isPlaying)
        .toString()
}

private fun PersistedPlaybackSession.Companion.fromJsonString(json: String): PersistedPlaybackSession {
    val root = JSONObject(json)
    val array = root.optJSONArray("queue") ?: JSONArray()
    val items = buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            add(
                PersistedPlaybackItem(
                    id = item.optLong("id"),
                    kind = item.optString("kind", MediaKind.AUDIO.name),
                    uriString = item.optString("uriString"),
                    title = item.optString("title"),
                    artist = item.optString("artist"),
                    album = item.optString("album"),
                    durationMs = item.optLong("durationMs"),
                    folder = item.optString("folder").takeIf { it.isNotBlank() }
                )
            )
        }
    }
    return PersistedPlaybackSession(
        queue = items,
        currentIndex = root.optInt("currentIndex", 0),
        positionMs = root.optLong("positionMs", 0L),
        isPlaying = root.optBoolean("isPlaying", false)
    )
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val mediaRepository = MediaStoreRepository(context)
    private val preferencesRepository = PreferencesRepository(context)
    private val onlineRepository = OnlineMusicRepository()
    private val database = NexoraDatabase.get(context)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var onlineSearchJob: Job? = null
    private var libraryRefreshJob: Job? = null
    private var resumeRestored = false

    init {
        observePlayback()
        observeDatabase()
        observePreferences()
        refreshLibrary()
        startLibraryPolling()
    }

    private fun observePlayback() {
        viewModelScope.launch {
            PlayerEngine.snapshot.collectLatest { snapshot ->
                _uiState.value = _uiState.value.copy(
                    queue = snapshot.queue,
                    queueIndex = snapshot.currentIndex,
                    currentItem = snapshot.currentItem,
                    isPlaying = snapshot.isPlaying
                )
                persistPlaybackSessionIfNeeded()
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                _uiState.value = _uiState.value.copy(
                    audioSort = prefs.audioSort,
                    videoSort = prefs.videoSort,
                    selectedDestination = prefs.lastDestination,
                    preferences = prefs,
                    playlists = mergeWithAutoPlaylist(_uiState.value.history, _uiState.value.playlists.filterNot { it.id == AUTO_PLAYLIST_ID })
                )
                PlayerEngine.setShuffleEnabled(prefs.shuffleEnabled)
                PlayerEngine.setCrossfadeEnabled(prefs.crossfadeEnabled, prefs.crossfadeDurationMs)
                refreshLibrary()
                refreshOnlineResultsIfNeeded()
                tryRestorePlaybackSession(prefs)
                if (prefs.sleepTimerEnabled && prefs.sleepTimerEndAtMs > 0L && System.currentTimeMillis() >= prefs.sleepTimerEndAtMs) {
                    PlayerEngine.clear(context)
                    viewModelScope.launch { preferencesRepository.setSleepTimerEnabled(false) }
                }
            }
        }
    }

    private fun observeDatabase() {
        viewModelScope.launch {
            combine(
                database.favoritesDao().observeAll(),
                database.playlistsDao().observePlaylists(),
                database.historyDao().observeRecent(),
                database.onlineSavedTracksDao().observeAll()
            ) { favorites, playlists, history, onlineSaved ->
                Quadruple(favorites, playlists, history, onlineSaved)
            }.collect { (favorites, playlists, history, onlineSaved) ->
                _uiState.value = _uiState.value.copy(
                    favorites = favorites,
                    playlists = mergeWithAutoPlaylist(history, playlists),
                    history = history,
                    onlineSavedTracks = onlineSaved
                )
            }
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            val hiddenIds = _uiState.value.preferences.hiddenAudioIds
            val hiddenFolders = _uiState.value.preferences.hiddenFolders
            val audio = mediaRepository.loadAudio(_uiState.value.audioSort)
                .filterNot { it.id in hiddenIds }
                .filterNot { entry ->
                    val folder = entry.folder.orEmpty()
                    hiddenFolders.any { hidden -> folder.startsWith(hidden) || folder.contains(hidden, ignoreCase = true) }
                }
            val videos = mediaRepository.loadVideos(_uiState.value.videoSort)
            _uiState.value = _uiState.value.copy(audio = audio, videos = videos)

            if (_uiState.value.preferences.libraryChangeNotificationsEnabled) {
                MediaLibraryNotifier.maybeNotify(context, audio, videos)
            }
        }
    }

    fun setDestination(destination: AppDestination) {
        _uiState.value = _uiState.value.copy(selectedDestination = destination)
        viewModelScope.launch { preferencesRepository.setLastDestination(destination) }
    }

    fun setSearch(query: String) {
        _uiState.value = _uiState.value.copy(search = query)
        scheduleOnlineSearch(query)
    }

    fun setAudioSort(sortMode: SortMode) {
        _uiState.value = _uiState.value.copy(audioSort = sortMode)
        viewModelScope.launch { preferencesRepository.setAudioSort(sortMode) }
        refreshLibrary()
    }

    fun setVideoSort(sortMode: SortMode) {
        _uiState.value = _uiState.value.copy(videoSort = sortMode)
        viewModelScope.launch { preferencesRepository.setVideoSort(sortMode) }
        refreshLibrary()
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDynamicColor(enabled) }
    }

    fun setLyricsTranslationEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setLyricsTranslationEnabled(enabled) }
    }

    fun setVolumeBoostEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setVolumeBoostEnabled(enabled) }
        PlayerEngine.setVolumeBoost(enabled, _uiState.value.preferences.volumeBoostGainMb)
        if (!enabled) {
            VolumeBoostSessionManager.update(false, _uiState.value.preferences.volumeBoostGainMb)
        }
    }

    fun setVolumeBoostGainMb(gainMb: Int) {
        val safeGain = gainMb.coerceIn(0, 1800)
        viewModelScope.launch { preferencesRepository.setVolumeBoostGainMb(safeGain) }
        PlayerEngine.setVolumeBoost(_uiState.value.preferences.volumeBoostEnabled, safeGain)
    }

    fun setLibraryChangeNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setLibraryChangeNotificationsEnabled(enabled) }
    }

    fun setShuffleEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setShuffleEnabled(enabled) }
        PlayerEngine.setShuffleEnabled(enabled)
    }

    fun setResumePlaybackEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setResumePlaybackEnabled(enabled) }
        if (!enabled) {
            viewModelScope.launch { preferencesRepository.clearPlaybackSessionJson() }
        }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setCrossfadeEnabled(enabled)
            PlayerEngine.setCrossfadeEnabled(enabled, _uiState.value.preferences.crossfadeDurationMs)
        }
    }

    fun setCrossfadeDurationMs(durationMs: Int) {
        val safe = durationMs.coerceIn(250, 5000)
        viewModelScope.launch {
            preferencesRepository.setCrossfadeDurationMs(safe)
            PlayerEngine.setCrossfadeEnabled(_uiState.value.preferences.crossfadeEnabled, safe)
        }
    }

    fun startSleepTimer(minutes: Int) {
        val safe = minutes.coerceIn(5, 240)
        val endAt = System.currentTimeMillis() + safe * 60_000L
        viewModelScope.launch {
            preferencesRepository.setSleepTimerMinutes(safe)
            preferencesRepository.setSleepTimerEndAtMs(endAt)
            preferencesRepository.setSleepTimerEnabled(true)
        }
    }

    fun cancelSleepTimer() {
        viewModelScope.launch {
            preferencesRepository.setSleepTimerEnabled(false)
            preferencesRepository.setSleepTimerEndAtMs(0L)
        }
    }

    fun addHiddenFolder(folder: String) {
        viewModelScope.launch {
            preferencesRepository.addHiddenFolder(folder)
            refreshLibrary()
        }
    }

    fun removeHiddenFolder(folder: String) {
        viewModelScope.launch {
            preferencesRepository.removeHiddenFolder(folder)
            refreshLibrary()
        }
    }

    fun clearHiddenFolders() {
        viewModelScope.launch {
            preferencesRepository.setHiddenFolders(emptySet())
            refreshLibrary()
        }
    }

    fun setOnlineMusicSearchEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setOnlineMusicSearchEnabled(enabled) }
        if (!enabled) {
            onlineSearchJob?.cancel()
            _uiState.value = _uiState.value.copy(
                onlineTracks = emptyList(),
                onlineLoading = false,
                onlineError = null
            )
        } else {
            refreshOnlineResultsIfNeeded()
        }
    }

    private fun scheduleOnlineSearch(query: String) {
        onlineSearchJob?.cancel()
        if (query.isBlank() || !_uiState.value.preferences.onlineMusicSearchEnabled) {
            _uiState.value = _uiState.value.copy(
                onlineTracks = emptyList(),
                onlineLoading = false,
                onlineError = null
            )
            return
        }

        onlineSearchJob = viewModelScope.launch {
            delay(250)
            _uiState.value = _uiState.value.copy(onlineLoading = true, onlineError = null)
            val results = runCatching { onlineRepository.search(query, limit = 20) }
                .getOrElse { throwable ->
                    _uiState.value = _uiState.value.copy(
                        onlineLoading = false,
                        onlineTracks = emptyList(),
                        onlineError = throwable.message ?: "Online search failed"
                    )
                    return@launch
                }

            _uiState.value = _uiState.value.copy(
                onlineLoading = false,
                onlineTracks = results,
                onlineError = null
            )
        }
    }

    private fun refreshOnlineResultsIfNeeded() {
        val query = _uiState.value.search
        if (query.isNotBlank() && _uiState.value.preferences.onlineMusicSearchEnabled) {
            scheduleOnlineSearch(query)
        }
    }

    fun playQueue(items: List<MediaEntry>, startIndex: Int = 0) {
        PlayerEngine.setShuffleEnabled(_uiState.value.preferences.shuffleEnabled)
        PlayerEngine.setCrossfadeEnabled(_uiState.value.preferences.crossfadeEnabled, _uiState.value.preferences.crossfadeDurationMs)
        PlayerEngine.playQueue(context, items, startIndex)
        persistPlaybackSessionFromCurrentPlayer()
    }

    fun play(item: MediaEntry) {
        PlayerEngine.setShuffleEnabled(_uiState.value.preferences.shuffleEnabled)
        PlayerEngine.setCrossfadeEnabled(_uiState.value.preferences.crossfadeEnabled, _uiState.value.preferences.crossfadeDurationMs)
        PlayerEngine.play(context, item)
        viewModelScope.launch {
            database.historyDao().insert(
                PlaybackHistoryEntity(
                    mediaId = item.id,
                    mediaKind = item.kind.name,
                    title = item.title,
                    artist = item.artist,
                    album = item.album,
                    durationMs = item.durationMs,
                    uriString = item.uri.toString()
                )
            )
        }
    }

    fun playFromLibrary(library: List<MediaEntry>, item: MediaEntry) {
        val startIndex = library.indexOfFirst { it.id == item.id }
        if (startIndex >= 0) playQueue(library, startIndex) else play(item)
    }

    fun playPlaylistQueue(items: List<PlaylistItemEntity>, item: PlaylistItemEntity) {
        val queue = items.map { it.toMediaEntry() }
        val startIndex = queue.indexOfFirst { it.id == item.mediaId && it.kind.name == item.mediaKind }
        if (startIndex >= 0) playQueue(queue, startIndex) else play(item.toMediaEntry())
    }

    fun togglePlayPause() {
        PlayerEngine.togglePlayPause(context)
    }

    fun playNext() {
        PlayerEngine.skipNext(context)
    }

    fun playPrevious() {
        PlayerEngine.skipPrevious(context)
    }

    fun jumpToQueueIndex(index: Int) {
        PlayerEngine.jumpTo(context, index)
    }

    fun removeQueueIndex(index: Int) {
        PlayerEngine.removeAt(context, index)
    }

    fun clearQueue() {
        PlayerEngine.clear(context)
        viewModelScope.launch { preferencesRepository.clearPlaybackSessionJson() }
    }

    fun hideFromLibrary(entry: MediaEntry) {
        viewModelScope.launch {
            preferencesRepository.addHiddenAudioId(entry.id)
            refreshLibrary()
        }
    }

    fun restoreHiddenAudio() {
        viewModelScope.launch {
            preferencesRepository.clearHiddenAudioIds()
            refreshLibrary()
        }
    }

    fun deleteFromLibrary(entry: MediaEntry) {
        viewModelScope.launch {
            try {
                context.contentResolver.delete(entry.uri, null, null)
            } finally {
                cleanupAfterMediaChange(entry)
                preferencesRepository.removeHiddenAudioId(entry.id)
                refreshLibrary()
            }
        }
    }

    fun toggleFavorite(entry: MediaEntry) {
        viewModelScope.launch {
            val mediaKind = entry.kind.name
            val exists = database.favoritesDao().isFavorite(entry.id, mediaKind)
            if (exists) {
                database.favoritesDao().delete(entry.id, mediaKind)
            } else {
                database.favoritesDao().upsert(
                    FavoriteMediaEntity(
                        mediaId = entry.id,
                        mediaKind = mediaKind,
                        title = entry.title,
                        artist = entry.artist,
                        album = entry.album,
                        durationMs = entry.durationMs,
                        uriString = entry.uri.toString()
                    )
                )
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            database.playlistsDao().insertPlaylist(PlaylistEntity(name = name.trim()))
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        if (playlist.id == AUTO_PLAYLIST_ID) return
        viewModelScope.launch {
            database.playlistsDao().deleteItemsForPlaylist(playlist.id)
            database.playlistsDao().deletePlaylist(playlist.id)
        }
    }

    fun addToPlaylist(playlist: PlaylistEntity, entry: MediaEntry) {
        addToPlaylist(playlist, listOf(entry))
    }

    fun addToPlaylist(playlist: PlaylistEntity, entries: List<MediaEntry>) {
        if (playlist.id == AUTO_PLAYLIST_ID || entries.isEmpty()) return
        viewModelScope.launch {
            var next = database.playlistsDao().nextOrderIndex(playlist.id)
            entries.distinctBy { it.id to it.kind }.forEach { entry ->
                next += 1
                database.playlistsDao().insertPlaylistItem(
                    PlaylistItemEntity(
                        playlistId = playlist.id,
                        mediaId = entry.id,
                        mediaKind = entry.kind.name,
                        title = entry.title,
                        artist = entry.artist,
                        album = entry.album,
                        durationMs = entry.durationMs,
                        uriString = entry.uri.toString(),
                        orderIndex = next
                    )
                )
            }
        }
    }

    fun playlistItems(playlistId: Long): Flow<List<PlaylistItemEntity>> {
        return if (playlistId == AUTO_PLAYLIST_ID) {
            database.historyDao().observeRecent().map { history -> buildMostPlayedPlaylist(history) }
        } else {
            database.playlistsDao().observeItems(playlistId)
        }
    }

    fun playlistPreviewItems(playlistId: Long): Flow<List<PlaylistItemEntity>> {
        return if (playlistId == AUTO_PLAYLIST_ID) {
            database.historyDao().observeRecent().map { history -> buildMostPlayedPlaylist(history).take(4) }
        } else {
            database.playlistsDao().observeItems(playlistId).map { it.take(4) }
        }
    }

    fun removeFromPlaylist(itemId: Long) {
        viewModelScope.launch {
            database.playlistsDao().deletePlaylistItem(itemId)
        }
    }

    fun playFavoriteQueue(favorites: List<FavoriteMediaEntity>, favorite: FavoriteMediaEntity) {
        val audioFavorites = favorites.map { it.toMediaEntry() }
        val selected = favorite.toMediaEntry()
        val startIndex = audioFavorites.indexOfFirst { it.id == selected.id && it.kind == selected.kind }
        if (startIndex >= 0) {
            playQueue(audioFavorites, startIndex)
        } else {
            play(selected)
        }
    }

    fun playOnlineQueue(tracks: List<OnlineTrack>, track: OnlineTrack) {
        val queue = tracks.map { it.toMediaEntry() }
        val startIndex = queue.indexOfFirst { it.id == track.stableMediaId }
        if (startIndex >= 0) {
            playQueue(queue, startIndex)
        } else {
            play(track.toMediaEntry())
        }
    }

    fun toggleSavedOnlineTrack(track: OnlineTrack) {
        viewModelScope.launch {
            val exists = database.onlineSavedTracksDao().isSaved(track.providerId, track.sourceId)
            if (exists) {
                database.onlineSavedTracksDao().delete(track.providerId, track.sourceId)
            } else {
                database.onlineSavedTracksDao().upsert(
                    OnlineSavedTrackEntity(
                        providerId = track.providerId,
                        sourceId = track.sourceId,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        artworkUrl = track.artworkUrl,
                        streamUrl = track.streamUrl,
                        downloadUrl = track.downloadUrl,
                        durationMs = track.durationMs,
                        sourcePageUrl = track.sourcePageUrl
                    )
                )
            }
        }
    }

    fun isOnlineTrackSaved(track: OnlineTrack): Boolean {
        return _uiState.value.onlineSavedTracks.any { it.providerId == track.providerId && it.sourceId == track.sourceId }
    }

    @Deprecated("Use playFavoriteQueue for favorites section playback")
    fun playFavorite(favorite: FavoriteMediaEntity) {
        play(favorite.toMediaEntry())
    }

    private suspend fun cleanupAfterMediaChange(entry: MediaEntry) {
        val snapshot = PlayerEngine.snapshot.value
        val queueIndex = snapshot.queue.indexOfFirst { it.id == entry.id && it.kind == entry.kind }
        if (queueIndex >= 0) {
            PlayerEngine.removeAt(context, queueIndex)
        }
        if (snapshot.currentItem?.id == entry.id && snapshot.currentItem?.kind == entry.kind) {
            when {
                snapshot.queue.size > 1 -> PlayerEngine.skipNext(context)
                else -> PlayerEngine.clear(context)
            }
        }
        database.favoritesDao().delete(entry.id, entry.kind.name)
        database.playlistsDao().deleteItemsByMediaId(entry.id, entry.kind.name)
        database.lyricsDao().deleteByMediaId(entry.id)
        database.historyDao().deleteByMediaId(entry.id)
    }

    fun playPlaylistItem(item: PlaylistItemEntity) {
        play(item.toMediaEntry())
    }

    private fun FavoriteMediaEntity.toMediaEntry(): MediaEntry {
        return MediaEntry(
            id = mediaId,
            kind = if (mediaKind == MediaKind.VIDEO.name) MediaKind.VIDEO else MediaKind.AUDIO,
            uri = android.net.Uri.parse(uriString),
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs
        )
    }

    private fun PlaylistItemEntity.toMediaEntry(): MediaEntry {
        return MediaEntry(
            id = mediaId,
            kind = if (mediaKind == MediaKind.VIDEO.name) MediaKind.VIDEO else MediaKind.AUDIO,
            uri = android.net.Uri.parse(uriString),
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs
        )
    }

    private fun OnlineSavedTrackEntity.toOnlineTrack(): OnlineTrack = OnlineTrack(
        providerId = providerId,
        providerLabel = providerId.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
        sourceId = sourceId,
        title = title,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        downloadUrl = downloadUrl,
        durationMs = durationMs,
        sourcePageUrl = sourcePageUrl
    )


    private fun mergeWithAutoPlaylist(
        history: List<PlaybackHistoryEntity>,
        playlists: List<PlaylistEntity>
    ): List<PlaylistEntity> {
        val auto = PlaylistEntity(
            id = AUTO_PLAYLIST_ID,
            name = "Más escuchadas",
            createdAt = 0L,
            updatedAt = 0L
        )
        return buildList {
            add(auto)
            addAll(playlists.filterNot { it.id == AUTO_PLAYLIST_ID })
        }
    }

    private fun buildMostPlayedPlaylist(history: List<PlaybackHistoryEntity>): List<PlaylistItemEntity> {
        return history
            .groupBy { it.mediaKind + ":" + it.mediaId }
            .values
            .mapNotNull { group ->
                val first = group.firstOrNull() ?: return@mapNotNull null
                PlaylistItemEntity(
                    playlistId = AUTO_PLAYLIST_ID,
                    mediaId = first.mediaId,
                    mediaKind = first.mediaKind,
                    title = first.title,
                    artist = first.artist,
                    album = first.album,
                    durationMs = first.durationMs,
                    uriString = first.uriString,
                    orderIndex = group.size
                )
            }
            .sortedWith(compareByDescending<PlaylistItemEntity> { it.orderIndex }.thenBy { it.title.lowercase() })
            .mapIndexed { index, item -> item.copy(orderIndex = index) }
    }

    private fun persistPlaybackSessionIfNeeded() {
        val snapshot = PlayerEngine.snapshot.value
        if (snapshot.currentItem == null || snapshot.queue.isEmpty() || !_uiState.value.preferences.resumePlaybackEnabled) return
        persistPlaybackSessionFromCurrentPlayer()
    }

    private fun persistPlaybackSessionFromCurrentPlayer() {
        val snapshot = PlayerEngine.snapshot.value
        val current = snapshot.currentItem ?: return
        if (snapshot.queue.isEmpty()) return
        val player = runCatching { PlayerEngine.get(context) }.getOrNull() ?: return
        val payload = PersistedPlaybackSession(
            queue = snapshot.queue.map { item ->
                PersistedPlaybackItem(
                    id = item.id,
                    kind = item.kind.name,
                    uriString = item.uri.toString(),
                    title = item.title,
                    artist = item.artist,
                    album = item.album,
                    durationMs = item.durationMs,
                    folder = item.folder
                )
            },
            currentIndex = snapshot.currentIndex,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            isPlaying = snapshot.isPlaying
        )
        viewModelScope.launch {
            preferencesRepository.setPlaybackSessionJson(payload.toJsonString())
        }
    }

    private fun tryRestorePlaybackSession(prefs: AppPreferences) {
        if (resumeRestored || !prefs.resumePlaybackEnabled) return
        val json = prefs.playbackSessionJson.trim()
        if (json.isBlank()) return
        val payload = runCatching { PersistedPlaybackSession.fromJsonString(json) }.getOrNull() ?: return
        if (payload.queue.isEmpty()) return
        if (PlayerEngine.snapshot.value.queue.isNotEmpty()) return

        val queue = payload.queue.map { item ->
            MediaEntry(
                id = item.id,
                kind = if (item.kind == MediaKind.VIDEO.name) MediaKind.VIDEO else MediaKind.AUDIO,
                uri = android.net.Uri.parse(item.uriString),
                title = item.title,
                artist = item.artist,
                album = item.album,
                durationMs = item.durationMs,
                folder = item.folder
            )
        }
        val safeIndex = payload.currentIndex.coerceIn(0, queue.lastIndex)
        PlayerEngine.setShuffleEnabled(prefs.shuffleEnabled)
        PlayerEngine.setCrossfadeEnabled(prefs.crossfadeEnabled, prefs.crossfadeDurationMs)
        PlayerEngine.playQueue(context, queue, safeIndex, payload.positionMs)
        if (!payload.isPlaying) {
            PlayerEngine.togglePlayPause(context)
        }
        resumeRestored = true
    }

    fun filteredAudio(): List<MediaEntry> {
        val q = _uiState.value.search.trim().lowercase()
        return _uiState.value.audio.filter {
            q.isBlank() || it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)
        }
    }

    private fun startLibraryPolling() {
        libraryRefreshJob?.cancel()
        libraryRefreshJob = viewModelScope.launch {
            while (true) {
                delay(10 * 60 * 1000L)
                refreshLibrary()
            }
        }
    }

    fun filteredVideos(): List<MediaEntry> {
        val q = _uiState.value.search.trim().lowercase()
        return _uiState.value.videos.filter {
            q.isBlank() || it.title.lowercase().contains(q) || it.folder.orEmpty().lowercase().contains(q)
        }
    }

    fun favoriteIds(): Set<Long> = _uiState.value.favorites.map { it.mediaId }.toSet()
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
