package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryContentType
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileKey
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileSpecs
import com.kazumaproject.markdownhelperkeyboard.converter.session.CandidateQueryMode
import com.kazumaproject.markdownhelperkeyboard.converter.session.ConversionBackend
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryRequest
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoudsBoundaryConversionInstrumentedTest {
    @Test fun boundaryPrefixesSurviveAllQueryModes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        val engine = entry.kanaKanjiEngine()
        for (backend in ConversionBackend.entries) {
            for (bunsetsu in listOf(false, true)) {
                val session = KanaKanjiConversionSession(engine, backend)
                for (config in listOf(PredictionConfig(), PredictionConfig(japanesePredictionEnabled = false),
                    PredictionConfig(lookaheadCharacterCount = 6, systemCandidateLimit = 16))) {
                    for (input in listOf("にほん", "ち", "ちゅ", "ちゅう", "ちゅうおう", "に", "にほ", "にほんこうぎょう")) {
                        for (mode in CandidateQueryMode.entries) {
                            val result = session.query(KanaKanjiQueryRequest(
                                input = input, mode = mode, bunsetsuSeparation = bunsetsu, n = 8,
                                userDictionaryRepository = entry.userDictionaryRepository(), learnRepository = null,
                                omissionSearchEnabled = false, typoCorrectionJapaneseFlickEnabled = false,
                                typoCorrectionQwertyEnglishEnabled = false, typoCorrectionOffsetScore = 3000,
                                omissionSearchOffsetScore = 3000, beamWidth = 20, predictionConfig = config,
                            ))
                            assertTrue("$backend/$bunsetsu/$input/$mode", result.candidates.isNotEmpty())
                            if (input == "ちゅう" && mode != CandidateQueryMode.EISUKANA) {
                                assertTrue("Missing 中: $backend/$bunsetsu/$mode", result.candidates.any { it.string == "中" })
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun bundledReadingDictionariesStopAtTheirLastTerminals() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reader = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
            .dictionaryBinaryReader()
        var checked = 0
        for (spec in DictionaryFileSpecs.all.filter { it.contentType == DictionaryContentType.LOUDS_WITH_TERM_ID }) {
            val trie = reader.loadLoudsWithTermId(spec.key)
            val lbs = reader.loadYomiLbsIndex(spec.key, trie)
            // The final stored edges are where omitted zero words affect child lookup.
            var node = trie.LBS.length()
            repeat(2) {
                if (node <= 1) return@repeat
                node = trie.LBS.previousSetBit(node - 1)
                if (node <= 0 || !trie.isLeaf[node]) return@repeat
                val reading = trie.getLetter(node)
                val matches = trie.predictiveSearch(reading, lbs)
                assertTrue(spec.key.name, matches.contains(reading))
                assertTrue(spec.key.name, matches.all { trie.getNodeIndex(it, lbs) > 0 })
                assertEquals(spec.key.name, -1, trie.getNodeIndex(reading + " " + reading, lbs))
            }
            checked++
        }
        val english = reader.loadEnglishReading(DictionaryFileKey.ENGLISH_READING)
        val lbs = reader.loadEnglishReadingLbsIndex(english)
        var node = english.LBS.length()
        repeat(2) {
            if (node <= 1) return@repeat
            node = english.LBS.previousSetBit(node - 1)
            if (node <= 0 || !english.isLeaf[node]) return@repeat
            val reading = english.getLetter(node)
            val matches = english.predictiveSearch(reading, lbs)
            assertTrue(matches.contains(reading))
            assertTrue(matches.all { english.getNodeIndex(it, lbs) > 0 })
            assertEquals(-1, english.getNodeIndex(reading + " " + reading, lbs))
        }
        assertTrue("No bundled dictionaries checked", checked > 100)
        println("LOUDS_BOUNDARY bundledReadingDictionaries=${checked + 1}")
    }
}
