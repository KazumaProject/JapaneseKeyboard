package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteLongPressConversionBehaviorTest {
    @Test
    fun deferred_isDefaultAndSuspendsCandidateResults() {
        assertEquals(
            DeleteLongPressConversionBehavior.Deferred,
            DeleteLongPressConversionBehavior.fromPreferenceValue(null),
        )
        assertEquals(
            DeleteLongPressConversionBehavior.Deferred,
            DeleteLongPressConversionBehavior.fromPreferenceValue("unknown"),
        )
        assertTrue(
            DeleteLongPressConversionBehavior.Deferred.suspendsCandidateResultsDuringRepeat
        )
    }

    @Test
    fun continuous_restoresPerDeleteCandidateConversion() {
        assertEquals(
            DeleteLongPressConversionBehavior.Continuous,
            DeleteLongPressConversionBehavior.fromPreferenceValue("continuous"),
        )
        assertFalse(
            DeleteLongPressConversionBehavior.Continuous.suspendsCandidateResultsDuringRepeat
        )
    }
}
