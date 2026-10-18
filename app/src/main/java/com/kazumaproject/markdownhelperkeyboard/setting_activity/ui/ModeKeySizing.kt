package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui

import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.tenkey.TenKey
import com.kazumaproject.gojuon_keyboard.GojuonKeyboardView
import com.kazumaproject.core.domain.extensions.KEY_GOJUON_SIZE
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView

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

    fun applySumire(view: FlickKeyboardView, mode: KeyboardInputMode) {
        view.applyKeySizing(
            AppPreference.flick_key_width_scale_percent ?: 160,
            AppPreference.flick_key_height_scale_percent ?: 160,
            AppPreference.flick_key_icon_scale_percent ?: 80,
            sumireCenter(mode),
            AppPreference.flick_special_key_text_size_sp ?: 12f,
        )
        view.setFlickGuideTextSizeSp(sumireGuide(mode))
    }

    fun gojuonCenter(mode: KeyboardInputMode): Float =
        AppPreference.modeKeySize("gojuon", mode, false) ?: KEY_GOJUON_SIZE

    fun applyGojuon(view: GojuonKeyboardView) {
        view.setModeLetterSizes(KeyboardInputMode.entries.associate { inputMode(it) to gojuonCenter(it) })
        view.setKeySizeScale(AppPreference.gojuon_key_width_scale_percent,
            AppPreference.gojuon_key_height_scale_percent)
        view.setSpecialKeySizing(AppPreference.gojuon_special_key_width_scale_percent,
            AppPreference.gojuon_special_key_height_scale_percent,
            AppPreference.gojuon_special_icon_scale_percent, AppPreference.gojuon_special_text_size_sp)
    }

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
