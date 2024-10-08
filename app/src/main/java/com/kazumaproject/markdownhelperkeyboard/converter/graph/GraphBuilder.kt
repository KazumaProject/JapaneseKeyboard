package com.kazumaproject.converter.graph

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.graph.Node
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.Other.BOS

class GraphBuilder {
    fun constructGraph(
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
        LBSBooleanArrayPreprocess: IntArray
    ): List<MutableList<MutableList<Node>>> {
        val graph: MutableList<MutableList<MutableList<Node>>> = MutableList(str.length + 2) { mutableListOf() }
        graph[0].add(mutableListOf(BOS))
        graph[str.length + 1].add(
            mutableListOf(
                Node(
                    l = 0,
                    r = 0,
                    score = 0,
                    f = 0,
                    g = 0,
                    tango = "EOS",
                    len = 0,
                    sPos = str.length + 1,
                )
            )
        )
        for (i in str.indices) {
            val subStr = str.substring(i)
            val commonPrefixSearch: MutableList<String> = yomiTrie.commonPrefixSearch(
                str = subStr,
                rank0Array = rank0ArrayLBSYomi,
                rank1Array = rank1ArrayLBSYomi,
            ).toMutableList().apply {
                if (isEmpty()) add(subStr)
            }

            //println("common prefix search: $commonPrefixSearch")

            for (yomiStr in commonPrefixSearch) {
                val nodeIndex = yomiTrie.getNodeIndex(yomiStr, rank1ArrayLBSYomi, LBSBooleanArray,LBSBooleanArrayPreprocess)
                val termId = yomiTrie.getTermId(nodeIndex, rank1ArrayIsLeafYomi)
                val listToken = tokenArray.getListDictionaryByYomiTermId(
                    termId,
                    rank0ArrayTokenArrayBitvector,
                    rank1ArrayTokenArrayBitvector
                )

                val tangoList = listToken.map {
                    //Timber.d("tangoList: ${it.nodeId} $yomiStr $nodeIndex $termId $listToken")
                    Node(
                        l = tokenArray.leftIds[it.posTableIndex.toInt()],
                        r = tokenArray.rightIds[it.posTableIndex.toInt()],
                        score = it.wordCost.toInt(),
                        f = it.wordCost.toInt(),
                        g = it.wordCost.toInt(),
                        tango = when (it.nodeId) {
                            -2 -> yomiStr
                            -1 -> yomiStr.hiraToKata()
                            else -> tangoTrie.getLetter(it.nodeId, rank0ArrayLBSTango, rank1ArrayLBSTango)
                        },
                        len = yomiStr.length.toShort(),
                        sPos = i,
                    )
                }

                //Timber.d("common prefix tango:$str $tangoList $nodeIndex")

                if (graph[i + yomiStr.length].isEmpty()) {
                    graph[i + yomiStr.length] = mutableListOf()
                }
                graph[i + yomiStr.length].add(tangoList.toMutableList())
            }
        }
        return graph.toList()
    }

    fun constructGraphLongest(
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
        LBSBooleanArrayPreprocess: IntArray
    ): List<MutableList<MutableList<Node>>> {
        val graph: MutableList<MutableList<MutableList<Node>>> = MutableList(str.length + 2) { mutableListOf() }
        graph[0].add(mutableListOf(BOS))
        graph[str.length + 1].add(
            mutableListOf(
                Node(
                    l = 0,
                    r = 0,
                    score = 0,
                    f = 0,
                    g = 0,
                    tango = "EOS",
                    len = 0,
                    sPos = str.length + 1,
                )
            )
        )

        for (i in str.indices) {
            val subStr = str.substring(i)
            val commonPrefixSearch = yomiTrie.commonPrefixSearch(
                str = subStr,
                rank0Array = rank0ArrayLBSYomi,
                rank1Array = rank1ArrayLBSYomi,
            ).toMutableList().apply {
                if (isEmpty()) add(subStr)
            }

            for (yomiStr in commonPrefixSearch) {
                val nodeIndex = yomiTrie.getNodeIndex(yomiStr, rank1ArrayLBSYomi, LBSBooleanArray,LBSBooleanArrayPreprocess)
                val termId = yomiTrie.getTermId(nodeIndex, rank1ArrayIsLeafYomi)
                val listToken = tokenArray.getListDictionaryByYomiTermId(
                    termId,
                    rank0ArrayTokenArrayBitvector,
                    rank1ArrayTokenArrayBitvector
                )
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
                            else -> tangoTrie.getLetter(it.nodeId, rank0ArrayLBSTango, rank1ArrayLBSTango)
                        },
                        len = yomiStr.length.toShort(),
                        sPos = i,
                    )
                }

                if (graph[i + yomiStr.length].isEmpty()) {
                    graph[i + yomiStr.length] = mutableListOf()
                }
                graph[i + yomiStr.length].add(tangoList.toMutableList())
            }
        }

//        println("time of construct graph longest: $time $str")
//        println("graph longest: $graph")
        return graph.toList()
    }

}