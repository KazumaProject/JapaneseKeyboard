package com.kazumaproject.qwerty_keyboard.ui

import com.kazumaproject.core.data.qwerty.CapsLockState
import com.kazumaproject.core.domain.state.QWERTYMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QwertyKeyboardUiState の値セマンティクスを検証するユニットテスト。
 *
 * snapshotUiState() / renderUiState() が QWERTYKeyboardView 同士で
 * 状態を非破壊的に伝搬するために、UiState 自体は immutable / value-equal で
 * あるべきという契約を担保する。
 */
class QwertyKeyboardUiStateTest {

    @Test
    fun sameContentIsEqual() {
        val a = QwertyKeyboardUiState(
            qwertyMode = QWERTYMode.Default,
            capsLockState = CapsLockState(shiftOn = true, capsLockOn = false),
            romajiMode = false,
            enterKeyText = "Return",
            spaceKeyText = "Space",
            showRomajiEnglishSwitchKey = false
        )
        val b = QwertyKeyboardUiState(
            qwertyMode = QWERTYMode.Default,
            capsLockState = CapsLockState(shiftOn = true, capsLockOn = false),
            romajiMode = false,
            enterKeyText = "Return",
            spaceKeyText = "Space",
            showRomajiEnglishSwitchKey = false
        )

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun differentQwertyModeIsNotEqual() {
        val base = QwertyKeyboardUiState(
            qwertyMode = QWERTYMode.Default,
            capsLockState = CapsLockState(),
            romajiMode = false,
            enterKeyText = "",
            spaceKeyText = "",
            showRomajiEnglishSwitchKey = false
        )
        val numberMode = base.copy(qwertyMode = QWERTYMode.Number)
        val symbolMode = base.copy(qwertyMode = QWERTYMode.Symbol)

        assertNotEquals(base, numberMode)
        assertNotEquals(base, symbolMode)
        assertNotEquals(numberMode, symbolMode)
    }

    @Test
    fun differentCapsLockStateIsNotEqual() {
        val base = QwertyKeyboardUiState(
            qwertyMode = QWERTYMode.Default,
            capsLockState = CapsLockState(),
            romajiMode = false,
            enterKeyText = "",
            spaceKeyText = "",
            showRomajiEnglishSwitchKey = false
        )
        val shifted = base.copy(capsLockState = CapsLockState(shiftOn = true))
        val capsLocked = base.copy(capsLockState = CapsLockState(capsLockOn = true))

        assertNotEquals(base, shifted)
        assertNotEquals(base, capsLocked)
        assertNotEquals(shifted, capsLocked)
    }

    @Test
    fun copyPreservesUnchangedFields() {
        val base = QwertyKeyboardUiState(
            qwertyMode = QWERTYMode.Number,
            capsLockState = CapsLockState(shiftOn = false, capsLockOn = true),
            romajiMode = true,
            enterKeyText = "決定",
            spaceKeyText = "空白",
            showRomajiEnglishSwitchKey = true
        )

        val copy = base.copy(qwertyMode = QWERTYMode.Symbol)

        assertEquals(QWERTYMode.Symbol, copy.qwertyMode)
        assertEquals(base.capsLockState, copy.capsLockState)
        assertEquals(base.romajiMode, copy.romajiMode)
        assertEquals(base.enterKeyText, copy.enterKeyText)
        assertEquals(base.spaceKeyText, copy.spaceKeyText)
        assertEquals(base.showRomajiEnglishSwitchKey, copy.showRomajiEnglishSwitchKey)
    }

    @Test
    fun romajiModeAndSwitchKeyVisibilityAreIndependent() {
        val a = QwertyKeyboardUiState(
            qwertyMode = QWERTYMode.Default,
            capsLockState = CapsLockState(),
            romajiMode = true,
            enterKeyText = "",
            spaceKeyText = "",
            showRomajiEnglishSwitchKey = false
        )
        val b = a.copy(showRomajiEnglishSwitchKey = true)

        assertTrue(a.romajiMode)
        assertFalse(a.showRomajiEnglishSwitchKey)
        assertTrue(b.romajiMode)
        assertTrue(b.showRomajiEnglishSwitchKey)
    }
}
