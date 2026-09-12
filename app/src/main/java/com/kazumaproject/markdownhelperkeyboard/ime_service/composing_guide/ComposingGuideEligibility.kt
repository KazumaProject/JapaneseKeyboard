package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

internal fun canShowComposingGuide(
    inputViewActive: Boolean,
    fullscreen: Boolean,
    hardwareKeyboard: Boolean,
    physicalKeyboardMode: Boolean,
    floatingMode: Boolean,
    password: Boolean,
    layoutEditing: Boolean,
): Boolean = inputViewActive && !fullscreen && !hardwareKeyboard && !physicalKeyboardMode &&
    !floatingMode && !password && !layoutEditing
