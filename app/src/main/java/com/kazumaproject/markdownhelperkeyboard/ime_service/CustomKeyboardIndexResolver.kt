package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout

fun resolveCustomKeyboardIndexByStableId(
    layouts: List<CustomKeyboardLayout>,
    stableId: String
): Int? {
    if (stableId.isBlank()) return null
    return layouts.indexOfFirst { it.stableId == stableId }
        .takeIf { it >= 0 }
}

fun resolveInitialCustomKeyboardIndex(
    layouts: List<CustomKeyboardLayout>,
    rememberLast: Boolean,
    savedStableId: String?
): Int? {
    if (layouts.isEmpty()) return null
    if (!rememberLast) return 0
    return resolveCustomKeyboardIndexByStableId(layouts, savedStableId.orEmpty()) ?: 0
}
