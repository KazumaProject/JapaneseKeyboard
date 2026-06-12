package com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior

enum class TypeNullInputBehaviorSetting(val preferenceValue: String) {
    DEFAULT("default"),
    DIRECT_COMMIT("direct_commit"),
    COMPOSING_TEXT("composing_text");

    companion object {
        fun fromPreferenceValue(value: String?): TypeNullInputBehaviorSetting {
            return entries.firstOrNull { it.preferenceValue == value } ?: DEFAULT
        }
    }
}
