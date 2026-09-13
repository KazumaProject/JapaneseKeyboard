package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import org.junit.Assert.*
import org.junit.Test

class ComposingGuideEligibilityTest {
    @Test fun onlyOrdinaryActiveSoftwareInputIsEligible() {
        assertTrue(canShowComposingGuide(true, false, false, false, false, false, false))
        assertFalse(canShowComposingGuide(false, false, false, false, false, false, false))
        // Each exclusion is sufficient on its own, including hardware connected before mode changes.
        for (excluded in 0..5) {
            val flags = List(6) { it == excluded }
            assertFalse(canShowComposingGuide(true, flags[0], flags[1], flags[2], flags[3], flags[4], flags[5]))
        }
    }
}
