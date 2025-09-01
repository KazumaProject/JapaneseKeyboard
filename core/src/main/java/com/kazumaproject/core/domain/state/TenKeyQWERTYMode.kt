package com.kazumaproject.core.domain.state

sealed class TenKeyQWERTYMode {
    data object Default : TenKeyQWERTYMode()
    data object TenKeyQWERTY : TenKeyQWERTYMode()
    data object TenKeyQWERTYRomaji : TenKeyQWERTYMode()
    data object Sumire : TenKeyQWERTYMode()
    data object Custom : TenKeyQWERTYMode()
    data object Number : TenKeyQWERTYMode()
}
