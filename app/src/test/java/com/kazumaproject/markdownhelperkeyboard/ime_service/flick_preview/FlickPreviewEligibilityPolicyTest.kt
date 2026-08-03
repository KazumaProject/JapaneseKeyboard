package com.kazumaproject.markdownhelperkeyboard.ime_service.flick_preview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlickPreviewEligibilityPolicyTest {
    @Test
    fun eligibleContextIsAccepted() {
        assertTrue(FlickPreviewEligibilityPolicy.isEligible(context()))
    }

    @Test
    fun settingSurfaceDirectAndUnsafeEditorAreRejected() {
        assertFalse(FlickPreviewEligibilityPolicy.isEligible(context(settingEnabled = false)))
        assertFalse(FlickPreviewEligibilityPolicy.isEligible(context(surfaceEligible = false)))
        assertFalse(
            FlickPreviewEligibilityPolicy.isEligible(
                context(inputBehaviorUsesComposingText = false)
            )
        )
        assertFalse(FlickPreviewEligibilityPolicy.isEligible(context(safeInputType = false)))
    }

    @Test
    fun conversionSelectionCursorAndTailStatesAreRejected() {
        assertFalse(FlickPreviewEligibilityPolicy.isEligible(context(isHenkan = true)))
        assertFalse(FlickPreviewEligibilityPolicy.isEligible(context(selectMode = true)))
        assertFalse(FlickPreviewEligibilityPolicy.isEligible(context(cursorMoveMode = true)))
        assertFalse(FlickPreviewEligibilityPolicy.isEligible(context(hasComposingTail = true)))
    }

    private fun context(
        settingEnabled: Boolean = true,
        surfaceEligible: Boolean = true,
        inputBehaviorUsesComposingText: Boolean = true,
        safeInputType: Boolean = true,
        isHenkan: Boolean = false,
        selectMode: Boolean = false,
        cursorMoveMode: Boolean = false,
        hasComposingTail: Boolean = false,
    ) = FlickPreviewContext(
        source = FlickPreviewSource.TENKEY,
        editorSessionId = 1L,
        settingEnabled = settingEnabled,
        surfaceEligible = surfaceEligible,
        inputBehaviorUsesComposingText = inputBehaviorUsesComposingText,
        safeInputType = safeInputType,
        isHenkan = isHenkan,
        selectMode = selectMode,
        cursorMoveMode = cursorMoveMode,
        hasComposingTail = hasComposingTail,
        hasInputConnection = true,
        baseInput = "",
        isFlickOnlyMode = false,
        isContinuousTapInputEnabled = false,
        lastFlickConvertedNextHiragana = false,
    )
}
