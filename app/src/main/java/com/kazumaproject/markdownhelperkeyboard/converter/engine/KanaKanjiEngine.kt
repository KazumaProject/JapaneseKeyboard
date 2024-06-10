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

    private lateinit var graphBuilder: GraphBuilder
    private lateinit var findPath: FindPath

    private lateinit var connectionIds: ShortArray

    private lateinit var systemYomiTrie: LOUDSWithTermId
    private lateinit var systemTangoTrie: LOUDS
    private lateinit var systemTokenArray: TokenArray

    private lateinit var systemRank0ArrayLBSYomi: IntArray
    private lateinit var systemRank1ArrayLBSYomi: IntArray
    private lateinit var systemRank1ArrayIsLeaf: IntArray
    private lateinit var systemRank0ArrayTokenArrayBitvector: IntArray
    private lateinit var systemRank1ArrayTokenArrayBitvector: IntArray
    private lateinit var systemRank0ArrayLBSTango: IntArray
    private lateinit var systemRank1ArrayLBSTango: IntArray
    private lateinit var systemYomiLBSBooleanArray: BooleanArray

    private lateinit var singleKanjiYomiTrie: LOUDSWithTermId
    private lateinit var singleKanjiTangoTrie: LOUDS
    private lateinit var singleKanjiTokenArray: TokenArray

    private lateinit var singleKanjiRank0ArrayLBSYomi: IntArray
    private lateinit var singleKanjiRank1ArrayLBSYomi: IntArray
    private lateinit var singleKanjiRank1ArrayIsLeaf: IntArray
    private lateinit var singleKanjiRank0ArrayTokenArrayBitvector: IntArray
    private lateinit var singleKanjiRank1ArrayTokenArrayBitvector: IntArray
    private lateinit var singleKanjiRank0ArrayLBSTango: IntArray
    private lateinit var singleKanjiRank1ArrayLBSTango: IntArray
    private lateinit var singleKanjiYomiLBSBooleanArray: BooleanArray

    fun buildEngine(
        graphBuilder: GraphBuilder,
        findPath: FindPath,
        connectionIdList: ShortArray,
        systemTangoTrie: LOUDS,
        systemYomiTrie: LOUDSWithTermId,
        systemTokenArray: TokenArray,
        systemRank0ArrayLBSYomi: IntArray,
        systemRank1ArrayLBSYomi: IntArray,
        systemRank1ArrayIsLeaf: IntArray,
        systemRank0ArrayTokenArrayBitvector: IntArray,
        systemRank1ArrayTokenArrayBitvector: IntArray,
        systemRank0ArrayLBSTango: IntArray,
        systemRank1ArrayLBSTango: IntArray,
        systemYomiLBSBooleanArray: BooleanArray,
        singleKanjiTangoTrie: LOUDS,
        singleKanjiYomiTrie: LOUDSWithTermId,
        singleKanjiTokenArray: TokenArray,
        singleKanjiRank0ArrayLBSYomi: IntArray,
        singleKanjiRank1ArrayLBSYomi: IntArray,
        singleKanjiRank1ArrayIsLeaf: IntArray,
        singleKanjiRank0ArrayTokenArrayBitvector: IntArray,
        singleKanjiRank1ArrayTokenArrayBitvector: IntArray,
        singleKanjiRank0ArrayLBSTango: IntArray,
        singleKanjiRank1ArrayLBSTango: IntArray,
        singleKanjiYomiLBSBooleanArray: BooleanArray,
    ){
        this@KanaKanjiEngine.graphBuilder = graphBuilder
        this@KanaKanjiEngine.findPath = findPath

        this@KanaKanjiEngine.connectionIds = connectionIdList

        this@KanaKanjiEngine.systemTangoTrie = systemTangoTrie
        this@KanaKanjiEngine.systemTokenArray = systemTokenArray
        this@KanaKanjiEngine.systemYomiTrie = systemYomiTrie

        this@KanaKanjiEngine.systemRank0ArrayLBSYomi = systemRank0ArrayLBSYomi
        this@KanaKanjiEngine.systemRank1ArrayLBSYomi = systemRank1ArrayLBSYomi
        this@KanaKanjiEngine.systemRank1ArrayIsLeaf = systemRank1ArrayIsLeaf
        this@KanaKanjiEngine.systemRank0ArrayTokenArrayBitvector = systemRank0ArrayTokenArrayBitvector
        this@KanaKanjiEngine.systemRank1ArrayTokenArrayBitvector = systemRank1ArrayTokenArrayBitvector
        this@KanaKanjiEngine.systemRank0ArrayLBSTango = systemRank0ArrayLBSTango
        this@KanaKanjiEngine.systemRank1ArrayLBSTango = systemRank1ArrayLBSTango
        this@KanaKanjiEngine.systemYomiLBSBooleanArray = systemYomiLBSBooleanArray

        this@KanaKanjiEngine.singleKanjiTangoTrie = singleKanjiTangoTrie
        this@KanaKanjiEngine.singleKanjiTokenArray = singleKanjiTokenArray
        this@KanaKanjiEngine.singleKanjiYomiTrie = singleKanjiYomiTrie

        this@KanaKanjiEngine.singleKanjiRank0ArrayLBSYomi = singleKanjiRank0ArrayLBSYomi
        this@KanaKanjiEngine.singleKanjiRank1ArrayLBSYomi = singleKanjiRank1ArrayLBSYomi
        this@KanaKanjiEngine.singleKanjiRank1ArrayIsLeaf = singleKanjiRank1ArrayIsLeaf
        this@KanaKanjiEngine.singleKanjiRank0ArrayTokenArrayBitvector = singleKanjiRank0ArrayTokenArrayBitvector
        this@KanaKanjiEngine.singleKanjiRank1ArrayTokenArrayBitvector = singleKanjiRank1ArrayTokenArrayBitvector
        this@KanaKanjiEngine.singleKanjiRank0ArrayLBSTango = singleKanjiRank0ArrayLBSTango
        this@KanaKanjiEngine.singleKanjiRank1ArrayLBSTango = singleKanjiRank1ArrayLBSTango
        this@KanaKanjiEngine.singleKanjiYomiLBSBooleanArray = singleKanjiYomiLBSBooleanArray

    }

    suspend fun getCandidates(
        input: String,
        n: Int
    ):List<Candidate> = CoroutineScope(Dispatchers.IO).async{
        val graph = graphBuilder.constructGraph(
            input,
            systemYomiTrie,
            systemTangoTrie,
            systemTokenArray,
            systemRank0ArrayLBSYomi,
            systemRank1ArrayLBSYomi,
            systemRank1ArrayIsLeaf,
            systemRank0ArrayTokenArrayBitvector,
            systemRank1ArrayTokenArrayBitvector,
            rank0ArrayLBSTango = systemRank0ArrayLBSTango,
            rank1ArrayLBSTango = systemRank1ArrayLBSTango,
            LBSBooleanArray = systemYomiLBSBooleanArray
        )

        val resultNBestFinal = async(Dispatchers.IO) {
            findPath.backwardAStar(graph, input.length, connectionIds, n)
        }.await()

        val yomiPartOf = systemYomiTrie.commonPrefixSearch(
            str = input,
            rank0Array = systemRank0ArrayLBSYomi,
            rank1Array = systemRank1ArrayLBSYomi,
        ).reversed()

        val yomiPartList = async(Dispatchers.IO) {
            val yomiPart = yomiPartOf.map { yomi ->
                val termId  = systemYomiTrie.getTermId(
                    systemYomiTrie.getNodeIndex(
                        yomi,
                        systemRank1ArrayLBSYomi,
                        systemYomiLBSBooleanArray,
                    ),
                    systemRank1ArrayIsLeaf
                )
                val listToken: List<TokenEntry> = systemTokenArray.getListDictionaryByYomiTermId(
                    termId,
                    systemRank0ArrayTokenArrayBitvector,
                    systemRank1ArrayTokenArrayBitvector
                )
                return@map listToken.sortedBy { it.wordCost }.map {
                    Candidate(
                        when (it.nodeId) {
                            -2 -> yomi
                            -1 -> yomi.hiraToKata()
                            else -> systemTangoTrie.getLetter(
                                it.nodeId,
                                systemRank0ArrayLBSTango,
                                systemRank1ArrayLBSTango
                            )
                        },
                        2,
                        yomi.length.toUByte(),
                        it.wordCost.toInt(),
                        systemTokenArray.leftIds[it.posTableIndex.toInt()]
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

        val singleKanjiCommonPrefix = singleKanjiYomiTrie.commonPrefixSearch(
            str = input,
            rank0Array = singleKanjiRank0ArrayLBSYomi,
            rank1Array = singleKanjiRank1ArrayLBSYomi,
        ).reversed()

        val singleKanjiList = async(Dispatchers.IO) {
            val singleKanjis = singleKanjiCommonPrefix.map { yomi ->
                val termId  = singleKanjiYomiTrie.getTermId(
                    singleKanjiYomiTrie.getNodeIndex(
                        yomi,
                        singleKanjiRank1ArrayLBSYomi,
                        singleKanjiYomiLBSBooleanArray,
                    ),
                    singleKanjiRank1ArrayIsLeaf
                )
                val listToken: List<TokenEntry> = singleKanjiTokenArray.getListDictionaryByYomiTermId(
                    termId,
                    singleKanjiRank0ArrayTokenArrayBitvector,
                    singleKanjiRank1ArrayTokenArrayBitvector
                )
                return@map listToken.sortedBy { it.wordCost }.map {
                    Candidate(
                        when (it.nodeId) {
                            -2 -> yomi
                            -1 -> yomi.hiraToKata()
                            else -> singleKanjiTangoTrie.getLetter(
                                it.nodeId,
                                singleKanjiRank0ArrayLBSTango,
                                singleKanjiRank1ArrayLBSTango
                            )
                        },
                        7,
                        yomi.length.toUByte(),
                        it.wordCost.toInt(),
                        singleKanjiTokenArray.leftIds[it.posTableIndex.toInt()]
                    )
                }.distinctBy { it.string }. toMutableList()
            }
            singleKanjis.flatten().distinctBy { it.string }.toMutableList()
        }.await()

        val finalResult = resultNBestFinal +
                secondPart.sortedBy { it.score }.filter { (it.score - resultNBestFinal.first().score) < 4000 } +
                longestConversionList +
                yomiPartList +
                singleKanjiList +
                hirakanaAndKana
        return@async finalResult.distinctBy { it.string }
    }.await()

    suspend fun nBestPath(
        input: String,
        n: Int
    ): List<Candidate> {
        val graph = graphBuilder.constructGraph(
            input,
            systemYomiTrie,
            systemTangoTrie,
            systemTokenArray,
            systemRank0ArrayLBSYomi,
            systemRank1ArrayLBSYomi,
            systemRank1ArrayIsLeaf,
            systemRank0ArrayTokenArrayBitvector,
            systemRank1ArrayTokenArrayBitvector,
            rank0ArrayLBSTango = systemRank0ArrayLBSTango,
            rank1ArrayLBSTango = systemRank1ArrayLBSTango,
            LBSBooleanArray = systemYomiLBSBooleanArray
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
            systemYomiTrie,
            systemTangoTrie,
            systemTokenArray,
            systemRank0ArrayLBSYomi,
            systemRank1ArrayLBSYomi,
            systemRank1ArrayIsLeaf,
            systemRank0ArrayTokenArrayBitvector,
            systemRank1ArrayTokenArrayBitvector,
            rank0ArrayLBSTango = systemRank0ArrayLBSTango,
            rank1ArrayLBSTango = systemRank1ArrayLBSTango,
            LBSBooleanArray = systemYomiLBSBooleanArray
        )
        return findPath.backwardAStarForLongest(graph, input.length, connectionIds, n)
    }

    suspend fun viterbiAlgorithm(
        input: String
    ): String {
        val graph = graphBuilder.constructGraph(
            input,
            systemYomiTrie,
            systemTangoTrie,
            systemTokenArray,
            systemRank0ArrayLBSYomi,
            systemRank1ArrayLBSYomi,
            systemRank1ArrayIsLeaf,
            systemRank0ArrayTokenArrayBitvector,
            systemRank1ArrayTokenArrayBitvector,
            rank0ArrayLBSTango = systemRank0ArrayLBSTango,
            rank1ArrayLBSTango = systemRank1ArrayLBSTango,
            LBSBooleanArray = systemYomiLBSBooleanArray
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