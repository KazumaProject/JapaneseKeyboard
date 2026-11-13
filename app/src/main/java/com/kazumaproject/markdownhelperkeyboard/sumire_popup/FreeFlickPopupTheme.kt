package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.custom_keyboard.view.PopupMapView
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference

/** Settings previews use the same persisted popup overrides and keyboard skin as the IME. */
internal fun PopupMapView.applyFreeFlickTheme() {
    val preferences = AppPreference
    val custom = preferences.theme_mode == "custom"
    applyPopupViewStyle(PopupViewStyle(100, preferences.flick_tfbi_popup_text_size_sp ?: 20f,
        backgroundColor = if (preferences.key_popup_use_custom_color) preferences.key_popup_background_color
            else preferences.custom_theme_key_color.takeIf { custom },
        textColor = if (preferences.key_popup_use_custom_color) preferences.key_popup_text_color
            else preferences.custom_theme_key_text_color.takeIf { custom },
        skinId = preferences.keyboardSkin),
        preferences.custom_theme_special_key_color.takeIf { custom },
        preferences.custom_theme_border_color.takeIf { custom && preferences.custom_theme_border_enable })
}
