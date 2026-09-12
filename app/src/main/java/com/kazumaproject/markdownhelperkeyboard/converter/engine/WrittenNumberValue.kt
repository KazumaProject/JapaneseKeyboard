package com.kazumaproject.markdownhelperkeyboard.converter.engine

/** Value check for dictionary spellings (e.g. 零/〇, 一千/千), never a kana decoder. */
internal object WrittenNumberValue {
    private val digits = "〇一二三四五六七八九"
    private val small = mapOf('十' to 10L, '百' to 100L, '千' to 1000L)
    private val big = mapOf('万' to 10000L, '億' to 100000000L, '兆' to 1000000000000L, '京' to 10000000000000000L)
    private val groupedDecimal = Regex("[0-9]{1,3}(,[0-9]{3})+")

    fun parse(text: String): Long? = try {
        parseChecked(text)
    } catch (_: ArithmeticException) {
        null
    }

    private fun parseChecked(text: String): Long? {
        if (text.isEmpty()) return null
        val normalized = text.map { if (it in '０'..'９') it - 0xFEE0 else it }.joinToString("")
        if (',' in normalized) return if (groupedDecimal.matches(normalized)) normalized.replace(",", "").toLongOrNull() else null
        var total = 0L
        var section = 0L
        var pending: Long? = null
        var lastSmall = 10000L
        var lastBig = Long.MAX_VALUE
        for (char in normalized) {
            val digit = when {
                char in '0'..'9' -> char - '0'
                char == '零' -> 0
                else -> digits.indexOf(char)
            }
            if (digit >= 0) {
                pending = Math.addExact(Math.multiplyExact(pending ?: 0L, 10L), digit.toLong())
                continue
            }
            val place = small[char]
            if (place != null) {
                val coefficient = pending ?: 1L
                if (place >= lastSmall || coefficient !in 1..9) return null
                section = Math.addExact(section, coefficient * place)
                pending = null
                lastSmall = place
                continue
            }
            val magnitude = big[char] ?: return null
            if (magnitude >= lastBig || (pending ?: 0L) >= lastSmall) return null
            val coefficient = Math.addExact(section, pending ?: 0L)
            if (coefficient !in 1..9999) return null
            total = Math.addExact(total, Math.multiplyExact(coefficient, magnitude))
            section = 0
            pending = null
            lastSmall = 10000
            lastBig = magnitude
        }
        if (lastSmall != 10000L && (pending ?: 0L) >= lastSmall) return null
        return Math.addExact(total, Math.addExact(section, pending ?: 0L))
    }
}
