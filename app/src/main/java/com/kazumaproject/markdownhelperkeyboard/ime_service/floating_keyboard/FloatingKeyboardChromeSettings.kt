package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_keyboard

import android.content.SharedPreferences

internal enum class FloatingHideButtonPosition { BOTTOM_LEFT, TOP_LEFT }

internal data class FloatingKeyboardChromeSettings(
    val hideButtonPosition: FloatingHideButtonPosition = FloatingHideButtonPosition.BOTTOM_LEFT,
    val showEditButton: Boolean = true,
) {
    companion object {
        const val HIDE_BUTTON_POSITION = "floating_keyboard_hide_button_position"
        const val SHOW_EDIT_BUTTON = "floating_keyboard_show_edit_button"

        fun read(prefs: SharedPreferences) = FloatingKeyboardChromeSettings(
            hideButtonPosition = if (prefs.getString(HIDE_BUTTON_POSITION, "bottom_left") == "top_left")
                FloatingHideButtonPosition.TOP_LEFT else FloatingHideButtonPosition.BOTTOM_LEFT,
            showEditButton = prefs.getBoolean(SHOW_EDIT_BUTTON, true),
        )
    }
}
