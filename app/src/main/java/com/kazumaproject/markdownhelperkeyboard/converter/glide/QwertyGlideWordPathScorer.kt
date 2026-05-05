package com.kazumaproject.markdownhelperkeyboard.converter.glide

import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyProximity
import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyboardProximityInfo
import kotlin.math.abs
import kotlin.math.hypot

data class QwertyGlideScoredWord(
    val entry: QwertyGlideDictionaryEntry,
    val totalCost: Float,
    val spatialCost: Float,
    val dictionaryCost: Float
)

class QwertyGlideWordPathScorer(
    private val options: QwertyGlideDecodeOptions = QwertyGlideDecodeOptions()
) {
    fun score(
        entry: QwertyGlideDictionaryEntry,
        stroke: NormalizedGlideStroke,
        pointProbabilities: List<List<PointKeyProbability>>,
        proximityInfo: QwertyKeyboardProximityInfo
    ): QwertyGlideScoredWord? {
        if (stroke.points.size < 2) return null
        val keyByChar = proximityInfo.keys.associateBy { it.char }
        val wordKeys = entry.word.map { keyByChar[it] ?: return null }
        val normalizedPath = wordKeys.toNormalizedPath(proximityInfo)
        val rawPath = wordKeys.map { it.centerX to it.centerY }
        val keyScale = hypot(
            proximityInfo.averageKeyWidth / proximityInfo.keyboardWidth.coerceAtLeast(1).toFloat(),
            proximityInfo.averageKeyHeight / proximityInfo.keyboardHeight.coerceAtLeast(1).toFloat()
        ).coerceAtLeast(0.01f)

        val startCost = distance(stroke.points.first(), normalizedPath.first()) / keyScale
        val endCost = distance(stroke.points.last(), normalizedPath.last()) / keyScale
        if (startCost > 2.6f || endCost > 2.6f) return null

        val pathShapeCost = stroke.points
            .map { point -> distanceToPolyline(point.x, point.y, normalizedPath) / keyScale }
            .average()
            .toFloat()
        val keyPassCost = normalizedPath
            .map { keyPoint -> distanceToStrokePolyline(keyPoint.first, keyPoint.second, stroke.points) / keyScale }
            .average()
            .toFloat()
        val proximityCost = entry.word.map { ch ->
            pointProbabilities.minOfOrNull { probs ->
                probs.firstOrNull { it.char == ch }?.cost ?: 5.0f
            } ?: 5.0f
        }.average().toFloat()
        val lengthCost = normalizedLengthCost(stroke, rawPath, proximityInfo, entry.word.length)
        val repeatedLetterCost = repeatedLetterCost(entry.word)
        val dictionaryCost = entry.wordCost.coerceAtLeast(0) * options.dictionaryWeight
        val spatialCost =
            options.startEndWeight * (startCost + endCost) +
                    options.pathWeight * (pathShapeCost + keyPassCost * 0.72f) +
                    options.proximityWeight * proximityCost +
                    options.lengthWeight * lengthCost +
                    options.repeatedLetterWeight * repeatedLetterCost
        return QwertyGlideScoredWord(
            entry = entry,
            totalCost = spatialCost + dictionaryCost,
            spatialCost = spatialCost,
            dictionaryCost = dictionaryCost
        )
    }

    private fun normalizedLengthCost(
        stroke: NormalizedGlideStroke,
        rawPath: List<Pair<Float, Float>>,
        proximityInfo: QwertyKeyboardProximityInfo,
        wordLength: Int
    ): Float {
        val idealRawLength = rawPath.pathLength()
        val keyWidth = proximityInfo.averageKeyWidth.coerceAtLeast(1f)
        val strokeUnits = stroke.rawLength / keyWidth
        val idealUnits = idealRawLength / keyWidth
        val geometric = abs(strokeUnits - idealUnits) / wordLength.coerceAtLeast(1)
        val expectedMin = (wordLength - 1).coerceAtLeast(1) * 0.22f
        val tooShort = (expectedMin - strokeUnits).coerceAtLeast(0f)
        return geometric + tooShort * 0.65f
    }

    private fun repeatedLetterCost(word: String): Float {
        var cost = 0f
        for (i in 1 until word.length) {
            if (word[i] == word[i - 1]) cost += 0.08f
        }
        return cost
    }
}

private fun List<QwertyKeyProximity>.toNormalizedPath(
    proximityInfo: QwertyKeyboardProximityInfo
): List<Pair<Float, Float>> {
    return map {
        it.centerX / proximityInfo.keyboardWidth.coerceAtLeast(1).toFloat() to
                it.centerY / proximityInfo.keyboardHeight.coerceAtLeast(1).toFloat()
    }
}

private fun List<Pair<Float, Float>>.pathLength(): Float {
    var length = 0f
    for (i in 1 until size) {
        length += hypot(this[i].first - this[i - 1].first, this[i].second - this[i - 1].second)
    }
    return length
}

private fun distance(point: NormalizedGlidePoint, keyPoint: Pair<Float, Float>): Float {
    return hypot(point.x - keyPoint.first, point.y - keyPoint.second)
}

private fun distanceToStrokePolyline(
    x: Float,
    y: Float,
    strokePoints: List<NormalizedGlidePoint>
): Float {
    if (strokePoints.isEmpty()) return Float.MAX_VALUE
    if (strokePoints.size == 1) return hypot(x - strokePoints.first().x, y - strokePoints.first().y)
    var best = Float.MAX_VALUE
    for (i in 1 until strokePoints.size) {
        best = minOf(
            best,
            distanceToSegment(
                x,
                y,
                strokePoints[i - 1].x,
                strokePoints[i - 1].y,
                strokePoints[i].x,
                strokePoints[i].y
            )
        )
    }
    return best
}

private fun distanceToPolyline(
    x: Float,
    y: Float,
    path: List<Pair<Float, Float>>
): Float {
    if (path.isEmpty()) return Float.MAX_VALUE
    if (path.size == 1) return hypot(x - path.first().first, y - path.first().second)
    var best = Float.MAX_VALUE
    for (i in 1 until path.size) {
        best = minOf(
            best,
            distanceToSegment(
                x,
                y,
                path[i - 1].first,
                path[i - 1].second,
                path[i].first,
                path[i].second
            )
        )
    }
    return best
}

private fun distanceToSegment(
    px: Float,
    py: Float,
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float
): Float {
    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared <= 0.000001f) return hypot(px - ax, py - ay)
    val t = (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
    val cx = ax + t * dx
    val cy = ay + t * dy
    return hypot(px - cx, py - cy)
}
