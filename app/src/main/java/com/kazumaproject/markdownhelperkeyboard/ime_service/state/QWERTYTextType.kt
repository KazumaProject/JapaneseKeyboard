package com.kazumaproject.markdownhelperkeyboard.ime_service.state

sealed class QWERTYTextType{
    object TypeDefault: QWERTYTextType()
    object TypePassword: QWERTYTextType()
}
