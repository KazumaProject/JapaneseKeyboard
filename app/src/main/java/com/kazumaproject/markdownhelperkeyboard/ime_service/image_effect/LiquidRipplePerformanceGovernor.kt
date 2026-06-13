package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

internal enum class LiquidRippleRendererState {
    Active,
    Settling,
    Idle
}

internal data class LiquidRippleSimulationGrid(
    val width: Int,
    val height: Int
)

internal data class LiquidRippleStepParams(
    val waveSpeed: Float,
    val damping: Float,
    val boundaryAbsorptionWidth: Float,
    val normalSampleMode: Int,
    val energyDamping: Float,
    val idleEnergyThreshold: Float
)

internal class LiquidRippleSimulationGridResolver {
    fun resolve(
        surfaceWidth: Int,
        surfaceHeight: Int,
        qualityLevel: Int,
        userQuality: String = KeyboardTouchEffectQuality.HIGH
    ): LiquidRippleSimulationGrid {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) {
            return LiquidRippleSimulationGrid(MIN_SIDE, MIN_SIDE)
        }

        val normalizedQuality = KeyboardTouchEffectQuality.normalize(userQuality)
        val baseLongSide = baseLongSide(normalizedQuality)
        val runtimeLimit = (baseLongSide * qualityFallbackScale(qualityLevel)).toInt()
        val surfaceLong = maxOf(surfaceWidth, surfaceHeight).toFloat()
        val surfaceShort = minOf(surfaceWidth, surfaceHeight).toFloat()
        val divisor = targetLongSideDivisor(normalizedQuality)
        val targetLong = minOf(
            runtimeLimit,
            maxOf(MIN_LONG_SIDE, (surfaceLong / divisor).toInt())
        )
        val targetShort = maxOf(MIN_SIDE, (targetLong * surfaceShort / surfaceLong).toInt())

        return if (surfaceWidth >= surfaceHeight) {
            LiquidRippleSimulationGrid(
                width = alignEven(targetLong),
                height = alignEven(targetShort)
            )
        } else {
            LiquidRippleSimulationGrid(
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

    private fun targetLongSideDivisor(userQuality: String): Float {
        return when (KeyboardTouchEffectQuality.normalize(userQuality)) {
            KeyboardTouchEffectQuality.BALANCED -> 3f
            KeyboardTouchEffectQuality.ULTRA -> 2f
            KeyboardTouchEffectQuality.EXTREME -> 1.5f
            else -> 2.5f
        }
    }

    private fun qualityFallbackScale(qualityLevel: Int): Float {
        return when (qualityLevel.coerceIn(LOWEST_QUALITY, HIGHEST_QUALITY)) {
            0 -> 1f
            -1 -> 0.875f
            -2 -> 0.75f
            else -> 0.625f
        }
    }

    companion object {
        const val HIGHEST_QUALITY = 0
        const val LOWEST_QUALITY = -3
        private const val MIN_SIDE = 32
        private const val MIN_LONG_SIDE = 128
    }
}

internal class LiquidRipplePerformanceGovernor {
    private var userQuality = KeyboardTouchEffectQuality.HIGH
    private var qualityLevel = LiquidRippleSimulationGridResolver.HIGHEST_QUALITY
    private var overBudgetFrameCount = 0
    private var underBudgetFrameCount = 0

    fun frameIntervalMillis(state: LiquidRippleRendererState): Long = when (state) {
        LiquidRippleRendererState.Active -> 16L
        LiquidRippleRendererState.Settling -> 34L
        LiquidRippleRendererState.Idle -> 120L
    }

    fun configureQuality(value: String) {
        val normalized = KeyboardTouchEffectQuality.normalize(value)
        if (normalized == userQuality) return
        userQuality = normalized
        qualityLevel = LiquidRippleSimulationGridResolver.HIGHEST_QUALITY
        overBudgetFrameCount = 0
        underBudgetFrameCount = 0
    }

    fun stepParams(state: LiquidRippleRendererState): LiquidRippleStepParams {
        val profile = qualityProfile(userQuality)
        val damping = when (state) {
            LiquidRippleRendererState.Active -> profile.activeDamping
            LiquidRippleRendererState.Settling -> profile.settlingDamping
            LiquidRippleRendererState.Idle -> profile.idleDamping
        }
        return LiquidRippleStepParams(
            waveSpeed = profile.waveSpeed,
            damping = damping,
            boundaryAbsorptionWidth = profile.boundaryAbsorptionWidth,
            normalSampleMode = profile.normalSampleMode,
            energyDamping = damping,
            idleEnergyThreshold = 0.012f
        )
    }

    fun qualityLevel(): Int = qualityLevel

    fun reportFrameTime(frameMillis: Long, state: LiquidRippleRendererState): Boolean {
        val budget = frameIntervalMillis(state)
        val profile = qualityProfile(userQuality)
        return if (frameMillis > budget) {
            underBudgetFrameCount = 0
            overBudgetFrameCount += 1
            if (
                overBudgetFrameCount >= profile.qualityDropFrameThreshold &&
                qualityLevel > LiquidRippleSimulationGridResolver.LOWEST_QUALITY
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

    companion object {
        private const val RECOVERY_FRAME_THRESHOLD = 120

        private data class QualityProfile(
            val waveSpeed: Float,
            val activeDamping: Float,
            val settlingDamping: Float,
            val idleDamping: Float,
            val boundaryAbsorptionWidth: Float,
            val normalSampleMode: Int,
            val qualityDropFrameThreshold: Int
        )

        private fun qualityProfile(userQuality: String): QualityProfile {
            return when (KeyboardTouchEffectQuality.normalize(userQuality)) {
                KeyboardTouchEffectQuality.BALANCED -> QualityProfile(
                    waveSpeed = 0.118f,
                    activeDamping = 0.986f,
                    settlingDamping = 0.989f,
                    idleDamping = 0.982f,
                    boundaryAbsorptionWidth = 0.055f,
                    normalSampleMode = 0,
                    qualityDropFrameThreshold = 8
                )

                KeyboardTouchEffectQuality.ULTRA -> QualityProfile(
                    waveSpeed = 0.132f,
                    activeDamping = 0.991f,
                    settlingDamping = 0.994f,
                    idleDamping = 0.987f,
                    boundaryAbsorptionWidth = 0.07f,
                    normalSampleMode = 2,
                    qualityDropFrameThreshold = 36
                )

                KeyboardTouchEffectQuality.EXTREME -> QualityProfile(
                    waveSpeed = 0.136f,
                    activeDamping = 0.992f,
                    settlingDamping = 0.995f,
                    idleDamping = 0.988f,
                    boundaryAbsorptionWidth = 0.085f,
                    normalSampleMode = 3,
                    qualityDropFrameThreshold = 60
                )

                else -> QualityProfile(
                    waveSpeed = 0.126f,
                    activeDamping = 0.989f,
                    settlingDamping = 0.992f,
                    idleDamping = 0.985f,
                    boundaryAbsorptionWidth = 0.06f,
                    normalSampleMode = 1,
                    qualityDropFrameThreshold = 20
                )
            }
        }
    }
}
