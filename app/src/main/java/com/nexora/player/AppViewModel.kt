package com.nexora.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.player.audio.VolumeBoostSessionManager
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
import com.nexora.player.data.model.MostPlayedTrack
import com.nexora.player.data.model.SmartPlaylists
import com.nexora.player.data.model.SortMode
import com.nexora.player.data.online.OnlineMusicRepository
import com.nexora.player.data.online.OnlineTrack
import com.nexora.player.data.preferences.AppPreferences
import com.nexora.player.data.preferences.PreferencesRepository
import com.nexora.player.data.repository.MediaStoreRepository
import com.nexora.player.notifications.MediaLibraryNotifier
import com.nexora.player.playback.PlayerEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    val currentPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val favorites: List<FavoriteMediaEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val history: List<PlaybackHistoryEntity> = emptyList(),
    val mostPlayedTracks: List<MostPlayedTrack> = emptyList(),
    val onlineSavedTracks: List<OnlineSavedTrackEntity> = emptyList(),
    val onlineTracks: List<OnlineTrack> = emptyList(),
    val onlineLoading: Boolean = false,
    val onlineError: String? = null,
    val preferences: AppPreferences = AppPreferences()
)

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
    private var sleepTimerJob: Job? = null
    private var resumeAttempted = false

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
                val previousItem = _uiState.value.currentItem
                val nextState = _uiState.value.copy(
                    queue = snapshot.queue,
                    queueIndex = snapshot.currentIndex,
                    currentItem = snapshot.currentItem,
                    currentPositionMs = snapshot.currentPositionMs,
                    isPlaying = snapshot.isPlaying
                )
                _uiState.value = nextState

                val nowItem = snapshot.currentItem
                if (nowItem != null && nowItem != previousItem) {
                    recordPlay(nowItem)
                }

                if (nowItem != null && _uiState.value.preferences.resumePlaybackEnabled) {
                    viewModelScope.launch {
                        preferencesRepository.setLastPlaybackState(
                            mediaId = nowItem.id,
                            mediaKind = nowItem.kind.name,
                            positionMs = snapshot.currentPositionMs
                        )
                    }
                }
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
                    preferences = prefs
                )
                PlayerEngine.setShuffleMode(prefs.shuffleEnabled)
                PlayerEngine.setCrossfade(prefs.crossfadeEnabled, prefs.crossfadeDurationMs)
                PlayerEngine.setVolumeBoost(prefs.volumeBoostEnabled, prefs.volumeBoostGainMb)
                configureSleepTimer(prefs)
                refreshLibrary()
                refreshOnlineResultsIfNeeded()
                syncAutoPlaylist()
                if (!resumeAttempted) {
                    resumeAttempted = true
                    tryResumePlayback(prefs)
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
                    playlists = playlists,
                    history = history,
                    onlineSavedTracks = onlineSaved,
                    mostPlayedTracks = buildMostPlayedTracks(history, _uiState.value.audio)
                )
                syncAutoPlaylist()
            }
        }
    }

    private fun buildMostPlayedTracks(
        history: List<PlaybackHistoryEntity>,
        audioLibrary: List<MediaEntry>
    ): List<MostPlayedTrack> {
        val counts = history
            .filter { it.mediaKind == MediaKind.AUDIO.name }
            .groupingBy { it.mediaId }
            .eachCount()

        val audioById = audioLibrary.associateBy { it.id }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
            .mapNotNull { (mediaId, playCount) ->
                audioById[mediaId]?.let { MostPlayedTrack(media = it, playCount = playCount) }
            }
    }

    private suspend fun ensureAutoPlaylistExists(): PlaylistEntity {
        val existing = database.playlistsDao().getPlaylistByName(SmartPlaylists.MOST_PLAYED_NAME)
        if (existing != null) return existing
        val newId = database.playlistsDao().insertPlaylist(PlaylistEntity(name = SmartPlaylists.MOST_PLAYED_NAME))
        return PlaylistEntity(id = newId, name = SmartPlaylists.MOST_PLAYED_NAME)
    }

    private fun buildAutoPlaylistMedia(): List<MediaEntry> {
        return _uiState.value.mostPlayedTracks.map { it.media }
    }

    private suspend fun syncAutoPlaylist() {
        val autoTracks = buildAutoPlaylistMedia()
        val autoPlaylist = ensureAutoPlaylistExists()
        val existingItems = database.playlistsDao().observeItems(autoPlaylist.id).first()
        val autoKeys = autoTracks.map { it.id to it.kind.name }.toSet()
        val manualExtras = existingItems.filterNot { (it.mediaId to it.mediaKind) in autoKeys }

        val desiredEntries = autoTracks.mapIndexed { index, media -> media.toPlaylistItem(autoPlaylist.id, orderIndex = index) } +
            manualExtras.mapIndexed { index, item -> item.copy(orderIndex = autoTracks.size + index) }

        val currentKeys = existingItems.map { it.mediaId to it.mediaKind to it.orderIndex }
        val desiredKeys = desiredEntries.map { it.mediaId to it.mediaKind to it.orderIndex }
        if (currentKeys == desiredKeys) return

        database.playlistsDao().deleteItemsForPlaylist(autoPlaylist.id)
        desiredEntries.forEach { item -> database.playlistsDao().insertPlaylistItem(item) }
        database.playlistsDao().updatePlaylist(autoPlaylist.copy(updatedAt = System.currentTimeMillis()))
    }

    private fun configureSleepTimer(prefs: AppPreferences) {
        sleepTimerJob?.cancel()
        if (!prefs.sleepTimerEnabled || prefs.sleepTimerMinutes <= 0) return
        sleepTimerJob = viewModelScope.launch {
            delay(prefs.sleepTimerMinutes.toLong() * 60_000L)
            PlayerEngine.clear(context)
        }
    }

    private fun tryResumePlayback(prefs: AppPreferences) {
        val mediaId = prefs.lastPlaybackMediaId
        val positionMs = prefs.lastPlaybackPositionMs
        if (!prefs.resumePlaybackEnabled || mediaId <= 0L || positionMs <= 0L) return
        val target = _uiState.value.audio.firstOrNull { it.id == mediaId }
            ?: _uiState.value.videos.firstOrNull { it.id == mediaId }
            ?: return
        PlayerEngine.play(context, target)
        PlayerEngine.seekTo(context, positionMs)
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            val prefs = _uiState.value.preferences
            val hiddenIds = prefs.hiddenAudioIds
            val hiddenFolders = prefs.hiddenFolderPaths.map { normalizeFolderPath(it) }.toSet()
            val audio = mediaRepository.loadAudio(_uiState.value.audioSort)
                .filterNot { it.id in hiddenIds }
                .filterNot { it.folder.normalizeFolder().let { folder -> folder in hiddenFolders } }
            val videos = mediaRepository.loadVideos(_uiState.value.videoSort)
            _uiState.value = _uiState.value.copy(
                audio = audio,
                videos = videos,
                mostPlayedTracks = buildMostPlayedTracks(_uiState.value.history, audio)
            )

            if (_uiState.value.preferences.libraryChangeNotificationsEnabled) {
                MediaLibraryNotifier.maybeNotify(context, audio, videos)
            }
            syncAutoPlaylist()
            if (_uiState.value.preferences.resumePlaybackEnabled && !resumeAttempted) {
                resumeAttempted = true
                tryResumePlayback(_uiState.value.preferences)
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

    fun setThemeMode(mode: AppThemeMode) { viewModelScope.launch { preferencesRepository.setThemeMode(mode) } }
    fun setDynamicColor(enabled: Boolean) { viewModelScope.launch { preferencesRepository.setDynamicColor(enabled) } }
    fun setLyricsTranslationEnabled(enabled: Boolean) { viewModelScope.launch { preferencesRepository.setLyricsTranslationEnabled(enabled) } }

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

    fun setShuffleEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setShuffleEnabled(enabled) }
        PlayerEngine.setShuffleMode(enabled)
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setCrossfadeEnabled(enabled) }
        PlayerEngine.setCrossfade(enabled, _uiState.value.preferences.crossfadeDurationMs)
    }

    fun setCrossfadeDurationMs(durationMs: Int) {
        viewModelScope.launch { preferencesRepository.setCrossfadeDurationMs(durationMs) }
        PlayerEngine.setCrossfade(_uiState.value.preferences.crossfadeEnabled, durationMs)
    }

    fun setSleepTimerEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSleepTimerEnabled(enabled) }
        configureSleepTimer(_uiState.value.preferences.copy(sleepTimerEnabled = enabled))
    }

    fun setSleepTimerMinutes(minutes: Int) {
        viewModelScope.launch { preferencesRepository.setSleepTimerMinutes(minutes) }
        configureSleepTimer(_uiState.value.preferences.copy(sleepTimerMinutes = minutes))
    }

    fun setResumePlaybackEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setResumePlaybackEnabled(enabled) }
    }

    fun setLibraryChangeNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setLibraryChangeNotificationsEnabled(enabled) }
    }

    fun setOnlineMusicSearchEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setOnlineMusicSearchEnabled(enabled) }
        if (!enabled) {
            onlineSearchJob?.cancel()
            _uiState.value = _uiState.value.copy(onlineTracks = emptyList(), onlineLoading = false, onlineError = null)
        } else {
            refreshOnlineResultsIfNeeded()
        }
    }

    fun setHiddenFolderPaths(paths: Set<String>) {
        viewModelScope.launch { preferencesRepository.setHiddenFolderPaths(paths) }
        refreshLibrary()
    }

    fun addHiddenFolderPath(path: String) {
        viewModelScope.launch { preferencesRepository.addHiddenFolderPath(path) }
        refreshLibrary()
    }

    fun removeHiddenFolderPath(path: String) {
        viewModelScope.launch { preferencesRepository.removeHiddenFolderPath(path) }
        refreshLibrary()
    }

    fun clearHiddenFolderPaths() {
        viewModelScope.launch { preferencesRepository.clearHiddenFolderPaths() }
        refreshLibrary()
    }

    private fun scheduleOnlineSearch(query: String) {
        onlineSearchJob?.cancel()
        if (query.isBlank() || !_uiState.value.preferences.onlineMusicSearchEnabled) {
            _uiState.value = _uiState.value.copy(onlineTracks = emptyList(), onlineLoading = false, onlineError = null)
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

            _uiState.value = _uiState.value.copy(onlineLoading = false, onlineTracks = results, onlineError = null)
        }
    }

    private fun refreshOnlineResultsIfNeeded() {
        val query = _uiState.value.search
        if (query.isNotBlank() && _uiState.value.preferences.onlineMusicSearchEnabled) {
            scheduleOnlineSearch(query)
        }
    }

    fun playQueue(items: List<MediaEntry>, startIndex: Int = 0) {
        PlayerEngine.playQueue(context, items, startIndex)
        items.getOrNull(startIndex.coerceIn(0, items.lastIndex))?.let { recordPlay(it) }
    }

    fun play(item: MediaEntry) {
        PlayerEngine.play(context, item)
        recordPlay(item)
    }

    private fun recordPlay(item: MediaEntry) {
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

    fun togglePlayPause() { PlayerEngine.togglePlayPause(context) }
    fun playNext() { PlayerEngine.skipNext(context) }
    fun playPrevious() { PlayerEngine.skipPrevious(context) }
    fun jumpToQueueIndex(index: Int) { PlayerEngine.jumpTo(context, index) }
    fun removeQueueIndex(index: Int) { PlayerEngine.removeAt(context, index) }
    fun clearQueue() { PlayerEngine.clear(context) }

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
        viewModelScope.launch { database.playlistsDao().insertPlaylist(PlaylistEntity(name = name.trim())) }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        if (playlist.name == SmartPlaylists.MOST_PLAYED_NAME) return
        viewModelScope.launch {
            database.playlistsDao().deleteItemsForPlaylist(playlist.id)
            database.playlistsDao().deletePlaylist(playlist.id)
        }
    }

    fun addToPlaylist(playlist: PlaylistEntity, entry: MediaEntry) {
        viewModelScope.launch {
            val existing = database.playlistsDao().observeItems(playlist.id).first()
            val duplicate = existing.any { it.mediaId == entry.id && it.mediaKind == entry.kind.name }
            if (duplicate) return@launch
            val next = database.playlistsDao().nextOrderIndex(playlist.id) + 1
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

    fun playlistItems(playlistId: Long): Flow<List<PlaylistItemEntity>> = database.playlistsDao().observeItems(playlistId)
    fun playlistPreviewItems(playlistId: Long): Flow<List<PlaylistItemEntity>> = database.playlistsDao().observeItems(playlistId).map { it.take(4) }

    fun removeFromPlaylist(itemId: Long) { viewModelScope.launch { database.playlistsDao().deletePlaylistItem(itemId) } }

    fun playFavoriteQueue(favorites: List<FavoriteMediaEntity>, favorite: FavoriteMediaEntity) {
        val audioFavorites = favorites.map { it.toMediaEntry() }
        val selected = favorite.toMediaEntry()
        val startIndex = audioFavorites.indexOfFirst { it.id == selected.id && it.kind == selected.kind }
        if (startIndex >= 0) playQueue(audioFavorites, startIndex) else play(selected)
    }

    fun playOnlineQueue(tracks: List<OnlineTrack>, track: OnlineTrack) {
        val queue = tracks.map { it.toMediaEntry() }
        val startIndex = queue.indexOfFirst { it.id == track.stableMediaId }
        if (startIndex >= 0) playQueue(queue, startIndex) else play(track.toMediaEntry())
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
    fun playFavorite(favorite: FavoriteMediaEntity) { play(favorite.toMediaEntry()) }

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

    fun playPlaylistItem(item: PlaylistItemEntity) { play(item.toMediaEntry()) }

    private fun FavoriteMediaEntity.toMediaEntry(): MediaEntry = MediaEntry(
        id = mediaId,
        kind = if (mediaKind == MediaKind.VIDEO.name) MediaKind.VIDEO else MediaKind.AUDIO,
        uri = android.net.Uri.parse(uriString),
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs
    )

    private fun PlaylistItemEntity.toMediaEntry(): MediaEntry = MediaEntry(
        id = mediaId,
        kind = if (mediaKind == MediaKind.VIDEO.name) MediaKind.VIDEO else MediaKind.AUDIO,
        uri = android.net.Uri.parse(uriString),
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs
    )

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

    fun filteredAudio(): List<MediaEntry> {
        val q = _uiState.value.search.trim().lowercase()
        return _uiState.value.audio.filter {
            q.isBlank() || it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)
        }
    }

    fun filteredVideos(): List<MediaEntry> {
        val q = _uiState.value.search.trim().lowercase()
        return _uiState.value.videos.filter {
            q.isBlank() || it.title.lowercase().contains(q) || it.folder.orEmpty().lowercase().contains(q)
        }
    }

    fun favoriteIds(): Set<Long> = _uiState.value.favorites.map { it.mediaId }.toSet()

    private fun normalizeFolderPath(path: String?): String = path.orEmpty().trim().lowercase().trimEnd('/')

    private fun String?.normalizeFolder(): String = normalizeFolderPath(this)

    private fun MediaEntry.toPlaylistItem(playlistId: Long, orderIndex: Int): PlaylistItemEntity = PlaylistItemEntity(
        playlistId = playlistId,
        mediaId = id,
        mediaKind = kind.name,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        uriString = uri.toString(),
        orderIndex = orderIndex
    )
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
