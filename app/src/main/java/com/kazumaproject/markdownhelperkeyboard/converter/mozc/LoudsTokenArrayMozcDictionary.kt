package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector

class LoudsTokenArrayMozcDictionary(
    private val yomiTrie: LOUDSWithTermId,
    private val tangoTrie: LOUDS,
    private val tokenArray: TokenArray,
    private val succinctBitVectorLBSYomi: SuccinctBitVector,
    private val succinctBitVectorIsLeafYomi: SuccinctBitVector,
    private val succinctBitVectorTokenArray: SuccinctBitVector,
    private val succinctBitVectorTangoLBS: SuccinctBitVector,
    private val trace: MozcConverterTrace? = null,
) : MozcDictionary {

    override fun lookupPrefix(
        key: String,
        beginPos: Int,
        options: MozcConversionOptions,
        builder: MozcNodeListBuilder,
    ) {
        if (beginPos !in key.indices) return
        val subKey = key.substring(beginPos)
        val yomiList = yomiTrie.commonPrefixSearch(
            str = subKey,
            succinctBitVector = succinctBitVectorLBSYomi,
        )
        for (yomi in yomiList) {
            appendNodesForYomi(key = yomi, beginPos = beginPos, builder = builder)
        }
    }

    override fun lookupExact(
        key: String,
        beginPos: Int,
        options: MozcConversionOptions,
        builder: MozcNodeListBuilder,
    ) {
        if (beginPos !in 0..key.length) return
        val exactKey = key.substring(beginPos)
        appendNodesForYomi(key = exactKey, beginPos = beginPos, builder = builder)
    }

    override fun lookupPredictive(
        key: String,
        beginPos: Int,
        options: MozcConversionOptions,
        builder: MozcNodeListBuilder,
    ) {
        // The current LOUDSWithTermId runtime exposes prefix lookup for input
        // prefixes, but not descendant enumeration by term id for prediction.
        // Normal conversion never depends on this method.
    }

    override fun lookupReverse(
        value: String,
        options: MozcConversionOptions,
        builder: MozcNodeListBuilder,
    ) {
        // The bundled dictionary assets do not include a reverse value index.
        // Normal conversion never depends on this method.
    }

    private fun appendNodesForYomi(
        key: String,
        beginPos: Int,
        builder: MozcNodeListBuilder,
    ) {
        if (key.isEmpty()) return
        val nodeIndex = yomiTrie.getNodeIndex(key, succinctBitVectorLBSYomi)
        if (nodeIndex <= 0) return
        val termId = yomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafYomi)
        if (termId < 0) return
        val tokens = tokenArray.getListDictionaryByYomiTermId(
            termId,
            succinctBitVectorTokenArray,
        )
        for (token in tokens) {
            val posIndex = token.posTableIndex.toInt()
            if (posIndex !in tokenArray.leftIds.indices ||
                posIndex !in tokenArray.rightIds.indices
            ) {
                continue
            }
            val value = when (token.nodeId) {
                -2 -> key
                -1 -> key.hiraToKata()
                else -> tangoTrie.getLetter(
                    token.nodeId,
                    succinctBitVector = succinctBitVectorTangoLBS,
                )
            }
            builder.add(
                MozcNode().apply {
                    this.key = key
                    this.value = value
                    leftId = tokenArray.leftIds[posIndex]
                    rightId = tokenArray.rightIds[posIndex]
                    wordCost = token.wordCost.toInt()
                    this.beginPos = beginPos
                    endPos = beginPos + key.length
                    nodeType = MozcNodeType.NORMAL
                    attributes = MozcNodeAttribute.SYSTEM_DICTIONARY
                },
            )
            trace?.dictionaryNodeCount = (trace?.dictionaryNodeCount ?: 0) + 1
        }
    }
}
