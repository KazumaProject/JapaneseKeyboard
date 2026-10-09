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
        for (bunsetsu in listOf(false,true)) for (input in listOf("にほん", "ごご", "さんご", "いっぱい", "しせん", "にだい", "ごばん", "きゅうばん", "さんだん", "さんぱつ", "ごえん", "ちょうじかん")) {
            val query = request(input,repository).copy(bunsetsuSeparation=bunsetsu)
            val before = baseline.query(query).candidates.first().string
            val candidates = integrated.query(query).candidates
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
        for (phrase in listOf("ひゃくにじゅうさんぼんをかう", "ねこがさんびきいる", "ごごさんじはんにあう", "ちょうさんど", "にほんにいく", "さんごがきれい", "いっぱいのむ")) {
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

    @Test fun grammarAndQuantitiesKeepMeaningAcrossCandidateRoutes() = runBlocking {
        val repository = mock<UserDictionaryRepository>()
        whenever(repository.commonPrefixSearchInUserDict(any())).thenReturn(emptyList())
        whenever(repository.exactMatchesForConversion(any())).thenReturn(emptyList())
        val cases = mapOf(
            "なのか" to "なのか", "ほんとうなのか" to "本当なのか",
            "なのかもしれない" to "なのかもしれない", "じゅうようなのか" to "重要なのか",
            "いつかであう" to "いつか出会う",
            "いつか" to "いつか", "ごばん" to "碁盤", "きゅうばん" to "吸盤",
            "さんだん" to "散弾", "さんぱつ" to "散髪",
            "ごえん" to "誤嚥", "ちょうじかん" to "長時間", "いつかまたあおう" to "いつかまた会おう",
            "いつかゆめがかなう" to "いつか夢が叶う", "いつかはわかる" to "いつかはわかる",
            "いつかのはなし" to "いつかの話", "いつかはつれる" to "いつかは釣れる",
            "くじであたる" to "くじで当たる", "なのかにやすむ" to "7日に休む",
            "なのかまえ" to "7日前", "くじをひく" to "くじを引く",
            "にじがでた" to "虹が出た", "にじゅうさんにち" to "23日",
            "いちまんじょう" to "10000条", "ふたつぶ" to "2粒", "くじかん" to "9時間",
            "こんげつなのかにやすむ" to "今月7日に休む",
            "こんげついつかにやすむ" to "今月5日に休む",
            "ほんをさんさつとえんぴつをにほんかう" to "本を3冊と鉛筆を2本買う",
        )
        val failures = mutableListOf<String>()
        for (backend in ConversionBackend.entries) for (mode in listOf(CandidateQueryMode.NO_TAB_DEFAULT, CandidateQueryMode.PREDICTION, CandidateQueryMode.CONVERSION)) {
            for (bunsetsu in listOf(false, true)) for ((input, expected) in cases) {
                val result = KanaKanjiConversionSession(engine, backend).query(request(input, repository).copy(mode=mode, bunsetsuSeparation=bunsetsu))
                val actual = result.candidates.firstOrNull()?.string
                println("COUNTER_ROOT $backend/$mode/$bunsetsu $input => ${result.candidates.take(5).map { it.string to it.score }}")
                if (actual != expected) failures += "$backend/$mode/$bunsetsu/$input: $actual (expected $expected)"
                val segments = result.candidateSegmentsByString[actual] ?: result.candidates.firstOrNull()?.conversionSegments.orEmpty()
                if (segments.isNotEmpty()) {
                    assertEquals(input, actual, segments.joinToString("") { it.output })
                    assertEquals(input, input.length, segments.last().inputEnd)
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        assertEquals("なのか", engine.getCandidatesEnglishKana("なのか").first().string)
        assertTrue(engine.getCandidatesEnglishKana("なのか").any { it.string == "7日" })
    }

    @Test fun numeralSplitsCannotEscapeQuantityAndClockBounds() = runBlocking {
        val repository = mock<UserDictionaryRepository>()
        whenever(repository.commonPrefixSearchInUserDict(any())).thenReturn(emptyList())
        whenever(repository.exactMatchesForConversion(any())).thenReturn(emptyList())
        val forbidden = mapOf(
            "にじゅうさんにち" to listOf("213日", "2１３日"),
            "いちまんじょう" to listOf("110000条"),
            "にじゅうよじ" to listOf("214時", "214:00"),
            "さんじろくじゅっぷん" to listOf("3時60分", "03:60"),
            "さんじろくじゅうびょう" to listOf("3時60秒"),
            "さんじはんじゅうごふん" to listOf("3時半15分", "03:3015分"),
        )
        for (bunsetsu in listOf(false,true)) for ((input, invalid) in forbidden) {
            val result = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY).query(request(input, repository).copy(n=32,bunsetsuSeparation=bunsetsu))
            assertTrue("$input: ${result.candidates.map { it.string }}", result.candidates.none { it.string in invalid })
        }
    }

    @Test fun grammarAndNumericPathsSurviveSystemNgramAndNarrowBeams() = runBlocking {
        val runtime = com.kazumaproject.markdownhelperkeyboard.converter.ngram.SystemNgramRuntime
        runtime.initialize(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>(), true)
        val production = TestEngineFactory.create(findPath = com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.FindPath(
            systemNgramDictionaryProvider = { runtime.current() }))
        val repository = mock<UserDictionaryRepository>()
        whenever(repository.commonPrefixSearchInUserDict(any())).thenReturn(emptyList())
        whenever(repository.exactMatchesForConversion(any())).thenReturn(emptyList())
        val cases = mapOf("なのか" to "なのか", "ほんとうなのか" to "本当なのか",
            "いつかまたあおう" to "いつかまた会おう", "くじであたる" to "くじで当たる",
            "にじゅうさんにち" to "23日", "いちまんじょう" to "10000条")
        val failures = mutableListOf<String>()
        for (beam in listOf(1, 2, 20)) for (bunsetsu in listOf(false, true)) {
            for ((input, expected) in cases) {
                val query = request(input, repository).copy(beamWidth = beam, bunsetsuSeparation = bunsetsu)
                val result = KanaKanjiConversionSession(production, ConversionBackend.LEGACY).query(query)
                if (result.candidates.first().string != expected)
                    failures += "$beam/$bunsetsu/$input: ${result.candidates.take(4).map { it.string }}"
            }
        }
        production.setCounterDictionaryEnabled(false)
        for (mode in CandidateQueryMode.entries) for (bunsetsu in listOf(false, true)) {
            assertEquals("$mode/$bunsetsu", "なのか", KanaKanjiConversionSession(production, ConversionBackend.LEGACY)
                .query(request("なのか",repository).copy(mode=mode,bunsetsuSeparation=bunsetsu)).candidates.first().string)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun explicitUserAndLearnedQuantitySpellingsAreNotRewritten() = runBlocking {
        val repository = mock<UserDictionaryRepository>()
        val learned = mock<com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository>()
        whenever(repository.commonPrefixSearchInUserDict(any())).thenReturn(emptyList())
        whenever(repository.exactMatchesForConversion(any())).thenReturn(emptyList())
        whenever(repository.searchByReadingPrefixSuspend(any(),any())).thenReturn(emptyList())
        whenever(repository.searchByReadingExactMatchSuspend(any())).thenReturn(emptyList())
        whenever(learned.findCommonPrefixes(any())).thenReturn(emptyList())
        whenever(learned.findExactMatchesForConversion(any())).thenReturn(emptyList())
        for (user in listOf(false,true)) {
            val word = com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord(
                word="七日",reading="なのか",posIndex=0,posScore=-20000)
            whenever(repository.commonPrefixSearchInUserDict("なのか")).thenReturn(if(user)listOf(word)else emptyList())
            whenever(repository.exactMatchesForConversion("なのか")).thenReturn(if(user)listOf(word)else emptyList())
            val entry = com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity("なのか","七日",-20000)
            whenever(learned.findCommonPrefixes("なのか")).thenReturn(if(user)emptyList()else listOf(entry))
            whenever(learned.findExactMatchesForConversion("なのか")).thenReturn(if(user)emptyList()else listOf(entry))
            for (backend in ConversionBackend.entries) for (bunsetsu in listOf(false,true)) {
                val result = KanaKanjiConversionSession(engine, backend).query(
                    request("なのか",repository).copy(learnRepository=learned,bunsetsuSeparation=bunsetsu))
                assertEquals("$user/$backend/$bunsetsu", "七日",result.candidates.first().string)
                assertEquals(if(user) com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_DICTIONARY
                    else com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_LEARNED_DICTIONARY,
                    result.candidates.first().type)
            }
        }
    }

    private fun request(input: String, repo: UserDictionaryRepository) = KanaKanjiQueryRequest(input, CandidateQueryMode.CONVERSION, true, 8, false, false, false, false, false, repo, null, false, false, false, 3000, 3000, 20, collectCandidateSegments=true)
    companion object {
        private lateinit var engine: KanaKanjiEngine
        @BeforeClass @JvmStatic fun setup() { engine=TestEngineFactory.create() }
    }
}
