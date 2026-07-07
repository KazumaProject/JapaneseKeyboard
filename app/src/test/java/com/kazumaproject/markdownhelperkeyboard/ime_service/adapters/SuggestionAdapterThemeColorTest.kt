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

    @Test
    fun candidateEmptyPopupColorsPreferDedicatedCustomColors() {
        val colors = resolveCandidateEmptyPopupThemeColors(
            popupBackgroundColor = 0x11111111,
            popupTextColor = 0x22222222,
            specialKeyColor = 0x33333333,
            specialKeyTextColor = 0x44444444,
            defaultBackgroundColor = 0x55555555,
            defaultTextColor = 0x66666666,
        )

        assertEquals(0x11111111, colors.backgroundColor)
        assertEquals(0x22222222, colors.textColor)
    }

    @Test
    fun candidateEmptyPopupColorsFallBackToSpecialKeyColors() {
        val colors = resolveCandidateEmptyPopupThemeColors(
            popupBackgroundColor = null,
            popupTextColor = null,
            specialKeyColor = 0x33333333,
            specialKeyTextColor = 0x44444444,
            defaultBackgroundColor = 0x55555555,
            defaultTextColor = 0x66666666,
        )

        assertEquals(0x33333333, colors.backgroundColor)
        assertEquals(0x44444444, colors.textColor)
    }

    @Test
    fun candidateEmptyPopupColorsFallBackToSafeDefaults() {
        val colors = resolveCandidateEmptyPopupThemeColors(
            popupBackgroundColor = null,
            popupTextColor = null,
            specialKeyColor = null,
            specialKeyTextColor = null,
            defaultBackgroundColor = 0x55555555,
            defaultTextColor = 0x66666666,
        )

        assertEquals(0x55555555, colors.backgroundColor)
        assertEquals(0x66666666, colors.textColor)
    }
}
