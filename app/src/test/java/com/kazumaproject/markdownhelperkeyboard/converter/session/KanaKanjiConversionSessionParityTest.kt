package com.kazumaproject.markdownhelperkeyboard.converter.session

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KanaKanjiConversionSessionParityTest {

    @Test
    fun incrementalSessionMatchesLegacyAcrossModesAndBunsetsu() = runBlocking {
        val legacy = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)
        val incremental = KanaKanjiConversionSession(
            engine,
            ConversionBackend.INCREMENTAL_SESSION,
        )
        val transitions = listOf(
            "き" to CandidateQueryMode.PREDICTION,
            "きょ" to CandidateQueryMode.PREDICTION,
            "きょう" to CandidateQueryMode.PREDICTION,
            "きょう" to CandidateQueryMode.CONVERSION,
            "きょう" to CandidateQueryMode.NO_TAB_DEFAULT,
            "きょう" to CandidateQueryMode.EISUKANA,
            "きょうは" to CandidateQueryMode.PREDICTION,
        )

        for (bunsetsu in listOf(false, true)) {
            for ((input, mode) in transitions) {
                val request = request(input, mode, bunsetsu)
                val legacyResult = legacy.query(request)
                val incrementalResult = incremental.query(request)

                assertEquals(
                    "$input/$mode/bunsetsu=$bunsetsu candidates",
                    legacyResult.candidates.fingerprint(),
                    incrementalResult.candidates.fingerprint(),
                )
                assertEquals(
                    "$input/$mode/bunsetsu=$bunsetsu splits",
                    legacyResult.bunsetsuResult?.splitPatterns,
                    incrementalResult.bunsetsuResult?.splitPatterns,
                )
                assertEquals(
                    "$input/$mode/bunsetsu=$bunsetsu split map",
                    legacyResult.bunsetsuResult?.splitPatternByCandidateString,
                    incrementalResult.bunsetsuResult?.splitPatternByCandidateString,
                )
                assertEquals(
                    "$input/$mode/bunsetsu=$bunsetsu conversion segments",
                    legacyResult.candidateSegmentsByString,
                    incrementalResult.candidateSegmentsByString,
                )
            }
        }
    }

    @Test
    fun segmentCollectionUsesExactPathNodesWhenBunsetsuDisplayIsDisabled() = runBlocking {
        val result = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY).query(
            request("ひを", CandidateQueryMode.CONVERSION, bunsetsu = false),
        )

        assertEquals(
            listOf(Triple(0, 1, "火"), Triple(1, 2, "を")),
            result.candidateSegmentsByString.getValue("火を").map {
                Triple(it.inputStart, it.inputEnd, it.output)
            },
        )
        assertEquals(null, result.bunsetsuResult)
    }

    @Test
    fun conversionKeepsExactSymbolEmojiEmoticonAndValueBasedNumberCandidates() = runBlocking {
        val session = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)

        val neko = session.query(request("ねこ", CandidateQueryMode.CONVERSION, bunsetsu = false))
        assertTrue(neko.candidates.map { it.string }.any { it.contains("🐈") })

        val niko = session.query(request("にこ", CandidateQueryMode.CONVERSION, bunsetsu = false))
        assertTrue(niko.candidates.map { it.string }.contains("(^o^)"))

        val ichi = session.query(request("いち", CandidateQueryMode.CONVERSION, bunsetsu = false))
        assertTrue(ichi.candidates.map { it.string }.contains("①"))
    }

    @Test
    fun predictionIncludesExactSymbolEmojiEmoticonCandidates() = runBlocking {
        val session = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)

        val exactEmoji = session.query(
            request("ねこ", CandidateQueryMode.PREDICTION, bunsetsu = false),
        )
        assertTrue(exactEmoji.candidates.map { it.string }.any { it.contains("🐈") })

        val exactEmojiWithBunsetsu = session.query(
            request("ねこ", CandidateQueryMode.PREDICTION, bunsetsu = true),
        )
        assertTrue(exactEmojiWithBunsetsu.candidates.map { it.string }.any { it.contains("🐈") })

        val exactEmoticon = session.query(
            request("にこ", CandidateQueryMode.PREDICTION, bunsetsu = false),
        )
        assertTrue(exactEmoticon.candidates.map { it.string }.contains("(^o^)"))

        val exactEmoticonWithBunsetsu = session.query(
            request("にこ", CandidateQueryMode.PREDICTION, bunsetsu = true),
        )
        assertTrue(exactEmoticonWithBunsetsu.candidates.map { it.string }.contains("(^o^)"))

        val exactSymbol = session.query(
            request("さんかく", CandidateQueryMode.PREDICTION, bunsetsu = false),
        )
        assertTrue(exactSymbol.candidates.any { it.type.toInt() == 13 })

        val prefixEmoji = session.query(
            request("うれし", CandidateQueryMode.PREDICTION, bunsetsu = false),
        )
        assertTrue(prefixEmoji.candidates.any { it.type.toInt() == 11 })

        val prefixEmoticon = session.query(
            request("にこに", CandidateQueryMode.PREDICTION, bunsetsu = false),
        )
        assertTrue(prefixEmoticon.candidates.any { it.type.toInt() == 12 })

        val prefixSymbol = session.query(
            request("さんか", CandidateQueryMode.PREDICTION, bunsetsu = false),
        )
        assertTrue(prefixSymbol.candidates.any { it.type.toInt() == 13 })

        val disabledConfig = PredictionConfig(symbolEmojiEnabled = false)
        val disabledPrefixEmoji = session.query(
            request("うれし", CandidateQueryMode.PREDICTION, bunsetsu = false).copy(
                predictionConfig = disabledConfig,
            ),
        )
        assertTrue(disabledPrefixEmoji.candidates.none { it.type.toInt() == 11 })

        val disabledPrefixEmoticon = session.query(
            request("にこに", CandidateQueryMode.PREDICTION, bunsetsu = false).copy(
                predictionConfig = disabledConfig,
            ),
        )
        assertTrue(disabledPrefixEmoticon.candidates.none { it.type.toInt() == 12 })

        val disabledPrefixSymbol = session.query(
            request("さんか", CandidateQueryMode.PREDICTION, bunsetsu = false).copy(
                predictionConfig = disabledConfig,
            ),
        )
        assertTrue(disabledPrefixSymbol.candidates.none { it.type.toInt() == 13 })
    }

    @Test
    fun cancelledInPlaceAppendIsDiscardedBeforeNextRequest() = runBlocking {
        val localEngine = TestEngineFactory.create()
        val repository = mock<UserDictionaryRepository>()
        var failDuringAppend = false
        var lookupCount = 0
        whenever(repository.commonPrefixSearchInUserDict(any())).thenAnswer {
            lookupCount++
            if (failDuringAppend && lookupCount == 3) {
                throw kotlinx.coroutines.CancellationException("controlled partial append")
            }
            emptyList<UserWord>()
        }
        whenever(repository.exactMatchesForConversion(any())).thenAnswer {
            lookupCount++
            if (failDuringAppend && lookupCount == 3) {
                throw kotlinx.coroutines.CancellationException("controlled partial append")
            }
            emptyList<UserWord>()
        }
        val incremental = KanaKanjiConversionSession(
            localEngine,
            ConversionBackend.INCREMENTAL_SESSION,
        )
        incremental.query(
            request("きょ", CandidateQueryMode.PREDICTION, true).copy(
                userDictionaryRepository = repository,
            ),
        )

        failDuringAppend = true
        lookupCount = 0
        var cancellationObserved = false
        try {
            incremental.query(
                request("きょう", CandidateQueryMode.PREDICTION, true).copy(
                    userDictionaryRepository = repository,
                ),
            )
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancellationObserved = true
        }
        assertTrue(cancellationObserved)
        assertEquals("きょ", incremental.committedInput())

        failDuringAppend = false
        lookupCount = 0
        val recovered = incremental.query(
            request("きょうは", CandidateQueryMode.PREDICTION, true).copy(
                userDictionaryRepository = repository,
            ),
        )
        val rebuilt = KanaKanjiConversionSession(localEngine, ConversionBackend.LEGACY).query(
            request("きょうは", CandidateQueryMode.PREDICTION, true).copy(
                userDictionaryRepository = repository,
            ),
        )
        assertEquals(rebuilt.candidates.fingerprint(), recovered.candidates.fingerprint())
        assertEquals(rebuilt.bunsetsuResult?.splitPatterns, recovered.bunsetsuResult?.splitPatterns)
        assertEquals("きょうは", incremental.committedInput())
    }

    @Test
    fun completedGraphSurvivesCancellationAfterForwardDp() = runBlocking {
        val localEngine = TestEngineFactory.create()
        val incremental = KanaKanjiConversionSession(
            localEngine,
            ConversionBackend.INCREMENTAL_SESSION,
        )
        incremental.query(request("きょ", CandidateQueryMode.PREDICTION, true))

        var cancelAfterForwardDp = true
        incremental.setAfterForwardDpForTest {
            if (cancelAfterForwardDp) {
                cancelAfterForwardDp = false
                throw kotlinx.coroutines.CancellationException("controlled post-forward cancellation")
            }
        }
        var cancellationObserved = false
        try {
            incremental.query(request("きょう", CandidateQueryMode.PREDICTION, true))
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancellationObserved = true
        }

        assertTrue(cancellationObserved)
        assertEquals("きょう", incremental.committedInput())

        incremental.setAfterForwardDpForTest(null)
        val recovered = incremental.query(request("きょうは", CandidateQueryMode.PREDICTION, true))
        val rebuilt = KanaKanjiConversionSession(localEngine, ConversionBackend.LEGACY).query(
            request("きょうは", CandidateQueryMode.PREDICTION, true),
        )
        assertEquals(rebuilt.candidates.fingerprint(), recovered.candidates.fingerprint())
        assertEquals(rebuilt.bunsetsuResult?.splitPatterns, recovered.bunsetsuResult?.splitPatterns)
        assertEquals("きょうは", incremental.committedInput())
    }

    @Test
    fun oneCharacterAppendReusesCommittedForwardDpAndMatchesLegacy() = runBlocking {
        val localEngine = TestEngineFactory.create()
        val incremental = KanaKanjiConversionSession(
            localEngine,
            ConversionBackend.INCREMENTAL_SESSION,
        ).also { it.enablePerformanceProbe() }
        val legacy = KanaKanjiConversionSession(localEngine, ConversionBackend.LEGACY)

        incremental.query(request("きょう", CandidateQueryMode.PREDICTION, true))
        val incrementalResult = incremental.query(
            request("きょうは", CandidateQueryMode.PREDICTION, true),
        )
        val legacyResult = legacy.query(
            request("きょうは", CandidateQueryMode.PREDICTION, true),
        )

        assertTrue(incremental.performanceSnapshot()?.forwardDpReused == true)
        assertEquals(legacyResult.candidates.fingerprint(), incrementalResult.candidates.fingerprint())
        assertEquals(
            legacyResult.bunsetsuResult?.splitPatterns,
            incrementalResult.bunsetsuResult?.splitPatterns,
        )
    }

    private fun request(
        input: String,
        mode: CandidateQueryMode,
        bunsetsu: Boolean,
    ) = KanaKanjiQueryRequest(
        input = input,
        mode = mode,
        bunsetsuSeparation = bunsetsu,
        n = 4,
        mozcUtPersonName = false,
        mozcUtPlaces = false,
        mozcUtWiki = false,
        mozcUtNeologd = false,
        mozcUtWeb = false,
        userDictionaryRepository = userDictionaryRepository,
        learnRepository = null,
        omissionSearchEnabled = false,
        typoCorrectionJapaneseFlickEnabled = false,
        typoCorrectionQwertyEnglishEnabled = false,
        typoCorrectionOffsetScore = 3000,
        omissionSearchOffsetScore = 1900,
        beamWidth = 20,
        collectCandidateSegments = true,
    )

    private fun List<Candidate>.fingerprint(): List<List<Any?>> = map { candidate ->
        listOf(
            candidate.string,
            candidate.type,
            candidate.length,
            candidate.score,
            candidate.yomi,
            candidate.leftId,
            candidate.rightId,
        )
    }

    companion object {
        private lateinit var engine: com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
        private lateinit var userDictionaryRepository: UserDictionaryRepository

        @JvmStatic
        @BeforeClass
        fun setUp() {
            engine = TestEngineFactory.create()
            userDictionaryRepository = mock()
            runBlocking {
                whenever(userDictionaryRepository.commonPrefixSearchInUserDict(any()))
                    .thenReturn(emptyList())
                whenever(userDictionaryRepository.exactMatchesForConversion(any()))
                    .thenReturn(emptyList())
            }
        }
    }
}
