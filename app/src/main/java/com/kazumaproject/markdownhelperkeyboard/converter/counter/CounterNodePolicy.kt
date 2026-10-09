package com.kazumaproject.markdownhelperkeyboard.converter.counter

import com.kazumaproject.counter.CounterCandidate
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector

/** Context IDs are from the bundled Mozc id.def; a quantity is one atomic display node. */
internal object CounterNodePolicy {
    const val WORD_COST = 2000
    fun cost(candidateIndex: Int): Int = WORD_COST + candidateIndex * 200
    // FindPath adds 2,000 once to outputs containing digits. Compensate only rule-produced
    // numeric nodes so the explicit ASCII/kanji/fullwidth ordering survives sentence search.
    fun wordCost(candidateIndex: Int, candidate: CounterCandidate): Int =
        cost(candidateIndex) - if (candidate.notation != "kanji") 2000 else 0
    /** Keep a stronger lexical whole-reading entry (日本/午後/産後/いっぱい) ahead of a
     * newly interpreted quantity. Numeric dictionary entries such as 一本 are not competitors.
     * Sentence spans still use the ordinary lattice context, where 2本 can follow 鉛筆を. */
    fun wholeReadingLexicalPenalty(
        input: String,
        candidates: List<CounterCandidate>,
        yomiTrie: LOUDSWithTermId,
        tangoTrie: LOUDS,
        tokenArray: TokenArray,
        yomiLbs: SuccinctBitVector,
        yomiLeaf: SuccinctBitVector,
        tokenBits: SuccinctBitVector,
        tangoLbs: SuccinctBitVector,
    ): Int {
        if (candidates.isEmpty()) return 0
        val nodeIndex = yomiTrie.getNodeIndex(input, yomiLbs)
        if (nodeIndex <= 0) return 0
        val termId = yomiTrie.getTermId(nodeIndex, yomiLeaf)
        if (termId < 0) return 0
        val numericValues = candidates.mapTo(HashSet()) { it.value }
        var numericCost = Int.MAX_VALUE
        var lexicalCost = Int.MAX_VALUE
        tokenArray.forEachDictionaryByYomiTermId(termId, tokenBits) { _, cost, tokenNode ->
            val value = when (tokenNode) {
                -2 -> input
                -1 -> input.hiraToKata()
                else -> tangoTrie.getLetter(tokenNode, tangoLbs)
            }
            if (value in numericValues) numericCost = minOf(numericCost, cost.toInt())
            else lexicalCost = minOf(lexicalCost, cost.toInt())
        }
        return if (lexicalCost < numericCost) maxOf(0, lexicalCost + 8000) else 0
    }

    fun leftId(candidate: CounterCandidate): Short = if (candidate.counterId == "time") 1909 else 2043
    fun rightId(candidate: CounterCandidate): Short = when {
        candidate.counterId == "time" -> 1909
        candidate.value.endsWith("つ") -> 2012
        candidate.value.endsWith("周") -> 2013
        candidate.value.endsWith("回") -> 2014
        candidate.value.endsWith("時") -> 2015
        candidate.value.endsWith("月") -> 2016
        candidate.value.endsWith("次") -> 2017
        candidate.value.endsWith("階") -> 2018
        else -> 2011
    }
}
