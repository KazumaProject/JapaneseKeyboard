package com.kazumaproject.markdownhelperkeyboard.converter.engine

import android.content.Context
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.connection_id.ConnectionIdBuilder
import com.kazumaproject.converter.graph.GraphBuilder
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.hiraToKata
import com.kazumaproject.viterbi.FindPath
import java.io.BufferedInputStream
import java.io.ObjectInputStream
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class KanaKanjiEngine {

    private lateinit var yomiTrie: LOUDSWithTermId
    private lateinit var tangoTrie: LOUDS
    private lateinit var connectionIds: List<Short>
    private lateinit var tokenArray: TokenArray

    @OptIn(ExperimentalTime::class)
    fun buildEngine(
        context: Context,
    ) {
        val time = measureTime {

            val assetManager = context.assets

            val objectInputYomi = ObjectInputStream(BufferedInputStream(assetManager.open("yomi.dat")))
            val objectInputTango = ObjectInputStream(BufferedInputStream(assetManager.open("tango.dat")))
            val objectInputTokenArray = ObjectInputStream(BufferedInputStream(assetManager.open("token.dat")))
            val objectInputReadPOSTable = ObjectInputStream(BufferedInputStream(assetManager.open("pos_table.dat")))
            val objectInputConnectionId = ObjectInputStream(BufferedInputStream(assetManager.open("connectionIds.dat")))

            val time1 = measureTime {
                tokenArray =  TokenArray()
                tokenArray.readExternal(objectInputTokenArray)
                tokenArray.readPOSTable(objectInputReadPOSTable)
            }

            val time2 = measureTime {
                yomiTrie = LOUDSWithTermId().readExternalNotCompress(objectInputYomi)
            }

            val time3 = measureTime {
                tangoTrie = LOUDS().readExternalNotCompress(objectInputTango)
            }

            val time4 = measureTime {
                connectionIds = ConnectionIdBuilder().read(objectInputConnectionId)
            }

            println("token: $time1")
            println("yomi: $time2")
            println("tango: $time3")
            println("connection: $time4")
        }

        println("loading tries: $time")
    }

    fun buildEngine(
        yomi: LOUDSWithTermId,
        tango: LOUDS,
        token: TokenArray,
        connectionIdList: List<Short>
    ){
        this.yomiTrie = yomi
        this.tangoTrie = tango
        this.connectionIds = connectionIdList
        this.tokenArray = token
    }

    suspend fun nBestPath(
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

    suspend fun viterbiAlgorithm(
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