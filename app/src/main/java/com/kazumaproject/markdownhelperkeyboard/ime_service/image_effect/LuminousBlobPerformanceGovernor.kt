package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

internal enum class LuminousBlobRendererState {
    Ambient,
    Active,
    Settling,
    Idle
}

internal data class LuminousBlobStepParams(
    val renderScale: Float,
    val edgeSharpness: Float,
    val innerStrength: Float,
    val shadowStrength: Float,
    val pointerGlowRadiusScale: Float,
    val pointerRisePerSecond: Float,
    val pointerDecayPerSecond: Float,
    val driftSpeedScale: Float,
    val maxAlpha: Float,
    val idleStrengthThreshold: Float
)

internal class LuminousBlobPerformanceGovernor {
    private var userQuality = KeyboardTouchEffectQuality.HIGH
    private var qualityLevel = HIGHEST_QUALITY

    fun configureQuality(value: String) {
        val normalized = KeyboardTouchEffectQuality.normalize(value)
        if (normalized == userQuality) return
        userQuality = normalized
        qualityLevel = HIGHEST_QUALITY
    }

    fun frameIntervalMillis(state: LuminousBlobRendererState): Long {
        val profile = qualityProfile(userQuality)
        return when (state) {
            LuminousBlobRendererState.Active -> fpsToIntervalMillis(profile.activeFps)
            LuminousBlobRendererState.Settling -> fpsToIntervalMillis(profile.settlingFps)
            LuminousBlobRendererState.Ambient -> fpsToIntervalMillis(profile.ambientFps)
            LuminousBlobRendererState.Idle -> 160L
        }
    }

    fun maxCommandsPerFrame(): Int {
        val scale = qualityScale()
        return (BASE_MAX_COMMANDS_PER_FRAME * scale).toInt()
            .coerceAtLeast(MIN_COMMANDS_PER_FRAME)
    }

    fun qualityLevel(): Int = qualityLevel

    fun stepParams(state: LuminousBlobRendererState): LuminousBlobStepParams {
        val profile = qualityProfile(userQuality)
        val runtimeScale = qualityScale()
        val stateAlpha = when (state) {
            LuminousBlobRendererState.Active -> 1f
            LuminousBlobRendererState.Settling -> 0.96f
            LuminousBlobRendererState.Ambient -> 0.9f
            LuminousBlobRendererState.Idle -> 0f
        }
        return LuminousBlobStepParams(
            renderScale = (profile.renderScale * runtimeScale)
                .coerceIn(MIN_RENDER_SCALE, 1f),
            edgeSharpness = profile.edgeSharpness,
            innerStrength = profile.innerStrength,
            shadowStrength = profile.shadowStrength,
            pointerGlowRadiusScale = profile.pointerGlowRadiusScale,
            pointerRisePerSecond = profile.pointerRisePerSecond,
            pointerDecayPerSecond = profile.pointerDecayPerSecond,
            driftSpeedScale = profile.driftSpeedScale,
            maxAlpha = profile.maxAlpha * stateAlpha,
            idleStrengthThreshold = DEFAULT_IDLE_STRENGTH_THRESHOLD
        )
    }

    fun reportFrameTime(frameMillis: Long, state: LuminousBlobRendererState): Boolean {
        if (state == LuminousBlobRendererState.Idle) return false
        val budget = frameIntervalMillis(state)
        return if (frameMillis > budget && qualityLevel > LOWEST_QUALITY) {
            qualityLevel -= 1
            true
        } else {
            false
        }
    }

    private fun qualityScale(): Float {
        return when (qualityLevel.coerceIn(LOWEST_QUALITY, HIGHEST_QUALITY)) {
            0 -> 1f
            -1 -> 0.86f
            -2 -> 0.72f
            else -> 0.58f
        }
    }

    private fun fpsToIntervalMillis(fps: Int): Long {
        return (1000f / fps.coerceAtLeast(1)).toLong().coerceAtLeast(1L)
    }

    companion object {
        const val HIGHEST_QUALITY = 0
        const val LOWEST_QUALITY = -3
        private const val BASE_MAX_COMMANDS_PER_FRAME = 48
        private const val MIN_COMMANDS_PER_FRAME = 12
        private const val MIN_RENDER_SCALE = 0.35f
        private const val DEFAULT_IDLE_STRENGTH_THRESHOLD = 0.018f

        private data class QualityProfile(
            val renderScale: Float,
            val ambientFps: Int,
            val activeFps: Int,
            val settlingFps: Int,
            val edgeSharpness: Float,
            val innerStrength: Float,
            val shadowStrength: Float,
            val pointerGlowRadiusScale: Float,
            val pointerRisePerSecond: Float,
            val pointerDecayPerSecond: Float,
            val driftSpeedScale: Float,
            val maxAlpha: Float
        )

        private fun qualityProfile(userQuality: String): QualityProfile {
            return when (KeyboardTouchEffectQuality.normalize(userQuality)) {
                KeyboardTouchEffectQuality.BALANCED -> QualityProfile(
                    renderScale = 0.5f,
                    ambientFps = 20,
                    activeFps = 45,
                    settlingFps = 30,
                    edgeSharpness = 14.5f,
                    innerStrength = 0.88f,
                    shadowStrength = 0.88f,
                    pointerGlowRadiusScale = 0.20f,
                    pointerRisePerSecond = 13.5f,
                    pointerDecayPerSecond = 2.8f,
                    driftSpeedScale = 0.92f,
                    maxAlpha = 0.48f
                )

                KeyboardTouchEffectQuality.ULTRA -> QualityProfile(
                    renderScale = 1f,
                    ambientFps = 30,
                    activeFps = 60,
                    settlingFps = 45,
                    edgeSharpness = 18.5f,
                    innerStrength = 1.04f,
                    shadowStrength = 1.02f,
                    pointerGlowRadiusScale = 0.24f,
                    pointerRisePerSecond = 16f,
                    pointerDecayPerSecond = 2.45f,
                    driftSpeedScale = 1f,
                    maxAlpha = 0.56f
                )

                KeyboardTouchEffectQuality.EXTREME -> QualityProfile(
                    renderScale = 1f,
                    ambientFps = 30,
                    activeFps = 60,
                    settlingFps = 45,
                    edgeSharpness = 21f,
                    innerStrength = 1.12f,
                    shadowStrength = 1.16f,
                    pointerGlowRadiusScale = 0.26f,
                    pointerRisePerSecond = 17.5f,
                    pointerDecayPerSecond = 2.25f,
                    driftSpeedScale = 1.08f,
                    maxAlpha = 0.6f
                )

                else -> QualityProfile(
                    renderScale = 0.75f,
                    ambientFps = 24,
                    activeFps = 60,
                    settlingFps = 40,
                    edgeSharpness = 16.5f,
                    innerStrength = 0.98f,
                    shadowStrength = 0.96f,
                    pointerGlowRadiusScale = 0.22f,
                    pointerRisePerSecond = 15f,
                    pointerDecayPerSecond = 2.6f,
                    driftSpeedScale = 1f,
                    maxAlpha = 0.52f
                )
            }
        }
    }
}
