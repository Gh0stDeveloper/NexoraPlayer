package com.nexora.player.equalizer

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.equalizerDataStore by preferencesDataStore(name = "nexora_equalizer")

data class EqualizerSettings(
    val enabled: Boolean = false,
    val templateId: String = NexoraEqualizerTemplates.flat.id,
    val customName: String = "Mi personalización",
    val customCurve: List<Float> = emptyList()
)

class EqualizerPreferencesRepository(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val TEMPLATE_ID = stringPreferencesKey("template_id")
        val CUSTOM_NAME = stringPreferencesKey("custom_name")
        val CUSTOM_CURVE = stringPreferencesKey("custom_curve")
    }

    val settings: Flow<EqualizerSettings> = context.equalizerDataStore.data.map { prefs ->
        EqualizerSettings(
            enabled = prefs[Keys.ENABLED] ?: false,
            templateId = prefs[Keys.TEMPLATE_ID] ?: NexoraEqualizerTemplates.flat.id,
            customName = prefs[Keys.CUSTOM_NAME] ?: "Mi personalización",
            customCurve = (prefs[Keys.CUSTOM_CURVE] ?: "")
                .decodeCurve()
                .ifEmpty { emptyList() }
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.equalizerDataStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun setTemplateId(templateId: String) {
        context.equalizerDataStore.edit { it[Keys.TEMPLATE_ID] = templateId }
    }

    suspend fun saveCustomPreset(name: String, curve: List<Float>) {
        context.equalizerDataStore.edit { prefs ->
            prefs[Keys.TEMPLATE_ID] = NEXORA_CUSTOM_TEMPLATE_ID
            prefs[Keys.CUSTOM_NAME] = name.trim().ifBlank { "Mi personalización" }
            prefs[Keys.CUSTOM_CURVE] = curve.encodeCurve()
        }
    }

    suspend fun restoreDefault() {
        context.equalizerDataStore.edit { prefs ->
            prefs[Keys.TEMPLATE_ID] = NexoraEqualizerTemplates.flat.id
            prefs[Keys.CUSTOM_NAME] = "Mi personalización"
            prefs[Keys.CUSTOM_CURVE] = ""
            prefs[Keys.ENABLED] = false
        }
    }
}

private fun List<Float>.encodeCurve(): String = joinToString(",") { value ->
    String.format(Locale.US, "%.4f", value.coerceIn(0f, 1f))
}

private fun String.decodeCurve(): List<Float> =
    split(',')
        .mapNotNull { it.trim().takeIf(String::isNotEmpty)?.toFloatOrNull() }
        .map { it.coerceIn(0f, 1f) }

private fun Preferences.stringValue(key: Preferences.Key<String>, default: String): String =
    this[key] ?: default
