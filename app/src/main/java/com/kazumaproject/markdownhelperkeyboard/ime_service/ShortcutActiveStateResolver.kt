package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior.ResolvedInputBehavior
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType

internal fun resolveShortcutActiveTypes(
    keyboardLayoutEditActive: Boolean,
    keyboardFloatingActive: Boolean,
    inputBehavior: ResolvedInputBehavior,
    liveConversionEnabled: Boolean,
    learningPaused: Boolean = false,
): Set<ShortcutType> = buildSet {
    if (keyboardLayoutEditActive) {
        add(ShortcutType.KEYBOARD_LAYOUT_EDIT)
    }

    if (keyboardFloatingActive) {
        add(ShortcutType.KEYBOARD_FLOATING_TOGGLE)
    }

    if (inputBehavior == ResolvedInputBehavior.DIRECT_COMMIT) {
        add(ShortcutType.INPUT_BEHAVIOR_TOGGLE)
    }

    if (liveConversionEnabled) {
        add(ShortcutType.LIVE_CONVERSION_TOGGLE)
    }

    if (learningPaused) {
        add(ShortcutType.LEARNING_PAUSE)
    }
}
