package com.nexora.player.ui.screens

import android.content.Context
import android.content.Intent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexora.player.R
import com.nexora.player.data.model.AppLanguage
import com.nexora.player.data.model.AppThemeMode
import com.nexora.player.data.model.NexoraRepeatMode
import com.nexora.player.data.share.NexoraShareText

// ── Data model for selectively restoring hidden audio ────────────────────────

data class HiddenAudioItem(
    val id: Long,
    val title: String,
    val artist: String = "",
    val album: String  = ""
)

// ── Main screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    themeMode: AppThemeMode,
    dynamicColor: Boolean,
    hiddenAudioCount: Int,
    hiddenAudioItems: List<HiddenAudioItem> = emptyList(),
    onlineMusicSearchEnabled: Boolean,
    lyricsTranslationEnabled: Boolean,
    volumeBoostEnabled: Boolean,
    libraryChangeNotificationsEnabled: Boolean,
    shuffleEnabled: Boolean = false,
    repeatMode: NexoraRepeatMode = NexoraRepeatMode.OFF,
    resumePlaybackEnabled: Boolean = true,
    crossfadeEnabled: Boolean = false,
    crossfadeDurationMs: Int = 1200,
    sleepTimerEnabled: Boolean = false,
    sleepTimerMinutes: Int = 30,
    sleepTimerStopAtEndOfTrack: Boolean = false,
    hiddenFolders: List<String> = emptyList(),
    shareUrl: String = "https://nexoraplayer.vercel.app",
    updateChecking: Boolean = false,
    updateError: String? = null,
    currentLanguage: AppLanguage,
    onBack: (() -> Unit)? = null,
    onThemeChange: (AppThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onOnlineMusicSearchChange: (Boolean) -> Unit,
    onLyricsTranslationChange: (Boolean) -> Unit,
    onVolumeBoostChange: (Boolean) -> Unit,
    onLibraryChangeNotificationsChange: (Boolean) -> Unit,
    onShuffleChange: (Boolean) -> Unit = {},
    onRepeatModeChange: (NexoraRepeatMode) -> Unit = {},
    onResumePlaybackChange: (Boolean) -> Unit = {},
    onCrossfadeChange: (Boolean) -> Unit = {},
    onCrossfadeDurationChange: (Int) -> Unit = {},
    onStartSleepTimer: (Int) -> Unit = {},
    onStartSleepTimerAtEndOfTrack: () -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onLanguageChange: (AppLanguage) -> Unit,
    onRestoreHiddenAudio: () -> Unit,
    onAddHiddenFolder: (String) -> Unit = {},
    onRemoveHiddenFolder: (String) -> Unit = {},
    onClearHiddenFolders: () -> Unit = {},
    onOpenFolderManager: () -> Unit = {},
    onOpenNotificationCenter: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onOpenThemeSelection: () -> Unit = {},
    onOpenLanguageSelection: () -> Unit = {},
    onCheckUpdates: () -> Unit = {},
    onClearUpdateMessage: () -> Unit = {},
    onRestoreHiddenItem: (Long) -> Unit = {}
) {
    val uriHandler      = LocalUriHandler.current
    val context         = LocalContext.current
    val showTerms       = remember { mutableStateOf(false) }
    val showPrivacy     = remember { mutableStateOf(false) }
    val showHiddenSheet = remember { mutableStateOf(false) }
    val showFolderSheet = remember { mutableStateOf(false) }
    val folderInput     = remember { mutableStateOf("") }
    val updateStatusDialog = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(updateError) {
        if (!updateError.isNullOrBlank()) {
            updateStatusDialog.value = updateError
        }
    }

    updateStatusDialog.value?.let { message ->
        AlertDialog(
            onDismissRequest = {
                updateStatusDialog.value = null
                onClearUpdateMessage()
            },
            title = { Text(stringResource(R.string.update_status_dialog_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        updateStatusDialog.value = null
                        onClearUpdateMessage()
                    }
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Page title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 18.dp, bottom = 8.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(4.dp))

        // ════════════════════════════════════════════════════════════════════
        // PERSONALIZACIÓN
        // ════════════════════════════════════════════════════════════════════
        SectionHeader(stringResource(R.string.section_personalization))
        SettingsGroup {
            SettingsLinkRow(
                icon      = Icons.Filled.Language,
                iconColor = Color(0xFF007AFF),
                title     = stringResource(R.string.settings_language),
                subtitle  = stringResource(R.string.settings_language_current, stringResource(currentLanguage.labelRes)),
                onClick   = onOpenLanguageSelection
            )

            RowDivider()

            SettingsLinkRow(
                icon      = Icons.Filled.Brightness4,
                iconColor = Color(0xFF5856D6),
                title     = stringResource(R.string.settings_theme),
                subtitle  = stringResource(R.string.settings_theme_current, stringResource(themeMode.labelRes)),
                onClick   = onOpenThemeSelection
            )

        }

        // ════════════════════════════════════════════════════════════════════
        // REPRODUCCIÓN Y CARPETAS
        // ════════════════════════════════════════════════════════════════════
        SectionHeader(stringResource(R.string.section_playback_folders))
        SettingsGroup {
            SettingsToggleRow(
                icon            = Icons.Filled.NotificationsActive,
                iconColor       = Color(0xFF34C759),
                title           = stringResource(R.string.settings_shuffle_default),
                subtitle        = stringResource(R.string.settings_shuffle_default_desc),
                checked         = shuffleEnabled,
                onCheckedChange = onShuffleChange
            )
            RowDivider()
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_repeat), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = repeatMode == NexoraRepeatMode.OFF,
                        onClick = { onRepeatModeChange(NexoraRepeatMode.OFF) },
                        shape = SegmentedButtonDefaults.itemShape(0, 3)
                    ) { Text(stringResource(R.string.repeat_off), fontSize = 13.sp) }
                    SegmentedButton(
                        selected = repeatMode == NexoraRepeatMode.ONE,
                        onClick = { onRepeatModeChange(NexoraRepeatMode.ONE) },
                        shape = SegmentedButtonDefaults.itemShape(1, 3)
                    ) { Text(stringResource(R.string.repeat_one), fontSize = 13.sp) }
                    SegmentedButton(
                        selected = repeatMode == NexoraRepeatMode.ALL,
                        onClick = { onRepeatModeChange(NexoraRepeatMode.ALL) },
                        shape = SegmentedButtonDefaults.itemShape(2, 3)
                    ) { Text(stringResource(R.string.repeat_all), fontSize = 13.sp) }
                }
            }
            RowDivider()
            SettingsToggleRow(
                icon            = Icons.Filled.MusicNote,
                iconColor       = Color(0xFF0A84FF),
                title           = stringResource(R.string.settings_resume_playback),
                subtitle        = stringResource(R.string.settings_resume_playback_desc),
                checked         = resumePlaybackEnabled,
                onCheckedChange = onResumePlaybackChange
            )
            RowDivider()
            SettingsToggleRow(
                icon            = Icons.Filled.Translate,
                iconColor       = Color(0xFFFF9500),
                title           = stringResource(R.string.settings_crossfade),
                subtitle        = stringResource(R.string.settings_crossfade_desc),
                checked         = crossfadeEnabled,
                onCheckedChange = onCrossfadeChange
            )
            if (crossfadeEnabled) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(stringResource(R.string.settings_duration_ms, crossfadeDurationMs), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(600, 900, 1200, 1800).forEach { value ->
                            FilledTonalButton(onClick = { onCrossfadeDurationChange(value) }) {
                                Text("${value / 1000.0} s")
                            }
                        }
                    }
                }
            }
            RowDivider()
            SettingsToggleRow(
                icon            = Icons.Filled.Lock,
                iconColor       = Color(0xFF5856D6),
                title           = stringResource(R.string.settings_sleep_timer),
                subtitle        = if (sleepTimerEnabled) {
                    if (sleepTimerStopAtEndOfTrack) {
                        stringResource(R.string.settings_sleep_active_end_track)
                    } else {
                        stringResource(R.string.settings_sleep_active_minutes, sleepTimerMinutes)
                    }
                } else {
                    stringResource(R.string.settings_sleep_desc)
                },
                checked         = sleepTimerEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) onStartSleepTimer(sleepTimerMinutes) else onCancelSleepTimer()
                }
            )
            if (sleepTimerEnabled) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15, 30, 60).forEach { value ->
                            OutlinedButton(onClick = { onStartSleepTimer(value) }) {
                                Text("${value} min")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onStartSleepTimerAtEndOfTrack) { Text(stringResource(R.string.settings_sleep_end_song)) }
                        TextButton(onClick = onCancelSleepTimer) { Text(stringResource(R.string.settings_disable)) }
                    }
                }
            }
            RowDivider()
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(stringResource(R.string.settings_hidden_folders), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                Text(
                    if (hiddenFolders.isEmpty()) stringResource(R.string.settings_no_hidden_folders) else hiddenFolders.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onOpenFolderManager) { Text(stringResource(R.string.settings_manage)) }
                    if (hiddenFolders.isNotEmpty()) {
                        OutlinedButton(onClick = onClearHiddenFolders) { Text(stringResource(R.string.settings_clean)) }
                    }
                }
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // LETRAS Y AUDIO
        // ════════════════════════════════════════════════════════════════════
        SectionHeader(stringResource(R.string.section_lyrics_audio))
        SettingsGroup {
            SettingsToggleRow(
                icon            = Icons.Filled.Translate,
                iconColor       = Color(0xFF34C759),
                title           = stringResource(R.string.settings_translate_lyrics_auto),
                subtitle        = stringResource(R.string.settings_translate_lyrics_auto_desc),
                checked         = lyricsTranslationEnabled,
                onCheckedChange = onLyricsTranslationChange
            )

            RowDivider()

            SettingsToggleRow(
                icon            = Icons.AutoMirrored.Filled.VolumeUp,
                iconColor       = Color(0xFF5856D6),
                title           = stringResource(R.string.settings_volume_boost),
                subtitle        = stringResource(R.string.settings_volume_boost_desc),
                checked         = volumeBoostEnabled,
                onCheckedChange = onVolumeBoostChange
            )

            RowDivider()

            SettingsToggleRow(
                icon            = Icons.Filled.NotificationsActive,
                iconColor       = Color(0xFF007AFF),
                title           = stringResource(R.string.settings_library_notifications),
                subtitle        = stringResource(R.string.settings_library_notifications_desc),
                checked         = libraryChangeNotificationsEnabled,
                onCheckedChange = onLibraryChangeNotificationsChange
            )
        }

        // ════════════════════════════════════════════════════════════════════
        // BIBLIOTECA
        // ════════════════════════════════════════════════════════════════════
        SectionHeader(stringResource(R.string.section_library))
        SettingsGroup {
            SettingsToggleRow(
                icon            = Icons.Filled.Search,
                iconColor       = Color(0xFF30B0C7),
                title           = stringResource(R.string.settings_online_search),
                subtitle        = if (onlineMusicSearchEnabled)
                                      stringResource(R.string.settings_online_enabled)
                                  else
                                      stringResource(R.string.settings_online_disabled),
                checked         = onlineMusicSearchEnabled,
                onCheckedChange = onOnlineMusicSearchChange
            )

            RowDivider()

            // Hidden audio row — tappable to show selective restore sheet
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = hiddenAudioCount > 0) {
                        showHiddenSheet.value = true
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsIcon(Icons.Filled.VisibilityOff, Color(0xFFFF3B30))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_library_privacy),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        if (hiddenAudioCount == 0)
                            stringResource(R.string.settings_no_hidden_songs)
                        else
                            stringResource(R.string.settings_hidden_songs_count, hiddenAudioCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hiddenAudioCount > 0)
                                    Color(0xFFFF3B30).copy(alpha = 0.80f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (hiddenAudioCount > 0) {
                    Icon(
                        Icons.Filled.ChevronRight, null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // SOBRE LA APP
        // ════════════════════════════════════════════════════════════════════
        SectionHeader(stringResource(R.string.section_about_app))
        SettingsGroup {
            // App identity block — compact, no empty space
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement   = Arrangement.spacedBy(0.dp),
                horizontalAlignment   = Alignment.CenterHorizontally
            ) {
                // App icon area
                Box(
                    modifier         = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF7C3AED)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.MusicNote, null,
                        tint     = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "NexoraPlayer",
                    style     = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.settings_free_notice),
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            RowDivider()

            // Developer
            SettingsInfoRow(
                icon      = Icons.Filled.Person,
                iconColor = Color(0xFF34C759),
                title     = stringResource(R.string.settings_developer),
                value     = "Ghost Developer"
            )

            RowDivider()

            // GitHub
            SettingsLinkRow(
                icon      = Icons.Filled.Code,
                iconColor = Color(0xFF1C1C1E),
                title     = stringResource(R.string.settings_github),
                subtitle  = "github.com/Gh0stDeveloper",
                onClick   = { uriHandler.openUri("https://github.com/Gh0stDeveloper") }
            )

            RowDivider()

            // Telegram
            SettingsLinkRow(
                icon      = Icons.AutoMirrored.Filled.OpenInNew,
                iconColor = Color(0xFF007AFF),
                title     = stringResource(R.string.settings_profile),
                subtitle  = "t.me/Gh0stDeveloper",
                onClick   = { uriHandler.openUri("https://t.me/Gh0stDeveloper") }
            )

            RowDivider()

            SettingsLinkRow(
                icon      = Icons.Filled.Share,
                iconColor = Color(0xFFFF9500),
                title     = stringResource(R.string.settings_share_title),
                subtitle  = stringResource(R.string.settings_share_desc),
                onClick   = { shareNexoraPlayer(context, shareUrl) }
            )

            RowDivider()

            SettingsLinkRow(
                icon      = Icons.Filled.SystemUpdate,
                iconColor = Color(0xFF0A84FF),
                title     = if (updateChecking) stringResource(R.string.settings_update_checking) else stringResource(R.string.settings_update_check),
                subtitle  = updateError ?: stringResource(R.string.settings_update_desc),
                onClick   = onCheckUpdates
            )

            RowDivider()

            SettingsLinkRow(
                icon      = Icons.Filled.NotificationsActive,
                iconColor = Color(0xFF34C759),
                title     = stringResource(R.string.settings_news_title),
                subtitle  = stringResource(R.string.settings_news_desc),
                onClick   = onOpenNotificationCenter
            )

            RowDivider()

            SettingsLinkRow(
                icon      = Icons.Filled.Insights,
                iconColor = Color(0xFFFF2D55),
                title     = stringResource(R.string.settings_stats_title),
                subtitle  = stringResource(R.string.settings_stats_desc),
                onClick   = onOpenStats
            )

            RowDivider()

            SettingsLinkRow(
                icon      = Icons.Filled.Widgets,
                iconColor = Color(0xFFAF52DE),
                title     = stringResource(R.string.settings_widget_title),
                subtitle  = stringResource(R.string.settings_widget_desc),
                onClick   = { }
            )

            RowDivider()

            // Copyright footer inside the card
            Text(
                text      = stringResource(R.string.settings_copyright),
                style     = MaterialTheme.typography.labelSmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        // ════════════════════════════════════════════════════════════════════
        // LEGAL
        // ════════════════════════════════════════════════════════════════════
        SectionHeader(stringResource(R.string.section_legal))
        SettingsGroup {
            SettingsLinkRow(
                icon      = Icons.Filled.Gavel,
                iconColor = Color(0xFF5856D6),
                title     = stringResource(R.string.legal_terms_title),
                subtitle  = stringResource(R.string.legal_terms_desc),
                onClick   = { showTerms.value = true }
            )
            RowDivider()
            SettingsLinkRow(
                icon      = Icons.Filled.Lock,
                iconColor = Color(0xFF34C759),
                title     = stringResource(R.string.legal_privacy_title),
                subtitle  = stringResource(R.string.legal_privacy_desc),
                onClick   = { showPrivacy.value = true }
            )
        }

        // ════════════════════════════════════════════════════════════════════
        // ESTADO
        // ════════════════════════════════════════════════════════════════════
        SectionHeader(stringResource(R.string.section_status))
        SettingsGroup {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(R.string.settings_status_title),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.settings_status_line1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.settings_status_line2),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.settings_status_line3),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    // ── Hidden audio bottom sheet ────────────────────────────────────────────
    if (showHiddenSheet.value) {
        HiddenAudioSheet(
            items           = hiddenAudioItems,
            totalCount      = hiddenAudioCount,
            onRestoreItem   = { id ->
                onRestoreHiddenItem(id)
            },
            onRestoreAll    = {
                onRestoreHiddenAudio()
                showHiddenSheet.value = false
            },
            onDismiss       = { showHiddenSheet.value = false }
        )
    }

    if (showFolderSheet.value) {
        Dialog(onDismissRequest = { showFolderSheet.value = false }) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.hidden_folders_manage_title), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = folderInput.value,
                        onValueChange = { folderInput.value = it },
                        label = { Text(stringResource(R.string.hidden_folder_path)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (folderInput.value.isNotBlank()) {
                                onAddHiddenFolder(folderInput.value)
                                folderInput.value = ""
                            }
                        }) { Text(stringResource(R.string.hidden_folder_hide)) }
                        OutlinedButton(onClick = { showFolderSheet.value = false }) { Text(stringResource(R.string.close)) }
                    }
                    if (hiddenFolders.isNotEmpty()) {
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(hiddenFolders, key = { it }) { folder ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(folder, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    TextButton(onClick = { onRemoveHiddenFolder(folder) }) { Text(stringResource(R.string.hidden_folder_remove)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Terms & Conditions full-screen dialog ────────────────────────────────
    if (showTerms.value) {
        LegalViewerDialog(
            title     = stringResource(R.string.legal_terms_title),
            content   = stringResource(R.string.legal_terms_text),
            onDismiss = { showTerms.value = false }
        )
    }

    // ── Privacy Policy full-screen dialog ───────────────────────────────────
    if (showPrivacy.value) {
        LegalViewerDialog(
            title     = stringResource(R.string.legal_privacy_title),
            content   = stringResource(R.string.legal_privacy_text),
            onDismiss = { showPrivacy.value = false }
        )
    }
}

// ── Hidden audio bottom sheet ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HiddenAudioSheet(
    items: List<HiddenAudioItem>,
    totalCount: Int,
    onRestoreItem: (Long) -> Unit,
    onRestoreAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.80f)
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.hidden_audio_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        stringResource(R.string.settings_hidden_songs_count, totalCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (totalCount > 1) {
                    TextButton(onClick = onRestoreAll) {
                        Text(stringResource(R.string.hidden_audio_restore_all),
                            color = Color(0xFFFF3B30),
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            if (items.isEmpty()) {
                // No detailed list available — show count + restore all
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.VisibilityOff, null,
                            modifier = Modifier.size(48.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.settings_hidden_songs_count, totalCount),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onRestoreAll,
                            shape   = RoundedCornerShape(12.dp),
                            colors  = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF3B30)
                            )
                        ) {
                            Icon(Icons.Filled.RestoreFromTrash, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.hidden_audio_restore_all))
                        }
                    }
                }
            } else {
                // Detailed list — each song individually restorable
                Text(
                    stringResource(R.string.hidden_audio_restore_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(
                    modifier            = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        HiddenSongRow(
                            item      = item,
                            onRestore = { onRestoreItem(item.id) }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun HiddenSongRow(
    item: HiddenAudioItem,
    onRestore: () -> Unit
) {
    Surface(
        shape         = RoundedCornerShape(14.dp),
        color         = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        modifier      = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier         = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.MusicNote, null,
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title.ifBlank { stringResource(R.string.hidden_audio_unknown_song) },
                    style    = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.artist.isNotBlank() || item.album.isNotBlank()) {
                    Text(
                        listOfNotNull(
                            item.artist.takeIf { it.isNotBlank() },
                            item.album.takeIf  { it.isNotBlank() }
                        ).joinToString(" · "),
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            FilledTonalButton(
                onClick = onRestore,
                shape   = RoundedCornerShape(10.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Filled.RestoreFromTrash, null,
                    modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.hidden_audio_show), fontSize = 12.sp)
            }
        }
    }
}

// ── Full-screen legal viewer ─────────────────────────────────────────────────

@Composable
private fun LegalViewerDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color    = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Top bar
                Surface(
                    shadowElevation = 4.dp,
                    color           = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                        Text(
                            title,
                            style    = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Scrollable content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Text(
                        text      = content,
                        style     = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
                        color     = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

// ── Reusable components ──────────────────────────────────────────────────────

@Composable
private fun SectionHeader(label: String) {
    Text(
        text     = label,
        style    = MaterialTheme.typography.labelSmall.copy(
            fontWeight    = FontWeight.Medium,
            letterSpacing = 0.8.sp
        ),
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 32.dp, top = 22.dp, bottom = 6.dp, end = 20.dp)
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        modifier        = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape           = RoundedCornerShape(16.dp),
        color           = MaterialTheme.colorScheme.surface,
        tonalElevation  = 2.dp,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(start = 58.dp),
        thickness = 0.5.dp,
        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f)
    )
}

@Composable
private fun SettingsIcon(icon: ImageVector, color: Color) {
    Box(
        modifier         = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = Color.White,
            modifier           = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsIcon(icon, iconColor)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            if (subtitle != null) {
                Text(subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsInfoRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsIcon(icon, iconColor)
        Text(title,
            style    = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f))
        Text(value,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingsLinkRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsIcon(icon, iconColor)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            if (subtitle != null) {
                Text(subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            Icons.Filled.ChevronRight, null,
            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
            modifier = Modifier.size(20.dp)
        )
    }
}


private fun shareNexoraPlayer(context: Context, downloadUrl: String) {
    val safeUrl = downloadUrl.ifBlank { "https://nexoraplayer.vercel.app" }
    val message = NexoraShareText.build(context, safeUrl)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "NexoraPlayer")
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share_nexora)))
}
