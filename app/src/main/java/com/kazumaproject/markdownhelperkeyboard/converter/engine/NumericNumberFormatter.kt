package com.kazumaproject.markdownhelperkeyboard.converter.engine

import java.math.BigInteger

/** Shared BigInteger formatting used by the numeric renderer and legacy number extensions. */
internal object NumericNumberFormatter {

    private val tenThousand = BigInteger.valueOf(10_000)
    private val zero = BigInteger.ZERO
    private val bigUnits = listOf(
        "", "万", "億", "兆", "京", "垓", "𥝱", "穣", "溝", "澗", "正", "載", "極",
        "恒河沙", "阿僧祇", "那由他", "不可思議", "無量大数",
    )
    private val digits = listOf("〇", "一", "二", "三", "四", "五", "六", "七", "八", "九")
    private val smallUnits = listOf("", "十", "百", "千")

    fun toJapaneseKanji(value: BigInteger): String {
        if (value == zero) return "〇"
        if (value.signum() < 0) return "−" + toJapaneseKanji(value.negate())

        var remaining = value
        var unitIndex = 0
        var result = ""
        while (remaining > zero) {
            val chunk = remaining.mod(tenThousand).toInt()
            if (chunk != 0) {
                if (unitIndex >= bigUnits.size) return value.toString()
                result = renderSmallChunk(chunk) + bigUnits[unitIndex] + result
            }
            remaining = remaining.divide(tenThousand)
            unitIndex++
        }
        return result
    }

    fun toMixedKanji(value: BigInteger): String {
        if (value < tenThousand) return value.toString()
        if (value.signum() < 0) return "−" + toMixedKanji(value.negate())

        var remaining = value
        var unitIndex = 0
        val chunks = mutableListOf<Pair<Int, Int>>()
        while (remaining > zero) {
            val chunk = remaining.mod(tenThousand).toInt()
            if (chunk != 0) chunks += chunk to unitIndex
            remaining = remaining.divide(tenThousand)
            unitIndex++
        }
        if (chunks.any { it.second >= bigUnits.size }) return value.toString()
        return chunks.asReversed().joinToString("") { (chunk, index) ->
            chunk.toString() + bigUnits[index]
        }
    }

    fun addDigitGrouping(digitsText: String): String {
        if (digitsText.length <= 3) return digitsText
        val firstLength = digitsText.length % 3
        val first = if (firstLength == 0) {
            digitsText.substring(0, 3)
        } else {
            digitsText.substring(0, firstLength)
        }
        val restStart = first.length
        return if (restStart == digitsText.length) {
            digitsText
        } else {
            first + digitsText.substring(restStart).chunked(3).joinToString("", prefix = ",")
        }
    }

    fun toFullWidthDigits(digitsText: String): String = digitsText.map { character ->
        if (character in '0'..'9') (character.code + 0xFEE0).toChar() else character
    }.joinToString("")

    fun toExponentOrNull(value: BigInteger): String? {
        if (value < BigInteger.valueOf(100_000_000L)) return null
        val exponent = value.toString().length - 1
        return "10" + exponent.toString().map { superscript(it) }.joinToString("")
    }

    fun toSuperscript(digitsText: String): String = digitsText.map(::superscript).joinToString("")

    fun toSubscript(digitsText: String): String = digitsText.map { character ->
        subscriptDigits[character] ?: character
    }.joinToString("")

    private fun renderSmallChunk(chunk: Int): String {
        var value = chunk
        var unitIndex = 0
        var result = ""
        while (value > 0) {
            val digit = value % 10
            if (digit != 0) {
                val digitText = if (digit == 1 && unitIndex > 0) "" else digits[digit]
                result = digitText + smallUnits[unitIndex] + result
            }
            value /= 10
            unitIndex++
        }
        return result
    }

    private val superscriptDigits = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    )

    private val subscriptDigits = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
    )

    private fun superscript(character: Char): Char = superscriptDigits[character] ?: character
}
