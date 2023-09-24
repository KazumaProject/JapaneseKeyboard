package com.kazumaproject.markdownhelperkeyboard.ime_service.state

sealed class QWERTYLayoutMode{
    object KeyboardLayoutNormal: QWERTYLayoutMode()
    object KeyboardLayoutSpecialLetters: QWERTYLayoutMode()
    object KeyboardLayoutSpecialLetters2: QWERTYLayoutMode()
}
