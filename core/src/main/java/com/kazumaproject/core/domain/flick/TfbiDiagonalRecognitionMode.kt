package com.kazumaproject.core.domain.flick

enum class TfbiDiagonalRecognitionMode(val preferenceValue: String) {
    LEGACY("legacy"),
    STABLE("stable");

    companion object {
        fun fromPreferenceValue(value: String?): TfbiDiagonalRecognitionMode =
            entries.firstOrNull { it.preferenceValue == value } ?: LEGACY
    }
}
