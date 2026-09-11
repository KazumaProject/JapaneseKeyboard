package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toNumber

/**
 * The permissive number decoder also accepts fragments such as ぜん and じっし.
 * Counter candidates need a complete cardinal reading, with sound changes in the
 * correct position, so ordinary words such as ぜんにん and じっしじ stay lexical.
 */
internal object JapaneseNumberCounterReading {
    private val ones = "(?:いち|に|さん|よん|ご|ろく|なな|しち|はち|きゅう)"
    private val tens = "(?:じゅう|にじゅう|さんじゅう|よんじゅう|しじゅう|ごじゅう|ろくじゅう|ななじゅう|しちじゅう|はちじゅう|きゅうじゅう)"
    private val hundreds = "(?:ひゃく|にひゃく|さんびゃく|よんひゃく|ごひゃく|ろっぴゃく|ななひゃく|しちひゃく|はっぴゃく|きゅうひゃく)"
    private val thousands = "(?:せん|いっせん|にせん|さんぜん|よんせん|ごせん|ろくせん|ななせん|しちせん|はっせん|きゅうせん)"
    private val section = "(?:${thousands}${hundreds}?${tens}?${ones}?|${hundreds}${tens}?${ones}?|${tens}${ones}?|${ones})"
    private val cardinal = Regex("(?:(?:${section}ちょう)?(?:${section}おく)?(?:${section}まん)?${section}?|ぜろ|れい)")

    // Standard minute readings include よんふん/よんぷん and はちふん/はっぷん.
    // Reference: https://www.jpf.or.kr/irodori/sData/pdf/resources/wordlist_X.pdf
    private val funEndings = listOf("に", "さん", "よん", "ご", "なな", "しち", "はち", "きゅう", "ぜろ", "れい", "おく", "ちょう")
    private val punEndings = listOf("さん", "よん", "せん", "ぜん", "まん")
    private val minuteContractions = listOf(
        "じゅっ" to "じゅう", "じっ" to "じゅう",
        "ひゃっ" to "ひゃく", "びゃっ" to "びゃく", "ぴゃっ" to "ぴゃく",
        "いっ" to "いち", "ろっ" to "ろく", "はっ" to "はち", "おっ" to "おく",
    )

    fun parse(numberReading: String, counterReading: String): Pair<String, String>? {
        if (numberReading.isEmpty()) return null
        var normalized = when (counterReading) {
            "ふん" -> if (funEndings.any(numberReading::endsWith)) numberReading else return null
            "ぷん" -> {
                val contraction = minuteContractions.firstOrNull { numberReading.endsWith(it.first) }
                when {
                    contraction != null -> numberReading.dropLast(contraction.first.length) + contraction.second
                    punEndings.any(numberReading::endsWith) -> numberReading
                    else -> return null
                }
            }
            "じ", "にん", "えん" -> numberReading
            else -> return null
        }
        // These contractions occur before a large place, not at arbitrary positions.
        normalized = normalized.replace("いっちょう", "いちちょう")
            .replace("はっちょう", "はちちょう")
        if (!cardinal.matches(normalized) && counterReading in listOf("じ", "にん")) {
            normalized = when {
                normalized.endsWith("よ") -> normalized.dropLast(1) + "よん"
                normalized.endsWith("く") -> normalized.dropLast(1) + "きゅう"
                else -> return null
            }
        }
        if (!cardinal.matches(normalized)) return null
        val number = normalized.toNumber() ?: return null
        // 時 is a clock-hour suffix; allow late-night notation through 29時.
        // Durations in 分 and counts in 人/円 are not clock-hour bounded.
        if (counterReading == "じ" && number.second.toLong() !in 0L..29L) return null
        return number
    }
}
