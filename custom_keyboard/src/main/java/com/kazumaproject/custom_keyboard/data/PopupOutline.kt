package com.kazumaproject.custom_keyboard.data

import androidx.annotation.Keep
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/** Coordinates inside the popup's placement bounds: (0,0) is top-left, (1,1) bottom-right. */
@Keep
data class PopupVertex(val x: Float, val y: Float)

/** Null on a region means the legacy rounded rectangle; zero vertices means a physical circle. */
@Keep
data class PopupOutline(val vertices: List<PopupVertex> = emptyList()) {
    val isCircle get() = vertices.isEmpty()

    fun isValid(): Boolean {
        if (isCircle) return true
        if (vertices.size !in 3..MAX_VERTICES) return false
        if (vertices.any { !it.x.isFinite() || !it.y.isFinite() || it.x !in 0f..1f || it.y !in 0f..1f }) return false
        val edges = vertices.indices.map { vertices[it] to vertices[(it + 1) % vertices.size] }
        if (edges.any { (a, b) -> hypot(a.x - b.x, a.y - b.y) < EPSILON }) return false
        val area = edges.sumOf { (a, b) -> (a.x * b.y - b.x * a.y).toDouble() }
        if (abs(area) < EPSILON) return false
        for (i in edges.indices) for (j in i + 1 until edges.size) {
            if (j == i + 1 || (i == 0 && j == edges.lastIndex)) continue
            if (segmentsIntersect(edges[i].first, edges[i].second, edges[j].first, edges[j].second)) return false
        }
        // Adjacent edges may be collinear after inserting a point, but must not double back.
        return vertices.indices.none { i ->
            val a = vertices[(i + vertices.size - 1) % vertices.size]
            val b = vertices[i]
            val c = vertices[(i + 1) % vertices.size]
            abs(cross(a, b, c)) < EPSILON && (a.x - b.x) * (c.x - b.x) + (a.y - b.y) * (c.y - b.y) > 0f
        }
    }

    companion object {
        const val MAX_VERTICES = 16
        val RECTANGLE = listOf(PopupVertex(0f, 0f), PopupVertex(1f, 0f), PopupVertex(1f, 1f), PopupVertex(0f, 1f))
        val TRIANGLE = listOf(PopupVertex(.5f, 0f), PopupVertex(1f, 1f), PopupVertex(0f, 1f))
        private const val EPSILON = .00001f

        private fun cross(a: PopupVertex, b: PopupVertex, c: PopupVertex) =
            (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

        private fun segmentsIntersect(a: PopupVertex, b: PopupVertex, c: PopupVertex, d: PopupVertex): Boolean {
            fun onSegment(p: PopupVertex, q: PopupVertex, r: PopupVertex) = abs(cross(p, q, r)) < EPSILON &&
                r.x >= minOf(p.x, q.x) - EPSILON && r.x <= maxOf(p.x, q.x) + EPSILON &&
                r.y >= minOf(p.y, q.y) - EPSILON && r.y <= maxOf(p.y, q.y) + EPSILON
            val abC = cross(a, b, c); val abD = cross(a, b, d)
            val cdA = cross(c, d, a); val cdB = cross(c, d, b)
            return (abC * abD < 0 && cdA * cdB < 0) || onSegment(a, b, c) || onSegment(a, b, d) ||
                onSegment(c, d, a) || onSegment(c, d, b)
        }
    }
}

private fun PopupRegion.physicalVertices(keyWidth: Float, keyHeight: Float): List<PopupVertex> =
    outline!!.vertices.map { PopupVertex((bounds.left + it.x * bounds.width) * keyWidth, (bounds.top + it.y * bounds.height) * keyHeight) }

private fun segmentDistance(x: Float, y: Float, a: PopupVertex, b: PopupVertex): Float {
    val dx = b.x - a.x; val dy = b.y - a.y
    val t = (((x - a.x) * dx + (y - a.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
    return hypot(x - a.x - t * dx, y - a.y - t * dy)
}

private fun polygonContains(x: Float, y: Float, vertices: List<PopupVertex>): Boolean {
    var inside = false
    vertices.indices.forEach { i ->
        val a = vertices[i]; val b = vertices[(i + 1) % vertices.size]
        if (segmentDistance(x, y, a, b) < .00001f) return true
        if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
    }
    return inside
}

fun PopupRegion.contains(x: Float, y: Float, keyWidth: Float = 1f, keyHeight: Float = 1f): Boolean {
    if (!x.isFinite() || !y.isFinite()) return false
    val shape = outline ?: return bounds.contains(x, y, keyWidth, keyHeight)
    if (shape.isCircle) {
        val radius = min(bounds.width * keyWidth, bounds.height * keyHeight) / 2
        return hypot((x - (bounds.left + bounds.right) / 2) * keyWidth, (y - (bounds.top + bounds.bottom) / 2) * keyHeight) <= radius
    }
    return polygonContains(x * keyWidth, y * keyHeight, physicalVertices(keyWidth, keyHeight))
}

/** The same contour as the renderer, measured in physical pixels, including concave polygons. */
fun PopupRegion.distanceTo(x: Float, y: Float, keyWidth: Float, keyHeight: Float): Float {
    val shape = outline ?: return bounds.distanceTo(x, y, keyWidth, keyHeight)
    if (shape.isCircle) {
        val radius = min(bounds.width * keyWidth, bounds.height * keyHeight) / 2
        return (hypot((x - (bounds.left + bounds.right) / 2) * keyWidth, (y - (bounds.top + bounds.bottom) / 2) * keyHeight) - radius).coerceAtLeast(0f)
    }
    val vertices = physicalVertices(keyWidth, keyHeight)
    if (polygonContains(x * keyWidth, y * keyHeight, vertices)) return 0f
    return vertices.indices.minOf { segmentDistance(x * keyWidth, y * keyHeight, vertices[it], vertices[(it + 1) % vertices.size]) }
}

/** A label box inside the contour; concave shapes need not contain the placement-box centre. */
fun PopupRegion.labelBounds(keyWidth: Float, keyHeight: Float): PopupBounds {
    val shape = outline ?: return bounds.let { PopupBounds(it.left + it.width * .08f, it.top + it.height * .08f, it.right - it.width * .08f, it.bottom - it.height * .08f) }
    val cx = (bounds.left + bounds.right) / 2; val cy = (bounds.top + bounds.bottom) / 2
    if (shape.isCircle) {
        val half = min(bounds.width * keyWidth, bounds.height * keyHeight) / (2 * sqrt(2f))
        return PopupBounds(cx - half / keyWidth, cy - half / keyHeight, cx + half / keyWidth, cy + half / keyHeight)
    }
    val vertices = physicalVertices(keyWidth, keyHeight)
    var best = PopupVertex(cx * keyWidth, cy * keyHeight)
    var clearance = -1f
    for (row in 0..16) for (column in 0..16) {
        val p = PopupVertex((bounds.left + bounds.width * column / 16) * keyWidth, (bounds.top + bounds.height * row / 16) * keyHeight)
        if (!polygonContains(p.x, p.y, vertices)) continue
        val distance = vertices.indices.minOf { segmentDistance(p.x, p.y, vertices[it], vertices[(it + 1) % vertices.size]) }
        if (distance > clearance) { best = p; clearance = distance }
    }
    if (clearance <= 0f) best = vertices.first()
    val half = clearance.coerceAtLeast(.001f) / sqrt(2f)
    return PopupBounds((best.x - half) / keyWidth, (best.y - half) / keyHeight, (best.x + half) / keyWidth, (best.y + half) / keyHeight)
}
