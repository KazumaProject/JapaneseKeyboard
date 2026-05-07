package com.kazumaproject.markdownhelperkeyboard.converter.glide

import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyProximity
import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyboardProximityInfo
import kotlin.math.abs
import kotlin.math.hypot

data class QwertyGlidePrefilteredCandidate(
    val entry: QwertyGlideIndexedEntry,
    val cheapCost: Float
)

class QwertyGlideCandidatePrefilter(
    private val options: QwertyGlideDecodeOptions = QwertyGlideDecodeOptions(),
    private val topKSelector: QwertyGlideTopKSelector = QwertyGlideTopKSelector()
) {
    fun prefilter(
        entries: List<QwertyGlideIndexedEntry>,
        stroke: NormalizedGlideStroke,
        pointProbabilities: List<List<PointKeyProbability>>,
        proximityInfo: QwertyKeyboardProximityInfo,
        targetCount: Int = options.fullScoreCandidateLimit
    ): List<QwertyGlidePrefilteredCandidate> {
        if (entries.isEmpty()) return emptyList()
        val keyByChar = proximityInfo.keys.associateBy { it.char }
        val strokeMask = pointProbabilities.strokeCharacterMask()
        val scored = ArrayList<QwertyGlidePrefilteredCandidate>(entries.size)
        for (entry in entries) {
            val cheapCost = cheapScore(
                entry = entry,
                stroke = stroke,
                pointProbabilities = pointProbabilities,
                proximityInfo = proximityInfo,
                keyByChar = keyByChar,
                strokeMask = strokeMask
            ) ?: continue
            scored.add(QwertyGlidePrefilteredCandidate(entry, cheapCost))
        }
        val count = targetCount.coerceAtLeast(options.maxResults)
        return topKSelector.selectPrefiltered(scored, count)
    }

    private fun cheapScore(
        entry: QwertyGlideIndexedEntry,
        stroke: NormalizedGlideStroke,
        pointProbabilities: List<List<PointKeyProbability>>,
        proximityInfo: QwertyKeyboardProximityInfo,
        keyByChar: Map<Char, QwertyKeyProximity>,
        strokeMask: Int
    ): Float? {
        val firstKey = keyByChar[entry.firstChar] ?: return null
        val lastKey = keyByChar[entry.lastChar] ?: return null
        val keyScale = proximityInfo.normalizedKeyScale()
        val startCost = distance(stroke.points.first(), firstKey.normalizedCenter(proximityInfo)) / keyScale
        val endCost = distance(stroke.points.last(), lastKey.normalizedCenter(proximityInfo)) / keyScale
        if (startCost > options.startEndRejectCost || endCost > options.startEndRejectCost) return null

        val missingMask = entry.characterMask and strokeMask.inv()
        val missingCost = missingMask.countOneBits() * 0.42f
        val proximityCost = entry.word.fastProximityCost(pointProbabilities)
        val rawPathLength = entry.word.rawPathLength(keyByChar) ?: return null
        val lengthCost = roughLengthCost(stroke, rawPathLength, proximityInfo, entry.length)
        val directionCost = roughDirectionCost(stroke, keyByChar[entry.word.first()], keyByChar[entry.word.last()])
        val transitionCost = if (entry.transitionMask == 0L) 0.06f else 0f
        return options.startEndWeight * (startCost + endCost) +
                options.proximityWeight * proximityCost +
                options.lengthWeight * lengthCost +
                missingCost +
                directionCost +
                transitionCost
    }

    private fun roughLengthCost(
        stroke: NormalizedGlideStroke,
        rawPathLength: Float,
        proximityInfo: QwertyKeyboardProximityInfo,
        wordLength: Int
    ): Float {
        val keyWidth = proximityInfo.averageKeyWidth.coerceAtLeast(1f)
        val strokeUnits = stroke.rawLength / keyWidth
        val idealUnits = rawPathLength / keyWidth
        val expectedMin = (wordLength - 1).coerceAtLeast(1) * 0.22f
        return abs(strokeUnits - idealUnits) / wordLength.coerceAtLeast(1) +
                (expectedMin - strokeUnits).coerceAtLeast(0f) * 0.65f
    }

    private fun roughDirectionCost(
        stroke: NormalizedGlideStroke,
        first: QwertyKeyProximity?,
        last: QwertyKeyProximity?
    ): Float {
        if (first == null || last == null || stroke.points.size < 2) return 0f
        val sx = stroke.points.last().x - stroke.points.first().x
        val sy = stroke.points.last().y - stroke.points.first().y
        val wx = last.centerX - first.centerX
        val wy = last.centerY - first.centerY
        if (hypot(sx, sy) <= 0.001f || hypot(wx, wy) <= 0.001f) return 0f
        val dot = sx * wx + sy * wy
        return if (dot < 0f) 0.35f else 0f
    }
}

private fun List<List<PointKeyProbability>>.strokeCharacterMask(): Int {
    var mask = 0
    for (probs in this) {
        for (prob in probs) {
            if (prob.char in 'a'..'z') mask = mask or (1 shl (prob.char - 'a'))
        }
    }
    return mask
}

private fun String.fastProximityCost(pointProbabilities: List<List<PointKeyProbability>>): Float {
    var total = 0f
    for (ch in this) {
        var best = 5.0f
        for (probs in pointProbabilities) {
            val cost = probs.firstOrNull { it.char == ch }?.cost ?: continue
            if (cost < best) best = cost
        }
        total += best
    }
    return total / length.coerceAtLeast(1)
}

private fun String.rawPathLength(keyByChar: Map<Char, QwertyKeyProximity>): Float? {
    var length = 0f
    var previous: QwertyKeyProximity? = null
    for (ch in this) {
        val key = keyByChar[ch] ?: return null
        val prev = previous
        if (prev != null) {
            length += hypot(key.centerX - prev.centerX, key.centerY - prev.centerY)
        }
        previous = key
    }
    return length
}

internal fun QwertyKeyboardProximityInfo.normalizedKeyScale(): Float {
    return hypot(
        averageKeyWidth / keyboardWidth.coerceAtLeast(1).toFloat(),
        averageKeyHeight / keyboardHeight.coerceAtLeast(1).toFloat()
    ).coerceAtLeast(0.01f)
}

private fun QwertyKeyProximity.normalizedCenter(
    proximityInfo: QwertyKeyboardProximityInfo
): Pair<Float, Float> {
    return centerX / proximityInfo.keyboardWidth.coerceAtLeast(1).toFloat() to
            centerY / proximityInfo.keyboardHeight.coerceAtLeast(1).toFloat()
}

private fun distance(point: NormalizedGlidePoint, keyPoint: Pair<Float, Float>): Float {
    return hypot(point.x - keyPoint.first, point.y - keyPoint.second)
}
