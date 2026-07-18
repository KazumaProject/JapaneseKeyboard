package com.kazumaproject.markdownhelperkeyboard.converter.compact

/** Exact fixed-width storage for integer arrays whose value range is narrower than 32 bits. */
class PackedIntArray private constructor(
    val size: Int,
    private val minimum: Int,
    private val bitWidth: Int,
    private val words: LongArray,
) {
    operator fun get(index: Int): Int {
        require(index in 0 until size) { "PackedIntArray index out of bounds: $index/$size" }
        if (bitWidth == 0) return minimum
        val bitIndex = index.toLong() * bitWidth
        val wordIndex = (bitIndex ushr 6).toInt()
        val shift = (bitIndex and 63L).toInt()
        var encoded = words[wordIndex] ushr shift
        if (shift + bitWidth > Long.SIZE_BITS) {
            encoded = encoded or (words[wordIndex + 1] shl (Long.SIZE_BITS - shift))
        }
        return (encoded and mask(bitWidth)).toInt() + minimum
    }

    fun toIntArray(): IntArray = IntArray(size) { get(it) }

    companion object {
        fun from(values: IntArray): PackedIntArray {
            val minimum = values.minOrNull() ?: 0
            val maximum = values.maxOrNull() ?: minimum
            return from(values.size, minimum, maximum) { values[it] }
        }

        internal fun from(
            size: Int,
            minimum: Int,
            maximum: Int,
            valueAt: (Int) -> Int,
        ): PackedIntArray {
            require(size >= 0)
            require(maximum >= minimum)
            val range = maximum.toLong() - minimum.toLong()
            val bitWidth = if (range == 0L) 0 else Long.SIZE_BITS - java.lang.Long.numberOfLeadingZeros(range)
            val wordCount = if (bitWidth == 0 || size == 0) {
                0
            } else {
                ((size.toLong() * bitWidth + Long.SIZE_BITS - 1) / Long.SIZE_BITS).toInt()
            }
            val words = LongArray(wordCount)
            if (bitWidth > 0) {
                for (index in 0 until size) {
                    val encoded = valueAt(index).toLong() - minimum.toLong()
                    require(encoded in 0..range) {
                        "Packed value out of range at $index: ${valueAt(index)} not in $minimum..$maximum"
                    }
                    val bitIndex = index.toLong() * bitWidth
                    val wordIndex = (bitIndex ushr 6).toInt()
                    val shift = (bitIndex and 63L).toInt()
                    words[wordIndex] = words[wordIndex] or (encoded shl shift)
                    if (shift + bitWidth > Long.SIZE_BITS) {
                        words[wordIndex + 1] = words[wordIndex + 1] or
                            (encoded ushr (Long.SIZE_BITS - shift))
                    }
                }
            }
            return PackedIntArray(size, minimum, bitWidth, words)
        }

        private fun mask(bitWidth: Int): Long =
            if (bitWidth == Long.SIZE_BITS) -1L else (1L shl bitWidth) - 1L
    }
}

/** Exact character storage using a per-array alphabet and packed alphabet indices. */
class PackedCharArray private constructor(
    private val alphabet: CharArray,
    private val indices: PackedIntArray,
) {
    val size: Int
        get() = indices.size

    operator fun get(index: Int): Char = alphabet[indices[index]]

    fun toCharArray(): CharArray = CharArray(size) { get(it) }

    companion object {
        fun from(values: CharArray): PackedCharArray {
            if (values.isEmpty()) {
                return PackedCharArray(charArrayOf(), PackedIntArray.from(intArrayOf()))
            }
            val present = BooleanArray(Char.MAX_VALUE.code + 1)
            values.forEach { present[it.code] = true }
            val alphabet = CharArray(present.count { it })
            val indexByChar = IntArray(present.size)
            var alphabetIndex = 0
            present.indices.forEach { charCode ->
                if (present[charCode]) {
                    alphabet[alphabetIndex] = charCode.toChar()
                    indexByChar[charCode] = alphabetIndex++
                }
            }
            val indices = PackedIntArray.from(
                size = values.size,
                minimum = 0,
                maximum = alphabet.lastIndex,
            ) { index -> indexByChar[values[index].code] }
            return PackedCharArray(alphabet, indices)
        }
    }
}
