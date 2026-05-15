package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionAdapterThemeColorTest {

    @Test
    fun candidateItemColorSettersUpdateAdapterState() {
        val state = CandidateItemColorState()

        state.setBackgroundColor(0x11223344)
        state.setPressedBackgroundColor(0x55667788)

        assertEquals(0x11223344, state.backgroundColor)
        assertEquals(0x55667788, state.pressedBackgroundColor)
    }

    @Test
    fun candidateItemColorsSetterUpdatesBothStatesTogether() {
        val state = CandidateItemColorState()

        state.setColors(0x01020304, 0x05060708)

        assertEquals(0x01020304, state.backgroundColor)
        assertEquals(0x05060708, state.pressedBackgroundColor)
    }
}
