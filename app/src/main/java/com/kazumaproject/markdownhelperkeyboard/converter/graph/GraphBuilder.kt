package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.core.domain.extensions.hasNConsecutiveChars
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.graph.MozcNodeAttributes
import com.kazumaproject.graph.MozcNodeType
import com.kazumaproject.graph.Node
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.Other.BOS
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcNodeAttributeTable
import com.kazumaproject.markdownhelperkeyboard.converter.trace.GraphNodeTrace
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllHalfWidthAscii
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.PosMapper

class GraphBuilder {

    private var systemUserYomiTrie: LOUDSWithTermId? = null
    private var systemUserTangoTrie: LOUDS? = null
    private var systemUserTokenArray: TokenArray? = null
    private var systemUserSuccinctBitVectorLBSYomi: SuccinctBitVector? = null
    private var systemUserSuccinctBitVectorIsLeafYomi: SuccinctBitVector? = null
    private var systemUserSuccinctBitVectorTokenArray: SuccinctBitVector? = null
    private var systemUserSuccinctBitVectorTangoLBS: SuccinctBitVector? = null

    fun updateSystemUserDictionary(
        yomiTrie: LOUDSWithTermId?,
        tangoTrie: LOUDS?,
        tokenArray: TokenArray?,
        succinctBitVectorLBSYomi: SuccinctBitVector?,
        succinctBitVectorIsLeafYomi: SuccinctBitVector?,
        succinctBitVectorTokenArray: SuccinctBitVector?,
        succinctBitVectorTangoLBS: SuccinctBitVector?,
    ) {
        systemUserYomiTrie = yomiTrie
        systemUserTangoTrie = tangoTrie
        systemUserTokenArray = tokenArray
        systemUserSuccinctBitVectorLBSYomi = succinctBitVectorLBSYomi
        systemUserSuccinctBitVectorIsLeafYomi = succinctBitVectorIsLeafYomi
        systemUserSuccinctBitVectorTokenArray = succinctBitVectorTokenArray
        systemUserSuccinctBitVectorTangoLBS = succinctBitVectorTangoLBS
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
        newNode: Node,
        mode: GraphNodeDedupMode,
        trace: MutableList<GraphNodeTrace>?,
        input: String,
        source: String,
    ) {
        val nodes = graph.computeIfAbsent(endIndex) { mutableListOf() }

        when (mode) {
            GraphNodeDedupMode.MOZC_KEEP_ALL -> {
                nodes.add(newNode)
                trace?.add(newNode.toTrace(input, endIndex, source, "ADDED"))
            }
            GraphNodeDedupMode.EXISTING_BY_TANGO_L_R -> {
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
                        trace?.add(newNode.toTrace(input, endIndex, source, "REPLACED"))
                    } else {
                        trace?.add(newNode.toTrace(input, endIndex, source, "SKIPPED"))
                    }
                } else {
                    // 新しい単語、または、同じ単語だが品詞が異なる場合は、単純に追加する
                    nodes.add(newNode)
                    trace?.add(newNode.toTrace(input, endIndex, source, "ADDED"))
                }
            }
        }
    }

    private fun Node.toTrace(
        input: String,
        endIndex: Int,
        source: String,
        event: String,
    ): GraphNodeTrace =
        GraphNodeTrace(
            input = input,
            yomiUsed = yomiUsed,
            tango = tango,
            startPos = sPos,
            endPos = endIndex,
            leftId = l.toInt(),
            rightId = r.toInt(),
            wordCost = score,
            source = source,
            event = event,
        )

    suspend fun constructGraph(
        str: String,
        yomiTrie: LOUDSWithTermId,
        tangoTrie: LOUDS,
        tokenArray: TokenArray,
        succinctBitVectorLBSYomi: SuccinctBitVector,
        succinctBitVectorIsLeafYomi: SuccinctBitVector,
        succinctBitVectorTokenArray: SuccinctBitVector,
        succinctBitVectorTangoLBS: SuccinctBitVector,
        userDictionaryRepository: UserDictionaryRepository?,
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
        enableTypoCorrectionJapaneseFlick: Boolean = false,
        typoCorrectionOffsetScore: Int,
        omissionSearchOffSetScore: Int,
        graphNodeDedupMode: GraphNodeDedupMode = GraphNodeDedupMode.EXISTING_BY_TANGO_L_R,
        mozcNodeAttributeTable: MozcNodeAttributeTable? = null,
        graphNodeTrace: MutableList<GraphNodeTrace>? = null,
    ): MutableMap<Int, MutableList<Node>> {
        if (str.isAllHalfWidthAscii()) return mutableMapOf()
        fun mozcAttributesFor(leftId: Short): Int =
            mozcNodeAttributeTable?.attributesFor(leftId.toInt()) ?: MozcNodeAttributes.NONE

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
                yomiUsed = "EOS",
                len = 0,
                sPos = str.length + 1,
                mozcNodeType = MozcNodeType.EOS,
            )
        )
        for (i in str.indices) {
            var subStrCache: String? = null
            fun subStr(): String {
                val cached = subStrCache
                if (cached != null) return cached
                return str.substring(i).also { subStrCache = it }
            }
            var foundInAnyDictionary = false

            // 1. ユーザー辞書
            val userWords = userDictionaryRepository?.commonPrefixSearchInUserDict(subStr()) ?: emptyList()
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
                    yomiUsed = userWord.reading,
                    len = userWord.reading.length.toShort(),
                    sPos = i,
                    mozcAttributes = mozcAttributesFor(contextId),
                )
                addOrUpdateNode(graph, endIndex, node, graphNodeDedupMode, graphNodeTrace, str, "USER")
            }

            // 2. 学習辞書
            val learnedWords = learnRepository?.findCommonPrefixes(subStr()) ?: emptyList()
            if (learnedWords.isNotEmpty()) foundInAnyDictionary = true
            learnedWords.forEach { learnedWord ->
                val endIndex = i + learnedWord.input.length
                val node = Node(
                    l = learnedWord.leftId ?: 1851.toShort(),
                    r = learnedWord.rightId ?: 1851.toShort(),
                    score = learnedWord.score.toInt(),
                    f = learnedWord.score.toInt(),
                    g = learnedWord.score.toInt(),
                    tango = learnedWord.out,
                    yomiUsed = learnedWord.input,
                    len = learnedWord.input.length.toShort(),
                    sPos = i,
                    mozcAttributes = mozcAttributesFor(learnedWord.leftId ?: 1851.toShort()),
                )
                addOrUpdateNode(graph, endIndex, node, graphNodeDedupMode, graphNodeTrace, str, "LEARN")
            }

            val localSystemUserYomiTrie = systemUserYomiTrie
            val localSystemUserTangoTrie = systemUserTangoTrie
            val localSystemUserTokenArray = systemUserTokenArray
            val localSystemUserLBSYomi = systemUserSuccinctBitVectorLBSYomi
            val localSystemUserIsLeaf = systemUserSuccinctBitVectorIsLeafYomi
            val localSystemUserTokenBitVector = systemUserSuccinctBitVectorTokenArray
            val localSystemUserTangoLBS = systemUserSuccinctBitVectorTangoLBS

            if (
                localSystemUserYomiTrie != null &&
                localSystemUserTangoTrie != null &&
                localSystemUserTokenArray != null &&
                localSystemUserLBSYomi != null &&
                localSystemUserIsLeaf != null &&
                localSystemUserTokenBitVector != null &&
                localSystemUserTangoLBS != null
            ) {
                val systemUserPrefixSearch = localSystemUserYomiTrie.commonPrefixSearchWithNodeIndex(
                    str = str,
                    start = i,
                    succinctBitVector = localSystemUserLBSYomi,
                )
                if (systemUserPrefixSearch.isNotEmpty()) foundInAnyDictionary = true
                for (prefixResult in systemUserPrefixSearch) {
                    val yomiStr = prefixResult.yomi
                    val nodeIndex = prefixResult.nodeIndex
                    if (nodeIndex <= 0) continue
                    val termId = localSystemUserYomiTrie.getTermId(nodeIndex, localSystemUserIsLeaf)
                    val endIndex = i + yomiStr.length
                    localSystemUserTokenArray.forEachDictionaryByYomiTermId(
                        termId,
                        localSystemUserTokenBitVector,
                    ) { posTableIndex, wordCost, tokenNodeId ->
                        val tango = when (tokenNodeId) {
                            -2 -> yomiStr
                            -1 -> yomiStr.hiraToKata()
                            else -> localSystemUserTangoTrie.getLetter(
                                tokenNodeId,
                                succinctBitVector = localSystemUserTangoLBS,
                            )
                        }
                        val leftId = localSystemUserTokenArray.leftIds[posTableIndex.toInt()]
                        addOrUpdateNode(
                            graph,
                            endIndex,
                            Node(
                                l = leftId,
                                r = localSystemUserTokenArray.rightIds[posTableIndex.toInt()],
                                score = wordCost.toInt(),
                                f = wordCost.toInt(),
                                g = wordCost.toInt(),
                                tango = tango,
                                yomiUsed = yomiStr,
                                len = yomiStr.length.toShort(),
                                sPos = i,
                                mozcAttributes = mozcAttributesFor(
                                    leftId,
                                ),
                            ),
                            graphNodeDedupMode,
                            graphNodeTrace,
                            str,
                            "SYSTEM_USER",
                        )
                    }
                }

                // 1.x システムユーザー辞書 (Typo Correction Prefix)
                if (enableTypoCorrectionJapaneseFlick && subStr().length > 2) {
                    val typoPrefixResults = localSystemUserYomiTrie.commonPrefixSearchWithTypoCorrectionPrefix(
                        str = subStr(),
                        succinctBitVector = localSystemUserLBSYomi,
                        maxResults = 98,
                        maxLen = 12,
                    )
                    if (typoPrefixResults.isNotEmpty()) foundInAnyDictionary = true

                    for (typo in typoPrefixResults) {
                        if (typo.penaltyUsed == 0) continue

                        val yomiStr = typo.yomi
                        val nodeIndex = localSystemUserYomiTrie.getNodeIndex(
                            yomiStr,
                            localSystemUserLBSYomi,
                        )
                        if (nodeIndex <= 0) continue

                        val termId = localSystemUserYomiTrie.getTermId(
                            nodeIndex,
                            localSystemUserIsLeaf,
                        )
                        val listToken = localSystemUserTokenArray.getListDictionaryByYomiTermId(
                            termId,
                            localSystemUserTokenBitVector,
                        )
                        val endIndex = i + yomiStr.length
                        val penalty = typoCorrectionOffsetScore * typo.penaltyUsed

                        listToken
                            .sortedBy { it.wordCost }
                            .take(5)
                            .forEach { token ->
                                val tango = when (token.nodeId) {
                                    -2 -> yomiStr
                                    -1 -> yomiStr.hiraToKata()
                                    else -> localSystemUserTangoTrie.getLetter(
                                        token.nodeId,
                                        succinctBitVector = localSystemUserTangoLBS,
                                    )
                                }
                                val cost = token.wordCost.toInt() + penalty
                                addOrUpdateNode(
                                    graph,
                                    endIndex,
                                    Node(
                                        l = localSystemUserTokenArray.leftIds[token.posTableIndex.toInt()],
                                        r = localSystemUserTokenArray.rightIds[token.posTableIndex.toInt()],
                                        score = cost,
                                        f = cost,
                                        g = cost,
                                        tango = tango,
                                        yomiUsed = yomiStr,
                                        len = yomiStr.length.toShort(),
                                        sPos = i,
                                        mozcAttributes = mozcAttributesFor(
                                            localSystemUserTokenArray.leftIds[token.posTableIndex.toInt()],
                                        ),
                                    ),
                                    graphNodeDedupMode,
                                    graphNodeTrace,
                                    str,
                                    "SYSTEM_USER_TYPO",
                                )
                            }
                    }
                }

                // 1.y システムユーザー辞書 (Omission Search)
                if (isOmissionSearchEnable && !subStr().hasNConsecutiveChars(4)) {
                    val omissionSearchResults = localSystemUserYomiTrie.commonPrefixSearchWithOmission(
                        str = subStr(),
                        succinctBitVector = localSystemUserLBSYomi,
                    )
                    if (omissionSearchResults.isNotEmpty()) foundInAnyDictionary = true

                    for (omissionResult in omissionSearchResults) {
                        val yomiStr = omissionResult.yomi
                        val didOmit = omissionResult.omissionOccurred
                        val nodeIndex = localSystemUserYomiTrie.getNodeIndex(
                            yomiStr,
                            localSystemUserLBSYomi,
                        )
                        if (nodeIndex <= 0) continue

                        val termId = localSystemUserYomiTrie.getTermId(nodeIndex, localSystemUserIsLeaf)
                        val listToken = localSystemUserTokenArray.getListDictionaryByYomiTermId(
                            termId,
                            localSystemUserTokenBitVector,
                        )
                        val endIndex = i + yomiStr.length

                        listToken.sortedBy { it.wordCost }.take(5).forEach { token ->
                            val tango = when (token.nodeId) {
                                -2 -> yomiStr
                                -1 -> yomiStr.hiraToKata()
                                else -> localSystemUserTangoTrie.getLetter(
                                    token.nodeId,
                                    succinctBitVector = localSystemUserTangoLBS,
                                )
                            }
                            val scoreOffset = if (didOmit) omissionSearchOffSetScore else 0
                            addOrUpdateNode(
                                graph,
                                endIndex,
                                Node(
                                    l = localSystemUserTokenArray.leftIds[token.posTableIndex.toInt()],
                                    r = localSystemUserTokenArray.rightIds[token.posTableIndex.toInt()],
                                    score = token.wordCost.toInt() + scoreOffset,
                                    f = token.wordCost.toInt() + scoreOffset,
                                    g = token.wordCost.toInt() + scoreOffset,
                                    tango = tango,
                                    yomiUsed = yomiStr,
                                    len = yomiStr.length.toShort(),
                                    sPos = i,
                                    mozcAttributes = mozcAttributesFor(
                                        localSystemUserTokenArray.leftIds[token.posTableIndex.toInt()],
                                    ),
                                ),
                                graphNodeDedupMode,
                                graphNodeTrace,
                                str,
                                "SYSTEM_USER_OMISSION",
                            )
                        }
                    }
                }
            }

            // 3. システム辞書 (通常検索)
            val commonPrefixSearchSystem = yomiTrie.commonPrefixSearchWithNodeIndex(
                str = str,
                start = i,
                succinctBitVector = succinctBitVectorLBSYomi
            )
            if (commonPrefixSearchSystem.isNotEmpty()) foundInAnyDictionary = true
            for (prefixResult in commonPrefixSearchSystem) {
                val yomiStr = prefixResult.yomi
                val nodeIndex = prefixResult.nodeIndex
                if (nodeIndex > 0) {
                    val termId = yomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafYomi)
                    val endIndex = i + yomiStr.length
                    tokenArray.forEachDictionaryByYomiTermId(
                        termId,
                        succinctBitVectorTokenArray
                    ) { posTableIndex, wordCost, tokenNodeId ->
                        val tango = when (tokenNodeId) {
                            -2 -> yomiStr
                            -1 -> yomiStr.hiraToKata()
                            else -> tangoTrie.getLetter(
                                tokenNodeId,
                                succinctBitVector = succinctBitVectorTangoLBS
                            )
                        }
                        val leftId = tokenArray.leftIds[posTableIndex.toInt()]
                        val node = Node(
                            l = leftId,
                            r = tokenArray.rightIds[posTableIndex.toInt()],
                            score = wordCost.toInt(),
                            f = wordCost.toInt(),
                            g = wordCost.toInt(),
                            tango = tango,
                            yomiUsed = yomiStr,
                            len = yomiStr.length.toShort(),
                            sPos = i,
                            mozcAttributes = mozcAttributesFor(leftId),
                        )
                        addOrUpdateNode(graph, endIndex, node, graphNodeDedupMode, graphNodeTrace, str, "SYSTEM")
                    }
                }
            }

            // 3.x システム辞書 (Typo Correction Prefix)
            if (enableTypoCorrectionJapaneseFlick && subStr().length > 2) {
                val typoPrefixResults = yomiTrie.commonPrefixSearchWithTypoCorrectionPrefix(
                    str = subStr(),
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
                    val penalty = typoCorrectionOffsetScore * typo.penaltyUsed

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
                                    yomiUsed = yomiStr,
                                    len = yomiStr.length.toShort(),
                                    sPos = i,
                                    mozcAttributes = mozcAttributesFor(tokenArray.leftIds[token.posTableIndex.toInt()]),
                                ),
                                graphNodeDedupMode,
                                graphNodeTrace,
                                str,
                                "SYSTEM_TYPO",
                            )
                        }
                }
            }

            // 4. システム辞書 (Omission Search)
            if (isOmissionSearchEnable && !subStr().hasNConsecutiveChars(4)) {
                val omissionSearchResults: List<OmissionSearchResult> =
                    yomiTrie.commonPrefixSearchWithOmission(
                        str = subStr(),
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
                            val scoreOffset = if (didOmit) omissionSearchOffSetScore else 0
                            val node = Node(
                                l = tokenArray.leftIds[token.posTableIndex.toInt()],
                                r = tokenArray.rightIds[token.posTableIndex.toInt()],
                                score = token.wordCost.toInt() + scoreOffset,
                                f = token.wordCost.toInt() + scoreOffset,
                                g = token.wordCost.toInt() + scoreOffset,
                                tango = tango,
                                yomiUsed = yomiStr,
                                len = yomiStr.length.toShort(),
                                sPos = i,
                                mozcAttributes = mozcAttributesFor(tokenArray.leftIds[token.posTableIndex.toInt()]),
                            )
                            addOrUpdateNode(graph, endIndex, node, graphNodeDedupMode, graphNodeTrace, str, "SYSTEM_OMISSION")
                        }
                    }
                }
            }

            // 5. Wiki辞書
            if (wikiYomiTrie != null && wikiTangoTrie != null && wikiTokenArray != null &&
                succinctBitVectorLBSWikiYomi != null && succinctBitVectorWikiTangoLBS != null &&
                succinctBitVectorWikiTokenArray != null && succinctBitVectorIsLeafWikiYomi != null
            ) {
                val commonPrefixSearchWiki = wikiYomiTrie.commonPrefixSearchWithNodeIndex(
                    str = str,
                    start = i,
                    succinctBitVector = succinctBitVectorLBSWikiYomi
                )
                if (commonPrefixSearchWiki.isNotEmpty()) foundInAnyDictionary = true
                for (prefixResult in commonPrefixSearchWiki) {
                    val yomiStr = prefixResult.yomi
                    val nodeIndex = prefixResult.nodeIndex
                    if (nodeIndex > 0) {
                        val termId =
                            wikiYomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafWikiYomi)
                        val endIndex = i + yomiStr.length
                        wikiTokenArray.forEachDictionaryByYomiTermId(
                            termId,
                            succinctBitVectorWikiTokenArray
                        ) { posTableIndex, wordCost, tokenNodeId ->
                            val tango = when (tokenNodeId) {
                                -2 -> yomiStr
                                -1 -> yomiStr.hiraToKata()
                                else -> wikiTangoTrie.getLetter(
                                    tokenNodeId,
                                    succinctBitVector = succinctBitVectorWikiTangoLBS
                                )
                            }
                            val leftId = wikiTokenArray.leftIds[posTableIndex.toInt()]
                            val node = Node(
                                l = leftId,
                                r = wikiTokenArray.rightIds[posTableIndex.toInt()],
                                score = wordCost.toInt(),
                                f = wordCost.toInt(),
                                g = wordCost.toInt(),
                                tango = tango,
                                yomiUsed = yomiStr,
                                len = yomiStr.length.toShort(),
                                sPos = i,
                                mozcAttributes = mozcAttributesFor(leftId),
                            )
                            addOrUpdateNode(graph, endIndex, node, graphNodeDedupMode, graphNodeTrace, str, "WIKI")
                        }
                    }
                }
            }

            // 6. Web辞書
            if (webYomiTrie != null && webTangoTrie != null && webTokenArray != null &&
                succinctBitVectorLBSwebYomi != null && succinctBitVectorwebTangoLBS != null &&
                succinctBitVectorwebTokenArray != null && succinctBitVectorIsLeafwebYomi != null
            ) {
                val commonPrefixSearchWeb = webYomiTrie.commonPrefixSearchWithNodeIndex(
                    str = str,
                    start = i,
                    succinctBitVector = succinctBitVectorLBSwebYomi
                )
                if (commonPrefixSearchWeb.isNotEmpty()) foundInAnyDictionary = true
                for (prefixResult in commonPrefixSearchWeb) {
                    val yomiStr = prefixResult.yomi
                    val nodeIndex = prefixResult.nodeIndex
                    if (nodeIndex > 0) {
                        val termId =
                            webYomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafwebYomi)
                        val endIndex = i + yomiStr.length
                        webTokenArray.forEachDictionaryByYomiTermId(
                            termId,
                            succinctBitVectorwebTokenArray
                        ) { posTableIndex, wordCost, tokenNodeId ->
                            val tango = when (tokenNodeId) {
                                -2 -> yomiStr
                                -1 -> yomiStr.hiraToKata()
                                else -> webTangoTrie.getLetter(
                                    tokenNodeId,
                                    succinctBitVector = succinctBitVectorwebTangoLBS
                                )
                            }
                            val leftId = webTokenArray.leftIds[posTableIndex.toInt()]
                            val node = Node(
                                l = leftId,
                                r = webTokenArray.rightIds[posTableIndex.toInt()],
                                score = wordCost.toInt(),
                                f = wordCost.toInt(),
                                g = wordCost.toInt(),
                                tango = tango,
                                yomiUsed = yomiStr,
                                len = yomiStr.length.toShort(),
                                sPos = i,
                                mozcAttributes = mozcAttributesFor(leftId),
                            )
                            addOrUpdateNode(graph, endIndex, node, graphNodeDedupMode, graphNodeTrace, str, "WEB")
                        }
                    }
                }
            }

            // 7. 人名辞書
            if (personYomiTrie != null && personTangoTrie != null && personTokenArray != null &&
                succinctBitVectorLBSpersonYomi != null && succinctBitVectorpersonTangoLBS != null &&
                succinctBitVectorpersonTokenArray != null && succinctBitVectorIsLeafpersonYomi != null
            ) {
                val commonPrefixSearchPerson = personYomiTrie.commonPrefixSearchWithNodeIndex(
                    str = str,
                    start = i,
                    succinctBitVector = succinctBitVectorLBSpersonYomi
                )
                if (commonPrefixSearchPerson.isNotEmpty()) foundInAnyDictionary = true
                for (prefixResult in commonPrefixSearchPerson) {
                    val yomiStr = prefixResult.yomi
                    val nodeIndex = prefixResult.nodeIndex
                    if (nodeIndex > 0) {
                        val termId =
                            personYomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafpersonYomi)
                        val endIndex = i + yomiStr.length
                        personTokenArray.forEachDictionaryByYomiTermId(
                            termId,
                            succinctBitVectorpersonTokenArray
                        ) { posTableIndex, wordCost, tokenNodeId ->
                            val tango = when (tokenNodeId) {
                                -2 -> yomiStr
                                -1 -> yomiStr.hiraToKata()
                                else -> personTangoTrie.getLetter(
                                    tokenNodeId,
                                    succinctBitVector = succinctBitVectorpersonTangoLBS
                                )
                            }
                            val leftId = personTokenArray.leftIds[posTableIndex.toInt()]
                            val node = Node(
                                l = leftId,
                                r = personTokenArray.rightIds[posTableIndex.toInt()],
                                score = wordCost.toInt(),
                                f = wordCost.toInt(),
                                g = wordCost.toInt(),
                                tango = tango,
                                yomiUsed = yomiStr,
                                len = yomiStr.length.toShort(),
                                sPos = i,
                                mozcAttributes = mozcAttributesFor(leftId),
                            )
                            addOrUpdateNode(graph, endIndex, node, graphNodeDedupMode, graphNodeTrace, str, "PERSON")
                        }
                    }
                }
            }

            // 8. Neologd辞書
            if (neologdYomiTrie != null && neologdTangoTrie != null && neologdTokenArray != null &&
                succinctBitVectorLBSneologdYomi != null && succinctBitVectorneologdTangoLBS != null &&
                succinctBitVectorneologdTokenArray != null && succinctBitVectorIsLeafneologdYomi != null
            ) {
                val commonPrefixSearchNeologd = neologdYomiTrie.commonPrefixSearchWithNodeIndex(
                    str = str,
                    start = i,
                    succinctBitVector = succinctBitVectorLBSneologdYomi
                )
                if (commonPrefixSearchNeologd.isNotEmpty()) foundInAnyDictionary = true
                for (prefixResult in commonPrefixSearchNeologd) {
                    val yomiStr = prefixResult.yomi
                    val nodeIndex = prefixResult.nodeIndex
                    if (nodeIndex > 0) {
                        val termId =
                            neologdYomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafneologdYomi)
                        val endIndex = i + yomiStr.length
                        neologdTokenArray.forEachDictionaryByYomiTermId(
                            termId,
                            succinctBitVectorneologdTokenArray
                        ) { posTableIndex, wordCost, tokenNodeId ->
                            val tango = when (tokenNodeId) {
                                -2 -> yomiStr
                                -1 -> yomiStr.hiraToKata()
                                else -> neologdTangoTrie.getLetter(
                                    tokenNodeId,
                                    succinctBitVector = succinctBitVectorneologdTangoLBS
                                )
                            }
                            val leftId = neologdTokenArray.leftIds[posTableIndex.toInt()]
                            val node = Node(
                                l = leftId,
                                r = neologdTokenArray.rightIds[posTableIndex.toInt()],
                                score = wordCost.toInt(),
                                f = wordCost.toInt(),
                                g = wordCost.toInt(),
                                tango = tango,
                                yomiUsed = yomiStr,
                                len = yomiStr.length.toShort(),
                                sPos = i,
                                mozcAttributes = mozcAttributesFor(leftId),
                            )
                            addOrUpdateNode(graph, endIndex, node, graphNodeDedupMode, graphNodeTrace, str, "NEOLOGD")
                        }
                    }
                }
            }

            // 9. どの辞書にもヒットしなかった場合のフォールバック
            if (!foundInAnyDictionary && i < str.length) {
                val yomiStr = str.substring(i, i + 1) // 1文字だけを未知語として切り出す
                val endIndex = i + yomiStr.length
                val unknownNode = Node(
                    l = 0, // 未知語用のID (一般名詞など)
                    r = 0, // 未知語用のID (一般名詞など)
                    score = 10000, // 高いコスト
                    f = 10000,
                    g = 10000,
                    tango = yomiStr, // 読みをそのまま単語とする
                    yomiUsed = yomiStr,
                    len = yomiStr.length.toShort(),
                    sPos = i,
                    mozcAttributes = mozcAttributesFor(0),
                )
                // 未知語は重複を考慮せずそのまま追加する
                graph.computeIfAbsent(endIndex) { mutableListOf() }.add(unknownNode)
                graphNodeTrace?.add(unknownNode.toTrace(str, endIndex, "UNKNOWN", "ADDED"))
            }
        }
        return graph
    }
}
