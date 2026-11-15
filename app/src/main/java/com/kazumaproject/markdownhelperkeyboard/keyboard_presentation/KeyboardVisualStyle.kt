package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import com.google.android.material.color.DynamicColors
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.tenkey.TenKey
import com.kazumaproject.gojuon_keyboard.GojuonKeyboardView
import com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView

/** The same immutable theme parameters are used for IME surfaces and settings previews. */
internal data class KeyboardVisualStyle(
    val skin: KeyboardSkinId,
    val mode: String,
    val night: Int,
    val dynamic: Boolean,
    val background: Int,
    val key: Int,
    val special: Int,
    val text: Int,
    val specialText: Int,
    val glass: Boolean,
    val border: Boolean,
    val borderColor: Int,
    val keyAlpha: Int,
    val borderWidth: Int,
) {
    fun apply(view: View) {
        when (view) {
            is TenKey -> view.applyKeyboardTheme(
                skinId = skin, themeMode = mode, currentNightMode = night,
                isDynamicColorEnabled = dynamic, customBgColor = background,
                customKeyColor = key, customSpecialKeyColor = special,
                customKeyTextColor = text, customSpecialKeyTextColor = specialText,
                liquidGlassEnable = glass, customBorderEnable = border,
                customBorderColor = borderColor, liquidGlassKeyAlphaEnable = keyAlpha,
                borderWidth = borderWidth
            )
            is GojuonKeyboardView -> view.applyKeyboardTheme(
                skinId = skin, themeMode = mode, currentNightMode = night,
                isDynamicColorEnabled = dynamic, customBgColor = background,
                customKeyColor = key, customSpecialKeyColor = special,
                customKeyTextColor = text, customSpecialKeyTextColor = specialText,
                liquidGlassEnable = glass, customBorderEnable = border,
                customBorderColor = borderColor, liquidGlassKeyAlphaEnable = keyAlpha,
                borderWidth = borderWidth
            )
            is QWERTYKeyboardView -> view.applyKeyboardTheme(
                skinId = skin, themeMode = mode, currentNightMode = night,
                isDynamicColorEnabled = dynamic, customBgColor = background,
                customKeyColor = key, customSpecialKeyColor = special,
                customKeyTextColor = text, customSpecialKeyTextColor = specialText,
                liquidGlassEnable = glass, customBorderEnable = border,
                customBorderColor = borderColor, liquidGlassKeyAlphaEnable = keyAlpha,
                borderWidth = borderWidth
            )
            is FlickKeyboardView -> view.applyKeyboardTheme(
                skinId = skin, themeMode = mode, currentNightMode = night,
                isDynamicColorEnabled = dynamic, customBgColor = background,
                customKeyColor = key, customSpecialKeyColor = special,
                customKeyTextColor = text, customSpecialKeyTextColor = specialText,
                liquidGlassEnable = glass, customBorderEnable = border,
                customBorderColor = borderColor, liquidGlassKeyAlphaEnable = keyAlpha,
                borderWidth = borderWidth
            )
        }
    }

    companion object {
        fun read(context: Context, skin: KeyboardSkinId = AppPreference.keyboardSkin): KeyboardVisualStyle {
            val palette = KeyboardSkinRegistry.find(skin)?.palette
            return KeyboardVisualStyle(
                skin, if (palette != null) "custom" else AppPreference.theme_mode,
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK,
                DynamicColors.isDynamicColorAvailable(),
                palette?.background ?: AppPreference.custom_theme_bg_color, palette?.key ?: AppPreference.custom_theme_key_color,
                palette?.specialKey ?: AppPreference.custom_theme_special_key_color, palette?.text ?: AppPreference.custom_theme_key_text_color,
                palette?.specialText ?: AppPreference.custom_theme_special_key_text_color, palette == null && AppPreference.liquid_glass_preference,
                palette == null && AppPreference.custom_theme_border_enable == true,
                AppPreference.custom_theme_border_color ?: Color.BLACK,
                if (palette != null) 255 else AppPreference.liquid_glass_key_alpha, AppPreference.custom_theme_border_width,
            )
        }
    }
}
