package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut

import android.view.KeyEvent
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.database.PhysicalKeyboardShortcutItem

object PhysicalShortcutMatcher {
    fun match(
        shortcuts: List<PhysicalKeyboardShortcutItem>,
        currentContext: PhysicalKeyboardShortcutContext,
        keyCode: Int,
        event: KeyEvent
    ): PhysicalKeyboardShortcutItem? {
        return match(
            shortcuts = shortcuts,
            currentContext = currentContext,
            keyCode = keyCode,
            scanCode = event.scanCode,
            ctrl = event.isCtrlPressed,
            shift = event.isShiftPressed,
            alt = event.isAltPressed,
            meta = event.isMetaPressed
        )
    }

    fun match(
        shortcuts: List<PhysicalKeyboardShortcutItem>,
        currentContext: PhysicalKeyboardShortcutContext,
        keyCode: Int,
        scanCode: Int,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
        meta: Boolean
    ): PhysicalKeyboardShortcutItem? {
        val candidates = shortcuts.filter {
            it.enabled &&
                it.keyCode == keyCode &&
                it.ctrl == ctrl &&
                it.shift == shift &&
                it.alt == alt &&
                it.meta == meta &&
                (it.scanCode == null || it.scanCode == scanCode)
        }
        return candidates.firstOrNull { it.context == currentContext.id }
            ?: candidates.firstOrNull { it.context == PhysicalKeyboardShortcutContext.ANY.id }
    }
}
