package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.core.domain.extensions.hasNConsecutiveChars
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.graph.Node
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.Other.BOS
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllHalfWidthAscii
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.PosMapper

class GraphBuilder {

    companion object {
        private const val OMISSION_SCORE_OFFSET = 1900
        private const val TYPO_SCORE_OFFSET = 2200
    }

    /**
     * グラフにノードを追加または更新する。
     * 同じ終了位置に【同じ単語】かつ【同じ品詞ID(l/r)】のノードが既に存在する場合、
     * 新しいノードのスコアが既存のノードよりも良ければ（低ければ）置き換える。
     * 品詞IDが異なる場合は、別のノードとしてリストに追加する。
     *
     * @param graph グラフ全体
     * @param endIndex ノードの終了インデックス
     * @param newNode 追加する新しいノード
     */
    private fun addOrUpdateNode(
        graph: MutableMap<Int, MutableList<Node>>,
        endIndex: Int,
        newNode: Node
    ) {
        val nodes = graph.computeIfAbsent(endIndex) { mutableListOf() }

        // tango, l, r の3つがすべて一致するノードを探す
        val existingNodeIndex = nodes.indexOfFirst {
            it.tango == newNode.tango && it.l == newNode.l && it.r == newNode.r
        }

        if (existingNodeIndex != -1) {
            // 完全に一致する既存ノードが見つかった場合
            val existingNode = nodes[existingNodeIndex]
            if (newNode.score < existingNode.score) {
                // 新しいノードの方がスコアが良い（低い）ので置き換える
                nodes[existingNodeIndex] = newNode
            }
        } else {
            // 新しい単語、または、同じ単語だが品詞が異なる場合は、単純に追加する
            nodes.add(newNode)
        }
    }

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
        learnRepository: LearnRepository?,
        wikiYomiTrie: LOUDSWithTermId?,
        wikiTangoTrie: LOUDS?,
        wikiTokenArray: TokenArray?,
        succinctBitVectorLBSWikiYomi: SuccinctBitVector?,
        succinctBitVectorIsLeafWikiYomi: SuccinctBitVector?,
        succinctBitVectorWikiTokenArray: SuccinctBitVector?,
        succinctBitVectorWikiTangoLBS: SuccinctBitVector?,
        webYomiTrie: LOUDSWithTermId?,
        webTangoTrie: LOUDS?,
        webTokenArray: TokenArray?,
        succinctBitVectorLBSwebYomi: SuccinctBitVector?,
        succinctBitVectorIsLeafwebYomi: SuccinctBitVector?,
        succinctBitVectorwebTokenArray: SuccinctBitVector?,
        succinctBitVectorwebTangoLBS: SuccinctBitVector?,
        personYomiTrie: LOUDSWithTermId?,
        personTangoTrie: LOUDS?,
        personTokenArray: TokenArray?,
        succinctBitVectorLBSpersonYomi: SuccinctBitVector?,
        succinctBitVectorIsLeafpersonYomi: SuccinctBitVector?,
        succinctBitVectorpersonTokenArray: SuccinctBitVector?,
        succinctBitVectorpersonTangoLBS: SuccinctBitVector?,
        neologdYomiTrie: LOUDSWithTermId?,
        neologdTangoTrie: LOUDS?,
        neologdTokenArray: TokenArray?,
        succinctBitVectorLBSneologdYomi: SuccinctBitVector?,
        succinctBitVectorIsLeafneologdYomi: SuccinctBitVector?,
        succinctBitVectorneologdTokenArray: SuccinctBitVector?,
        succinctBitVectorneologdTangoLBS: SuccinctBitVector?,
        isOmissionSearchEnable: Boolean,
        enableTypoCorrectionJapaneseFlick: Boolean = false
    ): MutableMap<Int, MutableList<Node>> {
        if (str.isAllHalfWidthAscii()) return mutableMapOf()
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

            // 1. ユーザー辞書
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
                addOrUpdateNode(graph, endIndex, node)
            }

            // 2. 学習辞書
            val learnedWords = learnRepository?.findCommonPrefixes(subStr) ?: emptyList()
            if (learnedWords.isNotEmpty()) foundInAnyDictionary = true
            learnedWords.forEach { learnedWord ->
                val endIndex = i + learnedWord.input.length
                val node = Node(
                    l = learnedWord.leftId ?: 1851,
                    r = learnedWord.rightId ?: 1851,
                    score = learnedWord.score.toInt(),
                    f = learnedWord.score.toInt(),
                    g = learnedWord.score.toInt(),
                    tango = learnedWord.out,
                    len = learnedWord.input.length.toShort(),
                    sPos = i
                )
                addOrUpdateNode(graph, endIndex, node)
            }

            // 3. システム辞書 (通常検索)
            val commonPrefixSearchSystem: List<String> = yomiTrie.commonPrefixSearch(
                str = subStr,
                succinctBitVector = succinctBitVectorLBSYomi
            )
            if (commonPrefixSearchSystem.isNotEmpty()) foundInAnyDictionary = true
            for (yomiStr in commonPrefixSearchSystem) {
                val nodeIndex = yomiTrie.getNodeIndex(yomiStr, succinctBitVectorLBSYomi)
                if (nodeIndex > 0) {
                    val termId = yomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafYomi)
                    val listToken = tokenArray.getListDictionaryByYomiTermId(
                        termId,
                        succinctBitVectorTokenArray
                    )
                    val endIndex = i + yomiStr.length
                    listToken.forEach { token ->
                        val tango = when (token.nodeId) {
                            -2 -> yomiStr
                            -1 -> yomiStr.hiraToKata()
                            else -> tangoTrie.getLetter(
                                token.nodeId,
                                succinctBitVector = succinctBitVectorTangoLBS
                            )
                        }
                        val node = Node(
                            l = tokenArray.leftIds[token.posTableIndex.toInt()],
                            r = tokenArray.rightIds[token.posTableIndex.toInt()],
                            score = token.wordCost.toInt(),
                            f = token.wordCost.toInt(),
                            g = token.wordCost.toInt(),
                            tango = tango,
                            len = yomiStr.length.toShort(),
                            sPos = i
                        )
                        addOrUpdateNode(graph, endIndex, node)
                    }
                }
            }

            // 3.x システム辞書 (Typo Correction Prefix)
            if (enableTypoCorrectionJapaneseFlick && subStr.length > 2) {
                val typoPrefixResults = yomiTrie.commonPrefixSearchWithTypoCorrectionPrefix(
                    str = subStr,
                    succinctBitVector = succinctBitVectorLBSYomi,
                    maxResults = 98,
                    maxLen = 12, // ここは調整（予測変換の最大長に合わせる）
                )

                // 見つかったら辞書ヒット扱いにして未知語フォールバック抑制
                if (typoPrefixResults.isNotEmpty()) foundInAnyDictionary = true

                for (typo in typoPrefixResults) {
                    // penaltyUsed==0 は通常検索と重複しやすいのでスキップ推奨
                    if (typo.penaltyUsed == 0) continue

                    val yomiStr = typo.yomi
                    val nodeIndex = yomiTrie.getNodeIndex(yomiStr, succinctBitVectorLBSYomi)
                    if (nodeIndex <= 0) continue

                    val termId = yomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafYomi)
                    val listToken = tokenArray.getListDictionaryByYomiTermId(
                        termId,
                        succinctBitVectorTokenArray
                    )

                    val endIndex = i + yomiStr.length
                    val penalty = TYPO_SCORE_OFFSET * typo.penaltyUsed

                    listToken
                        .sortedBy { it.wordCost }
                        .take(5)
                        .forEach { token ->
                            val tango = when (token.nodeId) {
                                -2 -> yomiStr
                                -1 -> yomiStr.hiraToKata()
                                else -> tangoTrie.getLetter(token.nodeId, succinctBitVectorTangoLBS)
                            }

                            val cost = token.wordCost.toInt() + penalty

                            addOrUpdateNode(
                                graph,
                                endIndex,
                                Node(
                                    l = tokenArray.leftIds[token.posTableIndex.toInt()],
                                    r = tokenArray.rightIds[token.posTableIndex.toInt()],
                                    score = cost,
                                    f = cost,
                                    g = cost,
                                    tango = tango,
                                    len = yomiStr.length.toShort(),
                                    sPos = i,
                                )
                            )
                        }
                }
            }

            // 4. システム辞書 (Omission Search)
            if (isOmissionSearchEnable && !subStr.hasNConsecutiveChars(4)) {
                val omissionSearchResults: List<OmissionSearchResult> =
                    yomiTrie.commonPrefixSearchWithOmission(
                        str = subStr,
                        succinctBitVector = succinctBitVectorLBSYomi
                    )
                if (omissionSearchResults.isNotEmpty()) foundInAnyDictionary = true
                for (omissionResult in omissionSearchResults) {
                    val yomiStr = omissionResult.yomi
                    val didOmit = omissionResult.omissionOccurred
                    val nodeIndex = yomiTrie.getNodeIndex(yomiStr, succinctBitVectorLBSYomi)
                    if (nodeIndex > 0) {
                        val termId = yomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafYomi)
                        val listToken = tokenArray.getListDictionaryByYomiTermId(
                            termId,
                            succinctBitVectorTokenArray
                        )
                        val endIndex = i + yomiStr.length
                        listToken.sortedBy { it.wordCost }.take(5).forEach { token ->
                            val tango = when (token.nodeId) {
                                -2 -> yomiStr
                                -1 -> yomiStr.hiraToKata()
                                else -> tangoTrie.getLetter(
                                    token.nodeId,
                                    succinctBitVector = succinctBitVectorTangoLBS
                                )
                            }
                            val scoreOffset = if (didOmit) OMISSION_SCORE_OFFSET else 0
                            val node = Node(
                                l = tokenArray.leftIds[token.posTableIndex.toInt()],
                                r = tokenArray.rightIds[token.posTableIndex.toInt()],
                                score = token.wordCost.toInt() + scoreOffset,
                                f = token.wordCost.toInt() + scoreOffset,
                                g = token.wordCost.toInt() + scoreOffset,
                                tango = tango,
                                len = yomiStr.length.toShort(),
                                sPos = i
                            )
                            addOrUpdateNode(graph, endIndex, node)
                        }
                    }
                }
            }

            // 5. Wiki辞書
            if (wikiYomiTrie != null && wikiTangoTrie != null && wikiTokenArray != null &&
                succinctBitVectorLBSWikiYomi != null && succinctBitVectorWikiTangoLBS != null &&
                succinctBitVectorWikiTokenArray != null && succinctBitVectorIsLeafWikiYomi != null
            ) {
                val commonPrefixSearchWiki: List<String> = wikiYomiTrie.commonPrefixSearch(
                    str = subStr,
                    succinctBitVector = succinctBitVectorLBSWikiYomi
                )
                if (commonPrefixSearchWiki.isNotEmpty()) foundInAnyDictionary = true
                for (yomiStr in commonPrefixSearchWiki) {
                    val nodeIndex = wikiYomiTrie.getNodeIndex(yomiStr, succinctBitVectorLBSWikiYomi)
                    if (nodeIndex > 0) {
                        val termId =
                            wikiYomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafWikiYomi)
                        val listToken = wikiTokenArray.getListDictionaryByYomiTermId(
                            termId,
                            succinctBitVectorWikiTokenArray
                        )
                        val endIndex = i + yomiStr.length
                        listToken.forEach { token ->
                            val tango = when (token.nodeId) {
                                -2 -> yomiStr
                                -1 -> yomiStr.hiraToKata()
                                else -> wikiTangoTrie.getLetter(
                                    token.nodeId,
                                    succinctBitVector = succinctBitVectorWikiTangoLBS
                                )
                            }
                            val node = Node(
                                l = wikiTokenArray.leftIds[token.posTableIndex.toInt()],
                                r = wikiTokenArray.rightIds[token.posTableIndex.toInt()],
                                score = token.wordCost.toInt(),
                                f = token.wordCost.toInt(),
                                g = token.wordCost.toInt(),
                                tango = tango,
                                len = yomiStr.length.toShort(),
                                sPos = i
                            )
                            addOrUpdateNode(graph, endIndex, node)
                        }
                    }
                }
            }

            // 6. Web辞書
            if (webYomiTrie != null && webTangoTrie != null && webTokenArray != null &&
                succinctBitVectorLBSwebYomi != null && succinctBitVectorwebTangoLBS != null &&
                succinctBitVectorwebTokenArray != null && succinctBitVectorIsLeafwebYomi != null
            ) {
                val commonPrefixSearchWeb: List<String> = webYomiTrie.commonPrefixSearch(
                    str = subStr,
                    succinctBitVector = succinctBitVectorLBSwebYomi
                )
                if (commonPrefixSearchWeb.isNotEmpty()) foundInAnyDictionary = true
                for (yomiStr in commonPrefixSearchWeb) {
                    val nodeIndex = webYomiTrie.getNodeIndex(yomiStr, succinctBitVectorLBSwebYomi)
                    if (nodeIndex > 0) {
                        val termId =
                            webYomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafwebYomi)
                        val listToken = webTokenArray.getListDictionaryByYomiTermId(
                            termId,
                            succinctBitVectorwebTokenArray
                        )
                        val endIndex = i + yomiStr.length
                        listToken.forEach { token ->
                            val tango = when (token.nodeId) {
                                -2 -> yomiStr
                                -1 -> yomiStr.hiraToKata()
                                else -> webTangoTrie.getLetter(
                                    token.nodeId,
                                    succinctBitVector = succinctBitVectorwebTangoLBS
                                )
                            }
                            val node = Node(
                                l = webTokenArray.leftIds[token.posTableIndex.toInt()],
                                r = webTokenArray.rightIds[token.posTableIndex.toInt()],
                                score = token.wordCost.toInt(),
                                f = token.wordCost.toInt(),
                                g = token.wordCost.toInt(),
                                tango = tango,
                                len = yomiStr.length.toShort(),
                                sPos = i
                            )
                            addOrUpdateNode(graph, endIndex, node)
                        }
                    }
                }
            }

            // 7. 人名辞書
            if (personYomiTrie != null && personTangoTrie != null && personTokenArray != null &&
                succinctBitVectorLBSpersonYomi != null && succinctBitVectorpersonTangoLBS != null &&
                succinctBitVectorpersonTokenArray != null && succinctBitVectorIsLeafpersonYomi != null
            ) {
                val commonPrefixSearchPerson: List<String> = personYomiTrie.commonPrefixSearch(
                    str = subStr,
                    succinctBitVector = succinctBitVectorLBSpersonYomi
                )
                if (commonPrefixSearchPerson.isNotEmpty()) foundInAnyDictionary = true
                for (yomiStr in commonPrefixSearchPerson) {
                    val nodeIndex =
                        personYomiTrie.getNodeIndex(yomiStr, succinctBitVectorLBSpersonYomi)
                    if (nodeIndex > 0) {
                        val termId =
                            personYomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafpersonYomi)
                        val listToken = personTokenArray.getListDictionaryByYomiTermId(
                            termId,
                            succinctBitVectorpersonTokenArray
                        )
                        val endIndex = i + yomiStr.length
                        listToken.forEach { token ->
                            val tango = when (token.nodeId) {
                                -2 -> yomiStr
                                -1 -> yomiStr.hiraToKata()
                                else -> personTangoTrie.getLetter(
                                    token.nodeId,
                                    succinctBitVector = succinctBitVectorpersonTangoLBS
                                )
                            }
                            val node = Node(
                                l = personTokenArray.leftIds[token.posTableIndex.toInt()],
                                r = personTokenArray.rightIds[token.posTableIndex.toInt()],
                                score = token.wordCost.toInt(),
                                f = token.wordCost.toInt(),
                                g = token.wordCost.toInt(),
                                tango = tango,
                                len = yomiStr.length.toShort(),
                                sPos = i
                            )
                            addOrUpdateNode(graph, endIndex, node)
                        }
                    }
                }
            }

            // 8. Neologd辞書
            if (neologdYomiTrie != null && neologdTangoTrie != null && neologdTokenArray != null &&
                succinctBitVectorLBSneologdYomi != null && succinctBitVectorneologdTangoLBS != null &&
                succinctBitVectorneologdTokenArray != null && succinctBitVectorIsLeafneologdYomi != null
            ) {
                val commonPrefixSearchNeologd: List<String> = neologdYomiTrie.commonPrefixSearch(
                    str = subStr,
                    succinctBitVector = succinctBitVectorLBSneologdYomi
                )
                if (commonPrefixSearchNeologd.isNotEmpty()) foundInAnyDictionary = true
                for (yomiStr in commonPrefixSearchNeologd) {
                    val nodeIndex =
                        neologdYomiTrie.getNodeIndex(yomiStr, succinctBitVectorLBSneologdYomi)
                    if (nodeIndex > 0) {
                        val termId =
                            neologdYomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafneologdYomi)
                        val listToken = neologdTokenArray.getListDictionaryByYomiTermId(
                            termId,
                            succinctBitVectorneologdTokenArray
                        )
                        val endIndex = i + yomiStr.length
                        listToken.forEach { token ->
                            val tango = when (token.nodeId) {
                                -2 -> yomiStr
                                -1 -> yomiStr.hiraToKata()
                                else -> neologdTangoTrie.getLetter(
                                    token.nodeId,
                                    succinctBitVector = succinctBitVectorneologdTangoLBS
                                )
                            }
                            val node = Node(
                                l = neologdTokenArray.leftIds[token.posTableIndex.toInt()],
                                r = neologdTokenArray.rightIds[token.posTableIndex.toInt()],
                                score = token.wordCost.toInt(),
                                f = token.wordCost.toInt(),
                                g = token.wordCost.toInt(),
                                tango = tango,
                                len = yomiStr.length.toShort(),
                                sPos = i
                            )
                            addOrUpdateNode(graph, endIndex, node)
                        }
                    }
                }
            }

            // 9. どの辞書にもヒットしなかった場合のフォールバック
            if (!foundInAnyDictionary && subStr.isNotEmpty()) {
                val yomiStr = subStr.substring(0, 1) // 1文字だけを未知語として切り出す
                val endIndex = i + yomiStr.length
                val unknownNode = Node(
                    l = 0, // 未知語用のID (一般名詞など)
                    r = 0, // 未知語用のID (一般名詞など)
                    score = 10000, // 高いコスト
                    f = 10000,
                    g = 10000,
                    tango = yomiStr, // 読みをそのまま単語とする
                    len = yomiStr.length.toShort(),
                    sPos = i,
                )
                // 未知語は重複を考慮せずそのまま追加する
                graph.computeIfAbsent(endIndex) { mutableListOf() }.add(unknownNode)
            }
        }
        return graph
    }
}
