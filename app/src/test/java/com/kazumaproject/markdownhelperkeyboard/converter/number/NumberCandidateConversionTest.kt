package com.kazumaproject.markdownhelperkeyboard.converter.number

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.session.*
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NumberCandidateConversionTest {
    @Test fun canonicalWordsAndAllOrdersWorkAcrossEveryEngineRouteAndBackend() = runBlocking {
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            for (mode in CandidateQueryMode.entries) {
                for (bunsetsu in listOf(false, true)) {
                    for (order in permutations(NumberCandidateFormat.entries.toList())) {
                        for (input in listOf("いちまん", "にまん", "いちまんえん", "にまんえん", "はつか", "はつかかん")) {
                            val result = session.query(request(input, mode, bunsetsu).copy(numberCandidateConfig = NumberCandidateConfig(order = order)))
                            val formats = result.candidates.mapNotNull { it.numberVariant?.format }.distinct()
                            assertEquals("$backend/$mode/$bunsetsu/$input", order, formats)
                            assertTrue(result.candidates.all { it.string == it.commitText })
                            result.bunsetsuResult?.let { assertEquals(result.candidates, it.candidates) }
                        }
                    }
                }
            }
        }
    }

    @Test fun sentenceVariantsKeepExactRubyAndBunsetsuCorrespondence() = runBlocking {
        val input = "はつかにいちまんえんはらう"
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            for (mode in listOf(CandidateQueryMode.NO_TAB_DEFAULT, CandidateQueryMode.PREDICTION, CandidateQueryMode.CONVERSION)) {
                for (bunsetsu in listOf(false, true)) {
                    val result = session.query(request(input, mode, bunsetsu))
                    println("$backend/$mode/$bunsetsu sentence: ${result.candidates.take(20).map { it.string }}")
                    assertTrue("$backend/$mode/$bunsetsu", result.candidates.any { it.string == "20日に10000円払う" })
                    assertTrue(result.candidates.any { it.string == "２０日に１００００円払う" })
                    val variant = result.candidates.first { it.string == "20日に10000円払う" }
                    assertEquals(variant.string, variant.commitText)
                    val segments = result.candidateSegmentsByString.getValue(variant.string)
                    assertEquals(variant.string, segments.joinToString("") { it.output })
                    if (bunsetsu) {
                        val bunsetsuResult = result.bunsetsuResult!!
                        val originalSplits = bunsetsuResult.splitPatternByCandidateString.getValue("二十日に一万円払う")
                        assertEquals(originalSplits, bunsetsuResult.splitPatternByCandidateString.getValue(variant.string))
                    }
                }
            }
        }
    }

    @Test fun disabledSupplementAndOrdinaryWordsPreserveProductionBehaviour() = runBlocking {
        val session = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)
        val off = NumberCandidateConfig(enhanceCounterCandidates = false)
        val day = session.query(request("はつか", CandidateQueryMode.CONVERSION, true).copy(numberCandidateConfig = off))
        assertFalse(day.candidates.any { it.string == "20日" })
        assertTrue(day.candidates.any { it.string == "二十日" })
        val japan = session.query(request("にほん", CandidateQueryMode.CONVERSION, true))
        assertTrue(japan.candidates.any { it.string == "日本" })
        for (input in listOf("よしよし", "しえん", "しじ", "しせん", "くちょう")) {
            val result = session.query(request(input, CandidateQueryMode.CONVERSION, true))
            assertTrue(input, result.candidates.none { it.numberVariant != null })
        }
    }

    @Test fun generatedSentencePathsAreAvailableWithoutRubyOrCustomOrderSettings() = runBlocking {
        for (backend in ConversionBackend.entries) {
            val result = KanaKanjiConversionSession(engine, backend).query(
                request("いちまんえんはらう", CandidateQueryMode.PREDICTION, true)
                    .copy(collectCandidateSegments = false),
            )
            val text = "10000円払う"
            assertTrue(result.candidates.any { it.string == text })
            assertEquals(text, result.candidateSegmentsByString.getValue(text).joinToString("") { it.output })
        }
    }

    private fun request(input: String, mode: CandidateQueryMode, bunsetsu: Boolean) = KanaKanjiQueryRequest(
        input = input, mode = mode, bunsetsuSeparation = bunsetsu, n = 8,
        mozcUtPersonName = false, mozcUtPlaces = false, mozcUtWiki = false, mozcUtNeologd = false, mozcUtWeb = false,
        userDictionaryRepository = repository, learnRepository = null, omissionSearchEnabled = false,
        typoCorrectionJapaneseFlickEnabled = false, typoCorrectionQwertyEnglishEnabled = false,
        typoCorrectionOffsetScore = 3000, omissionSearchOffsetScore = 3000, beamWidth = 20, collectCandidateSegments = true,
    )
    private fun <T> permutations(values: List<T>): List<List<T>> = if (values.isEmpty()) listOf(emptyList())
        else values.flatMap { first -> permutations(values - first).map { listOf(first) + it } }
    companion object {
        private lateinit var engine: KanaKanjiEngine
        private lateinit var repository: UserDictionaryRepository
        @JvmStatic @BeforeClass fun setUp() {
            engine = TestEngineFactory.create()
            repository = mock()
            runBlocking {
                whenever(repository.commonPrefixSearchInUserDict(any())).thenReturn(emptyList<UserWord>())
                whenever(repository.exactMatchesForConversion(any())).thenReturn(emptyList<UserWord>())
            }
        }
    }
}
