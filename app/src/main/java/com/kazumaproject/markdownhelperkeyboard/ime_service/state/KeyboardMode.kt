package com.kazumaproject.markdownhelperkeyboard.ime_service.state

sealed class KeyboardMode{
    object ModeTenKeyboard: KeyboardMode()
    object ModeKigouView: KeyboardMode()
}
