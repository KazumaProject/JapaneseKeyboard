package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.core.domain.extensions.hasNConsecutiveChars
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.graph.CandidateSource
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class GraphBuilder {

    internal data class CachedGraph(
        val input: String,
        val signature: Int,
        val graph: MutableMap<Int, MutableList<Node>>,
        val systemOmissionStates: Map<Int, List<LOUDSWithTermId.OmissionSearchState>>,
        val systemUserOmissionStates: Map<Int, List<LOUDSWithTermId.OmissionSearchState>>,
        val systemTypoProgress: Map<Int, LOUDSWithTermId.TypoSearchProgress>,
        val systemUserTypoProgress: Map<Int, LOUDSWithTermId.TypoSearchProgress>,
        val systemPrefixStates: Map<Int, Int>,
        val systemUserPrefixStates: Map<Int, Int>,
        val wikiPrefixStates: Map<Int, Int>,
        val webPrefixStates: Map<Int, Int>,
        val personPrefixStates: Map<Int, Int>,
        val neologdPrefixStates: Map<Int, Int>,
        val unprunedPositions: Map<Int, MutableList<Node>>?,
    )

    /**
     * Incremental graph state owned by one IME conversion session.
     *
     * The legacy API keeps using [cachedGraph].  A session API supplies one of these states so
     * candidate-tab requests and other input sessions cannot evict each other's append frontier.
     */
    class SessionState internal constructor() {
        internal var cachedGraph: CachedGraph? = null
        internal var performanceProbeEnabled: Boolean = false
        internal var lastConstructGraphNs: Long = 0
        internal var lastAppendReused: Boolean = false

        private var queryTransaction: QueryTransaction? = null

        private data class InPlaceAppendRollback(
            val graph: IncrementalGraph,
            val previousReusedThroughEndIndex: Int,
            val previousForwardDpReusableThroughEndIndex: Int,
            val previousInputLength: Int,
            val previousPositions: Map<Int, MutableList<Node>?>,
            val previousEosIndex: Int,
            val previousEos: MutableList<Node>?,
        ) {
            fun restore() {
                graph.keys.removeAll { it > previousInputLength }
                previousPositions.forEach { (position, nodes) ->
                    nodes?.let { graph[position] = it } ?: graph.remove(position)
                }
                previousEos?.let { graph[previousEosIndex] = it }
                    ?: graph.remove(previousEosIndex)
                graph.reusedThroughEndIndex = previousReusedThroughEndIndex
                graph.forwardDpReusableThroughEndIndex =
                    previousForwardDpReusableThroughEndIndex
                check(graph.keys.none { it > previousEosIndex })
            }
        }

        private data class QueryTransaction(
            val committedCache: CachedGraph?,
            var inPlaceAppendRollback: InPlaceAppendRollback? = null,
        )

        internal fun beginQueryTransaction() {
            check(queryTransaction == null) { "Graph query transaction is already active" }
            queryTransaction = QueryTransaction(committedCache = cachedGraph)
        }

        internal fun registerInPlaceAppend(
            graph: IncrementalGraph,
            previousReusedThroughEndIndex: Int,
            previousForwardDpReusableThroughEndIndex: Int,
            previousInputLength: Int,
            positionsToRestore: Set<Int>,
            previousEosIndex: Int,
            previousEos: MutableList<Node>?,
        ) {
            queryTransaction?.inPlaceAppendRollback = InPlaceAppendRollback(
                graph = graph,
                previousReusedThroughEndIndex = previousReusedThroughEndIndex,
                previousForwardDpReusableThroughEndIndex =
                    previousForwardDpReusableThroughEndIndex,
                previousInputLength = previousInputLength,
                previousPositions = positionsToRestore.associateWith { graph[it] },
                previousEosIndex = previousEosIndex,
                previousEos = previousEos,
            )
        }

        internal fun commitQueryTransaction() {
            queryTransaction = null
        }

        internal fun committedInput(): String? = cachedGraph?.input

        internal fun rollbackQueryTransaction() {
            val transaction = queryTransaction ?: return
            transaction.inPlaceAppendRollback?.restore()
            cachedGraph = transaction.committedCache
            queryTransaction = null
        }

        internal fun reset() {
            queryTransaction = null
            cachedGraph = null
            lastConstructGraphNs = 0
            lastAppendReused = false
        }
    }

    internal class IncrementalGraph(
        override var reusedThroughEndIndex: Int,
        override val conversionSignature: Int,
        override var forwardDpReusableThroughEndIndex: Int = reusedThroughEndIndex,
    ) : LinkedHashMap<Int, MutableList<Node>>(), IncrementalGraphMetadata

    @Volatile
    private var cachedGraph: CachedGraph? = null

    fun createSessionState(): SessionState = SessionState()

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
        cachedGraph = null
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
        val reusedThroughEndIndex = (graph as? IncrementalGraph)?.reusedThroughEndIndex ?: -1
        if (endIndex <= reusedThroughEndIndex) return
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
        beamWidth: Int = 20,
        graphNodeDedupMode: GraphNodeDedupMode = GraphNodeDedupMode.EXISTING_BY_TANGO_L_R,
        mozcNodeAttributeTable: MozcNodeAttributeTable? = null,
        graphNodeTrace: MutableList<GraphNodeTrace>? = null,
        sessionState: SessionState? = null,
    ): MutableMap<Int, MutableList<Node>> {
        val performanceStartNs = if (sessionState?.performanceProbeEnabled == true) {
            System.nanoTime()
        } else {
            0L
        }
        if (str.isAllHalfWidthAscii()) {
            if (performanceStartNs != 0L) {
                sessionState?.lastConstructGraphNs = System.nanoTime() - performanceStartNs
            }
            return mutableMapOf()
        }
        fun mozcAttributesFor(leftId: Short): Int =
            mozcNodeAttributeTable?.attributesFor(leftId.toInt()) ?: MozcNodeAttributes.NONE

        val signature = conversionSignature(
            yomiTrie = yomiTrie,
            wikiYomiTrie = wikiYomiTrie,
            webYomiTrie = webYomiTrie,
            personYomiTrie = personYomiTrie,
            neologdYomiTrie = neologdYomiTrie,
            userDictionaryRepository = userDictionaryRepository,
            learnRepository = learnRepository,
            isOmissionSearchEnable = isOmissionSearchEnable,
            enableTypoCorrectionJapaneseFlick = enableTypoCorrectionJapaneseFlick,
            typoCorrectionOffsetScore = typoCorrectionOffsetScore,
            omissionSearchOffSetScore = omissionSearchOffSetScore,
            beamWidth = beamWidth,
            graphNodeDedupMode = graphNodeDedupMode,
            mozcNodeAttributeTable = mozcNodeAttributeTable,
        )
        val activeCache = if (sessionState != null) sessionState.cachedGraph else cachedGraph
        val reusable = activeCache?.takeIf {
            graphNodeTrace == null &&
                it.signature == signature &&
                (
                    sessionState != null && str.length > it.input.length ||
                        sessionState == null && str.length == it.input.length + 1
                ) &&
                str.startsWith(it.input) &&
                // Appending within an alphabet token changes every maximal-run node that starts
                // in that token.  The current append cache can only preserve a fully stable
                // prefix, so rebuild this transition instead of publishing a stale short token.
                str.substring((it.input.length - 1).coerceAtLeast(0))
                    .windowed(2)
                    .none { pair -> pair[0].isAsciiAlphabet() && pair[1].isAsciiAlphabet() }
        }
        if (sessionState?.performanceProbeEnabled == true) {
            sessionState.lastAppendReused = reusable != null
        }
        val reusablePrefixLength = reusable?.input?.length ?: -1
        val systemOmissionStates = LinkedHashMap<Int, List<LOUDSWithTermId.OmissionSearchState>>()
        val systemUserOmissionStates = LinkedHashMap<Int, List<LOUDSWithTermId.OmissionSearchState>>()
        val systemTypoProgress = LinkedHashMap<Int, LOUDSWithTermId.TypoSearchProgress>()
        val systemUserTypoProgress = LinkedHashMap<Int, LOUDSWithTermId.TypoSearchProgress>()
        val systemPrefixStates = LinkedHashMap<Int, Int>()
        val systemUserPrefixStates = LinkedHashMap<Int, Int>()
        val wikiPrefixStates = LinkedHashMap<Int, Int>()
        val webPrefixStates = LinkedHashMap<Int, Int>()
        val personPrefixStates = LinkedHashMap<Int, Int>()
        val neologdPrefixStates = LinkedHashMap<Int, Int>()

        fun prefixSearch(
            trie: LOUDSWithTermId,
            bitVector: SuccinctBitVector,
            start: Int,
            previousStates: Map<Int, Int>?,
            currentStates: MutableMap<Int, Int>,
        ): List<LOUDSWithTermId.CommonPrefixSearchResult> {
            val previousNodeIndex = previousStates?.get(start)
            val progress = if (
                sessionState != null &&
                reusable != null &&
                previousNodeIndex != null
            ) {
                var current = LOUDSWithTermId.CommonPrefixSearchProgress(
                    results = emptyList(),
                    nodeIndex = previousNodeIndex,
                )
                val newResults = ArrayList<LOUDSWithTermId.CommonPrefixSearchResult>()
                for (endIndex in reusable.input.length until str.length) {
                    current = trie.advanceCommonPrefixSearch(
                        previousNodeIndex = current.nodeIndex,
                        char = str[endIndex],
                        input = str.subSequence(0, endIndex + 1),
                        start = start,
                        succinctBitVector = bitVector,
                    )
                    newResults.addAll(current.results)
                    if (current.nodeIndex < 0) break
                }
                current.copy(results = newResults)
            } else {
                trie.commonPrefixSearchWithProgress(
                    str = str,
                    start = start,
                    succinctBitVector = bitVector,
                )
            }
            currentStates[start] = progress.nodeIndex
            return progress.results
        }

        fun typoSearch(
            trie: LOUDSWithTermId,
            bitVector: SuccinctBitVector,
            start: Int,
            previous: LOUDSWithTermId.TypoSearchProgress?,
        ): LOUDSWithTermId.TypoSearchProgress {
            if (sessionState == null || reusable == null || previous == null) {
                return trie.commonPrefixSearchWithTypoCorrectionProgress(
                    str = str,
                    startIndex = start,
                    succinctBitVector = bitVector,
                    maxResults = 98,
                    maxLen = 12,
                )
            }
            var current: LOUDSWithTermId.TypoSearchProgress = checkNotNull(previous)
            val newResults = ArrayList<TypoCorrectionResult>()
            for (index in reusable.input.length until str.length) {
                current = trie.advanceTypoCorrectionSearch(
                    previous = current,
                    char = str[index],
                    succinctBitVector = bitVector,
                    maxResults = 98,
                    maxLen = 12,
                )
                newResults.addAll(current.results)
                if (current.terminalStates.isEmpty()) break
            }
            return current.copy(results = newResults)
        }

        fun omissionSearch(
            trie: LOUDSWithTermId,
            bitVector: SuccinctBitVector,
            start: Int,
            previous: List<LOUDSWithTermId.OmissionSearchState>?,
        ): LOUDSWithTermId.OmissionSearchProgress {
            if (sessionState == null || reusable == null) {
                return trie.commonPrefixSearchWithOmissionProgress(
                    str = str,
                    startIndex = start,
                    succinctBitVector = bitVector,
                )
            }
            if (previous == null) {
                // Preserve the former one-character append semantics exactly.  A larger jump is
                // the cancellation catch-up path; build the missing frontier from its true start
                // so no matches in the skipped characters are lost.
                return if (str.length == reusable.input.length + 1) {
                    trie.advanceOmissionSearch(
                        states = listOf(LOUDSWithTermId.OmissionSearchState(0, false)),
                        char = str.last(),
                        succinctBitVector = bitVector,
                    )
                } else {
                    trie.commonPrefixSearchWithOmissionProgress(
                        str = str,
                        startIndex = start,
                        succinctBitVector = bitVector,
                    )
                }
            }
            var states: List<LOUDSWithTermId.OmissionSearchState> = previous
            val newResults = ArrayList<OmissionSearchResult>()
            for (index in reusable.input.length until str.length) {
                val current = trie.advanceOmissionSearch(
                    states = states,
                    char = str[index],
                    succinctBitVector = bitVector,
                )
                states = current.terminalStates
                newResults.addAll(current.results)
                if (states.isEmpty()) break
            }
            return LOUDSWithTermId.OmissionSearchProgress(newResults, states)
        }
        val graph: MutableMap<Int, MutableList<Node>> = if (reusable != null) {
            val sessionGraph = reusable.graph as? IncrementalGraph
            if (sessionState != null && sessionGraph != null) {
                // A session owns this graph exclusively.  Keep the already-pruned lattice and
                // extend it in place instead of cloning every node twice for every typed letter.
                // Clear the published cache first so cancellation cannot expose a partial append.
                sessionState.registerInPlaceAppend(
                    graph = sessionGraph,
                    previousReusedThroughEndIndex = sessionGraph.reusedThroughEndIndex,
                    previousForwardDpReusableThroughEndIndex =
                        sessionGraph.forwardDpReusableThroughEndIndex,
                    previousInputLength = reusablePrefixLength,
                    positionsToRestore = reusable.unprunedPositions?.keys.orEmpty(),
                    previousEosIndex = reusablePrefixLength + 1,
                    previousEos = sessionGraph[reusablePrefixLength + 1],
                )
                sessionState.cachedGraph = null
                sessionGraph.reusedThroughEndIndex = reusablePrefixLength
                sessionGraph.forwardDpReusableThroughEndIndex =
                    if (reusable.unprunedPositions.isNullOrEmpty()) reusablePrefixLength else -1
                sessionGraph.remove(reusablePrefixLength + 1) // previous EOS
                // Beam pruning is query-local. With correction and optional dictionaries, nodes
                // discarded on an earlier prefix can still participate in a later K-best path.
                // Restore the node lists while retaining the node objects and search frontiers.
                reusable.unprunedPositions?.forEach { (position, nodes) ->
                    sessionGraph[position] = nodes
                }
                sessionGraph
            } else {
                IncrementalGraph(reusablePrefixLength, signature).apply {
                    reusable.graph.forEach { (endIndex, nodes) ->
                        if (endIndex != reusablePrefixLength + 1) {
                            put(endIndex, nodes.mapTo(mutableListOf()) { it.copyForGraphBuild() })
                        }
                    }
                }
            }
        } else {
            IncrementalGraph(-1, signature).apply { put(0, mutableListOf(BOS)) }
        }
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
            currentCoroutineContext().ensureActive()
            var subStrCache: String? = null
            fun subStr(): String {
                val cached = subStrCache
                if (cached != null) return cached
                return str.substring(i).also { subStrCache = it }
            }
            var foundInAnyDictionary = false

            // 1. ユーザー辞書
            val userWords = userDictionaryRepository?.let { repository ->
                if (reusable != null && str.length == reusable.input.length + 1) {
                    repository.exactMatchesForConversion(subStr())
                } else {
                    repository.commonPrefixSearchInUserDict(subStr())
                }
            } ?: emptyList()
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
                    candidateSource = CandidateSource.USER_DICTIONARY,
                )
                addOrUpdateNode(graph, endIndex, node, graphNodeDedupMode, graphNodeTrace, str, "USER")
            }

            // 2. 学習辞書
            val learnedWords = learnRepository?.let { repository ->
                if (reusable != null && str.length == reusable.input.length + 1) {
                    repository.findExactMatchesForConversion(subStr())
                } else {
                    repository.findCommonPrefixes(subStr())
                }
            } ?: emptyList()
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
                    candidateSource = CandidateSource.LEARNED_DICTIONARY,
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
                val systemUserPrefixSearch = prefixSearch(
                    trie = localSystemUserYomiTrie,
                    bitVector = localSystemUserLBSYomi,
                    start = i,
                    previousStates = reusable?.systemUserPrefixStates,
                    currentStates = systemUserPrefixStates,
                )
                if (systemUserPrefixSearch.isNotEmpty()) foundInAnyDictionary = true
                for (prefixResult in systemUserPrefixSearch) {
                    val yomiStr = prefixResult.yomi
                    val nodeIndex = prefixResult.nodeIndex
                    if (nodeIndex <= 0) continue
                    val termId = localSystemUserYomiTrie.getTermId(nodeIndex, localSystemUserIsLeaf)
                    val endIndex = i + yomiStr.length
                    if (endIndex <= reusablePrefixLength) continue
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
                    val typoProgress = typoSearch(
                        trie = localSystemUserYomiTrie,
                        bitVector = localSystemUserLBSYomi,
                        start = i,
                        previous = reusable?.systemUserTypoProgress?.get(i),
                    )
                    systemUserTypoProgress[i] = typoProgress
                    val typoPrefixResults = typoProgress.results
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
                        if (endIndex <= reusablePrefixLength) continue
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
                    val omissionProgress = omissionSearch(
                        trie = localSystemUserYomiTrie,
                        bitVector = localSystemUserLBSYomi,
                        start = i,
                        previous = reusable?.systemUserOmissionStates?.get(i),
                    )
                    systemUserOmissionStates[i] = omissionProgress.terminalStates
                    val omissionSearchResults = omissionProgress.results
                    if (omissionSearchResults.isNotEmpty()) foundInAnyDictionary = true

                    for (omissionResult in omissionSearchResults) {
                        val yomiStr = omissionResult.yomi
                        val didOmit = omissionResult.omissionOccurred
                        if (!didOmit) continue
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
                        if (endIndex <= reusablePrefixLength) continue

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
            val commonPrefixSearchSystem = prefixSearch(
                trie = yomiTrie,
                bitVector = succinctBitVectorLBSYomi,
                start = i,
                previousStates = reusable?.systemPrefixStates,
                currentStates = systemPrefixStates,
            )
            if (commonPrefixSearchSystem.isNotEmpty()) foundInAnyDictionary = true
            for (prefixResult in commonPrefixSearchSystem) {
                val yomiStr = prefixResult.yomi
                val nodeIndex = prefixResult.nodeIndex
                if (nodeIndex > 0) {
                    val termId = yomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafYomi)
                    val endIndex = i + yomiStr.length
                    if (endIndex <= reusablePrefixLength) continue
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
                val typoProgress = typoSearch(
                    trie = yomiTrie,
                    bitVector = succinctBitVectorLBSYomi,
                    start = i,
                    previous = reusable?.systemTypoProgress?.get(i),
                )
                systemTypoProgress[i] = typoProgress
                val typoPrefixResults = typoProgress.results

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
                    if (endIndex <= reusablePrefixLength) continue
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
                val omissionProgress = omissionSearch(
                    trie = yomiTrie,
                    bitVector = succinctBitVectorLBSYomi,
                    start = i,
                    previous = reusable?.systemOmissionStates?.get(i),
                )
                systemOmissionStates[i] = omissionProgress.terminalStates
                val omissionSearchResults: List<OmissionSearchResult> = omissionProgress.results
                if (omissionSearchResults.isNotEmpty()) foundInAnyDictionary = true
                for (omissionResult in omissionSearchResults) {
                    val yomiStr = omissionResult.yomi
                    val didOmit = omissionResult.omissionOccurred
                    if (!didOmit) continue
                    val nodeIndex = yomiTrie.getNodeIndex(yomiStr, succinctBitVectorLBSYomi)
                    if (nodeIndex > 0) {
                        val termId = yomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafYomi)
                        val listToken = tokenArray.getListDictionaryByYomiTermId(
                            termId,
                            succinctBitVectorTokenArray
                        )
                        val endIndex = i + yomiStr.length
                        if (endIndex <= reusablePrefixLength) continue
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
                val commonPrefixSearchWiki = prefixSearch(
                    trie = wikiYomiTrie,
                    bitVector = succinctBitVectorLBSWikiYomi,
                    start = i,
                    previousStates = reusable?.wikiPrefixStates,
                    currentStates = wikiPrefixStates,
                )
                if (commonPrefixSearchWiki.isNotEmpty()) foundInAnyDictionary = true
                for (prefixResult in commonPrefixSearchWiki) {
                    val yomiStr = prefixResult.yomi
                    val nodeIndex = prefixResult.nodeIndex
                    if (nodeIndex > 0) {
                        val termId =
                            wikiYomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafWikiYomi)
                        val endIndex = i + yomiStr.length
                        if (endIndex <= reusablePrefixLength) continue
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
                val commonPrefixSearchWeb = prefixSearch(
                    trie = webYomiTrie,
                    bitVector = succinctBitVectorLBSwebYomi,
                    start = i,
                    previousStates = reusable?.webPrefixStates,
                    currentStates = webPrefixStates,
                )
                if (commonPrefixSearchWeb.isNotEmpty()) foundInAnyDictionary = true
                for (prefixResult in commonPrefixSearchWeb) {
                    val yomiStr = prefixResult.yomi
                    val nodeIndex = prefixResult.nodeIndex
                    if (nodeIndex > 0) {
                        val termId =
                            webYomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafwebYomi)
                        val endIndex = i + yomiStr.length
                        if (endIndex <= reusablePrefixLength) continue
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
                val commonPrefixSearchPerson = prefixSearch(
                    trie = personYomiTrie,
                    bitVector = succinctBitVectorLBSpersonYomi,
                    start = i,
                    previousStates = reusable?.personPrefixStates,
                    currentStates = personPrefixStates,
                )
                if (commonPrefixSearchPerson.isNotEmpty()) foundInAnyDictionary = true
                for (prefixResult in commonPrefixSearchPerson) {
                    val yomiStr = prefixResult.yomi
                    val nodeIndex = prefixResult.nodeIndex
                    if (nodeIndex > 0) {
                        val termId =
                            personYomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafpersonYomi)
                        val endIndex = i + yomiStr.length
                        if (endIndex <= reusablePrefixLength) continue
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
                val commonPrefixSearchNeologd = prefixSearch(
                    trie = neologdYomiTrie,
                    bitVector = succinctBitVectorLBSneologdYomi,
                    start = i,
                    previousStates = reusable?.neologdPrefixStates,
                    currentStates = neologdPrefixStates,
                )
                if (commonPrefixSearchNeologd.isNotEmpty()) foundInAnyDictionary = true
                for (prefixResult in commonPrefixSearchNeologd) {
                    val yomiStr = prefixResult.yomi
                    val nodeIndex = prefixResult.nodeIndex
                    if (nodeIndex > 0) {
                        val termId =
                            neologdYomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafneologdYomi)
                        val endIndex = i + yomiStr.length
                        if (endIndex <= reusablePrefixLength) continue
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

            // An append can complete a dictionary word that did not exist in the previous
            // prefix. Remove the now-invalid unknown fallback retained by the incremental graph.
            if (foundInAnyDictionary && reusablePrefixLength >= 0) {
                val fallbackEndIndex = i + 1
                graph[fallbackEndIndex]?.removeAll {
                    it.candidateSource == CandidateSource.UNKNOWN && it.sPos == i
                }
                if (graph[fallbackEndIndex]?.isEmpty() == true) {
                    graph.remove(fallbackEndIndex)
                }
            }

            // 9. Mozc-style alphabet group node / unknown fallback
            if (i < str.length) {
                val isAsciiAlphabet = str[i].isAsciiAlphabet()
                // Mozc's ImmutableConverter::AddCharacterTypeBasedNodes runs after every
                // dictionary lookup and adds a maximal same-script/form alphabet node in
                // parallel with dictionary nodes.  Our old fallback ran only when no dictionary
                // entry existed, so one-letter entries suppressed the `abc` group and the strict
                // boundary checker severed the path between letters.
                if (!isAsciiAlphabet && foundInAnyDictionary) continue
                val endExclusive = if (isAsciiAlphabet) {
                    var end = i + 1
                    while (end < str.length && str[end].isAsciiAlphabet()) end++
                    end
                } else {
                    i + 1
                }
                val yomiStr = str.substring(i, endExclusive)
                val endIndex = i + yomiStr.length
                if (endIndex <= reusablePrefixLength) continue
                val wordCost = if (isAsciiAlphabet) {
                    if (yomiStr.length > 1) MOZC_MAX_WORD_COST / 2 else MOZC_MAX_WORD_COST
                } else {
                    10000
                }
                val posId = if (isAsciiAlphabet) MOZC_UNKNOWN_POS_ID else 0
                val unknownNode = Node(
                    l = posId,
                    r = posId,
                    score = wordCost,
                    f = wordCost,
                    g = wordCost,
                    tango = yomiStr, // 読みをそのまま単語とする
                    yomiUsed = yomiStr,
                    len = yomiStr.length.toShort(),
                    sPos = i,
                    mozcAttributes = mozcAttributesFor(posId),
                    candidateSource = CandidateSource.UNKNOWN,
                )
                // 未知語は重複を考慮せずそのまま追加する
                graph.computeIfAbsent(endIndex) { mutableListOf() }.add(unknownNode)
                graphNodeTrace?.add(unknownNode.toTrace(str, endIndex, "UNKNOWN", "ADDED"))
            }
        }
        val updatedCache = CachedGraph(
            input = str,
            signature = signature,
            graph = if (sessionState != null) graph else graph.deepCopyForGraphBuild(),
            systemOmissionStates = systemOmissionStates,
            systemUserOmissionStates = systemUserOmissionStates,
            systemTypoProgress = systemTypoProgress,
            systemUserTypoProgress = systemUserTypoProgress,
            systemPrefixStates = systemPrefixStates,
            systemUserPrefixStates = systemUserPrefixStates,
            wikiPrefixStates = wikiPrefixStates,
            webPrefixStates = webPrefixStates,
            personPrefixStates = personPrefixStates,
            neologdPrefixStates = neologdPrefixStates,
            unprunedPositions = if (sessionState != null) {
                graph.entries
                    .asSequence()
                    .filter { (endIndex, _) -> endIndex <= str.length }
                    .associateTo(LinkedHashMap()) { (endIndex, nodes) ->
                        endIndex to nodes.toMutableList()
                    }
            } else {
                null
            },
        )
        if (sessionState != null) {
            sessionState.cachedGraph = updatedCache
            if (performanceStartNs != 0L) {
                sessionState.lastConstructGraphNs = System.nanoTime() - performanceStartNs
            }
        } else {
            cachedGraph = updatedCache
        }
        return graph
    }

    private fun Node.copyForGraphBuild(): Node = copy(
        f = score,
        g = score,
        prev = null,
        next = null,
        adjustedScore = score,
    )

    private fun MutableMap<Int, MutableList<Node>>.deepCopyForGraphBuild(): MutableMap<Int, MutableList<Node>> =
        LinkedHashMap<Int, MutableList<Node>>(size).also { copy ->
            forEach { (endIndex, nodes) ->
                copy[endIndex] = nodes.mapTo(mutableListOf()) { it.copyForGraphBuild() }
            }
        }

    private fun Char.isAsciiAlphabet(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

    private fun conversionSignature(
        yomiTrie: LOUDSWithTermId,
        wikiYomiTrie: LOUDSWithTermId?,
        webYomiTrie: LOUDSWithTermId?,
        personYomiTrie: LOUDSWithTermId?,
        neologdYomiTrie: LOUDSWithTermId?,
        userDictionaryRepository: UserDictionaryRepository?,
        learnRepository: LearnRepository?,
        isOmissionSearchEnable: Boolean,
        enableTypoCorrectionJapaneseFlick: Boolean,
        typoCorrectionOffsetScore: Int,
        omissionSearchOffSetScore: Int,
        beamWidth: Int,
        graphNodeDedupMode: GraphNodeDedupMode,
        mozcNodeAttributeTable: MozcNodeAttributeTable?,
    ): Int {
        var result = System.identityHashCode(yomiTrie)
        result = 31 * result + System.identityHashCode(wikiYomiTrie)
        result = 31 * result + System.identityHashCode(webYomiTrie)
        result = 31 * result + System.identityHashCode(personYomiTrie)
        result = 31 * result + System.identityHashCode(neologdYomiTrie)
        result = 31 * result + System.identityHashCode(systemUserYomiTrie)
        result = 31 * result + System.identityHashCode(userDictionaryRepository)
        result = 31 * result + (userDictionaryRepository?.conversionRevision?.hashCode() ?: 0)
        result = 31 * result + System.identityHashCode(learnRepository)
        result = 31 * result + (learnRepository?.conversionRevision?.hashCode() ?: 0)
        result = 31 * result + isOmissionSearchEnable.hashCode()
        result = 31 * result + enableTypoCorrectionJapaneseFlick.hashCode()
        result = 31 * result + typoCorrectionOffsetScore
        result = 31 * result + omissionSearchOffSetScore
        result = 31 * result + beamWidth
        result = 31 * result + graphNodeDedupMode.hashCode()
        result = 31 * result + System.identityHashCode(mozcNodeAttributeTable)
        return result
    }

    private companion object {
        // app/src/main/assets/id.def: 名詞,サ変接続,*,*,*,*,*
        const val MOZC_UNKNOWN_POS_ID: Short = 1841
        const val MOZC_MAX_WORD_COST = 32767
    }
}
