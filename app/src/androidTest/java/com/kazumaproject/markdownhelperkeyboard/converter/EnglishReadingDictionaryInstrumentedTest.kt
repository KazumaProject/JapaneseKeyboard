package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    private fun setEnabled(
        preferences: android.content.SharedPreferences,
        enabled: Boolean,
    ) {
        preferences.edit()
            .putBoolean(DictionaryOverrideStore.ENGLISH_READING_ENABLED_PREFERENCE, enabled)
            .commit()
    }

    private companion object {
        val ISSUE_927_OVERMATCHES = mapOf(
            "すな" to "Sioux",
            "すない" to "Through",
            "てい" to "REM",
            "りり" to "Rim",
        )
    }
}
