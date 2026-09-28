package com.kazumaproject.markdownhelperkeyboard.converter.number

import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toKanji
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterReadingLexiconTest {

    @Test
    fun reviewedCounterTableCoversAllThirtyFiveRequestedCountersFromOneToTen() {
        val expectedCounters = setOf(
            "つ", "人", "個", "枚", "冊", "本", "匹", "杯", "頭", "台", "着", "足",
            "回", "階", "番", "度", "件", "軒", "点", "通", "発", "泊", "時", "分",
            "秒", "時間", "日", "月", "年", "歳", "か月", "週間", "円", "グラム", "メートル",
        )

        val entriesByCounter = CounterReadingLexicon.readings.groupBy { it.counter }

        assertEquals(expectedCounters, entriesByCounter.keys)
        entriesByCounter.forEach { (counter, entries) ->
            assertTrue(
                "$counter does not cover 1 through 10",
                entries.map { it.value }.toSet().containsAll((1..10).toSet()),
            )
        }
    }

    @Test
    fun reviewedAllophonesAndHomophonesKeepTheirInterpretationsSeparate() {
        val expected = mapOf(
            "いっぽん" to setOf(1 to "本"),
            "さんぼん" to setOf(3 to "本"),
            "ろっぽん" to setOf(6 to "本"),
            "はっぽん" to setOf(8 to "本"),
            "じゅっぽん" to setOf(10 to "本"),
            "さんそく" to setOf(3 to "足"),
            "さんぞく" to setOf(3 to "足"),
            "さんがい" to setOf(3 to "階"),
            "はっかい" to setOf(8 to "回", 8 to "階"),
            "はっぷん" to setOf(8 to "分"),
            "しちにん" to setOf(7 to "人"),
            "くにん" to setOf(9 to "人"),
        )

        expected.forEach { (reading, values) ->
            assertEquals(
                reading,
                values,
                CounterReadingLexicon.matchAll(reading).map { it.value to it.counter }.toSet(),
            )
        }
        assertEquals(setOf("日"), CounterReadingLexicon.matchAll("ついたち").map { it.counter }.toSet())
        assertEquals("日数", CounterReadingLexicon.matchAll("いちにち").single().interpretation)
        assertTrue(CounterReadingLexicon.matchAll("ごかい").map { it.counter }.containsAll(setOf("回", "階")))
    }

    @Test
    fun everyExplicitCounterReadingProducesItsReviewedValueAndCounterSurface() {
        CounterReadingLexicon.readings.forEach { reading ->
            val candidates = NumberFallbackCandidateFactory.candidatesForReading(reading.reading)
            val halfWidth = "${reading.value}${reading.counter}"
            val fullWidth = "${reading.value.toString().map { char -> (char.code + 0xFEE0).toChar() }.joinToString("")}${reading.counter}"
            val kanji = "${reading.value.toLong().toKanji()}${reading.counter}"
            val source = candidates.firstOrNull { it.string == halfWidth }
            assertTrue(
                "${reading.reading} should produce ${reading.value}${reading.counter}: " +
                    candidates.map { it.string },
                source != null,
            )
            val presented = NumberCandidatePresenter.present(
                candidates = listOf(source!!),
                segmentsByCandidateString = emptyMap(),
                config = NumberPresentationConfig(),
            )
            val surfaces = presented.candidates.mapTo(hashSetOf()) { it.string }
            assertTrue("${reading.reading} did not render $halfWidth: $surfaces", halfWidth in surfaces)
            assertTrue("${reading.reading} did not render $fullWidth: $surfaces", fullWidth in surfaces)
            assertTrue("${reading.reading} did not render $kanji: $surfaces", kanji in surfaces)
            presented.candidates.forEach { assertEquals(it.string, it.commitText) }
        }

        val floorsAndBuildings = CounterReadingLexicon.matchAll("はっかい")
        assertEquals(setOf("回", "階"), floorsAndBuildings.map { it.counter }.toSet())
        assertEquals(setOf("さんがい"), CounterReadingLexicon.matchAll("さんがい").map { it.reading }.toSet())
        assertTrue(CounterReadingLexicon.matchAll("さんけん").any { it.counter == "件" })
        assertFalse(CounterReadingLexicon.matchAll("さんけん").any { it.counter == "軒" })
        assertEquals("さんげん", CounterReadingLexicon.matchAll("さんげん").single().reading)
    }

    @Test
    fun fallbackAddsOnlyExplicitSimpleReadingsAndConservativeCompoundSuffixes() {
        fun surfaces(reading: String) = NumberFallbackCandidateFactory.candidatesForReading(reading)
            .map { it.string }
            .toSet()

        assertTrue(surfaces("ごかい").containsAll(setOf("5回", "5階")))
        assertTrue(surfaces("さんぞく").contains("3足"))
        assertTrue(surfaces("さんそく").contains("3足"))
        assertTrue(surfaces("さんじゅうにまい").contains("32枚"))
        assertTrue(surfaces("さんじゅうにほん").contains("32本"))
        assertTrue(surfaces("にじゅうにほん").contains("22本"))
        assertFalse(surfaces("にじゅうほん").contains("20本"))
        assertFalse(surfaces("じゅうさんそく").contains("13足"))
        assertFalse(surfaces("じゅうさんぞく").contains("13足"))
        assertFalse(surfaces("にじゅういちほん").contains("21本"))
        assertFalse(surfaces("さんじゅうさんほん").contains("33本"))
        assertFalse(surfaces("にじゅうふん").contains("20分"))
        assertTrue(surfaces("にじゅっぷん").contains("20分"))
        assertFalse(surfaces("にじゅっふん").contains("20分"))
        assertTrue(surfaces("じゅういちがつ").contains("11月"))
        assertFalse(surfaces("じゅうさんがつ").contains("13月"))
        assertTrue(surfaces("じゅうよっか").contains("14日"))
        assertTrue(surfaces("はつか").contains("20日"))
        assertTrue(surfaces("にじゅうよっか").contains("24日"))
        assertFalse(surfaces("にじゅうにち").contains("20日"))
        assertFalse(surfaces("じゅうよんにち").contains("14日"))
        assertFalse(surfaces("にじゅうよんにち").contains("24日"))
        assertTrue(surfaces("しちにん").contains("7人"))
        assertTrue(surfaces("くにん").contains("9人"))
        assertTrue(surfaces("さんげん").contains("3軒"))
        assertFalse(surfaces("さんけん").contains("3軒"))

        assertFalse(surfaces("しにん").contains("4人"))
        assertFalse(surfaces("しえん").contains("4円"))
        assertFalse(surfaces("くえん").contains("9円"))
        assertFalse(surfaces("いちひき").contains("1匹"))
        assertFalse(surfaces("さんじゅうさんぼん").contains("33本"))
        assertFalse(surfaces("さんじゅうしにん").contains("44人"))
        assertTrue(surfaces("にじゅうごじ").contains("25時"))
        assertFalse(surfaces("さんじゅういちじ").contains("31時"))
    }

    @Test
    fun adjacentClockAndDurationUnitsProduceOnlyValidatedCompositeFallbacks() {
        fun surfaces(reading: String) = NumberFallbackCandidateFactory.candidatesForReading(reading)
            .map { it.string }
            .toSet()

        assertTrue(surfaces("くじごふん").contains("9時5分"))
        assertTrue(surfaces("にじゅうごじごふん").contains("25時5分"))
        assertTrue(surfaces("にじゅうきゅうじごふん").contains("29時5分"))
        assertFalse(surfaces("さんじゅういちじごふん").contains("31時5分"))
        assertTrue(surfaces("じゅうじじゅうごふん").contains("10時15分"))
        assertTrue(surfaces("じゅうにじごふん").contains("12時5分"))
        assertTrue(surfaces("いちじかんにじゅうごふん").contains("1時間25分"))
        assertTrue(surfaces("くじごふんごびょう").contains("9時5分5秒"))
        assertTrue(surfaces("にじゅうごふんさんびょう").contains("25分3秒"))

        assertFalse(surfaces("くじよんふん").contains("9時4分"))
        assertFalse(surfaces("ごふんくじ").contains("5分9時"))
        assertFalse(surfaces("くじごふんじ").contains("9時5分1秒"))
    }

    @Test
    fun composedClockFallbackHasBothDigitSpansAndRendersEveryConfiguredStyle() {
        val input = "くじごふん"
        val fallback = NumberFallbackCandidateFactory.candidatesForReading(input)
            .single { it.string == "9時5分" }
        val spans = fallback.numberMetadata!!.numericSpans

        assertEquals(listOf("9", "5"), spans.map { it.valueDigits })
        assertEquals(listOf(0 to 1, 2 to 3), spans.map { it.outputStart to it.outputEnd })
        assertTrue(NumberFallbackCandidateFactory.hasComposedTimeReading(input))
        assertTrue(NumberFallbackCandidateFactory.hasComposedTimeReading("くじごふんから"))
        assertFalse(NumberFallbackCandidateFactory.hasComposedTimeReading("おくじごふんから"))
        assertTrue(CounterReadingLexicon.hasCounterReadingWithin(input))
        assertTrue(CounterReadingLexicon.hasCounterReadingWithin("くじごふんから"))
        assertEquals(32, NumberCandidatePresenter.expandedSearchCount(input, requested = 1))
        assertEquals(32, NumberCandidatePresenter.expandedSearchCount("くじごふんから", requested = 1))
        assertTrue(NumberCandidatePresenter.shouldCollectSegments(input))
        assertTrue(NumberCandidatePresenter.shouldCollectSegments("くじごふんから"))

        val presented = NumberCandidatePresenter.present(
            candidates = listOf(fallback),
            segmentsByCandidateString = emptyMap(),
            config = NumberPresentationConfig(),
        )
        val timeStyles = setOf("9時5分", "９時５分", "九時五分")
        assertTrue(presented.candidates.map { it.string }.toSet().containsAll(timeStyles))
        presented.candidates.forEach { candidate ->
            assertEquals(candidate.string, candidate.commitText)
        }

        val embedded = NumberFallbackCandidateFactory.candidatesForReading("くじごふんから")
            .single { it.string == "9時5分から" }
        val embeddedPresentation = NumberCandidatePresenter.present(
            candidates = listOf(embedded),
            segmentsByCandidateString = emptyMap(),
            config = NumberPresentationConfig(),
        )
        assertTrue(
            embeddedPresentation.candidates.map { it.string }.toSet().toString(),
            embeddedPresentation.candidates.map { it.string }.toSet().containsAll(
                setOf("9時5分から", "９時５分から", "九時五分から"),
            ),
        )

        val allOrders = listOf(
            listOf(NumberStyle.HALF_WIDTH, NumberStyle.FULL_WIDTH, NumberStyle.KANJI),
            listOf(NumberStyle.HALF_WIDTH, NumberStyle.KANJI, NumberStyle.FULL_WIDTH),
            listOf(NumberStyle.FULL_WIDTH, NumberStyle.HALF_WIDTH, NumberStyle.KANJI),
            listOf(NumberStyle.FULL_WIDTH, NumberStyle.KANJI, NumberStyle.HALF_WIDTH),
            listOf(NumberStyle.KANJI, NumberStyle.HALF_WIDTH, NumberStyle.FULL_WIDTH),
            listOf(NumberStyle.KANJI, NumberStyle.FULL_WIDTH, NumberStyle.HALF_WIDTH),
        )
        val surfaceByStyle = mapOf(
            NumberStyle.HALF_WIDTH to "9時5分",
            NumberStyle.FULL_WIDTH to "９時５分",
            NumberStyle.KANJI to "九時五分",
        )
        allOrders.forEach { order ->
            val ordered = NumberCandidatePresenter.present(
                candidates = listOf(fallback),
                segmentsByCandidateString = emptyMap(),
                config = NumberPresentationConfig(styleOrder = order),
            )
            assertEquals(
                order.map(surfaceByStyle::getValue),
                ordered.candidates.map { it.string }.filter(timeStyles::contains),
            )
        }

        val disabled = NumberCandidatePresenter.present(
            candidates = listOf(fallback),
            segmentsByCandidateString = emptyMap(),
            config = NumberPresentationConfig(additionsEnabled = false),
        )
        assertTrue(disabled.candidates.isEmpty())
    }

    @Test
    fun separatedTimeFallbackRendersEveryRecognizedTimeInOneSentenceStyle() {
        val input = "くじごふんとにじゅうごじじゅっぷんから"
        val halfWidth = "9時5分と25時10分から"
        val fallback = NumberFallbackCandidateFactory.candidatesForReading(input)
            .single { it.string == halfWidth }
        val spans = fallback.numberMetadata!!.numericSpans
        assertEquals(
            listOf(
                listOf(0, 2, 0, 1),
                listOf(2, 5, 2, 3),
                listOf(6, 12, 5, 7),
                listOf(12, 17, 8, 10),
            ),
            spans.map { listOf(it.inputStart, it.inputEnd, it.outputStart, it.outputEnd) },
        )

        val presented = NumberCandidatePresenter.present(
            candidates = listOf(fallback),
            segmentsByCandidateString = emptyMap(),
            config = NumberPresentationConfig(),
        )
        val styles = presented.candidates.map { it.string }.toSet()
        assertTrue(
            styles.toString(),
            styles.containsAll(
                setOf(
                    halfWidth,
                    "９時５分と２５時１０分から",
                    "九時五分と二十五時十分から",
                ),
            ),
        )
        presented.candidates.forEach { assertEquals(it.string, it.commitText) }
    }
}
