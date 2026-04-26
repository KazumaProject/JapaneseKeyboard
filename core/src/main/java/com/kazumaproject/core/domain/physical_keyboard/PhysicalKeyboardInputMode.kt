package com.kazumaproject.core.domain.physical_keyboard

enum class PhysicalKeyboardInputMode(val preferenceValue: String) {
    ROMAJI("romaji"),
    KANA("kana");

    companion object {
        fun fromPreferenceValue(value: String?): PhysicalKeyboardInputMode {
            return entries.firstOrNull { it.preferenceValue == value } ?: ROMAJI
        }
    }
}
