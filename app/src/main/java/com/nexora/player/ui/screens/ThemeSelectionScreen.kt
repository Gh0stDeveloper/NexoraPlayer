package com.nexora.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexora.player.data.model.AppThemeMode

private data class ThemeOption(
    val mode: AppThemeMode,
    val title: String,
    val subtitle: String,
    val colors: List<Color>
)

@Composable
fun ThemeSelectionScreen(
    modifier: Modifier = Modifier,
    selectedTheme: AppThemeMode,
    dynamicColor: Boolean,
    onBack: () -> Unit,
    onThemeSelected: (AppThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit
) {
    val options = listOf(
        ThemeOption(AppThemeMode.SYSTEM, "Sistema", "Usa el tema configurado en Android.", listOf(Color(0xFFF54047), Color(0xFF111827), Color(0xFFF7F6FB))),
        ThemeOption(AppThemeMode.NEXORA_DARK, "Nexora Dark", "Oscuro elegante con acento rojo Nexora.", listOf(Color(0xFF090B14), Color(0xFFF54047), Color(0xFFE64366))),
        ThemeOption(AppThemeMode.IOS_LIGHT, "iOS Light", "Claro, limpio y minimalista.", listOf(Color(0xFFFFFFFF), Color(0xFFF7F6FB), Color(0xFFF54047))),
        ThemeOption(AppThemeMode.AMOLED_BLACK, "AMOLED Black", "Negro puro para pantallas OLED.", listOf(Color.Black, Color(0xFFFF375F), Color(0xFF64D2FF))),
        ThemeOption(AppThemeMode.FLAMINGO, "Flamingo", "Cálido, moderno y visual.", listOf(Color(0xFF100713), Color(0xFFFF4D6D), Color(0xFFFF9F1C))),
        ThemeOption(AppThemeMode.NEON, "Neon", "Contraste fuerte con acentos brillantes.", listOf(Color(0xFF050712), Color(0xFF00F5D4), Color(0xFFB517FF))),
        ThemeOption(AppThemeMode.MATERIAL_YOU, "Material You", "Colores dinámicos del sistema.", listOf(Color(0xFF6750A4), Color(0xFF625B71), Color(0xFFEADDFF)))
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Temas visuales", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                Text("Elige el estilo principal de Nexora Player", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Material You automático", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (dynamicColor) "Los colores dinámicos están activados." else "Activa colores del sistema cuando estén disponibles.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = dynamicColor,
                            onCheckedChange = onDynamicColorChange
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            items(options, key = { it.mode.name }) { option ->
                ThemeOptionCard(
                    option = option,
                    selected = selectedTheme == option.mode ||
                        (selectedTheme == AppThemeMode.DARK && option.mode == AppThemeMode.NEXORA_DARK) ||
                        (selectedTheme == AppThemeMode.LIGHT && option.mode == AppThemeMode.IOS_LIGHT),
                    onClick = { onThemeSelected(option.mode) }
                )
            }
        }
    }
}

@Composable
private fun ThemeOptionCard(
    option: ThemeOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected) 5.dp else 2.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(option.colors)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.DarkMode, contentDescription = null, tint = Color.White.copy(alpha = 0.92f))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(option.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(option.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    option.colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Seleccionado", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
