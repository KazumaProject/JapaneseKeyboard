package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

internal enum class FluidRendererState {
    Active,
    Settling,
    IdlePersistent
}

internal data class FluidSimulationGrid(
    val width: Int,
    val height: Int
)

internal data class FluidStepParams(
    val pressureIterations: Int,
    val velocityDiffusionIterations: Int,
    val velocityViscosity: Float,
    val velocityDissipation: Float,
    val dyeDissipation: Float,
    val vorticity: Float,
    val waterDrift: Float
)

internal class FluidSimulationGridResolver {
    fun resolve(
        surfaceWidth: Int,
        surfaceHeight: Int,
        qualityLevel: Int,
        userQuality: String = KeyboardTouchEffectQuality.HIGH
    ): FluidSimulationGrid {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) {
            return FluidSimulationGrid(MIN_SIDE, MIN_SIDE)
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
            FluidSimulationGrid(
                width = alignEven(targetLong),
                height = alignEven(targetShort)
            )
        } else {
            FluidSimulationGrid(
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

internal class FluidPerformanceGovernor {
    private var userQuality = KeyboardTouchEffectQuality.HIGH
    private var pressureIterations = qualityProfile(userQuality).maxPressureIterations
    private var qualityLevel = FluidSimulationGridResolver.HIGHEST_QUALITY
    private var overBudgetFrameCount = 0
    private var underBudgetFrameCount = 0

    fun frameIntervalMillis(state: FluidRendererState): Long = when (state) {
        FluidRendererState.Active -> 16L
        FluidRendererState.Settling -> 34L
        FluidRendererState.IdlePersistent -> 120L
    }

    fun configureQuality(value: String) {
        val normalized = KeyboardTouchEffectQuality.normalize(value)
        if (normalized == userQuality) return
        userQuality = normalized
        val profile = qualityProfile(userQuality)
        pressureIterations = profile.maxPressureIterations
        qualityLevel = FluidSimulationGridResolver.HIGHEST_QUALITY
        overBudgetFrameCount = 0
        underBudgetFrameCount = 0
    }

    fun stepParams(
        state: FluidRendererState,
        transportMode: FluidInkTransportMode = FluidInkTransportMode.PHYSICAL
    ): FluidStepParams {
        val profile = qualityProfile(userQuality)
        val velocityDissipation = when (state) {
            FluidRendererState.Active -> profile.velocityDissipation
            FluidRendererState.Settling -> (profile.velocityDissipation + 0.003f).coerceAtMost(0.996f)
            FluidRendererState.IdlePersistent -> (profile.velocityDissipation - 0.01f).coerceAtLeast(0.965f)
        }
        val dyeDissipation = when (transportMode) {
            FluidInkTransportMode.PHYSICAL -> profile.dyeDissipation
            FluidInkTransportMode.WATER_DRIFT -> (profile.dyeDissipation + 0.0008f)
                .coerceAtMost(0.9998f)
        }
        return FluidStepParams(
            pressureIterations = pressureIterations,
            velocityDiffusionIterations = profile.velocityDiffusionIterations,
            velocityViscosity = profile.velocityViscosity,
            velocityDissipation = velocityDissipation,
            dyeDissipation = dyeDissipation,
            vorticity = profile.vorticityFor(state),
            waterDrift = profile.waterDriftFor(state, transportMode)
        )
    }

    fun qualityLevel(): Int = qualityLevel

    fun reportFrameTime(frameMillis: Long, state: FluidRendererState): Boolean {
        val budget = frameIntervalMillis(state)
        val profile = qualityProfile(userQuality)
        return if (frameMillis > budget) {
            underBudgetFrameCount = 0
            overBudgetFrameCount += 1
            if (pressureIterations > profile.minPressureIterations) {
                pressureIterations =
                    (pressureIterations - 2).coerceAtLeast(profile.minPressureIterations)
                overBudgetFrameCount = 0
                return false
            }
            if (
                overBudgetFrameCount >= profile.qualityDropFrameThreshold &&
                qualityLevel > FluidSimulationGridResolver.LOWEST_QUALITY
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
                pressureIterations =
                    (pressureIterations + 1).coerceAtMost(profile.maxPressureIterations)
            }
            false
        }
    }

    companion object {
        private const val RECOVERY_FRAME_THRESHOLD = 90

        private data class QualityProfile(
            val maxPressureIterations: Int,
            val minPressureIterations: Int,
            val qualityDropFrameThreshold: Int,
            val vorticityActive: Float,
            val vorticitySettling: Float,
            val vorticityIdle: Float,
            val velocityDiffusionIterations: Int,
            val velocityViscosity: Float,
            val velocityDissipation: Float,
            val dyeDissipation: Float,
            val waterDrift: Float
        ) {
            fun vorticityFor(state: FluidRendererState): Float {
                return when (state) {
                    FluidRendererState.Active -> vorticityActive
                    FluidRendererState.Settling -> vorticitySettling
                    FluidRendererState.IdlePersistent -> vorticityIdle
                }
            }

            fun waterDriftFor(
                state: FluidRendererState,
                transportMode: FluidInkTransportMode
            ): Float {
                if (transportMode != FluidInkTransportMode.WATER_DRIFT) return 0f
                return when (state) {
                    FluidRendererState.Active -> waterDrift
                    FluidRendererState.Settling -> waterDrift * 0.86f
                    FluidRendererState.IdlePersistent -> waterDrift * 0.72f
                }
            }
        }

        private fun qualityProfile(userQuality: String): QualityProfile {
            return when (KeyboardTouchEffectQuality.normalize(userQuality)) {
                KeyboardTouchEffectQuality.BALANCED -> QualityProfile(
                    maxPressureIterations = 20,
                    minPressureIterations = 10,
                    qualityDropFrameThreshold = 8,
                    vorticityActive = 3.8f,
                    vorticitySettling = 3.0f,
                    vorticityIdle = 0.9f,
                    velocityDiffusionIterations = 4,
                    velocityViscosity = 0.000045f,
                    velocityDissipation = 0.984f,
                    dyeDissipation = 0.9968f,
                    waterDrift = 0.030f
                )

                KeyboardTouchEffectQuality.ULTRA -> QualityProfile(
                    maxPressureIterations = 48,
                    minPressureIterations = 18,
                    qualityDropFrameThreshold = 36,
                    vorticityActive = 7.2f,
                    vorticitySettling = 5.8f,
                    vorticityIdle = 1.7f,
                    velocityDiffusionIterations = 8,
                    velocityViscosity = 0.000025f,
                    velocityDissipation = 0.991f,
                    dyeDissipation = 0.9992f,
                    waterDrift = 0.040f
                )

                KeyboardTouchEffectQuality.EXTREME -> QualityProfile(
                    maxPressureIterations = 64,
                    minPressureIterations = 24,
                    qualityDropFrameThreshold = 60,
                    vorticityActive = 8.4f,
                    vorticitySettling = 6.8f,
                    vorticityIdle = 2.0f,
                    velocityDiffusionIterations = 10,
                    velocityViscosity = 0.000018f,
                    velocityDissipation = 0.993f,
                    dyeDissipation = 0.99955f,
                    waterDrift = 0.045f
                )

                else -> QualityProfile(
                    maxPressureIterations = 32,
                    minPressureIterations = 14,
                    qualityDropFrameThreshold = 20,
                    vorticityActive = 5.6f,
                    vorticitySettling = 4.4f,
                    vorticityIdle = 1.4f,
                    velocityDiffusionIterations = 6,
                    velocityViscosity = 0.000035f,
                    velocityDissipation = 0.988f,
                    dyeDissipation = 0.9984f,
                    waterDrift = 0.035f
                )
            }
        }
    }
}
