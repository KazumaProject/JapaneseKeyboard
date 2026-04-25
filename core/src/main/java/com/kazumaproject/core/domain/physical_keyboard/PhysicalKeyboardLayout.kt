package com.kazumaproject.core.domain.physical_keyboard

enum class PhysicalKeyboardLayout(val preferenceValue: String) {
    JAPANESE_109A("japanese_109a"),
    JIS("jis"),
    US("us");

    companion object {
        fun fromPreferenceValue(value: String?): PhysicalKeyboardLayout {
            return entries.firstOrNull { it.preferenceValue == value } ?: JAPANESE_109A
        }
    }
}
