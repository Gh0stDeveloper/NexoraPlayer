package com.nexora.player

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.player.data.local.FavoriteMediaEntity
import com.nexora.player.data.local.PlaylistEntity
import com.nexora.player.data.local.PlaylistItemEntity
import com.nexora.player.data.model.MediaEntry
import com.nexora.player.data.model.AppDestination
import com.nexora.player.data.model.AppLanguage
import com.nexora.player.data.model.AppThemeMode
import com.nexora.player.data.model.MediaKind
import com.nexora.player.ui.components.BottomPlayerBar
import com.nexora.player.ui.components.ux.IosBottomTabBar
import com.nexora.player.ui.components.GreetingBanner
import com.nexora.player.ui.screens.FavoritesScreen
import com.nexora.player.ui.screens.FolderManagerScreen
import com.nexora.player.ui.screens.MusicScreen
import com.nexora.player.ui.screens.NowPlayingScreen
import com.nexora.player.ui.screens.PlaylistDetailScreen
import com.nexora.player.ui.screens.PlaylistsScreen
import com.nexora.player.ui.screens.StatsScreen
import com.nexora.player.ui.screens.ThemeSelectionScreen
import com.nexora.player.ui.screens.LanguageSelectionScreen
import com.nexora.player.ui.screens.NotificationCenterScreen
import com.nexora.player.ui.screens.SearchResultsScreen
import com.nexora.player.ui.screens.ReleaseNotesDialog
import com.nexora.player.ui.screens.SettingsScreen
import com.nexora.player.ui.screens.VideoScreen
import com.nexora.player.ui.theme.NexoraTheme
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : AppCompatActivity() {

    private val viewModel: AppViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshLibrary() }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        requestMediaPermissions()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            var showNowPlaying by rememberSaveable { mutableStateOf(false) }
            var searchExpanded by rememberSaveable { mutableStateOf(false) }
            var selectedPlaylistId by rememberSaveable { mutableStateOf<Long?>(null) }
            var showFolderManager by rememberSaveable { mutableStateOf(false) }
            var showNotificationCenter by rememberSaveable { mutableStateOf(false) }
            var showStatsScreen by rememberSaveable { mutableStateOf(false) }
            var showThemeScreen by rememberSaveable { mutableStateOf(false) }
            var showLanguageScreen by rememberSaveable { mutableStateOf(false) }
            var showSettingsScreen by rememberSaveable { mutableStateOf(false) }
            val greeting = rememberGreeting()
            val availableUpdate = state.updateInfo
            val shouldShowUpdateDialog = availableUpdate?.available == true && (
                availableUpdate.required ||
                    state.forceShowUpdateDialog ||
                    (!state.updateDialogDismissedInSession && state.preferences.postponedUpdateVersionCode < availableUpdate.latestVersion.versionCode)
            )


            NexoraTheme(
                darkTheme = when (state.preferences.themeMode) {
                    AppThemeMode.SYSTEM, AppThemeMode.MATERIAL_YOU -> androidx.compose.foundation.isSystemInDarkTheme()
                    AppThemeMode.LIGHT, AppThemeMode.IOS_LIGHT -> false
                    AppThemeMode.DARK, AppThemeMode.NEXORA_DARK, AppThemeMode.AMOLED_BLACK, AppThemeMode.FLAMINGO, AppThemeMode.NEON -> true
                },
                dynamicColor = state.preferences.dynamicColor,
                themeMode = state.preferences.themeMode
            ) {
                val destinations = listOf(
                    AppDestination.MUSIC,
                    AppDestination.VIDEOS,
                    AppDestination.PLAYLISTS
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        Column(
                            modifier = Modifier
                                .statusBarsPadding()
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            GreetingBanner(
                                greeting = greeting,
                                query = state.search,
                                expanded = searchExpanded,
                                onExpandedChange = { searchExpanded = it },
                                onQueryChange = { viewModel.setSearch(it) },
                                modifier = Modifier.fillMaxWidth(),
                                sortMode = if (state.selectedDestination == AppDestination.MUSIC) state.audioSort else null,
                                onSortSelected = viewModel::setAudioSort,
                                onSettingsClick = {
                                    searchExpanded = false
                                    showSettingsScreen = true
                                }
                            )
                        }
                    },
                    bottomBar = {
                        Column(modifier = Modifier.navigationBarsPadding()) {
                            BottomPlayerBar(
                                current = state.currentItem,
                                isPlaying = state.isPlaying,
                                onClick = { showNowPlaying = true },
                                onPrevious = viewModel::playPrevious,
                                onTogglePlay = viewModel::togglePlayPause,
                                onNext = viewModel::playNext
                            )
                            IosBottomTabBar(
                                destinations = destinations,
                                selected = if (state.selectedDestination in destinations) state.selectedDestination else AppDestination.MUSIC,
                                onDestinationSelected = { destination ->
                                    searchExpanded = false
                                    selectedPlaylistId = null
                                    viewModel.setDestination(destination)
                                },
                                iconFor = ::iconFor
                            )
                        }
                    }
                ) { padding ->
                    AppContent(
                        modifier = Modifier.padding(padding),
                        viewModel = viewModel,
                        state = state,
                        selectedPlaylistId = selectedPlaylistId,
                        showFolderManager = showFolderManager,
                        showNotificationCenter = showNotificationCenter,
                        showStatsScreen = showStatsScreen,
                        showThemeScreen = showThemeScreen,
                        showLanguageScreen = showLanguageScreen,
                        showSettingsScreen = showSettingsScreen,
                        onOpenPlaylist = { selectedPlaylistId = it.id },
                        onClosePlaylist = { selectedPlaylistId = null },
                        onOpenFolderManager = { showFolderManager = true },
                        onCloseFolderManager = { showFolderManager = false },
                        onOpenNotificationCenter = { showNotificationCenter = true },
                        onCloseNotificationCenter = { showNotificationCenter = false },
                        onOpenStats = { showStatsScreen = true },
                        onCloseStats = { showStatsScreen = false },
                        onOpenThemeScreen = { showThemeScreen = true },
                        onCloseThemeScreen = { showThemeScreen = false },
                        onOpenLanguageScreen = { showLanguageScreen = true },
                        onCloseLanguageScreen = { showLanguageScreen = false },
                        onCloseSettingsScreen = { showSettingsScreen = false }
                    )
                }

                if (shouldShowUpdateDialog && availableUpdate != null) {
                    ReleaseNotesDialog(
                        updateInfo = availableUpdate,
                        onDownload = {
                            viewModel.downloadAndInstallUpdate(availableUpdate)
                        },
                        onLater = {
                            viewModel.dismissUpdateDialog(postpone = true)
                        },
                        installState = state.updateInstallState,
                        onOpenInBrowser = { openExternalUrl(availableUpdate.urls.download) },
                        onAuthorizeInstallPermission = viewModel::openInstallPermissionSettings,
                        onClearInstallMessage = viewModel::clearUpdateInstallMessage
                    )
                }

                if (showNowPlaying) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { showNowPlaying = false },
                        properties = androidx.compose.ui.window.DialogProperties(
                            usePlatformDefaultWidth = false
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            NowPlayingScreen(
                                modifier = Modifier.fillMaxSize(),
                                onClose = { showNowPlaying = false }
                            )
                        }
                    }
                }

                NexoraVolumeOverlay(
                    visible = state.nexoraVolumeOverlayVisible,
                    percent = state.nexoraVolumePercent,
                    boosted = state.nexoraVolumeBoosted
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.resumeUpdateAfterInstallPermission()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN) viewModel.adjustNexoraVolume(+1)
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) viewModel.adjustNexoraVolume(-1)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event?.repeatCount == 0) viewModel.adjustNexoraVolume(+1)
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event?.repeatCount == 0) viewModel.adjustNexoraVolume(-1)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> true
            else -> super.onKeyUp(keyCode, event)
        }
    }

    private fun openExternalUrl(url: String) {
        val safeUrl = url.ifBlank { BuildConfig.NEXORA_SERVER_URL }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl))
        runCatching { startActivity(intent) }
    }

    private fun requestMediaPermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppContent(
    modifier: Modifier = Modifier,
    viewModel: AppViewModel,
    state: AppUiState,
    selectedPlaylistId: Long?,
    showFolderManager: Boolean,
    showNotificationCenter: Boolean,
    showStatsScreen: Boolean,
    showThemeScreen: Boolean,
    showLanguageScreen: Boolean,
    showSettingsScreen: Boolean,
    onOpenPlaylist: (PlaylistEntity) -> Unit,
    onClosePlaylist: () -> Unit,
    onOpenFolderManager: () -> Unit,
    onCloseFolderManager: () -> Unit,
    onOpenNotificationCenter: () -> Unit,
    onCloseNotificationCenter: () -> Unit,
    onOpenStats: () -> Unit,
    onCloseStats: () -> Unit,
    onOpenThemeScreen: () -> Unit,
    onCloseThemeScreen: () -> Unit,
    onOpenLanguageScreen: () -> Unit,
    onCloseLanguageScreen: () -> Unit,
    onCloseSettingsScreen: () -> Unit
) {
    if (showFolderManager) {
        BackHandler(onBack = onCloseFolderManager)
        FolderManagerScreen(
            modifier = modifier,
            folders = state.folderSummaries,
            hiddenFolders = state.preferences.hiddenFolders,
            onBack = onCloseFolderManager,
            onHideFolder = viewModel::addHiddenFolder,
            onShowFolder = viewModel::removeHiddenFolder,
            onHideSmallFolders = { viewModel.hideSmallFolders() },
            onHideSuggestedNoiseFolders = viewModel::hideSuggestedNoiseFolders,
            onRestoreAll = viewModel::clearHiddenFolders
        )
        return
    }

    if (showNotificationCenter) {
        BackHandler(onBack = onCloseNotificationCenter)
        NotificationCenterScreen(
            modifier = modifier,
            notices = state.remoteNotices,
            onBack = onCloseNotificationCenter,
            onClearAll = viewModel::clearRemoteNotices,
            onMarkRead = viewModel::markNoticeRead
        )
        return
    }

    if (showStatsScreen) {
        BackHandler(onBack = onCloseStats)
        StatsScreen(
            modifier = modifier,
            stats = state.playbackStats,
            onBack = onCloseStats
        )
        return
    }

    if (showThemeScreen) {
        BackHandler(onBack = onCloseThemeScreen)
        ThemeSelectionScreen(
            modifier = modifier,
            selectedTheme = state.preferences.themeMode,
            dynamicColor = state.preferences.dynamicColor,
            onBack = onCloseThemeScreen,
            onThemeSelected = viewModel::setThemeMode,
            onDynamicColorChange = viewModel::setDynamicColor
        )
        return
    }

    if (showLanguageScreen) {
        BackHandler(onBack = onCloseLanguageScreen)
        LanguageSelectionScreen(
            modifier = modifier,
            selectedLanguage = rememberAppLanguage(),
            onBack = onCloseLanguageScreen,
            onLanguageSelected = { language ->
                applyLanguage(language)
                onCloseLanguageScreen()
            }
        )
        return
    }

    if (showSettingsScreen) {
        BackHandler(onBack = onCloseSettingsScreen)
        SettingsScreen(
            modifier = modifier,
            themeMode = state.preferences.themeMode,
            dynamicColor = state.preferences.dynamicColor,
            hiddenAudioCount = state.preferences.hiddenAudioIds.size,
            onlineMusicSearchEnabled = state.preferences.onlineMusicSearchEnabled,
            lyricsTranslationEnabled = state.preferences.lyricsTranslationEnabled,
            volumeBoostEnabled = state.preferences.volumeBoostEnabled,
            libraryChangeNotificationsEnabled = state.preferences.libraryChangeNotificationsEnabled,
            shuffleEnabled = state.preferences.shuffleEnabled,
            repeatMode = state.preferences.repeatMode,
            resumePlaybackEnabled = state.preferences.resumePlaybackEnabled,
            crossfadeEnabled = state.preferences.crossfadeEnabled,
            crossfadeDurationMs = state.preferences.crossfadeDurationMs,
            sleepTimerEnabled = state.preferences.sleepTimerEnabled,
            sleepTimerMinutes = state.preferences.sleepTimerMinutes,
            sleepTimerStopAtEndOfTrack = state.preferences.sleepTimerStopAtEndOfTrack,
            hiddenFolders = state.preferences.hiddenFolders.toList(),
            shareUrl = state.shareUrl,
            updateChecking = state.updateChecking,
            updateError = state.updateError,
            currentLanguage = rememberAppLanguage(),
            onBack = onCloseSettingsScreen,
            onThemeChange = viewModel::setThemeMode,
            onDynamicColorChange = viewModel::setDynamicColor,
            onOnlineMusicSearchChange = viewModel::setOnlineMusicSearchEnabled,
            onLyricsTranslationChange = viewModel::setLyricsTranslationEnabled,
            onVolumeBoostChange = viewModel::setVolumeBoostEnabled,
            onLibraryChangeNotificationsChange = viewModel::setLibraryChangeNotificationsEnabled,
            onShuffleChange = viewModel::setShuffleEnabled,
            onRepeatModeChange = viewModel::setRepeatMode,
            onResumePlaybackChange = viewModel::setResumePlaybackEnabled,
            onCrossfadeChange = viewModel::setCrossfadeEnabled,
            onCrossfadeDurationChange = viewModel::setCrossfadeDurationMs,
            onStartSleepTimer = viewModel::startSleepTimer,
            onStartSleepTimerAtEndOfTrack = viewModel::startSleepTimerAtEndOfTrack,
            onCancelSleepTimer = viewModel::cancelSleepTimer,
            onLanguageChange = ::applyLanguage,
            onRestoreHiddenAudio = viewModel::restoreHiddenAudio,
            onAddHiddenFolder = viewModel::addHiddenFolder,
            onRemoveHiddenFolder = viewModel::removeHiddenFolder,
            onClearHiddenFolders = viewModel::clearHiddenFolders,
            onOpenFolderManager = onOpenFolderManager,
            onOpenNotificationCenter = onOpenNotificationCenter,
            onOpenStats = onOpenStats,
            onOpenThemeSelection = onOpenThemeScreen,
            onOpenLanguageSelection = onOpenLanguageScreen,
            onCheckUpdates = { viewModel.checkForUpdates(showDialogOnAvailable = true) },
            onClearUpdateMessage = viewModel::clearUpdateStatusMessage
        )
        return
    }

    if (state.search.isNotBlank()) {
        BackHandler { viewModel.setSearch("") }
        SearchResultsScreen(
            modifier = modifier,
            query = state.search,
            audio = viewModel.filteredAudio(),
            videos = viewModel.filteredVideos(),
            onlineEnabled = state.preferences.onlineMusicSearchEnabled,
            onlineLoading = state.onlineLoading,
            onlineError = state.onlineError,
            onlineTracks = state.onlineTracks,
            savedOnlineKeys = state.onlineSavedTracks.map { "${it.providerId}:${it.sourceId}" }.toSet(),
            onPlayAudio = viewModel::playFromLibrary,
            onPlayVideo = viewModel::playFromLibrary,
            onPlayOnline = viewModel::playOnlineQueue,
            onToggleFavorite = viewModel::toggleFavorite,
            onToggleSaveOnline = viewModel::toggleSavedOnlineTrack,
            favoriteIds = viewModel.favoriteIds()
        )
        return
    }

    selectedPlaylistId?.let { playlistId ->
        val playlist = state.playlists.firstOrNull { it.id == playlistId }
        if (playlist != null) {
            val playlistItems by viewModel.playlistItems(playlist.id)
                .collectAsStateWithLifecycle(initialValue = emptyList())

            BackHandler(onBack = onClosePlaylist)
            PlaylistDetailScreen(
                modifier = modifier,
                playlist = playlist,
                playlistItems = playlistItems,
                availableSongs = viewModel.filteredAudio(),
                onBack = onClosePlaylist,
                onPlayItem = viewModel::playPlaylistQueue,
                onRemoveItem = { item ->
                    if (playlist.id == NEXORA_LIKED_PLAYLIST_ID) {
                        viewModel.removeFavoritePlaylistItem(item)
                    } else {
                        viewModel.removeFromPlaylist(item.id)
                    }
                },
                onAddSongs = { songs ->
                    viewModel.addToPlaylist(playlist, songs)
                },
                onRenamePlaylist = { name -> viewModel.renamePlaylist(playlist, name) },
                onDuplicatePlaylist = { viewModel.duplicatePlaylist(playlist, playlistItems) },
                onExportPlaylist = { viewModel.exportPlaylist(playlist, playlistItems) },
                onMoveItem = { from, to -> viewModel.movePlaylistItem(playlistItems, from, to) },
                onPlayShuffle = { items -> viewModel.playQueue(items.map { it.toMediaEntryMain() }.shuffled(), 0) },
                isAutoPlaylist = playlist.id < 0,
                canRemoveItems = playlist.id == NEXORA_LIKED_PLAYLIST_ID
            )
            return
        } else {
            onClosePlaylist()
        }
    }

    DestinationPagerContent(
        modifier = modifier,
        state = state,
        viewModel = viewModel,
        onOpenPlaylist = onOpenPlaylist,
        onOpenFolderManager = onOpenFolderManager,
        onOpenNotificationCenter = onOpenNotificationCenter,
        onOpenStats = onOpenStats,
        onOpenThemeScreen = onOpenThemeScreen,
        onOpenLanguageScreen = onOpenLanguageScreen
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DestinationPagerContent(
    modifier: Modifier,
    state: AppUiState,
    viewModel: AppViewModel,
    onOpenPlaylist: (PlaylistEntity) -> Unit,
    onOpenFolderManager: () -> Unit,
    onOpenNotificationCenter: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenThemeScreen: () -> Unit,
    onOpenLanguageScreen: () -> Unit
) {
    val destinations = listOf(
        AppDestination.MUSIC,
        AppDestination.VIDEOS,
        AppDestination.PLAYLISTS
    )

    val pagerState = rememberPagerState(
        initialPage = destinations.indexOf(state.selectedDestination).coerceAtLeast(0),
        pageCount = { destinations.size }
    )

    LaunchedEffect(state.selectedDestination) {
        val target = destinations.indexOf(state.selectedDestination).coerceAtLeast(0)
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val destination = destinations[pagerState.currentPage]
        if (state.selectedDestination != destination) {
            viewModel.setDestination(destination)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize()
    ) { page ->
        when (destinations[page]) {
            AppDestination.MUSIC -> MusicScreen(
                modifier = Modifier.fillMaxSize(),
                items = viewModel.filteredAudio(),
                favorites = viewModel.favoriteIds(),
                playlists = state.playlists,
                sortMode = state.audioSort,
                onPlay = viewModel::playFromLibrary,
                onToggleFavorite = viewModel::toggleFavorite,
                onAddToPlaylist = viewModel::addToPlaylist,
                onHideFromLibrary = viewModel::hideFromLibrary,
                onDeleteFromLibrary = viewModel::deleteFromLibrary,
                onRefresh = viewModel::refreshLibrary,
                onSortSelected = viewModel::setAudioSort
            )

            AppDestination.VIDEOS -> VideoScreen(
                modifier = Modifier.fillMaxSize(),
                items = viewModel.filteredVideos(),
                sortMode = state.videoSort,
                onPlay = viewModel::playFromLibrary,
                onRefresh = viewModel::refreshLibrary,
                onSortSelected = viewModel::setVideoSort,
                onEditVideo = viewModel::editMediaFile,
                onHideVideo = viewModel::hideFromLibrary,
                onDeleteVideo = viewModel::onMediaDeleted
            )

            AppDestination.PLAYLISTS -> PlaylistsScreen(
                modifier = Modifier.fillMaxSize(),
                playlists = state.playlists,
                onCreatePlaylist = viewModel::createPlaylist,
                onDeletePlaylist = viewModel::deletePlaylist,
                onOpenPlaylist = onOpenPlaylist,
                playlistPreviewItems = { playlistId -> viewModel.playlistPreviewItems(playlistId) }
            )

            AppDestination.FAVORITES -> FavoritesScreen(
                modifier = Modifier.fillMaxSize(),
                favorites = state.favorites.filter { it.mediaKind == com.nexora.player.data.model.MediaKind.AUDIO.name },
                onPlayFavoriteQueue = viewModel::playFavoriteQueue,
                onToggleFavorite = { favorite -> viewModel.toggleFavorite(favorite.toMediaEntry()) }
            )

            AppDestination.SETTINGS -> SettingsScreen(
                modifier = Modifier.fillMaxSize(),
                themeMode = state.preferences.themeMode,
                dynamicColor = state.preferences.dynamicColor,
                hiddenAudioCount = state.preferences.hiddenAudioIds.size,
                onlineMusicSearchEnabled = state.preferences.onlineMusicSearchEnabled,
                lyricsTranslationEnabled = state.preferences.lyricsTranslationEnabled,
                volumeBoostEnabled = state.preferences.volumeBoostEnabled,
                libraryChangeNotificationsEnabled = state.preferences.libraryChangeNotificationsEnabled,
                shuffleEnabled = state.preferences.shuffleEnabled,
                repeatMode = state.preferences.repeatMode,
                resumePlaybackEnabled = state.preferences.resumePlaybackEnabled,
                crossfadeEnabled = state.preferences.crossfadeEnabled,
                crossfadeDurationMs = state.preferences.crossfadeDurationMs,
                sleepTimerEnabled = state.preferences.sleepTimerEnabled,
                sleepTimerMinutes = state.preferences.sleepTimerMinutes,
                sleepTimerStopAtEndOfTrack = state.preferences.sleepTimerStopAtEndOfTrack,
                hiddenFolders = state.preferences.hiddenFolders.toList(),
                shareUrl = state.shareUrl,
                updateChecking = state.updateChecking,
                updateError = state.updateError,
                currentLanguage = rememberAppLanguage(),
                onThemeChange = viewModel::setThemeMode,
                onDynamicColorChange = viewModel::setDynamicColor,
                onOnlineMusicSearchChange = viewModel::setOnlineMusicSearchEnabled,
                onLyricsTranslationChange = viewModel::setLyricsTranslationEnabled,
                onVolumeBoostChange = viewModel::setVolumeBoostEnabled,
                onLibraryChangeNotificationsChange = viewModel::setLibraryChangeNotificationsEnabled,
                onShuffleChange = viewModel::setShuffleEnabled,
                onRepeatModeChange = viewModel::setRepeatMode,
                onResumePlaybackChange = viewModel::setResumePlaybackEnabled,
                onCrossfadeChange = viewModel::setCrossfadeEnabled,
                onCrossfadeDurationChange = viewModel::setCrossfadeDurationMs,
                onStartSleepTimer = viewModel::startSleepTimer,
                onStartSleepTimerAtEndOfTrack = viewModel::startSleepTimerAtEndOfTrack,
                onCancelSleepTimer = viewModel::cancelSleepTimer,
                onLanguageChange = ::applyLanguage,
                onRestoreHiddenAudio = viewModel::restoreHiddenAudio,
                onAddHiddenFolder = viewModel::addHiddenFolder,
                onRemoveHiddenFolder = viewModel::removeHiddenFolder,
                onClearHiddenFolders = viewModel::clearHiddenFolders,
                onOpenFolderManager = onOpenFolderManager,
                onOpenNotificationCenter = onOpenNotificationCenter,
                onOpenStats = onOpenStats,
                onOpenThemeSelection = onOpenThemeScreen,
                onOpenLanguageSelection = onOpenLanguageScreen,
                onCheckUpdates = { viewModel.checkForUpdates(showDialogOnAvailable = true) },
                onClearUpdateMessage = viewModel::clearUpdateStatusMessage
            )

            AppDestination.QUEUE, AppDestination.HISTORY -> MusicScreen(
                modifier = Modifier.fillMaxSize(),
                items = viewModel.filteredAudio(),
                favorites = viewModel.favoriteIds(),
                playlists = state.playlists,
                sortMode = state.audioSort,
                onPlay = viewModel::playFromLibrary,
                onToggleFavorite = viewModel::toggleFavorite,
                onAddToPlaylist = viewModel::addToPlaylist,
                onHideFromLibrary = viewModel::hideFromLibrary,
                onDeleteFromLibrary = viewModel::deleteFromLibrary,
                onRefresh = viewModel::refreshLibrary,
                onSortSelected = viewModel::setAudioSort
            )
        }
    }
}
private fun FavoriteMediaEntity.toMediaEntry(): MediaEntry = MediaEntry(
    id = mediaId,
    kind = if (mediaKind == MediaKind.VIDEO.name) MediaKind.VIDEO else MediaKind.AUDIO,
    uri = android.net.Uri.parse(uriString),
    title = title,
    album = album,
    artist = artist,
    durationMs = durationMs
)

private fun PlaylistItemEntity.toMediaEntryMain(): MediaEntry = MediaEntry(
    id = mediaId,
    kind = if (mediaKind == MediaKind.VIDEO.name) MediaKind.VIDEO else MediaKind.AUDIO,
    uri = android.net.Uri.parse(uriString),
    title = title,
    album = album,
    artist = artist,
    durationMs = durationMs
)

@Composable
private fun NexoraVolumeOverlay(
    visible: Boolean,
    percent: Int,
    boosted: Boolean
) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 84.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xEE090B14),
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .fillMaxWidth(0.72f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (boosted) "Volumen Nexora · $percent%" else "Volumen · $percent%",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
                LinearProgressIndicator(
                    progress = { percent.coerceIn(0, 150) / 150f },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (boosted) Color(0xFFF54047) else Color.White,
                    trackColor = Color.White.copy(alpha = 0.18f)
                )
                Text(
                    text = if (boosted) "Amplificado hasta 150%" else "Volumen del sistema interceptado por Nexora",
                    color = Color.White.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun rememberGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> stringResource(com.nexora.player.R.string.greeting_morning)
        in 12..18 -> stringResource(com.nexora.player.R.string.greeting_afternoon)
        else -> stringResource(com.nexora.player.R.string.greeting_evening)
    }
}

@Composable
private fun rememberAppLanguage(): AppLanguage {
    val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    return AppLanguage.fromTag(tags)
}

private fun applyLanguage(language: AppLanguage) {
    val locales = when (val tag = language.tag) {
        null -> LocaleListCompat.getEmptyLocaleList()
        else -> LocaleListCompat.forLanguageTags(tag)
    }
    AppCompatDelegate.setApplicationLocales(locales)
}

private fun iconFor(destination: AppDestination) = when (destination) {
    AppDestination.MUSIC -> Icons.Filled.LibraryMusic
    AppDestination.VIDEOS -> Icons.Filled.Movie
    AppDestination.PLAYLISTS -> Icons.AutoMirrored.Filled.PlaylistPlay
    AppDestination.FAVORITES -> Icons.Filled.Favorite
    AppDestination.SETTINGS -> Icons.Filled.Settings
    AppDestination.QUEUE -> Icons.Filled.LibraryMusic
    AppDestination.HISTORY -> Icons.Filled.History
}
