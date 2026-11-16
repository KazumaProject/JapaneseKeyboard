package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import androidx.appcompat.content.res.AppCompatResources
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.ModeKeySizing
import com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView
import com.kazumaproject.tenkey.TenKey

/** Appearance only. Input listeners, input connections, dictionaries and saved choices are not touched. */
internal object KeyboardKeyPresentation {
    fun tenkey(view: TenKey) = with(AppPreference) {
        ModeKeySizing.applyTenkey(view)
        view.setKeySizeScale(tenkey_key_width_scale_percent ?: 100, tenkey_key_height_scale_percent ?: 100)
        view.setUseThreeStateKeyboard(tenkey_use_three_state_keyboard_preference, tenkey_number_symbol_key_gap_preference)
        view.setUseQwertyNumberWhenThreeStateOff(tenkey_switch_number_to_qwerty_number_preference)
        view.setLanguageEnableKeyState(tenkey_show_language_button_preference)
        view.setBackgroundSmallLetterKey(AppCompatResources.getDrawable(view.context,
            if (tenkey_show_language_button_preference) com.kazumaproject.core.R.drawable.language_24dp
            else com.kazumaproject.core.R.drawable.kana_small))
        view.setFlickGuideEnabled(tenkey_keymap_guide_layout == true, tenkey_keymap_guide_english, tenkey_keymap_guide_number)
    }

    fun qwerty(view: QWERTYKeyboardView, showNumberSwitch: Boolean = false) = with(AppPreference) {
        view.setSpecialKeyVisibility(qwerty_show_cursor_buttons == true, qwerty_show_ime_button != false,
            qwerty_show_kutouten_buttons == true, qwerty_show_emoji_button == true)
        view.setRomajiEnglishSwitchKeyTextWithStyle(true)
        view.updateSymbolKeymapState(qwerty_show_keymap_symbols == true)
        view.updateNumberKeyState(qwerty_show_number_buttons == true)
        view.setFlickUpDetectionEnabled(qwerty_enable_flick_up_preference == true)
        view.setFlickDownDetectionEnabled(qwerty_enable_flick_down_preference == true)
        view.setNumberKeyFlickUpChars(getQwertyNumberKeyFlickUpChars())
        view.setNumberKeyFlickDownChars(getQwertyNumberKeyFlickDownChars())
        view.setNumberSwitchKeyTextStyle(qwerty_switch_number_key_without_number_preference)
        view.setSwitchNumberLayoutKeyVisibility(showNumberSwitch)
        view.setKeyMargins(qwerty_key_vertical_margin ?: 5f, qwerty_key_horizontal_gap ?: 2f,
            qwerty_key_indent_large ?: 23f, qwerty_key_indent_small ?: 9f, qwerty_key_side_margin ?: 4f,
            qwerty_key_text_size ?: 18f, qwerty_symbol_keymap_text_size ?: 9f,
            qwerty_special_key_text_size ?: 12f, qwerty_special_key_icon_size ?: 18f)
    }

    fun flick(view: FlickKeyboardView, sumire: Boolean, mode: KeyboardInputMode = KeyboardInputMode.HIRAGANA) = with(AppPreference) {
        view.applyKeySizing(flick_key_width_scale_percent ?: 160, flick_key_height_scale_percent ?: 160,
            flick_key_icon_scale_percent ?: 80, flick_key_text_size_sp ?: 16f, flick_special_key_text_size_sp ?: 12f)
        val enabled = if (sumire) when (mode) {
            KeyboardInputMode.HIRAGANA -> sumire_keymap_guide_japanese
            KeyboardInputMode.ENGLISH -> sumire_keymap_guide_english
            KeyboardInputMode.SYMBOLS -> sumire_keymap_guide_number
        } else flick_keymap_guide_layout == true
        view.setFlickGuideEnabled(enabled, sumire)
        view.setFlickGuideTextSizeSp((flick_guide_text_size_sp_preference ?: 9).coerceIn(6, 16).toFloat())
        view.setFlickGuideMaxCodePoints((flick_guide_max_characters_preference ?: 1).coerceIn(1, 4))
        if (sumire) ModeKeySizing.applySumire(view, mode)
    }
}
