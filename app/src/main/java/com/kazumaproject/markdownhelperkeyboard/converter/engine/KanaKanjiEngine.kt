package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.converter.graph.GraphBuilder
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.dictionary.models.TokenEntry
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateTemp
import com.kazumaproject.viterbi.FindPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

class KanaKanjiEngine {

    private lateinit var yomiTrie: LOUDSWithTermId

    private lateinit var tangoTrie: LOUDS
    private lateinit var connectionIds: ShortArray
    private lateinit var tokenArray: TokenArray

    private lateinit var rank0ArrayLBSYomi: IntArray
    private lateinit var rank1ArrayLBSYomi: IntArray
    private lateinit var rank1ArrayIsLeaf: IntArray
    private lateinit var rank0ArrayTokenArrayBitvector: IntArray
    private lateinit var rank1ArrayTokenArrayBitvector: IntArray
    private lateinit var rank0ArrayLBSTango: IntArray
    private lateinit var rank1ArrayLBSTango: IntArray

    private lateinit var graphBuilder: GraphBuilder
    private lateinit var findPath: FindPath

    private lateinit var yomiLBSBooleanArray: BooleanArray

    fun buildEngine(
        graphBuilder: GraphBuilder,
        findPath: FindPath,
        connectionIdList: ShortArray,
        tangoTrie: LOUDS,
        yomiTrie: LOUDSWithTermId,
        tokenArray: TokenArray,
        rank0ArrayLBSYomi: IntArray,
        rank1ArrayLBSYomi: IntArray,
        rank1ArrayIsLeaf: IntArray,
        rank0ArrayTokenArrayBitvector: IntArray,
        rank1ArrayTokenArrayBitvector: IntArray,
        rank0ArrayLBSTango: IntArray,
        rank1ArrayLBSTango: IntArray,
        yomiLBSBooleanArray: BooleanArray
    ){
        this@KanaKanjiEngine.graphBuilder = graphBuilder
        this@KanaKanjiEngine.findPath = findPath

        this@KanaKanjiEngine.connectionIds = connectionIdList
        this@KanaKanjiEngine.tangoTrie = tangoTrie
        this@KanaKanjiEngine.tokenArray = tokenArray
        this@KanaKanjiEngine.yomiTrie = yomiTrie

        this@KanaKanjiEngine.rank0ArrayLBSYomi = rank0ArrayLBSYomi
        this@KanaKanjiEngine.rank1ArrayLBSYomi = rank1ArrayLBSYomi
        this@KanaKanjiEngine.rank1ArrayIsLeaf = rank1ArrayIsLeaf
        this@KanaKanjiEngine.rank0ArrayTokenArrayBitvector = rank0ArrayTokenArrayBitvector
        this@KanaKanjiEngine.rank1ArrayTokenArrayBitvector = rank1ArrayTokenArrayBitvector
        this@KanaKanjiEngine.rank0ArrayLBSTango = rank0ArrayLBSTango
        this@KanaKanjiEngine.rank1ArrayLBSTango = rank1ArrayLBSTango

        this@KanaKanjiEngine.yomiLBSBooleanArray = yomiLBSBooleanArray

    }

    fun buildEngine(
        yomi: LOUDSWithTermId,
        tango: LOUDS,
        token: TokenArray,
        connectionIdList: ShortArray
    ){
        this.yomiTrie = yomi
        this.tangoTrie = tango
        this.connectionIds = connectionIdList
        this.tokenArray = token
    }

    suspend fun getCandidates(
        input: String,
        n: Int
    ):List<Candidate> = CoroutineScope(Dispatchers.IO).async{
        val graph = graphBuilder.constructGraph(
            input,
            yomiTrie,
            tangoTrie,
            tokenArray,
            rank0ArrayLBSYomi,
            rank1ArrayLBSYomi,
            rank1ArrayIsLeaf,
            rank0ArrayTokenArrayBitvector,
            rank1ArrayTokenArrayBitvector,
            rank0ArrayLBSTango = rank0ArrayLBSTango,
            rank1ArrayLBSTango = rank1ArrayLBSTango,
            LBSBooleanArray = yomiLBSBooleanArray
        )

        val resultNBestFinal = async(Dispatchers.IO) {
            findPath.backwardAStar(graph, input.length, connectionIds, n)
        }.await()

        val yomiPartOf = yomiTrie.commonPrefixSearch(
            str = input,
            rank0Array = rank0ArrayLBSYomi,
            rank1Array = rank1ArrayLBSYomi,
        ).reversed()

        val a = async(Dispatchers.IO) {
            val yomiPart = yomiPartOf.map { yomi ->
                val termId  = yomiTrie.getTermId(
                    yomiTrie.getNodeIndex(
                        yomi,
                        rank1ArrayLBSYomi,
                        yomiLBSBooleanArray,
                    ),
                    rank1ArrayIsLeaf
                )
                val listToken: List<TokenEntry> = tokenArray.getListDictionaryByYomiTermId(
                    termId,
                    rank0ArrayTokenArrayBitvector,
                    rank1ArrayTokenArrayBitvector
                )
                return@map listToken.sortedBy { it.wordCost }.map {
                    Candidate(
                        when (it.nodeId) {
                            -2 -> yomi
                            -1 -> yomi.hiraToKata()
                            else -> tangoTrie.getLetter(
                                it.nodeId,
                                rank0ArrayLBSTango,
                                rank1ArrayLBSTango
                            )
                        },
                        2,
                        yomi.length.toUByte(),
                        it.wordCost.toInt(),
                        tokenArray.leftIds[it.posTableIndex.toInt()]
                    )
                }.distinctBy { it.string }. toMutableList()
            }
            yomiPart.flatten().distinctBy { it.string }.toMutableList()
        }.await()

        val hirakanaAndKana = listOf(
            Candidate(input,3,input.length.toUByte(),6000),
            Candidate(input.hiraToKata(),4,input.length.toUByte(),6000),
        )

        val longest = yomiPartOf.first()
        val longestConversionList = async(Dispatchers.IO) {
            nBestPathForLongest(longest,n * 2).map { Candidate(
                it.string,(5).toByte(),longest.length.toUByte(),it.wordCost, it.leftId
            ) }
        }.await()

        val secondPart = if (longest.length < input.length){
            async(Dispatchers.IO) {
                val tempSecondStr = input.substring(longest.length)
                val tempFirstStrConversionList = nBestPathForLongest(longest,n * 2)
                val tempSecondStrConversionList = nBestPathForLongest(tempSecondStr,n * 2 )
                val result = tempFirstStrConversionList.flatMap { item1 ->
                    tempSecondStrConversionList.map { item2 ->
                        Pair(item1.string + item2.string, item1.wordCost + item2.wordCost)
                    }
                }
                result.map { s ->
                    Candidate(
                        string =  s.first ,
                        type = (6).toByte(),
                        length = (longest.length + tempSecondStr.length).toUByte(),
                        s.second
                    )
                }
            }.await()
        }else{
            emptyList()
        }

        val finalResult = resultNBestFinal +
                secondPart.sortedBy { it.score }.filter { (it.score - resultNBestFinal.first().score) < 4000 } +
                longestConversionList +
                a +
                hirakanaAndKana
        return@async finalResult.distinctBy { it.string }
    }.await()

    suspend fun nBestPath(
        input: String,
        n: Int
    ): List<Candidate> {
        val graph = graphBuilder.constructGraph(
            input,
            yomiTrie,
            tangoTrie,
            tokenArray,
            rank0ArrayLBSYomi,
            rank1ArrayLBSYomi,
            rank1ArrayIsLeaf,
            rank0ArrayTokenArrayBitvector,
            rank1ArrayTokenArrayBitvector,
            rank0ArrayLBSTango = rank0ArrayLBSTango,
            rank1ArrayLBSTango = rank1ArrayLBSTango,
            LBSBooleanArray = yomiLBSBooleanArray
        )
        val result = findPath.backwardAStar(graph, input.length, connectionIds, n)
        result.apply {
            if (!this.map { it.string }.contains(input)){
                add(
                    Candidate(
                        string = input,
                        type = (3).toByte(),
                        length = (input.length).toUByte(),
                        score = (input.length) * 2000
                    )
                )
            }
            if (!this.map { it.string }.contains(input.hiraToKata())){
                add(
                    Candidate(
                        string = input.hiraToKata(),
                        type = (3).toByte(),
                        length = (input.length).toUByte(),
                        score = (input.length) * 2000
                    )
                )
            }
        }
        return result
    }

    suspend fun nBestPathForLongest(
        input: String,
        n: Int
    ): List<CandidateTemp> {
        val graph = graphBuilder.constructGraph(
            input,
            yomiTrie,
            tangoTrie,
            tokenArray,
            rank0ArrayLBSYomi,
            rank1ArrayLBSYomi,
            rank1ArrayIsLeaf,
            rank0ArrayTokenArrayBitvector,
            rank1ArrayTokenArrayBitvector,
            rank0ArrayLBSTango = rank0ArrayLBSTango,
            rank1ArrayLBSTango = rank1ArrayLBSTango,
            LBSBooleanArray = yomiLBSBooleanArray
        )
        return findPath.backwardAStarForLongest(graph, input.length, connectionIds, n)
    }

    suspend fun viterbiAlgorithm(
        input: String
    ): String {
        val graph = graphBuilder.constructGraph(
            input,
            yomiTrie,
            tangoTrie,
            tokenArray,
            rank0ArrayLBSYomi,
            rank1ArrayLBSYomi,
            rank1ArrayIsLeaf,
            rank0ArrayTokenArrayBitvector,
            rank1ArrayTokenArrayBitvector,
            rank0ArrayLBSTango = rank0ArrayLBSTango,
            rank1ArrayLBSTango = rank1ArrayLBSTango,
            LBSBooleanArray = yomiLBSBooleanArray
        )
        return findPath.viterbi(graph, input.length, connectionIds)
    }

    private fun precomputeLabelIndexMap(LBSInBoolArray: BooleanArray): Map<Int, Int> {
        val map = mutableMapOf<Int, Int>()
        var count = 0

        for (i in LBSInBoolArray.indices) {
            if (!LBSInBoolArray[i]) {
                map[count] = i + 1
                count++
            }
        }
        return map
    }
}