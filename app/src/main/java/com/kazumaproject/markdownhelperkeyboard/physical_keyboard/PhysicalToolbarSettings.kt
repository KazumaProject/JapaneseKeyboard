package com.kazumaproject.markdownhelperkeyboard.physical_keyboard

import android.content.SharedPreferences

data class PhysicalToolbarSettings(
    val floating: Boolean,
    val showMode: Boolean,
    val showKeyboard: Boolean,
    val showVoice: Boolean,
) {
    companion object {
        const val STYLE_KEY = "physical_toolbar_style"
        const val MODE_KEY = "physical_toolbar_show_mode"
        const val KEYBOARD_KEY = "physical_toolbar_show_keyboard"
        const val VOICE_KEY = "physical_toolbar_show_voice"
        const val X_KEY = "physical_toolbar_position_x"
        const val Y_KEY = "physical_toolbar_position_y"
        val keys = setOf(STYLE_KEY, MODE_KEY, KEYBOARD_KEY, VOICE_KEY)

        fun read(prefs: SharedPreferences): PhysicalToolbarSettings {
            val mode = prefs.getBoolean(MODE_KEY, true)
            val keyboard = prefs.getBoolean(KEYBOARD_KEY, true)
            val voice = prefs.getBoolean(VOICE_KEY, false)
            return PhysicalToolbarSettings(
                floating = prefs.getString(STYLE_KEY, "bottom") == "floating",
                showMode = mode || !keyboard && !voice,
                showKeyboard = keyboard,
                showVoice = voice,
            )
        }
    }
}
