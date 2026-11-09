package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

private data class PredictionToken(
    val dictionary: TripleDictionaryData,
    val reading: String,
    val posTableIndex: Short,
    val nodeId: Int,
    val score: Int,
)

internal fun predictSystemDictionaryCandidates(
    input: String,
    config: PredictionConfig,
    systemDictionary: TripleDictionaryData,
    systemUserDictionary: TripleDictionaryData?,
): List<Candidate> {
    val maxReadingLength = config.maxJapanesePredictionReadingLength(input.length)
        ?: return emptyList()
    val limit = config.normalizedSystemCandidateLimit
    val selected = ArrayList<PredictionToken>(limit)

    fun collect(dictionary: TripleDictionaryData) {
        dictionary.yomiTrie.forEachPredictiveMatch(
            prefix = input,
            maxReadingLength = maxReadingLength,
            lbsIndex = dictionary.succinctBitVectorLBSYomi,
            leafIndex = dictionary.succinctBitVectorIsLeafYomi,
        ) { reading, termId ->
            if (reading.length == input.length) return@forEachPredictiveMatch
            val penalty = config.completionPenalty(input.length, reading.length)
            dictionary.tokenArray.forEachDictionaryByYomiTermId(
                termId,
                dictionary.succinctBitVectorTokenArray,
            ) { pos, cost, nodeId ->
                val score = cost.toInt() + penalty
                if (selected.size == limit && score >= selected.last().score) {
                    return@forEachDictionaryByYomiTermId
                }
                // Insert after earlier ties to preserve trie order and the system/user dictionary order.
                var insertion = selected.size
                while (insertion > 0 && score < selected[insertion - 1].score) insertion--
                if (selected.size == limit) selected.removeAt(limit - 1)
                selected.add(insertion, PredictionToken(dictionary, reading, pos, nodeId, score))
            }
        }
    }

    if (config.systemDictionaryEnabled) collect(systemDictionary)
    if (config.systemUserDictionaryEnabled && systemUserDictionary != null) collect(systemUserDictionary)

    // Only the winners need a surface lookup or a Candidate.
    return selected.map { token ->
        val dictionary = token.dictionary
        val pos = token.posTableIndex.toInt()
        Candidate(
            string = when (token.nodeId) {
                -2 -> token.reading
                -1 -> token.reading.hiraToKata()
                else -> dictionary.tangoTrie.getLetter(token.nodeId, dictionary.succinctBitVectorTangoLBS)
            },
            type = 9,
            length = token.reading.length.toUByte(),
            score = token.score,
            leftId = dictionary.tokenArray.leftIds[pos],
            rightId = dictionary.tokenArray.rightIds[pos],
        )
    }
}
