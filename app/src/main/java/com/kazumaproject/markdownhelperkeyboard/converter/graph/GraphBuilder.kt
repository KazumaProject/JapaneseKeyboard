package com.kazumaproject.converter.graph

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.Other.BOS
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.graph.Node
import com.kazumaproject.hiraToKata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class GraphBuilder {

    @OptIn(ExperimentalTime::class)
     suspend fun constructGraph(
        str: String,
        yomiTrie: LOUDSWithTermId,
        tangoTrie: LOUDS,
        tokenArray: TokenArray
    ): List<MutableList<MutableList<Node>>> = CoroutineScope(Dispatchers.IO).async {
        val graph: MutableList<MutableList<MutableList<Node>>?> = mutableListOf()
        for (i in 0 .. str.length + 1){
            when(i){
                0 -> graph.add(i, mutableListOf(mutableListOf(BOS)))
                str.length + 1 -> graph.add(i,
                    mutableListOf(
                        mutableListOf(
                            Node(
                                l = 0,
                                r = 0,
                                score = 0,
                                f = 0,
                                g = 0,
                                tango = "EOS",
                                len = 0,
                                sPos = str.length + 1
                            )
                        )
                    )
                )
                else -> graph.add(i,null)
            }
        }

        val time = measureTime {
            var commonPrefixSearch: List<String>
            str.indices.map { i ->
                async(Dispatchers.IO) {
                    val subStr = str.substring(i, str.length)
                    val time1 = measureTime {
                        commonPrefixSearch = yomiTrie.commonPrefixSearch(subStr)
                    }
                    val time2 = measureTime {
                        commonPrefixSearch.forEach { yomiStr ->
                            val termId = yomiTrie.getTermId(yomiTrie.getNodeIndex(yomiStr))
                            val listToken = tokenArray.getListDictionaryByYomiTermId(termId)
                            val tangoList = listToken.map {
                                Node(
                                    l = tokenArray.leftIds[it.posTableIndex.toInt()],
                                    r = tokenArray.rightIds[it.posTableIndex.toInt()],
                                    score = it.wordCost.toInt(),
                                    f = it.wordCost.toInt(),
                                    g = it.wordCost.toInt(),
                                    tango = when (it.nodeId) {
                                        -2 -> yomiStr
                                        -1 -> yomiStr.hiraToKata()
                                        else -> tangoTrie.getLetter(it.nodeId)
                                    },
                                    len = yomiStr.length.toShort(),
                                    sPos = i
                                )
                            }
                            if (graph[i + yomiStr.length].isNullOrEmpty()) graph[i + yomiStr.length] = mutableListOf()
                            graph[i + yomiStr.length]!!.add(tangoList.toMutableList())
                            //println("add graph: ${tangoList.map { "${it.tango} ${it.score} ${it.f} ${it.g}" }}")
                        }
                    }
                    println("$i $commonPrefixSearch $time1 $time2 ${time1 + time2} $subStr")
                }
            }.awaitAll()
        }

        println("time of construct graph: $time $str")

        println("graph: $graph")

        return@async graph.toList().filterNotNull()
    }.await()

}