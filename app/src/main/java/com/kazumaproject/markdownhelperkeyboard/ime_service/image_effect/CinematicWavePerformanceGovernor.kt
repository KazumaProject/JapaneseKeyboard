package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

internal enum class CinematicWaveRendererState {
    Ambient,
    Active,
    Settling,
    Idle
}

internal data class CinematicWaveStepParams(
    val renderScale: Float,
    val frameIntervalMs: Long,
    val noiseOctaves: Int,
    val glowStrength: Float,
    val warpStrength: Float,
    val motionSpeed: Float,
    val touchResponse: Float,
    val maxCommandsPerFrame: Int
)

internal class CinematicWavePerformanceGovernor {
    private var quality = CinematicWaveSettings.QUALITY_BALANCED
    private var qualityLevel = HIGHEST_QUALITY

    fun configureQuality(value: String) {
        val normalized = CinematicWaveSettings.normalizeQuality(value)
        if (normalized == quality) return
        quality = normalized
        qualityLevel = HIGHEST_QUALITY
    }

    fun stepParams(settings: CinematicWaveSettings, state: CinematicWaveRendererState):
        CinematicWaveStepParams {
        val profile = profile(quality)
        val runtimeScale = runtimeScale()
        val motionScale = when (settings.normalizedMotion) {
            CinematicWaveSettings.MOTION_CALM -> 0.64f
            CinematicWaveSettings.MOTION_DYNAMIC -> 1.32f
            else -> 1f
        }
        val touchScale = when (settings.normalizedTouchResponse) {
            CinematicWaveSettings.TOUCH_RESPONSE_SUBTLE -> 0.70f
            CinematicWaveSettings.TOUCH_RESPONSE_DEEP -> 1.34f
            else -> 1f
        }
        val stateFpsScale = when (state) {
            CinematicWaveRendererState.Active -> 1f
            CinematicWaveRendererState.Settling -> 0.72f
            CinematicWaveRendererState.Ambient -> profile.ambientFpsScale
            CinematicWaveRendererState.Idle -> 0.25f
        }
        val fps = (profile.maxFps * stateFpsScale).toInt().coerceAtLeast(12)
        return CinematicWaveStepParams(
            renderScale = (profile.renderScale * runtimeScale).coerceIn(MIN_RENDER_SCALE, 1f),
            frameIntervalMs = fpsToIntervalMillis(fps),
            noiseOctaves = profile.noiseOctaves,
            glowStrength = profile.glowStrength,
            warpStrength = profile.warpStrength,
            motionSpeed = profile.motionSpeed * motionScale,
            touchResponse = touchScale,
            maxCommandsPerFrame = (BASE_MAX_COMMANDS_PER_FRAME * runtimeScale).toInt()
                .coerceAtLeast(MIN_COMMANDS_PER_FRAME)
        )
    }

    fun reportFrameTime(frameMillis: Long, state: CinematicWaveRendererState): Boolean {
        if (state == CinematicWaveRendererState.Idle) return false
        return if (frameMillis > profile(quality).budgetMs && qualityLevel > LOWEST_QUALITY) {
            qualityLevel -= 1
            true
        } else {
            false
        }
    }

    fun qualityLevel(): Int = qualityLevel

    private fun runtimeScale(): Float {
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

    private data class QualityProfile(
        val renderScale: Float,
        val maxFps: Int,
        val ambientFpsScale: Float,
        val noiseOctaves: Int,
        val glowStrength: Float,
        val warpStrength: Float,
        val motionSpeed: Float,
        val budgetMs: Long
    )

    private fun profile(quality: String): QualityProfile {
        return when (CinematicWaveSettings.normalizeQuality(quality)) {
            CinematicWaveSettings.QUALITY_BATTERY_SAVER -> QualityProfile(
                renderScale = 0.5f,
                maxFps = 30,
                ambientFpsScale = 0.84f,
                noiseOctaves = 2,
                glowStrength = 0.78f,
                warpStrength = 0.76f,
                motionSpeed = 0.86f,
                budgetMs = 34L
            )

            CinematicWaveSettings.QUALITY_CINEMATIC -> QualityProfile(
                renderScale = 1f,
                maxFps = 60,
                ambientFpsScale = 1f,
                noiseOctaves = 4,
                glowStrength = 1.18f,
                warpStrength = 1.12f,
                motionSpeed = 1f,
                budgetMs = 17L
            )

            else -> QualityProfile(
                renderScale = 0.75f,
                maxFps = 60,
                ambientFpsScale = 0.72f,
                noiseOctaves = 3,
                glowStrength = 1f,
                warpStrength = 1f,
                motionSpeed = 0.94f,
                budgetMs = 18L
            )
        }
    }

    companion object {
        const val HIGHEST_QUALITY = 0
        const val LOWEST_QUALITY = -3
        private const val MIN_RENDER_SCALE = 0.45f
        private const val BASE_MAX_COMMANDS_PER_FRAME = 48
        private const val MIN_COMMANDS_PER_FRAME = 12
    }
}
