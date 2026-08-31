package com.kazumaproject.markdownhelperkeyboard.ime_service.state

enum class KeyboardType {
    TENKEY, GOJUON, SUMIRE, QWERTY, ROMAJI, CUSTOM
}

val KeyboardType.isTenKeyFamily: Boolean
    get() = when (this) {
        KeyboardType.TENKEY,
        KeyboardType.GOJUON,
        KeyboardType.SUMIRE,
        KeyboardType.CUSTOM -> true

        KeyboardType.QWERTY,
        KeyboardType.ROMAJI -> false
    }
