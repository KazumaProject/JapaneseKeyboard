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

    @Test fun reviewedSentenceRegressionsWorkWithSmallNBestAndEnhancementOff() = runBlocking {
        val inputs = listOf("にじゅっぷんまって", "にかげつかかる", "さんにんでいく", "いちまんはらう")
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            for (mode in listOf(CandidateQueryMode.NO_TAB_DEFAULT, CandidateQueryMode.PREDICTION, CandidateQueryMode.CONVERSION)) {
                for (bunsetsu in listOf(false, true)) for (n in listOf(1, 4, 8)) {
                    for (order in permutations(NumberCandidateFormat.entries.toList())) for (enabled in listOf(false, true)) {
                        for (input in inputs) {
                            val config = NumberCandidateConfig(enabled, order)
                            val result = session.query(request(input, mode, bunsetsu).copy(n = n, numberCandidateConfig = config))
                            val label = "$backend/$mode/$bunsetsu/$n/$order/$enabled/$input"
                            assertFalse(label, result.candidates.any { it.string.contains("210分") })
                            val numbers = result.candidates.filter { it.numberVariant != null }
                            if (enabled) assertTrue(label + result.candidates.map { it.string }, numbers.isNotEmpty())
                            // A protected proper-name row can already occupy the canonical kanji text.
                            if (enabled) assertTrue(label, numbers.any { it.numberVariant!!.format == NumberCandidateFormat.FULL_WIDTH })
                            numbers.groupBy { it.numberVariant!!.group }.values.forEach { group ->
                                val ranks = group.map { order.indexOf(it.numberVariant!!.format) }
                                assertEquals(label, ranks.sorted(), ranks)
                            }
                            for (candidate in numbers) {
                                val segments = result.candidateSegmentsByString.getValue(candidate.string)
                                assertEquals(label, candidate.string, segments.joinToString("") { it.output })
                                assertEquals(label, segments, candidate.conversionSegments)
                                assertEquals(label, candidate.string, candidate.commitText)
                                assertEquals(label, 0, segments.first().inputStart)
                                assertEquals(label, input.length, segments.last().inputEnd)
                                segments.zipWithNext().forEach { (a, b) -> assertEquals(label, a.inputEnd, b.inputStart) }
                                val splits = result.bunsetsuResult?.splitPatternByCandidateString?.get(candidate.string).orEmpty()
                                assertTrue(label, splits.all { split -> segments.any { it.inputStart == split } })
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun typingDeletingAndTogglingNumericalUnitsMatchesFreshConversions() = runBlocking {
        val incremental = KanaKanjiConversionSession(engine, ConversionBackend.INCREMENTAL_SESSION)
        for (input in listOf("にじゅっぷんまって", "にかげつかかる", "はつかにいちまんえんはらう")) {
            val edits = (1..input.length).map { input.take(it) } + (input.length - 1 downTo 1).map { input.take(it) }
            for (text in edits) for (enabled in listOf(true, false, true)) {
                val query = request(text, CandidateQueryMode.CONVERSION, true).copy(n = 4,
                    numberCandidateConfig = NumberCandidateConfig(enabled))
                val actual = incremental.query(query)
                val expected = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY).query(query)
                assertEquals("$text/$enabled", expected.candidates, actual.candidates)
                assertEquals("$text/$enabled", expected.candidateSegmentsByString, actual.candidateSegmentsByString)
                assertEquals("$text/$enabled", expected.bunsetsuResult, actual.bunsetsuResult)
            }
        }
    }

    @Test fun cancellationWhileCompletingNumericSpanKeepsCommittedGraphAndRecovers() = runBlocking {
        val localRepository = mock<UserDictionaryRepository>()
        var cancelNextLookup = false
        whenever(localRepository.commonPrefixSearchInUserDict(any())).thenAnswer {
            if (cancelNextLookup) {
                cancelNextLookup = false
                throw kotlinx.coroutines.CancellationException("numeric span completion")
            }
            emptyList<UserWord>()
        }
        whenever(localRepository.exactMatchesForConversion(any())).thenReturn(emptyList())
        val session = KanaKanjiConversionSession(engine, ConversionBackend.INCREMENTAL_SESSION)
        val initial = request("にじゅっぷ", CandidateQueryMode.CONVERSION, true).copy(userDictionaryRepository = localRepository)
        session.query(initial)
        cancelNextLookup = true
        try {
            session.query(initial.copy(input = "にじゅっぷん"))
            fail("Expected cancellation")
        } catch (_: kotlinx.coroutines.CancellationException) { }
        assertEquals(initial.input, session.committedInput())
        val next = initial.copy(input = "にじゅっぷんまって")
        val recovered = session.query(next)
        val fresh = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY).query(next)
        assertEquals(fresh.candidates, recovered.candidates)
        assertEquals(fresh.candidateSegmentsByString, recovered.candidateSegmentsByString)
        assertEquals(fresh.bunsetsuResult, recovered.bunsetsuResult)
    }

    @Test fun particlesAndSignedOrDecimalInputNeverForceUnrequestedNumberPaths() = runBlocking {
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            for (input in listOf("きょうにいく", "まごにあう", "-2えん", "1.5えん")) {
                val query = request(input, CandidateQueryMode.CONVERSION, true).copy(n = 1)
                val enabled = session.query(query)
                val disabled = session.query(query.copy(numberCandidateConfig = NumberCandidateConfig(false)))
                assertEquals(input, disabled.candidates.map { it.string }, enabled.candidates.map { it.string })
            }
        }
    }

    @Test fun formatOrderOnlyReordersButToggleAndDictionaryRevisionInvalidateIt() = runBlocking {
        val localRepository = mock<UserDictionaryRepository>()
        whenever(localRepository.commonPrefixSearchInUserDict(any())).thenReturn(emptyList())
        whenever(localRepository.exactMatchesForConversion(any())).thenReturn(emptyList())
        var revision = 0L
        whenever(localRepository.conversionRevision).thenAnswer { revision }
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            val query = request("さんにんでいく", CandidateQueryMode.CONVERSION, true).copy(userDictionaryRepository = localRepository)
            val original = session.query(query)
            clearInvocations(localRepository)
            val reversed = query.copy(numberCandidateConfig = NumberCandidateConfig(order = NumberCandidateFormat.entries.reversed()))
            val reordered = session.query(reversed)
            verify(localRepository, never()).commonPrefixSearchInUserDict(any())
            verify(localRepository, never()).exactMatchesForConversion(any())
            assertEquals(original.candidates.map { it.string }.toSet(), reordered.candidates.map { it.string }.toSet())
            assertEquals(original.candidateSegmentsByString, reordered.candidateSegmentsByString)
            assertEquals(NumberCandidateFormat.KANJI, reordered.candidates.first { it.numberVariant != null }.numberVariant!!.format)
            revision++
            clearInvocations(localRepository)
            session.query(query)
            verify(localRepository, atLeastOnce()).commonPrefixSearchInUserDict(any())
            clearInvocations(localRepository)
            session.query(query.copy(numberCandidateConfig = NumberCandidateConfig(false, NumberCandidateFormat.entries.reversed())))
            verify(localRepository, atLeastOnce()).commonPrefixSearchInUserDict(any())
        }
    }

    @Test fun disabledEnhancementStillPrioritizesExistingSentenceNumerals() = runBlocking {
        for (backend in ConversionBackend.entries) {
            val result = KanaKanjiConversionSession(engine, backend).query(
                request("さんにんでいく", CandidateQueryMode.CONVERSION, true).copy(n = 4,
                    numberCandidateConfig = NumberCandidateConfig(false)))
            val half = result.candidates.indexOfFirst { it.string == "3人で行く" }
            val kanji = result.candidates.indexOfFirst { it.string == "三人で行く" }
            assertTrue("$backend: ${result.candidates.map { it.string }}", half >= 0 && kanji > half)
            assertEquals(NumberCandidateFormat.HALF_WIDTH, result.candidates[half].numberVariant!!.format)
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
