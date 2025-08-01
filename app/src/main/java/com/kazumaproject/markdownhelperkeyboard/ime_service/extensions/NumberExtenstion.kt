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

                // Ensure the leftText is a valid numeric sequence
                if (leftText.isEmpty() || leftText.any { it !in digits.keys.joinToString("") }) {
                    return null  // Reject cases like "まんご"
                }

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

