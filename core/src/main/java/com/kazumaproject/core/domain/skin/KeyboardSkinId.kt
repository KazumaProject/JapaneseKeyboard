package com.kazumaproject.core.domain.skin

/** Stored separately from the legacy color theme, so selecting a skin never destroys it. */
enum class KeyboardSkinId(val preferenceValue: String) {
    DEFAULT("default"),
    CUPERTINO_LIGHT("cupertino_light"),
    CUPERTINO_DARK("cupertino_dark");

    companion object {
        const val PREFERENCE_KEY = "keyboard_skin_preference"

        /** Old installs and removed/future skin IDs retain the existing keyboard. */
        fun fromPreference(value: String?): KeyboardSkinId =
            entries.firstOrNull { it.preferenceValue == value } ?: DEFAULT
    }
}
