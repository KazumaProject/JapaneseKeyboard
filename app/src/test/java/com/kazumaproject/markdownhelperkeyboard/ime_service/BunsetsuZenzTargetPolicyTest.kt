package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BunsetsuZenzTargetPolicyTest {

    @Test
    fun buildBunsetsuZenzLeftContext_joinsDisplayTextBeforeFocusedSegment() {
        val leftContext = buildBunsetsuZenzLeftContext(
            segmentDisplayTexts = listOf("布で", "フルートを", "吹く"),
            focusedIndex = 2
        )

        assertEquals("布でフルートを", leftContext)
    }

    @Test
    fun isBunsetsuZenzTargetCurrent_matchesFocusedSegmentAndLeftContext() {
        val isCurrent = isBunsetsuZenzTargetCurrent(
            conversionInput = "ふのでふるーとをふく",
            segmentReadings = listOf("ふので", "ふるーとを", "ふく"),
            segmentDisplayTexts = listOf("布で", "フルートを", "吹く"),
            focusedIndex = 2,
            targetConversionInput = "ふのでふるーとをふく",
            targetSegmentIndex = 2,
            targetSegmentReading = "ふく",
            targetLeftContext = "布でフルートを"
        )

        assertTrue(isCurrent)
    }

    @Test
    fun isBunsetsuZenzTargetCurrent_rejectsDifferentFocusedSegment() {
        val isCurrent = isBunsetsuZenzTargetCurrent(
            conversionInput = "ふのでふるーとをふく",
            segmentReadings = listOf("ふので", "ふるーとを", "ふく"),
            segmentDisplayTexts = listOf("布で", "フルートを", "吹く"),
            focusedIndex = 1,
            targetConversionInput = "ふのでふるーとをふく",
            targetSegmentIndex = 2,
            targetSegmentReading = "ふく",
            targetLeftContext = "布でフルートを"
        )

        assertFalse(isCurrent)
    }

    @Test
    fun isBunsetsuZenzTargetCurrent_rejectsChangedLeftContext() {
        val isCurrent = isBunsetsuZenzTargetCurrent(
            conversionInput = "ふのでふるーとをふく",
            segmentReadings = listOf("ふので", "ふるーとを", "ふく"),
            segmentDisplayTexts = listOf("布ので", "フルートを", "吹く"),
            focusedIndex = 2,
            targetConversionInput = "ふのでふるーとをふく",
            targetSegmentIndex = 2,
            targetSegmentReading = "ふく",
            targetLeftContext = "布でフルートを"
        )

        assertFalse(isCurrent)
    }
}
