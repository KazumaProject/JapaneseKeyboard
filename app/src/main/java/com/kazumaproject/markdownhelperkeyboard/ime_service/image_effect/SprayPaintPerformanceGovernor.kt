package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

internal enum class SprayPaintRendererState {
    Active,
    Settling,
    Idle
}

internal data class SprayPaintSimulationGrid(
    val width: Int,
    val height: Int
)

internal data class SprayPaintStepParams(
    val tapParticleCount: Int,
    val moveParticleCount: Int,
    val upParticleCount: Int,
    val popParticleCount: Int,
    val longPressParticlesPerSecond: Float,
    val maxParticlesPerFrame: Int,
    val mistScale: Float,
    val shineMode: Int,
    val shineStrength: Float,
    val dripEnabled: Boolean,
    val decayPerSecond: Float,
    val idleEnergyThreshold: Float
)

internal class SprayPaintSimulationGridResolver {
    fun resolve(
        surfaceWidth: Int,
        surfaceHeight: Int,
        qualityLevel: Int,
        userQuality: String = KeyboardTouchEffectQuality.HIGH
    ): SprayPaintSimulationGrid {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) {
            return SprayPaintSimulationGrid(MIN_SIDE, MIN_SIDE)
        }

        val normalizedQuality = KeyboardTouchEffectQuality.normalize(userQuality)
        val runtimeLimit = (baseLongSide(normalizedQuality) * qualityFallbackScale(qualityLevel)).toInt()
        val surfaceLong = maxOf(surfaceWidth, surfaceHeight).toFloat()
        val surfaceShort = minOf(surfaceWidth, surfaceHeight).toFloat()
        val targetLong = minOf(runtimeLimit, surfaceLong.toInt()).coerceAtLeast(MIN_LONG_SIDE)
        val targetShort = maxOf(MIN_SIDE, (targetLong * surfaceShort / surfaceLong).toInt())

        return if (surfaceWidth >= surfaceHeight) {
            SprayPaintSimulationGrid(
                width = alignEven(targetLong),
                height = alignEven(targetShort)
            )
        } else {
            SprayPaintSimulationGrid(
                width = alignEven(targetShort),
                height = alignEven(targetLong)
            )
        }
    }

    private fun alignEven(value: Int): Int {
        val clamped = value.coerceAtLeast(MIN_SIDE)
        return if (clamped % 2 == 0) clamped else clamped + 1
    }

    private fun baseLongSide(userQuality: String): Int {
        return when (KeyboardTouchEffectQuality.normalize(userQuality)) {
            KeyboardTouchEffectQuality.BALANCED -> 384
            KeyboardTouchEffectQuality.ULTRA -> 768
            KeyboardTouchEffectQuality.EXTREME -> 1024
            else -> 512
        }
    }

    private fun qualityFallbackScale(qualityLevel: Int): Float {
        return when (qualityLevel.coerceIn(LOWEST_QUALITY, HIGHEST_QUALITY)) {
            0 -> 1f
            -1 -> 0.84f
            -2 -> 0.68f
            else -> 0.54f
        }
    }

    companion object {
        const val HIGHEST_QUALITY = 0
        const val LOWEST_QUALITY = -3
        private const val MIN_SIDE = 32
        private const val MIN_LONG_SIDE = 128
    }
}

internal class SprayPaintPerformanceGovernor {
    private var userQuality = KeyboardTouchEffectQuality.HIGH
    private var qualityLevel = SprayPaintSimulationGridResolver.HIGHEST_QUALITY
    private var overBudgetFrameCount = 0
    private var underBudgetFrameCount = 0

    fun configureQuality(value: String) {
        val normalized = KeyboardTouchEffectQuality.normalize(value)
        if (normalized == userQuality) return
        userQuality = normalized
        qualityLevel = SprayPaintSimulationGridResolver.HIGHEST_QUALITY
        overBudgetFrameCount = 0
        underBudgetFrameCount = 0
    }

    fun frameIntervalMillis(state: SprayPaintRendererState): Long = when (state) {
        SprayPaintRendererState.Active -> 16L
        SprayPaintRendererState.Settling -> 34L
        SprayPaintRendererState.Idle -> 160L
    }

    fun maxCommandsPerFrame(): Int {
        val profile = qualityProfile(userQuality)
        val scale = qualityScale()
        return (profile.maxCommandsPerFrame * scale).toInt()
            .coerceAtLeast(MIN_COMMANDS_PER_FRAME)
    }

    fun qualityLevel(): Int = qualityLevel

    fun stepParams(state: SprayPaintRendererState): SprayPaintStepParams {
        val profile = qualityProfile(userQuality)
        val scale = qualityScale()
        val activeDecay = when (state) {
            SprayPaintRendererState.Active -> profile.activeDecayPerSecond
            SprayPaintRendererState.Settling -> profile.settlingDecayPerSecond
            SprayPaintRendererState.Idle -> profile.idleDecayPerSecond
        }
        return SprayPaintStepParams(
            tapParticleCount = (profile.tapParticleCount * scale).toInt().coerceAtLeast(12),
            moveParticleCount = (profile.moveParticleCount * scale).toInt().coerceAtLeast(8),
            upParticleCount = (profile.upParticleCount * scale).toInt().coerceAtLeast(4),
            popParticleCount = (profile.popParticleCount * scale).toInt().coerceAtLeast(4),
            longPressParticlesPerSecond = profile.longPressParticlesPerSecond * scale,
            maxParticlesPerFrame = (profile.maxParticlesPerFrame * scale).toInt().coerceAtLeast(64),
            mistScale = profile.mistScale,
            shineMode = profile.shineMode,
            shineStrength = profile.shineStrength,
            dripEnabled = profile.dripEnabled && qualityLevel > SprayPaintSimulationGridResolver.LOWEST_QUALITY,
            decayPerSecond = activeDecay,
            idleEnergyThreshold = DEFAULT_IDLE_ENERGY_THRESHOLD
        )
    }

    fun reportFrameTime(frameMillis: Long, state: SprayPaintRendererState): Boolean {
        val budget = frameIntervalMillis(state)
        val profile = qualityProfile(userQuality)
        return if (frameMillis > budget) {
            underBudgetFrameCount = 0
            overBudgetFrameCount += 1
            if (
                overBudgetFrameCount >= profile.qualityDropFrameThreshold &&
                qualityLevel > SprayPaintSimulationGridResolver.LOWEST_QUALITY
            ) {
                overBudgetFrameCount = 0
                qualityLevel -= 1
                true
            } else {
                false
            }
        } else {
            overBudgetFrameCount = 0
            underBudgetFrameCount += 1
            if (underBudgetFrameCount >= RECOVERY_FRAME_THRESHOLD) {
                underBudgetFrameCount = 0
            }
            false
        }
    }

    private fun qualityScale(): Float {
        return when (qualityLevel.coerceIn(
            SprayPaintSimulationGridResolver.LOWEST_QUALITY,
            SprayPaintSimulationGridResolver.HIGHEST_QUALITY
        )) {
            0 -> 1f
            -1 -> 0.82f
            -2 -> 0.64f
            else -> 0.48f
        }
    }

    companion object {
        private const val DEFAULT_IDLE_ENERGY_THRESHOLD = 0.012f
        private const val RECOVERY_FRAME_THRESHOLD = 120
        private const val MIN_COMMANDS_PER_FRAME = 16

        private data class QualityProfile(
            val tapParticleCount: Int,
            val moveParticleCount: Int,
            val upParticleCount: Int,
            val popParticleCount: Int,
            val longPressParticlesPerSecond: Float,
            val maxParticlesPerFrame: Int,
            val maxCommandsPerFrame: Int,
            val mistScale: Float,
            val shineMode: Int,
            val shineStrength: Float,
            val dripEnabled: Boolean,
            val activeDecayPerSecond: Float,
            val settlingDecayPerSecond: Float,
            val idleDecayPerSecond: Float,
            val qualityDropFrameThreshold: Int
        )

        private fun qualityProfile(userQuality: String): QualityProfile {
            return when (KeyboardTouchEffectQuality.normalize(userQuality)) {
                KeyboardTouchEffectQuality.BALANCED -> QualityProfile(
                    tapParticleCount = 36,
                    moveParticleCount = 16,
                    upParticleCount = 8,
                    popParticleCount = 10,
                    longPressParticlesPerSecond = 18f,
                    maxParticlesPerFrame = 180,
                    maxCommandsPerFrame = 32,
                    mistScale = 0.86f,
                    shineMode = 0,
                    shineStrength = 0.055f,
                    dripEnabled = false,
                    activeDecayPerSecond = 0.955f,
                    settlingDecayPerSecond = 0.93f,
                    idleDecayPerSecond = 0.86f,
                    qualityDropFrameThreshold = 8
                )

                KeyboardTouchEffectQuality.ULTRA -> QualityProfile(
                    tapParticleCount = 128,
                    moveParticleCount = 46,
                    upParticleCount = 22,
                    popParticleCount = 28,
                    longPressParticlesPerSecond = 46f,
                    maxParticlesPerFrame = 560,
                    maxCommandsPerFrame = 72,
                    mistScale = 1.14f,
                    shineMode = 2,
                    shineStrength = 0.1f,
                    dripEnabled = true,
                    activeDecayPerSecond = 0.976f,
                    settlingDecayPerSecond = 0.955f,
                    idleDecayPerSecond = 0.89f,
                    qualityDropFrameThreshold = 36
                )

                KeyboardTouchEffectQuality.EXTREME -> QualityProfile(
                    tapParticleCount = 208,
                    moveParticleCount = 72,
                    upParticleCount = 32,
                    popParticleCount = 42,
                    longPressParticlesPerSecond = 64f,
                    maxParticlesPerFrame = 840,
                    maxCommandsPerFrame = 96,
                    mistScale = 1.28f,
                    shineMode = 3,
                    shineStrength = 0.12f,
                    dripEnabled = true,
                    activeDecayPerSecond = 0.982f,
                    settlingDecayPerSecond = 0.964f,
                    idleDecayPerSecond = 0.905f,
                    qualityDropFrameThreshold = 60
                )

                else -> QualityProfile(
                    tapParticleCount = 72,
                    moveParticleCount = 28,
                    upParticleCount = 14,
                    popParticleCount = 18,
                    longPressParticlesPerSecond = 32f,
                    maxParticlesPerFrame = 340,
                    maxCommandsPerFrame = 48,
                    mistScale = 1f,
                    shineMode = 1,
                    shineStrength = 0.08f,
                    dripEnabled = true,
                    activeDecayPerSecond = 0.968f,
                    settlingDecayPerSecond = 0.945f,
                    idleDecayPerSecond = 0.88f,
                    qualityDropFrameThreshold = 20
                )
            }
        }
    }
}
