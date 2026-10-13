package com.kazumaproject.custom_keyboard.data

/** Additional character groups offered by the built-in Sumire three-step kana layouts. */
enum class SumireFlickInputMode(val preferenceValue: String) {
    CONTRACTED("contracted"),
    VOICED("voiced"),
    ALL("all");

    companion object {
        fun fromPreferenceValue(value: String?): SumireFlickInputMode =
            entries.firstOrNull { it.preferenceValue == value } ?: ALL
    }
}
