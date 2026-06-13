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
    val energyDamping: Float,
    val idleEnergyThreshold: Float
)

internal class LiquidRippleSimulationGridResolver {
    fun resolve(surfaceWidth: Int, surfaceHeight: Int, qualityLevel: Int): LiquidRippleSimulationGrid {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) {
            return LiquidRippleSimulationGrid(MIN_SIDE, MIN_SIDE)
        }

        val longSideLimit = when (qualityLevel.coerceIn(LOWEST_QUALITY, HIGHEST_QUALITY)) {
            0 -> 192
            -1 -> 176
            -2 -> 160
            else -> 128
        }
        val surfaceLong = maxOf(surfaceWidth, surfaceHeight).toFloat()
        val surfaceShort = minOf(surfaceWidth, surfaceHeight).toFloat()
        val targetLong = minOf(longSideLimit, maxOf(MIN_LONG_SIDE, (surfaceLong / 5f).toInt()))
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

    companion object {
        const val HIGHEST_QUALITY = 0
        const val LOWEST_QUALITY = -3
        private const val MIN_SIDE = 32
        private const val MIN_LONG_SIDE = 128
    }
}

internal class LiquidRipplePerformanceGovernor {
    private var qualityLevel = LiquidRippleSimulationGridResolver.HIGHEST_QUALITY
    private var overBudgetFrameCount = 0
    private var underBudgetFrameCount = 0

    fun frameIntervalMillis(state: LiquidRippleRendererState): Long = when (state) {
        LiquidRippleRendererState.Active -> 16L
        LiquidRippleRendererState.Settling -> 34L
        LiquidRippleRendererState.Idle -> 120L
    }

    fun stepParams(state: LiquidRippleRendererState): LiquidRippleStepParams {
        val damping = when (state) {
            LiquidRippleRendererState.Active -> 0.992f
            LiquidRippleRendererState.Settling -> 0.988f
            LiquidRippleRendererState.Idle -> 0.982f
        }
        return LiquidRippleStepParams(
            waveSpeed = 0.235f,
            damping = damping,
            energyDamping = damping,
            idleEnergyThreshold = 0.012f
        )
    }

    fun qualityLevel(): Int = qualityLevel

    fun reportFrameTime(frameMillis: Long, state: LiquidRippleRendererState): Boolean {
        val budget = frameIntervalMillis(state)
        return if (frameMillis > budget) {
            underBudgetFrameCount = 0
            overBudgetFrameCount += 1
            if (
                overBudgetFrameCount >= QUALITY_DROP_FRAME_THRESHOLD &&
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
        private const val QUALITY_DROP_FRAME_THRESHOLD = 8
        private const val RECOVERY_FRAME_THRESHOLD = 120
    }
}
