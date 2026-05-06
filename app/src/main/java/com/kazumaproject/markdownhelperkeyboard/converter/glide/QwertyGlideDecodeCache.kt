package com.kazumaproject.markdownhelperkeyboard.converter.glide

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyboardProximityInfo
import kotlin.math.roundToInt

class QwertyGlideDecodeCache(
    private val maxEntries: Int = 8
) {
    private val cache = object : LinkedHashMap<Key, List<Candidate>>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, List<Candidate>>): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    fun get(key: Key): List<Candidate>? = cache[key]

    @Synchronized
    fun put(key: Key, candidates: List<Candidate>) {
        cache[key] = candidates
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }

    data class Key(
        val strokeSignature: String,
        val geometrySignature: Int,
        val previousTextSignature: Int
    )

    companion object {
        fun keyOf(
            stroke: NormalizedGlideStroke,
            proximityInfo: QwertyKeyboardProximityInfo,
            previousText: String
        ): Key {
            val strokeSignature = buildString {
                append(stroke.points.size)
                append(':')
                append((stroke.rawLength / proximityInfo.averageKeyWidth.coerceAtLeast(1f) * 8f).roundToInt())
                for (point in stroke.points) {
                    append('|')
                    append((point.x * 64f).roundToInt())
                    append(',')
                    append((point.y * 64f).roundToInt())
                }
            }
            return Key(
                strokeSignature = strokeSignature,
                geometrySignature = proximityInfo.geometrySignature(),
                previousTextSignature = previousText.trim().lowercase().hashCode()
            )
        }
    }
}

internal fun QwertyKeyboardProximityInfo.geometrySignature(): Int {
    var result = keyboardWidth
    result = 31 * result + keyboardHeight
    result = 31 * result + averageKeyWidth.roundToInt()
    result = 31 * result + averageKeyHeight.roundToInt()
    for (key in keys.sortedBy { it.char }) {
        result = 31 * result + key.char.code
        result = 31 * result + key.centerX.roundToInt()
        result = 31 * result + key.centerY.roundToInt()
        result = 31 * result + key.width.roundToInt()
        result = 31 * result + key.height.roundToInt()
    }
    return result
}
