package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.kazumaproject.custom_keyboard.data.KeyAction

/** The two latches belong to an input session, not to a keyboard view. */
internal data class SoftwareModifiers(val ctrl: Boolean = false, val alt: Boolean = false) {
    val active get() = ctrl || alt
    fun toggle(action: KeyAction): SoftwareModifiers = when (action) {
        KeyAction.CtrlLock -> copy(ctrl = !ctrl)
        KeyAction.AltLock -> copy(alt = !alt)
        else -> this
    }
}

internal object SpecialKeyPolicy {
    /** Only request navigation advertised by this editor; never infer success from dispatch. */
    fun editorNavigationAction(info: EditorInfo?, action: KeyAction): Int? {
        val options = info?.imeOptions ?: return null
        val advertised = options and EditorInfo.IME_MASK_ACTION
        return when (action) {
            KeyAction.NextField -> EditorInfo.IME_ACTION_NEXT.takeIf {
                advertised == it || options and EditorInfo.IME_FLAG_NAVIGATE_NEXT != 0
            }
            KeyAction.PreviousField -> EditorInfo.IME_ACTION_PREVIOUS.takeIf {
                advertised == it || options and EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS != 0
            }
            else -> null
        }
    }

    /** True means an advertised request was issued, not that focus moved. */
    fun dispatchEditorNavigation(connection: InputConnection?, info: EditorInfo?, action: KeyAction): Boolean {
        val editorAction = editorNavigationAction(info, action) ?: return false
        val editor = connection ?: return false
        editor.performEditorAction(editorAction)
        return true
    }

    fun physicalKeyCode(action: KeyAction): Int? = when (action) {
        KeyAction.Tab -> KeyEvent.KEYCODE_TAB
        KeyAction.Escape -> KeyEvent.KEYCODE_ESCAPE
        KeyAction.Home -> KeyEvent.KEYCODE_MOVE_HOME
        KeyAction.End -> KeyEvent.KEYCODE_MOVE_END
        KeyAction.PageUp -> KeyEvent.KEYCODE_PAGE_UP
        KeyAction.PageDown -> KeyEvent.KEYCODE_PAGE_DOWN
        KeyAction.MoveCursorLeft -> KeyEvent.KEYCODE_DPAD_LEFT
        KeyAction.MoveCursorRight -> KeyEvent.KEYCODE_DPAD_RIGHT
        KeyAction.MoveCursorUp -> KeyEvent.KEYCODE_DPAD_UP
        KeyAction.MoveCursorDown -> KeyEvent.KEYCODE_DPAD_DOWN
        KeyAction.Delete, KeyAction.Backspace -> KeyEvent.KEYCODE_DEL
        KeyAction.DeleteAfterCursor -> KeyEvent.KEYCODE_FORWARD_DEL
        KeyAction.Space -> KeyEvent.KEYCODE_SPACE
        KeyAction.Enter -> KeyEvent.KEYCODE_ENTER
        else -> null
    }

    data class CharacterKey(val keyCode: Int, val shift: Boolean = false)

    /** Only map one known US keyboard character. Other text keeps the normal input path. */
    fun characterKey(text: String): CharacterKey? {
        if (text.length != 1) return null
        val c = text[0]
        if (c in 'a'..'z' || c in 'A'..'Z') {
            return CharacterKey(KeyEvent.KEYCODE_A + (c.lowercaseChar() - 'a'))
        }
        if (c in '0'..'9') return CharacterKey(KeyEvent.KEYCODE_0 + (c - '0'))
        val unshifted = " `-=[]\\;',./"
        val shifted = " ~_+{}|:\"<>?"
        val keyCodes = intArrayOf(KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_GRAVE,
            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_LEFT_BRACKET,
            KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_BACKSLASH, KeyEvent.KEYCODE_SEMICOLON,
            KeyEvent.KEYCODE_APOSTROPHE, KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD,
            KeyEvent.KEYCODE_SLASH)
        val plainIndex = unshifted.indexOf(c)
        if (plainIndex >= 0) return CharacterKey(keyCodes[plainIndex])
        val shiftedIndex = shifted.indexOf(c)
        if (shiftedIndex >= 0) return CharacterKey(keyCodes[shiftedIndex], true)
        val digitIndex = ")!@#$%^&*(".indexOf(c)
        return if (digitIndex >= 0) CharacterKey(KeyEvent.KEYCODE_0 + digitIndex, true) else null
    }

    /** Send paired modifier and key events to this connection; never leave a modifier pressed. */
    fun sendKey(connection: InputConnection, code: Int, modifiers: SoftwareModifiers, shift: Boolean = false) {
        val downTime = SystemClock.uptimeMillis()
        var meta = 0
        val pressed = mutableListOf<Pair<Int, Int>>()
        fun event(action: Int, keyCode: Int, state: Int) = KeyEvent(
            downTime, SystemClock.uptimeMillis(), action, keyCode, 0, state,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE, InputDevice.SOURCE_KEYBOARD
        )
        fun downModifier(keyCode: Int, bits: Int) {
            meta = meta or bits
            pressed.add(keyCode to bits)
            connection.sendKeyEvent(event(KeyEvent.ACTION_DOWN, keyCode, meta))
        }
        try {
            if (modifiers.ctrl) downModifier(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
            if (modifiers.alt) downModifier(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON)
            if (shift) downModifier(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
            try {
                connection.sendKeyEvent(event(KeyEvent.ACTION_DOWN, code, meta))
            } finally {
                connection.sendKeyEvent(event(KeyEvent.ACTION_UP, code, meta))
            }
        } finally {
            pressed.asReversed().forEach { (keyCode, bits) ->
                meta = meta and bits.inv()
                connection.sendKeyEvent(event(KeyEvent.ACTION_UP, keyCode, meta))
            }
        }
    }
}
