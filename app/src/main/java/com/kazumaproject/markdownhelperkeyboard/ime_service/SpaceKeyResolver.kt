package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.core.domain.state.InputMode

fun resolveEmptySpaceForCurrentMode(
    isCustomLayoutDirectMode: Boolean,
    customDirectModeSpaceHankakuPreference: Boolean,
    isFlick: Boolean,
    currentInputMode: InputMode
): String {
    if (isCustomLayoutDirectMode) {
        return if (customDirectModeSpaceHankakuPreference) " " else "　"
    }

    return if (currentInputMode == InputMode.ModeJapanese) {
        if (isFlick) " " else "　"
    } else {
        " "
    }
}
