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
    val velocityDissipation: Float,
    val dyeDissipation: Float,
    val vorticity: Float,
    val waterDrift: Float
)

internal class FluidSimulationGridResolver {
    fun resolve(surfaceWidth: Int, surfaceHeight: Int, qualityLevel: Int): FluidSimulationGrid {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) {
            return FluidSimulationGrid(MIN_SIDE, MIN_SIDE)
        }

        val longSideLimit = when (qualityLevel.coerceIn(LOWEST_QUALITY, HIGHEST_QUALITY)) {
            0 -> 256
            -1 -> 224
            -2 -> 192
            else -> 160
        }
        val surfaceLong = maxOf(surfaceWidth, surfaceHeight).toFloat()
        val surfaceShort = minOf(surfaceWidth, surfaceHeight).toFloat()
        val targetLong = minOf(longSideLimit, maxOf(MIN_LONG_SIDE, (surfaceLong / 4f).toInt()))
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

    companion object {
        const val HIGHEST_QUALITY = 0
        const val LOWEST_QUALITY = -3
        private const val MIN_SIDE = 32
        private const val MIN_LONG_SIDE = 128
    }
}

internal class FluidPerformanceGovernor {
    private var pressureIterations = MAX_PRESSURE_ITERATIONS
    private var qualityLevel = FluidSimulationGridResolver.HIGHEST_QUALITY
    private var overBudgetFrameCount = 0
    private var underBudgetFrameCount = 0

    fun frameIntervalMillis(state: FluidRendererState): Long = when (state) {
        FluidRendererState.Active -> 34L
        FluidRendererState.Settling -> 42L
        FluidRendererState.IdlePersistent -> 120L
    }

    fun stepParams(state: FluidRendererState): FluidStepParams {
        val velocityDissipation = when (state) {
            FluidRendererState.Active -> 0.988f
            FluidRendererState.Settling -> 0.991f
            FluidRendererState.IdlePersistent -> 0.976f
        }
        val vorticity = when (state) {
            FluidRendererState.Active -> 5.6f
            FluidRendererState.Settling -> 4.4f
            FluidRendererState.IdlePersistent -> 1.4f
        }
        val waterDrift = when (state) {
            FluidRendererState.Active -> 1f
            FluidRendererState.Settling -> 1.45f
            FluidRendererState.IdlePersistent -> 1.65f
        }
        return FluidStepParams(
            pressureIterations = pressureIterations,
            velocityDissipation = velocityDissipation,
            dyeDissipation = 1f,
            vorticity = vorticity,
            waterDrift = waterDrift
        )
    }

    fun qualityLevel(): Int = qualityLevel

    fun reportFrameTime(frameMillis: Long, state: FluidRendererState): Boolean {
        val budget = frameIntervalMillis(state)
        return if (frameMillis > budget) {
            underBudgetFrameCount = 0
            overBudgetFrameCount += 1
            pressureIterations = (pressureIterations - 2).coerceAtLeast(MIN_PRESSURE_ITERATIONS)
            if (
                overBudgetFrameCount >= QUALITY_DROP_FRAME_THRESHOLD &&
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
                    (pressureIterations + 1).coerceAtMost(MAX_PRESSURE_ITERATIONS)
            }
            false
        }
    }

    companion object {
        private const val MAX_PRESSURE_ITERATIONS = 18
        private const val MIN_PRESSURE_ITERATIONS = 8
        private const val QUALITY_DROP_FRAME_THRESHOLD = 8
        private const val RECOVERY_FRAME_THRESHOLD = 90
    }
}
