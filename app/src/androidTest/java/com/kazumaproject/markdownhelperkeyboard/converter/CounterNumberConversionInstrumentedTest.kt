package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.converter.number.*
import com.kazumaproject.markdownhelperkeyboard.converter.session.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CounterNumberConversionInstrumentedTest {
    @Test fun verifyRequestedVariantsAndSentencePathsAcrossProductionModes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        val engine = entry.kanaKanjiEngine()
        val repository = entry.userDictionaryRepository()
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            for (mode in CandidateQueryMode.entries) for (bunsetsu in listOf(false, true)) {
                for (order in permutations(NumberCandidateFormat.entries.toList())) {
                    for (input in listOf("いちまん", "にまん", "いちまんえん", "にまんえん", "はつか", "はつかかん")) {
                        val result = session.query(request(input, mode, bunsetsu, repository).copy(numberCandidateConfig = NumberCandidateConfig(order = order)))
                        assertEquals("$backend/$mode/$bunsetsu/$input", order, result.candidates.mapNotNull { it.numberVariant?.format }.distinct())
                        assertTrue(result.candidates.all { it.string == it.commitText })
                    }
                }
                if (mode != CandidateQueryMode.EISUKANA) {
                    val input = "はつかにいちまんえんはらう"
                    val result = session.query(request(input, mode, bunsetsu, repository))
                    val variant = result.candidates.firstOrNull { it.string == "20日に10000円払う" }
                    assertNotNull("$backend/$mode/$bunsetsu: ${result.candidates.map { it.string }}", variant)
                    assertEquals(variant!!.string, variant.commitText)
                    assertEquals(variant.string, result.candidateSegmentsByString.getValue(variant.string).joinToString("") { it.output })
                }
            }
        }
    }

    private fun request(input: String, mode: CandidateQueryMode, bunsetsu: Boolean, repository: UserDictionaryRepository) = KanaKanjiQueryRequest(
        input = input, mode = mode, bunsetsuSeparation = bunsetsu, n = 8,
        mozcUtPersonName = false, mozcUtPlaces = false, mozcUtWiki = false, mozcUtNeologd = false, mozcUtWeb = false,
        userDictionaryRepository = repository, learnRepository = null, omissionSearchEnabled = false,
        typoCorrectionJapaneseFlickEnabled = false, typoCorrectionQwertyEnglishEnabled = false,
        typoCorrectionOffsetScore = 3000, omissionSearchOffsetScore = 3000, beamWidth = 20, collectCandidateSegments = true,
    )
    private fun <T> permutations(values: List<T>): List<List<T>> = if (values.isEmpty()) listOf(emptyList())
        else values.flatMap { first -> permutations(values - first).map { listOf(first) + it } }
}
