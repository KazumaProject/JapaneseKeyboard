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
    @Test
    fun numericLearningRequiresRecognizedReadingAndEnabledSetting() {
        for (output in listOf("100", "１００", "1,000")) {
            val input = if (output == "1,000") "せん" else "ひゃく"
            assertTrue(LearningEligibilityPolicy.isEligible(input, output, true))
            assertFalse(LearningEligibilityPolicy.isEligible(input, output, false))
        }
        assertFalse(LearningEligibilityPolicy.isEligible("ひゃく", "200", true))
        assertFalse(LearningEligibilityPolicy.isEligible("ひゃく", "💯", true))
        assertFalse(LearningEligibilityPolicy.isEligible("", "100", true))
    }
}
