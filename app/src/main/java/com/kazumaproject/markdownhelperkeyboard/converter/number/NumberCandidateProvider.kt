package com.kazumaproject.markdownhelperkeyboard.converter.number

import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertToKanjiNotation
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toFullWidthChar
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toKanji

internal data class CounterReading(val reading: String, val surface: String, val rightId: Short = 2011)

internal enum class NumberStyle(val format: NumberCandidateFormat, val priority: Int) {
    HALF(NumberCandidateFormat.HALF_WIDTH, 0),
    COMMA(NumberCandidateFormat.HALF_WIDTH, 1),
    MIXED(NumberCandidateFormat.HALF_WIDTH, 2),
    FULL(NumberCandidateFormat.FULL_WIDTH, 0),
    KANJI(NumberCandidateFormat.KANJI, 0),
}

internal data class ParsedNumber(val value: Long, val counter: String = "", val rightId: Short = 2044, val digits: String = value.toString()) {
    private val rendered by lazy { NumberStyle.entries.map(::renderUncached) }
    private val renderingStyles by lazy {
        buildMap { NumberStyle.entries.forEach { putIfAbsent(render(it), it) } }
    }
    fun render(style: NumberStyle): String = rendered[style.ordinal]
    private fun renderUncached(style: NumberStyle): String = when (style) {
        NumberStyle.HALF -> digits
        NumberStyle.FULL -> buildString(digits.length) { digits.forEach { append(it.toFullWidthChar()) } }
        NumberStyle.KANJI -> value.toKanji()
        NumberStyle.MIXED -> value.convertToKanjiNotation()
        NumberStyle.COMMA -> value.toString().reversed().chunked(3).joinToString(",").reversed()
    } + counter

    fun renderings(): Map<String, NumberStyle> = renderingStyles
}

/** Only anchored readings are decoded. Ordinary kana are never globally replaced. */
internal object NumberCandidateProvider {
    private val counters = listOf(
        CounterReading("えん", "円"), CounterReading("にん", "人"),
        CounterReading("ほん", "本"), CounterReading("ぼん", "本"), CounterReading("ぽん", "本"),
        CounterReading("まい", "枚"), CounterReading("にち", "日"), CounterReading("にちかん", "日間"),
        CounterReading("こ", "個"), CounterReading("ひき", "匹"), CounterReading("びき", "匹"), CounterReading("ぴき", "匹"),
        CounterReading("さつ", "冊"), CounterReading("はい", "杯"), CounterReading("ばい", "杯"), CounterReading("ぱい", "杯"),
        CounterReading("かい", "回", 2014), CounterReading("かい", "階", 2018), CounterReading("がい", "階", 2018),
        CounterReading("ねん", "年"), CounterReading("ねんかん", "年間"), CounterReading("がつ", "月", 2016),
        CounterReading("かげつ", "か月"), CounterReading("さい", "歳"),
        CounterReading("ふん", "分"), CounterReading("ぷん", "分"), CounterReading("じ", "時", 2015),
        CounterReading("じかん", "時間"), CounterReading("びょう", "秒"), CounterReading("めい", "名"),
    ).sortedByDescending { it.reading.length }
    private val days = mapOf(
        "ついたち" to 1L, "ふつか" to 2L, "みっか" to 3L, "よっか" to 4L, "いつか" to 5L,
        "むいか" to 6L, "なのか" to 7L, "ようか" to 8L, "ここのか" to 9L, "とおか" to 10L,
        "じゅうよっか" to 14L, "はつか" to 20L, "にじゅうよっか" to 24L,
    )
    private val irregular = buildMap {
        days.forEach { (reading, value) ->
            put(reading, ParsedNumber(value, "日", 2011))
            if (reading != "ついたち") put(reading + "かん", ParsedNumber(value, "日間", 2011))
        }
        put("ひとり", ParsedNumber(1, "人", 2011))
        put("ふたり", ParsedNumber(2, "人", 2011))
        put("はたち", ParsedNumber(20, "歳", 2011))
    }
    private val irregularReadings = irregular.keys.sortedByDescending { it.length }
    private val aliases = mapOf("ヶ月" to "か月", "箇月" to "か月", "カ月" to "か月", "ケ月" to "か月", "か月" to "か月")
    internal val counterSurfaces = counters.mapTo(hashSetOf()) { it.surface } + aliases.keys + "間"
    private val orderedCounterSurfaces = counterSurfaces.sortedByDescending { it.length }
    fun counterIdentity(surface: String): String = aliases[surface] ?: surface
    internal data class ReadingSpan(val start: Int, val end: Int, val numbers: List<ParsedNumber>, val canSupplement: Boolean = true)

    /** Scan maximal numeric runs once. A failed whole run is never retried from its suffix. */
    fun spans(input: String): List<ReadingSpan> {
        val spans = mutableListOf<ReadingSpan>()
        var start = 0
        while (start < input.length) {
            val irregularReading = irregularReadings.firstOrNull { input.startsWith(it, start) }
            if (irregularReading != null) {
                val end = start + irregularReading.length
                spans.add(ReadingSpan(start, end, listOf(irregular.getValue(irregularReading))))
                start = end
                continue
            }
            // 「…日に一万円…」 has a particle between two independent numeric units.
            if (input[start] == 'に' && spans.lastOrNull()?.end == start &&
                NumberReadingDecoder.tokenAt(input, start + 1)?.place != true) { start++; continue }
            var end = start
            var matched: ReadingSpan? = null
            var tokenCount = 0
            while (end < input.length) {
                val token = NumberReadingDecoder.tokenAt(input, end) ?: break
                end += token.text.length
                tokenCount++
                val nextToken = NumberReadingDecoder.tokenAt(input, end)
                val suffix = counters.firstOrNull { input.startsWith(it.reading, end) &&
                    (nextToken == null || nextToken.text.length < it.reading.length) }
                if (suffix != null) {
                    val suffixEnd = end + suffix.reading.length
                    val numbers = if (tokenCount <= 96) parse(input.substring(start, suffixEnd), false) else emptyList()
                    if (numbers.isNotEmpty()) matched = ReadingSpan(start, suffixEnd, numbers)
                    // Counter syllables can also be number tokens (こ / にち / じ). Consume them atomically.
                    end = suffixEnd
                    break
                }
            }
            val unsupportedBoundary = (start > 0 && input[start - 1] in ".．-−+") ||
                (end + 1 < input.length && input[end] in ".．" &&
                    (input[end + 1] in '0'..'9' || input[end + 1] in '０'..'９'))
            if (matched != null && !unsupportedBoundary) spans.add(matched)
            else if (matched == null && !unsupportedBoundary && end > start && tokenCount <= 96) {
                val numbers = parse(input.substring(start, end))
                if (numbers.isNotEmpty()) spans.add(ReadingSpan(start, end, numbers,
                    canSupplement = start == 0 && end == input.length || tokenCount > 1 ||
                        input.substring(start, end).all { it in '0'..'9' || it in '０'..'９' }))
            }
            start = if (end > start) end else start + 1
        }
        return spans
    }
    fun analyze(input: String): NumberReadingAnalysis = NumberReadingAnalysis(input,
        spans(input).flatMap { span -> span.numbers.map { number ->
            NumericSpan(span.start, span.end, NumericIdentity(number.value, number.counter, number.digits), number.rightId, span.canSupplement)
        } })

    fun parse(input: String, allowBareNumber: Boolean = true): List<ParsedNumber> {
        if (input.isEmpty()) return emptyList()
        irregular[input]?.let { return listOf(it) }
        val result = mutableListOf<ParsedNumber>()
        for (counter in counters) {
            if (!input.endsWith(counter.reading) || input.length <= counter.reading.length) continue
            val prefix = input.dropLast(counter.reading.length)
            val tokens = NumberReadingDecoder.tokens(prefix) ?: continue
            if (!NumberReadingDecoder.validInternalSounds(tokens, true)) continue
            val value = NumberReadingDecoder.value(tokens) ?: continue
            if (!validCounter(tokens.last(), counter, value)) continue
            result.add(ParsedNumber(value, counter.surface, counter.rightId, numericDigits(prefix) ?: value.toString()))
        }
        if (result.isNotEmpty()) return result.distinctBy { it.counter }
        if (!allowBareNumber) return emptyList()
        val value = NumberReadingDecoder.readingValue(input) ?: return emptyList()
        return listOf(ParsedNumber(value, digits = numericDigits(input) ?: value.toString()))
    }

    private fun numericDigits(input: String): String? = input.takeIf {
        it.isNotEmpty() && it.all { ch -> ch in '0'..'9' || ch in '０'..'９' }
    }?.map { if (it in '０'..'９') it - 0xfee0 else it }?.joinToString("")

    private fun validCounter(tail: NumberReadingDecoder.Token, counter: CounterReading, value: Long): Boolean {
        val reading = tail.text
        if (counter.surface == "月" && value !in 1..12) return false
        if (numericDigits(reading) != null) return true
        val contracted = reading in setOf("いっ", "ろっ", "はっ", "じゅっ", "じっ", "ひゃっ", "びゃっ", "ぴゃっ")
        val p = contracted
        return when (counter.surface) {
            "本", "匹", "杯" -> {
                val expected = when { p -> 2; reading == "さん" -> 1; else -> 0 }
                val actual = when (counter.reading.first()) { 'ぽ', 'ぴ', 'ぱ' -> 2; 'ぼ', 'び', 'ば' -> 1; else -> 0 }
                expected == actual && reading !in setOf("いち", "ろく", "はち", "じゅう", "じゅー", "し", "よ", "く")
            }
            "分" -> when (counter.reading) {
                "ぷん" -> (contracted || reading in setOf("さん", "よん")) && reading !in setOf("いち", "ろく", "はち", "じゅう")
                else -> !contracted && reading !in setOf("いち", "さん", "し", "よ", "よん", "ろく", "じゅう", "じゅー", "く")
            }
            "回", "階", "個", "か月" -> reading !in setOf("いち", "ろく", "じゅう", "じゅー", "し", "よ", "く") &&
                (counter.reading != "がい" || reading == "さん") &&
                (reading != "はち" || counter.surface in setOf("階", "か月"))
            "冊", "歳" -> (!contracted || reading in setOf("いっ", "はっ", "じゅっ", "じっ")) && reading !in setOf("いち", "はち", "じゅう", "じゅー", "ろっ", "し", "よ", "く")
            "月" -> !contracted && when (value) {
                4L -> reading == "し"
                7L -> reading == "しち"
                9L -> reading == "く"
                else -> reading !in setOf("し", "よ", "く")
            }
            "時", "時間" -> !contracted && when (value % 10) {
                4L -> reading == "よ"
                7L -> reading in setOf("しち", "なな")
                9L -> reading == "く"
                else -> reading !in setOf("し", "よ", "く")
            }
            "日", "日間" -> !contracted && value !in setOf(2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 14L, 20L, 24L) &&
                reading !in setOf("し", "よ")
            "人" -> !contracted && value !in 1..2 && reading !in setOf("し", "よん", "く")
            "円" -> !contracted && reading !in setOf("し", "く")
            else -> !contracted && reading !in setOf("し", "よ", "く")
        }
    }

    /** Match by value, preserving the dictionary's counter spelling. */
    fun matchSurface(text: String, number: ParsedNumber): ParsedNumber? {
        val suffix = if (number.counter.isEmpty()) "" else orderedCounterSurfaces.firstOrNull {
            text.endsWith(it) && counterIdentity(it) == counterIdentity(number.counter)
        } ?: return null
        val numeric = text.dropLast(suffix.length)
        if (',' in numeric && !commaNumber.matches(numeric)) return null
        val value = NumberReadingDecoder.surfaceValue(numeric.replace(",", "")) ?: return null
        return if (value == number.value) number.copy(counter = suffix) else null
    }
    private val commaNumber = Regex("[0-9０-９]{1,3}(,[0-9０-９]{3})+")
    fun styleOf(text: String, number: ParsedNumber): NumberStyle {
        val numeric = text.dropLast(number.counter.length)
        return when {
            numeric.any { it in '０'..'９' } && numeric.none { it in '0'..'9' } -> NumberStyle.FULL
            ',' in numeric -> NumberStyle.COMMA
            numeric.any { it in '0'..'9' } -> if (numeric.any { it in "十百千万億兆京" }) NumberStyle.MIXED else NumberStyle.HALF
            else -> NumberStyle.KANJI
        }
    }
}
