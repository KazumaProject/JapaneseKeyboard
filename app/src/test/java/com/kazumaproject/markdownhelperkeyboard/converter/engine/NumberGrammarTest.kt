package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Independent arithmetic oracle. Accepted pronunciations are pinned to Osaka 数字 p.17 and
 * Irodori 入門 L9/L13. No expected value is calculated by a production decoder.
 * https://www.pref.osaka.lg.jp/documents/26351/2suuji.pdf
 * https://www.irodori.jpf.go.jp/assets/data/wordlist_X.pdf
 */
class NumberGrammarTest {
    @Test fun everyValueAndFiveThousandBrokenHundredsAndThousands() {
        for (n in 0..9999) {
            assertEquals("n=$n", n.toLong(), ValidatedNumber.parseReading(spoken(n))?.value)
        }
        var rejected = 0
        for (n in 0..999) {
            for (prefix in listOf("にびゃく", "ごぴゃく", "にぜん", "ろくひゃく", "はちせん")) {
                val reading = prefix + if (n == 0) "" else spoken(n)
                assertNull(reading, reading.toNumber())
                assertNull(reading, reading.toNumberExponent())
                rejected++
            }
        }
        assertEquals(5000, rejected)
    }

    @Test fun terminalVariantsDoNotLeakIntoCoefficientsOrCounters() {
        for ((reading, value) in mapOf("じゅうし" to 14L, "じゅうく" to 19L, "にじゅうし" to 24L,
            "じゅうしち" to 17L, "しちじゅう" to 70L, "いっせん" to 1000L,
            "いっちょう" to 1000000000000L, "はっちょう" to 8000000000000L,
            "じゅっちょう" to 10000000000000L, "じっちょう" to 10000000000000L)) {
            assertEquals(reading, value, ValidatedNumber.parseReading(reading)?.value)
        }
        for (reading in listOf("", "し", "よ", "く", "じゅうよ", "じゅういちし", "じゅうしく",
            "ごぜん", "ぜんご", "いちぜん", "にびゃく", "ごぴゃく", "にぜん", "いっまん", "じゅっおく",
            "じゅうしまん", "じゅうくおく", "じゅうしえん", "じゅうくえん", "いっちょういっまん",
            "いちちょう", "はちちょう", "じゅうちょう", "さんじゅうちょう", "じゅういちちょう", "まん", "いちおくにちょう", "いちまんにまん", "じゅー", "きゅー", "まいなすいち", "いちてんご", " いち")) {
            assertNull(reading, ValidatedNumber.parse(reading))
        }
    }

    @Test fun largePlacesAndNumericOverflowBoundaries() {
        for (n in listOf(1, 9, 10, 99, 100, 999, 1000, 9999)) {
            for ((suffix, multiplier) in listOf("まん" to 10000L, "おく" to 100000000L, "ちょう" to 1000000000000L)) {
                val coefficient = if (suffix == "ちょう") when {
                    n % 10 == 1 || n % 10 == 8 -> spoken(n).dropLast(1) + "っ"
                    n % 10 == 0 && n % 100 != 0 -> spoken(n).dropLast(2) + "っ"
                    else -> spoken(n)
                } else spoken(n)
                assertEquals("$n$suffix", n * multiplier, ValidatedNumber.parseReading(coefficient + suffix)?.value)
            }
        }
        val maxReading = List(4) { spoken(9999) }.joinToString("|").split("|")
        assertEquals(9999999999999999L, ValidatedNumber.parseReading(maxReading[0]+"ちょう"+maxReading[1]+"おく"+maxReading[2]+"まん"+maxReading[3])?.value)
        assertNull(ValidatedNumber.parseReading("いちまんちょう"))
        assertEquals(Long.MAX_VALUE, ValidatedNumber.parseDigits("9223372036854775807")?.value)
        assertNull(ValidatedNumber.parseDigits("9223372036854775808"))
        assertNull(ValidatedNumber.parseDigits("-1"))
        assertNull(ValidatedNumber.parseDigits("1.0"))
        assertTrue(createValueBasedSymbolCandidates(4294967297L, 10u).isEmpty())
        assertNull("におく".toNumberExponent())
        assertNull("いちおくいち".toNumberExponent())
        assertEquals("10⁸", "いちおく".toNumberExponent()?.first)
    }

    @Test fun allOrdersPreserveLexicalSlotsLeadingZerosAndExplicitRegistration() {
        val engine = KanaKanjiEngine()
        val english = org.mockito.kotlin.mock<EnglishEngine>()
        org.mockito.kotlin.whenever(english.getCandidates(org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(emptyList())
        KanaKanjiEngine::class.java.getDeclaredField("englishEngine").apply { isAccessible = true; set(engine, english) }
        for (reading in listOf("さん", "さんにん", "003", "００３")) {
            val forms = if (reading == "さんにん") listOf("3人", "３人", "三人") else
                if (reading in listOf("003", "００３")) listOf("003", "００３", "三") else listOf("3", "３", "三")
            for (order in NumberCandidateOrder.entries) {
                val config = PredictionConfig(numberCandidateOrder = order, japaneseNumberCandidatesEnabled = reading !in listOf("003", "００３"))
                val actual = engine.getCandidatesEnglishKana(reading, config).distinctBy { it.string }
                assertEquals("$reading/$order", order.indices.map(forms::get), actual.filter { it.string in forms }.map { it.string })
                val source = listOf(Candidate(forms[2], 1, reading.length.toUByte(), 1), Candidate("通常語", 1, 3u, 1),
                    Candidate(forms[0], 1, reading.length.toUByte(), 1), Candidate(forms[1], 1, reading.length.toUByte(), 1))
                val sorted = NumberCandidatePolicy.order(reading, source, order)
                assertEquals("通常語", sorted[1].string)
                assertEquals(order.indices.map(forms::get), sorted.filter { it.string in forms }.map { it.string })
            }
        }
        assertEquals(NumberCandidateOrder.HALF_FULL_KANJI, NumberCandidateOrder.fromPreference("corrupt"))
    }

    @Test fun learnedCandidatesCommitTextAndSegmentsAreAudited() {
        fun learned(text: String, reading: String) = Candidate(text, CANDIDATE_TYPE_LEARNED_DICTIONARY, reading.length.toUByte(), 0, yomi = reading)
        for (text in listOf("1005", "１００５", "千五", "1,005", "10月5日", "10⁸", "①", "❶", "⑴", "⒈", "¹")) {
            assertFalse(text, NumberCandidatePolicy.eligible("ぜんご", learned(text, "ぜんご")))
        }
        assertTrue(NumberCandidatePolicy.eligible("じゅうぶん", learned("十分", "じゅうぶん")))
        assertFalse(NumberCandidatePolicy.eligible("ぜんご", learned("普通", "ぜんご").copy(commitText = "1005")))
        assertTrue(NumberCandidatePolicy.eligible("ぜんご", learned("1005", "ぜんご").copy(type = CANDIDATE_TYPE_USER_DICTIONARY)))
        val joined = Candidate("千です", 1, 5u, 0, conversionSegments = listOf(
            CandidateConversionSegment(0, 2, "千", "ぜん"), CandidateConversionSegment(2, 4, "です", "です")))
        assertFalse(NumberCandidatePolicy.eligible("ぜんです", joined))
    }

    @Test fun dictionaryNumericSpellingsAreValueEquivalent() {
        for ((reading, output) in listOf("れい" to "零", "せん" to "一千", "じゅうに" to "一二", "さんにん" to "三人")) {
            assertTrue("$reading/$output", NumberCandidatePolicy.eligible(reading, Candidate(output, 1, reading.length.toUByte(), 0)))
        }
        assertEquals(Long.MAX_VALUE, WrittenNumberValue.parse("九百二十二京三千三百七十二兆三百六十八億五千四百七十七万五千八百七"))
        assertNull(WrittenNumberValue.parse("九百二十二京三千三百七十二兆三百六十八億五千四百七十七万五千八百八"))
        assertNull(WrittenNumberValue.parse("1,,234"))
        assertNull(WrittenNumberValue.parse("十百"))
        assertFalse(NumberCandidatePolicy.eligible("さん", Candidate("二", 1, 2u, 0)))
        assertFalse(NumberCandidatePolicy.eligible("003", Candidate("3", 1, 3u, 0)))
    }

    @Test fun lexicalContextCannotRescueFragmentsButCompleteNumbersKeepGrammaticalBoundaries() {
        fun path(input: String, vararg segments: CandidateConversionSegment) = Candidate(
            segments.joinToString("") { it.output }, 1, input.length.toUByte(), 0,
            yomi = input, conversionSegments = segments.toList())
        assertFalse(NumberCandidatePolicy.eligible("にほんご", path("にほんご",
            CandidateConversionSegment(0, 3, "日本", "にほん"), CandidateConversionSegment(3, 4, "5", "ご"))))
        assertTrue(NumberCandidatePolicy.eligible("これはよじ", path("これはよじ",
            CandidateConversionSegment(0, 2, "これ", "これ"), CandidateConversionSegment(2, 3, "は", "は", startsWithParticle = true),
            CandidateConversionSegment(3, 4, "4", "よ"), CandidateConversionSegment(4, 5, "時", "じ"))))
        assertFalse(NumberCandidatePolicy.eligible("これはさんにん", path("これはさんにん",
            CandidateConversionSegment(0, 2, "これ", "これ"), CandidateConversionSegment(2, 3, "は", "は", startsWithParticle = true),
            CandidateConversionSegment(3, 5, "2", "さん"), CandidateConversionSegment(5, 7, "人", "にん"))))
        assertTrue(NumberCandidatePolicy.eligible("さんです", path("さんです",
            CandidateConversionSegment(0, 2, "3", "さん"), CandidateConversionSegment(2, 4, "です", "です"))))
        assertTrue(NumberCandidatePolicy.eligible("これはさんにん", path("これはさんにん",
            CandidateConversionSegment(0, 2, "これ", "これ"), CandidateConversionSegment(2, 3, "は", "は", startsWithParticle = true),
            CandidateConversionSegment(3, 7, "3人", "さんにん"))))
    }

    @Test fun malformedInputCannotReturnThroughOldAsyncResultsOrEditedInput() {
        val engine = KanaKanjiEngine()
        val candidate = engine.getCandidatesEnglishKana("ごせん").first { it.string == "5000" }
        val tracker = com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateRequestTracker()
        val backend = com.kazumaproject.markdownhelperkeyboard.converter.session.ConversionBackend.LEGACY
        val mode = com.kazumaproject.markdownhelperkeyboard.converter.session.CandidateQueryMode.PREDICTION
        tracker.restart(backend)
        val old = tracker.begin("ごせん", mode, backend)
        tracker.invalidate() // preference change
        assertFalse(tracker.isCurrent(old))
        assertFalse(NumberCandidatePolicy.eligible("ごせん", candidate, PredictionConfig(japaneseNumberCandidatesEnabled = false)))
        assertFalse(NumberCandidatePolicy.eligible("ごぜん", candidate))
        val changed = tracker.begin("ごぜん", mode, backend)
        assertFalse(tracker.isCurrent(old))
        assertTrue(tracker.isCurrent(changed))
        tracker.restart(backend)
        assertFalse(tracker.isCurrent(changed))
    }

    @Test fun dictionaryWordsAndExplicitSourcesDoNotGrantBlanketNumericExemptions() {
        val c = Candidate("10⁸", CANDIDATE_TYPE_LEARNED_DICTIONARY, 3u, 0, yomi = "きょう")
        assertFalse(NumberCandidatePolicy.eligible("きょう", c))
        val bad = Candidate("全5", 1, 3u, 0, yomi = "ぜんご", conversionSegments = listOf(
            CandidateConversionSegment(0, 2, "全", "ぜん"), CandidateConversionSegment(2, 3, "5", "ご")))
        assertFalse(NumberCandidatePolicy.eligible("ぜんご", bad))
        assertTrue(NumberCandidatePolicy.eligible("ぜんご", c.copy(type = CANDIDATE_TYPE_USER_TEMPLATE)))
        assertTrue(NumberCandidatePolicy.eligible("ぜんご", c.copy(type = CANDIDATE_TYPE_TEXT_MACRO)))
    }

    companion object {
        fun spoken(n: Int): String {
            if (n == 0) return "ぜろ"
            val digit = arrayOf("", "いち", "に", "さん", "よん", "ご", "ろく", "なな", "はち", "きゅう")
            val thousand = arrayOf("", "せん", "にせん", "さんぜん", "よんせん", "ごせん", "ろくせん", "ななせん", "はっせん", "きゅうせん")
            val hundred = arrayOf("", "ひゃく", "にひゃく", "さんびゃく", "よんひゃく", "ごひゃく", "ろっぴゃく", "ななひゃく", "はっぴゃく", "きゅうひゃく")
            val ten = n / 10 % 10
            return thousand[n / 1000] + hundred[n / 100 % 10] +
                (if (ten == 0) "" else (if (ten == 1) "" else digit[ten]) + "じゅう") + digit[n % 10]
        }
    }
}
