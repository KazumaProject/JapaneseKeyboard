package com.kazumaproject.markdownhelperkeyboard.ime_service.autofill

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineAutofillGenerationTrackerTest {

    @Test
    fun startingAnotherInputSessionRejectsThePreviousFieldsResponse() {
        val tracker = InlineAutofillGenerationTracker()
        tracker.startInputSession()
        val fieldAResponse = tracker.beginResponse()

        tracker.startInputSession()

        assertFalse(tracker.isCurrent(fieldAResponse))
    }

    @Test
    fun beginningAnotherResponseRejectsAnOlderResponseInTheSameField() {
        val tracker = InlineAutofillGenerationTracker()
        tracker.startInputSession()
        val olderResponse = tracker.beginResponse()
        val currentResponse = tracker.beginResponse()

        assertFalse(tracker.isCurrent(olderResponse))
        assertTrue(tracker.isCurrent(currentResponse))
    }

    @Test
    fun clearingSuggestionsRejectsInflationThatFinishesLater() {
        val tracker = InlineAutofillGenerationTracker()
        tracker.startInputSession()
        val inflatingResponse = tracker.beginResponse()
        val clearedState = tracker.invalidateResponse()

        assertFalse(tracker.isCurrent(inflatingResponse))
        assertTrue(tracker.isCurrent(clearedState))
    }
}
