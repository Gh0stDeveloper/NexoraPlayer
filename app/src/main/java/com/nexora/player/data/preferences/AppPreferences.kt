package com.nexora.player.data.preferences

import com.nexora.player.data.model.AppDestination
import com.nexora.player.data.model.AppThemeMode
import com.nexora.player.data.model.SortMode

data class AppPreferences(
    val audioSort: SortMode = SortMode.DATE_ADDED_DESC,
    val videoSort: SortMode = SortMode.DATE_ADDED_DESC,
    val lastDestination: AppDestination = AppDestination.MUSIC,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val onlineMusicSearchEnabled: Boolean = true,
    val lyricsTranslationEnabled: Boolean = true,
    val volumeBoostEnabled: Boolean = false,
    val volumeBoostGainMb: Int = 600,
    val libraryChangeNotificationsEnabled: Boolean = true,
    val shuffleEnabled: Boolean = false,
    val resumePlaybackEnabled: Boolean = true,
    val crossfadeEnabled: Boolean = false,
    val crossfadeDurationMs: Int = 1200,
    val sleepTimerEnabled: Boolean = false,
    val sleepTimerMinutes: Int = 30,
    val sleepTimerEndAtMs: Long = 0L,
    val playbackSessionJson: String = "",
    val hiddenAudioIds: Set<Long> = emptySet(),
    val hiddenFolders: Set<String> = emptySet()
)
