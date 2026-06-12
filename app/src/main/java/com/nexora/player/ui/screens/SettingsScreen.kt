package com.nexora.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.nexora.player.data.model.AppThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    themeMode: AppThemeMode,
    dynamicColor: Boolean,
    hiddenAudioCount: Int,
    onThemeChange: (AppThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onRestoreHiddenAudio: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val showAbout = remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)

        ElevatedCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Tema", style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = themeMode == AppThemeMode.SYSTEM,
                        onClick = { onThemeChange(AppThemeMode.SYSTEM) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                    ) { Text("Sistema") }
                    SegmentedButton(
                        selected = themeMode == AppThemeMode.LIGHT,
                        onClick = { onThemeChange(AppThemeMode.LIGHT) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) { Text("Claro") }
                    SegmentedButton(
                        selected = themeMode == AppThemeMode.DARK,
                        onClick = { onThemeChange(AppThemeMode.DARK) },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) { Text("Oscuro") }
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("Color dinámico")
                        Text(
                            "Ajusta el esquema al sistema del dispositivo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
                }
            }
        }

        ElevatedCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Biblioteca y privacidad", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Canciones ocultas: $hiddenAudioCount",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Las pistas ocultas no aparecen en la biblioteca principal ni en la búsqueda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onRestoreHiddenAudio,
                    enabled = hiddenAudioCount > 0
                ) {
                    Text("Restablecer canciones ocultas")
                }
            }
        }

        ElevatedCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Desarrollador", style = MaterialTheme.typography.titleMedium)
                Text("Ghost Developer · CHICO-CP")
                Text(
                    "Todos los derechos reservados. Nexora Player es gratuita y no está a la venta.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { uriHandler.openUri("https://github.com/CHICO-CP") },
                        label = { Text("GitHub") },
                        leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Code, contentDescription = null) }
                    )
                    AssistChip(
                        onClick = { uriHandler.openUri("https://github.com/CHICO-CP") },
                        label = { Text("Perfil") },
                        leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.OpenInNew, contentDescription = null) }
                    )
                }
                AssistChip(
                    onClick = { showAbout.value = !showAbout.value },
                    label = { Text(if (showAbout.value) "Ocultar descripción" else "Ver descripción detallada") },
                    leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Verified, contentDescription = null) }
                )
            }
        }

        if (showAbout.value) {
            ElevatedCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Qué hace la app", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Nexora Player organiza la biblioteca local de audio y video, reproduce contenido con Media3 / ExoPlayer, permite búsqueda rápida, listas de reproducción, favoritos, historial y ajustes persistentes."
                    )
                    Text(
                        "La interfaz prioriza navegación por secciones, vistas compactas, reproducción en pantalla completa para video y una pantalla de audio tipo ahora reproduciendo."
                    )
                    Text(
                        "También incluye portada o miniatura cuando el archivo la tiene, controles de brillo y volumen por gesto en video, y opciones para ocultar pistas que no quieras ver en la biblioteca principal."
                    )
                }
            }
        }

        ElevatedCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Estado técnico", style = MaterialTheme.typography.titleMedium)
                Text("Persistencia local: Room + DataStore")
                Text("Reproductor base: Media3 / ExoPlayer")
                Text("Listo para listas, favoritos, historial y UI más avanzada.")
            }
        }
    }
}
