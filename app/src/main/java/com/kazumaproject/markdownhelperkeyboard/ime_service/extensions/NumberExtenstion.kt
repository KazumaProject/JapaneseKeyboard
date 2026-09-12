package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import java.text.DecimalFormat

fun detectMultipleSen(input: String): Boolean {
    val regex = """^(せん){2,}$""".toRegex()
    return regex.matches(input)
}

/** Compatibility adapters: all paths use the same complete-reading grammar. */
fun String.toNumber(): Pair<String, String>? =
    com.kazumaproject.markdownhelperkeyboard.converter.engine.ValidatedNumber.parseReading(this)
        ?.let { it.fullWidth to it.digits }

fun String.toNumberExponent(): Pair<String, String>? =
    com.kazumaproject.markdownhelperkeyboard.converter.engine.ValidatedNumber.parseReading(this)
        ?.exponent()?.let { it to it }

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
    if (this == 0L) return "0"

    val units = listOf(
        Pair(1_000_000_000_000L, "兆"),      // 10^12
        Pair(100_000_000L, "億"),           // 10^8
        Pair(10_000L, "万")                 // 10^4
    )

    var remaining = this
    val parts = mutableListOf<String>()

    for ((unitValue, unitName) in units) {
        if (remaining >= unitValue) {
            val unitCount = remaining / unitValue
            remaining %= unitValue
            parts.add("${unitCount}${unitName}")
        }
    }

    if (remaining > 0 || parts.isEmpty()) {
        parts.add(remaining.toString())
    }

    return parts.joinToString("")
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
    if (this == 0L) return "〇"

    val kanjiDigits = listOf("〇", "一", "二", "三", "四", "五", "六", "七", "八", "九")
    val kanjiUnits = listOf("", "十", "百", "千")
    val kanjiBigUnits = listOf("", "万", "億", "兆", "京")

    var num = this
    var result = ""
    var bigUnitIndex = 0

    while (num > 0) {
        val chunk = (num % 10000).toInt()
        if (chunk > 0) {
            var chunkStr = ""
            var n = chunk
            var unitIndex = 0
            while (n > 0) {
                val digit = n % 10
                if (digit > 0) {
                    // 10, 100, 1000 の場合、先頭の「一」は省略する
                    val digitStr = if (digit == 1 && unitIndex > 0) "" else kanjiDigits[digit]
                    chunkStr = digitStr + kanjiUnits[unitIndex] + chunkStr
                }
                n /= 10
                unitIndex++
            }
            result = chunkStr + kanjiBigUnits[bigUnitIndex] + result
        }
        num /= 10000
        bigUnitIndex++
    }
    return result
}
