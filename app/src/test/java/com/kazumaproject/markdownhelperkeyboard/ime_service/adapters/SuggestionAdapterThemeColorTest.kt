package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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
    fun classicCandidateItemsUseNoFillNoCornersAndKeepPressedFeedback() {
        val state = CandidateItemColorState()

        state.setColors(android.graphics.Color.TRANSPARENT, 0xffb6bec9.toInt(), 0f)

        assertEquals(android.graphics.Color.TRANSPARENT, state.backgroundColor)
        assertEquals(0xffb6bec9.toInt(), state.pressedBackgroundColor)
        assertEquals(0f, state.cornerRadiusDp)

        state.setColors(null, null)
        assertEquals(16f, state.cornerRadiusDp)
    }

    @Test
    fun classicCandidateDrawableIsClearAtRestAndFlatWhenPressed() {
        val drawable = createCandidateItemBackgroundDrawable(
            backgroundColor = android.graphics.Color.TRANSPARENT,
            pressedColor = 0xffb6bec9.toInt(),
            density = 2f,
            cornerRadiusDp = 0f,
        )

        drawable.state = intArrayOf()
        val normal = drawable.current as android.graphics.drawable.GradientDrawable
        assertEquals(android.graphics.Color.TRANSPARENT, requireNotNull(normal.color).defaultColor)
        assertEquals(0f, normal.cornerRadius)

        drawable.state = intArrayOf(android.R.attr.state_pressed)
        val pressed = drawable.current as android.graphics.drawable.GradientDrawable
        assertEquals(0xffb6bec9.toInt(), requireNotNull(pressed.color).defaultColor)
        assertEquals(0f, pressed.cornerRadius)
        assertTrue(drawable.isStateful)
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
