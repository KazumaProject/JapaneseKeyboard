package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.core.domain.extensions.hasNConsecutiveChars
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.graph.Node
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.Other.BOS
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.PosMapper
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class GraphBuilder {

    /**
     * Constructs a word lattice graph from the input string in parallel.
     *
     * This function parallelizes the process by treating the analysis starting from each
     * character index as an independent task. It uses Kotlin coroutines to run these
     * tasks concurrently, potentially speeding up the process significantly on multi-core
     * processors, especially for long input strings.
     *
     * @return A MutableMap representing the graph, where keys are end-character indices
     * and values are lists of possible word nodes ending at that index.
     */
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
        ngWords: List<String>,
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
        isOmissionSearchEnable: Boolean
    ): MutableMap<Int, MutableList<Node>> {
        // Final graph to be returned
        val resultGraph: MutableMap<Int, MutableList<Node>> = LinkedHashMap()
        // Assuming BOS is a predefined constant available in the scope
        resultGraph[0] = mutableListOf(BOS)
        resultGraph[str.length + 1] = mutableListOf(
            Node(
                l = 0, r = 0, score = 0, f = 0, g = 0,
                tango = "EOS", len = 0, sPos = str.length + 1
            )
        )

        coroutineScope {
            // 1. MAP: Launch an asynchronous task for each starting index 'i'
            val deferredNodeMaps: List<Deferred<Map<Int, List<Node>>>> = str.indices.map { i ->
                async(Dispatchers.Default) {
                    val localGraph: MutableMap<Int, MutableList<Node>> = mutableMapOf()
                    val subStr = str.substring(i)
                    var foundInAnyDictionary = false

                    // 1. User Dictionary Search
                    val userWords = userDictionaryRepository.commonPrefixSearchInUserDict(subStr)
                    if (userWords.isNotEmpty()) foundInAnyDictionary = true
                    userWords.forEach { userWord ->
                        val endIndex = i + userWord.reading.length
                        val contextId = PosMapper.getContextIdForPos(userWord.posIndex)
                        val node = Node(
                            l = contextId, r = contextId, score = userWord.posScore,
                            f = userWord.posScore, g = userWord.posScore, tango = userWord.word,
                            len = userWord.reading.length.toShort(), sPos = i
                        )
                        localGraph.computeIfAbsent(endIndex) { mutableListOf() }.add(node)
                    }

                    // 2. Learning Dictionary Search
                    val learnedWords = learnRepository?.findCommonPrefixes(subStr) ?: emptyList()
                    if (learnedWords.isNotEmpty()) foundInAnyDictionary = true
                    learnedWords.forEach { learnedWord ->
                        val endIndex = i + learnedWord.input.length
                        val node = Node(
                            l = learnedWord.leftId ?: 1851, r = learnedWord.rightId ?: 1851,
                            score = learnedWord.score.toInt(), f = learnedWord.score.toInt(),
                            g = learnedWord.score.toInt(), tango = learnedWord.out,
                            len = learnedWord.input.length.toShort(), sPos = i
                        )
                        localGraph.computeIfAbsent(endIndex) { mutableListOf() }.add(node)
                    }

                    // 3. System Dictionary Search
                    if (isOmissionSearchEnable && !subStr.hasNConsecutiveChars(4)) {
                        val commonPrefixSearchSystem: List<String> =
                            yomiTrie.commonPrefixSearchWithOmission(
                                subStr,
                                succinctBitVectorLBSYomi
                            )
                        if (commonPrefixSearchSystem.isNotEmpty()) foundInAnyDictionary = true
                        for (omissionResult in commonPrefixSearchSystem) {
                            val nodeIndex =
                                yomiTrie.getNodeIndex(omissionResult, succinctBitVectorLBSYomi)
                            if (nodeIndex > 0) {
                                val termId =
                                    yomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafYomi)
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
                                            -2 -> omissionResult
                                            -1 -> omissionResult.hiraToKata()
                                            else -> tangoTrie.getLetter(
                                                it.nodeId,
                                                succinctBitVectorTangoLBS
                                            )
                                        },
                                        len = omissionResult.length.toShort(),
                                        sPos = i
                                    )
                                }.filter { cand -> ngWords.none { ng -> ng == cand.tango } }
                                val endIndex = i + omissionResult.length
                                localGraph.computeIfAbsent(endIndex) { mutableListOf() }
                                    .addAll(tangoList)
                            }
                        }
                    } else {
                        val commonPrefixSearchSystem: List<String> =
                            yomiTrie.commonPrefixSearch(subStr, succinctBitVectorLBSYomi)
                        if (commonPrefixSearchSystem.isNotEmpty()) foundInAnyDictionary = true
                        for (yomiStr in commonPrefixSearchSystem) {
                            val nodeIndex = yomiTrie.getNodeIndex(yomiStr, succinctBitVectorLBSYomi)
                            if (nodeIndex > 0) {
                                val termId =
                                    yomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafYomi)
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
                                                succinctBitVectorTangoLBS
                                            )
                                        },
                                        len = yomiStr.length.toShort(),
                                        sPos = i
                                    )
                                }.filter { cand -> ngWords.none { ng -> ng == cand.tango } }
                                val endIndex = i + yomiStr.length
                                localGraph.computeIfAbsent(endIndex) { mutableListOf() }
                                    .addAll(tangoList)
                            }
                        }
                    }

                    // 4. Wiki Dictionary Search
                    if (wikiYomiTrie != null && wikiTangoTrie != null && wikiTokenArray != null && succinctBitVectorLBSWikiYomi != null && succinctBitVectorWikiTangoLBS != null && succinctBitVectorWikiTokenArray != null && succinctBitVectorIsLeafWikiYomi != null) {
                        val commonPrefixSearchWiki: List<String> =
                            wikiYomiTrie.commonPrefixSearch(subStr, succinctBitVectorLBSWikiYomi)
                        if (commonPrefixSearchWiki.isNotEmpty()) foundInAnyDictionary = true
                        for (yomiStr in commonPrefixSearchWiki) {
                            val nodeIndex =
                                wikiYomiTrie.getNodeIndex(yomiStr, succinctBitVectorLBSWikiYomi)
                            if (nodeIndex > 0) {
                                val termId = wikiYomiTrie.getTermId(
                                    nodeIndex,
                                    succinctBitVectorIsLeafWikiYomi
                                )
                                val listToken = wikiTokenArray.getListDictionaryByYomiTermId(
                                    termId,
                                    succinctBitVectorWikiTokenArray
                                )
                                val tangoList = listToken.map {
                                    Node(
                                        l = wikiTokenArray.leftIds[it.posTableIndex.toInt()],
                                        r = wikiTokenArray.rightIds[it.posTableIndex.toInt()],
                                        score = it.wordCost.toInt(),
                                        f = it.wordCost.toInt(),
                                        g = it.wordCost.toInt(),
                                        tango = when (it.nodeId) {
                                            -2 -> yomiStr
                                            -1 -> yomiStr.hiraToKata()
                                            else -> wikiTangoTrie.getLetter(
                                                it.nodeId,
                                                succinctBitVectorWikiTangoLBS
                                            )
                                        },
                                        len = yomiStr.length.toShort(),
                                        sPos = i
                                    )
                                }.filter { cand -> ngWords.none { ng -> ng == cand.tango } }
                                val endIndex = i + yomiStr.length
                                localGraph.computeIfAbsent(endIndex) { mutableListOf() }
                                    .addAll(tangoList)
                            }
                        }
                    }

                    // 5. Web Dictionary Search
                    if (webYomiTrie != null && webTangoTrie != null && webTokenArray != null && succinctBitVectorLBSwebYomi != null && succinctBitVectorwebTangoLBS != null && succinctBitVectorwebTokenArray != null && succinctBitVectorIsLeafwebYomi != null) {
                        val commonPrefixSearchWeb: List<String> =
                            webYomiTrie.commonPrefixSearch(subStr, succinctBitVectorLBSwebYomi)
                        if (commonPrefixSearchWeb.isNotEmpty()) foundInAnyDictionary = true
                        for (yomiStr in commonPrefixSearchWeb) {
                            val nodeIndex =
                                webYomiTrie.getNodeIndex(yomiStr, succinctBitVectorLBSwebYomi)
                            if (nodeIndex > 0) {
                                val termId =
                                    webYomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafwebYomi)
                                val listToken = webTokenArray.getListDictionaryByYomiTermId(
                                    termId,
                                    succinctBitVectorwebTokenArray
                                )
                                val tangoList = listToken.map {
                                    Node(
                                        l = webTokenArray.leftIds[it.posTableIndex.toInt()],
                                        r = webTokenArray.rightIds[it.posTableIndex.toInt()],
                                        score = it.wordCost.toInt(),
                                        f = it.wordCost.toInt(),
                                        g = it.wordCost.toInt(),
                                        tango = when (it.nodeId) {
                                            -2 -> yomiStr
                                            -1 -> yomiStr.hiraToKata()
                                            else -> webTangoTrie.getLetter(
                                                it.nodeId,
                                                succinctBitVectorwebTangoLBS
                                            )
                                        },
                                        len = yomiStr.length.toShort(),
                                        sPos = i
                                    )
                                }.filter { cand -> ngWords.none { ng -> ng == cand.tango } }
                                val endIndex = i + yomiStr.length
                                localGraph.computeIfAbsent(endIndex) { mutableListOf() }
                                    .addAll(tangoList)
                            }
                        }
                    }

                    // 6. Person Dictionary Search
                    if (personYomiTrie != null && personTangoTrie != null && personTokenArray != null && succinctBitVectorLBSpersonYomi != null && succinctBitVectorpersonTangoLBS != null && succinctBitVectorpersonTokenArray != null && succinctBitVectorIsLeafpersonYomi != null) {
                        val commonPrefixSearchPerson: List<String> =
                            personYomiTrie.commonPrefixSearch(
                                subStr,
                                succinctBitVectorLBSpersonYomi
                            )
                        if (commonPrefixSearchPerson.isNotEmpty()) foundInAnyDictionary = true
                        for (yomiStr in commonPrefixSearchPerson) {
                            val nodeIndex =
                                personYomiTrie.getNodeIndex(yomiStr, succinctBitVectorLBSpersonYomi)
                            if (nodeIndex > 0) {
                                val termId = personYomiTrie.getTermId(
                                    nodeIndex,
                                    succinctBitVectorIsLeafpersonYomi
                                )
                                val listToken = personTokenArray.getListDictionaryByYomiTermId(
                                    termId,
                                    succinctBitVectorpersonTokenArray
                                )
                                val tangoList = listToken.map {
                                    Node(
                                        l = personTokenArray.leftIds[it.posTableIndex.toInt()],
                                        r = personTokenArray.rightIds[it.posTableIndex.toInt()],
                                        score = it.wordCost.toInt(),
                                        f = it.wordCost.toInt(),
                                        g = it.wordCost.toInt(),
                                        tango = when (it.nodeId) {
                                            -2 -> yomiStr
                                            -1 -> yomiStr.hiraToKata()
                                            else -> personTangoTrie.getLetter(
                                                it.nodeId,
                                                succinctBitVectorpersonTangoLBS
                                            )
                                        },
                                        len = yomiStr.length.toShort(),
                                        sPos = i
                                    )
                                }.filter { cand -> ngWords.none { ng -> ng == cand.tango } }
                                val endIndex = i + yomiStr.length
                                localGraph.computeIfAbsent(endIndex) { mutableListOf() }
                                    .addAll(tangoList)
                            }
                        }
                    }

                    // 7. Neologd Dictionary Search
                    if (neologdYomiTrie != null && neologdTangoTrie != null && neologdTokenArray != null && succinctBitVectorLBSneologdYomi != null && succinctBitVectorneologdTangoLBS != null && succinctBitVectorneologdTokenArray != null && succinctBitVectorIsLeafneologdYomi != null) {
                        val commonPrefixSearchNeologd: List<String> =
                            neologdYomiTrie.commonPrefixSearch(
                                subStr,
                                succinctBitVectorLBSneologdYomi
                            )
                        if (commonPrefixSearchNeologd.isNotEmpty()) foundInAnyDictionary = true
                        for (yomiStr in commonPrefixSearchNeologd) {
                            val nodeIndex = neologdYomiTrie.getNodeIndex(
                                yomiStr,
                                succinctBitVectorLBSneologdYomi
                            )
                            if (nodeIndex > 0) {
                                val termId = neologdYomiTrie.getTermId(
                                    nodeIndex,
                                    succinctBitVectorIsLeafneologdYomi
                                )
                                val listToken = neologdTokenArray.getListDictionaryByYomiTermId(
                                    termId,
                                    succinctBitVectorneologdTokenArray
                                )
                                val tangoList = listToken.map {
                                    Node(
                                        l = neologdTokenArray.leftIds[it.posTableIndex.toInt()],
                                        r = neologdTokenArray.rightIds[it.posTableIndex.toInt()],
                                        score = it.wordCost.toInt(),
                                        f = it.wordCost.toInt(),
                                        g = it.wordCost.toInt(),
                                        tango = when (it.nodeId) {
                                            -2 -> yomiStr
                                            -1 -> yomiStr.hiraToKata()
                                            else -> neologdTangoTrie.getLetter(
                                                it.nodeId,
                                                succinctBitVectorneologdTangoLBS
                                            )
                                        },
                                        len = yomiStr.length.toShort(),
                                        sPos = i
                                    )
                                }.filter { cand -> ngWords.none { ng -> ng == cand.tango } }
                                val endIndex = i + yomiStr.length
                                localGraph.computeIfAbsent(endIndex) { mutableListOf() }
                                    .addAll(tangoList)
                            }
                        }
                    }

                    // 8. Unknown Word Fallback
                    if (!foundInAnyDictionary && subStr.isNotEmpty()) {
                        val yomiStr = subStr.substring(0, 1)
                        val endIndex = i + yomiStr.length
                        val unknownNode = Node(
                            l = 0, r = 0, score = 10000, f = 10000, g = 10000,
                            tango = yomiStr, len = yomiStr.length.toShort(), sPos = i
                        )
                        localGraph.computeIfAbsent(endIndex) { mutableListOf() }.add(unknownNode)
                    }

                    // Return the results calculated in this coroutine
                    localGraph
                }
            }

            // 2. REDUCE: Wait for all async tasks to complete and collect their results
            val allNodeMaps: List<Map<Int, List<Node>>> = deferredNodeMaps.awaitAll()

            // 3. MERGE: Combine the results from all local maps into the final resultGraph
            allNodeMaps.forEach { nodeMap ->
                nodeMap.forEach { (endIndex, nodes) ->
                    resultGraph.computeIfAbsent(endIndex) { mutableListOf() }.addAll(nodes)
                }
            }
        }

        return resultGraph
    }
}
