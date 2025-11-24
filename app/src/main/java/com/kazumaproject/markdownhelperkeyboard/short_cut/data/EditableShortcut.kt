package com.kazumaproject.markdownhelperkeyboard.short_cut.data

import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType

data class EditableShortcut(
    val type: ShortcutType,
    var isEnabled: Boolean,
    val id: Long = 0 // DB上のID (新規なら0)
)
