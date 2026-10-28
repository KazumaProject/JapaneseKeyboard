package com.kazumaproject.gesture_keyboard

import kotlin.math.hypot
import kotlin.math.max

/**
 * Ordered geometric template matching: arc-length resampling, uniform scale and translation.
 * Inspired by the published $1 approach (Wobbrock, Wilson, Li, UIST 2007).
 * This implementation is independently written; it intentionally does not rotate, mirror,
 * reverse, or independently scale the axes of the path.
 */
object GestureRecognizer {
    private const val POINTS = 64
    const val DEFAULT_MAX_DISTANCE = 0.14f
    const val DEFAULT_MIN_MARGIN = 0.025f
    data class Match(val entry: GestureEntry, val distance: Float)
    class Prepared(set: GestureSet) {
        private val samples = set.entries.flatMap { entry ->
            entry.samples.mapNotNull { normalize(it)?.let { normalized -> entry to normalized } }
        }
        fun ranked(points: List<GesturePoint>): List<Match> {
            val normalized = normalize(points) ?: return emptyList()
            return samples.map { (entry, sample) -> Match(entry, distance(normalized, sample)) }
                .groupBy { it.entry.output }.values.map { matches -> matches.minBy { it.distance } }
                .sortedBy { it.distance }
        }
        fun recognize(points: List<GesturePoint>, maxDistance: Float = DEFAULT_MAX_DISTANCE): Match? {
            val ranked = ranked(points)
            val best = ranked.firstOrNull() ?: return null
            if (best.distance > maxDistance) return null
            if (ranked.size > 1 && ranked[1].distance - best.distance < DEFAULT_MIN_MARGIN) return null
            return best
        }
    }
    fun normalize(input: List<GesturePoint>): List<GesturePoint>? {
        if (input.size < 2 || input.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        val cumulative = FloatArray(input.size)
        for (i in 1 until input.size) cumulative[i] = cumulative[i - 1] + length(input[i - 1], input[i])
        val total = cumulative.last()
        if (!total.isFinite() || total <= 0.00001f) return null
        var segment = 1
        val points = (0 until POINTS).map { i ->
            val target = total * i / (POINTS - 1)
            while (segment < input.lastIndex && cumulative[segment] < target) segment++
            val span = cumulative[segment] - cumulative[segment - 1]
            val t = if (span > 0f) ((target - cumulative[segment - 1]) / span).coerceIn(0f, 1f) else 0f
            val a = input[segment - 1]; val b = input[segment]
            GesturePoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }
        val scale = max(points.maxOf { it.x } - points.minOf { it.x }, points.maxOf { it.y } - points.minOf { it.y })
        if (scale <= 0.00001f) return null
        val cx = points.sumOf { it.x.toDouble() }.toFloat() / POINTS
        val cy = points.sumOf { it.y.toDouble() }.toFloat() / POINTS
        return points.map { GesturePoint((it.x - cx) / scale, (it.y - cy) / scale) }
    }
    fun distance(a: List<GesturePoint>, b: List<GesturePoint>): Float =
        a.indices.sumOf { length(a[it], b[it]).toDouble() }.toFloat() / a.size
    private fun length(a: GesturePoint, b: GesturePoint) = hypot(a.x - b.x, a.y - b.y)
}
