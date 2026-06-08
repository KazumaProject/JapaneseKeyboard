package com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit

import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutEditStateTest {

    @Test
    fun targetFromModeTreatsOnlyTenKeyQwertyModesAsQwertyFamily() {
        assertEquals(
            KeyboardLayoutEditTarget.QwertyFamily,
            KeyboardLayoutEditTarget.from(TenKeyQWERTYMode.TenKeyQWERTY),
        )
        assertEquals(
            KeyboardLayoutEditTarget.QwertyFamily,
            KeyboardLayoutEditTarget.from(TenKeyQWERTYMode.TenKeyQWERTYRomaji),
        )
        assertEquals(
            KeyboardLayoutEditTarget.TenKeyFamily,
            KeyboardLayoutEditTarget.from(TenKeyQWERTYMode.Default),
        )
        assertEquals(
            KeyboardLayoutEditTarget.TenKeyFamily,
            KeyboardLayoutEditTarget.from(TenKeyQWERTYMode.Number),
        )
    }

    @Test
    fun preferenceSlotSeparatesPortraitLandscapeAndTenkeyQwerty() {
        val slots = setOf(
            KeyboardLayoutEditPreferenceSlot(
                KeyboardLayoutEditOrientation.Portrait,
                KeyboardLayoutEditTarget.TenKeyFamily,
            ),
            KeyboardLayoutEditPreferenceSlot(
                KeyboardLayoutEditOrientation.Portrait,
                KeyboardLayoutEditTarget.QwertyFamily,
            ),
            KeyboardLayoutEditPreferenceSlot(
                KeyboardLayoutEditOrientation.Landscape,
                KeyboardLayoutEditTarget.TenKeyFamily,
            ),
            KeyboardLayoutEditPreferenceSlot(
                KeyboardLayoutEditOrientation.Landscape,
                KeyboardLayoutEditTarget.QwertyFamily,
            ),
        )

        assertEquals(4, slots.size)
    }

    @Test
    fun normalValuesContainMarginsAndPositionButFloatingValuesOnlyContainSize() {
        val normal = KeyboardLayoutEditValues.Normal(
            heightDp = 220,
            widthPercent = 100,
            bottomMarginDp = 12,
            marginStartDp = 3,
            marginEndDp = 4,
            positionIsEnd = true,
        )
        val floating = KeyboardLayoutEditValues.Floating(
            heightDp = 220,
            widthPercent = 100,
        )

        assertTrue(normal.positionIsEnd)
        assertEquals(12, normal.bottomMarginDp)
        assertEquals(3, normal.marginStartDp)
        assertEquals(4, normal.marginEndDp)
        assertEquals(220, floating.heightDp)
        assertEquals(100, floating.widthPercent)
    }

    @Test
    fun normalClampMatchesKeyboardSizeSettingsRange() {
        val normalized = KeyboardLayoutEditConstraints.normalizeNormal(
            KeyboardLayoutEditValues.Normal(
                heightDp = 999,
                widthPercent = 10,
                bottomMarginDp = -1,
                marginStartDp = -2,
                marginEndDp = -3,
                positionIsEnd = false,
            )
        )

        assertEquals(420, normalized.heightDp)
        assertEquals(32, normalized.widthPercent)
        assertEquals(0, normalized.bottomMarginDp)
        assertEquals(0, normalized.marginStartDp)
        assertEquals(0, normalized.marginEndDp)
        assertFalse(normalized.positionIsEnd)
    }

    @Test
    fun widthNearFullNormalizesToOneHundredPercent() {
        assertEquals(100, KeyboardLayoutEditConstraints.normalizeWidthPercent(98))
        assertEquals(100, KeyboardLayoutEditConstraints.normalizeWidthPercent(99))
        assertEquals(100, KeyboardLayoutEditConstraints.normalizeWidthPercent(100))
        assertEquals(97, KeyboardLayoutEditConstraints.normalizeWidthPercent(97))
    }

    @Test
    fun floatingClampUsesExistingFloatingHeightRangeAndKeepsOnlySize() {
        val normalized = KeyboardLayoutEditConstraints.normalizeFloating(
            KeyboardLayoutEditValues.Floating(
                heightDp = 40,
                widthPercent = 140,
            )
        )

        assertEquals(60, normalized.heightDp)
        assertEquals(100, normalized.widthPercent)
    }
}
