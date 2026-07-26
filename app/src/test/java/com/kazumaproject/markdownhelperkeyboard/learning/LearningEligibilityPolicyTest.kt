package com.kazumaproject.markdownhelperkeyboard.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningEligibilityPolicyTest {
    @Test
    fun mixedJapaneseCanBeEnabledWithoutLearningPureSymbols() {
        assertTrue(
            LearningEligibilityPolicy.isEligible("れいわはちねん", "令和8年", true)
        )
        assertFalse(
            LearningEligibilityPolicy.isEligible("れいわはちねん", "令和8年", false)
        )
        assertFalse(LearningEligibilityPolicy.isEligible("いちにさん", "123", true))
        assertFalse(LearningEligibilityPolicy.isEligible("かお", "(^^)", true))
    }
}
