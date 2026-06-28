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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
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

// ── Data model for selectively restoring hidden audio ────────────────────────

data class HiddenAudioItem(
    val id: Long,
    val title: String,
    val artist: String = "",
    val album: String  = ""
)

// ── Legal texts ──────────────────────────────────────────────────────────────

private val TERMS_TEXT = """
TÉRMINOS Y CONDICIONES DE NEXORAPLAYER
Última actualización: 2025

1. ACEPTACIÓN
Al descargar, instalar o usar NexoraPlayer aceptas estos términos en su totalidad. Si no estás de acuerdo con alguna parte, por favor desinstala la aplicación.

2. USO PERMITIDO
NexoraPlayer está diseñado exclusivamente para reproducir archivos multimedia almacenados localmente en tu dispositivo. Queda estrictamente prohibido:
• Reproducir, distribuir o almacenar contenido que infrinja derechos de autor.
• Usar la aplicación con fines ilegales o que contravengan las leyes de tu país.
• Intentar modificar, descompilar o revertir el código de la aplicación.

3. CONTENIDO DEL USUARIO
Todo el contenido multimedia que reproduces es de tu exclusiva responsabilidad. NexoraPlayer no almacena, distribuye, monetiza ni accede de forma remota a ninguno de tus archivos. Tu biblioteca permanece completamente en tu dispositivo.

4. FUNCIÓN DE LETRAS EN LÍNEA
La búsqueda de letras accede a servicios de terceros. Esta funcionalidad es completamente opcional. No garantizamos la exactitud, disponibilidad permanente ni la legalidad del contenido obtenido de dichos servicios externos. El uso de letras descargadas debe respetar los derechos de autor correspondientes.

5. ACTUALIZACIONES Y CAMBIOS
El desarrollador puede actualizar, modificar o descontinuar NexoraPlayer en cualquier momento sin previo aviso. Las actualizaciones pueden modificar funcionalidades existentes o agregar nuevas condiciones de uso.

6. LIMITACIÓN DE RESPONSABILIDAD
NexoraPlayer se proporciona "tal cual", sin garantías de ningún tipo, expresas o implícitas. El desarrollador no se hace responsable por:
• Pérdida o daño de archivos del dispositivo.
• Fallos de reproducción o compatibilidad con formatos específicos.
• El contenido proveniente de servicios de terceros.
• Cualquier daño directo, indirecto o consecuente derivado del uso de la app.

7. PROPIEDAD INTELECTUAL
El código fuente, diseño, interfaz y nombre de NexoraPlayer son propiedad de Ghost Developer. Queda prohibida su reproducción o distribución sin autorización escrita y expresa del desarrollador.

8. LEGISLACIÓN APLICABLE
Estos términos se rigen por las leyes aplicables en el país de residencia del usuario. Cualquier disputa será resuelta en los tribunales competentes correspondientes.

9. CONTACTO
Para cualquier consulta sobre estos términos:
Telegram: t.me/Gh0stDeveloper
GitHub: github.com/Gh0stDeveloper

© 2025 NexoraPlayer · Ghost Developer
Todos los derechos reservados.
""".trimIndent()

private val PRIVACY_TEXT = """
POLÍTICA DE PRIVACIDAD DE NEXORAPLAYER
Última actualización: 2025

En NexoraPlayer tu privacidad es una prioridad. Esta política explica de forma clara y transparente qué información se utiliza, cómo y por qué.

──────────────────────────────────────
¿QUÉ DATOS RECOPILAMOS?
──────────────────────────────────────
NexoraPlayer NO recopila:
• Nombre, correo electrónico, teléfono ni ningún dato personal identificable.
• Historial de reproducción ni listas de canciones.
• Datos de ubicación geográfica.
• Información de contactos ni otras apps instaladas.
• Identificadores publicitarios ni analíticas de comportamiento.

──────────────────────────────────────
ACCESO A ARCHIVOS DEL DISPOSITIVO
──────────────────────────────────────
La aplicación solicita permiso de lectura de almacenamiento únicamente para listar y reproducir los archivos de audio y video que tú eliges. Estos archivos:
• Nunca se transmiten a servidores externos.
• No se comparten con terceros.
• Permanecen completamente bajo tu control.

──────────────────────────────────────
LETRAS EN LÍNEA (OPCIONAL)
──────────────────────────────────────
Cuando activas manualmente la búsqueda de letras, se envían el título de la canción y el nombre del artista a servicios de terceros para obtener el resultado. Esto significa:
• No enviamos información a nuestros propios servidores.
• No vinculamos estas búsquedas a tu identidad.
• No almacenamos un historial de tus búsquedas de letras.
Esta función es completamente opcional y puedes desactivarla en cualquier momento desde los ajustes.

──────────────────────────────────────
ECUALIZADOR Y AUDIO
──────────────────────────────────────
El ecualizador y todos los procesados de audio operan de forma completamente local en tu dispositivo. Ningún fragmento de audio se transmite a servidores externos.

──────────────────────────────────────
PERMISOS REQUERIDOS
──────────────────────────────────────
• Leer almacenamiento externo: para acceder a tus archivos de música y video.
• Internet (opcional): únicamente para la búsqueda de letras cuando la activas.
• Notificaciones: para mostrar los controles de reproducción en la barra de notificaciones.
• Primer plano (Foreground Service): para mantener la reproducción activa al minimizar la app.

──────────────────────────────────────
CAMBIOS A ESTA POLÍTICA
──────────────────────────────────────
Cualquier cambio significativo a esta política será notificado a través de actualizaciones de la aplicación. Continuando el uso de NexoraPlayer tras una actualización, aceptas la política vigente.

──────────────────────────────────────
CONTACTO
──────────────────────────────────────
Para consultas sobre privacidad o para ejercer tus derechos:
Telegram: t.me/Gh0stDeveloper
GitHub: github.com/Gh0stDeveloper

© 2025 NexoraPlayer · Ghost Developer
""".trimIndent()

// ── Main screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
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
    currentLanguage: AppLanguage,
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
    onRestoreHiddenItem: (Long) -> Unit = {}
) {
    val uriHandler      = LocalUriHandler.current
    val context         = LocalContext.current
    val showTerms       = remember { mutableStateOf(false) }
    val showPrivacy     = remember { mutableStateOf(false) }
    val showHiddenSheet = remember { mutableStateOf(false) }
    val showFolderSheet = remember { mutableStateOf(false) }
    val folderInput     = remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Page title
        Text(
            text     = stringResource(R.string.settings_title),
            style    = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp, end = 20.dp)
        )
        Spacer(Modifier.height(4.dp))

        // ════════════════════════════════════════════════════════════════════
        // PERSONALIZACIÓN
        // ════════════════════════════════════════════════════════════════════
        SectionHeader("PERSONALIZACIÓN")
        SettingsGroup {
            // Language
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsIcon(Icons.Filled.Language, Color(0xFF007AFF))
                    Text(
                        stringResource(R.string.settings_language),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = currentLanguage == AppLanguage.SYSTEM,
                        onClick  = { onLanguageChange(AppLanguage.SYSTEM) },
                        shape    = SegmentedButtonDefaults.itemShape(0, 3)
                    ) { Text(stringResource(AppLanguage.SYSTEM.labelRes), fontSize = 13.sp) }
                    SegmentedButton(
                        selected = currentLanguage == AppLanguage.SPANISH,
                        onClick  = { onLanguageChange(AppLanguage.SPANISH) },
                        shape    = SegmentedButtonDefaults.itemShape(1, 3)
                    ) { Text(stringResource(AppLanguage.SPANISH.labelRes), fontSize = 13.sp) }
                    SegmentedButton(
                        selected = currentLanguage == AppLanguage.ENGLISH,
                        onClick  = { onLanguageChange(AppLanguage.ENGLISH) },
                        shape    = SegmentedButtonDefaults.itemShape(2, 3)
                    ) { Text(stringResource(AppLanguage.ENGLISH.labelRes), fontSize = 13.sp) }
                }
            }

            RowDivider()

            // Theme
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsIcon(Icons.Filled.Brightness4, Color(0xFF5856D6))
                    Text(
                        stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = themeMode == AppThemeMode.SYSTEM,
                        onClick  = { onThemeChange(AppThemeMode.SYSTEM) },
                        shape    = SegmentedButtonDefaults.itemShape(0, 3)
                    ) { Text(stringResource(R.string.settings_system), fontSize = 13.sp) }
                    SegmentedButton(
                        selected = themeMode == AppThemeMode.LIGHT,
                        onClick  = { onThemeChange(AppThemeMode.LIGHT) },
                        shape    = SegmentedButtonDefaults.itemShape(1, 3)
                    ) { Text(stringResource(R.string.settings_light), fontSize = 13.sp) }
                    SegmentedButton(
                        selected = themeMode == AppThemeMode.DARK,
                        onClick  = { onThemeChange(AppThemeMode.DARK) },
                        shape    = SegmentedButtonDefaults.itemShape(2, 3)
                    ) { Text(stringResource(R.string.settings_dark), fontSize = 13.sp) }
                }
            }

            RowDivider()

            SettingsToggleRow(
                icon            = Icons.Filled.Palette,
                iconColor       = Color(0xFFFF9500),
                title           = stringResource(R.string.settings_dynamic_color),
                subtitle        = stringResource(R.string.settings_dynamic_color_desc),
                checked         = dynamicColor,
                onCheckedChange = onDynamicColorChange
            )
        }

        // ════════════════════════════════════════════════════════════════════
        // REPRODUCCIÓN Y CARPETAS
        // ════════════════════════════════════════════════════════════════════
        SectionHeader("REPRODUCCIÓN Y CARPETAS")
        SettingsGroup {
            SettingsToggleRow(
                icon            = Icons.Filled.NotificationsActive,
                iconColor       = Color(0xFF34C759),
                title           = "Shuffle por defecto",
                subtitle        = "Activa la reproducción aleatoria al iniciar listas y álbumes.",
                checked         = shuffleEnabled,
                onCheckedChange = onShuffleChange
            )
            RowDivider()
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Repetición", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = repeatMode == NexoraRepeatMode.OFF,
                        onClick = { onRepeatModeChange(NexoraRepeatMode.OFF) },
                        shape = SegmentedButtonDefaults.itemShape(0, 3)
                    ) { Text("Off", fontSize = 13.sp) }
                    SegmentedButton(
                        selected = repeatMode == NexoraRepeatMode.ONE,
                        onClick = { onRepeatModeChange(NexoraRepeatMode.ONE) },
                        shape = SegmentedButtonDefaults.itemShape(1, 3)
                    ) { Text("Una", fontSize = 13.sp) }
                    SegmentedButton(
                        selected = repeatMode == NexoraRepeatMode.ALL,
                        onClick = { onRepeatModeChange(NexoraRepeatMode.ALL) },
                        shape = SegmentedButtonDefaults.itemShape(2, 3)
                    ) { Text("Todo", fontSize = 13.sp) }
                }
            }
            RowDivider()
            SettingsToggleRow(
                icon            = Icons.Filled.MusicNote,
                iconColor       = Color(0xFF0A84FF),
                title           = "Reanudar reproducción",
                subtitle        = "Recupera la lista, la canción y la posición al volver a abrir la app.",
                checked         = resumePlaybackEnabled,
                onCheckedChange = onResumePlaybackChange
            )
            RowDivider()
            SettingsToggleRow(
                icon            = Icons.Filled.Translate,
                iconColor       = Color(0xFFFF9500),
                title           = "Crossfade",
                subtitle        = "Aplica una transición suave entre canciones.",
                checked         = crossfadeEnabled,
                onCheckedChange = onCrossfadeChange
            )
            if (crossfadeEnabled) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text("Duración: ${crossfadeDurationMs} ms", style = MaterialTheme.typography.bodyMedium)
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
                title           = "Sleep timer",
                subtitle        = if (sleepTimerEnabled) { if (sleepTimerStopAtEndOfTrack) "Activo: al terminar la canción" else "Activo: ${sleepTimerMinutes} min" } else "Apaga la reproducción tras un tiempo definido.",
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
                        FilledTonalButton(onClick = onStartSleepTimerAtEndOfTrack) { Text("Al terminar canción") }
                        TextButton(onClick = onCancelSleepTimer) { Text("Desactivar") }
                    }
                }
            }
            RowDivider()
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("Carpetas ocultas", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                Text(
                    if (hiddenFolders.isEmpty()) "No hay carpetas ocultas" else hiddenFolders.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onOpenFolderManager) { Text("Gestionar") }
                    if (hiddenFolders.isNotEmpty()) {
                        OutlinedButton(onClick = onClearHiddenFolders) { Text("Limpiar") }
                    }
                }
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // LETRAS Y AUDIO
        // ════════════════════════════════════════════════════════════════════
        SectionHeader("LETRAS Y AUDIO")
        SettingsGroup {
            SettingsToggleRow(
                icon            = Icons.Filled.Translate,
                iconColor       = Color(0xFF34C759),
                title           = "Traducir letras automáticamente",
                subtitle        = "Si la letra coincide con el idioma de la app o del sistema, no se traduce.",
                checked         = lyricsTranslationEnabled,
                onCheckedChange = onLyricsTranslationChange
            )

            RowDivider()

            SettingsToggleRow(
                icon            = Icons.Filled.VolumeUp,
                iconColor       = Color(0xFF5856D6),
                title           = "Volumen ampliado",
                subtitle        = "Sube el nivel por encima del sistema. Puede distorsionar el audio o dañar altavoces y oído.",
                checked         = volumeBoostEnabled,
                onCheckedChange = onVolumeBoostChange
            )

            RowDivider()

            SettingsToggleRow(
                icon            = Icons.Filled.NotificationsActive,
                iconColor       = Color(0xFF007AFF),
                title           = "Avisar cuando haya nuevos archivos",
                subtitle        = "Muestra una notificación cuando cambie la música o los videos disponibles.",
                checked         = libraryChangeNotificationsEnabled,
                onCheckedChange = onLibraryChangeNotificationsChange
            )
        }

        // ════════════════════════════════════════════════════════════════════
        // BIBLIOTECA
        // ════════════════════════════════════════════════════════════════════
        SectionHeader("BIBLIOTECA")
        SettingsGroup {
            SettingsToggleRow(
                icon            = Icons.Filled.Search,
                iconColor       = Color(0xFF30B0C7),
                title           = "Búsqueda en línea",
                subtitle        = if (onlineMusicSearchEnabled)
                                      "Online activado. Se usarán datos de internet."
                                  else
                                      "Desactivado. Solo datos locales.",
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
                            "No hay canciones ocultas"
                        else
                            "$hiddenAudioCount canción${if (hiddenAudioCount != 1) "es" else ""} oculta${if (hiddenAudioCount != 1) "s" else ""}",
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
        SectionHeader("SOBRE LA APP")
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
                title     = "Desarrollador",
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
                title     = "Compártenos",
                subtitle  = "Comparte el enlace de descarga para que más usuarios conozcan NexoraPlayer.",
                onClick   = { shareNexoraPlayer(context) }
            )

            RowDivider()

            // Copyright footer inside the card
            Text(
                text      = "© 2025 NexoraPlayer · Todos los derechos reservados",
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
        SectionHeader("LEGAL")
        SettingsGroup {
            SettingsLinkRow(
                icon      = Icons.Filled.Gavel,
                iconColor = Color(0xFF5856D6),
                title     = "Términos y condiciones",
                subtitle  = "Ver los términos de uso de la app",
                onClick   = { showTerms.value = true }
            )
            RowDivider()
            SettingsLinkRow(
                icon      = Icons.Filled.Lock,
                iconColor = Color(0xFF34C759),
                title     = "Política de privacidad",
                subtitle  = "Cómo manejamos tu información",
                onClick   = { showPrivacy.value = true }
            )
        }

        // ════════════════════════════════════════════════════════════════════
        // ESTADO
        // ════════════════════════════════════════════════════════════════════
        SectionHeader("ESTADO")
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
                    Text("Gestionar carpetas ocultas", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = folderInput.value,
                        onValueChange = { folderInput.value = it },
                        label = { Text("Ruta de carpeta") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (folderInput.value.isNotBlank()) {
                                onAddHiddenFolder(folderInput.value)
                                folderInput.value = ""
                            }
                        }) { Text("Ocultar") }
                        OutlinedButton(onClick = { showFolderSheet.value = false }) { Text("Cerrar") }
                    }
                    if (hiddenFolders.isNotEmpty()) {
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(hiddenFolders, key = { it }) { folder ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(folder, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    TextButton(onClick = { onRemoveHiddenFolder(folder) }) { Text("Quitar") }
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
            title     = "Términos y condiciones",
            content   = TERMS_TEXT,
            onDismiss = { showTerms.value = false }
        )
    }

    // ── Privacy Policy full-screen dialog ───────────────────────────────────
    if (showPrivacy.value) {
        LegalViewerDialog(
            title     = "Política de privacidad",
            content   = PRIVACY_TEXT,
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
                        "Canciones ocultas",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "$totalCount canción${if (totalCount != 1) "es" else ""} oculta${if (totalCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (totalCount > 1) {
                    TextButton(onClick = onRestoreAll) {
                        Text("Restaurar todas",
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
                            "$totalCount canción${if (totalCount != 1) "es" else ""} ocult${if (totalCount != 1) "as" else "a"}",
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
                            Text("Restaurar todas")
                        }
                    }
                }
            } else {
                // Detailed list — each song individually restorable
                Text(
                    "Toca una canción para volver a mostrarla en tu biblioteca.",
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
                    item.title.ifBlank { "Canción desconocida" },
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
                Text("Mostrar", fontSize = 12.sp)
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
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


private fun shareNexoraPlayer(context: Context) {
    val downloadUrl = "https://github.com/CHICO-CP/NexoraPlayer/releases/latest"
    val message = "Compártenos para que más usuarios nos conozcan. Descarga NexoraPlayer aquí: $downloadUrl"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "NexoraPlayer")
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir NexoraPlayer"))
}
