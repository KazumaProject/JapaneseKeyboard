package com.kazumaproject.core.domain.state

sealed class QWERTYMode {
    data object Default : QWERTYMode()
    data object Number : QWERTYMode()
    data object Symbol : QWERTYMode()
}
