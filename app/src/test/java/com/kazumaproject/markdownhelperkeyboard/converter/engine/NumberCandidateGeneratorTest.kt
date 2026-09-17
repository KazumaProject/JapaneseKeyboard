package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_DICTIONARY
import org.junit.Assert.*
import org.junit.Test

class NumberCandidateGeneratorTest {
    private fun generated(input: String, config: PredictionConfig = PredictionConfig()) =
        NumberCandidateGenerator.generate(input, config).map { it.string }

    @Test fun completeReadingsAndBoundaries() {
        val cases = mapOf(
            "よじ" to listOf("4時", "４時", "四時"),
            "いちじごふん" to listOf("1時5分", "１時５分", "一時五分", "1:05"),
            "さんじごふん" to listOf("3時5分", "３時５分", "三時五分", "3:05"),
            "にじゅうくじごじゅうきゅうふん" to listOf("29時59分", "２９時５９分", "二十九時五十九分", "29:59"),
            "ぜろじぜろふん" to listOf("0時0分", "０時０分", "〇時〇分", "0:00"),
            "ひゃっぷん" to listOf("100分", "１００分", "百分"),
        )
        cases.forEach { (reading, expected) -> assertEquals(reading, expected, generated(reading)) }
        listOf("これはよじ", "ごぜん", "しじ", "さんじゅうじ", "さんじろくじゅっぷん", "いちじいちふん",
            "いちじごふんご", "いちじご", "ぜん", "さんひゃく", "いっ", "びゃく",
            "じゅうよふん", "じゅうくふん", "じゅうよぷん", "じゅうくぷん").forEach {
            assertTrue("$it: ${generated(it)}", generated(it).isEmpty())
        }
        assertTrue(generated("229").contains("2月29日"))
        assertFalse(generated("230").contains("2月30日"))
        assertFalse(generated("431").contains("4月31日"))
        assertFalse(generated("3000").contains("30:00"))
        assertEquals(listOf("００３", "003"), generated("003", PredictionConfig(
            numberCandidateOrder = NumberCandidateOrder.FULL_HALF_KANJI)).take(2))
        assertTrue(generated("9223372036854775808").contains("９２２３３７２０３６８５４７７５８０８"))
    }

    @Test fun eachKindControlsOnlyGeneratedAdditions() {
        val cases = listOf(
            Triple(NumberCandidateKind.TIME, "いちじごふん", "1時5分"),
            Triple(NumberCandidateKind.PEOPLE, "さんにん", "3人"),
            Triple(NumberCandidateKind.YEN, "ひゃくえん", "100円"),
            Triple(NumberCandidateKind.DATE, "ひゃくいち", "1月1日"),
            Triple(NumberCandidateKind.COMMA, "せん", "1,000"),
            Triple(NumberCandidateKind.LARGE_UNIT, "いちまん", "1万"),
            Triple(NumberCandidateKind.EXPONENT, "いちおく", "10⁸"),
            Triple(NumberCandidateKind.SUPERSCRIPT, "さん", "³"),
            Triple(NumberCandidateKind.SUBSCRIPT, "さん", "₃"),
            Triple(NumberCandidateKind.CIRCLED, "さん", "③"),
            Triple(NumberCandidateKind.BLACK_CIRCLED, "さん", "❸"),
            Triple(NumberCandidateKind.ROMAN, "さん", "Ⅲ"),
            Triple(NumberCandidateKind.PARENTHESIZED, "さん", "⑶"),
            Triple(NumberCandidateKind.PERIOD, "さん", "⒊"),
        )
        cases.forEach { (kind, reading, surface) ->
            assertTrue("$kind", surface in generated(reading))
            assertFalse("$kind", surface in generated(reading, PredictionConfig(
                numberCandidateConfig = NumberCandidateConfig(disabledKinds = setOf(kind)))))
        }
        assertTrue(generated("さん", PredictionConfig(japaneseNumberCandidatesEnabled = false)).isEmpty())
        assertTrue(generated("3", PredictionConfig(japaneseNumberCandidatesEnabled = false)).contains("3"))
        assertFalse(generated("さん", PredictionConfig(showSymbolCandidates = false)).contains("③"))
    }

    @Test fun customUnitsRoundTripAndUpdateWithoutChangingDictionaryCandidates() {
        val unit = CustomNumberUnit("pieces", "個", "こ", specialReadings = listOf(
            SpecialNumberReading(1, "いっこ"), SpecialNumberReading(8, "はっこ"), SpecialNumberReading(8, "はちこ")))
        val settings = NumberCandidateConfig(units = listOf(unit))
        fun own(reading: String, units: List<CustomNumberUnit> = listOf(unit)): List<String> {
            val config = settings.copy(units = units)
            return ValidatedNumber.parseAll(reading, config).filter { it.customUnit != null }.flatMap {
                NumberCandidateGenerator.generate(it, PredictionConfig(numberCandidateConfig = config))
            }.map { it.string }
        }
        assertEquals(settings, NumberCandidateConfig.decode(settings.encode()))
        assertEquals(listOf("2個", "２個", "二個"), own("にこ"))
        assertEquals(listOf("1個", "１個", "一個"), own("いっこ"))
        assertTrue(own("いちこ").isEmpty())
        assertTrue(own("じゅういっこ").isEmpty())
        assertEquals(own("はっこ"), own("はちこ"))
        assertTrue(own("いっこ", emptyList()).isEmpty())
        assertTrue(own("いっこ", listOf(unit.copy(enabled = false))).isEmpty())
        assertEquals(listOf("1組", "１組", "一組"), own("いっこ", listOf(unit.copy(output = "組"))))
        assertFalse(unit.copy(specialReadings = listOf(SpecialNumberReading(1, "にこ"))).isValid())
        assertFalse(unit.copy(specialReadings = listOf(SpecialNumberReading(1, "いっこ"), SpecialNumberReading(2, "いっこ"))).isValid())
        assertEquals(NumberCandidateConfig(), NumberCandidateConfig.decode("broken"))
        assertEquals(NumberCandidateConfig(), NumberCandidateConfig.decode("{\"version\":2}"))
    }

    @Test fun notationOrderPreservesOtherSlotsAndDictionaryObjects() {
        val reading = "いちじごふん"
        val forms = listOf("1時5分", "１時５分", "一時五分")
        val candidates = listOf("1時5分", "1:05", "通常語", "１時５分", "一時五分").map {
            Candidate(it, 1, reading.length.toUByte(), 1000)
        }
        for (order in NumberCandidateOrder.entries) {
            val sorted = NumberCandidateGenerator.order(reading, candidates, PredictionConfig(numberCandidateOrder = order))
            assertSame(candidates[2], sorted[2])
            assertEquals(order.indices.map(forms::get) + "1:05", sorted.filterNot { it.string == "通常語" }.map { it.string })
            assertEquals(candidates.toSet(), sorted.toSet())
        }
        val explicit = candidates[0].copy(type = CANDIDATE_TYPE_USER_DICTIONARY)
        val source = listOf(explicit) + candidates.drop(1)
        assertSame(explicit, NumberCandidateGenerator.order(reading, source,
            PredictionConfig(numberCandidateOrder = NumberCandidateOrder.KANJI_FULL_HALF))[0])
        assertEquals(candidates, NumberCandidateGenerator.order(reading, candidates,
            PredictionConfig(japaneseNumberCandidatesEnabled = false)))
        assertEquals(candidates, NumberCandidateGenerator.order("これはよじ", candidates, PredictionConfig()))
    }
    @Test fun allBuiltInCountersAndTheirSwitches() {
        val cases = listOf(
            Triple(BuiltInCounter.THINGS, "ひとつ", "1つ"), Triple(BuiltInCounter.DAY, "ふつか", "2日"),
            Triple(BuiltInCounter.MONTH, "しがつ", "4月"), Triple(BuiltInCounter.HOURS, "よじかん", "4時間"),
            Triple(BuiltInCounter.YEAR, "さんねん", "3年"), Triple(BuiltInCounter.AGE, "はたち", "20歳"),
            Triple(BuiltInCounter.MONTHS, "いっかげつ", "1か月"), Triple(BuiltInCounter.PIECES, "いっこ", "1個"),
            Triple(BuiltInCounter.LONG_OBJECTS, "さんぼん", "3本"), Triple(BuiltInCounter.ANIMALS, "ろっぴき", "6匹"),
            Triple(BuiltInCounter.CUPS, "いっぱい", "1杯"), Triple(BuiltInCounter.BOOKS, "いっさつ", "1冊"),
            Triple(BuiltInCounter.TIMES, "ろっかい", "6回"), Triple(BuiltInCounter.FLOORS, "さんがい", "3階"),
            Triple(BuiltInCounter.CASES, "いっけん", "1件"), Triple(BuiltInCounter.HOUSES, "さんげん", "3軒"),
            Triple(BuiltInCounter.HEADS, "いっとう", "1頭"), Triple(BuiltInCounter.MACHINES, "さんだい", "3台"),
            Triple(BuiltInCounter.SHEETS, "さんまい", "3枚"), Triple(BuiltInCounter.CLOTHES, "いっちゃく", "1着"),
            Triple(BuiltInCounter.PAIRS, "いっそく", "1足"), Triple(BuiltInCounter.NIGHTS, "ろっぱく", "6泊"),
            Triple(BuiltInCounter.SHOTS, "さんぱつ", "3発"), Triple(BuiltInCounter.POINTS, "さんてん", "3点"),
            Triple(BuiltInCounter.LETTERS, "いっつう", "1通"),
        )
        assertEquals(BuiltInCounter.entries.toSet(), cases.map { it.first }.toSet())
        cases.forEach { (counter, reading, surface) ->
            assertTrue(reading, surface in generated(reading))
            val config = NumberCandidateConfig(disabledCounters = setOf(counter.storageId))
            assertFalse(reading, surface in generated(reading, PredictionConfig(numberCandidateConfig = config)))
            assertEquals(config, NumberCandidateConfig.decode(config.encode()))
            assertTrue(generated(reading, PredictionConfig(japaneseNumberCandidatesEnabled = false)).isEmpty())
        }
        assertTrue(generated("いっかい").containsAll(listOf("1回", "1階")))
        assertTrue("1階" in generated("いっかい", PredictionConfig(numberCandidateConfig =
            NumberCandidateConfig(disabledCounters = setOf(BuiltInCounter.TIMES.storageId)))))
        assertEquals(listOf("4円", "４円", "四円"), generated("よえん"))
        assertEquals(generated("よえん"), generated("よんえん"))
    }

    @Test fun pairsAcceptVoicedAndOrdinaryReadingsAndRespectSwitch() {
        val disabled = PredictionConfig(numberCandidateConfig = NumberCandidateConfig(
            disabledCounters = setOf(BuiltInCounter.PAIRS.storageId)))
        for ((prefix, forms) in listOf(
            "" to listOf("3足", "３足", "三足"),
            "じゅう" to listOf("13足", "１３足", "十三足"),
            "にじゅう" to listOf("23足", "２３足", "二十三足")
        )) {
            for (ending in listOf("さんぞく", "さんそく")) {
                val reading = prefix + ending
                assertEquals(reading, forms, generated(reading))
                assertTrue(reading, generated(reading, disabled).isEmpty())
            }
        }
        assertEquals(generated("はっそく"), generated("はちそく"))
        for ((reading, surface) in mapOf("いっそく" to "1足", "はっそく" to "8足",
            "じゅっそく" to "10足", "じっそく" to "10足", "じゅういっそく" to "11足")) {
            assertTrue(reading, surface in generated(reading))
        }
        for (reading in listOf("これはさんぞく", "さんぞくです", "さんぞくさんぞく")) {
            assertTrue(reading, generated(reading).isEmpty())
        }
    }

    @Test fun builtInCompositionAndExactExceptions() {
        val valid = mapOf("じゅういっこ" to "11個", "にじゅうさんぼん" to "23本", "にじゅうろっぽん" to "26本",
            "にじゅっこ" to "20個", "さんじっこ" to "30個", "ひゃっこ" to "100個", "さんびゃっぽん" to "300本",
            "せんびき" to "1000匹", "いちまんいっさつ" to "10001冊", "じゅうよっか" to "14日",
            "にじゅうよっか" to "24日", "はつか" to "20日", "じゅうくにち" to "19日", "ひゃくにち" to "100日",
            "しちがつ" to "7月", "くがつ" to "9月", "じゅうにがつ" to "12月", "さんじゅうよじかん" to "34時間",
            "いちにち" to "1日", "よんぱつ" to "4発", "ろくはつ" to "6発", "にじゅうよんぱく" to "24泊",
            "にじゅっさい" to "20歳", "にじゅういっさい" to "21歳", "さんけん" to "3件")
        valid.forEach { (reading, surface) -> assertTrue("$reading: ${generated(reading)}", surface in generated(reading)) }
        listOf("じゅうひとつ", "じゅうはたち", "にじゅうふつか", "さんひゃっぽん", "いちほん", "ろくこ",
            "いちさつ", "じゅうさんがつ", "ぜろがつ", "よんがつ", "じゅうきゅうにち", "にじゅうしこ",
            "これはいっこ", "いっこです", "いっこいっこ").forEach {
            assertTrue("$it: ${generated(it)}", generated(it).isEmpty())
        }
        assertTrue("2月29日" in generated("229", PredictionConfig(numberCandidateConfig =
            NumberCandidateConfig(disabledCounters = setOf(BuiltInCounter.DAY.storageId)))))
        assertTrue("2日" in generated("ふつか", PredictionConfig(numberCandidateConfig =
            NumberCandidateConfig(disabledKinds = setOf(NumberCandidateKind.DATE)))))
    }

    @Test fun customCompositionValidationPrecedenceAndRoundTrip() {
        val compose = SpecialNumberReadingMode.COMPOSE
        val unit = CustomNumberUnit("packs", "箱", "はこ", specialReadings = listOf(
            SpecialNumberReading(1, "いっぱこ", compose, "いち"),
            SpecialNumberReading(10, "じゅっぱこ", compose, "じゅう"),
            SpecialNumberReading(100, "ひゃっぱこ", compose, "ひゃく"),
            SpecialNumberReading(1000, "せんぱこ", compose, "せん"),
            SpecialNumberReading(20, "はたはこ")))
        val config = NumberCandidateConfig(units = listOf(unit), disabledCounters = setOf("pieces"))
        assertTrue(unit.isValid())
        assertEquals(config, NumberCandidateConfig.decode(config.encode()))
        fun own(reading: String, u: CustomNumberUnit = unit) = generated(reading,
            PredictionConfig(numberCandidateConfig = config.copy(units = listOf(u))))
        mapOf("じゅういっぱこ" to "11箱", "にじゅっぱこ" to "20箱", "にひゃっぱこ" to "200箱",
            "にせんぱこ" to "2000箱", "はたはこ" to "20箱", "にはこ" to "2箱").forEach { (r, v) -> assertTrue(r, v in own(r)) }
        listOf("じゅうはたはこ", "じゅういちはこ", "にじゅうはこ", "これはいっぱこ", "いっぱこです").forEach { assertTrue(it, own(it).isEmpty()) }
        assertTrue(own("じゅういっぱこ", unit.copy(enabled = false)).isEmpty())
        val exactWins = unit.copy(specialReadings = unit.specialReadings + SpecialNumberReading(42, "じゅういっぱこ"))
        assertEquals(listOf("42箱", "４２箱", "四十二箱"), own("じゅういっぱこ", exactWins))
        val overlapping = unit.copy(specialReadings = listOf(
            SpecialNumberReading(1, "いっこ", compose, "いち"),
            SpecialNumberReading(10, "じゅういっこ", compose, "じゅう")))
        assertTrue(own("にじゅういっこ", overlapping).containsAll(listOf("21箱", "20箱")))
        assertFalse(SpecialNumberReading(1, "いっぱこ", compose, "に").isValid())
        assertFalse(SpecialNumberReading(0, "れいはこ", compose, "れい").isValid())
        assertFalse(SpecialNumberReading(1, "いっぱこ", compose, "").isValid())
        assertTrue(SpecialNumberReading(20, "はたち").isValid())
        val sameOutput = CustomNumberUnit("pieces", "個", "こ", specialReadings = listOf(SpecialNumberReading(1, "いっこ", compose, "いち")))
        assertEquals(listOf("11個", "１１個", "十一個"), generated("じゅういっこ", PredictionConfig(
            numberCandidateConfig = NumberCandidateConfig(units = listOf(sameOutput)))))
        assertEquals(listOf("11個", "１１個", "十一個"), generated("じゅういっこ", PredictionConfig(
            numberCandidateConfig = NumberCandidateConfig(units = listOf(sameOutput), disabledCounters = setOf("pieces")))))
    }

    @Test fun suggestedBaseReadingsParseToTheRegisteredValue() {
        for (value in listOf(0L, 1, 10, 20, 100, 300, 600, 800, 1000, 3000, 8000, 10000, 100000001,
            1_000_000_000_000, 8_000_000_000_000, 10_000_000_000_000, 9_999_999_999_999_999)) {
            assertEquals(value, ValidatedNumber.parseReading(SpecialNumberReading.suggestBase(value))?.value)
        }
        assertEquals("", SpecialNumberReading.suggestBase(Long.MAX_VALUE))
    }

    @Test fun counterReadingsOneThroughTen() {
        CounterReadingCases.rows.forEach { (counter, readings) ->
            val inputs = readings.split(" ")
            inputs.forEachIndexed { i, reading -> assertTrue("$reading: ${generated(reading)}", "${i + 1}$counter" in generated(reading)) }
        }
    }

}
