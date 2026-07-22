package com.kazumaproject.markdownhelperkeyboard.learning.database

object LearningScorePolicy {
    private const val REINFORCEMENT = 1500

    fun initial(candidateScore: Int, candidateIndex: Int): Int =
        (candidateScore - 500L * candidateIndex.coerceAtLeast(0))
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()

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
