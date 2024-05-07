package com.kazumaproject.markdownhelperkeyboard.converter.engine

import android.content.Context
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.connection_id.ConnectionIdBuilder
import com.kazumaproject.converter.graph.GraphBuilder
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.hiraToKata
import com.kazumaproject.viterbi.FindPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.FileInputStream
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
        context: Context
    ) = CoroutineScope(Dispatchers.Main).launch {

        val objectInputYomi = ObjectInputStream(context.assets.open("yomi.dat"))
        val objectInputTango = ObjectInputStream(context.assets.open("tango.dat"))
        val objectInputTokenArray = ObjectInputStream(context.assets.open("token.dat"))
        val objectInputConnectionId = ObjectInputStream(context.assets.open("connectionIds.dat"))
        val objectInputReadPOSTable = ObjectInputStream(context.assets.open("pos_table.dat"))

        val time = measureTime {
            val timeA = measureTime {
                yomiTrie = LOUDSWithTermId().readExternal(objectInputYomi)
            }
            val timeB = measureTime {
                tangoTrie = LOUDS().readExternal(objectInputTango)
            }
            val timeC = measureTime {
                connectionIds = ConnectionIdBuilder().read(objectInputConnectionId)
            }
            val timeD = measureTime {
                tokenArray = async {
                    val a = TokenArray()
                    a.readExternal(objectInputTokenArray)
                    a.readPOSTable(objectInputReadPOSTable)
                    return@async a
                }.await()
            }
            println("yomi.dat: $timeA")
            println("tango.dat: $timeB")
            println("connectionIds.dat: $timeC")
            println("token.dat: $timeD")
        }
        Timber.d("finished to build kana kanji engine $time")
    }

    fun buildEngineForTest(
        objectInputStreamForReadPOSTable: ObjectInputStream
    ){
        val objectInputYomi = ObjectInputStream(FileInputStream("src/test/resources/yomi.dat"))
        val objectInputTango = ObjectInputStream(FileInputStream("src/test/resources/tango.dat"))
        val objectInputTokenArray = ObjectInputStream(FileInputStream("src/test/resources/token.dat"))
        val objectInputConnectionId = ObjectInputStream(FileInputStream("src/test/resources/connectionIds.dat"))
        yomiTrie = LOUDSWithTermId().readExternal(objectInputYomi)
        tangoTrie = LOUDS().readExternal(objectInputTango)
        tokenArray = TokenArray()
        tokenArray.readExternal(objectInputTokenArray)
        tokenArray.readPOSTable(objectInputStreamForReadPOSTable)
        connectionIds = ConnectionIdBuilder().read(objectInputConnectionId)
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