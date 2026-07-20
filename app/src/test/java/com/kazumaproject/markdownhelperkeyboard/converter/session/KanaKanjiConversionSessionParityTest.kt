package com.kazumaproject.markdownhelperkeyboard.converter.session

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
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
            }
        }
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

        failDuringAppend = false
        lookupCount = 0
        val recovered = incremental.query(
            request("きょう", CandidateQueryMode.PREDICTION, true).copy(
                userDictionaryRepository = repository,
            ),
        )
        val rebuilt = KanaKanjiConversionSession(localEngine, ConversionBackend.LEGACY).query(
            request("きょう", CandidateQueryMode.PREDICTION, true).copy(
                userDictionaryRepository = repository,
            ),
        )
        assertEquals(rebuilt.candidates.fingerprint(), recovered.candidates.fingerprint())
        assertEquals(rebuilt.bunsetsuResult?.splitPatterns, recovered.bunsetsuResult?.splitPatterns)
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
            }
        }
    }
}
