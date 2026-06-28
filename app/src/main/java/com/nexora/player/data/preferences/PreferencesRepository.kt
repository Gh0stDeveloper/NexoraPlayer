package com.nexora.player.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nexora.player.data.model.AppDestination
import com.nexora.player.data.model.AppThemeMode
import com.nexora.player.data.model.SortMode
import com.nexora.player.data.model.NexoraRepeatMode
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
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val PLAYBACK_SPEED = stringPreferencesKey("playback_speed")
        val PLAYBACK_VOLUME = stringPreferencesKey("playback_volume")
        val RESUME_PLAYBACK_ENABLED = booleanPreferencesKey("resume_playback_enabled")
        val CROSSFADE_ENABLED = booleanPreferencesKey("crossfade_enabled")
        val CROSSFADE_DURATION_MS = stringPreferencesKey("crossfade_duration_ms")
        val SLEEP_TIMER_ENABLED = booleanPreferencesKey("sleep_timer_enabled")
        val SLEEP_TIMER_MINUTES = stringPreferencesKey("sleep_timer_minutes")
        val SLEEP_TIMER_END_AT_MS = longPreferencesKey("sleep_timer_end_at_ms")
        val SLEEP_TIMER_STOP_AT_END_OF_TRACK = booleanPreferencesKey("sleep_timer_stop_at_end_of_track")
        val PLAYBACK_SESSION_JSON = stringPreferencesKey("playback_session_json")
        val HIDDEN_AUDIO_IDS = stringSetPreferencesKey("hidden_audio_ids")
        val HIDDEN_FOLDERS = stringSetPreferencesKey("hidden_folders")
        val LAST_SEEN_VERSION_CODE = stringPreferencesKey("last_seen_version_code")
        val POSTPONED_UPDATE_VERSION_CODE = stringPreferencesKey("postponed_update_version_code")
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
            repeatMode = prefs.stringValue(Keys.REPEAT_MODE, NexoraRepeatMode.OFF.name).toRepeatMode(),
            playbackSpeed = prefs.stringValue(Keys.PLAYBACK_SPEED, "1.0").toFloatOrNull()?.coerceIn(0.5f, 2.0f) ?: 1.0f,
            playbackVolume = prefs.stringValue(Keys.PLAYBACK_VOLUME, "1.0").toFloatOrNull()?.coerceIn(0f, 1f) ?: 1.0f,
            resumePlaybackEnabled = prefs[Keys.RESUME_PLAYBACK_ENABLED] ?: true,
            crossfadeEnabled = prefs[Keys.CROSSFADE_ENABLED] ?: false,
            crossfadeDurationMs = prefs.stringValue(Keys.CROSSFADE_DURATION_MS, "1200").toIntOrNull()?.coerceIn(250, 5000) ?: 1200,
            sleepTimerEnabled = prefs[Keys.SLEEP_TIMER_ENABLED] ?: false,
            sleepTimerMinutes = prefs.stringValue(Keys.SLEEP_TIMER_MINUTES, "30").toIntOrNull()?.coerceIn(5, 240) ?: 30,
            sleepTimerEndAtMs = prefs[Keys.SLEEP_TIMER_END_AT_MS] ?: 0L,
            sleepTimerStopAtEndOfTrack = prefs[Keys.SLEEP_TIMER_STOP_AT_END_OF_TRACK] ?: false,
            playbackSessionJson = prefs.stringValue(Keys.PLAYBACK_SESSION_JSON, ""),
            hiddenAudioIds = prefs.stringSetValue(Keys.HIDDEN_AUDIO_IDS, emptySet())
                .mapNotNull { it.toLongOrNull() }
                .toSet(),
            hiddenFolders = prefs.stringSetValue(Keys.HIDDEN_FOLDERS, emptySet()),
            lastSeenVersionCode = prefs.stringValue(Keys.LAST_SEEN_VERSION_CODE, "0").toIntOrNull() ?: 0,
            postponedUpdateVersionCode = prefs.stringValue(Keys.POSTPONED_UPDATE_VERSION_CODE, "0").toIntOrNull() ?: 0
        )
    }

    suspend fun setAudioSort(sortMode: SortMode) {
        context.dataStore.edit { it[Keys.AUDIO_SORT] = sortMode.name }
    }

    suspend fun setVideoSort(sortMode: SortMode) {
        context.dataStore.edit { it[Keys.VIDEO_SORT] = sortMode.name }
    }

    suspend fun setLastDestination(destination: AppDestination) {
        context.dataStore.edit { it[Keys.LAST_DESTINATION] = destination.name }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setOnlineMusicSearchEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ONLINE_MUSIC_SEARCH_ENABLED] = enabled }
    }

    suspend fun setLyricsTranslationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LYRICS_TRANSLATION_ENABLED] = enabled }
    }

    suspend fun setVolumeBoostEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VOLUME_BOOST_ENABLED] = enabled }
    }

    suspend fun setVolumeBoostGainMb(gainMb: Int) {
        context.dataStore.edit { it[Keys.VOLUME_BOOST_GAIN_MB] = gainMb.coerceIn(0, 1800).toString() }
    }

    suspend fun setLibraryChangeNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LIBRARY_CHANGE_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHUFFLE_ENABLED] = enabled }
    }

    suspend fun setRepeatMode(mode: NexoraRepeatMode) {
        context.dataStore.edit { it[Keys.REPEAT_MODE] = mode.name }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.dataStore.edit { it[Keys.PLAYBACK_SPEED] = speed.coerceIn(0.5f, 2.0f).toString() }
    }

    suspend fun setPlaybackVolume(volume: Float) {
        context.dataStore.edit { it[Keys.PLAYBACK_VOLUME] = volume.coerceIn(0f, 1f).toString() }
    }

    suspend fun setResumePlaybackEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RESUME_PLAYBACK_ENABLED] = enabled }
    }

    suspend fun setCrossfadeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CROSSFADE_ENABLED] = enabled }
    }

    suspend fun setCrossfadeDurationMs(durationMs: Int) {
        context.dataStore.edit { it[Keys.CROSSFADE_DURATION_MS] = durationMs.coerceIn(250, 5000).toString() }
    }

    suspend fun setSleepTimerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SLEEP_TIMER_ENABLED] = enabled }
    }

    suspend fun setSleepTimerMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.SLEEP_TIMER_MINUTES] = minutes.coerceIn(5, 240).toString() }
    }

    suspend fun setSleepTimerEndAtMs(endAtMs: Long) {
        context.dataStore.edit { it[Keys.SLEEP_TIMER_END_AT_MS] = endAtMs }
    }

    suspend fun setSleepTimerStopAtEndOfTrack(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SLEEP_TIMER_STOP_AT_END_OF_TRACK] = enabled }
    }

    suspend fun setPlaybackSessionJson(sessionJson: String) {
        context.dataStore.edit { it[Keys.PLAYBACK_SESSION_JSON] = sessionJson }
    }

    suspend fun clearPlaybackSessionJson() {
        context.dataStore.edit { it.remove(Keys.PLAYBACK_SESSION_JSON) }
    }

    suspend fun setHiddenAudioIds(ids: Set<Long>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HIDDEN_AUDIO_IDS] = ids.map { it.toString() }.toSet()
        }
    }

    suspend fun addHiddenAudioId(id: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs.stringSetValue(Keys.HIDDEN_AUDIO_IDS, emptySet()).toMutableSet()
            current.add(id.toString())
            prefs[Keys.HIDDEN_AUDIO_IDS] = current
        }
    }

    suspend fun removeHiddenAudioId(id: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs.stringSetValue(Keys.HIDDEN_AUDIO_IDS, emptySet()).toMutableSet()
            current.remove(id.toString())
            prefs[Keys.HIDDEN_AUDIO_IDS] = current
        }
    }

    suspend fun clearHiddenAudioIds() {
        context.dataStore.edit { prefs ->
            prefs[Keys.HIDDEN_AUDIO_IDS] = emptySet()
        }
    }

    suspend fun setHiddenFolders(folders: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HIDDEN_FOLDERS] = folders.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        }
    }

    suspend fun addHiddenFolder(folder: String) {
        val normalized = folder.trim()
        if (normalized.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = prefs.stringSetValue(Keys.HIDDEN_FOLDERS, emptySet()).toMutableSet()
            current.add(normalized)
            prefs[Keys.HIDDEN_FOLDERS] = current
        }
    }

    suspend fun setLastSeenVersionCode(versionCode: Int) {
        context.dataStore.edit { it[Keys.LAST_SEEN_VERSION_CODE] = versionCode.coerceAtLeast(0).toString() }
    }

    suspend fun setPostponedUpdateVersionCode(versionCode: Int) {
        context.dataStore.edit { it[Keys.POSTPONED_UPDATE_VERSION_CODE] = versionCode.coerceAtLeast(0).toString() }
    }

    suspend fun removeHiddenFolder(folder: String) {
        val normalized = folder.trim()
        if (normalized.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = prefs.stringSetValue(Keys.HIDDEN_FOLDERS, emptySet()).toMutableSet()
            current.remove(normalized)
            prefs[Keys.HIDDEN_FOLDERS] = current
        }
    }
}

private fun Preferences.stringValue(key: Preferences.Key<String>, default: String): String =
    this[key] ?: default

private fun Preferences.stringSetValue(
    key: Preferences.Key<Set<String>>,
    default: Set<String>
): Set<String> = this[key] ?: default

private fun String.toSortMode(): SortMode = runCatching { SortMode.valueOf(this) }.getOrDefault(SortMode.DATE_ADDED_DESC)
private fun String.toDestination(): AppDestination = runCatching { AppDestination.valueOf(this) }.getOrDefault(AppDestination.MUSIC)
private fun String.toThemeMode(): AppThemeMode = runCatching { AppThemeMode.valueOf(this) }.getOrDefault(AppThemeMode.SYSTEM)
private fun String.toRepeatMode(): NexoraRepeatMode = runCatching { NexoraRepeatMode.valueOf(this) }.getOrDefault(NexoraRepeatMode.OFF)
