package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut

import android.content.Context
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.database.PhysicalKeyboardShortcutItem

object PhysicalShortcutFormatter {
    fun format(context: Context, item: PhysicalKeyboardShortcutItem): String {
        val parts = mutableListOf<String>()
        if (item.ctrl) parts += context.getString(R.string.physical_keyboard_shortcut_ctrl)
        if (item.shift) parts += context.getString(R.string.physical_keyboard_shortcut_shift)
        if (item.alt) parts += context.getString(R.string.physical_keyboard_shortcut_alt)
        if (item.meta) parts += context.getString(R.string.physical_keyboard_shortcut_meta)
        parts += PhysicalKeyboardShortcutKey.fromKeyCode(item.keyCode)?.displayLabel(context)
            ?: context.getString(R.string.physical_keyboard_shortcut_unknown_key_code, item.keyCode)
        return parts.joinToString(" + ")
    }
}
