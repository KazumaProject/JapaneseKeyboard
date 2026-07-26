package com.kazumaproject.core.domain.flick

import kotlin.math.abs

enum class FlickThresholdShape(val preferenceValue: String) {
    Radial("radial"),
    Rectangular("rectangular");

    companion object {
        fun fromPreferenceValue(value: String?): FlickThresholdShape {
            return entries.firstOrNull { it.preferenceValue == value } ?: Radial
        }
    }
}

/**
 * Shared distance and direction calculations for cardinal keyboard flick gestures.
 *
 * The persisted sensitivity is an abstract 1..200 user setting, not a pixel count.
 * A value of 100 maps to [normalMultiplier], while the two ends map to
 * [sensitiveMultiplier] and [stableMultiplier].
 */
object FlickGestureMath {

    fun thresholdPxForSensitivity(
        sensitivity: Int,
        scaledTouchSlopPx: Int,
        sensitiveMultiplier: Float,
        normalMultiplier: Float,
        stableMultiplier: Float
    ): Float {
        require(scaledTouchSlopPx > 0) { "scaledTouchSlopPx must be positive" }
        require(sensitiveMultiplier > 0f) { "sensitiveMultiplier must be positive" }
        require(normalMultiplier >= sensitiveMultiplier) {
            "normalMultiplier must be at least sensitiveMultiplier"
        }
        require(stableMultiplier >= normalMultiplier) {
            "stableMultiplier must be at least normalMultiplier"
        }

        val normalized = sensitivity.coerceIn(1, 200)
        val multiplier = if (normalized <= 100) {
            lerp(
                start = sensitiveMultiplier,
                end = normalMultiplier,
                fraction = (normalized - 1) / 99f
            )
        } else {
            lerp(
                start = normalMultiplier,
                end = stableMultiplier,
                fraction = (normalized - 100) / 100f
            )
        }
        return scaledTouchSlopPx * multiplier
    }

    fun isThresholdCrossed(
        deltaX: Float,
        deltaY: Float,
        thresholdPx: Float,
        thresholdShape: FlickThresholdShape = FlickThresholdShape.Radial
    ): Boolean {
        val safeThreshold = thresholdPx.coerceAtLeast(1f)
        return when (thresholdShape) {
            FlickThresholdShape.Radial ->
                deltaX * deltaX + deltaY * deltaY >= safeThreshold * safeThreshold

            FlickThresholdShape.Rectangular ->
                abs(deltaX) >= safeThreshold || abs(deltaY) >= safeThreshold
        }
    }

    fun cardinalDirection(
        deltaX: Float,
        deltaY: Float,
        thresholdPx: Float,
        thresholdShape: FlickThresholdShape = FlickThresholdShape.Radial
    ): FlickDirection {
        if (!isThresholdCrossed(deltaX, deltaY, thresholdPx, thresholdShape)) {
            return FlickDirection.Tap
        }

        return if (abs(deltaX) > abs(deltaY)) {
            if (deltaX < 0f) FlickDirection.Left else FlickDirection.Right
        } else {
            if (deltaY < 0f) FlickDirection.Top else FlickDirection.Bottom
        }
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction.coerceIn(0f, 1f)
    }
}
