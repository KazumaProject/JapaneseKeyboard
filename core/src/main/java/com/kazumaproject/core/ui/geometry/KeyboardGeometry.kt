package com.kazumaproject.core.ui.geometry

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class KeyboardCurveHand { LEFT, RIGHT }
enum class KeyboardCurveDirection { DOWN, UP }

/** Presentation settings for one keyboard surface; the unmodified layout is the default. */
data class KeyboardGeometry(
    val splitPercent: Int = 0,
    val curveDegrees: Int = 0,
    val hand: KeyboardCurveHand = KeyboardCurveHand.RIGHT,
    val autoHeight: Boolean = true,
    val curveDirection: KeyboardCurveDirection = KeyboardCurveDirection.DOWN,
) {
    fun normalized() = copy(
        splitPercent = splitPercent.coerceIn(0, MAX_SPLIT_PERCENT),
        curveDegrees = curveDegrees.coerceIn(0, MAX_CURVE_DEGREES),
    )

    val isDefault: Boolean get() = splitPercent <= 0 && curveDegrees <= 0

    companion object {
        const val MAX_SPLIT_PERCENT = 40
        const val MAX_CURVE_DEGREES = 25
    }
}

data class KeyboardCurvePoint(val offsetY: Float, val rotationDegrees: Float)

/** Geometry only. This code is used by IME views, not by the settings preview. */
object KeyboardGeometryMath {
    /**
     * A circular arc whose tangent is horizontal at the outside edge and reaches [degrees]
     * at the inside edge. [distance] and [span] are measured from that outside edge;
     * [direction] reflects both the offset and the tangent without changing their magnitude.
     */
    fun curve(
        distance: Float,
        span: Float,
        degrees: Float,
        mirror: Boolean,
        direction: KeyboardCurveDirection = KeyboardCurveDirection.DOWN,
    ): KeyboardCurvePoint {
        if (span <= 0f || degrees <= 0f || !span.isFinite() || !distance.isFinite()) {
            return KeyboardCurvePoint(0f, 0f)
        }
        val angle = Math.toRadians(degrees.coerceIn(0f, KeyboardGeometry.MAX_CURVE_DEGREES.toFloat()).toDouble())
        val radius = span / sin(angle).toFloat()
        val d = distance.coerceIn(0f, span)
        val offset = radius - sqrt(max(0f, radius * radius - d * d))
        val tangent = Math.toDegrees(asin((d / radius).coerceIn(-1f, 1f)).toDouble()).toFloat()
        val sign = if (direction == KeyboardCurveDirection.UP) -1f else 1f
        return KeyboardCurvePoint(offset * sign, (if (mirror) -tangent else tangent) * sign)
    }

    /** Minimum horizontal room for ten QWERTY letters; never alters the stored preference. */
    fun effectiveSplitGap(width: Float, percent: Int, minimumLetterWidth: Float): Float {
        if (!width.isFinite() || width <= 0f) return 0f
        val requested = width * percent.coerceIn(0, KeyboardGeometry.MAX_SPLIT_PERCENT) / 100f
        return min(requested, max(0f, width - 10f * max(0f, minimumLetterWidth)))
    }

    /** Rotate a screen-space movement into the pressed key's coordinate system. */
    fun localDelta(dx: Float, dy: Float, rotationDegrees: Float): Pair<Float, Float> {
        if (rotationDegrees == 0f) return dx to dy
        val angle = Math.toRadians(rotationDegrees.toDouble())
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        return (dx * c + dy * s) to (-dx * s + dy * c)
    }
}
