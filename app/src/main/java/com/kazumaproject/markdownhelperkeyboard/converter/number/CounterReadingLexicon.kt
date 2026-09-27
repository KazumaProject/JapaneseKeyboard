package com.kazumaproject.markdownhelperkeyboard.converter.number

import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toNumber

/**
 * Explicit readings for the supported counter fallback. The base forms are a closed reviewed
 * table cross-checked against Japan Foundation teaching materials and the University of Texas
 * Japanese Online Self-Help Utility. Duration and date forms are checked separately. Broader
 * numeric suffixes are composed only when their final sound has no listed counter change.
 *
 * Sources:
 * - https://www.kyozai.jpf.go.jp/kyozai/material/KML00038/ja/render.do (counter readings for 1–10)
 * - https://www.kyozai.jpf.go.jp/kyozai/material/BTS00010/ja/render.do
 * - https://www.jpf.go.jp/j/urawa/j_rsorcs/textbook/dl/setsumei/setsumei_all.pdf (pp. 268–270)
 * - https://laits.utexas.edu/japanese/joshu/counters/counters_category.php
 * - https://ncu.repo.nii.ac.jp/record/1536/files/B421-19980331-41.pdf (documented 三足 reading variation)
 * - https://www.kyozai.jpf.go.jp/kyozai/material/DNE00012/ja/render.do
 * - https://marugoto.jpf.go.jp/assets/docs/download/elementary1_c/MarugotoElementary1CompetencesVocabularyIndex_EN.pdf
 * - https://www.bunka.go.jp/seisaku/kokugo_nihongo/kyoiku/seikatsusha/h25_nihongo_program_a/pdf/a_35.pdf
 * - https://www.coelang.tufs.ac.jp/ja/zt/gmod/contents/exercises/019.html
 * - https://www.kyozai.jpf.go.jp/kyozai/material/BTS00020/ja/render.do
 */
object CounterReadingLexicon {
    data class Reading(
        val value: Int,
        val counter: String,
        val reading: String,
        val interpretation: String = counter,
    )

    data class Suffix(
        val counter: String,
        val reading: String,
        val interpretation: String = counter,
        /** Final number digits whose sound changes the counter; plain suffix composition is unsafe. */
        val blockedCompoundLastDigits: Set<Int> = emptySet(),
        /** Calendar months stop at 12; longer durations use か月 instead. */
        val maximumCompoundValue: Long? = null,
    )

    private val rows = listOf(
        "つ" to listOf("ひとつ", "ふたつ", "みっつ", "よっつ", "いつつ", "むっつ", "ななつ", "やっつ", "ここのつ", "とお"),
        "人" to listOf("ひとり", "ふたり", "さんにん", "よにん", "ごにん", "ろくにん", "ななにん", "はちにん", "きゅうにん", "じゅうにん"),
        "個" to listOf("いっこ", "にこ", "さんこ", "よんこ", "ごこ", "ろっこ", "ななこ", "はっこ", "きゅうこ", "じゅっこ"),
        "枚" to listOf("いちまい", "にまい", "さんまい", "よんまい", "ごまい", "ろくまい", "ななまい", "はちまい", "きゅうまい", "じゅうまい"),
        "冊" to listOf("いっさつ", "にさつ", "さんさつ", "よんさつ", "ごさつ", "ろくさつ", "ななさつ", "はっさつ", "きゅうさつ", "じゅっさつ"),
        "本" to listOf("いっぽん", "にほん", "さんぼん", "よんほん", "ごほん", "ろっぽん", "ななほん", "はっぽん", "きゅうほん", "じゅっぽん"),
        "匹" to listOf("いっぴき", "にひき", "さんびき", "よんひき", "ごひき", "ろっぴき", "ななひき", "はっぴき", "きゅうひき", "じゅっぴき"),
        "杯" to listOf("いっぱい", "にはい", "さんばい", "よんはい", "ごはい", "ろっぱい", "ななはい", "はっぱい", "きゅうはい", "じゅっぱい"),
        "頭" to listOf("いっとう", "にとう", "さんとう", "よんとう", "ごとう", "ろくとう", "ななとう", "はっとう", "きゅうとう", "じゅっとう"),
        "台" to listOf("いちだい", "にだい", "さんだい", "よんだい", "ごだい", "ろくだい", "ななだい", "はちだい", "きゅうだい", "じゅうだい"),
        "着" to listOf("いっちゃく", "にちゃく", "さんちゃく", "よんちゃく", "ごちゃく", "ろくちゃく", "ななちゃく", "はっちゃく", "きゅうちゃく", "じゅっちゃく"),
        "足" to listOf("いっそく", "にそく", "さんそく", "よんそく", "ごそく", "ろくそく", "ななそく", "はっそく", "きゅうそく", "じゅっそく"),
        "回" to listOf("いっかい", "にかい", "さんかい", "よんかい", "ごかい", "ろっかい", "ななかい", "はっかい", "きゅうかい", "じゅっかい"),
        "階" to listOf("いっかい", "にかい", "さんかい", "よんかい", "ごかい", "ろっかい", "ななかい", "はっかい", "きゅうかい", "じゅっかい"),
        "番" to listOf("いちばん", "にばん", "さんばん", "よんばん", "ごばん", "ろくばん", "ななばん", "はちばん", "きゅうばん", "じゅうばん"),
        "度" to listOf("いちど", "にど", "さんど", "よんど", "ごど", "ろくど", "ななど", "はちど", "きゅうど", "じゅうど"),
        "件" to listOf("いっけん", "にけん", "さんけん", "よんけん", "ごけん", "ろっけん", "ななけん", "はっけん", "きゅうけん", "じゅっけん"),
        "軒" to listOf("いっけん", "にけん", "さんげん", "よんけん", "ごけん", "ろっけん", "ななけん", "はっけん", "きゅうけん", "じゅっけん"),
        "点" to listOf("いってん", "にてん", "さんてん", "よんてん", "ごてん", "ろくてん", "ななてん", "はってん", "きゅうてん", "じゅってん"),
        "通" to listOf("いっつう", "につう", "さんつう", "よんつう", "ごつう", "ろくつう", "ななつう", "はっつう", "きゅうつう", "じゅっつう"),
        "発" to listOf("いっぱつ", "にはつ", "さんぱつ", "よんぱつ", "ごはつ", "ろっぱつ", "ななはつ", "はっぱつ", "きゅうはつ", "じゅっぱつ"),
        "泊" to listOf("いっぱく", "にはく", "さんぱく", "よんぱく", "ごはく", "ろっぱく", "ななはく", "はっぱく", "きゅうはく", "じゅっぱく"),
        "時" to listOf("いちじ", "にじ", "さんじ", "よじ", "ごじ", "ろくじ", "しちじ", "はちじ", "くじ", "じゅうじ"),
        "分" to listOf("いっぷん", "にふん", "さんぷん", "よんぷん", "ごふん", "ろっぷん", "ななふん", "はちふん", "きゅうふん", "じゅっぷん"),
        "秒" to listOf("いちびょう", "にびょう", "さんびょう", "よんびょう", "ごびょう", "ろくびょう", "ななびょう", "はちびょう", "きゅうびょう", "じゅうびょう"),
        "時間" to listOf("いちじかん", "にじかん", "さんじかん", "よじかん", "ごじかん", "ろくじかん", "しちじかん", "はちじかん", "くじかん", "じゅうじかん"),
        "日" to listOf("ついたち", "ふつか", "みっか", "よっか", "いつか", "むいか", "なのか", "ようか", "ここのか", "とおか"),
        "月" to listOf("いちがつ", "にがつ", "さんがつ", "しがつ", "ごがつ", "ろくがつ", "しちがつ", "はちがつ", "くがつ", "じゅうがつ"),
        "年" to listOf("いちねん", "にねん", "さんねん", "よねん", "ごねん", "ろくねん", "ななねん", "はちねん", "きゅうねん", "じゅうねん"),
        "歳" to listOf("いっさい", "にさい", "さんさい", "よんさい", "ごさい", "ろくさい", "ななさい", "はっさい", "きゅうさい", "じゅっさい"),
        "か月" to listOf("いっかげつ", "にかげつ", "さんかげつ", "よんかげつ", "ごかげつ", "ろっかげつ", "ななかげつ", "はっかげつ", "きゅうかげつ", "じゅっかげつ"),
        "週間" to listOf("いっしゅうかん", "にしゅうかん", "さんしゅうかん", "よんしゅうかん", "ごしゅうかん", "ろくしゅうかん", "ななしゅうかん", "はっしゅうかん", "きゅうしゅうかん", "じゅっしゅうかん"),
        "円" to listOf("いちえん", "にえん", "さんえん", "よえん", "ごえん", "ろくえん", "ななえん", "はちえん", "きゅうえん", "じゅうえん"),
        "グラム" to listOf("いちぐらむ", "にぐらむ", "さんぐらむ", "よんぐらむ", "ごぐらむ", "ろくぐらむ", "ななぐらむ", "はちぐらむ", "きゅうぐらむ", "じゅうぐらむ"),
        "メートル" to listOf("いちめーとる", "にめーとる", "さんめーとる", "よんめーとる", "ごめーとる", "ろくめーとる", "ななめーとる", "はちめーとる", "きゅうめーとる", "じゅうめーとる"),
    )

    private val suffixes = listOf(
        Suffix("人", "にん"),
        Suffix("個", "こ", blockedCompoundLastDigits = setOf(1, 6, 8, 0)),
        Suffix("枚", "まい"),
        Suffix("冊", "さつ", blockedCompoundLastDigits = setOf(1, 8, 0)),
        Suffix("本", "ほん", blockedCompoundLastDigits = setOf(1, 3, 6, 8, 0)),
        Suffix("匹", "ひき", blockedCompoundLastDigits = setOf(1, 3, 6, 8, 0)),
        Suffix("杯", "はい", blockedCompoundLastDigits = setOf(1, 3, 6, 8, 0)),
        Suffix("頭", "とう", blockedCompoundLastDigits = setOf(1, 8, 0)),
        Suffix("台", "だい"),
        Suffix("着", "ちゃく", blockedCompoundLastDigits = setOf(1, 8, 0)),
        // The reviewed 1–10 table gives さんそく; do not assume that sound carries into 13.
        Suffix("足", "そく", blockedCompoundLastDigits = setOf(1, 3, 8, 0)),
        Suffix("回", "かい", blockedCompoundLastDigits = setOf(1, 6, 8, 0)),
        Suffix("階", "かい", blockedCompoundLastDigits = setOf(1, 6, 8, 0)),
        Suffix("番", "ばん"), Suffix("度", "ど"),
        Suffix("件", "けん", blockedCompoundLastDigits = setOf(1, 6, 8, 0)),
        Suffix("軒", "けん", blockedCompoundLastDigits = setOf(1, 3, 6, 8, 0)),
        Suffix("点", "てん", blockedCompoundLastDigits = setOf(1, 8, 0)),
        Suffix("通", "つう", blockedCompoundLastDigits = setOf(1, 8, 0)),
        Suffix("発", "はつ", blockedCompoundLastDigits = setOf(1, 3, 6, 8, 0)),
        Suffix("泊", "はく", blockedCompoundLastDigits = setOf(1, 3, 6, 8, 0)),
        Suffix("時", "じ", "時刻"),
        Suffix("分", "ふん", "時間", blockedCompoundLastDigits = setOf(1, 3, 4, 6, 8, 0)),
        Suffix("秒", "びょう", "時間"),
        Suffix("時間", "じかん", "時間"),
        // Calendar days ending in 4 and 0 have dedicated readings (よっか/はつか).
        Suffix("日", "にち", "日数", blockedCompoundLastDigits = setOf(0, 4)),
        Suffix("月", "がつ", "月", maximumCompoundValue = 12), Suffix("年", "ねん", "年数"),
        Suffix("歳", "さい", "年齢", blockedCompoundLastDigits = setOf(1, 8, 0)),
        Suffix("か月", "かげつ", "月数", blockedCompoundLastDigits = setOf(1, 6, 8, 0)),
        Suffix("週間", "しゅうかん", "週数", blockedCompoundLastDigits = setOf(1, 8, 0)),
        Suffix("円", "えん", "金額"),
        Suffix("グラム", "ぐらむ", "重量"), Suffix("メートル", "めーとる", "長さ"),
    ).sortedByDescending { it.reading.length }
    private val suffixesByLastKana: Map<Char, List<Suffix>> =
        suffixes.groupBy { it.reading.last() }

    val readings: List<Reading> = buildList {
        rows.forEach { (counter, values) ->
            values.forEachIndexed { index, reading ->
                add(Reading(index + 1, counter, reading))
            }
        }
        // The official guide distinguishes a calendar date from a duration: one day is
        // "いちにち", while the first day of a month is "ついたち".
        (listOf("いちにち", "ふつか", "みっか", "よっか", "いつか", "むいか", "なのか", "ようか", "ここのか", "とおか"))
            .forEachIndexed { index, reading -> add(Reading(index + 1, "日", reading, "日数")) }
        add(Reading(3, "階", "さんがい"))
        // Japan Foundation material lists さんそく for 3足; a phonology study documents the
        // voiced さんぞく variant as well. Both map to the same counter meaning.
        add(Reading(3, "足", "さんぞく"))
        add(Reading(7, "人", "しちにん"))
        add(Reading(9, "人", "くにん"))
        add(Reading(14, "日", "じゅうよっか", "日数"))
        add(Reading(20, "日", "はつか", "日数"))
        add(Reading(24, "日", "にじゅうよっか", "日数"))
        add(Reading(20, "分", "にじゅっぷん", "時間"))
        add(Reading(8, "分", "はっぷん", "時間"))
        add(Reading(8, "か月", "はちかげつ", "月数"))
        add(Reading(8, "か月", "はっかげつ", "月数"))
    }

    private val readingsBySurface: Map<String, List<Reading>> = readings.groupBy { it.reading }

    init {
        require(rows.size == 35)
        require(rows.all { it.second.size == 10 })
        require(readings.distinct().size == readings.size)
    }

    fun matchAll(reading: String): List<Reading> = readingsBySurface[reading].orEmpty()

    fun suffixMatches(reading: String): List<Pair<String, Suffix>> =
        suffixesByLastKana[reading.lastOrNull()].orEmpty().mapNotNull { suffix ->
        if (reading.length <= suffix.reading.length || !reading.endsWith(suffix.reading)) {
            null
        } else {
            val numberReading = reading.removeSuffix(suffix.reading)
            // Simple readings are listed explicitly above. Only compose a reading when a place
            // value makes it unambiguous; this avoids inventing forms such as しにん or いちひき.
            val compound = listOf("じゅう", "ひゃく", "せん", "まん", "おく", "ちょう", "けい")
                .any(numberReading::contains)
            val numericValue = numberReading.toNumber()?.second?.toLongOrNull()
            if (
                !compound ||
                numberReading.endsWith("し") ||
                numericValue == null ||
                suffix.maximumCompoundValue?.let { numericValue > it } == true ||
                (numericValue % 10).toInt() in suffix.blockedCompoundLastDigits
            ) {
                null
            } else {
                numberReading to suffix
            }
        }
    }

    /**
     * Finds a counter reading at a plausible bunsetsu boundary, such as さんまいとにまい.
     * Substring matching alone would treat ordinary words containing a counter's sound as
     * numeric input and trigger unnecessary N-best expansion.
     */
    fun hasCounterReadingWithin(input: String): Boolean {
        for (reading in readings) {
            var start = input.indexOf(reading.reading)
            while (start >= 0) {
                val end = start + reading.reading.length
                if (NumberReadingBoundary.isBoundary(input, start, end)) return true
                start = input.indexOf(reading.reading, start + 1)
            }
        }
        for (suffix in suffixes) {
            var suffixStart = input.indexOf(suffix.reading)
            while (suffixStart >= 0) {
                val suffixEnd = suffixStart + suffix.reading.length
                if (!NumberReadingBoundary.isEndBoundary(input, suffixEnd)) {
                    suffixStart = input.indexOf(suffix.reading, suffixStart + 1)
                    continue
                }
                for (numberStart in 0 until suffixStart) {
                    if (!NumberReadingBoundary.isStartBoundary(input, numberStart)) continue
                    val completeReading = input.substring(numberStart, suffixEnd)
                    if (
                        suffixMatches(completeReading).any { it.second == suffix } &&
                        !completeReading.removeSuffix(suffix.reading).endsWith("し")
                    ) {
                        return true
                    }
                }
                suffixStart = input.indexOf(suffix.reading, suffixStart + 1)
            }
        }
        return false
    }

    val counterSurfaces: Set<String> = rows.mapTo(LinkedHashSet()) { it.first }
}
