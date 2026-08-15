package com.kazumaproject.core.data.popup

/**
 * Presentation modes for the two-step/three-step flick popup.
 *
 * The legacy mode is intentionally the default so existing installations keep their
 * current popup behavior when the preference is not present yet.
 */
enum class TfbiPopupPresentationMode(
    val preferenceValue: String
) {
    LEGACY_GRID("legacy_grid"),
    GUIDE_ABOVE_KEY("guide_above_key");

    companion object {
        fun fromPreference(value: String?): TfbiPopupPresentationMode {
            return entries.firstOrNull { it.preferenceValue == value }
                ?: LEGACY_GRID
        }
    }
}
