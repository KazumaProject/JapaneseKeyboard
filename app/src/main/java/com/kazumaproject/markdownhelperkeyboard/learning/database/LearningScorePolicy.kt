package com.kazumaproject.markdownhelperkeyboard.learning.database

object LearningScorePolicy {
    private const val MAX_INITIAL_RANK_DISCOUNT = 3000L
    private const val INITIAL_RANK_DISCOUNT = 500L
    private const val MAX_PARTIAL_DISCOUNT = 500L
    private const val FALLBACK_WORD_COST = 6000L
    private const val REINFORCEMENT = 500L

    fun initial(candidateScore: Int, candidateIndex: Int): Int =
        (candidateScore.toLong() -
            minOf(MAX_INITIAL_RANK_DISCOUNT, INITIAL_RANK_DISCOUNT * candidateIndex.coerceAtLeast(0).toLong()))
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()

    /**
     * Applies the existing stored score at read time: exact whole-input matches keep their
     * learned preference, while partial matches cannot outrank their original dictionary cost
     * by more than [MAX_PARTIAL_DISCOUNT]. Legacy scores are adapted in memory and never written
     * back to learning history.
     */
    fun conversionCost(learnedScore: Int, dictionaryWordCost: Int?, isExactInput: Boolean): Int {
        val safeStoredScore = learnedScore.coerceAtLeast(0)
        if (isExactInput) return safeStoredScore

        val referenceCost = (dictionaryWordCost?.toLong() ?: FALLBACK_WORD_COST)
        val partialCostFloor = (referenceCost - MAX_PARTIAL_DISCOUNT).coerceAtLeast(0L)
        return maxOf(safeStoredScore.toLong(), partialCostFloor)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    fun phrase(fragmentScores: List<Int>): Int =
        fragmentScores.takeIf { it.isNotEmpty() }
            ?.map(Int::toLong)
            ?.average()
            ?.toLong()
            ?.coerceIn(0L, Int.MAX_VALUE.toLong())
            ?.toInt()
            ?: 3000

    fun reinforce(existingScore: Int, incomingScore: Int): Int =
        (minOf(existingScore, incomingScore).toLong() - REINFORCEMENT)
            .coerceAtLeast(0L)
            .toInt()
}
