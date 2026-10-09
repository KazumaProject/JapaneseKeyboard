package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.converter.session.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CounterConversionInstrumentedTest {
    private fun entry() = EntryPointAccessors.fromApplication(ApplicationProvider.getApplicationContext<Context>(), KanaKanjiEngineEntryPoint::class.java)
    @Test fun wholeReadingsAndSentencesUseRulesInEveryProductionPath() = runBlocking {
        val entry = entry()
        val engine = entry.kanaKanjiEngine()
        engine.initializeOptionalDictionaryStateFromCurrentSources()
        val cases = linkedMapOf(
            "いっぽん" to "1本", "ひゃくにじゅうさんぼん" to "123本", "さんびき" to "3匹",
            "ごごさんじはん" to "午後3時半", "ごぜんじゅうにじ" to "午前12時",
            "ひゃくにじゅうさんぼんをかう" to "123本を買う",
            "ねこがさんびきいる" to "猫が3匹いる", "ごごさんじはんにあう" to "午後3時半に合う",
        )
        for (mode in listOf(CandidateQueryMode.NO_TAB_DEFAULT, CandidateQueryMode.PREDICTION, CandidateQueryMode.CONVERSION)) {
            for (bunsetsu in listOf(false, true)) {
                val session = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)
                for ((input, expected) in cases) {
                    val result = session.query(CounterConversionPerformanceInstrumentedTest.request(input, entry.userDictionaryRepository(), mode, bunsetsu))
                    println("COUNTER_CASE $mode bunsetsu=$bunsetsu $input => ${result.candidates.take(12).joinToString("|"){it.string}}")
                    assertEquals("$mode/$bunsetsu/$input", expected, result.candidates.first().string)
                    val segments = result.candidateSegmentsByString[expected] ?: result.candidates.first().conversionSegments
                    assertFalse("Missing segments: $input", segments.isEmpty())
                    assertEquals(input.length, segments.last().inputEnd)
                    assertEquals(expected, segments.joinToString("") { it.output })
                    assertEquals(0, segments.first().inputStart)
                    assertTrue(segments.zipWithNext().all { it.first.inputEnd == it.second.inputStart })
                    if (bunsetsu) assertTrue(result.bunsetsuResult!!.primarySplitPositions.all { it in 1 until input.length })
                }
            }
        }
        val exact = engine.getCandidatesEnglishKana("ひゃくにじゅうさんぼん").map { it.string }
        assertEquals("123本", exact.first())
        assertTrue(exact.containsAll(listOf("百二十三本", "１２３本")))
        val time = engine.getCandidatesEnglishKana("ごごさんじはん").map { it.string }
        assertTrue(time.containsAll(listOf("午後3時半", "午後三時半", "午後３時半", "15:30")))
    }
    @Test fun incrementalTypingEditsAndCancellationMatchFreshConversion() = runBlocking {
        val entry = entry()
        val engine = entry.kanaKanjiEngine()
        engine.initializeOptionalDictionaryStateFromCurrentSources()
        val incremental = KanaKanjiConversionSession(engine, ConversionBackend.INCREMENTAL_SESSION)
        for (phrase in listOf("ひゃくにじゅうさんぼんをかう", "ねこがさんびきいる", "ごごさんじはんにあう", "ほんをさんさつとえんぴつをにほんかう")) {
            val inputs = (1..phrase.length).map(phrase::take) + phrase.dropLast(1) + phrase + phrase.replace("さん", "よん") + phrase
            for (input in inputs) {
                val request = CounterConversionPerformanceInstrumentedTest.request(input, entry.userDictionaryRepository())
                val fresh = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY).query(request)
                val actual = incremental.query(request)
                assertEquals(input, fresh.candidates.map { it.string to it.score }, actual.candidates.map { it.string to it.score })
                assertEquals(input, fresh.candidateSegmentsByString, actual.candidateSegmentsByString)
                assertEquals(input, fresh.bunsetsuResult?.splitPatternByCandidateString, actual.bunsetsuResult?.splitPatternByCandidateString)
            }
        }
        val input = "ごごさんじはんにあう"
        incremental.setAfterForwardDpForTest { throw CancellationException("counter cancellation probe") }
        try { incremental.query(CounterConversionPerformanceInstrumentedTest.request(input + "よ", entry.userDictionaryRepository())); fail("Cancellation was not observed") }
        catch (_: CancellationException) { }
        finally { incremental.setAfterForwardDpForTest(null) }
        val request = CounterConversionPerformanceInstrumentedTest.request(input, entry.userDictionaryRepository())
        assertEquals(KanaKanjiConversionSession(engine, ConversionBackend.LEGACY).query(request).candidates, incremental.query(request).candidates)
    }
    @Test fun bunsetsuProjectionKeepsCounterTextAndSupportsChangingItsNotation() = runBlocking {
        val entry = entry()
        val engine = entry.kanaKanjiEngine()
        val input = "ひゃくにじゅうさんぼんをかう"
        val session = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)
        val result = session.query(CounterConversionPerformanceInstrumentedTest.request(input, entry.userDictionaryRepository()))
        val bunsetsu = result.bunsetsuResult!!
        val snapshot = com.kazumaproject.markdownhelperkeyboard.ime_service.BunsetsuConversionSnapshot(
            input, result.candidates, result.candidateSegmentsByString,
            bunsetsu.splitPatterns, bunsetsu.primarySplitPositions,
        )
        val segments = com.kazumaproject.markdownhelperkeyboard.ime_service.buildConvertedBunsetsuSegments(
            input, bunsetsu.primarySplitPositions, snapshot,
        )
        assertEquals("123本を買う", segments.joinToString("") { it.displayText })
        assertTrue(segments.size > 1)
        val counterIndex = segments.indexOfFirst { it.displayText.contains("123本") }
        assertTrue(counterIndex >= 0)
        val counter = segments[counterIndex]
        val alternatives = session.query(CounterConversionPerformanceInstrumentedTest.request(counter.reading, entry.userDictionaryRepository())).candidates
        val merged = com.kazumaproject.markdownhelperkeyboard.ime_service.mergeBunsetsuCandidates(counter, alternatives)
        assertEquals(counter.displayText, merged.displayText)
        assertTrue(merged.candidates.any { it.string.contains("百二十三本") })
        assertTrue(merged.candidates.any { it.string.contains("１２３本") })
        val changed = segments.mapIndexed { index, segment ->
            if(index == counterIndex) merged.candidates.first { it.string.contains("百二十三本") }.commitText
            else segment.displayText
        }.joinToString("")
        assertEquals("百二十三本を買う", changed)
        println("COUNTER_PROJECTION ${segments.map { it.reading to it.displayText }} changed=$changed")
    }

    @Test fun ordinaryWordsAndMultipleCountersRemainConvertible() = runBlocking {
        val entry = entry()
        val session = KanaKanjiConversionSession(entry.kanaKanjiEngine(), ConversionBackend.LEGACY)
        for ((input, expected) in mapOf("にほんご" to "日本語", "きょう" to "今日", "よしよし" to "よしよし")) {
            val result = session.query(CounterConversionPerformanceInstrumentedTest.request(input, entry.userDictionaryRepository()))
            println("COUNTER_ORDINARY $input => ${result.candidates.take(8).joinToString("|"){it.string}}")
            assertEquals(input, expected, result.candidates.first().string)
        }
        val result = session.query(CounterConversionPerformanceInstrumentedTest.request("ほんをさんさつとえんぴつをにほんかう", entry.userDictionaryRepository()))
        assertTrue(result.candidates.take(8).joinToString { it.string }, result.candidates.any { it.string.contains("3冊") && it.string.contains("2本") })
    }
}
