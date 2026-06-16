package com.nexora.player.equalizer

import kotlin.math.max
import kotlin.math.min

const val NEXORA_CUSTOM_TEMPLATE_ID = "custom"

/**
 * Plantillas base del ecualizador.
 *
 * Ajusta estos valores si quieres cambiar el carácter sonoro de la app sin tocar la UI.
 * Los puntos se guardan como curva normalizada (0f..1f) y luego se interpolan al número
 * de bandas reales del dispositivo.
 */
data class EqualizerTemplate(
    val id: String,
    val name: String,
    val description: String,
    val curve: List<Float>
)

object NexoraEqualizerTemplates {
    val flat = EqualizerTemplate(
        id = "flat",
        name = "Plano",
        description = "Respuesta neutra para referencia.",
        curve = listOf(0.50f, 0.50f, 0.50f, 0.50f, 0.50f)
    )

    val bassBoost = EqualizerTemplate(
        id = "bass_boost",
        name = "Bajos",
        description = "Refuerza el impacto de graves y subgraves.",
        curve = listOf(0.88f, 0.78f, 0.54f, 0.38f, 0.30f)
    )

    val warm = EqualizerTemplate(
        id = "warm",
        name = "Cálido",
        description = "Suaviza agudos y redondea medios.",
        curve = listOf(0.72f, 0.68f, 0.58f, 0.48f, 0.42f)
    )

    val vocal = EqualizerTemplate(
        id = "vocal",
        name = "Voces",
        description = "Destaca la zona media para voz y diálogo.",
        curve = listOf(0.42f, 0.58f, 0.72f, 0.62f, 0.46f)
    )

    val treble = EqualizerTemplate(
        id = "treble",
        name = "Agudos",
        description = "Aporta brillo y detalle a la mezcla.",
        curve = listOf(0.34f, 0.42f, 0.56f, 0.74f, 0.90f)
    )

    val party = EqualizerTemplate(
        id = "party",
        name = "Fiesta",
        description = "Más presencia en extremos y más energía.",
        curve = listOf(0.80f, 0.68f, 0.54f, 0.70f, 0.86f)
    )

    val podcast = EqualizerTemplate(
        id = "podcast",
        name = "Podcast",
        description = "Reduce ruido musical y prioriza claridad.",
        curve = listOf(0.30f, 0.48f, 0.78f, 0.66f, 0.40f)
    )

    val all: List<EqualizerTemplate> = listOf(
        flat,
        bassBoost,
        warm,
        vocal,
        treble,
        party,
        podcast
    )

    fun resolve(templateId: String): EqualizerTemplate =
        all.firstOrNull { it.id == templateId } ?: flat
}

fun interpolateCurve(points: List<Float>, bandCount: Int): List<Float> {
    if (bandCount <= 0) return emptyList()
    if (points.isEmpty()) return List(bandCount) { 0.5f }
    if (bandCount == 1) return listOf(points.first().coerceIn(0f, 1f))
    if (points.size == 1) return List(bandCount) { points.first().coerceIn(0f, 1f) }

    val lastIndex = points.lastIndex
    return List(bandCount) { bandIndex ->
        val position = bandIndex.toFloat() * lastIndex / max(1, bandCount - 1).toFloat()
        val leftIndex = position.toInt().coerceIn(0, lastIndex)
        val rightIndex = min(leftIndex + 1, lastIndex)
        val fraction = (position - leftIndex).coerceIn(0f, 1f)
        val left = points[leftIndex]
        val right = points[rightIndex]
        (left + (right - left) * fraction).coerceIn(0f, 1f)
    }
}

fun resolveCurveForBands(template: EqualizerTemplate, bandCount: Int): List<Float> =
    interpolateCurve(template.curve, bandCount)

fun resolveCurveForBands(curve: List<Float>, bandCount: Int): List<Float> =
    interpolateCurve(curve, bandCount)
