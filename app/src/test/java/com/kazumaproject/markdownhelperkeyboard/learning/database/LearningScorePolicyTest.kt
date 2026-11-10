package com.kazumaproject.markdownhelperkeyboard.learning.database

import org.junit.Assert.assertEquals
import org.junit.Test

class LearningScorePolicyTest {
    @Test
    fun initialRankBonusIsCappedAndHandlesExtremeIndices() {
        assertEquals(10_000, LearningScorePolicy.initial(candidateScore = 10_000, candidateIndex = 0))
        assertEquals(9500, LearningScorePolicy.initial(candidateScore = 10_000, candidateIndex = 1))
        assertEquals(7000, LearningScorePolicy.initial(candidateScore = 10_000, candidateIndex = Int.MAX_VALUE))
        assertEquals(10_000, LearningScorePolicy.initial(candidateScore = 10_000, candidateIndex = -1))
        assertEquals(Int.MAX_VALUE, LearningScorePolicy.initial(candidateScore = Int.MAX_VALUE, candidateIndex = 0))
    }

    @Test
    fun reinforcementRewardsTheBestObservedScoreByFiveHundredWithoutOverflow() {
        assertEquals(2500, LearningScorePolicy.reinforce(existingScore = 3000, incomingScore = 3000))
        assertEquals(500, LearningScorePolicy.reinforce(existingScore = 9000, incomingScore = 1000))
        assertEquals(0, LearningScorePolicy.reinforce(existingScore = 400, incomingScore = 400))
        assertEquals(
            Int.MAX_VALUE - 500,
            LearningScorePolicy.reinforce(existingScore = Int.MAX_VALUE, incomingScore = Int.MAX_VALUE),
        )
    }

    @Test
    fun legacyZeroScoreKeepsExactPreferenceWithoutOverpoweringPartialWords() {
        assertEquals(0, LearningScorePolicy.conversionCost(0, 6082, isExactInput = true))
        assertEquals(5582, LearningScorePolicy.conversionCost(0, 6082, isExactInput = false))
        assertEquals(0, LearningScorePolicy.conversionCost(0, 1, isExactInput = false))
    }

    @Test
    fun missingDictionaryReferenceIsConservativeAndDoesNotLowerHighStoredCosts() {
        assertEquals(0, LearningScorePolicy.conversionCost(0, null, isExactInput = true))
        assertEquals(5500, LearningScorePolicy.conversionCost(0, null, isExactInput = false))
        assertEquals(9000, LearningScorePolicy.conversionCost(9000, null, isExactInput = false))
        assertEquals(Int.MAX_VALUE, LearningScorePolicy.conversionCost(Int.MAX_VALUE, null, isExactInput = false))
        assertEquals(0, LearningScorePolicy.initial(Int.MIN_VALUE, Int.MAX_VALUE))
    }
}
