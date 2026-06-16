package com.nexora.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.player.equalizer.EqualizerController
import com.nexora.player.equalizer.EqualizerPreferencesRepository
import com.nexora.player.equalizer.EqualizerSettings
import com.nexora.player.equalizer.NEXORA_CUSTOM_TEMPLATE_ID
import com.nexora.player.equalizer.NexoraEqualizerTemplates
import com.nexora.player.equalizer.resolveCurveForBands
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
    audioSessionId: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { EqualizerPreferencesRepository(context) }
    val savedSettings by repository.settings.collectAsStateWithLifecycle(initialValue = EqualizerSettings())
    val controller = remember(audioSessionId) { EqualizerController(audioSessionId) }
    val hardwareInfo = controller.hardwareInfo
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    DisposableEffect(audioSessionId) {
        onDispose { controller.close() }
    }

    var enabled by remember(audioSessionId) { mutableStateOf(savedSettings.enabled) }
    var selectedTemplateId by remember(audioSessionId) { mutableStateOf(savedSettings.templateId) }
    var customName by remember(audioSessionId) { mutableStateOf(savedSettings.customName) }
    var bandCurve by remember(audioSessionId) {
        mutableStateOf(
            resolveInitialCurve(
                savedSettings = savedSettings,
                bandCount = hardwareInfo.bandCount
            )
        )
    }

    LaunchedEffect(savedSettings, hardwareInfo.bandCount) {
        enabled = savedSettings.enabled
        selectedTemplateId = savedSettings.templateId
        customName = savedSettings.customName
        bandCurve = resolveInitialCurve(savedSettings, hardwareInfo.bandCount)
    }

    LaunchedEffect(enabled, bandCurve, hardwareInfo.bandCount) {
        controller.setEnabled(enabled)
        controller.applyCurve(bandCurve)
    }

    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(imageVector = Icons.Filled.Equalizer, contentDescription = null)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Ecualizador Nexora", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (hardwareInfo.supported) {
                                    "${hardwareInfo.bandCount} bandas activas"
                                } else {
                                    "Ecualizador no disponible en este dispositivo"
                                }
                            )
                        }
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { checked ->
                            enabled = checked
                            scope.launch { repository.setEnabled(checked) }
                        }
                    )
                }
            }

            item {
                Text(
                    text = "Plantillas",
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(NexoraEqualizerTemplates.all) { template ->
                        FilterChip(
                            selected = selectedTemplateId == template.id,
                            onClick = {
                                selectedTemplateId = template.id
                                bandCurve = resolveCurveForBands(template, hardwareInfo.bandCount)
                                scope.launch { repository.setTemplateId(template.id) }
                            },
                            label = { Text(template.name) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Filled.Tune, contentDescription = null)
                            }
                        )
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Personalización", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text("Nombre del perfil") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = {
                                    val template = NexoraEqualizerTemplates.resolve(selectedTemplateId)
                                    bandCurve = resolveCurveForBands(template, hardwareInfo.bandCount)
                                },
                                label = { Text("Restaurar plantilla") },
                                leadingIcon = { Icon(imageVector = Icons.Filled.Restore, contentDescription = null) }
                            )
                            AssistChip(
                                onClick = {
                                    scope.launch {
                                        repository.saveCustomPreset(customName, bandCurve)
                                        repository.setEnabled(enabled)
                                    }
                                    selectedTemplateId = NEXORA_CUSTOM_TEMPLATE_ID
                                },
                                label = { Text("Guardar") },
                                leadingIcon = { Icon(imageVector = Icons.Filled.Save, contentDescription = null) }
                            )
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
            }

            if (hardwareInfo.supported && hardwareInfo.bandCount > 0) {
                items(hardwareInfo.bandCount) { index ->
                    val centerHz = hardwareInfo.centerFrequenciesHz.getOrNull(index) ?: 0f
                    val sliderValue = bandCurve.getOrNull(index) ?: 0.5f

                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = formatBandLabel(centerHz),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = formatBandDb(sliderValue, hardwareInfo.minLevelDb, hardwareInfo.maxLevelDb),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Slider(
                                value = sliderValue,
                                onValueChange = { value ->
                                    val next = bandCurve.toMutableList()
                                    while (next.size < hardwareInfo.bandCount) next.add(0.5f)
                                    next[index] = value.coerceIn(0f, 1f)
                                    bandCurve = next
                                    selectedTemplateId = NEXORA_CUSTOM_TEMPLATE_ID
                                },
                                onValueChangeFinished = {
                                    scope.launch {
                                        repository.saveCustomPreset(customName, bandCurve)
                                        repository.setEnabled(enabled)
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "Este dispositivo no expone un ecualizador compatible.",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            enabled = false
                            selectedTemplateId = NexoraEqualizerTemplates.flat.id
                            bandCurve = resolveCurveForBands(NexoraEqualizerTemplates.flat, hardwareInfo.bandCount)
                            scope.launch {
                                repository.restoreDefault()
                            }
                        }
                    ) {
                        Text("Restablecer todo")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cerrar")
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

private fun resolveInitialCurve(
    savedSettings: EqualizerSettings,
    bandCount: Int
): List<Float> {
    return if (savedSettings.templateId == NEXORA_CUSTOM_TEMPLATE_ID && savedSettings.customCurve.isNotEmpty()) {
        resolveCurveForBands(savedSettings.customCurve, bandCount)
    } else {
        resolveCurveForBands(NexoraEqualizerTemplates.resolve(savedSettings.templateId), bandCount)
    }
}

private fun formatBandLabel(centerHz: Float): String {
    if (centerHz <= 0f) return "Banda"
    return when {
        centerHz >= 1000f -> "${(centerHz / 1000f).roundToInt()} kHz"
        else -> "${centerHz.roundToInt()} Hz"
    }
}

private fun formatBandDb(level: Float, minDb: Float, maxDb: Float): String {
    val db = minDb + level.coerceIn(0f, 1f) * (maxDb - minDb)
    return "${String.format(java.util.Locale.US, "%.1f", db)} dB"
}
