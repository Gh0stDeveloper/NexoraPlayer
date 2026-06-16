package com.nexora.player.equalizer

import android.media.audiofx.Equalizer
import kotlin.math.roundToInt


data class EqualizerHardwareInfo(
    val supported: Boolean,
    val bandCount: Int,
    val minLevelDb: Float,
    val maxLevelDb: Float,
    val centerFrequenciesHz: List<Float>
)

class EqualizerController(audioSessionId: Int) : AutoCloseable {

    private var equalizer: Equalizer? = null

    val hardwareInfo: EqualizerHardwareInfo

    init {
        hardwareInfo = runCatching {
            val effect = Equalizer(0, audioSessionId)
            equalizer = effect

            val bandCount = effect.numberOfBands.toInt().coerceAtLeast(0)
            val range = effect.bandLevelRange
            val minLevelDb = range.getOrNull(0)?.toFloat()?.div(100f) ?: -15f
            val maxLevelDb = range.getOrNull(1)?.toFloat()?.div(100f) ?: 15f
            val centerFrequenciesHz = (0 until bandCount).map { band ->
                effect.getCenterFreq(band.toShort()) / 1000f
            }

            effect.enabled = true

            EqualizerHardwareInfo(
                supported = bandCount > 0,
                bandCount = bandCount,
                minLevelDb = minLevelDb,
                maxLevelDb = maxLevelDb,
                centerFrequenciesHz = centerFrequenciesHz
            )
        }.getOrElse {
            EqualizerHardwareInfo(
                supported = false,
                bandCount = 0,
                minLevelDb = -15f,
                maxLevelDb = 15f,
                centerFrequenciesHz = emptyList()
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        runCatching { equalizer?.enabled = enabled }
    }

    fun applyCurve(normalizedCurve: List<Float>) {
        val effect = equalizer ?: return
        if (!hardwareInfo.supported || hardwareInfo.bandCount <= 0) return

        val interpolated = resolveCurveForBands(normalizedCurve, hardwareInfo.bandCount)
        interpolated.forEachIndexed { index, value ->
            val levelDb = hardwareInfo.minLevelDb + value.coerceIn(0f, 1f) *
                (hardwareInfo.maxLevelDb - hardwareInfo.minLevelDb)
            val levelMb = (levelDb * 100f).roundToInt().toShort()
            runCatching { effect.setBandLevel(index.toShort(), levelMb) }
        }
    }

    override fun close() {
        runCatching { equalizer?.release() }
        equalizer = null
    }
}
