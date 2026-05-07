package com.kazumaproject.markdownhelperkeyboard.converter.glide

import com.kazumaproject.qwerty_keyboard.glide.QwertyInputPointerPoint
import com.kazumaproject.qwerty_keyboard.glide.QwertyInputPointers
import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyProximity
import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyboardProximityInfo
import kotlin.math.roundToInt

internal object QwertyGlideTestFixtures {
    val proximityInfo: QwertyKeyboardProximityInfo by lazy {
        val rows = listOf(
            "qwertyuiop" to 0f,
            "asdfghjkl" to 50f,
            "zxcvbnm" to 150f
        )
        val keys = rows.flatMapIndexed { rowIndex, (letters, offset) ->
            letters.mapIndexed { columnIndex, ch ->
                QwertyKeyProximity(
                    char = ch,
                    centerX = offset + 50f + columnIndex * 100f,
                    centerY = 50f + rowIndex * 100f,
                    width = 92f,
                    height = 92f,
                    rowIndex = rowIndex,
                    columnIndex = columnIndex,
                    neighborChars = emptyList()
                )
            }
        }
        QwertyKeyboardProximityInfo(
            keys = keys,
            keyboardWidth = 1000,
            keyboardHeight = 300,
            averageKeyWidth = 100f,
            averageKeyHeight = 100f
        )
    }

    fun strokeFor(
        word: String,
        startOffsetX: Float = 0f,
        startOffsetY: Float = 0f,
        endOffsetX: Float = 0f,
        endOffsetY: Float = 0f
    ): QwertyInputPointers {
        val keyByChar = proximityInfo.keys.associateBy { it.char }
        val centers = word.mapIndexed { index, ch ->
            val key = keyByChar[ch] ?: error("Missing test key: $ch")
            val x = key.centerX +
                    if (index == 0) startOffsetX else if (index == word.lastIndex) endOffsetX else 0f
            val y = key.centerY +
                    if (index == 0) startOffsetY else if (index == word.lastIndex) endOffsetY else 0f
            x to y
        }
        val points = ArrayList<QwertyInputPointerPoint>()
        var time = 0
        points.add(centers.first().toPoint(time))
        for (i in 1 until centers.size) {
            val previous = centers[i - 1]
            val current = centers[i]
            for (step in 1..3) {
                val ratio = step / 3f
                val x = previous.first + (current.first - previous.first) * ratio
                val y = previous.second + (current.second - previous.second) * ratio
                time += 12
                points.add((x to y).toPoint(time))
            }
        }
        return QwertyInputPointers(points)
    }

    fun dictionary(extraNoiseCount: Int = 0): List<QwertyGlideDictionaryEntry> {
        val base = listOf(
            "hello", "hell", "help", "jello", "hero",
            "world", "word", "would", "wild",
            "test", "tent", "text", "toast",
            "keyboard", "keypad", "keyboards",
            "glide", "guide", "glove",
            "good", "food", "goof", "gold",
            "time", "tide", "tone",
            "home", "hone", "hope",
            "something", "smoothing", "soothing"
        ).mapIndexed { index, word ->
            QwertyGlideDictionaryEntry(word, 5000 + index)
        }
        if (extraNoiseCount <= 0) return base
        return base + syntheticNoise(extraNoiseCount)
    }

    fun sameBucketNoise(
        count: Int,
        first: Char,
        last: Char
    ): List<QwertyGlideDictionaryEntry> {
        return (0 until count).map { index ->
            val middle = index.toBase26Word(minLength = 4 + (index % 6))
            QwertyGlideDictionaryEntry("$first$middle$last", 9000 + index)
        }
    }

    private fun syntheticNoise(count: Int): List<QwertyGlideDictionaryEntry> {
        val alphabet = "abcdefghijklmnopqrstuvwxyz"
        return (0 until count).map { index ->
            val length = 2 + (index % 12)
            val word = buildString {
                repeat(length) { pos ->
                    append(alphabet[(index * 13 + pos * 7 + pos * pos) % alphabet.length])
                }
            }
            QwertyGlideDictionaryEntry(word, 12000 + index)
        }
    }

    private fun Pair<Float, Float>.toPoint(time: Int): QwertyInputPointerPoint {
        return QwertyInputPointerPoint(
            x = first.roundToInt(),
            y = second.roundToInt(),
            time = time,
            pointerId = 0
        )
    }

    private fun Int.toBase26Word(minLength: Int): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz"
        var value = this
        val chars = StringBuilder()
        do {
            chars.append(alphabet[value % alphabet.length])
            value /= alphabet.length
        } while (value > 0)
        while (chars.length < minLength) {
            chars.append(alphabet[(this + chars.length * 5) % alphabet.length])
        }
        return chars.toString()
    }
}
