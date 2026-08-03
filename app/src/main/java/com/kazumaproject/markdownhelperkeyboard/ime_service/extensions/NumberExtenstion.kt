package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import java.text.DecimalFormat

fun detectMultipleSen(input: String): Boolean {
    val regex = """^(せん){2,}$""".toRegex()
    return regex.matches(input)
}

private enum class JapaneseNumberReadingType {
    UNIT,
    SMALL_PLACE,
    BIG_PLACE,
}

private data class JapaneseNumberReading(
    val reading: String,
    val type: JapaneseNumberReadingType,
    val value: Long,
    val placeOrder: Int = 1,
)

/**
 * Number readings accepted by Mozc's NumberDecoder, limited to the range that
 * can be represented by this app's Long-based number candidate API.
 *
 * Sound changes such as 「いっ」「ろっ」「はっ」 are units here, not global
 * string replacements.  Decoder state determines where they are usable.
 */
private val japaneseNumberReadings = listOf(
    JapaneseNumberReading("ぜろ", JapaneseNumberReadingType.UNIT, 0L),
    JapaneseNumberReading("れい", JapaneseNumberReadingType.UNIT, 0L),
    JapaneseNumberReading("いち", JapaneseNumberReadingType.UNIT, 1L),
    JapaneseNumberReading("いっ", JapaneseNumberReadingType.UNIT, 1L),
    JapaneseNumberReading("に", JapaneseNumberReadingType.UNIT, 2L),
    JapaneseNumberReading("さん", JapaneseNumberReadingType.UNIT, 3L),
    JapaneseNumberReading("し", JapaneseNumberReadingType.UNIT, 4L),
    JapaneseNumberReading("よん", JapaneseNumberReadingType.UNIT, 4L),
    JapaneseNumberReading("よ", JapaneseNumberReadingType.UNIT, 4L),
    JapaneseNumberReading("ご", JapaneseNumberReadingType.UNIT, 5L),
    JapaneseNumberReading("ろく", JapaneseNumberReadingType.UNIT, 6L),
    JapaneseNumberReading("ろっ", JapaneseNumberReadingType.UNIT, 6L),
    JapaneseNumberReading("なな", JapaneseNumberReadingType.UNIT, 7L),
    JapaneseNumberReading("しち", JapaneseNumberReadingType.UNIT, 7L),
    JapaneseNumberReading("はち", JapaneseNumberReadingType.UNIT, 8L),
    JapaneseNumberReading("はっ", JapaneseNumberReadingType.UNIT, 8L),
    JapaneseNumberReading("きゅう", JapaneseNumberReadingType.UNIT, 9L),
    JapaneseNumberReading("きゅー", JapaneseNumberReadingType.UNIT, 9L),
    JapaneseNumberReading("く", JapaneseNumberReadingType.UNIT, 9L),
    JapaneseNumberReading("じゅう", JapaneseNumberReadingType.SMALL_PLACE, 10L, 2),
    JapaneseNumberReading("じゅー", JapaneseNumberReadingType.SMALL_PLACE, 10L, 2),
    JapaneseNumberReading("じゅっ", JapaneseNumberReadingType.SMALL_PLACE, 10L, 2),
    JapaneseNumberReading("じっ", JapaneseNumberReadingType.SMALL_PLACE, 10L, 2),
    JapaneseNumberReading("ひゃく", JapaneseNumberReadingType.SMALL_PLACE, 100L, 3),
    JapaneseNumberReading("ひゃっ", JapaneseNumberReadingType.SMALL_PLACE, 100L, 3),
    JapaneseNumberReading("びゃく", JapaneseNumberReadingType.SMALL_PLACE, 100L, 3),
    JapaneseNumberReading("びゃっ", JapaneseNumberReadingType.SMALL_PLACE, 100L, 3),
    JapaneseNumberReading("ぴゃく", JapaneseNumberReadingType.SMALL_PLACE, 100L, 3),
    JapaneseNumberReading("ぴゃっ", JapaneseNumberReadingType.SMALL_PLACE, 100L, 3),
    JapaneseNumberReading("せん", JapaneseNumberReadingType.SMALL_PLACE, 1_000L, 4),
    JapaneseNumberReading("ぜん", JapaneseNumberReadingType.SMALL_PLACE, 1_000L, 4),
    JapaneseNumberReading("まん", JapaneseNumberReadingType.BIG_PLACE, 10_000L, 5),
    JapaneseNumberReading("おく", JapaneseNumberReadingType.BIG_PLACE, 100_000_000L, 9),
    JapaneseNumberReading("おっ", JapaneseNumberReadingType.BIG_PLACE, 100_000_000L, 9),
    JapaneseNumberReading("ちょう", JapaneseNumberReadingType.BIG_PLACE, 1_000_000_000_000L, 13),
).sortedByDescending { it.reading.length }

private fun parseJapaneseNumberValue(input: String): Long? {
    if (input.isEmpty()) return null

    var index = 0
    var total = 0L
    var section = -1L
    var smallPlaceOrder = -1
    var bigPlaceOrder = Int.MAX_VALUE
    var hasNumber = false
    var restrictedUnit: String? = null

    while (index < input.length) {
        val entry = japaneseNumberReadings.firstOrNull {
            input.startsWith(it.reading, index)
        } ?: return null

        // Mozc accepts these historical readings only in constrained places:
        // 「よ」「く」 must finish the number, while 「し」 may additionally
        // precede canonical 「じゅう」 (e.g. 四十 = しじゅう).
        when (restrictedUnit) {
            "よ", "く" -> return null
            "し" -> if (entry.reading != "じゅう") return null
        }
        restrictedUnit = if (
            entry.type == JapaneseNumberReadingType.UNIT &&
            (entry.reading == "し" || entry.reading == "よ" || entry.reading == "く")
        ) {
            entry.reading
        } else {
            null
        }

        when (entry.type) {
            JapaneseNumberReadingType.UNIT -> {
                if (hasNumber && entry.value == 0L) return null
                if (section == 0L || (section >= 0L && section % 10L != 0L)) return null

                section = if (section < 0L) {
                    entry.value
                } else {
                    Math.addExact(section, entry.value)
                }
                hasNumber = true
            }

            JapaneseNumberReadingType.SMALL_PLACE -> {
                if (smallPlaceOrder > 1 && entry.placeOrder >= smallPlaceOrder) return null
                if (section == 0L) return null

                section = if (section < 0L) {
                    entry.value
                } else {
                    val unit = maxOf(1L, section % 10L)
                    val base = section / 10L * 10L
                    Math.addExact(base, Math.multiplyExact(unit, entry.value))
                }
                smallPlaceOrder = entry.placeOrder
                hasNumber = true
            }

            JapaneseNumberReadingType.BIG_PLACE -> {
                if (entry.placeOrder >= bigPlaceOrder || section <= 0L) return null

                total = Math.addExact(total, Math.multiplyExact(section, entry.value))
                section = -1L
                smallPlaceOrder = -1
                bigPlaceOrder = entry.placeOrder
                hasNumber = true
            }
        }

        index += entry.reading.length
    }

    if (!hasNumber) return null
    return if (section >= 0L) Math.addExact(total, section) else total
}

fun String.toNumber(): Pair<String, String>? {
    val total = try {
        parseJapaneseNumberValue(this)
    } catch (_: ArithmeticException) {
        null
    } ?: return null

    val fullWidth = total.toString().map { it.toFullWidthChar() }.joinToString("")
    val halfWidth = total.toString()

    return Pair(fullWidth, halfWidth)
}

fun String.toNumberExponent(): Pair<String, String>? {
    val total = try {
        parseJapaneseNumberValue(this)
    } catch (_: ArithmeticException) {
        null
    } ?: return null

    val result = if (total < 100_000_000L) {
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
