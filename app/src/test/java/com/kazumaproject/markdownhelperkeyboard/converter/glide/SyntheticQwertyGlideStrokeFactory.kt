package com.kazumaproject.markdownhelperkeyboard.converter.glide

import com.kazumaproject.qwerty_keyboard.glide.QwertyInputPointerPoint
import com.kazumaproject.qwerty_keyboard.glide.QwertyInputPointers
import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyProximity
import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyboardProximityInfo
import kotlin.math.hypot
import kotlin.random.Random

object FixedQwertyGeometryFactory {
    fun create(): QwertyKeyboardProximityInfo {
        val rows = listOf(
            "qwertyuiop" to 0f,
            "asdfghjkl" to 0.5f,
            "zxcvbnm" to 1.5f
        )
        val keyWidth = 100f
        val keyHeight = 80f
        val rowHeight = 100f
        val keys = rows.flatMapIndexed { rowIndex, (letters, offset) ->
            letters.mapIndexed { columnIndex, ch ->
                QwertyKeyProximity(
                    char = ch,
                    centerX = (offset + columnIndex + 0.5f) * keyWidth,
                    centerY = (rowIndex + 0.5f) * rowHeight,
                    width = keyWidth,
                    height = keyHeight,
                    rowIndex = rowIndex,
                    columnIndex = columnIndex,
                    neighborChars = emptyList()
                )
            }
        }
        val neighborRadius = hypot(keyWidth, keyHeight) * 1.35f
        val withNeighbors = keys.map { key ->
            key.copy(
                neighborChars = keys
                    .filter { it.char != key.char }
                    .map { it.char to hypot(key.centerX - it.centerX, key.centerY - it.centerY) }
                    .filter { it.second <= neighborRadius }
                    .sortedBy { it.second }
                    .map { it.first }
                    .take(8)
            )
        }
        return QwertyKeyboardProximityInfo(
            keys = withNeighbors,
            keyboardWidth = 1000,
            keyboardHeight = 300,
            averageKeyWidth = keyWidth,
            averageKeyHeight = keyHeight
        )
    }
}

class SyntheticQwertyGlideStrokeFactory(
    private val proximityInfo: QwertyKeyboardProximityInfo,
    private val random: Random = Random(7)
) {
    private val keyByChar = proximityInfo.keys.associateBy { it.char }

    fun ideal(word: String): QwertyInputPointers {
        return fromPath(word.map { center(it) }, stepsPerSegment = 6)
    }

    fun noisy(word: String): QwertyInputPointers {
        val noiseX = proximityInfo.averageKeyWidth * 0.18f
        val noiseY = proximityInfo.averageKeyHeight * 0.18f
        val points = ideal(word).points.mapIndexed { index, point ->
            if (index == 0 || index == ideal(word).points.lastIndex) {
                point
            } else {
                point.copy(
                    x = (point.x + random.nextDouble(-noiseX.toDouble(), noiseX.toDouble())).toInt(),
                    y = (point.y + random.nextDouble(-noiseY.toDouble(), noiseY.toDouble())).toInt()
                )
            }
        }
        return QwertyInputPointers(points)
    }

    fun fastSparse(word: String): QwertyInputPointers {
        val centers = word.map { center(it) }
        val sparse = centers.filterIndexed { index, _ ->
            index == 0 || index == centers.lastIndex || index % 2 == 0
        }
        return fromPath(sparse, stepsPerSegment = 1)
    }

    fun overshoot(word: String): QwertyInputPointers {
        val centers = word.map { center(it) }.toMutableList()
        if (centers.size >= 2) {
            val first = centers[0]
            val second = centers[1]
            centers[0] = first.first - (second.first - first.first) * 0.18f to
                    first.second - (second.second - first.second) * 0.18f
            val lastIndex = centers.lastIndex
            val previous = centers[lastIndex - 1]
            val last = centers[lastIndex]
            centers[lastIndex] = last.first + (last.first - previous.first) * 0.18f to
                    last.second + (last.second - previous.second) * 0.18f
        }
        return fromPath(centers, stepsPerSegment = 6)
    }

    fun repeatedLetter(word: String): QwertyInputPointers {
        return noisy(word)
    }

    private fun center(ch: Char): Pair<Float, Float> {
        val key = keyByChar[ch.lowercaseChar()] ?: error("Missing key $ch")
        return key.centerX to key.centerY
    }

    private fun fromPath(
        centers: List<Pair<Float, Float>>,
        stepsPerSegment: Int
    ): QwertyInputPointers {
        val result = ArrayList<QwertyInputPointerPoint>()
        var time = 0
        result.add(QwertyInputPointerPoint(centers.first().first.toInt(), centers.first().second.toInt(), time, 0))
        for (i in 1 until centers.size) {
            val a = centers[i - 1]
            val b = centers[i]
            for (step in 1..stepsPerSegment) {
                val ratio = step / stepsPerSegment.toFloat()
                time += 12
                result.add(
                    QwertyInputPointerPoint(
                        x = (a.first + (b.first - a.first) * ratio).toInt(),
                        y = (a.second + (b.second - a.second) * ratio).toInt(),
                        time = time,
                        pointerId = 0
                    )
                )
            }
        }
        return QwertyInputPointers(result)
    }
}
