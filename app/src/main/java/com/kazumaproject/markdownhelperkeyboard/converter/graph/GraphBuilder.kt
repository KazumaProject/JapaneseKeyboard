package com.kazumaproject.converter.graph

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.Other.BOS
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.dictionary.models.TokenEntry
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
        tokenArray: TokenArray,
        rank0ArrayLBSYomi: IntArray,
        rank1ArrayLBSYomi: IntArray,
        rank1ArrayIsLeafYomi: IntArray,
        rank0ArrayTokenArrayBitvector: IntArray,
        rank1ArrayTokenArrayBitvector: IntArray,
        rank0ArrayLBSTango: IntArray,
        rank1ArrayLBSTango: IntArray,
        LBSBooleanArray: BooleanArray,
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
            var commonPrefixSearch: List<String>
            for (i in str.indices){
                val subStr = str.substring(i, str.length)
                val time1 = measureTime {
                    commonPrefixSearch = yomiTrie.commonPrefixSearch(
                        str = subStr,
                        rank0Array = rank0ArrayLBSYomi,
                        rank1Array = rank1ArrayLBSYomi,
                    )
                }
                println("common prefix $subStr $commonPrefixSearch")
                val time2 = measureTime {
                    commonPrefixSearch.map { yomiStr ->
                        CoroutineScope(Dispatchers.Default).async {
                            var termId : Int
                            val timeTermId = measureTime {
                                termId = yomiTrie.getTermId(
                                    yomiTrie.getNodeIndex(
                                        yomiStr,
                                        rank1ArrayLBSYomi,
                                        LBSBooleanArray
                                    ),
                                    rank1ArrayIsLeafYomi
                                )
                            }
                            var listToken: List<TokenEntry>
                            val timeListToken = measureTime {
                                listToken = tokenArray.getListDictionaryByYomiTermId(
                                    termId,
                                    rank0ArrayTokenArrayBitvector,
                                    rank1ArrayTokenArrayBitvector
                                )
                            }
                            var tangoList = listOf<Node>()
                            val timeTangoList = measureTime {
                                tangoList = listToken.map {
                                    Node(
                                        l = tokenArray.leftIds[it.posTableIndex.toInt()],
                                        r = tokenArray.rightIds[it.posTableIndex.toInt()],
                                        score = it.wordCost.toInt(),
                                        f = it.wordCost.toInt(),
                                        g = it.wordCost.toInt(),
                                        tango = when (it.nodeId) {
                                            -2 -> yomiStr
                                            -1 -> yomiStr.hiraToKata()
                                            else -> tangoTrie.getLetter(
                                                it.nodeId,
                                                rank0ArrayLBSTango,
                                                rank1ArrayLBSTango
                                            )
                                        },
                                        len = yomiStr.length.toShort(),
                                        sPos = i
                                    )
                                }
                            }

                            println("time for total $yomiStr: $timeTermId $timeListToken $timeTangoList ${timeTermId + timeListToken + timeTangoList}")
                            println("term Id $yomiStr: $termId")
                            println("listToken $yomiStr: $listToken")
                            println("tangoList $yomiStr: $tangoList")

                            if (graph[i + yomiStr.length].isNullOrEmpty()) graph[i + yomiStr.length] = mutableListOf()
                            graph[i + yomiStr.length]!!.add(tangoList.toMutableList())
                            println("$yomiStr $termId")
                        }
                    }.awaitAll()
                }
                println("$i $commonPrefixSearch $time1 $time2 ${time1 + time2} $subStr")
            }
        }

        println("time of construct graph: $time $str")

        println("graph: $graph")

        return graph.toList().filterNotNull()
    }

}