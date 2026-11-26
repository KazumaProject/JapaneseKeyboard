package com.kazumaproject.markdownhelperkeyboard.converter.english.louds

internal interface EnglishLoudsLabels {
    val size: Int
    operator fun get(index: Int): Char

    fun toCharArray(): CharArray = CharArray(size) { get(it) }

    class Chars(private val values: CharArray) : EnglishLoudsLabels {
        override val size: Int get() = values.size
        override fun get(index: Int): Char = values[index]
        override fun toCharArray(): CharArray = values
    }

    class Indexed(private val indices: ByteArray, private val alphabet: CharArray) : EnglishLoudsLabels {
        override val size: Int get() = indices.size
        override fun get(index: Int): Char = alphabet[indices[index].toInt() and 0xff]
    }
}
