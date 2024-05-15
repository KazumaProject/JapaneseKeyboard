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

class KanaKanjiEngine {

    private lateinit var yomiTrie: LOUDSWithTermId
    private lateinit var tangoTrie: LOUDS
    private lateinit var connectionIds: List<Short>
    private lateinit var tokenArray: TokenArray

    fun buildEngine(context: Context){
        val objectInputYomi = ObjectInputStream(BufferedInputStream(context.assets.open("yomi.dat")))
        val objectInputTango = ObjectInputStream(BufferedInputStream(context.assets.open("tango.dat")))
        val objectInputTokenArray = ObjectInputStream(BufferedInputStream(context.assets.open("token.dat")))
        val objectInputReadPOSTable = ObjectInputStream(BufferedInputStream(context.assets.open("pos_table.dat")))
        val objectInputConnectionId = ObjectInputStream(BufferedInputStream(context.assets.open("connectionIds.dat")))
        tokenArray = TokenArray()
        tokenArray.readExternal(objectInputTokenArray)
        tokenArray.readPOSTable(objectInputReadPOSTable)
        yomiTrie = LOUDSWithTermId().readExternal(objectInputYomi)
        tangoTrie = LOUDS().readExternal(objectInputTango)
        connectionIds = ConnectionIdBuilder().read(objectInputConnectionId)
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