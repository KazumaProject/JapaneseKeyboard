package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.converter.engine.TripleDictionaryData
import com.kazumaproject.markdownhelperkeyboard.converter.session.*
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClassifiedExactCandidatesInstrumentedTest {
    @Test fun completeReadingsSurviveAllQueryModesAndSearchSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.contains(".classifiedprobe"))
        val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        val engine = entry.kanaKanjiEngine()
        val store = DictionaryOverrideStore(context, DictionaryOverrideValidator())
        val previous = ClassifiedDictionaries.all.associate { it.category to store.isOptionalBundledEnabled(it.category) }
        try {
            ClassifiedDictionaries.all.forEach { store.setOptionalBundledEnabled(it.category, true) }
            engine.applyDictionaryOverrideState(context)
            @Suppress("UNCHECKED_CAST")
            val dictionaries = KanaKanjiEngine::class.java.getDeclaredField("classifiedDictionaries")
                .apply { isAccessible = true }.get(engine) as Map<DictionaryCategory, TripleDictionaryData>
            val inputs = listOf("あ", "しょう", "ほのか", "かな", "かいぞくばんせんたいせいくりっどへきさごん",
                "やくようしょくぶつしどうせんたー", "じーぴーてぃーしっくすてんいちそる")
            val expected = inputs.associateWith { input ->
                val words = linkedSetOf<String>()
                for (data in dictionaries.values) {
                    val node = data.yomiTrie.getNodeIndex(input, data.succinctBitVectorLBSYomi)
                    if (node <= 0) continue
                    val term = data.yomiTrie.getTermId(node, data.succinctBitVectorIsLeafYomi)
                    if (term < 0) continue
                    data.tokenArray.forEachDictionaryByYomiTermId(term, data.succinctBitVectorTokenArray) { _, _, id ->
                        words.add(data.surfaceFor(id, input))
                    }
                }
                words
            }
            assertTrue(expected.getValue("しょう").containsAll(listOf("SHO", "紫耀")))
            assertEquals(48, expected.getValue("ほのか").size)
            assertEquals(1, expected.getValue("かな").size)
            assertTrue(expected.getValue("やくようしょくぶつしどうせんたー").contains("薬用植物指導センター"))
            assertTrue(expected.getValue("じーぴーてぃーしっくすてんいちそる").contains("GPT-6.1 Sol"))
            var checked = 0
            for (backend in ConversionBackend.entries) {
                val session = KanaKanjiConversionSession(engine, backend)
                for (mode in listOf(CandidateQueryMode.NO_TAB_DEFAULT, CandidateQueryMode.CONVERSION, CandidateQueryMode.PREDICTION)) {
                    for (bunsetsu in listOf(false, true)) for (input in inputs) for (limited in listOf(false, true)) {
                        val request = KanaKanjiQueryRequest(
                            input, mode, bunsetsu, if (limited) 2 else 16, ClassifiedDictionaries.categories,
                            entry.userDictionaryRepository(), null, false, false, false, 0, 0,
                            if (limited) 1 else 100,
                            PredictionConfig(japanesePredictionEnabled = !limited, externalMozcEnabled = !limited,
                                minimumInputLength = if (limited) 8 else 3, systemCandidateLimit = if (limited) 1 else 16),
                            collectCandidateSegments = true,
                        )
                        val result = session.query(request)
                        val words = result.candidates.map { it.string }.toSet()
                        assertTrue("$backend/$mode/$bunsetsu/$input/$limited missing=${expected.getValue(input) - words}",
                            words.containsAll(expected.getValue(input)))
                        val firstAuxiliary = result.candidates.indexOfFirst { it.type == 3.toByte() }
                        if (firstAuxiliary >= 0) {
                            for (surface in expected.getValue(input)) {
                                assertTrue("Exact word $surface is behind auxiliary candidates: $backend/$mode/$bunsetsu/$limited",
                                    result.candidates.indexOfFirst { it.string == surface } < firstAuxiliary)
                            }
                        }
                        if (input == "しょう" && limited) println("CLASSIFIED_EXACT_RANK $backend/$mode/$bunsetsu " +
                            result.candidates.withIndex().filter { it.value.string in setOf("SHO", "紫耀") }
                                .map { "${it.value.string}=${it.index + 1}:${it.value.score}" })
                        if (input == "しょう" && limited) {
                            for (surface in listOf("SHO", "紫耀")) {
                                val candidate = result.candidates.first { it.string == surface }
                                assertEquals(input, candidate.yomi)
                                assertEquals(input.length, candidate.length.toInt())
                                assertTrue(DictionaryCategory.CLASSIFIED_PERSON_PERSONAL_NAME_ORIGIN_JAPANESE in candidate.dictionaryCategories)
                                assertEquals(listOf(CandidateConversionSegment(0, input.length, surface)),
                                    result.candidateSegmentsByString[surface])
                                if (bunsetsu) assertEquals(emptyList<Int>(), result.bunsetsuResult!!.splitPatternByCandidateString[surface])
                            }
                        }
                        val disabled = session.query(request.copy(enabledDictionaryCategories = emptySet()))
                        assertTrue(disabled.candidates.none { it.dictionaryCategories.isNotEmpty() })
                        checked++
                    }
                }
            }
            println("CLASSIFIED_EXACT checked=$checked allCategories=${dictionaries.size} homophones=48 shortAndLong=true")
        } finally {
            previous.forEach { (category, enabled) -> store.setOptionalBundledEnabled(category, enabled) }
            engine.applyDictionaryOverrideState(context)
        }
    }
}
