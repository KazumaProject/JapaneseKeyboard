package com.kazumaproject.markdownhelperkeyboard.converter.number

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.FindPath
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.NgramRuleScorer
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.CompositeSystemNgramDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.PackedSystemNgramDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.*
import com.kazumaproject.markdownhelperkeyboard.converter.session.*
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import com.kazumaproject.markdownhelperkeyboard.user_template.database.UserTemplate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.BeforeClass
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NumberCandidateReviewRegressionTest {
    @Test fun ordinarySentenceKeepsSplitSearchAlternativesWithProductionNgramData() = runBlocking {
        val assets = listOf(File("app/src/main/assets"), File("src/main/assets")).first { it.exists() }
        val ngram = CompositeSystemNgramDictionary(listOf("system_ngram.dat", "system_ngram_unigram.dat")
            .map { PackedSystemNgramDictionary.read(File(assets, "ngram/$it").readBytes()) })
        val productionEngine = TestEngineFactory.create(findPath = FindPath(systemNgramDictionaryProvider = { ngram }))
        for (backend in ConversionBackend.entries) for (mode in japaneseModes) {
            val session = KanaKanjiConversionSession(productionEngine, backend)
            for (n in listOf(1, 4, 8)) for (enabled in listOf(false, true)) {
                val result = session.query(request("もちをやく", mode, n).copy(
                    numberCandidateConfig = NumberCandidateConfig(enabled)))
                val bunsetsu = requireNotNull(result.bunsetsuResult)
                assertEquals("$backend/$mode/$n/$enabled", listOf(listOf(3), emptyList(), listOf(1, 3)),
                    bunsetsu.splitPatterns)
                assertEquals(listOf(3), bunsetsu.primarySplitPositions)
            }
        }
    }

    @Test fun ordinaryWordsInvalidReadingsAndUnsupportedExpressionsDoNotGainNumberPaths() = runBlocking {
        val inputs = listOf("じゅうしょ", "きゅうじゅうみん", "にほんご", "じゅうじつ", "いつから",
            "ふつかよい", "ひとりじめ", "に", "し", "ご", "よ", "く", "－２えん", "＋２えん",
            "-ついたち", "-ふたり", "+はつか", "1.5えん", "１．５えん", "いちてんごえん",
            "まいなすにえん", "じゅうぜろえん", "ひゃくれいえん", "いちまんぜろえん", "にびゃっぷん", "さんひゃっこ")
        for (backend in ConversionBackend.entries) for (mode in japaneseModes) {
            val session = KanaKanjiConversionSession(engine, backend)
            for (input in inputs) for (n in listOf(1, 4, 8)) {
                val q = request(input, mode, n)
                val on = session.query(q)
                val off = session.query(q.copy(numberCandidateConfig = NumberCandidateConfig(false)))
                assertEquals("$backend/$mode/$n/$input", off.candidates.map { it.string }.toSet(), on.candidates.map { it.string }.toSet())
            }
        }
    }

    @Test fun particleAndCommaKeepTheActualValueAcrossFormats() = runBlocking {
        val expected = mapOf("はつかにせんえんはらう" to listOf("20日に1000円払う", "２０日に１０００円払う", "二十日に千円払う"),
            "1,000えん" to listOf("1000円", "１０００円", "千円"),
            "100,000えん" to listOf("100000円", "１０００００円", "十万円"),
            "1,234えん" to listOf("1234円", "１２３４円", "千二百三十四円"),
            "１，２３４えん" to listOf("1234円", "１２３４円", "千二百三十四円"))
        for (backend in ConversionBackend.entries) for (mode in japaneseModes) {
            val session = KanaKanjiConversionSession(engine, backend)
            for ((input, texts) in expected) for (n in listOf(1,4,8)) for (bunsetsu in listOf(false,true)) {
                for (order in permutations(NumberCandidateFormat.entries)) {
                    val q = request(input,mode,n).copy(bunsetsuSeparation=bunsetsu, numberCandidateConfig=NumberCandidateConfig(order=order))
                    val result = session.query(q)
                    val label = "$backend/$mode/$n/$bunsetsu/$order/$input"
                    assertTrue(label + result.candidates.map { it.string }, result.candidates.map { it.string }.containsAll(texts))
                    assertFalse(label, result.candidates.any { it.string in setOf("1,0円", "100,0円", "一,〇円", "20日2000円払う", "２０日２０００円払う") })
                    for (candidate in result.candidates.filter { it.numberVariant != null }) {
                        assertEquals(label, candidate.string, candidate.commitText)
                        val segments = result.candidateSegmentsByString.getValue(candidate.string)
                        assertEquals(label, candidate.string, segments.joinToString("") { it.output })
                        assertEquals(label, segments, candidate.conversionSegments)
                        assertEquals(label, 0, segments.first().inputStart)
                        assertEquals(label, input.length, segments.last().inputEnd)
                        segments.zipWithNext().forEach { (a,b) -> assertEquals(label,a.inputEnd,b.inputStart) }
                        assertTrue(label,segments.all { it.leftId != null && it.rightId != null })
                        result.bunsetsuResult?.splitPatternByCandidateString?.get(candidate.string).orEmpty().forEach { split ->
                            assertTrue(label,segments.any { it.inputStart == split })
                        }
                    }
                    result.candidates.filter { it.numberVariant != null }.groupBy { it.numberVariant!!.group }.values.forEach { group ->
                        val ranks = group.map { order.indexOf(it.numberVariant!!.format) }
                        assertEquals(label,ranks.sorted(),ranks)
                    }
                }
            }
        }
    }

    @Test fun englishKanaKeepsLiteralZerosAndHonoursDisabledSupplementation() {
        for (input in listOf("0002", "０００２", "０002")) for (enabled in listOf(false,true)) {
            val candidates = engine.getCandidatesEnglishKana(input, numberCandidateConfig=NumberCandidateConfig(enabled))
            assertTrue("$input/$enabled", candidates.map { it.string }.containsAll(listOf("0002","０００２")))
        }
        for (input in listOf("ひとり","ふたり")) {
            val off = engine.getCandidatesEnglishKana(input, numberCandidateConfig=NumberCandidateConfig(false))
            assertFalse(input, off.any { it.string in setOf("1人","１人","2人","２人") })
            assertTrue(input,engine.getCandidatesEnglishKana(input).any { it.numberVariant != null })
        }
        assertTrue(engine.getCandidatesEnglishKana("さんにん",numberCandidateConfig=NumberCandidateConfig(false)).any { it.string == "3人" })
    }

    @Test fun actualUserAndTemplateRowsInheritFormatAfterDeduplication() = runBlocking {
        val input = "さんにんでいく"
        val core = KanaKanjiConversionSession(engine,ConversionBackend.LEGACY).query(request(input,CandidateQueryMode.CONVERSION,4)).candidates
        val text = core.first { it.numberVariant?.format == NumberCandidateFormat.KANJI }.string
        val rows = listOf(UserWord(word=text,reading=input,posIndex=0,posScore=50).toUserDictionaryCandidate(),
            UserTemplate(word=text,reading=input,posIndex=0,posScore=50).toUserTemplateCandidate())
        for (row in rows) for (order in permutations(NumberCandidateFormat.entries)) {
            val merged = NumberCandidateComposer.inheritVariantIdentity(listOf(row)+core).distinctBy { it.string }
            val result = NumberCandidateComposer.reorder(input,merged,NumberCandidateConfig(order=order))
            assertNotNull(result.single { it.string==text }.numberVariant)
            assertEquals(row.type,result.single { it.string==text }.type)
            val group = result.filter { it.numberVariant?.group==merged.first().numberVariant?.group }
            val ranks=group.map { order.indexOf(it.numberVariant!!.format) }
            assertEquals(ranks.sorted(),ranks)
        }
    }

    @Test fun connectionMatrixReplacementInvalidatesOrderOnlyCache() = runBlocking {
        val field = KanaKanjiEngine::class.java.getDeclaredField("connectionMatrix").apply { isAccessible=true }
        val previous = field.get(engine) as ConnectionMatrix.CostTable
        try {
            for (backend in ConversionBackend.entries) {
                field.set(engine,previous)
                val session=KanaKanjiConversionSession(engine,backend)
                val q=request("さんにんでいく",CandidateQueryMode.CONVERSION,4)
                session.query(q)
                field.set(engine,object: ConnectionMatrix.CostTable {
                    override val matrixSize=previous.matrixSize
                    override val entryCount=previous.entryCount
                    override fun cost(rid:Int,lid:Int)=previous.cost(rid,lid)*2
                })
                val reversed=q.copy(numberCandidateConfig=NumberCandidateConfig(order=NumberCandidateFormat.entries.reversed()))
                clearInvocations(repository)
                val actual=session.query(reversed)
                verify(repository, atLeastOnce()).commonPrefixSearchInUserDict(any())
                val fresh=KanaKanjiConversionSession(engine,backend).query(reversed)
                assertEquals(backend.name,fresh,actual)
            }
        } finally { field.set(engine,previous) }
    }

    @Test fun dictionaryPublicationAndScoringProviderChangesInvalidateOrderCache() = runBlocking {
        var scorer = NgramRuleScorer(emptyList())
        val localEngine = TestEngineFactory.create(findPath = FindPath(ngramRuleScorerProvider = { scorer }))
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(localEngine, backend)
            var q = request("さんにんでいく", CandidateQueryMode.CONVERSION, 4)
            session.query(q)
            for (update in listOf<() -> Unit>(
                { localEngine.releasePersonNamesDictionary() },
                { scorer = NgramRuleScorer.createDefault() },
            )) {
                update()
                q = q.copy(numberCandidateConfig = NumberCandidateConfig(order = q.numberCandidateConfig.order.reversed()))
                clearInvocations(repository)
                val actual = session.query(q)
                verify(repository, atLeastOnce()).commonPrefixSearchInUserDict(any())
                assertEquals(KanaKanjiConversionSession(localEngine, backend).query(q), actual)
            }
        }
    }

    @Test fun environmentChangeDuringConversionPreventsSavingOrderCache() = runBlocking {
        val field = KanaKanjiEngine::class.java.getDeclaredField("connectionMatrix").apply { isAccessible = true }
        val previous = field.get(engine) as ConnectionMatrix.CostTable
        val localRepository = mock<UserDictionaryRepository>()
        var replaceOnLookup = true
        whenever(localRepository.commonPrefixSearchInUserDict(any())).thenAnswer {
            if (replaceOnLookup) {
                replaceOnLookup = false
                field.set(engine, object : ConnectionMatrix.CostTable {
                    override val matrixSize = previous.matrixSize
                    override val entryCount = previous.entryCount
                    override fun cost(rid: Int, lid: Int) = previous.cost(rid, lid)
                })
            }
            emptyList<UserWord>()
        }
        whenever(localRepository.exactMatchesForConversion(any())).thenReturn(emptyList())
        try {
            for (backend in ConversionBackend.entries) {
                field.set(engine, previous)
                replaceOnLookup = true
                val session = KanaKanjiConversionSession(engine, backend)
                val q = request("さんにんでいく", CandidateQueryMode.CONVERSION, 4).copy(userDictionaryRepository = localRepository)
                session.query(q)
                clearInvocations(localRepository)
                val reversed = q.copy(numberCandidateConfig = NumberCandidateConfig(order = NumberCandidateFormat.entries.reversed()))
                val result = session.query(reversed)
                verify(localRepository, atLeastOnce()).commonPrefixSearchInUserDict(any())
                assertEquals(KanaKanjiConversionSession(engine, backend).query(reversed), result)
            }
        } finally { field.set(engine, previous) }
    }

    private fun request(input:String,mode:CandidateQueryMode,n:Int)=KanaKanjiQueryRequest(
        input=input,mode=mode,bunsetsuSeparation=true,n=n,mozcUtPersonName=false,mozcUtPlaces=false,
        mozcUtWiki=false,mozcUtNeologd=false,mozcUtWeb=false,userDictionaryRepository=repository,
        learnRepository=null,omissionSearchEnabled=false,typoCorrectionJapaneseFlickEnabled=false,
        typoCorrectionQwertyEnglishEnabled=false,typoCorrectionOffsetScore=3000,omissionSearchOffsetScore=3000,
        beamWidth=20,collectCandidateSegments=true)
    private fun <T> permutations(items:List<T>):List<List<T>> = if(items.isEmpty()) listOf(emptyList())
        else items.flatMap { head -> permutations(items-head).map { listOf(head)+it } }
    companion object {
        private val japaneseModes=listOf(CandidateQueryMode.NO_TAB_DEFAULT,CandidateQueryMode.PREDICTION,CandidateQueryMode.CONVERSION)
        private lateinit var engine:KanaKanjiEngine
        private lateinit var repository:UserDictionaryRepository
        @JvmStatic @BeforeClass fun setup() {
            engine=TestEngineFactory.create()
            repository=mock()
            runBlocking {
                whenever(repository.commonPrefixSearchInUserDict(any())).thenReturn(emptyList<UserWord>())
                whenever(repository.exactMatchesForConversion(any())).thenReturn(emptyList<UserWord>())
            }
        }
    }
}
