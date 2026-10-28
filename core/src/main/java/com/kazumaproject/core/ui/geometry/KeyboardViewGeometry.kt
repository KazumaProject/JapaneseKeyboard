package com.kazumaproject.core.ui.geometry

import android.graphics.Matrix
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class KeyboardGeometryGroup { LEFT, RIGHT, SINGLE }

/** Bounds are in the keyboard view's untransformed coordinate system. */
data class KeyboardGeometryItem(
    val view: View,
    val group: KeyboardGeometryGroup,
    val bounds: RectF = RectF(view.left.toFloat(), view.top.toFloat(), view.right.toFloat(), view.bottom.toFloat()),
)

data class KeyboardGeometryResult(val splitGapPx: Float, val requiredHeightPx: Float)

/** Applies presentation-only transforms to existing views after their normal layout. */
object KeyboardViewGeometry {
    private data class Placed(
        val item: KeyboardGeometryItem,
        var x: Float,
        var y: Float,
        var scaleX: Float,
        var scaleY: Float,
        val angle: Float,
    ) {
        val cosAngle = cos(Math.toRadians(angle.toDouble())).toFloat()
        val sinAngle = sin(Math.toRadians(angle.toDouble())).toFloat()
        fun halfWidth(): Float {
            return (abs(cosAngle) * item.view.width * scaleX + abs(sinAngle) * item.view.height * scaleY) / 2f
        }
        fun halfHeight(): Float {
            return (abs(sinAngle) * item.view.width * scaleX + abs(cosAngle) * item.view.height * scaleY) / 2f
        }
    }

    fun reset(items: Iterable<KeyboardGeometryItem>) {
        items.forEach { item ->
            item.view.apply { rotation = 0f; translationX = 0f; translationY = 0f; scaleX = 1f; scaleY = 1f }
        }
    }

    fun apply(
        owner: ViewGroup,
        items: List<KeyboardGeometryItem>,
        geometry: KeyboardGeometry,
        qwerty: Boolean,
        baselineHeight: Float,
    ): KeyboardGeometryResult {
        val visible = items.filter { it.view.visibility == View.VISIBLE && it.view.width > 0 && it.view.height > 0 }
        val width = owner.width.toFloat()
        val height = owner.height.toFloat()
        if (visible.isEmpty() || width <= 0f || height <= 0f) return KeyboardGeometryResult(0f, baselineHeight)
        val safe = geometry.normalized()
        if (safe.isDefault) {
            reset(items)
            return KeyboardGeometryResult(0f, baselineHeight)
        }

        val gap = if (qwerty) KeyboardGeometryMath.effectiveSplitGap(width, safe.splitPercent,
            24f * owner.resources.displayMetrics.density) else 0f
        val sideWidth = (width - gap) / 2f
        val groupBounds = visible.groupBy { it.group }.mapValues { (_, group) ->
            group.minOf { it.bounds.left } to group.maxOf { it.bounds.right }
        }
        val placed = visible.map { item ->
            val bounds = item.bounds
            var x = bounds.centerX()
            var xScale = bounds.width() / item.view.width
            val yScale = bounds.height() / item.view.height
            if (qwerty && gap > 0f && item.group != KeyboardGeometryGroup.SINGLE) {
                val (left, right) = groupBounds.getValue(item.group)
                val fit = min(1f, sideWidth / (right - left).coerceAtLeast(1f))
                val start = if (item.group == KeyboardGeometryGroup.LEFT) 0f else width - sideWidth
                x = start + (x - left) * fit
                xScale *= fit
            }
            val mirror = when (item.group) {
                KeyboardGeometryGroup.LEFT -> false
                KeyboardGeometryGroup.RIGHT -> true
                KeyboardGeometryGroup.SINGLE -> safe.hand == KeyboardCurveHand.RIGHT
            }
            val span = if (qwerty) sideWidth else width
            val distance = if (mirror) width - x else x
            val curve = if (qwerty && item.group == KeyboardGeometryGroup.SINGLE) {
                // A single, unsplit space remains horizontal while following the middle of
                // the two arcs; this also preserves the curved keyboard's true vertical extent.
                KeyboardGeometryMath.curve(span, span, safe.curveDegrees.toFloat(), false).copy(rotationDegrees = 0f)
            } else KeyboardGeometryMath.curve(distance, span, safe.curveDegrees.toFloat(), mirror)
            Placed(item, x, bounds.centerY() + curve.offsetY, xScale, yScale, curve.rotationDegrees)
        }

        // Rotated corners must not cross the reserved center gap or the keyboard edges.
        if (qwerty && gap > 0f) {
            for (group in listOf(KeyboardGeometryGroup.LEFT, KeyboardGeometryGroup.RIGHT)) {
                val part = placed.filter { it.item.group == group }
                if (part.isEmpty()) continue
                val minX = part.minOf { it.x - it.halfWidth() }
                val maxX = part.maxOf { it.x + it.halfWidth() }
                val fit = min(1f, sideWidth / (maxX - minX).coerceAtLeast(1f))
                val edge = if (group == KeyboardGeometryGroup.LEFT) 0f else width - sideWidth
                part.forEach { p -> p.x = edge + (p.x - minX) * fit; p.scaleX *= fit; p.scaleY *= fit }
            }
        } else {
            // Rotation can push the outer corners outside the keyboard even when the
            // original cells fit. Fit the whole surface, keeping the relative positions.
            val minX = placed.minOf { it.x - it.halfWidth() }
            val maxX = placed.maxOf { it.x + it.halfWidth() }
            if (minX < 0f || maxX > width) {
                val fit = min(1f, width / (maxX - minX).coerceAtLeast(1f))
                val fittedWidth = (maxX - minX) * fit
                val start = ((width - fittedWidth) / 2f).coerceAtLeast(0f)
                placed.forEach { p ->
                    p.x = start + (p.x - minX) * fit
                    p.scaleX *= fit
                    p.scaleY *= fit
                }
            }
        }

        // A rotation can make adjacent keys overlap even though their original cells do not.
        // Shrink the key surfaces around their centers only as much as needed to separate them.
        fun overlaps(factor: Float): Boolean {
            for (i in placed.indices) for (j in i + 1 until placed.size) {
                val a = placed[i]; val b = placed[j]
                if (RectF.intersects(a.item.bounds, b.item.bounds)) continue
                if (intersects(a, b, factor)) return true
            }
            return false
        }
        if (overlaps(1f)) {
            var low = 0f
            var high = 1f
            repeat(12) {
                val middle = (low + high) / 2f
                if (overlaps(middle)) high = middle else low = middle
            }
            placed.forEach { it.scaleX *= low; it.scaleY *= low }
        }

        val originalTop = visible.minOf { it.bounds.top }.coerceAtLeast(0f)
        val originalBottomPadding = (baselineHeight - visible.maxOf { it.bounds.bottom }).coerceAtLeast(0f)
        val minY = placed.minOf { it.y - it.halfHeight() }
        val maxY = placed.maxOf { it.y + it.halfHeight() }
        val desiredHeight = maxY - minY + originalTop + originalBottomPadding
        val available = (height - originalTop - originalBottomPadding).coerceAtLeast(1f)
        val fitY = min(1f, available / (maxY - minY).coerceAtLeast(1f))
        val bottom = height - originalBottomPadding
        placed.forEach { p ->
            val targetY = bottom - (maxY - p.y) * fitY
            p.scaleX *= fitY
            p.scaleY *= fitY
            val view = p.item.view
            view.pivotX = view.width / 2f
            view.pivotY = view.height / 2f
            view.scaleX = p.scaleX
            view.scaleY = p.scaleY
            view.rotation = p.angle
            view.translationX = p.x - (view.left + view.width / 2f)
            view.translationY = targetY - (view.top + view.height / 2f)
        }
        return KeyboardGeometryResult(gap, desiredHeight)
    }

    private fun intersects(a: Placed, b: Placed, factor: Float): Boolean {
        val dx = b.x - a.x
        val dy = b.y - a.y
        if (abs(dx) >= (a.halfWidth() + b.halfWidth()) * factor ||
            abs(dy) >= (a.halfHeight() + b.halfHeight()) * factor) return false
        val aw = a.item.view.width * a.scaleX * factor / 2f
        val ah = a.item.view.height * a.scaleY * factor / 2f
        val bw = b.item.view.width * b.scaleX * factor / 2f
        val bh = b.item.view.height * b.scaleY * factor / 2f
        val cosine = abs(a.cosAngle * b.cosAngle + a.sinAngle * b.sinAngle)
        val sine = abs(a.cosAngle * b.sinAngle - a.sinAngle * b.cosAngle)
        // Separating-axis test using the four edge normals. Avoid allocating arrays
        // and lists for every key pair during a layout traversal.
        return abs(dx * a.cosAngle + dy * a.sinAngle) < aw + bw * cosine + bh * sine &&
            abs(-dx * a.sinAngle + dy * a.cosAngle) < ah + bw * sine + bh * cosine &&
            abs(dx * b.cosAngle + dy * b.sinAngle) < bw + aw * cosine + ah * sine &&
            abs(-dx * b.sinAngle + dy * b.cosAngle) < bh + aw * sine + ah * cosine
    }

    /** Exact hit against the visible, rotated key rather than its axis-aligned bounding box. */
    fun containsParentPoint(view: View, x: Float, y: Float): Boolean {
        val point = parentToLocal(view, x, y) ?: return false
        return point.first >= 0f && point.first < view.width && point.second >= 0f && point.second < view.height
    }

    fun parentToLocal(view: View, x: Float, y: Float): Pair<Float, Float>? {
        val inverse = Matrix()
        if (!view.matrix.invert(inverse)) return null
        val point = floatArrayOf(x - view.left, y - view.top)
        inverse.mapPoints(point)
        return point[0] to point[1]
    }

    fun centerInParent(view: View): Pair<Float, Float> {
        val point = floatArrayOf(view.width / 2f, view.height / 2f)
        view.matrix.mapPoints(point)
        return (view.left + point[0]) to (view.top + point[1])
    }
}
