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

    @Test fun verifySecondReviewCasesOnDevice() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        val engine = entry.kanaKanjiEngine()
        for (backend in ConversionBackend.entries) for (mode in listOf(CandidateQueryMode.NO_TAB_DEFAULT, CandidateQueryMode.PREDICTION, CandidateQueryMode.CONVERSION)) {
            val session = KanaKanjiConversionSession(engine, backend)
            for (input in listOf("じゅうしょ", "にほんご", "じゅうじつ", "いつから", "ひとりじめ", "に", "－２えん", "-ふたり", "じゅうぜろえん", "にびゃっぷん", "さんひゃっこ")) {
                val q = request(input, mode, true, entry.userDictionaryRepository()).copy(n = 8)
                val on = session.query(q)
                val off = session.query(q.copy(numberCandidateConfig = NumberCandidateConfig(false)))
                assertEquals("$backend/$mode/$input", off.candidates.map { it.string }.toSet(), on.candidates.map { it.string }.toSet())
            }
            for ((input, expected) in mapOf("はつかにせんえんはらう" to "20日に1000円払う", "1,000えん" to "1000円", "１，２３４えん" to "1234円")) {
                val result = session.query(request(input, mode, true, entry.userDictionaryRepository()).copy(n = 1))
                assertTrue("$backend/$mode/$input", result.candidates.any { it.string == expected })
                assertFalse(result.candidates.any { it.string in setOf("20日2000円払う", "1,0円", "一,〇円") })
            }
        }
        for (enabled in listOf(false, true)) {
            assertTrue(engine.getCandidatesEnglishKana("０００２", numberCandidateConfig = NumberCandidateConfig(enabled)).any { it.string == "0002" })
        }
        assertFalse(engine.getCandidatesEnglishKana("ふたり", numberCandidateConfig = NumberCandidateConfig(false)).any { it.string == "2人" })
    }

    @Test fun verifyNasalCountersAndGroupedLiteralsAcrossModes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        val expected = mapOf("せんぷん" to "1000分",
            "せんぼん" to "1000本", "さんぜんびき" to "3000匹", "いちまんぼん" to "10000本",
            "いちまんびき" to "10000匹", "いちまんばい" to "10000杯",
            "１，２３４" to "1234", "1,234" to "1234", "０002" to "0002", "０００２" to "0002")
        val failures = mutableListOf<String>()
        for (backend in ConversionBackend.entries) for (mode in CandidateQueryMode.entries) {
            val session = KanaKanjiConversionSession(entry.kanaKanjiEngine(), backend)
            for ((input, half) in expected) {
                val q = request(input, mode, true, entry.userDictionaryRepository()).copy(n = 4)
                val result = session.query(q)
                val texts = result.candidates.map { it.string }
                val full = half.map { if (it in '0'..'9') it + 0xFEE0 else it }.joinToString("")
                val label = "$backend/$mode/$input"
                if (!texts.containsAll(listOf(half, full))) failures.add("$label missing=$half/$full actual=$texts")
                if (mode == CandidateQueryMode.EISUKANA && input == "せんぷん") {
                    val off = session.query(q.copy(numberCandidateConfig = NumberCandidateConfig(false))).candidates.map { it.string }
                    if (!off.containsAll(listOf(half, full))) failures.add("$label OFF missing=$half/$full actual=$off")
                }
            }
        }
        assertEquals(failures.joinToString("\n"), emptyList<String>(), failures)
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
