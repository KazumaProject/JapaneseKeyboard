package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.kazumaproject.custom_keyboard.data.KeyAction
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SpecialKeyPolicyTest {
    @Test fun navigationRequiresTheCorrespondingAdvertisedActionOrFlag() {
        fun info(options: Int) = EditorInfo().apply { imeOptions = options }
        assertNull(SpecialKeyPolicy.editorNavigationAction(null, KeyAction.NextField))
        assertNull(SpecialKeyPolicy.editorNavigationAction(info(EditorInfo.IME_ACTION_DONE), KeyAction.NextField))
        assertNull(SpecialKeyPolicy.editorNavigationAction(info(EditorInfo.IME_ACTION_NEXT), KeyAction.PreviousField))
        assertEquals(EditorInfo.IME_ACTION_NEXT, SpecialKeyPolicy.editorNavigationAction(info(EditorInfo.IME_ACTION_NEXT), KeyAction.NextField))
        assertEquals(EditorInfo.IME_ACTION_PREVIOUS, SpecialKeyPolicy.editorNavigationAction(info(EditorInfo.IME_ACTION_PREVIOUS), KeyAction.PreviousField))
        assertEquals(EditorInfo.IME_ACTION_NEXT, SpecialKeyPolicy.editorNavigationAction(
            info(EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NAVIGATE_NEXT or EditorInfo.IME_FLAG_NO_ENTER_ACTION), KeyAction.NextField))
        assertEquals(EditorInfo.IME_ACTION_PREVIOUS, SpecialKeyPolicy.editorNavigationAction(
            info(EditorInfo.IME_ACTION_NEXT or EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS), KeyAction.PreviousField))
    }

    @Test fun navigationDoesNotSendFallbackKeysWhenUnsupportedOrDispatchFails() {
        val editor = mock<InputConnection>()
        val unsupported = EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_DONE }
        assertFalse(SpecialKeyPolicy.dispatchEditorNavigation(editor, unsupported, KeyAction.NextField))
        verifyNoInteractions(editor)
        val supported = EditorInfo().apply { imeOptions = EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS }
        whenever(editor.performEditorAction(EditorInfo.IME_ACTION_PREVIOUS)).thenReturn(false)
        assertTrue(SpecialKeyPolicy.dispatchEditorNavigation(editor, supported, KeyAction.PreviousField))
        verify(editor).performEditorAction(EditorInfo.IME_ACTION_PREVIOUS)
        verify(editor, never()).sendKeyEvent(any())
    }

    @Test fun modifiersToggleIndependentlyAndCharacterMappingDoesNotInventUnicodeKeys() {
        val both = SoftwareModifiers().toggle(KeyAction.CtrlLock).toggle(KeyAction.AltLock)
        assertEquals(SoftwareModifiers(ctrl = true, alt = true), both)
        assertEquals(SoftwareModifiers(alt = true), both.toggle(KeyAction.CtrlLock))
        assertEquals(SpecialKeyPolicy.CharacterKey(KeyEvent.KEYCODE_A), SpecialKeyPolicy.characterKey("a"))
        assertEquals(SpecialKeyPolicy.CharacterKey(KeyEvent.KEYCODE_A), SpecialKeyPolicy.characterKey("A"))
        assertEquals(SpecialKeyPolicy.CharacterKey(KeyEvent.KEYCODE_1, true), SpecialKeyPolicy.characterKey("!"))
        assertNull(SpecialKeyPolicy.characterKey("あ"))
        assertNull(SpecialKeyPolicy.characterKey("abc"))
    }

    @Test fun navigationKeysUseTheirMatchingAndroidKeyCodes() {
        val keys = mapOf(
            KeyAction.Tab to KeyEvent.KEYCODE_TAB,
            KeyAction.Escape to KeyEvent.KEYCODE_ESCAPE,
            KeyAction.Home to KeyEvent.KEYCODE_MOVE_HOME,
            KeyAction.End to KeyEvent.KEYCODE_MOVE_END,
            KeyAction.PageUp to KeyEvent.KEYCODE_PAGE_UP,
            KeyAction.PageDown to KeyEvent.KEYCODE_PAGE_DOWN,
        )
        keys.forEach { (action, expected) ->
            assertEquals(expected, SpecialKeyPolicy.physicalKeyCode(action))
        }
        assertNull(SpecialKeyPolicy.physicalKeyCode(KeyAction.NextField))
        assertNull(SpecialKeyPolicy.physicalKeyCode(KeyAction.PreviousField))
    }

    @Test fun combinedModifiersArePressedAndReleasedAroundEachKey() {
        val connection = mock<InputConnection>()
        val events = mutableListOf<KeyEvent>()
        whenever(connection.sendKeyEvent(any())).thenAnswer { events.add(it.getArgument(0)); true }
        SpecialKeyPolicy.sendKey(connection, KeyEvent.KEYCODE_A, SoftwareModifiers(ctrl = true, alt = true), shift = true)
        assertEquals(listOf(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_CTRL_LEFT), events.map { it.keyCode })
        assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_DOWN, KeyEvent.ACTION_DOWN,
            KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP, KeyEvent.ACTION_UP,
            KeyEvent.ACTION_UP, KeyEvent.ACTION_UP), events.map { it.action })
        assertTrue(events[3].isCtrlPressed && events[3].isAltPressed && events[3].isShiftPressed)
        assertEquals(0, events.last().metaState)
    }
}
