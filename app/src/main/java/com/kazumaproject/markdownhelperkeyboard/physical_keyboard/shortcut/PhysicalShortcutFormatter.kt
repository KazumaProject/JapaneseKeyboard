package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut

import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.database.PhysicalKeyboardShortcutItem

object PhysicalShortcutFormatter {
    fun format(item: PhysicalKeyboardShortcutItem): String {
        val parts = mutableListOf<String>()
        if (item.ctrl) parts += "Ctrl"
        if (item.shift) parts += "Shift"
        if (item.alt) parts += "Alt"
        if (item.meta) parts += "Meta"
        parts += PhysicalKeyboardShortcutKey.fromKeyCode(item.keyCode)?.label ?: "KeyCode ${item.keyCode}"
        return parts.joinToString(" + ")
    }
}
