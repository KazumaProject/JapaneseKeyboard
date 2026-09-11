package com.kazumaproject.markdownhelperkeyboard.converter.session

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TIME
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun numberWithCounterCandidatesAreAvailableAcrossModesAndBackends() = runBlocking {
        val cases = mapOf(
            "よじ" to "4時",
            "にじゅうよじ" to "24時",
            "くじ" to "9時",
            "さんにん" to "3人",
            "ごえん" to "5円",
            "にじゅっぷん" to "20分",
            "いっぷん" to "1分",
        )
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            for (bunsetsu in listOf(false, true)) {
                for (mode in CandidateQueryMode.entries) {
                    for ((input, expected) in cases) {
                        val label = "$input/$mode/$backend/bunsetsu=$bunsetsu"
                        val result = session.query(request(input, mode, bunsetsu))
                        val fullWidth = expected.map {
                            if (it in '0'..'9') it + 0xFEE0 else it
                        }.joinToString("")
                        for (value in listOf(expected, fullWidth)) {
                            assertTrue("$label missing $value", result.candidates.any {
                                it.string == value && it.length.toInt() == input.length
                            })
                        }
                        // Dictionary candidates may share the surface; the generated candidate
                        // must retain its type and POS IDs for full-input commits.
                        val generated = result.candidates.first {
                            it.string == expected && it.score == 8000
                        }
                        assertEquals(label, 2044.toShort(), generated.leftId)
                        assertEquals(label, (if (expected.endsWith("時")) 2015 else 2011).toShort(), generated.rightId)
                        assertEquals(label, if (expected.endsWith("時") || expected.endsWith("分")) {
                            CANDIDATE_TYPE_TIME
                        } else 18.toByte(), generated.type)
                    }
                }
            }
        }
    }

    @Test
    fun numberCandidatesKeepReadingGuardsAndIgnorePredictionAndSymbolToggles() = runBlocking {
        val forbidden = mapOf(
            "しじ" to setOf("4時", "４時"),
            "しえん" to setOf("4円", "４円"),
            "よせん" to setOf("4000", "４０００"),
            "よしよし" to setOf("4444", "４４４４"),
        )
        val disabled = PredictionConfig(
            japanesePredictionEnabled = false,
            englishPredictionEnabled = false,
            showSymbolCandidates = false,
        )
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            for (bunsetsu in listOf(false, true)) {
                for (mode in CandidateQueryMode.entries) {
                    for ((input, values) in forbidden) {
                        val result = session.query(request(input, mode, bunsetsu))
                        assertTrue("$input/$mode/$backend/$bunsetsu", result.candidates.none {
                            it.string in values
                        })
                    }
                    for ((input, expected) in mapOf("よじ" to "4時", "よんせん" to "4000")) {
                        val result = session.query(request(input, mode, bunsetsu).copy(
                            predictionConfig = disabled,
                        ))
                        assertTrue("$input/$mode/$backend/$bunsetsu", result.candidates.any {
                            it.string == expected
                        })
                    }
                }
            }
        }
    }

    @Test
    fun numberCandidateToggleCanBeChangedOnAnExistingSession() = runBlocking {
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            for (bunsetsu in listOf(false, true)) {
                for (mode in CandidateQueryMode.entries) {
                    val query = request("よじ", mode, bunsetsu)
                    val enabled = session.query(query)
                    val disabled = session.query(query.copy(predictionConfig = PredictionConfig(
                        japaneseNumberCandidatesEnabled = false,
                    )))
                    val restored = session.query(query)
                    val label = "$mode/$backend/$bunsetsu"
                    assertTrue(label, enabled.candidates.any { it.string == "4時" && it.score == 8000 })
                    assertFalse(label, disabled.candidates.any {
                        it.string in setOf("4時", "４時") && it.score in 8000..8001
                    })
                    assertEquals(label, enabled.candidates.fingerprint(), restored.candidates.fingerprint())
                    // Directly typed digits are outside the reading-generation toggle.
                    val digits = session.query(request("1234", mode, bunsetsu).copy(
                        predictionConfig = PredictionConfig(japaneseNumberCandidatesEnabled = false),
                    ))
                    assertTrue(label, digits.candidates.any { it.string == "1234" })
                }
            }
        }
    }

    @Test
    fun ordinaryWordsAndSentencesKeepExactlyTheSameCandidatesAndSegments() = runBlocking {
        val corpus = listOf(
            "しじ", "しえん", "しにん", "よせん", "しせん", "くせん", "くちょう",
            "よしよし", "ごご", "さんご", "いちいち", "さんさん", "ろくろく",
            "へんじ", "かんじ", "だいじ", "にんじん", "えんじん", "たぶん",
            "ひとり", "ふたり", "よんほん", "よんかい", "しがつ", "よっか",
            "よじまで", "よじです", "ごごよじ", "あしたはよじ", "さんにんで",
            "にじゅっぷんまつ", "きょうはいいてんき", "こんにちは", "ありがとう",
        )
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            for (bunsetsu in listOf(false, true)) {
                for (mode in CandidateQueryMode.entries) {
                    for (input in corpus) {
                        val query = request(input, mode, bunsetsu)
                        val baseline = session.query(query.copy(predictionConfig = PredictionConfig(
                            japaneseNumberCandidatesEnabled = false,
                        )))
                        val enabled = session.query(query)
                        val label = "$input/$mode/$backend/$bunsetsu"
                        assertEquals(label, baseline.candidates.fingerprint(), enabled.candidates.fingerprint())
                        assertEquals(label, baseline.candidateSegmentsByString, enabled.candidateSegmentsByString)
                        assertEquals(label, baseline.bunsetsuResult, enabled.bunsetsuResult)
                    }
                }
            }
        }
    }

    @Test
    fun ambiguousCounterReadingsOnlyAddExpectedVariantsWithoutReorderingDictionaryCandidates() = runBlocking {
        val cases = mapOf(
            "にじ" to "2時", "さんじ" to "3時", "くじ" to "9時", "ごえん" to "5円",
            "よじ" to "4時", "さんにん" to "3人", "にじゅっぷん" to "20分",
            "よにん" to "4人", "よんえん" to "4円", "きゅうえん" to "9円",
        )
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            for (bunsetsu in listOf(false, true)) {
                for (mode in CandidateQueryMode.entries) {
                    for ((input, expected) in cases) {
                        val query = request(input, mode, bunsetsu)
                        val baseline = session.query(query.copy(predictionConfig = PredictionConfig(
                            japaneseNumberCandidatesEnabled = false,
                        )))
                        val enabled = session.query(query)
                        val remaining = enabled.candidates.toMutableList()
                        val label = "$input/$mode/$backend/$bunsetsu"
                        val fullWidth = expected.map {
                            if (it in '0'..'9') it + 0xFEE0 else it
                        }.joinToString("")
                        for ((value, score) in listOf(expected to 8000, fullWidth to 8001)) {
                            val index = remaining.indexOfFirst { it.string == value && it.score == score }
                            assertTrue("$label missing generated $value", index >= 0)
                            remaining.removeAt(index)
                        }
                        if (mode == CandidateQueryMode.EISUKANA) {
                            val index = remaining.indexOfFirst { it.string == expected && it.score == 3000 }
                            assertTrue(label, index >= 0)
                            remaining.removeAt(index)
                        }
                        assertEquals(label, baseline.candidates.fingerprint(), remaining.fingerprint())
                        assertEquals(label, baseline.candidateSegmentsByString, enabled.candidateSegmentsByString)
                    }
                }
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
    fun symbolEmojiCandidateDisplaySettingsHideCandidatesInEveryMode() = runBlocking {
        val session = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)
        val hiddenConfig = PredictionConfig(
            showSymbolCandidates = false,
            showEmojiCandidates = false,
            showEmoticonCandidates = false,
        )

        for (mode in listOf(
            CandidateQueryMode.PREDICTION,
            CandidateQueryMode.CONVERSION,
            CandidateQueryMode.NO_TAB_DEFAULT,
        )) {
            for (bunsetsu in listOf(false, true)) {
                val neko = session.query(
                    request("ねこ", mode, bunsetsu).copy(predictionConfig = hiddenConfig),
                )
                assertFalse(
                    "$mode/bunsetsu=$bunsetsu emoji",
                    neko.candidates.any { it.string.contains("🐈") },
                )

                val niko = session.query(
                    request("にこ", mode, bunsetsu).copy(predictionConfig = hiddenConfig),
                )
                assertFalse(
                    "$mode/bunsetsu=$bunsetsu emoticon",
                    niko.candidates.any { it.string == "(^o^)" },
                )

                val ichi = session.query(
                    request("いち", mode, bunsetsu).copy(predictionConfig = hiddenConfig),
                )
                assertFalse(
                    "$mode/bunsetsu=$bunsetsu value-based symbol",
                    ichi.candidates.any { it.string == "①" },
                )
            }
        }
    }

    @Test
    fun symbolEmojiCandidateDisplaySettingsAreIndependent() = runBlocking {
        val session = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)

        val emojiHidden = session.query(
            request("ねこ", CandidateQueryMode.PREDICTION, bunsetsu = false).copy(
                predictionConfig = PredictionConfig(showEmojiCandidates = false),
            ),
        )
        assertFalse(emojiHidden.candidates.any { it.string.contains("🐈") })
        assertTrue(
            session.query(request("にこ", CandidateQueryMode.PREDICTION, false))
                .candidates.any { it.string == "(^o^)" },
        )
        assertTrue(
            session.query(request("さんかく", CandidateQueryMode.PREDICTION, false))
                .candidates.any { it.type.toInt() == 13 },
        )

        val emoticonHidden = session.query(
            request("にこ", CandidateQueryMode.PREDICTION, bunsetsu = false).copy(
                predictionConfig = PredictionConfig(showEmoticonCandidates = false),
            ),
        )
        assertFalse(emoticonHidden.candidates.any { it.string == "(^o^)" })
        assertTrue(
            session.query(request("ねこ", CandidateQueryMode.PREDICTION, false))
                .candidates.any { it.string.contains("🐈") },
        )

        val symbolHidden = session.query(
            request("さんかく", CandidateQueryMode.PREDICTION, bunsetsu = false).copy(
                predictionConfig = PredictionConfig(showSymbolCandidates = false),
            ),
        )
        assertTrue(symbolHidden.candidates.none { it.type.toInt() == 13 })
        assertFalse(
            session.query(
                request("いち", CandidateQueryMode.CONVERSION, false).copy(
                    predictionConfig = PredictionConfig(showSymbolCandidates = false),
                ),
            )
                .candidates.any { it.string == "①" },
        )
        assertTrue(
            session.query(request("ねこ", CandidateQueryMode.PREDICTION, false))
                .candidates.any { it.string.contains("🐈") },
        )
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
