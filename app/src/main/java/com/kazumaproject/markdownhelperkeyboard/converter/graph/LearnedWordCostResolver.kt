package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.markdownhelperkeyboard.converter.engine.TripleDictionaryData
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.PosMapper
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord

/**
 * Finds the original word cost for a learned word without retaining dictionary state between
 * graph queries. References are keyed by reading, surface, and both effective context IDs.
 */
internal class LearnedWordCostResolver(
    private val dictionaries: List<TripleDictionaryData>,
) {
    private data class Key(
        val reading: String,
        val surface: String,
        val leftId: Short,
        val rightId: Short,
    )

    private val referenceCosts = HashMap<Key, Int?>()

    fun referenceCost(
        reading: String,
        surface: String,
        leftId: Short,
        rightId: Short,
        userWords: List<UserWord>,
    ): Int? {
        val key = Key(reading, surface, leftId, rightId)
        if (key in referenceCosts) return referenceCosts[key]

        var cost: Int? = null
        dictionaries.forEach { dictionary ->
            dictionaryWordCost(dictionary, key)?.let { dictionaryCost ->
                cost = cost?.let { minOf(it, dictionaryCost) } ?: dictionaryCost
            }
        }
        userWords.forEach { userWord ->
            val contextId = PosMapper.getContextIdForPos(userWord.posIndex)
            if (
                userWord.reading == reading &&
                userWord.word == surface &&
                contextId == leftId &&
                contextId == rightId
            ) {
                cost = cost?.let { minOf(it, userWord.posScore) } ?: userWord.posScore
            }
        }
        return cost.also { referenceCosts[key] = it }
    }

    private fun dictionaryWordCost(dictionary: TripleDictionaryData, key: Key): Int? {
        val nodeIndex = dictionary.yomiTrie.getNodeIndex(
            key.reading,
            dictionary.succinctBitVectorLBSYomi,
        )
        if (nodeIndex <= 0) return null

        val termId = dictionary.yomiTrie.getTermId(
            nodeIndex,
            dictionary.succinctBitVectorIsLeafYomi,
        )
        if (termId < 0) return null

        var cost: Int? = null
        dictionary.tokenArray.forEachDictionaryByYomiTermId(
            termId,
            dictionary.succinctBitVectorTokenArray,
        ) { posTableIndex, wordCost, tokenNodeId ->
            val index = posTableIndex.toInt()
            if (
                dictionary.tokenArray.leftIds[index] == key.leftId &&
                dictionary.tokenArray.rightIds[index] == key.rightId &&
                dictionary.surfaceFor(tokenNodeId, key.reading) == key.surface
            ) {
                val rawCost = wordCost.toInt()
                cost = cost?.let { minOf(it, rawCost) } ?: rawCost
            }
        }
        return cost
    }
}
