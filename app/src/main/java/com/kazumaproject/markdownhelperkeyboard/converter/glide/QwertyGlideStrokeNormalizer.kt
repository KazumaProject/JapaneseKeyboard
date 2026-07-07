package com.kazumaproject.markdownhelperkeyboard.converter.glide

import com.kazumaproject.qwerty_keyboard.glide.QwertyInputPointerPoint
import com.kazumaproject.qwerty_keyboard.glide.QwertyInputPointers
import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyboardProximityInfo
import kotlin.math.hypot

data class NormalizedGlidePoint(
    val x: Float,
    val y: Float,
    val time: Int
)

data class NormalizedGlideStroke(
    val points: List<NormalizedGlidePoint>,
    val rawLength: Float,
    val normalizedLength: Float
)

class QwertyGlideStrokeNormalizer(
    private val options: QwertyGlideDecodeOptions = QwertyGlideDecodeOptions()
) {
    fun normalize(
        inputPointers: QwertyInputPointers,
        proximityInfo: QwertyKeyboardProximityInfo
    ): NormalizedGlideStroke {
        val raw = inputPointers.points
            .sortedBy { it.time }
            .dedupeClosePoints(
                minDistance = proximityInfo.averageKeyWidth
                    .coerceAtLeast(1f) * options.minSamplingDistanceRatio * 0.35f
            )
        if (raw.isEmpty()) return NormalizedGlideStroke(emptyList(), 0f, 0f)

        val rawLength = raw.rawPointerPathLength()
        val resampled = resample(
            points = raw,
            step = proximityInfo.averageKeyWidth
                .coerceAtLeast(1f) * options.minSamplingDistanceRatio
        )
        val normalizedPoints = resampled.map {
            NormalizedGlidePoint(
                x = it.x / proximityInfo.keyboardWidth.coerceAtLeast(1).toFloat(),
                y = it.y / proximityInfo.keyboardHeight.coerceAtLeast(1).toFloat(),
                time = it.time
            )
        }
        return NormalizedGlideStroke(
            points = normalizedPoints,
            rawLength = rawLength,
            normalizedLength = normalizedPoints.normalizedPathLength()
        )
    }

    private fun resample(
        points: List<QwertyInputPointerPoint>,
        step: Float
    ): List<QwertyInputPointerPoint> {
        if (points.size <= 2 || step <= 0f) return points
        val result = ArrayList<QwertyInputPointerPoint>()
        result.add(points.first())
        var carry = 0f
        var previous = points.first()

        for (i in 1 until points.size) {
            val current = points[i]
            var segmentLength = distance(previous, current)
            if (segmentLength <= 0f) continue
            var start = previous
            while (carry + segmentLength >= step) {
                val remaining = step - carry
                val ratio = (remaining / segmentLength).coerceIn(0f, 1f)
                val x = start.x + (current.x - start.x) * ratio
                val y = start.y + (current.y - start.y) * ratio
                val time = start.time + ((current.time - start.time) * ratio).toInt()
                val sample = QwertyInputPointerPoint(
                    x = x.toInt(),
                    y = y.toInt(),
                    time = time,
                    pointerId = current.pointerId
                )
                result.add(sample)
                start = sample
                segmentLength = distance(start, current)
                carry = 0f
                if (segmentLength <= 0f) break
            }
            carry += segmentLength
            previous = current
        }
        if (result.last() != points.last()) result.add(points.last())
        return result
    }
}

private fun List<QwertyInputPointerPoint>.dedupeClosePoints(
    minDistance: Float
): List<QwertyInputPointerPoint> {
    if (size <= 1) return this
    val result = ArrayList<QwertyInputPointerPoint>()
    result.add(first())
    for (point in drop(1)) {
        if (distance(result.last(), point) >= minDistance) {
            result.add(point)
        }
    }
    if (result.last() != last()) result.add(last())
    return result
}

private fun List<QwertyInputPointerPoint>.rawPointerPathLength(): Float {
    var length = 0f
    for (i in 1 until size) length += distance(this[i - 1], this[i])
    return length
}

private fun List<NormalizedGlidePoint>.normalizedPathLength(): Float {
    var length = 0f
    for (i in 1 until size) {
        length += hypot(this[i].x - this[i - 1].x, this[i].y - this[i - 1].y)
    }
    return length
}

private fun distance(a: QwertyInputPointerPoint, b: QwertyInputPointerPoint): Float {
    return hypot((a.x - b.x).toFloat(), (a.y - b.y).toFloat())
}
