package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyTextSizePreviewSizingTest {

    @Test
    fun previewHeightMatchesTheConfiguredKeyboardHeightWithoutCandidateStrip() {
        assertEquals(440, KeyTextSizePreviewSizing.heightPx(heightDp = 220, density = 2f))
    }

    @Test
    fun previewHeightUsesTheSameBoundsAsNormalKeyboardSizing() {
        assertEquals(200, KeyTextSizePreviewSizing.heightPx(heightDp = 60, density = 2f))
        assertEquals(840, KeyTextSizePreviewSizing.heightPx(heightDp = 500, density = 2f))
    }
}
