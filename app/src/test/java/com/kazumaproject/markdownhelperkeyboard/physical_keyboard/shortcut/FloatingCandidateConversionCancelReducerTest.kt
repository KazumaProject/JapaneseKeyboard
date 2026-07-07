package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut

import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FloatingCandidateConversionCancelReducerTest {
    @Test
    fun cancel_clearsFloatingConversionState() {
        val state = FloatingCandidateConversionState(
            inputString = "ここ",
            isHenkan = true,
            henkanPressedWithBunsetsuDetect = true,
            stringInTail = "では",
            currentHighlightIndex = 2,
            hasConversionSuggestions = true
        )

        val result = FloatingCandidateConversionCancelReducer.cancel(state, "ここでは")

        assertEquals("ここでは", result.inputString)
        assertFalse(result.isHenkan)
        assertFalse(result.henkanPressedWithBunsetsuDetect)
        assertEquals("", result.stringInTail)
        assertEquals(RecyclerView.NO_POSITION, result.currentHighlightIndex)
        assertFalse(result.hasConversionSuggestions)
    }
}
