package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomKeyboardShiftStateTest {
    @Test
    fun doubleTapSequence_promotesOneShotToLocked_thenNextShiftTurnsOff() {
        val afterFirstTap = CustomKeyboardShiftState.OFF.onShiftTap()
        val afterDoubleTapAction = afterFirstTap.onCapsLockTap()
        val afterNextShiftTap = afterDoubleTapAction.onShiftTap()

        assertEquals(CustomKeyboardShiftState.ONE_SHOT, afterFirstTap)
        assertEquals(CustomKeyboardShiftState.LOCKED, afterDoubleTapAction)
        assertEquals(CustomKeyboardShiftState.OFF, afterNextShiftTap)
    }

    @Test
    fun oneShot_isConsumed_butLockedIsRetained() {
        assertEquals(
            CustomKeyboardShiftState.OFF,
            CustomKeyboardShiftState.ONE_SHOT.consumeOneShot()
        )
        assertEquals(
            CustomKeyboardShiftState.LOCKED,
            CustomKeyboardShiftState.LOCKED.consumeOneShot()
        )
    }

    @Test
    fun uppercaseTransformation_onlyChangesAsciiLetters() {
        assertEquals(
            "ABC-あ1",
            CustomKeyboardShiftState.LOCKED.transformAsciiLetters("aBc-あ1")
        )
        assertEquals(
            "aBc-あ1",
            CustomKeyboardShiftState.OFF.transformAsciiLetters("aBc-あ1")
        )
    }
}
