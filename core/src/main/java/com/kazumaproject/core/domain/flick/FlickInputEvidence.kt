package com.kazumaproject.core.domain.flick

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** Immutable costs from a completed gesture. Coordinates never leave the keyboard view. */
data class FlickInputEvidence(
    val observedChar: Char,
    val alternativeCostFactors: Map<Char, Float>,
) {
    fun costFactor(char: Char): Float =
        alternativeCostFactors[char]?.takeIf { it.isFinite() }?.coerceIn(0.5f, 2.5f) ?: 1f
}

data class FlickEvidenceKey(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val characters: Map<FlickDirection, Char>,
)

object FlickEvidenceModel {
    fun create(
        observedChar: Char,
        downX: Float,
        downY: Float,
        deltaX: Float,
        deltaY: Float,
        thresholdPx: Float,
        thresholdShape: FlickThresholdShape,
        keys: List<FlickEvidenceKey>,
    ): FlickInputEvidence? {
        if (listOf(downX, downY, deltaX, deltaY, thresholdPx).any { !it.isFinite() }) return null
        val observedDirection = FlickGestureMath.cardinalDirection(
            deltaX, deltaY, thresholdPx, thresholdShape,
        )
        val factors = buildMap<Char, Float> {
            for (key in keys) {
                if (key.width <= 0f || key.height <= 0f) continue
                val xDistance = max(max(key.left - downX, downX - key.left - key.width), 0f) / key.width
                val yDistance = max(max(key.top - downY, downY - key.top - key.height), 0f) / key.height
                val keyDistance = hypot(xDistance, yDistance)
                val keyFactor = if (keyDistance == 0f) 0.5f else 0.5f + 3f * keyDistance
                for ((direction, char) in key.characters) {
                    if (char !in 'ぁ'..'ゖ' && char != 'ー') continue
                    val directionFactor = directionCost(
                        observedDirection, direction, deltaX, deltaY, thresholdPx, thresholdShape,
                    )
                    val factor = max(keyFactor, directionFactor).coerceIn(0.5f, 2.5f)
                    val previous = get(char)
                    if (previous == null || factor < previous) put(char, factor)
                }
            }
        }
        return FlickInputEvidence(observedChar, factors)
    }

    private fun directionCost(
        observed: FlickDirection,
        alternative: FlickDirection,
        dx: Float,
        dy: Float,
        threshold: Float,
        shape: FlickThresholdShape,
    ): Float {
        if (observed == alternative) return 0.5f
        val distance = when (shape) {
            FlickThresholdShape.Radial -> hypot(dx, dy)
            FlickThresholdShape.Rectangular -> max(abs(dx), abs(dy))
        }
        val ratio = distance / threshold.coerceAtLeast(1f)
        if (alternative == FlickDirection.Tap) return (0.5f + abs(ratio - 1f)).coerceAtMost(2.5f)
        val component = when (alternative) {
            FlickDirection.Left -> -dx
            FlickDirection.Right -> dx
            FlickDirection.Top -> -dy
            FlickDirection.Bottom -> dy
            FlickDirection.Tap -> 0f
        }
        if (observed == FlickDirection.Tap) {
            if (component <= 0f || ratio < 0.35f) return 2f
            return (0.5f + abs(ratio - 1f) + (distance - component) / threshold.coerceAtLeast(1f))
                .coerceAtMost(2.5f)
        }
        if (component <= 0f) return 2.5f
        return (0.5f + (max(abs(dx), abs(dy)) - component) / distance.coerceAtLeast(1f))
            .coerceAtMost(2.5f)
    }
}
