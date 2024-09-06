package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.converter.graph.GraphBuilder
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateTemp
import com.kazumaproject.viterbi.FindPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

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
    private lateinit var systemYomiLBSPreprocess: IntArray

    private lateinit var singleKanjiYomiTrie: LOUDSWithTermId
    private lateinit var singleKanjiTangoTrie: LOUDS
    private lateinit var singleKanjiTokenArray: TokenArray

    private lateinit var singleKanjiRank0ArrayLBSYomi: ShortArray
    private lateinit var singleKanjiRank1ArrayLBSYomi: ShortArray
    private lateinit var singleKanjiRank1ArrayIsLeaf: ShortArray
    private lateinit var singleKanjiRank0ArrayTokenArrayBitvector: ShortArray
    private lateinit var singleKanjiRank1ArrayTokenArrayBitvector: ShortArray
    private lateinit var singleKanjiRank0ArrayLBSTango: ShortArray
    private lateinit var singleKanjiRank1ArrayLBSTango: ShortArray
    private lateinit var singleKanjiYomiLBSBooleanArray: BooleanArray
    private lateinit var singleKanjiYomiLBSPreprocess: IntArray


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
        singleKanjiRank0ArrayLBSYomi: ShortArray,
        singleKanjiRank1ArrayLBSYomi: ShortArray,
        singleKanjiRank1ArrayIsLeaf: ShortArray,
        singleKanjiRank0ArrayTokenArrayBitvector: ShortArray,
        singleKanjiRank1ArrayTokenArrayBitvector: ShortArray,
        singleKanjiRank0ArrayLBSTango: ShortArray,
        singleKanjiRank1ArrayLBSTango: ShortArray,
        singleKanjiYomiLBSBooleanArray: BooleanArray,
    ) {
        this@KanaKanjiEngine.graphBuilder = graphBuilder
        this@KanaKanjiEngine.findPath = findPath

        this@KanaKanjiEngine.connectionIds = connectionIdList

        this@KanaKanjiEngine.systemTangoTrie = systemTangoTrie
        this@KanaKanjiEngine.systemTokenArray = systemTokenArray
        this@KanaKanjiEngine.systemYomiTrie = systemYomiTrie

        this@KanaKanjiEngine.systemRank0ArrayLBSYomi = systemRank0ArrayLBSYomi
        this@KanaKanjiEngine.systemRank1ArrayLBSYomi = systemRank1ArrayLBSYomi
        this@KanaKanjiEngine.systemRank1ArrayIsLeaf = systemRank1ArrayIsLeaf
        this@KanaKanjiEngine.systemRank0ArrayTokenArrayBitvector =
            systemRank0ArrayTokenArrayBitvector
        this@KanaKanjiEngine.systemRank1ArrayTokenArrayBitvector =
            systemRank1ArrayTokenArrayBitvector
        this@KanaKanjiEngine.systemRank0ArrayLBSTango = systemRank0ArrayLBSTango
        this@KanaKanjiEngine.systemRank1ArrayLBSTango = systemRank1ArrayLBSTango
        this@KanaKanjiEngine.systemYomiLBSBooleanArray = systemYomiLBSBooleanArray

        this@KanaKanjiEngine.singleKanjiTangoTrie = singleKanjiTangoTrie
        this@KanaKanjiEngine.singleKanjiTokenArray = singleKanjiTokenArray
        this@KanaKanjiEngine.singleKanjiYomiTrie = singleKanjiYomiTrie

        this@KanaKanjiEngine.singleKanjiRank0ArrayLBSYomi = singleKanjiRank0ArrayLBSYomi
        this@KanaKanjiEngine.singleKanjiRank1ArrayLBSYomi = singleKanjiRank1ArrayLBSYomi
        this@KanaKanjiEngine.singleKanjiRank1ArrayIsLeaf = singleKanjiRank1ArrayIsLeaf
        this@KanaKanjiEngine.singleKanjiRank0ArrayTokenArrayBitvector =
            singleKanjiRank0ArrayTokenArrayBitvector
        this@KanaKanjiEngine.singleKanjiRank1ArrayTokenArrayBitvector =
            singleKanjiRank1ArrayTokenArrayBitvector
        this@KanaKanjiEngine.singleKanjiRank0ArrayLBSTango = singleKanjiRank0ArrayLBSTango
        this@KanaKanjiEngine.singleKanjiRank1ArrayLBSTango = singleKanjiRank1ArrayLBSTango
        this@KanaKanjiEngine.singleKanjiYomiLBSBooleanArray = singleKanjiYomiLBSBooleanArray

        this@KanaKanjiEngine.systemYomiLBSPreprocess =
            preprocessLBSInBoolArray(systemYomiLBSBooleanArray)
        this@KanaKanjiEngine.singleKanjiYomiLBSPreprocess =
            preprocessLBSInBoolArray(singleKanjiYomiLBSBooleanArray)

    }

    suspend fun getCandidates(
        input: String,
        n: Int
    ): List<Candidate> = withContext(Dispatchers.Default) {

        // Construct the graph and find the best path concurrently
        val graphDeferred = async {
            graphBuilder.constructGraph(
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
                LBSBooleanArray = systemYomiLBSBooleanArray,
                LBSBooleanArrayPreprocess = systemYomiLBSPreprocess
            )
        }

        val resultNBestFinalDeferred = async {
            findPath.backwardAStar(graphDeferred.await(), input.length, connectionIds, n, input)
        }

        val hirakanaAndKana = listOf(
            Candidate(input, 3, input.length.toUByte(), 6000),
            Candidate(input.hiraToKata(), 4, input.length.toUByte(), 6000)
        )

        // Handle single character input
        if (input.length == 1) {
            val singleKanjiListDeferred = async {
                singleKanjiYomiTrie.commonPrefixSearchShortArray(
                    input,
                    singleKanjiRank0ArrayLBSYomi,
                    singleKanjiRank1ArrayLBSYomi
                ).asReversed().asSequence().flatMap { yomi ->
                    val termId = singleKanjiYomiTrie.getTermIdShortArray(
                        singleKanjiYomiTrie.getNodeIndex(
                            yomi,
                            singleKanjiRank1ArrayLBSYomi,
                            singleKanjiYomiLBSBooleanArray,
                            singleKanjiYomiLBSPreprocess
                        ),
                        singleKanjiRank1ArrayIsLeaf
                    )
                    singleKanjiTokenArray.getListDictionaryByYomiTermIdShortArray(
                        termId,
                        singleKanjiRank0ArrayTokenArrayBitvector,
                        singleKanjiRank1ArrayTokenArrayBitvector
                    ).sortedBy { it.wordCost }.map {
                        Candidate(
                            string = when (it.nodeId) {
                                -2 -> yomi
                                -1 -> yomi.hiraToKata()
                                else -> singleKanjiTangoTrie.getLetterShortArray(
                                    it.nodeId,
                                    singleKanjiRank0ArrayLBSTango,
                                    singleKanjiRank1ArrayLBSTango
                                )
                            },
                            type = 7,
                            length = yomi.length.toUByte(),
                            score = it.wordCost.toInt(),
                            leftId = singleKanjiTokenArray.leftIds[it.posTableIndex.toInt()],
                            rightId = singleKanjiTokenArray.rightIds[it.posTableIndex.toInt()]
                        )
                    }
                }.distinctBy { it.string }.toList()
            }
            return@withContext (resultNBestFinalDeferred.await() + hirakanaAndKana + singleKanjiListDeferred.await()).distinctBy { it.string }
        }

        // Async search for common prefixes and predictive search
        val yomiPartOfDeferred = async {
            if (input.length > 16) emptyList() else systemYomiTrie.commonPrefixSearch(
                input,
                systemRank0ArrayLBSYomi,
                systemRank1ArrayLBSYomi
            ).asReversed()
        }

        val predictiveSearchResultDeferred = async {
            systemYomiTrie.predictiveSearch(
                prefix = input,
                systemRank0ArrayLBSYomi,
                systemRank1ArrayLBSYomi
            ).filter {
                when (input.length) {
                    2 -> it.length <= input.length + 1
                    3 -> it.length <= input.length + 2
                    else -> it.length > input.length
                }
            }.sortedBy { it.length }.flatMap { yomi ->
                val termId = systemYomiTrie.getTermId(
                    systemYomiTrie.getNodeIndex(
                        yomi,
                        systemRank1ArrayLBSYomi,
                        systemYomiLBSBooleanArray,
                        systemYomiLBSPreprocess
                    ),
                    systemRank1ArrayIsLeaf
                )
                systemTokenArray.getListDictionaryByYomiTermId(
                    termId,
                    systemRank0ArrayTokenArrayBitvector,
                    systemRank1ArrayTokenArrayBitvector
                ).sortedBy { it.wordCost }.map {
                    Candidate(
                        string = when (it.nodeId) {
                            -2 -> yomi
                            -1 -> yomi.hiraToKata()
                            else -> systemTangoTrie.getLetter(
                                it.nodeId,
                                systemRank0ArrayLBSTango,
                                systemRank1ArrayLBSTango
                            )
                        },
                        type = 9,
                        length = yomi.length.toUByte(),
                        score = it.wordCost.toInt(),
                        leftId = systemTokenArray.leftIds[it.posTableIndex.toInt()],
                        rightId = systemTokenArray.rightIds[it.posTableIndex.toInt()]
                    )
                }.distinctBy { it.string }
            }.take(8)
        }

        val yomiPartListDeferred = async {
            yomiPartOfDeferred.await().asSequence().flatMap { yomi ->
                val termId = systemYomiTrie.getTermId(
                    systemYomiTrie.getNodeIndex(
                        yomi,
                        systemRank1ArrayLBSYomi,
                        systemYomiLBSBooleanArray,
                        systemYomiLBSPreprocess
                    ),
                    systemRank1ArrayIsLeaf
                )
                systemTokenArray.getListDictionaryByYomiTermId(
                    termId,
                    systemRank0ArrayTokenArrayBitvector,
                    systemRank1ArrayTokenArrayBitvector
                ).sortedBy { it.wordCost }.map {
                    Candidate(
                        string = when (it.nodeId) {
                            -2 -> yomi
                            -1 -> yomi.hiraToKata()
                            else -> systemTangoTrie.getLetter(
                                it.nodeId,
                                systemRank0ArrayLBSTango,
                                systemRank1ArrayLBSTango
                            )
                        },
                        type = if (yomi.length == input.length) 2 else 5,
                        length = yomi.length.toUByte(),
                        score = it.wordCost.toInt(),
                        leftId = systemTokenArray.leftIds[it.posTableIndex.toInt()],
                        rightId = systemTokenArray.rightIds[it.posTableIndex.toInt()]
                    )
                }.distinctBy { it.string }
            }.toList()
        }

        // Final combination of results, applying distinctBy only once
        return@withContext (resultNBestFinalDeferred.await() + hirakanaAndKana + predictiveSearchResultDeferred.await() + yomiPartListDeferred.await())
            .distinctBy { it.string }
    }

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
            LBSBooleanArray = systemYomiLBSBooleanArray,
            LBSBooleanArrayPreprocess = systemYomiLBSPreprocess,
        )
        val result = findPath.backwardAStar(graph, input.length, connectionIds, n, input)
        result.apply {
            if (!this.map { it.string }.contains(input)) {
                add(
                    Candidate(
                        string = input,
                        type = (3).toByte(),
                        length = (input.length).toUByte(),
                        score = (input.length) * 2000
                    )
                )
            }
            if (!this.map { it.string }.contains(input.hiraToKata())) {
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

    private fun nBestPathForLongest(
        input: String,
        n: Int
    ): List<CandidateTemp> {
        val graph = graphBuilder.constructGraphLongest(
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
            LBSBooleanArray = systemYomiLBSBooleanArray,
            LBSBooleanArrayPreprocess = systemYomiLBSPreprocess,
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
            LBSBooleanArray = systemYomiLBSBooleanArray,
            LBSBooleanArrayPreprocess = systemYomiLBSPreprocess,
        )
        return findPath.viterbi(graph, input.length, connectionIds)
    }

    private fun preprocessLBSInBoolArray(LBSInBoolArray: BooleanArray): IntArray {
        val prefixSum = IntArray(LBSInBoolArray.size + 1)
        for (i in LBSInBoolArray.indices) {
            prefixSum[i + 1] = prefixSum[i] + if (LBSInBoolArray[i]) 0 else 1
        }
        return prefixSum
    }

}