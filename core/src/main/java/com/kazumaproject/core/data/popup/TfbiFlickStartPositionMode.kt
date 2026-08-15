package com.kazumaproject.core.data.popup

/**
 * Defines the coordinate origin used by the TFBi guide popup.
 *
 * TOUCH_POINT preserves the original behavior: the guide marker starts at the actual
 * key-local touch position. KEY_CENTER uses a virtual marker that starts at the center of
 * the key and follows the displacement from the original touch point.
 */
enum class TfbiFlickStartPositionMode(
    val preferenceValue: String
) {
    TOUCH_POINT("touch_point"),
    KEY_CENTER("key_center");

    companion object {
        fun fromPreference(value: String?): TfbiFlickStartPositionMode {
            return entries.firstOrNull { it.preferenceValue == value }
                ?: TOUCH_POINT
        }
    }
}
