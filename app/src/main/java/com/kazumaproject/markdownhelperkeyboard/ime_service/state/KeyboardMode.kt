package com.kazumaproject.markdownhelperkeyboard.ime_service.state

sealed class KeyboardMode{
    object ModeKeyboard: KeyboardMode()
    object ModeMarkDownHelper: KeyboardMode()
}
