package com.kazumaproject.custom_keyboard.data

/** Placement of the turns after the first flick in the built-in Sumire kana layouts. */
enum class TfbiFlickRotation(val preferenceValue: String) {
    DEFAULT("default"),
    CLOCKWISE("clockwise"),
    COUNTERCLOCKWISE("counterclockwise");

    companion object {
        fun fromPreferenceValue(value: String?): TfbiFlickRotation =
            entries.firstOrNull { it.preferenceValue == value } ?: DEFAULT
    }
}
