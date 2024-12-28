package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import java.text.DecimalFormat

fun detectMultipleSen(input: String): Boolean {
    val regex = """^(せん){2,}$""".toRegex()
    return regex.matches(input)
}

fun String.toNumber(): Pair<String, String>? {
    if (this == "ちょうせん" || detectMultipleSen(this) || this == "おくせん") return null
    val digits = mapOf(
        "ぜろ" to 0L, "れい" to 0L,
        "いち" to 1L, "いっ" to 1L,
        "に" to 2L,
        "さん" to 3L,
        "よん" to 4L, "し" to 4L,
        "ご" to 5L,
        "ろく" to 6L, "ろっ" to 6L,
        "なな" to 7L, "しち" to 7L,
        "はち" to 8L, "はっ" to 8L,
        "きゅう" to 9L, "く" to 9L
    )

    val units = mapOf(
        "じゅう" to 10L,
        "ひゃく" to 100L, "びゃく" to 100L, "ぴゃく" to 100L,
        "せん" to 1000L, "ぜん" to 1000L
    )

    val bigUnits = mapOf(
        "まん" to 10_000L,
        "おく" to 100_000_000L,
        "ちょう" to 1_000_000_000_000L,
    )

    val patterns = (digits.keys + units.keys + bigUnits.keys).sortedByDescending { it.length }

    var text = this
    var total = 0L

    fun parseDigits(digitText: String): Long? {
        var num = 0L
        var tmpText = digitText
        while (tmpText.isNotEmpty()) {
            var matched = false
            for (pattern in patterns) {
                if (digits.containsKey(pattern) && tmpText.startsWith(pattern)) {
                    num = num * 10 + digits[pattern]!!
                    tmpText = tmpText.substring(pattern.length)
                    matched = true
                    break
                }
            }
            if (!matched) {
                // Cannot parse further, invalid digit sequence
                return null
            }
        }
        return num
    }

    fun parseSection(sectionText: String): Long? {
        var sectionTotal = 0L
        var tmpText = sectionText

        val unitList = listOf("せん", "ぜん", "ひゃく", "びゃく", "ぴゃく", "じゅう")

        for (unit in unitList) {
            while (tmpText.contains(unit)) {
                val index = tmpText.indexOf(unit)
                val digitText = tmpText.substring(0, index)
                val unitValue = units[unit]!!

                var digit = 1L // Default to 1 if no digit is specified
                if (digitText.isNotEmpty()) {
                    val parsedDigit = parseDigits(digitText)
                        ?: return null // Invalid digit sequence
                    digit = parsedDigit
                }

                val value = digit * unitValue
                sectionTotal += value

                tmpText = tmpText.substring(index + unit.length)
            }
        }

        // Remaining text may contain digits without units
        if (tmpText.isNotEmpty()) {
            val digit = parseDigits(tmpText)
                ?: return null // Invalid digit sequence
            sectionTotal += digit
        }

        return sectionTotal
    }

    // Process big units
    while (text.isNotEmpty()) {
        var matchedBigUnit = false
        for ((bigUnit, bigUnitValue) in bigUnits.entries.sortedByDescending { it.value }) {
            if (text.contains(bigUnit)) {
                val index = text.indexOf(bigUnit)
                val leftText = text.substring(0, index)
                val sectionValue = parseSection(leftText)
                    ?: return null // Invalid section
                total += sectionValue * bigUnitValue
                text = text.substring(index + bigUnit.length)
                matchedBigUnit = true
                break
            }
        }
        if (!matchedBigUnit) {
            val sectionValue = parseSection(text)
                ?: return null // Invalid section
            total += sectionValue
            break
        }
    }

    if (total <= 0) return null

    val fullWidth = total.toString().map { it.toFullWidthChar() }.joinToString("")
    val halfWidth = total.toString()

    return Pair(fullWidth, halfWidth)
}

fun String.toNumberExponent(): Pair<String, String>? {
    val digits = mapOf(
        "ぜろ" to 0L, "れい" to 0L,
        "いち" to 1L, "いっ" to 1L,
        "に" to 2L,
        "さん" to 3L,
        "よん" to 4L, "し" to 4L,
        "ご" to 5L,
        "ろく" to 6L, "ろっ" to 6L,
        "なな" to 7L, "しち" to 7L,
        "はち" to 8L, "はっ" to 8L,
        "きゅう" to 9L, "く" to 9L
    )

    val units = mapOf(
        "じゅう" to 10L,
        "ひゃく" to 100L, "びゃく" to 100L, "ぴゃく" to 100L,
        "せん" to 1000L, "ぜん" to 1000L
    )

    val bigUnits = mapOf(
        "まん" to 10_000L,
        "おく" to 100_000_000L,
        "ちょう" to 1_000_000_000_000L,
    )

    val patterns = (digits.keys + units.keys + bigUnits.keys).sortedByDescending { it.length }

    var text = this
    var total = 0L

    fun parseDigits(digitText: String): Long? {
        var num = 0L
        var tmpText = digitText
        while (tmpText.isNotEmpty()) {
            var matched = false
            for (pattern in patterns) {
                if (digits.containsKey(pattern) && tmpText.startsWith(pattern)) {
                    num = num * 10 + digits[pattern]!!
                    tmpText = tmpText.substring(pattern.length)
                    matched = true
                    break
                }
            }
            if (!matched) {
                // Cannot parse further, invalid digit sequence
                return null
            }
        }
        return num
    }

    fun parseSection(sectionText: String): Long? {
        var sectionTotal = 0L
        var tmpText = sectionText

        val unitList = listOf("せん", "ぜん", "ひゃく", "びゃく", "ぴゃく", "じゅう")

        for (unit in unitList) {
            while (tmpText.contains(unit)) {
                val index = tmpText.indexOf(unit)
                val digitText = tmpText.substring(0, index)
                val unitValue = units[unit]!!

                var digit = 1L // Default to 1 if no digit is specified
                if (digitText.isNotEmpty()) {
                    val parsedDigit = parseDigits(digitText)
                        ?: return null // Invalid digit sequence
                    digit = parsedDigit
                }

                val value = digit * unitValue
                sectionTotal += value

                tmpText = tmpText.substring(index + unit.length)
            }
        }

        // Remaining text may contain digits without units
        if (tmpText.isNotEmpty()) {
            val digit = parseDigits(tmpText)
                ?: return null // Invalid digit sequence
            sectionTotal += digit
        }

        return sectionTotal
    }

    // Process big units
    while (text.isNotEmpty()) {
        var matchedBigUnit = false
        for ((bigUnit, bigUnitValue) in bigUnits.entries.sortedByDescending { it.value }) {
            if (text.contains(bigUnit)) {
                val index = text.indexOf(bigUnit)
                val leftText = text.substring(0, index)
                val sectionValue = parseSection(leftText)
                    ?: return null // Invalid section
                total += sectionValue * bigUnitValue
                text = text.substring(index + bigUnit.length)
                matchedBigUnit = true
                break
            }
        }
        if (!matchedBigUnit) {
            val sectionValue = parseSection(text)
                ?: return null // Invalid section
            total += sectionValue
            break
        }
    }

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

fun String.toKanjiNumber(): String? {
    val digitsKanji = mapOf(
        "ぜろ" to "〇", "れい" to "〇",
        "いち" to "一", "いっ" to "一",
        "に" to "二",
        "さん" to "三",
        "よん" to "四", "し" to "四",
        "ご" to "五",
        "ろく" to "六", "ろっ" to "六",
        "なな" to "七", "しち" to "七",
        "はち" to "八", "はっ" to "八",
        "きゅう" to "九", "く" to "九"
    )

    val unitsKanji = mapOf(
        "じゅう" to "十",
        "ひゃく" to "百", "びゃく" to "百", "ぴゃく" to "百",
        "せん" to "千", "ぜん" to "千"
    )

    val bigUnitsKanji = mapOf(
        "まん" to "万",
        "おく" to "億",
        "ちょう" to "兆",
    )

    // Check if input contains any big units
    if (bigUnitsKanji.keys.any { this.contains(it) }) {
        return null
    }

    fun parseDigits(digitText: String): String? {
        var result = ""
        var tmpText = digitText
        val patterns = digitsKanji.keys.sortedByDescending { it.length }
        while (tmpText.isNotEmpty()) {
            var matched = false
            for (pattern in patterns) {
                if (tmpText.startsWith(pattern)) {
                    val digitKanji = digitsKanji[pattern]!!
                    result += digitKanji
                    tmpText = tmpText.substring(pattern.length)
                    matched = true
                    break
                }
            }
            if (!matched) {
                // Cannot parse further, invalid digit sequence
                return null
            }
        }
        return result
    }

    fun parseSection(sectionText: String): String? {
        var kanjiResult = ""
        var tmpText = sectionText

        val unitList = listOf("せん", "ぜん", "ひゃく", "びゃく", "ぴゃく", "じゅう")

        for (unit in unitList) {
            if (tmpText.contains(unit)) {
                val index = tmpText.indexOf(unit)
                val digitText = tmpText.substring(0, index)
                val unitKanji = unitsKanji[unit]!!

                val digitKanji: String = if (digitText.isNotEmpty()) {
                    val parsedDigit = parseDigits(digitText)
                        ?: return null // Invalid digit sequence
                    parsedDigit
                } else {
                    "一"
                }  // Default to '一' if no digit is specified

                kanjiResult += if (digitKanji == "一") {
                    unitKanji
                } else {
                    digitKanji + unitKanji
                }

                tmpText = tmpText.substring(index + unit.length)
            }
        }

        // Remaining text may contain digits without units
        if (tmpText.isNotEmpty()) {
            val digitKanji = parseDigits(tmpText)
                ?: return null // Invalid digit sequence
            kanjiResult += digitKanji
        }

        return kanjiResult
    }

    return parseSection(this)
}
