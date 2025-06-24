package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.graph.Node
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.Other.BOS
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.PosMapper

class GraphBuilder {

    suspend fun constructGraph(
        str: String,
        yomiTrie: LOUDSWithTermId,
        tangoTrie: LOUDS,
        tokenArray: TokenArray,
        succinctBitVectorLBSYomi: SuccinctBitVector,
        succinctBitVectorIsLeafYomi: SuccinctBitVector,
        succinctBitVectorTokenArray: SuccinctBitVector,
        succinctBitVectorTangoLBS: SuccinctBitVector,
        userDictionaryRepository: UserDictionaryRepository
    ): MutableMap<Int, MutableList<Node>> {
        val graph: MutableMap<Int, MutableList<Node>> = LinkedHashMap()
        graph[0] = mutableListOf(BOS)
        graph[str.length + 1] = mutableListOf(
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

        for (i in str.indices) {
            val subStr = str.substring(i)

            // 1. ユーザー辞書からCommon Prefix Searchを実行
            val userWords = userDictionaryRepository.commonPrefixSearchInUserDict(subStr)

            // ユーザー辞書の結果をNodeに変換してグラフに追加
            userWords.forEach { userWord ->
                val endIndex = i + userWord.reading.length
                val contextId = PosMapper.getContextIdForPos(userWord.posIndex)
                val node = Node(
                    l = contextId,
                    r = contextId,
                    score = userWord.posScore,
                    f = userWord.posScore,
                    g = userWord.posScore,
                    tango = userWord.word,
                    len = userWord.reading.length.toShort(),
                    sPos = i
                )
                graph.computeIfAbsent(endIndex) { mutableListOf() }.add(node)
            }

            // 2. システム辞書からCommon Prefix Searchを実行
            val commonPrefixSearchSystem: List<String> = yomiTrie.commonPrefixSearch(
                str = subStr,
                succinctBitVector = succinctBitVectorLBSYomi
            )

            // システム辞書の結果をNodeに変換してグラフに追加
            for (yomiStr in commonPrefixSearchSystem) {
                val nodeIndex = yomiTrie.getNodeIndex(
                    yomiStr,
                    succinctBitVectorLBSYomi,
                )
                if (nodeIndex > 0) { // ルートノードは除く
                    val termId = yomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafYomi)
                    val listToken = tokenArray.getListDictionaryByYomiTermId(
                        termId,
                        succinctBitVectorTokenArray
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
                                else -> tangoTrie.getLetter(
                                    it.nodeId,
                                    succinctBitVector = succinctBitVectorTangoLBS
                                )
                            },
                            len = yomiStr.length.toShort(),
                            sPos = i,
                        )
                    }
                    val endIndex = i + yomiStr.length
                    graph.computeIfAbsent(endIndex) { mutableListOf() }.addAll(tangoList)
                }
            }

            // 3. 辞書に全くヒットしなかった場合のフォールバック
            //    (ユーザー辞書にもシステム辞書にもsubStrの接頭辞となる単語がなかった場合)
            val isUserWordFound = userWords.any { it.reading.isNotEmpty() }
            val isSystemWordFound = commonPrefixSearchSystem.any { it.isNotEmpty() }
            if (!isUserWordFound && !isSystemWordFound && subStr.isNotEmpty()) {
                val yomiStr = subStr.substring(0, 1) // 1文字だけを未知語として切り出すなど
                val endIndex = i + yomiStr.length
                val unknownNode = Node(
                    l = 0, // 未知語用のID
                    r = 0, // 未知語用のID
                    score = 10000, // 高いコスト
                    f = 10000,
                    g = 10000,
                    tango = yomiStr, // 読みをそのまま単語とする
                    len = yomiStr.length.toShort(),
                    sPos = i,
                )
                graph.computeIfAbsent(endIndex) { mutableListOf() }.add(unknownNode)
            }
        }
        return graph
    }
}
