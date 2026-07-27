package com.kazumaproject.markdownhelperkeyboard.gemma.handwriting

/**
 * Splits clearly separated horizontal ink groups into character-sized segments.
 *
 * This is intentionally conservative: Gemma receives the complete image first, and these
 * segments are only used as a fallback when that result contains fewer characters than the
 * number of visible groups.
 */
object HandwritingStrokeSegmenter {
    private const val MAX_HORIZONTAL_GAP_WITHIN_SEGMENT = 0.08f
    private const val MAX_SEGMENTS = 12

    fun segment(strokes: List<HandwritingStroke>): List<List<HandwritingStroke>> {
        if (strokes.isEmpty()) return emptyList()

        val sorted = strokes
            .filter { stroke -> stroke.points.isNotEmpty() }
            .map { stroke ->
                StrokeBounds(
                    stroke = stroke,
                    minX = stroke.points.minOf(HandwritingPoint::x),
                    maxX = stroke.points.maxOf(HandwritingPoint::x),
                )
            }
            .sortedBy(StrokeBounds::minX)
        if (sorted.isEmpty()) return emptyList()

        val groups = mutableListOf<MutableList<StrokeBounds>>()
        sorted.forEach { bounds ->
            val matchingGroups = groups.filter { group ->
                val groupMinX = group.minOf(StrokeBounds::minX)
                val groupMaxX = group.maxOf(StrokeBounds::maxX)
                bounds.minX <= groupMaxX + MAX_HORIZONTAL_GAP_WITHIN_SEGMENT &&
                    bounds.maxX >= groupMinX - MAX_HORIZONTAL_GAP_WITHIN_SEGMENT
            }
            if (matchingGroups.isEmpty()) {
                groups += mutableListOf(bounds)
            } else {
                val target = matchingGroups.first()
                target += bounds
                matchingGroups.drop(1).forEach { extra ->
                    target += extra
                    groups.remove(extra)
                }
            }
        }

        return groups
            .sortedBy { group -> group.minOf(StrokeBounds::minX) }
            .takeIf { result -> result.size <= MAX_SEGMENTS }
            ?.map { group -> group.map(StrokeBounds::stroke) }
            ?: listOf(strokes)
    }

    private data class StrokeBounds(
        val stroke: HandwritingStroke,
        val minX: Float,
        val maxX: Float,
    )
}
