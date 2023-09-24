package com.kazumaproject.markdownhelperkeyboard.ime_service.state

sealed class InputMode{
    object ModeJapanese: InputMode()
    object ModeEnglish: InputMode()
    object ModeNumber: InputMode()
}
