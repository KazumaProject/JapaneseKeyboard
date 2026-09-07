package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileKey
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class EnglishReadingDictionaryInstrumentedTest {

    @Test
    fun bundledEnglishReadingDictionaryUsesExactLookupForEveryReadingAndPrefix() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        val reader = entryPoint.dictionaryBinaryReader()
        val trie = reader.loadLoudsWithTermId(DictionaryFileKey.ENGLISH_READING_YOMI)
        val lbsIndex = reader.loadYomiLbsIndex(DictionaryFileKey.ENGLISH_READING_YOMI, trie)
        val leafIndex = reader.loadYomiLeafIndex(DictionaryFileKey.ENGLISH_READING_YOMI, trie)
        val registeredReadings = trie.predictiveSearch("", lbsIndex).toSet()
        val nonTerminalPrefixes = registeredReadings
            .asSequence()
            .flatMap { reading -> (1 until reading.length).asSequence().map(reading::take) }
            .filterNot(registeredReadings::contains)
            .toSet()

        assertTrue("Bundled English-reading dictionary was empty", registeredReadings.isNotEmpty())
        registeredReadings.forEach { reading ->
            val nodeIndex = trie.getNodeIndex(reading, lbsIndex)
            assertTrue("Registered reading did not resolve: $reading", nodeIndex > 0)
            assertTrue(
                "Registered reading did not resolve to a term ID: $reading",
                trie.getTermIdShortArray(nodeIndex, leafIndex) >= 0,
            )
        }
        nonTerminalPrefixes.forEach { prefix ->
            val nodeIndex = trie.getNodeIndex(prefix, lbsIndex)
            assertEquals("Non-terminal prefix resolved: $prefix", -1, nodeIndex)
            assertEquals(
                "Non-terminal prefix resolved to a term ID: $prefix",
                -1,
                trie.getTermIdShortArray(nodeIndex, leafIndex).toInt(),
            )
        }
        println(
            "ISSUE927_DICTIONARY_COVERAGE " +
                "registered=${registeredReadings.size} " +
                "nonTerminalPrefixes=${nonTerminalPrefixes.size}",
        )
    }

    @Test
    fun englishReadingCandidatesFollowTheSetting() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val hadPreviousValue = preferences.contains(
            DictionaryOverrideStore.ENGLISH_READING_ENABLED_PREFERENCE
        )
        val previousValue = preferences.getBoolean(
            DictionaryOverrideStore.ENGLISH_READING_ENABLED_PREFERENCE,
            true,
        )
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        val engine = entryPoint.kanaKanjiEngine()
        val repository = entryPoint.userDictionaryRepository()

        try {
            setEnabled(preferences, true)
            engine.applyDictionaryOverrideState(context)
            val enabledCandidates = convert(engine, repository, "かー", nBest = 4)
            val enabledEnglishCases = enabledCandidates.map { it.string }.toSet()
            assertTrue(
                "English reading case candidates were not found: $enabledCandidates",
                setOf("car", "Car", "CAR").all(enabledEnglishCases::contains),
            )
            val galleryCandidates = convert(engine, repository, "ぎゃらりー")
            assertTrue(
                "Gallery candidate was not found: $galleryCandidates",
                galleryCandidates.any { it.string == "gallery" },
            )
            val artGalleryCandidates = convert(engine, repository, "あーとぎゃらりー")
            assertTrue(
                "Art gallery candidate was not found: $artGalleryCandidates",
                artGalleryCandidates.any { it.string == "art gallery" },
            )
            ISSUE_927_OVERMATCHES.forEach { (input, unexpectedWord) ->
                val candidates = convert(engine, repository, input)
                assertFalse(
                    "Unexpected English-reading candidate for $input: $candidates",
                    candidates.any {
                        it.string.lowercase(Locale.ROOT) == unexpectedWord.lowercase(Locale.ROOT)
                    },
                )
            }

            setEnabled(preferences, false)
            engine.applyDictionaryOverrideState(context)
            val disabledCandidates = convert(engine, repository, "かー")
            assertFalse(
                "English reading case candidates remained after disabling: $disabledCandidates",
                disabledCandidates.any { it.string in setOf("car", "Car", "CAR") },
            )
        } finally {
            if (hadPreviousValue) {
                setEnabled(preferences, previousValue)
            } else {
                preferences.edit()
                    .remove(DictionaryOverrideStore.ENGLISH_READING_ENABLED_PREFERENCE)
                    .commit()
            }
            engine.applyDictionaryOverrideState(context)
        }
    }

    @Test
    fun multiBunsetsuConversionDoesNotUseNonTerminalEnglishReadings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val hadPreviousValue = preferences.contains(
            DictionaryOverrideStore.ENGLISH_READING_ENABLED_PREFERENCE
        )
        val previousValue = preferences.getBoolean(
            DictionaryOverrideStore.ENGLISH_READING_ENABLED_PREFERENCE,
            true,
        )
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        val engine = entryPoint.kanaKanjiEngine()
        val repository = entryPoint.userDictionaryRepository()

        try {
            setEnabled(preferences, true)
            engine.applyDictionaryOverrideState(context)
            MULTI_BUNSETSU_VALID_ENGLISH_CASES.forEach { (input, expectedWord) ->
                val segments = linkedMapOf<String, List<CandidateConversionSegment>>()
                val result = convertWithBunsetsu(
                    engine = engine,
                    repository = repository,
                    input = input,
                    nBest = 128,
                    candidateSegmentCollector = segments,
                )
                val trace = engine.convertWithTrace(input)

                assertTrue(
                    "Expected English-reading graph node for $input: ${trace.graphNodes}",
                    trace.graphNodes.any { node ->
                        node.source == "ENGLISH_READING" &&
                            node.tango.equals(expectedWord, ignoreCase = true)
                    },
                )
                assertTrue(
                    "No multi-segment conversion path for $input: $segments",
                    segments.values.any { it.size > 1 },
                )
                assertTrue(
                    "No bunsetsu boundary for $input: ${result.splitPatterns}",
                    result.splitPatterns.any { it.isNotEmpty() },
                )
                printMultiBunsetsuReport(input, result, segments)
            }
            MULTI_BUNSETSU_ISSUE_927_CASES.forEach { (input, unexpectedWord) ->
                val segments = linkedMapOf<String, List<CandidateConversionSegment>>()
                val result = convertWithBunsetsu(
                    engine = engine,
                    repository = repository,
                    input = input,
                    nBest = 128,
                    candidateSegmentCollector = segments,
                )
                val trace = engine.convertWithTrace(input)

                assertTrue("No conversion candidates for $input", result.candidates.isNotEmpty())
                assertTrue(
                    "No multi-segment conversion path for $input: $segments",
                    segments.values.any { it.size > 1 },
                )
                assertTrue(
                    "No bunsetsu boundary for $input: ${result.splitPatterns}",
                    result.splitPatterns.any { it.isNotEmpty() },
                )
                assertFalse(
                    "Unexpected English-reading candidate for $input: ${result.candidates}",
                    result.candidates.any { candidate ->
                        candidate.string.contains(unexpectedWord, ignoreCase = true)
                    },
                )
                assertFalse(
                    "Unexpected English-reading graph node for $input: ${trace.graphNodes}",
                    trace.graphNodes.any { node ->
                        node.source == "ENGLISH_READING" &&
                            node.tango.equals(unexpectedWord, ignoreCase = true)
                    },
                )
                printMultiBunsetsuReport(input, result, segments)
            }
        } finally {
            if (hadPreviousValue) {
                setEnabled(preferences, previousValue)
            } else {
                preferences.edit()
                    .remove(DictionaryOverrideStore.ENGLISH_READING_ENABLED_PREFERENCE)
                    .commit()
            }
            engine.applyDictionaryOverrideState(context)
        }
    }

    private suspend fun convert(
        engine: KanaKanjiEngine,
        repository: com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository,
        input: String,
        nBest: Int = 64,
    ) = engine.getCandidatesWithBunsetsuSeparation(
        input = input,
        n = nBest,
        mozcUtPersonName = false,
        mozcUTPlaces = false,
        mozcUTWiki = false,
        mozcUTNeologd = false,
        mozcUTWeb = false,
        userDictionaryRepository = repository,
        learnRepository = null,
        isOmissionSearchEnable = false,
        enableTypoCorrectionJapaneseFlick = false,
        enableTypoCorrectionQwertyEnglish = false,
        typoCorrectionOffsetScore = 3000,
        omissionSearchOffsetScore = 1900,
        beamWidth = 20,
    ).candidates

    private suspend fun convertWithBunsetsu(
        engine: KanaKanjiEngine,
        repository: com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository,
        input: String,
        nBest: Int,
        candidateSegmentCollector: MutableMap<String, List<CandidateConversionSegment>>,
    ): BunsetsuCandidateResult = engine.getCandidatesWithBunsetsuSeparation(
        input = input,
        n = nBest,
        mozcUtPersonName = false,
        mozcUTPlaces = false,
        mozcUTWiki = false,
        mozcUTNeologd = false,
        mozcUTWeb = false,
        userDictionaryRepository = repository,
        learnRepository = null,
        isOmissionSearchEnable = false,
        enableTypoCorrectionJapaneseFlick = false,
        enableTypoCorrectionQwertyEnglish = false,
        typoCorrectionOffsetScore = 3000,
        omissionSearchOffsetScore = 1900,
        beamWidth = 20,
        candidateSegmentCollector = candidateSegmentCollector,
    )

    private fun setEnabled(
        preferences: android.content.SharedPreferences,
        enabled: Boolean,
    ) {
        preferences.edit()
            .putBoolean(DictionaryOverrideStore.ENGLISH_READING_ENABLED_PREFERENCE, enabled)
            .commit()
    }

    private fun printMultiBunsetsuReport(
        input: String,
        result: BunsetsuCandidateResult,
        segments: Map<String, List<CandidateConversionSegment>>,
    ) {
        val candidateFingerprint = result.candidates.joinToString("\n") { candidate ->
            listOf(
                candidate.string,
                candidate.score,
                candidate.type,
                candidate.length,
                candidate.yomi,
                candidate.leftId,
                candidate.rightId,
            ).joinToString("\t")
        }.sha256()
        val segmentFingerprint = segments.entries.joinToString("\n") { (candidate, parts) ->
            "$candidate\t${parts.joinToString { "${it.inputStart}-${it.inputEnd}:${it.output}" }}"
        }.sha256()
        println(
            "ISSUE927_MULTI_BUNSETSU input=$input " +
                "candidates=${result.candidates.size} " +
                "candidateFingerprint=$candidateFingerprint " +
                "splitPatterns=${result.splitPatterns} " +
                "multiSegmentCandidates=${segments.count { it.value.size > 1 }} " +
                "segmentFingerprint=$segmentFingerprint",
        )
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        val ISSUE_927_OVERMATCHES = mapOf(
            "すな" to "Sioux",
            "すない" to "Through",
            "てい" to "REM",
            "りり" to "Rim",
        )
        val MULTI_BUNSETSU_ISSUE_927_CASES = mapOf(
            "すなをあるく" to "Sioux",
            "すないをとおる" to "Through",
            "ていにとまる" to "REM",
            "りりがさく" to "Rim",
        )
        val MULTI_BUNSETSU_VALID_ENGLISH_CASES = mapOf(
            "かーは" to "car",
            "ぎゃらりーへ" to "gallery",
        )
    }
}
