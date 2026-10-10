package com.kazumaproject.markdownhelperkeyboard.converter.counter

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.converter.session.*
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.*

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class CounterCandidateRankingTest {
    @Test fun homonymsPreserveAllNormalCandidatesAndTheirMetadata() = runBlocking {
        val repository = repository()
        val readings = listOf("なのか", "いつか", "いっぽん", "ナノカ", "イッポン", "サンカク", "ゴチョウダイ", "いつかであう", "ごちょうだい", "はちまんじょう",
            "まんにわ", "さんかく", "さんかくをかく", "いっせんをかくす", "いちめんにひろがる", "いちいせい", "いちじしょり", "いちえんこうか", "１８きん", "いちじていし",
            "いちじょうかおる", "ほんとうなのか", "なのかにやすむ", "こんげつなのかにやすむ")
        for (backend in ConversionBackend.entries) {
            val actualSession = KanaKanjiConversionSession(enabled, backend)
            val baselineSession = KanaKanjiConversionSession(disabled, ConversionBackend.LEGACY)
            for (mode in CandidateQueryMode.entries) for (bunsetsu in listOf(false,true)) for (n in listOf(8,32)) {
                for (reading in readings) {
                    val query = request(reading, repository).copy(mode=mode,bunsetsuSeparation=bunsetsu,n=n)
                    val baseline = baselineSession.query(query)
                    val actual = actualSession.query(query)
                    val context = "$backend/$mode/$bunsetsu/$n/$reading"
                    assertEquals(context, baseline.candidates.take(3), actual.candidates.take(minOf(3,baseline.candidates.size)))
                    val normalStrings = baseline.candidates.mapTo(HashSet()) { it.string }
                    assertEquals(context, baseline.candidates, actual.candidates.filter { it.string in normalStrings })
                    for (candidate in baseline.candidates) {
                        assertEquals(context, baseline.candidateSegmentsByString[candidate.string], actual.candidateSegmentsByString[candidate.string])
                        assertEquals(context, baseline.bunsetsuResult?.splitPatternByCandidateString?.get(candidate.string),
                            actual.bunsetsuResult?.splitPatternByCandidateString?.get(candidate.string))
                    }
                    val added = actual.candidates.filterNot { it.string in normalStrings }
                    assertEquals(context, added.size, added.map { it.string }.distinct().size)
                    assertEquals(context, actual.bunsetsuResult?.candidates, actual.bunsetsuResult?.let { actual.candidates })
                    assertEquals(context, actual.candidates, actualSession.query(query.copy(collectCandidateSegments=false)).candidates)
                }
            }
        }
    }

    @Test fun uniqueExactQuantitiesKeepTheirPriorityAndSentencesRemainAligned() = runBlocking {
        val repository = repository()
        val cases = mapOf("ひゃくにじゅうさんぼん" to "123本", "さんびき" to "3匹")
        for (mode in CandidateQueryMode.entries) for (bunsetsu in listOf(false,true)) {
            val session = KanaKanjiConversionSession(enabled, ConversionBackend.LEGACY)
            for ((input, expected) in cases) {
                val result = session.query(request(input,repository).copy(mode=mode,bunsetsuSeparation=bunsetsu))
                println("HYBRID_UNIQUE $mode/$bunsetsu/$input ${result.candidates.take(8).map { it.string }}")
                assertEquals("$mode/$bunsetsu/$input", expected, result.candidates.first().string)
            }
        }
        for (mode in listOf(CandidateQueryMode.NO_TAB_DEFAULT, CandidateQueryMode.PREDICTION, CandidateQueryMode.CONVERSION)) for (n in listOf(4,8,32)) for (bunsetsu in listOf(false,true)) for ((input, expected) in mapOf(
            "ねこがさんびきいる" to "猫が3匹いる",
            "ひゃくにじゅうさんぼんをかう" to "123本を買う",
            "ほんをさんさつとえんぴつをにほんかう" to "本を3冊と鉛筆を2本買う",
            "ごごさんじはんにあう" to "午後3時半に合う")) {
            val result = KanaKanjiConversionSession(enabled, ConversionBackend.LEGACY).query(
                request(input,repository).copy(mode=mode,bunsetsuSeparation=bunsetsu,n=n))
            println("HYBRID_SENTENCE $mode/$n/$bunsetsu/$input ${result.candidates.take(12).map { it.string }}")
            assertTrue("$input: ${result.candidates.map { it.string }}", result.candidates.any { it.string == expected })
            val segments = result.candidateSegmentsByString[expected].orEmpty()
            assertEquals(expected, segments.joinToString("") { it.output })
            assertEquals(input.length, segments.last().inputEnd)
            assertTrue(segments.zipWithNext().all { (a,b) -> a.inputEnd == b.inputStart })
        }
        val result = KanaKanjiConversionSession(enabled, ConversionBackend.LEGACY).query(request("なのか",repository))
        assertTrue(result.candidates.indexOfFirst { it.string == "7日" } >= 3)
        assertFalse(KanaKanjiConversionSession(enabled, ConversionBackend.LEGACY).query(request("１８きん",repository))
            .candidates.any { it.string == "18基ん" })
    }

    @Test fun literalQuantitiesKeepTheNormalPathMetadataAndToggleBeforeInitialization() = runBlocking {
        val uninitialized = com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine()
        uninitialized.setCounterDictionaryEnabled(false)
        uninitialized.setCounterDictionaryEnabled(true)
        val repository = repository()
        for (bunsetsu in listOf(false,true)) {
            val query = request("２つぶをたべる",repository).copy(bunsetsuSeparation=bunsetsu,n=32)
            val baseline = KanaKanjiConversionSession(disabled, ConversionBackend.LEGACY).query(query)
            val result = KanaKanjiConversionSession(enabled, ConversionBackend.LEGACY).query(query)
            for (candidate in baseline.candidates) {
                assertEquals(baseline.candidateSegmentsByString[candidate.string], result.candidateSegmentsByString[candidate.string])
                assertEquals(baseline.bunsetsuResult?.splitPatternByCandidateString?.get(candidate.string),
                    result.bunsetsuResult?.splitPatternByCandidateString?.get(candidate.string))
            }
            for ((surface,segments) in result.candidateSegmentsByString)
                assertEquals(surface,segments.joinToString(""){it.output})
        }
    }

    private suspend fun repository() = mock<UserDictionaryRepository>().also {
        whenever(it.commonPrefixSearchInUserDict(any())).thenReturn(emptyList())
        whenever(it.exactMatchesForConversion(any())).thenReturn(emptyList())
    }
    private fun request(input: String, repo: UserDictionaryRepository) = KanaKanjiQueryRequest(input,
        CandidateQueryMode.CONVERSION,true,8,false,false,false,false,false,repo,null,false,false,false,
        3000,3000,20,collectCandidateSegments=true)
    companion object {
        private lateinit var enabled: com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
        private lateinit var disabled: com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
        @BeforeClass @JvmStatic fun setup() {
            enabled=TestEngineFactory.create()
            disabled=TestEngineFactory.create(counterConverter=null)
        }
    }
}
