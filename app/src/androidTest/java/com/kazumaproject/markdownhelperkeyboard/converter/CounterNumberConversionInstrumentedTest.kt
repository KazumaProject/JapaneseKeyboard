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

    @Test fun verifyReviewedRegressionsAndSmallNBestOnDevice() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(entry.kanaKanjiEngine(), backend)
            for (mode in listOf(CandidateQueryMode.NO_TAB_DEFAULT, CandidateQueryMode.PREDICTION, CandidateQueryMode.CONVERSION)) {
                for (bunsetsu in listOf(false, true)) for (n in listOf(1, 4, 8)) {
                    for (order in permutations(NumberCandidateFormat.entries.toList())) for (enabled in listOf(false, true)) {
                        for (input in listOf("にじゅっぷんまって", "にかげつかかる", "さんにんでいく", "いちまんはらう")) {
                            val result = session.query(request(input, mode, bunsetsu, entry.userDictionaryRepository()).copy(
                                n = n, numberCandidateConfig = NumberCandidateConfig(enabled, order)))
                            val label = "$backend/$mode/$bunsetsu/$n/$order/$enabled/$input"
                            assertFalse(label, result.candidates.any { it.string.contains("210分") })
                            val variants = result.candidates.filter { it.numberVariant != null }
                            if (enabled) assertTrue(label, variants.any { it.numberVariant!!.format == NumberCandidateFormat.FULL_WIDTH })
                            variants.groupBy { it.numberVariant!!.group }.values.forEach { group ->
                                val ranks = group.map { order.indexOf(it.numberVariant!!.format) }
                                assertEquals(label, ranks.sorted(), ranks)
                            }
                            variants.forEach { candidate ->
                                val segments = result.candidateSegmentsByString.getValue(candidate.string)
                                assertEquals(label, candidate.string, segments.joinToString("") { it.output })
                                assertEquals(label, segments, candidate.conversionSegments)
                                result.bunsetsuResult?.splitPatternByCandidateString?.get(candidate.string).orEmpty().forEach { split ->
                                    assertTrue(label, segments.any { it.inputStart == split })
                                }
                            }
                        }
                    }
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
