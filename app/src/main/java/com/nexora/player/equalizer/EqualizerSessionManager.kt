package com.nexora.player.equalizer

/**
 * Mantiene vivo un único ecualizador para la sesión de audio actual.
 * La UI solo modifica presets y bandas; el efecto sigue activo aunque la pantalla se cierre.
 */
object EqualizerSessionManager {
    private val lock = Any()

    private var controller: EqualizerController? = null
    private var attachedSessionId: Int = Int.MIN_VALUE
    private var hardwareInfoCache: EqualizerHardwareInfo = defaultHardwareInfo

    val hardwareInfo: EqualizerHardwareInfo
        get() = synchronized(lock) { hardwareInfoCache }

    fun attach(audioSessionId: Int): EqualizerHardwareInfo = synchronized(lock) {
        if (audioSessionId <= 0) return hardwareInfoCache

        if (attachedSessionId == audioSessionId && controller != null) {
            return hardwareInfoCache
        }

        controller?.close()
        controller = runCatching { EqualizerController(audioSessionId) }.getOrNull()
        attachedSessionId = audioSessionId
        hardwareInfoCache = controller?.hardwareInfo ?: defaultHardwareInfo
        hardwareInfoCache
    }

    fun applySettings(settings: EqualizerSettings) = synchronized(lock) {
        val effect = controller ?: return
        val curve = if (settings.templateId == NEXORA_CUSTOM_TEMPLATE_ID && settings.customCurve.isNotEmpty()) {
            settings.customCurve
        } else {
            NexoraEqualizerTemplates.resolve(settings.templateId).curve
        }

        effect.setEnabled(settings.enabled)
        effect.applyCurve(curve)
    }

    fun sync(audioSessionId: Int, settings: EqualizerSettings): EqualizerHardwareInfo {
        val info = attach(audioSessionId)
        applySettings(settings)
        return info
    }

    fun release() = synchronized(lock) {
        controller?.close()
        controller = null
        attachedSessionId = Int.MIN_VALUE
        hardwareInfoCache = defaultHardwareInfo
    }

    private val defaultHardwareInfo = EqualizerHardwareInfo(
        supported = false,
        bandCount = 0,
        minLevelDb = -15f,
        maxLevelDb = 15f,
        centerFrequenciesHz = emptyList()
    )
}
