package com.kazumaproject.markdownhelperkeyboard.converter.number

/** The semantic identity is independent of the displayed counter spelling. */
data class NumericIdentity(val value: Long, val counter: String, val digits: String = value.toString())

data class NumericSpan(
    val start: Int,
    val end: Int,
    val identity: NumericIdentity,
    val rightId: Short,
    /** Short kana such as particle 「に」 may identify an existing numeric node, but never force one. */
    val canSupplement: Boolean = true,
)

/** Immutable, query-local analysis shared by graph construction and candidate composition. */
class NumberReadingAnalysis internal constructor(
    internal val input: String,
    internal val spans: List<NumericSpan>,
)

internal object NumberReadingDecoder {
    internal data class Token(val text: String, val value: Long, val place: Boolean = false)
    private val units = listOf(
        "ぜろ" to 0L, "れい" to 0L, "いち" to 1L, "いっ" to 1L,
        "に" to 2L, "さん" to 3L, "し" to 4L, "よん" to 4L, "よ" to 4L,
        "ご" to 5L, "ろく" to 6L, "ろっ" to 6L, "なな" to 7L, "しち" to 7L,
        "はち" to 8L, "はっ" to 8L, "きゅう" to 9L, "きゅー" to 9L, "く" to 9L,
    ).map { Token(it.first, it.second) }
    private val places = listOf(
        "じゅう" to 10L, "じゅー" to 10L, "じゅっ" to 10L, "じっ" to 10L,
        "ひゃく" to 100L, "ひゃっ" to 100L, "びゃく" to 100L, "びゃっ" to 100L,
        "ぴゃく" to 100L, "ぴゃっ" to 100L, "せん" to 1000L, "ぜん" to 1000L,
        "まん" to 10000L, "おく" to 100000000L, "おっ" to 100000000L,
        "ちょう" to 1000000000000L, "けい" to 10000000000000000L,
    ).map { Token(it.first, it.second, true) }
    private val readings = (units + places).sortedByDescending { it.text.length }
    fun tokenAt(input: String, start: Int): Token? {
        if (start >= input.length) return null
        val digit = input[start].digit()
        if (digit != null) {
            var end = start + 1
            while (end < input.length && input[end].digit() != null) end++
            val text = input.substring(start, end)
            return Token(text, if (text.length > 96) -1L else text.map { it.digit()!! }.joinToString("").toLongOrNull() ?: -1L)
        }
        return readings.firstOrNull { input.startsWith(it.text, start) }
    }
    private fun Char.digit(): Int? = when (this) {
        in '0'..'9' -> this - '0'
        in '０'..'９' -> this - '０'
        else -> null
    }
    fun tokens(input: String): List<Token>? {
        val result = mutableListOf<Token>()
        var index = 0
        while (index < input.length) {
            val token = tokenAt(input, index) ?: return null
            if (token.value < 0 || result.size >= 96) return null
            result.add(token)
            index += token.text.length
        }
        return result.takeIf { it.isNotEmpty() }
    }
    fun value(tokens: List<Token>): Long? {
        return try {
        var total = 0L
        var section = 0L
        var pending: Long? = null
        var small = 10000L
        var big = Long.MAX_VALUE
        for (token in tokens) {
            if (!token.place) {
                if (pending != null || section > 0L && token.value >= small) return null
                pending = token.value
            } else if (token.value < 10000L) {
                if (token.value >= small) return null
                val coefficient = pending ?: 1L
                if (coefficient !in 1..9) return null
                section = Math.addExact(section, Math.multiplyExact(coefficient, token.value))
                pending = null
                small = token.value
            } else {
                if (token.value >= big) return null
                val coefficient = Math.addExact(section, pending ?: 0L)
                if (coefficient <= 0L || coefficient >= 10000L && section > 0L) return null
                val amount = Math.multiplyExact(coefficient, token.value)
                if (big != Long.MAX_VALUE && amount >= big) return null
                total = Math.addExact(total, amount)
                section = 0L
                pending = null
                small = 10000L
                big = token.value
            }
        }
        val remainder = Math.addExact(section, pending ?: 0L)
        if (big != Long.MAX_VALUE && remainder >= big) return null
        Math.addExact(total, remainder)
    } catch (_: ArithmeticException) { null }
    }

    /** Validate changes within a number; the final sound belongs to the counter registry. */
    fun validInternalSounds(tokens: List<Token>, allowContractedTail: Boolean): Boolean {
        for ((index, token) in tokens.withIndex()) {
            val next = tokens.getOrNull(index + 1)?.text
            val previous = tokens.getOrNull(index - 1)?.text
            when (token.text) {
                "いっ" -> if (next == null) { if (!allowContractedTail) return false }
                    else if (next !in setOf("せん", "ちょう", "けい")) return false
                "ろっ" -> if (next == null) { if (!allowContractedTail) return false }
                    else if (next !in setOf("ぴゃく", "ぴゃっ")) return false
                "はっ" -> if (next == null) { if (!allowContractedTail) return false }
                    else if (next !in setOf("ぴゃく", "ぴゃっ", "せん", "ちょう", "けい")) return false
                "じゅっ", "じっ", "ひゃっ", "びゃっ", "ぴゃっ" ->
                    if (next != null || !allowContractedTail) return false
                "おっ" -> if (next != "ちょう") return false
                "びゃく", "びゃっ" -> if (previous != "さん") return false
                "ぴゃく", "ぴゃっ" -> if (previous !in setOf("ろっ", "はっ")) return false
                "じゅう", "じゅー" -> if (previous == "いち") return false
                "ひゃく", "ひゃっ" -> if (previous in setOf("いち", "さん", "ろく", "ろっ", "はち", "はっ")) return false
                "ぜん" -> if (previous != "さん") return false
                "せん" -> if (previous in setOf("さん", "いち", "はち")) return false
                "よ", "く" -> if (next != null) return false
                "し" -> if (next != null && next != "じゅう") return false
            }
        }
        return true
    }

    fun readingValue(input: String): Long? {
        val tokens = tokens(input) ?: return null
        if (!validInternalSounds(tokens, false)) return null
        return value(tokens)
    }

    /** Positional digits and multiplicative units, including financial kanji. */
    fun surfaceValue(input: String): Long? {
        val digits = "〇一二三四五六七八九"
        val aliases = mapOf('零' to '〇', '壱' to '一', '弐' to '二', '参' to '三', '拾' to '十', '萬' to '万')
        val magnitudes = mapOf('十' to 10L, '百' to 100L, '千' to 1000L, '万' to 10000L,
            '億' to 100000000L, '兆' to 1000000000000L, '京' to 10000000000000000L)
        val tokens = mutableListOf<Token>()
        val run = StringBuilder()
        fun flush(): Boolean {
            if (run.isEmpty()) return true
            val value = run.toString().toLongOrNull() ?: return false
            tokens.add(Token(run.toString(), value))
            run.clear()
            return true
        }
        for (raw in input) {
            val ch = aliases[raw] ?: raw
            val digit = ch.digit() ?: digits.indexOf(ch).takeIf { it >= 0 }
            if (digit != null) run.append(digit) else {
                val place = magnitudes[ch] ?: return null
                if (!flush()) return null
                tokens.add(Token(ch.toString(), place, true))
            }
        }
        if (!flush() || tokens.isEmpty()) return null
        return value(tokens)
    }
}
