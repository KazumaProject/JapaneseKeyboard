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

    companion object {
        private const val SCORE_BONUS_PER_OMISSION = 250
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
                    l = learnedWord.leftId ?: 1851,
                    r = learnedWord.rightId ?: 1851,
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
            if (isOmissionSearchEnable) {
                val commonPrefixSearchSystem: List<OmissionSearchResult> =
                    yomiTrie.commonPrefixSearchWithOmission(
                        str = subStr,
                        succinctBitVector = succinctBitVectorLBSYomi
                    )
                if (commonPrefixSearchSystem.isNotEmpty()) foundInAnyDictionary = true

                for (omissionResult in commonPrefixSearchSystem) {
                    val nodeIndex = yomiTrie.getNodeIndex(
                        omissionResult.yomi,
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
                                score = when {
                                    omissionResult.omissionCount > 0 && omissionResult.yomi.length == 1 ->
                                        it.wordCost + 1500

                                    omissionResult.omissionCount > 0 ->
                                        it.wordCost + (SCORE_BONUS_PER_OMISSION * omissionResult.omissionCount)

                                    else ->
                                        (it.wordCost - 100).coerceAtLeast(0)
                                },
                                f = it.wordCost.toInt(),
                                g = it.wordCost.toInt(),
                                tango = when (it.nodeId) {
                                    -2 -> omissionResult.yomi
                                    -1 -> omissionResult.yomi.hiraToKata()
                                    else -> tangoTrie.getLetter(
                                        it.nodeId,
                                        succinctBitVector = succinctBitVectorTangoLBS
                                    )
                                },
                                len = omissionResult.yomi.length.toShort(),
                                sPos = i,
                            )
                        }.filter { cand ->
                            ngWords.none { ng -> ng == cand.tango }
                        }
                        val endIndex = i + omissionResult.yomi.length
                        graph.computeIfAbsent(endIndex) { mutableListOf() }.addAll(tangoList)
                    }
                }
            } else {
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
                        }.filter { cand ->
                            ngWords.none { ng -> ng == cand.tango }
                        }
                        val endIndex = i + yomiStr.length
                        graph.computeIfAbsent(endIndex) { mutableListOf() }.addAll(tangoList)
                    }
                }
            }

            // 4. wiki辞書からCommon Prefix Searchを実行
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
                    val nodeIndex = wikiYomiTrie.getNodeIndex(
                        yomiStr,
                        succinctBitVectorLBSWikiYomi,
                    )
                    if (nodeIndex > 0) { // ルートノードは除く
                        val termId =
                            wikiYomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafWikiYomi)
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
                                        succinctBitVector = succinctBitVectorWikiTangoLBS
                                    )
                                },
                                len = yomiStr.length.toShort(),
                                sPos = i,
                            )
                        }.filter { cand ->
                            ngWords.none { ng -> ng == cand.tango }
                        }
                        val endIndex = i + yomiStr.length
                        graph.computeIfAbsent(endIndex) { mutableListOf() }.addAll(tangoList)
                    }
                }
            }

            if (webYomiTrie != null && webTangoTrie != null && webTokenArray != null &&
                succinctBitVectorLBSwebYomi != null && succinctBitVectorwebTangoLBS != null &&
                succinctBitVectorwebTokenArray != null && succinctBitVectorIsLeafwebYomi != null
            ) {
                val commonPrefixSearchweb: List<String> = webYomiTrie.commonPrefixSearch(
                    str = subStr,
                    succinctBitVector = succinctBitVectorLBSwebYomi
                )
                if (commonPrefixSearchweb.isNotEmpty()) foundInAnyDictionary = true

                for (yomiStr in commonPrefixSearchweb) {
                    val nodeIndex = webYomiTrie.getNodeIndex(
                        yomiStr,
                        succinctBitVectorLBSwebYomi,
                    )
                    if (nodeIndex > 0) { // ルートノードは除く
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
                                        succinctBitVector = succinctBitVectorwebTangoLBS
                                    )
                                },
                                len = yomiStr.length.toShort(),
                                sPos = i,
                            )
                        }.filter { cand ->
                            ngWords.none { ng -> ng == cand.tango }
                        }
                        val endIndex = i + yomiStr.length
                        graph.computeIfAbsent(endIndex) { mutableListOf() }.addAll(tangoList)
                    }
                }
            }

            if (personYomiTrie != null && personTangoTrie != null && personTokenArray != null &&
                succinctBitVectorLBSpersonYomi != null && succinctBitVectorpersonTangoLBS != null &&
                succinctBitVectorpersonTokenArray != null && succinctBitVectorIsLeafpersonYomi != null
            ) {
                val commonPrefixSearchperson: List<String> = personYomiTrie.commonPrefixSearch(
                    str = subStr,
                    succinctBitVector = succinctBitVectorLBSpersonYomi
                )
                if (commonPrefixSearchperson.isNotEmpty()) foundInAnyDictionary = true

                for (yomiStr in commonPrefixSearchperson) {
                    val nodeIndex = personYomiTrie.getNodeIndex(
                        yomiStr,
                        succinctBitVectorLBSpersonYomi,
                    )
                    if (nodeIndex > 0) { // ルートノードは除く
                        val termId =
                            personYomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafpersonYomi)
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
                                        succinctBitVector = succinctBitVectorpersonTangoLBS
                                    )
                                },
                                len = yomiStr.length.toShort(),
                                sPos = i,
                            )
                        }.filter { cand ->
                            ngWords.none { ng -> ng == cand.tango }
                        }
                        val endIndex = i + yomiStr.length
                        graph.computeIfAbsent(endIndex) { mutableListOf() }.addAll(tangoList)
                    }
                }
            }

            if (neologdYomiTrie != null && neologdTangoTrie != null && neologdTokenArray != null &&
                succinctBitVectorLBSneologdYomi != null && succinctBitVectorneologdTangoLBS != null &&
                succinctBitVectorneologdTokenArray != null && succinctBitVectorIsLeafneologdYomi != null
            ) {
                val commonPrefixSearchneologd: List<String> = neologdYomiTrie.commonPrefixSearch(
                    str = subStr,
                    succinctBitVector = succinctBitVectorLBSneologdYomi
                )
                if (commonPrefixSearchneologd.isNotEmpty()) foundInAnyDictionary = true

                for (yomiStr in commonPrefixSearchneologd) {
                    val nodeIndex = neologdYomiTrie.getNodeIndex(
                        yomiStr,
                        succinctBitVectorLBSneologdYomi,
                    )
                    if (nodeIndex > 0) { // ルートノードは除く
                        val termId =
                            neologdYomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafneologdYomi)
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
                                        succinctBitVector = succinctBitVectorneologdTangoLBS
                                    )
                                },
                                len = yomiStr.length.toShort(),
                                sPos = i,
                            )
                        }.filter { cand ->
                            ngWords.none { ng -> ng == cand.tango }
                        }
                        val endIndex = i + yomiStr.length
                        graph.computeIfAbsent(endIndex) { mutableListOf() }.addAll(tangoList)
                    }
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
