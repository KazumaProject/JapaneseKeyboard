package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui

import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.tenkey.TenKey

internal object ModeKeySizing {
    fun inputMode(mode: KeyboardInputMode): InputMode = when (mode) {
        KeyboardInputMode.HIRAGANA -> InputMode.ModeJapanese
        KeyboardInputMode.ENGLISH -> InputMode.ModeEnglish
        KeyboardInputMode.SYMBOLS -> InputMode.ModeNumber
    }

    fun tenkeyCenter(mode: KeyboardInputMode, guides: Boolean): Float {
        AppPreference.modeKeySize("tenkey", mode, false)?.let { return it }
        val base = if (guides) 11f else when (mode) {
            KeyboardInputMode.HIRAGANA -> 17f
            KeyboardInputMode.ENGLISH -> 12f
            KeyboardInputMode.SYMBOLS -> 16f
        }
        return base + (AppPreference.key_letter_size ?: 0f).toInt()
    }

    fun tenkeyGuide(mode: KeyboardInputMode): Float =
        AppPreference.modeKeySize("tenkey", mode, true)
            ?: (11f + (AppPreference.key_letter_size ?: 0f).toInt()) * 0.6f

    fun sumireCenter(mode: KeyboardInputMode): Float =
        AppPreference.modeKeySize("sumire", mode, false) ?: AppPreference.flick_key_text_size_sp ?: 16f

    fun sumireGuide(mode: KeyboardInputMode): Float =
        AppPreference.modeKeySize("sumire", mode, true)
            ?: (AppPreference.flick_guide_text_size_sp_preference ?: 9).coerceIn(6, 16).toFloat()

    fun applyTenkey(view: TenKey) {
        view.setKeyLetterSizeDelta((AppPreference.key_letter_size ?: 0f).toInt())
        view.setModeLetterSizes(
            KeyboardInputMode.entries.mapNotNull { mode ->
                AppPreference.modeKeySize("tenkey", mode, false)?.let { inputMode(mode) to it }
            }.toMap(),
            KeyboardInputMode.entries.mapNotNull { mode ->
                AppPreference.modeKeySize("tenkey", mode, true)?.let { inputMode(mode) to it }
            }.toMap(),
        )
    }
}
