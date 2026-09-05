package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import com.kazumaproject.markdownhelperkeyboard.converter.engine.NumericNumberParser
import com.kazumaproject.markdownhelperkeyboard.converter.engine.NumericNumberFormatter
import java.text.DecimalFormat
import java.math.BigInteger

fun detectMultipleSen(input: String): Boolean {
    val regex = """^(せん){2,}$""".toRegex()
    return regex.matches(input)
}

fun String.toNumber(): Pair<String, String>? {
    val total = NumericNumberParser.parse(this)?.value ?: return null

    val fullWidth = total.toString().map { it.toFullWidthChar() }.joinToString("")
    val halfWidth = total.toString()

    return Pair(fullWidth, halfWidth)
}

fun String.toNumberExponent(): Pair<String, String>? {
    val total = NumericNumberParser.parse(this)?.value ?: return null

    val result = if (total < BigInteger.valueOf(100_000_000L)) {
        null
    } else {
        val exponent = total.toString().length - 1
        val exponentString = displayExponent(10, exponent)
        Pair(exponentString, exponentString)
    }

    return result
}

// Helper function to convert to full-width character
fun Char.toFullWidthChar(): Char {
    return if (this in '0'..'9') {
        (this + 0xFEE0)
    } else {
        this
    }
}

// Helper function to display exponent
fun displayExponent(base: Int, exponent: Int): String {
    val superscripts = mapOf(
        '-' to '⁻', '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³',
        '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷',
        '8' to '⁸', '9' to '⁹'
    )
    val exponentString = exponent.toString().map { superscripts[it] ?: it }.joinToString("")
    return "$base$exponentString"
}

fun Long.convertToKanjiNotation(): String {
    return NumericNumberFormatter.toMixedKanji(BigInteger.valueOf(this))
}

fun String.addCommasToNumber(): String {
    return try {
        val number = if (this.contains(".")) {
            this.toDouble() // Parse as Double for decimal numbers
        } else {
            this.toLong()   // Parse as Long for integer numbers
        }

        val formatter = DecimalFormat("#,###.##") // Formatter for both integer and decimal numbers
        formatter.format(number)
    } catch (e: NumberFormatException) {
        // Return the original string if parsing fails
        this
    }
}

// この関数をどこか（例えば NumberConverter.kt の末尾）に追加します
fun Long.toKanji(): String {
    return NumericNumberFormatter.toJapaneseKanji(BigInteger.valueOf(this))
}

fun BigInteger.convertToKanjiNotation(): String = NumericNumberFormatter.toMixedKanji(this)

fun BigInteger.toKanji(): String = NumericNumberFormatter.toJapaneseKanji(this)
