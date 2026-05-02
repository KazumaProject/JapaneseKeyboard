package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout

enum class CustomKeyboardSelectionReason {
    InitialRestore,
    InitialDefault,
    UserTabClick,
    UserNextTab,
    MoveToStableId
}

data class InitialCustomKeyboardSelection(
    val index: Int,
    val reason: CustomKeyboardSelectionReason
)

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
    return resolveInitialCustomKeyboardSelection(
        layouts = layouts,
        rememberLast = rememberLast,
        savedStableId = savedStableId
    )?.index
}

fun resolveInitialCustomKeyboardSelection(
    layouts: List<CustomKeyboardLayout>,
    rememberLast: Boolean,
    savedStableId: String?
): InitialCustomKeyboardSelection? {
    if (layouts.isEmpty()) return null
    if (rememberLast && !savedStableId.isNullOrBlank()) {
        val savedIndex = resolveCustomKeyboardIndexByStableId(layouts, savedStableId)
        if (savedIndex != null) {
            return InitialCustomKeyboardSelection(
                index = savedIndex,
                reason = CustomKeyboardSelectionReason.InitialRestore
            )
        }
    }
    return InitialCustomKeyboardSelection(
        index = 0,
        reason = CustomKeyboardSelectionReason.InitialDefault
    )
}

fun shouldPersistCustomKeyboardSelection(
    layout: CustomKeyboardLayout,
    rememberLast: Boolean,
    reason: CustomKeyboardSelectionReason
): Boolean {
    if (!rememberLast) return false
    if (layout.stableId.isBlank()) return false

    return reason == CustomKeyboardSelectionReason.UserTabClick ||
        reason == CustomKeyboardSelectionReason.UserNextTab ||
        reason == CustomKeyboardSelectionReason.MoveToStableId
}
