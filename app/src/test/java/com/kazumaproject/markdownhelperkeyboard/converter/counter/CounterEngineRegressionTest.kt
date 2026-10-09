package com.kazumaproject.markdownhelperkeyboard.converter.counter

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.session.*
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.*

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class CounterEngineRegressionTest {
    @Test fun rulesRankCompleteSpansAndPreserveOrdinaryWords() = runBlocking {
        val repository = mock<UserDictionaryRepository>()
        whenever(repository.commonPrefixSearchInUserDict(any())).thenReturn(emptyList())
        val session = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)
        val cases = mapOf("いっぽん" to "1本", "ひゃくにじゅうさんぼん" to "123本", "さんびき" to "3匹", "ごごさんじはん" to "午後3時半", "ひゃくにじゅうさんぼんをかう" to "123本を買う", "ねこがさんびきいる" to "猫が3匹いる", "ごごさんじはんにあう" to "午後3時半に合う", "にほんご" to "日本語", "きょう" to "今日", "よしよし" to "よしよし")
        for ((input, expected) in cases) {
            val result = session.query(request(input, repository))
            println("COUNTER_JVM $input => ${result.candidates.take(8).joinToString("|"){it.string+":"+it.score}}")
            assertEquals(input, expected, result.candidates.first().string)
            val segments = result.candidateSegmentsByString[expected] ?: result.candidates.first().conversionSegments
            assertEquals(expected, segments.joinToString("") { it.output })
            assertEquals(input.length, segments.last().inputEnd)
        }
    }
    @Test fun ambiguousOrdinaryReadingsKeepTheirExistingFirstCandidate() = runBlocking {
        val repository = mock<UserDictionaryRepository>()
        whenever(repository.commonPrefixSearchInUserDict(any())).thenReturn(emptyList())
        val baseline = KanaKanjiConversionSession(TestEngineFactory.create(counterConverter = null), ConversionBackend.LEGACY)
        val integrated = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)
        val failures = mutableListOf<String>()
        for (input in listOf("にほん", "ごご", "さんご", "いっぱい", "しせん")) {
            val before = baseline.query(request(input, repository)).candidates.first().string
            val candidates = integrated.query(request(input, repository)).candidates
            val after = candidates.first().string
            println("COUNTER_AMBIGUITY $input before=$before after=$after candidates=${candidates.take(8).map { it.string to it.score }}")
            if (before != after) failures += "$input: $before -> $after"
        }
        assertTrue(failures.joinToString(), failures.isEmpty())
    }

    @Test fun appendAndEditMatchFreshLattice() = runBlocking {
        val repository = mock<UserDictionaryRepository>()
        whenever(repository.commonPrefixSearchInUserDict(any())).thenReturn(emptyList())
        whenever(repository.exactMatchesForConversion(any())).thenReturn(emptyList())
        val incremental = KanaKanjiConversionSession(engine, ConversionBackend.INCREMENTAL_SESSION)
        for (phrase in listOf("ひゃくにじゅうさんぼんをかう", "ねこがさんびきいる", "ごごさんじはんにあう", "にほんにいく", "さんごがきれい", "いっぱいのむ")) {
            for (input in (1..phrase.length).map(phrase::take) + phrase.dropLast(1) + phrase + phrase.replace("さん", "よん")) {
                val request=request(input,repository)
                val fresh=KanaKanjiConversionSession(engine,ConversionBackend.LEGACY).query(request)
                val actual=incremental.query(request)
                assertEquals(input, fresh.candidates, actual.candidates)
                assertEquals(input, fresh.candidateSegmentsByString, actual.candidateSegmentsByString)
                assertEquals(input, fresh.bunsetsuResult?.splitPatternByCandidateString, actual.bunsetsuResult?.splitPatternByCandidateString)
            }
        }
    }
    @Test fun dictionaryToggleRestoresLegacyPathsAndInvalidatesRetainedLattices() = runBlocking {
        val repository = mock<UserDictionaryRepository>()
        whenever(repository.commonPrefixSearchInUserDict(any())).thenReturn(emptyList())
        whenever(repository.exactMatchesForConversion(any())).thenReturn(emptyList())
        val toggleEngine = TestEngineFactory.create()
        val baseline = TestEngineFactory.create(counterConverter = null)
        val inputs = listOf("ひゃくにじゅうさんぼんをかう", "ごごさんじはん", "いちへいほうめーとる")
        assertTrue(toggleEngine.getCandidatesEnglishKana("ごごさんじはん").any { it.string == "15:30" })
        for (backend in ConversionBackend.entries) {
            for (mode in CandidateQueryMode.entries) {
                for (bunsetsu in listOf(false, true)) {
                    val session = KanaKanjiConversionSession(toggleEngine, backend)
                    val requests = inputs.map { request(it, repository).copy(mode = mode, bunsetsuSeparation = bunsetsu) }
                    val enabled = requests.map { session.query(it) }
                    toggleEngine.setCounterDictionaryEnabled(false)
                    for (query in requests.flatMap { listOf(it, it.copy(input = it.input + "よ")) }) {
                        val expected = KanaKanjiConversionSession(baseline, ConversionBackend.LEGACY).query(query)
                        assertEquals("$backend/$mode/$bunsetsu/${query.input}", expected, session.query(query))
                    }
                    toggleEngine.setCounterDictionaryEnabled(true)
                    assertEquals("$backend/$mode/$bunsetsu", enabled, requests.map { session.query(it) })
                }
            }
        }
    }

    private fun request(input: String, repo: UserDictionaryRepository) = KanaKanjiQueryRequest(input, CandidateQueryMode.CONVERSION, true, 8, false, false, false, false, false, repo, null, false, false, false, 3000, 3000, 20, collectCandidateSegments=true)
    companion object {
        private lateinit var engine: KanaKanjiEngine
        @BeforeClass @JvmStatic fun setup() { engine=TestEngineFactory.create() }
    }
}
