package com.kazumaproject.markdownhelperkeyboard.converter

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt

/** Character n-gram weights learned with signed feature hashing. */
internal class ExperimentalBunsetsuBoundaryModel private constructor(private val weights: FloatArray) {
    fun costs(reading: String, scale: Int): IntArray {
        val padded = "^$reading\$"
        return IntArray(reading.length + 1) { gap ->
            if (gap == 0 || gap == reading.length) return@IntArray 0
            var logit = weights.last()
            for (size in 1..4) {
                for (start in -size..0) {
                    val from = gap + start + 1
                    val to = from + size
                    if (from < 0 || to > padded.length) continue
                    val hash = murmur3("$start:${padded.substring(from, to)}".encodeToByteArray())
                    val index = (abs(hash.toLong()) % FEATURE_COUNT).toInt()
                    logit += if (hash >= 0) weights[index] else -weights[index]
                }
            }
            (-logit.coerceIn(-4f, 4f) * scale).roundToInt()
        }
    }

    companion object {
        private const val FEATURE_COUNT = 1 shl 15

        fun load(file: File): ExperimentalBunsetsuBoundaryModel {
            require(file.length() == (FEATURE_COUNT + 1L) * Float.SIZE_BYTES)
            val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            val weights = FloatArray(FEATURE_COUNT + 1).also { buffer.get(it) }
            require(weights.all(Float::isFinite))
            return ExperimentalBunsetsuBoundaryModel(weights)
        }

        private fun murmur3(bytes: ByteArray): Int {
            fun mixed(value: Int): Int = Integer.rotateLeft(value * 0xcc9e2d51.toInt(), 15) * 0x1b873593
            var hash = 0
            var index = 0
            while (index + 4 <= bytes.size) {
                val word = (bytes[index].toInt() and 255) or
                    ((bytes[index + 1].toInt() and 255) shl 8) or
                    ((bytes[index + 2].toInt() and 255) shl 16) or
                    ((bytes[index + 3].toInt() and 255) shl 24)
                hash = Integer.rotateLeft(hash xor mixed(word), 13) * 5 + 0xe6546b64.toInt()
                index += 4
            }
            var tail = 0
            for (offset in index until bytes.size) {
                tail = tail or ((bytes[offset].toInt() and 255) shl ((offset - index) * 8))
            }
            hash = hash xor mixed(tail) xor bytes.size
            hash = (hash xor (hash ushr 16)) * 0x85ebca6b.toInt()
            hash = (hash xor (hash ushr 13)) * 0xc2b2ae35.toInt()
            return hash xor (hash ushr 16)
        }
    }
}
