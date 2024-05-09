package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.converter.graph.GraphBuilder
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.hiraToKata
import com.kazumaproject.viterbi.FindPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class KanaKanjiEngine {

    private lateinit var yomiTrie: LOUDSWithTermId
    private lateinit var tangoTrie: LOUDS
    private lateinit var connectionIds: List<Short>
    private lateinit var tokenArray: TokenArray

    @OptIn(ExperimentalTime::class)
    fun buildEngine(
        yomi: LOUDSWithTermId,
        tango: LOUDS,
        token: TokenArray,
        connectionIdList: List<Short>
    ) = CoroutineScope(Dispatchers.Main).launch {

        val time = measureTime {
            val timeA = measureTime {
                yomiTrie = yomi
            }
            val timeB = measureTime {
                tangoTrie = tango
            }
            val timeC = measureTime {
                connectionIds = connectionIdList
            }
            val timeD = measureTime {
                tokenArray = token
            }
            println("yomi.dat: $timeA")
            println("tango.dat: $timeB")
            println("connectionIds.dat: $timeC")
            println("token.dat: $timeD")

        }

        Timber.d("finished to build kana kanji engine $time")
    }

    fun nBestPath(
        input: String,
        n: Int
    ): List<String> {
        val findPath = FindPath()
        val graphBuilder = GraphBuilder()
        val graph = graphBuilder.constructGraph(
            input,
            yomiTrie,
            tangoTrie,
            tokenArray,
        )
        val result = findPath.backwardAStar(graph, input.length, connectionIds, n)
        result.apply {
            if (!this.contains(input)){
                add(input)
            }
            if (!this.contains(input.hiraToKata())){
                add(input.hiraToKata())
            }
        }
        return result
    }

    fun viterbiAlgorithm(
        input: String
    ): String {
        val findPath = FindPath()
        val graphBuilder = GraphBuilder()
        val graph = graphBuilder.constructGraph(
            input,
            yomiTrie,
            tangoTrie,
            tokenArray,
        )
        return findPath.viterbi(graph, input.length, connectionIds)
    }

}