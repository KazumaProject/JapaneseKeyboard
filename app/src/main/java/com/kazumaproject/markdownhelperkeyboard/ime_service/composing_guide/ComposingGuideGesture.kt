package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import kotlin.math.roundToInt

internal enum class GuideHandle { MOVE, LEFT, TOP, RIGHT, BOTTOM }
internal data class GuidePoint(val x: Float, val y: Float)

/** Screen-coordinate gesture reducer. Pointer IDs remain assigned to their original edge. */
internal class ComposingGuideGesture(
    initial: GuideBounds,
    private val area: GuideBounds,
    private val minWidth: Int,
    private val minHeight: Int,
) {
    private data class Pointer(val handle: GuideHandle, var start: GuidePoint, var point: GuidePoint)
    private val original = initial
    private var baseline = initial
    private val pointers = linkedMapOf<Int, Pointer>()
    var bounds = initial
        private set
    val pointerCount get() = pointers.size

    fun add(id: Int, handle: GuideHandle, point: GuidePoint): Boolean {
        if (id in pointers || pointers.size >= 2 || pointers.values.any { it.handle == handle } ||
            (pointers.isNotEmpty() && (handle == GuideHandle.MOVE || pointers.values.any { it.handle == GuideHandle.MOVE }))
        ) return false
        rebase()
        pointers[id] = Pointer(handle, point, point)
        return true
    }

    fun move(points: Map<Int, GuidePoint>): GuideBounds {
        pointers.forEach { (id, pointer) -> points[id]?.let { pointer.point = it } }
        fun dx(handle: GuideHandle) = pointers.values.firstOrNull { it.handle == handle }
            ?.let { (it.point.x - it.start.x).roundToInt() } ?: 0
        fun dy(handle: GuideHandle) = pointers.values.firstOrNull { it.handle == handle }
            ?.let { (it.point.y - it.start.y).roundToInt() } ?: 0
        if (pointers.values.any { it.handle == GuideHandle.MOVE }) {
            bounds = baseline.copy(
                x = (baseline.x + dx(GuideHandle.MOVE)).coerceIn(area.x, area.right - baseline.width),
                y = (baseline.y + dy(GuideHandle.MOVE)).coerceIn(area.y, area.bottom - baseline.height),
            )
        } else {
            val horizontal = constrain(
                baseline.x + dx(GuideHandle.LEFT), baseline.right + dx(GuideHandle.RIGHT),
                area.x, area.right, minWidth,
                pointers.values.any { it.handle == GuideHandle.LEFT },
                pointers.values.any { it.handle == GuideHandle.RIGHT },
            )
            val vertical = constrain(
                baseline.y + dy(GuideHandle.TOP), baseline.bottom + dy(GuideHandle.BOTTOM),
                area.y, area.bottom, minHeight,
                pointers.values.any { it.handle == GuideHandle.TOP },
                pointers.values.any { it.handle == GuideHandle.BOTTOM },
            )
            bounds = GuideBounds(horizontal.first, vertical.first,
                horizontal.second - horizontal.first, vertical.second - vertical.first)
        }
        return bounds
    }

    fun remove(id: Int) { pointers.remove(id); rebase() }
    fun cancel(): GuideBounds = original

    private fun rebase() {
        baseline = bounds
        pointers.values.forEach { it.start = it.point }
    }

    private fun constrain(start: Int, end: Int, low: Int, high: Int, minimum: Int,
                          movingStart: Boolean, movingEnd: Boolean): Pair<Int, Int> {
        var a = start.coerceIn(low, high)
        var b = end.coerceIn(low, high)
        if (b - a < minimum) {
            when {
                movingStart && !movingEnd -> a = b - minimum
                movingEnd && !movingStart -> b = a + minimum
                else -> { a = ((a + b - minimum) / 2).coerceIn(low, high - minimum); b = a + minimum }
            }
        }
        return a to b
    }
}
