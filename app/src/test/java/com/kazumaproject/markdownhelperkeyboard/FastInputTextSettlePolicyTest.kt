package com.kazumaproject.markdownhelperkeyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastInputTextSettlePolicyTest {
    @Test
    fun stablePartialTextDoesNotCompleteExpectedTrace() {
        val policy = FastInputTextSettlePolicy(expectedText = "あい", stableSamplesRequired = 2)

        assertFalse(policy.observe("あ"))
        assertFalse(policy.observe("あ"))
        assertFalse(policy.observe("あ"))
        assertFalse(policy.observe("あ"))
    }

    @Test
    fun expectedTextCompletesOnlyAfterRequiredStableSamples() {
        val policy = FastInputTextSettlePolicy(expectedText = "あい", stableSamplesRequired = 3)

        assertFalse(policy.observe("あ"))
        assertFalse(policy.observe("あい"))
        assertFalse(policy.observe("あい"))
        assertTrue(policy.observe("あい"))
    }

    @Test
    fun lateExtraTextInvalidatesTheCurrentStableWindow() {
        val policy = FastInputTextSettlePolicy(expectedText = "あい", stableSamplesRequired = 3)

        assertFalse(policy.observe("あい"))
        assertFalse(policy.observe("あい"))
        assertFalse(policy.observe("あいう"))
        assertFalse(policy.observe("あいう"))
        assertFalse(policy.observe("あい"))
        assertFalse(policy.observe("あい"))
        assertTrue(policy.observe("あい"))
    }
}
