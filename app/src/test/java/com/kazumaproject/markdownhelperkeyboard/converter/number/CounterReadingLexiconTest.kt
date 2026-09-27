package com.kazumaproject.markdownhelperkeyboard.converter.number

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
        assertFalse(CounterReadingLexicon.matchAll("さんそく").any { it.counter == "足" })
        assertEquals(setOf("日"), CounterReadingLexicon.matchAll("ついたち").map { it.counter }.toSet())
        assertEquals("日数", CounterReadingLexicon.matchAll("いちにち").single().interpretation)
        assertTrue(CounterReadingLexicon.matchAll("ごかい").map { it.counter }.containsAll(setOf("回", "階")))
    }

    @Test
    fun everyExplicitCounterReadingProducesItsReviewedValueAndCounterSurface() {
        CounterReadingLexicon.readings.forEach { reading ->
            val candidates = NumberFallbackCandidateFactory.candidatesForReading(reading.reading)
            assertTrue(
                "${reading.reading} should produce ${reading.value}${reading.counter}: " +
                    candidates.map { it.string },
                candidates.any { it.string == "${reading.value}${reading.counter}" },
            )
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
        assertFalse(surfaces("さんそく").contains("3足"))
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
    }
}
