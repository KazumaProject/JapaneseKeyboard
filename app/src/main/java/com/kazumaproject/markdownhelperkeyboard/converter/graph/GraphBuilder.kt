package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.graph.Node
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.Other.BOS
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.PosMapper
import timber.log.Timber

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
        userDictionaryRepository: UserDictionaryRepository,
        learnRepository: LearnRepository?
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
            var foundInAnyDictionary = false

            // 1. ユーザー辞書からCommon Prefix Searchを実行
            val userWords = userDictionaryRepository.commonPrefixSearchInUserDict(subStr)
            if (userWords.isNotEmpty()) foundInAnyDictionary = true
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

            // 2. 学習辞書からCommon Prefix Searchを実行
            val learnedWords = learnRepository?.findCommonPrefixes(subStr) ?: emptyList()
            if (learnedWords.isNotEmpty()) foundInAnyDictionary = true
            learnedWords.forEach { learnedWord ->
                val endIndex = i + learnedWord.input.length
                val node = Node(
                    l = 1851,
                    r = 1851,
                    score = learnedWord.score.toInt(),
                    f = learnedWord.score.toInt(),
                    g = learnedWord.score.toInt(),
                    tango = learnedWord.out,
                    len = learnedWord.input.length.toShort(),
                    sPos = i
                )
                graph.computeIfAbsent(endIndex) { mutableListOf() }.add(node)
            }

            Timber.d("learnedWords: $learnedWords")

            // 3. システム辞書からCommon Prefix Searchを実行
            val commonPrefixSearchSystem: List<String> = yomiTrie.commonPrefixSearch(
                str = subStr,
                succinctBitVector = succinctBitVectorLBSYomi
            )
            if (commonPrefixSearchSystem.isNotEmpty()) foundInAnyDictionary = true

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

            // 4. どの辞書にもヒットしなかった場合のフォールバック
            if (!foundInAnyDictionary && subStr.isNotEmpty()) {
                val yomiStr = subStr.substring(0, 1) // 1文字だけを未知語として切り出す
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
