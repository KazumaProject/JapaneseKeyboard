package com.kazumaproject.markdownhelperkeyboard.converter.number

import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.japaneseNumberReadingTokens
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertToKanjiNotation
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toFullWidthChar
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toJapaneseNumberValue
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
    }
    internal val counterSurfaces = counters.mapTo(hashSetOf()) { it.surface } + "間"
    private val placeEndings = listOf("じゅう", "ひゃく", "せん", "まん", "おく", "ちょう")

    /** Avoid collecting paths for ordinary words that merely contain a counter syllable. */
    fun mightContainCounter(input: String): Boolean {
        if (irregular.keys.any(input::contains)) return true
        for (start in input.indices) {
            var end = start
            while (end < input.length) {
                if (input[end] in '0'..'9' || input[end] in '０'..'９') {
                    do { end++ } while (end < input.length && (input[end] in '0'..'9' || input[end] in '０'..'９'))
                } else {
                    val token = japaneseNumberReadingTokens.firstOrNull { input.startsWith(it, end) } ?: break
                    end += token.length
                }
                if (counters.any { input.startsWith(it.reading, end) }) return true
            }
        }
        return false
    }

    fun parse(input: String, allowBareNumber: Boolean = true): List<ParsedNumber> {
        if (input.isEmpty()) return emptyList()
        irregular[input]?.let { return listOf(it) }
        val result = mutableListOf<ParsedNumber>()
        for (counter in counters) {
            if (!input.endsWith(counter.reading) || input.length <= counter.reading.length) continue
            val prefix = input.dropLast(counter.reading.length)
            val normalized = normalize(prefix, counter.reading) ?: continue
            val value = value(normalized) ?: continue
            result.add(ParsedNumber(value, counter.surface, counter.rightId, numericDigits(normalized) ?: value.toString()))
        }
        if (result.isNotEmpty()) return result.distinctBy { it.counter }
        if (!allowBareNumber) return emptyList()
        val value = value(input) ?: return emptyList()
        return listOf(ParsedNumber(value, digits = numericDigits(input) ?: value.toString()))
    }

    private fun numericDigits(input: String): String? = input.takeIf {
        it.isNotEmpty() && it.all { ch -> ch in '0'..'9' || ch in '０'..'９' }
    }?.map { if (it in '０'..'９') it - 0xfee0 else it }?.joinToString("")

    private fun value(input: String): Long? = numericDigits(input)?.toLongOrNull() ?: input.toJapaneseNumberValue()

    private fun normalize(reading: String, counter: String): String? {
        fun terminal(alias: String) = reading == alias ||
            (reading.endsWith(alias) && placeEndings.any { reading.dropLast(alias.length).endsWith(it) })
        if (terminal("し")) return null
        return when {
            counter == "じ" && terminal("よ") -> reading.dropLast(1) + "よん"
            counter == "じ" && terminal("く") -> reading.dropLast(1) + "きゅう"
            else -> reading
        }
    }
}
