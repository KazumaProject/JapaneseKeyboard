package com.kazumaproject.converter.graph

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.Other.BOS
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.graph.Node
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class GraphBuilder {

    @OptIn(ExperimentalTime::class)
     fun constructGraph(
        str: String,
        yomiTrie: LOUDSWithTermId,
        tangoTrie: LOUDS,
        tokenArray: TokenArray
    ): List<MutableList<MutableList<Node>>> {
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
            for (i in str.indices){
                val subStr = str.substring(i, str.length)
                var commonPrefixSearch: List<String>
                val time1 = measureTime {
                    commonPrefixSearch = yomiTrie.commonPrefixSearch(subStr)
                }
                println("$i $subStr $commonPrefixSearch $time1")
                commonPrefixSearch.forEach { yomiStr ->
                    val termId = yomiTrie.getTermId(yomiTrie.getNodeIndex(yomiStr))
                    val listToken = tokenArray.getListDictionaryByYomiTermId(termId)
                    val tangoList = listToken.map {
                        Node(
                            l = tokenArray.posTable[it.posTableIndex.toInt()].first,
                            r = tokenArray.posTable[it.posTableIndex.toInt()].second,
                            score = it.wordCost.toInt(),
                            f = it.wordCost.toInt(),
                            g = it.wordCost.toInt(),
                            tango = if (it.isSameYomi) yomiStr else tangoTrie.getLetter(it.nodeId),
                            len = yomiStr.length.toShort(),
                            sPos = i
                        )
                    }
                    if (graph[i + yomiStr.length].isNullOrEmpty()) graph[i + yomiStr.length] = mutableListOf()
                    graph[i + yomiStr.length]!!.add(tangoList.toMutableList())
                }
            }
        }

        println("time of construct graph: $time $str")

        return graph.toList().filterNotNull()
    }

}