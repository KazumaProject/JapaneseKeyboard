package com.kazumaproject.markdownhelperkeyboard.converter.number

import org.junit.Assert.*
import org.junit.Test

class NumberReadingDecoderTest {
    @Test fun standardCounterReadingsValidateInternalAndTerminalSoundsSeparately() {
        val valid = mapOf("しがつ" to 4L, "しちがつ" to 7L, "くがつ" to 9L, "はたち" to 20L,
            "よえん" to 4L, "よにん" to 4L, "きゅうにん" to 9L, "にじっぷん" to 20L,
            "にじゅっぷん" to 20L, "はちふん" to 8L, "はっぷん" to 8L,
            "いっけいえん" to 10000000000000000L,
            "さんびゃくろくじゅうごにち" to 365L, "さんぜんえん" to 3000L)
        valid.forEach { (reading, value) -> assertEquals(reading, value, NumberCandidateProvider.parse(reading).single().value) }
        val invalid = listOf("いっえん", "いっにん", "いちにん", "ににん", "ににち", "ににちかん", "にじゅうにち", "ろっにん", "はっがつ", "じゅっにん",
            "いちぼん", "さんぽん", "ろくぼん", "にじゅっふん", "さんふん", "くにん", "くえん",
            "にびゃくえん", "よんぜんえん", "はちひゃくえん", "さんひゃく", "はちせん",
            "にじゅうがつ", "いちじゅうえん", "いちひゃくえん", "じゅう20えん", "1まん20000えん", "1おく20000まんえん", "にがい", "ひゃっさつ", "いちいち", "いちまんにまんえん")
        invalid.forEach { assertTrue(it, NumberCandidateProvider.parse(it).isEmpty()) }
    }
    @Test fun allCountersAcceptCanonicalReadingsAcrossDigitAndPlaceBoundaries() {
        val numbers = listOf(
            0L to "れい", 1L to "いち", 2L to "に", 3L to "さん", 4L to "よん",
            5L to "ご", 6L to "ろく", 7L to "なな", 8L to "はち", 9L to "きゅう",
            10L to "じゅう", 11L to "じゅういち", 12L to "じゅうに", 20L to "にじゅう",
            100L to "ひゃく", 200L to "にひゃく", 300L to "さんびゃく",
            600L to "ろっぴゃく", 800L to "はっぴゃく", 1000L to "せん",
            3000L to "さんぜん", 8000L to "はっせん", 10000L to "いちまん",
            100000L to "じゅうまん", 100000000L to "いちおく",
            1000000000000L to "いっちょう", 10000000000000000L to "いっけい",
        )
        val contracted = mapOf(1L to "いっ", 6L to "ろっ", 8L to "はっ", 10L to "じゅっ",
            11L to "じゅういっ", 20L to "にじゅっ", 100L to "ひゃっ", 200L to "にひゃっ",
            300L to "さんびゃっ", 600L to "ろっぴゃっ", 800L to "はっぴゃっ")
        val voiced = setOf(3L, 1000L, 3000L, 8000L, 10000L, 100000L)
        val cases = mutableListOf<Triple<String, Long, String>>()
        fun add(reading: String, value: Long, surface: String) { cases.add(Triple(reading, value, surface)) }
        for ((value, reading) in numbers) {
            for ((suffix, surface) in mapOf("えん" to "円", "まい" to "枚", "ねん" to "年",
                "ねんかん" to "年間", "びょう" to "秒", "めい" to "名")) add(reading + suffix, value, surface)
            val people = when (value) { 1L -> "ひとり"; 2L -> "ふたり"; 4L -> "よにん"; else -> reading + "にん" }
            add(people, value, "人")
            for ((plain, voicedSuffix, contractedSuffix, surface) in listOf(
                listOf("ほん", "ぼん", "ぽん", "本"), listOf("ひき", "びき", "ぴき", "匹"),
                listOf("はい", "ばい", "ぱい", "杯"))) {
                val prefix = contracted[value] ?: reading
                val suffix = when { value in contracted -> contractedSuffix; value in voiced -> voicedSuffix; else -> plain }
                add(prefix + suffix, value, surface)
            }
            add((contracted[value] ?: reading) + if (value in contracted || value in voiced || value == 4L) "ぷん" else "ふん", value, "分")
            for ((suffix, surface) in mapOf("かい" to "回", "こ" to "個", "かげつ" to "か月"))
                add((contracted[value] ?: reading) + suffix, value, surface)
            add((contracted[value] ?: reading) + "かい", value, "階")
            val sPrefix = if (value in setOf(1L, 8L, 10L, 11L, 20L)) contracted.getValue(value) else reading
            add(sPrefix + "さつ", value, "冊")
            add(if (value == 20L) "はたち" else sPrefix + "さい", value, "歳")
            val time = if (value % 10 == 4L) reading.removeSuffix("よん") + "よ"
                else if (value % 10 == 9L) reading.removeSuffix("きゅう") + "く" else reading
            add(time + "じ", value, "時")
            add(time + "じかん", value, "時間")
        }
        listOf("いち", "に", "さん", "し", "ご", "ろく", "しち", "はち", "く", "じゅう", "じゅういち", "じゅうに")
            .forEachIndexed { index, reading -> add(reading + "がつ", (index + 1).toLong(), "月") }
        val days = listOf("いちにち", "ふつか", "みっか", "よっか", "いつか", "むいか", "なのか", "ようか",
            "ここのか", "とおか", "じゅういちにち", "じゅうににち", "じゅうさんにち", "じゅうよっか",
            "じゅうごにち", "じゅうろくにち", "じゅうしちにち", "じゅうはちにち", "じゅうくにち", "はつか",
            "にじゅういちにち", "にじゅうににち", "にじゅうさんにち", "にじゅうよっか", "にじゅうごにち",
            "にじゅうろくにち", "にじゅうしちにち", "にじゅうはちにち", "にじゅうくにち", "さんじゅうにち", "さんじゅういちにち")
        days.forEachIndexed { index, reading ->
            add(reading, (index + 1).toLong(), "日")
            add(reading + "かん", (index + 1).toLong(), "日間")
        }
        val failures = cases.filter { (reading, value, surface) ->
            NumberCandidateProvider.parse(reading).none { it.value == value && it.counter == surface }
        }
        assertEquals("Canonical counter readings rejected: $failures", emptyList<Triple<String, Long, String>>(), failures)
        for (reading in listOf("せんぽん", "さんぜんぴき", "いちまんぱい", "せんがつ", "せんっぷん", "ぜんぷん"))
            assertTrue(reading, NumberCandidateProvider.parse(reading).isEmpty())
    }

    @Test fun literalMagnitudeMixesAndLongRangeUseCheckedArithmetic() {
        val valid = mapOf("1まんえん" to 10000L, "１まんえん" to 10000L,
            "12おく3せんまん4えん" to 1230000004L,
            "9223372036854775807えん" to Long.MAX_VALUE, "０００２えん" to 2L)
        valid.forEach { (reading, value) -> assertEquals(reading, value, NumberCandidateProvider.parse(reading).single().value) }
        assertEquals("0002", NumberCandidateProvider.parse("０００２えん").single().digits)
        val grouped = NumberCandidateProvider.parse("１，２３４えん").single()
        assertEquals(1234L, NumberCandidateProvider.matchSurface("１，２３４円", grouped)?.value)
        listOf("9223372036854775808えん", "1000けいえん", "1おく2ちょうえん", "-2えん", "1.5えん")
            .forEach { assertTrue(it, NumberCandidateProvider.parse(it).isEmpty()) }
    }
    @Test fun semanticSurfaceParsingConservesValueAndCounterAliases() {
        val minutes = NumberCandidateProvider.parse("にじゅっぷん").single()
        assertEquals(20L, NumberCandidateProvider.matchSurface("2十分", minutes)?.value)
        assertNull(NumberCandidateProvider.matchSurface("210分", minutes))
        val financial = NumberCandidateProvider.parse("さんじゅっぷん").single()
        assertEquals(30L, NumberCandidateProvider.matchSurface("参拾分", financial)?.value)
        val months = NumberCandidateProvider.parse("にかげつ").single()
        for (suffix in listOf("か月", "ヶ月", "箇月", "カ月", "ケ月")) {
            assertEquals(suffix, NumberCandidateProvider.matchSurface("二$suffix", months)?.counter)
        }
        for (value in listOf(0L, 1L, 20L, 10000L, 123456789L, Long.MAX_VALUE)) {
            val number = ParsedNumber(value)
            for (style in NumberStyle.entries.filterNot { it == NumberStyle.COMMA }) {
                assertEquals("$value/$style", value, NumberReadingDecoder.surfaceValue(number.render(style)))
            }
        }
    }
    @Test fun reviewedSoundZeroAndSeparatorRegressions() {
        listOf("にびゃっぷん", "さんひゃっこ", "さんぴゃっぷん", "じゅうぜろえん", "ひゃくれいえん", "いちまんぜろえん")
            .forEach { assertTrue(it, NumberCandidateProvider.parse(it).isEmpty()) }
        mapOf("にひゃっこ" to 200L, "さんびゃっぷん" to 300L, "ろっぴゃっぷん" to 600L,
            "1,000えん" to 1000L, "１，２３４えん" to 1234L, "ぜろえん" to 0L)
            .forEach { (input,value) -> assertEquals(input,value,NumberCandidateProvider.parse(input).single().value) }
        listOf("1,00えん", "1,,000えん", "－２えん", "＋２えん", "-ついたち", "-ふたり", "+はつか", "1.5えん", "１．５えん")
            .forEach { assertTrue(it,NumberCandidateProvider.spans(it).isEmpty()) }
        listOf("に", "し", "ご", "よ", "く").forEach { assertTrue(it,NumberCandidateProvider.spans(it).none { span -> span.canSupplement }) }
    }
    @Test fun maximalInvalidRunsNeverProduceNumericSuffixes() {
        for (input in listOf("にじゅっふんまつ", "いちまんにまんえんはらう", "9".repeat(1000) + "えん", "に".repeat(1000), "0".repeat(1000) + "1えん", "-2えん", "1.5えん")) {
            assertTrue(input.take(40), NumberCandidateProvider.spans(input).isEmpty())
        }
        assertTrue(NumberCandidateProvider.spans("きょうにいく").none { it.canSupplement })
        assertTrue(NumberCandidateProvider.spans("まごにあう").none { it.canSupplement })
        val spans = NumberCandidateProvider.spans("はつかにいちまんえんはらう")
        assertEquals(listOf(0 to 3, 4 to 10), spans.map { it.start to it.end })
    }
}
