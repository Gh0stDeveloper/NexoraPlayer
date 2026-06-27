package com.nexora.player.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nexora.player.data.model.AppDestination
import com.nexora.player.data.model.AppThemeMode
import com.nexora.player.data.model.SortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nexora_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val AUDIO_SORT = stringPreferencesKey("audio_sort")
        val VIDEO_SORT = stringPreferencesKey("video_sort")
        val LAST_DESTINATION = stringPreferencesKey("last_destination")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ONLINE_MUSIC_SEARCH_ENABLED = booleanPreferencesKey("online_music_search_enabled")
        val LYRICS_TRANSLATION_ENABLED = booleanPreferencesKey("lyrics_translation_enabled")
        val VOLUME_BOOST_ENABLED = booleanPreferencesKey("volume_boost_enabled")
        val VOLUME_BOOST_GAIN_MB = stringPreferencesKey("volume_boost_gain_mb")
        val LIBRARY_CHANGE_NOTIFICATIONS_ENABLED = booleanPreferencesKey("library_change_notifications_enabled")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val CROSSFADE_ENABLED = booleanPreferencesKey("crossfade_enabled")
        val CROSSFADE_DURATION_MS = intPreferencesKey("crossfade_duration_ms")
        val SLEEP_TIMER_ENABLED = booleanPreferencesKey("sleep_timer_enabled")
        val SLEEP_TIMER_MINUTES = intPreferencesKey("sleep_timer_minutes")
        val RESUME_PLAYBACK_ENABLED = booleanPreferencesKey("resume_playback_enabled")
        val LAST_PLAYBACK_MEDIA_ID = stringPreferencesKey("last_playback_media_id")
        val LAST_PLAYBACK_MEDIA_KIND = stringPreferencesKey("last_playback_media_kind")
        val LAST_PLAYBACK_POSITION_MS = stringPreferencesKey("last_playback_position_ms")
        val HIDDEN_AUDIO_IDS = stringSetPreferencesKey("hidden_audio_ids")
        val HIDDEN_FOLDER_PATHS = stringSetPreferencesKey("hidden_folder_paths")
    }

    val preferences: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            audioSort = prefs.stringValue(Keys.AUDIO_SORT, SortMode.DATE_ADDED_DESC.name).toSortMode(),
            videoSort = prefs.stringValue(Keys.VIDEO_SORT, SortMode.DATE_ADDED_DESC.name).toSortMode(),
            lastDestination = prefs.stringValue(Keys.LAST_DESTINATION, AppDestination.MUSIC.name).toDestination(),
            themeMode = prefs.stringValue(Keys.THEME_MODE, AppThemeMode.SYSTEM.name).toThemeMode(),
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            onlineMusicSearchEnabled = prefs[Keys.ONLINE_MUSIC_SEARCH_ENABLED] ?: true,
            lyricsTranslationEnabled = prefs[Keys.LYRICS_TRANSLATION_ENABLED] ?: true,
            volumeBoostEnabled = prefs[Keys.VOLUME_BOOST_ENABLED] ?: false,
            volumeBoostGainMb = prefs.stringValue(Keys.VOLUME_BOOST_GAIN_MB, "600").toIntOrNull()?.coerceIn(0, 1800) ?: 600,
            libraryChangeNotificationsEnabled = prefs[Keys.LIBRARY_CHANGE_NOTIFICATIONS_ENABLED] ?: true,
            shuffleEnabled = prefs[Keys.SHUFFLE_ENABLED] ?: false,
            crossfadeEnabled = prefs[Keys.CROSSFADE_ENABLED] ?: false,
            crossfadeDurationMs = prefs[Keys.CROSSFADE_DURATION_MS] ?: 2500,
            sleepTimerEnabled = prefs[Keys.SLEEP_TIMER_ENABLED] ?: false,
            sleepTimerMinutes = prefs[Keys.SLEEP_TIMER_MINUTES] ?: 15,
            resumePlaybackEnabled = prefs[Keys.RESUME_PLAYBACK_ENABLED] ?: true,
            lastPlaybackMediaId = prefs.stringValue(Keys.LAST_PLAYBACK_MEDIA_ID, "-1").toLongOrNull() ?: -1L,
            lastPlaybackMediaKind = prefs.stringValue(Keys.LAST_PLAYBACK_MEDIA_KIND, ""),
            lastPlaybackPositionMs = prefs.stringValue(Keys.LAST_PLAYBACK_POSITION_MS, "0").toLongOrNull() ?: 0L,
            hiddenAudioIds = prefs.stringSetValue(Keys.HIDDEN_AUDIO_IDS, emptySet())
                .mapNotNull { it.toLongOrNull() }
                .toSet(),
            hiddenFolderPaths = prefs.stringSetValue(Keys.HIDDEN_FOLDER_PATHS, emptySet())
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        )
    }

    suspend fun setAudioSort(sortMode: SortMode) { context.dataStore.edit { it[Keys.AUDIO_SORT] = sortMode.name } }
    suspend fun setVideoSort(sortMode: SortMode) { context.dataStore.edit { it[Keys.VIDEO_SORT] = sortMode.name } }
    suspend fun setLastDestination(destination: AppDestination) { context.dataStore.edit { it[Keys.LAST_DESTINATION] = destination.name } }
    suspend fun setThemeMode(mode: AppThemeMode) { context.dataStore.edit { it[Keys.THEME_MODE] = mode.name } }
    suspend fun setDynamicColor(enabled: Boolean) { context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled } }
    suspend fun setOnlineMusicSearchEnabled(enabled: Boolean) { context.dataStore.edit { it[Keys.ONLINE_MUSIC_SEARCH_ENABLED] = enabled } }
    suspend fun setLyricsTranslationEnabled(enabled: Boolean) { context.dataStore.edit { it[Keys.LYRICS_TRANSLATION_ENABLED] = enabled } }
    suspend fun setVolumeBoostEnabled(enabled: Boolean) { context.dataStore.edit { it[Keys.VOLUME_BOOST_ENABLED] = enabled } }
    suspend fun setVolumeBoostGainMb(gainMb: Int) { context.dataStore.edit { it[Keys.VOLUME_BOOST_GAIN_MB] = gainMb.coerceIn(0, 1800).toString() } }
    suspend fun setLibraryChangeNotificationsEnabled(enabled: Boolean) { context.dataStore.edit { it[Keys.LIBRARY_CHANGE_NOTIFICATIONS_ENABLED] = enabled } }
    suspend fun setShuffleEnabled(enabled: Boolean) { context.dataStore.edit { it[Keys.SHUFFLE_ENABLED] = enabled } }
    suspend fun setCrossfadeEnabled(enabled: Boolean) { context.dataStore.edit { it[Keys.CROSSFADE_ENABLED] = enabled } }
    suspend fun setCrossfadeDurationMs(durationMs: Int) { context.dataStore.edit { it[Keys.CROSSFADE_DURATION_MS] = durationMs.coerceIn(500, 10000) } }
    suspend fun setSleepTimerEnabled(enabled: Boolean) { context.dataStore.edit { it[Keys.SLEEP_TIMER_ENABLED] = enabled } }
    suspend fun setSleepTimerMinutes(minutes: Int) { context.dataStore.edit { it[Keys.SLEEP_TIMER_MINUTES] = minutes.coerceIn(5, 180) } }
    suspend fun setResumePlaybackEnabled(enabled: Boolean) { context.dataStore.edit { it[Keys.RESUME_PLAYBACK_ENABLED] = enabled } }


    suspend fun setLastPlaybackState(mediaId: Long, mediaKind: String, positionMs: Long) {
        context.dataStore.edit {
            it[Keys.LAST_PLAYBACK_MEDIA_ID] = mediaId.toString()
            it[Keys.LAST_PLAYBACK_MEDIA_KIND] = mediaKind
            it[Keys.LAST_PLAYBACK_POSITION_MS] = positionMs.coerceAtLeast(0L).toString()
        }
    }

    suspend fun setHiddenAudioIds(ids: Set<Long>) { context.dataStore.edit { it[Keys.HIDDEN_AUDIO_IDS] = ids.map(Long::toString).toSet() } }
    suspend fun addHiddenAudioId(id: Long) { context.dataStore.edit { prefs -> val current = prefs.stringSetValue(Keys.HIDDEN_AUDIO_IDS, emptySet()).toMutableSet(); current.add(id.toString()); prefs[Keys.HIDDEN_AUDIO_IDS] = current } }
    suspend fun removeHiddenAudioId(id: Long) { context.dataStore.edit { prefs -> val current = prefs.stringSetValue(Keys.HIDDEN_AUDIO_IDS, emptySet()).toMutableSet(); current.remove(id.toString()); prefs[Keys.HIDDEN_AUDIO_IDS] = current } }
    suspend fun clearHiddenAudioIds() { context.dataStore.edit { it[Keys.HIDDEN_AUDIO_IDS] = emptySet() } }

    suspend fun setHiddenFolderPaths(paths: Set<String>) { context.dataStore.edit { it[Keys.HIDDEN_FOLDER_PATHS] = paths.map { path -> path.trim() }.filter { it.isNotBlank() }.toSet() } }
    suspend fun addHiddenFolderPath(path: String) { context.dataStore.edit { prefs -> val current = prefs.stringSetValue(Keys.HIDDEN_FOLDER_PATHS, emptySet()).toMutableSet(); current.add(path.trim()); prefs[Keys.HIDDEN_FOLDER_PATHS] = current } }
    suspend fun removeHiddenFolderPath(path: String) { context.dataStore.edit { prefs -> val current = prefs.stringSetValue(Keys.HIDDEN_FOLDER_PATHS, emptySet()).toMutableSet(); current.remove(path.trim()); prefs[Keys.HIDDEN_FOLDER_PATHS] = current } }
    suspend fun clearHiddenFolderPaths() { context.dataStore.edit { it[Keys.HIDDEN_FOLDER_PATHS] = emptySet() } }
}

private fun Preferences.stringValue(key: Preferences.Key<String>, default: String): String = this[key] ?: default
private fun Preferences.stringSetValue(key: Preferences.Key<Set<String>>, default: Set<String>): Set<String> = this[key] ?: default
private fun String.toSortMode(): SortMode = runCatching { SortMode.valueOf(this) }.getOrDefault(SortMode.DATE_ADDED_DESC)
private fun String.toDestination(): AppDestination = runCatching { AppDestination.valueOf(this) }.getOrDefault(AppDestination.MUSIC)
private fun String.toThemeMode(): AppThemeMode = runCatching { AppThemeMode.valueOf(this) }.getOrDefault(AppThemeMode.SYSTEM)
