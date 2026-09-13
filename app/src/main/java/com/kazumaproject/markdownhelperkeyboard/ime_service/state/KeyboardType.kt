package com.kazumaproject.markdownhelperkeyboard.ime_service.state

enum class KeyboardType {
    TENKEY, GOJUON, SUMIRE, QWERTY, ROMAJI, CUSTOM, DYNAMIC_ORBIT
}

val KeyboardType.isTenKeyFamily: Boolean
    get() = when (this) {
        KeyboardType.TENKEY,
        KeyboardType.GOJUON,
        KeyboardType.SUMIRE,
        KeyboardType.CUSTOM,
        KeyboardType.DYNAMIC_ORBIT -> true

        KeyboardType.QWERTY,
        KeyboardType.ROMAJI -> false
    }
