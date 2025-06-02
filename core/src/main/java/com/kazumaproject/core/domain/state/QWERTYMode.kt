package com.kazumaproject.core.domain.state

sealed class QWERTYMode {
    data object Default : QWERTYMode()
    data object QWERTY : QWERTYMode()
}
