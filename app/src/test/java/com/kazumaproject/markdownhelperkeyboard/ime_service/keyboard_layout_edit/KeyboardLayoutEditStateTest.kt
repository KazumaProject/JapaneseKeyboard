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
        val normalized = KeyboardLayoutEditConstraints.normalizeNormalForCommit(
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
    fun draftWidthNearFullDoesNotSnapToOneHundredPercent() {
        assertEquals(98, KeyboardLayoutEditConstraints.normalizeWidthPercentForDraft(98))
        assertEquals(99, KeyboardLayoutEditConstraints.normalizeWidthPercentForDraft(99))
        assertEquals(100, KeyboardLayoutEditConstraints.normalizeWidthPercentForDraft(100))
        assertEquals(97, KeyboardLayoutEditConstraints.normalizeWidthPercentForDraft(97))
    }

    @Test
    fun commitWidthNearFullNormalizesToOneHundredPercent() {
        assertEquals(100, KeyboardLayoutEditConstraints.normalizeWidthPercentForCommit(98))
        assertEquals(100, KeyboardLayoutEditConstraints.normalizeWidthPercentForCommit(99))
        assertEquals(100, KeyboardLayoutEditConstraints.normalizeWidthPercentForCommit(100))
        assertEquals(97, KeyboardLayoutEditConstraints.normalizeWidthPercentForCommit(97))
    }

    @Test
    fun widthDraftAndCommitClampToMinAndMax() {
        assertEquals(32, KeyboardLayoutEditConstraints.normalizeWidthPercentForDraft(10))
        assertEquals(100, KeyboardLayoutEditConstraints.normalizeWidthPercentForDraft(140))
        assertEquals(32, KeyboardLayoutEditConstraints.normalizeWidthPercentForCommit(10))
        assertEquals(100, KeyboardLayoutEditConstraints.normalizeWidthPercentForCommit(140))
    }

    @Test
    fun floatingClampUsesExistingFloatingHeightRangeAndKeepsOnlySize() {
        val normalized = KeyboardLayoutEditConstraints.normalizeFloatingForCommit(
            KeyboardLayoutEditValues.Floating(
                heightDp = 40,
                widthPercent = 140,
            )
        )

        assertEquals(60, normalized.heightDp)
        assertEquals(100, normalized.widthPercent)
    }

    @Test
    fun floatingDraftNormalizesOnlyHeightAndWidth() {
        val normalized = KeyboardLayoutEditConstraints.normalizeFloatingForDraft(
            KeyboardLayoutEditValues.Floating(
                heightDp = 40,
                widthPercent = 98,
            )
        )

        assertEquals(60, normalized.heightDp)
        assertEquals(98, normalized.widthPercent)
    }

    @Test
    fun normalDraftPreservesMarginsAndPositionWhileNormalizingSizeAndMargins() {
        val normalized = KeyboardLayoutEditConstraints.normalizeNormalForDraft(
            KeyboardLayoutEditValues.Normal(
                heightDp = 999,
                widthPercent = 98,
                bottomMarginDp = 12,
                marginStartDp = 3,
                marginEndDp = 4,
                positionIsEnd = false,
            )
        )

        assertEquals(420, normalized.heightDp)
        assertEquals(98, normalized.widthPercent)
        assertEquals(12, normalized.bottomMarginDp)
        assertEquals(3, normalized.marginStartDp)
        assertEquals(4, normalized.marginEndDp)
        assertFalse(normalized.positionIsEnd)
    }

    @Test
    fun moveDragUpdatesEndMarginOppositeToHorizontalDrag() {
        val values = normalValues(positionIsEnd = true, marginEndDp = 20)

        assertEquals(
            10,
            calculateMoveDragValues(values, deltaXDp = 10f, deltaYDp = 0f, 100).marginEndDp,
        )
        assertEquals(
            30,
            calculateMoveDragValues(values, deltaXDp = -10f, deltaYDp = 0f, 100).marginEndDp,
        )
    }

    @Test
    fun moveDragUpdatesStartMarginWithHorizontalDrag() {
        val values = normalValues(positionIsEnd = false, marginStartDp = 20)

        assertEquals(
            30,
            calculateMoveDragValues(values, deltaXDp = 10f, deltaYDp = 0f, 100).marginStartDp,
        )
        assertEquals(
            10,
            calculateMoveDragValues(values, deltaXDp = -10f, deltaYDp = 0f, 100).marginStartDp,
        )
    }

    @Test
    fun moveDragClampsHorizontalMarginWhenNoAvailableSpace() {
        val endValues = normalValues(positionIsEnd = true, marginEndDp = 20)
        val startValues = normalValues(positionIsEnd = false, marginStartDp = 20)

        assertEquals(
            0,
            calculateMoveDragValues(endValues, deltaXDp = -10f, deltaYDp = 0f, 0).marginEndDp,
        )
        assertEquals(
            0,
            calculateMoveDragValues(startValues, deltaXDp = 10f, deltaYDp = 0f, 0).marginStartDp,
        )
    }

    @Test
    fun moveDragClampsHorizontalMarginWithinAvailableSpace() {
        val endValues = normalValues(positionIsEnd = true, marginEndDp = 20)
        val startValues = normalValues(positionIsEnd = false, marginStartDp = 20)

        assertEquals(
            50,
            calculateMoveDragValues(endValues, deltaXDp = -100f, deltaYDp = 0f, 50).marginEndDp,
        )
        assertEquals(
            50,
            calculateMoveDragValues(startValues, deltaXDp = 100f, deltaYDp = 0f, 50).marginStartDp,
        )
    }

    @Test
    fun moveDragUpdatesBottomMarginFromVerticalDrag() {
        val values = normalValues(bottomMarginDp = 20)

        assertEquals(
            10,
            calculateMoveDragValues(values, deltaXDp = 0f, deltaYDp = 10f, 100).bottomMarginDp,
        )
        assertEquals(
            30,
            calculateMoveDragValues(values, deltaXDp = 0f, deltaYDp = -10f, 100).bottomMarginDp,
        )
    }

    private fun normalValues(
        positionIsEnd: Boolean = true,
        bottomMarginDp: Int = 0,
        marginStartDp: Int = 0,
        marginEndDp: Int = 0,
    ): KeyboardLayoutEditValues.Normal {
        return KeyboardLayoutEditValues.Normal(
            heightDp = 220,
            widthPercent = 80,
            bottomMarginDp = bottomMarginDp,
            marginStartDp = marginStartDp,
            marginEndDp = marginEndDp,
            positionIsEnd = positionIsEnd,
        )
    }
}
