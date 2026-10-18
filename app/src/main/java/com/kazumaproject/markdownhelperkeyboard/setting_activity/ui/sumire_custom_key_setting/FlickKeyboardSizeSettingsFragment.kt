package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.sumire_custom_key_setting

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.KeyboardSizingControls
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.KeyboardSizingValues
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.ModeKeySizing
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.SizingField
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.CommonPreferenceFragment

class FlickKeyboardSizeSettingsFragment : Fragment(R.layout.fragment_flick_keyboard_size_settings) {
    private var controls: KeyboardSizingControls? = null
    private val isSumire: Boolean
        get() = arguments?.getString("sizingSurface") != "custom" &&
            arguments?.getString(CommonPreferenceFragment.ARG_HIGHLIGHT_PREFERENCE_KEY) != "custom_keyboard_size_preference"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val keyboard = view.findViewById<FlickKeyboardView>(R.id.previewFlickKeyboardView)
        keyboard.setOnTouchListener { _, _ -> true }
        keyboard.applyKeyboardTheme(
            themeMode = AppPreference.theme_mode,
            currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK,
            isDynamicColorEnabled = false,
            customBgColor = AppPreference.custom_theme_bg_color,
            customKeyColor = AppPreference.custom_theme_key_color,
            customSpecialKeyColor = AppPreference.custom_theme_special_key_color,
            customKeyTextColor = AppPreference.custom_theme_key_text_color,
            customSpecialKeyTextColor = AppPreference.custom_theme_special_key_text_color,
            liquidGlassEnable = AppPreference.liquid_glass_preference,
            customBorderEnable = AppPreference.custom_theme_border_enable,
            customBorderColor = AppPreference.custom_theme_border_color,
            liquidGlassKeyAlphaEnable = AppPreference.liquid_glass_key_alpha,
            borderWidth = AppPreference.custom_theme_border_width,
        )
        controls = KeyboardSizingControls(view, savedInstanceState, tenkey = false, modeSpecific = isSumire,
            read = { mode, _ -> KeyboardSizingValues(
                if (isSumire) ModeKeySizing.sumireCenter(mode) else AppPreference.flick_key_text_size_sp ?: 16f,
                if (isSumire) ModeKeySizing.sumireGuide(mode) else (AppPreference.flick_guide_text_size_sp_preference ?: 9).toFloat(),
                (AppPreference.flick_key_width_scale_percent ?: 160).toFloat(),
                (AppPreference.flick_key_height_scale_percent ?: 160).toFloat(),
                (AppPreference.flick_key_icon_scale_percent ?: 80).toFloat(),
                AppPreference.flick_special_key_text_size_sp ?: 12f,
            ) },
            save = { mode, field, value -> when (field) {
                SizingField.CENTER -> if (isSumire) AppPreference.setModeKeySize("sumire", mode, false, value)
                    else AppPreference.flick_key_text_size_sp = value
                SizingField.GUIDE -> if (isSumire) AppPreference.setModeKeySize("sumire", mode, true, value)
                    else AppPreference.flick_guide_text_size_sp_preference = value.toInt()
                SizingField.WIDTH -> AppPreference.flick_key_width_scale_percent = value.toInt()
                SizingField.HEIGHT -> AppPreference.flick_key_height_scale_percent = value.toInt()
                SizingField.ICON -> AppPreference.flick_key_icon_scale_percent = value.toInt()
                SizingField.SPECIAL -> AppPreference.flick_special_key_text_size_sp = value
                SizingField.SPECIAL_WIDTH, SizingField.SPECIAL_HEIGHT -> Unit
            } },
            render = { mode, guides, values ->
                keyboard.applyKeySizing(values.width.toInt(), values.height.toInt(), values.icon.toInt(),
                    values.center, values.special)
                keyboard.setFlickGuideEnabled(guides, allowMultiCharacterLabels = true)
                keyboard.setFlickGuideTextSizeSp(values.guide)
                keyboard.setFlickGuideMaxCodePoints(AppPreference.flick_guide_max_characters_preference ?: 1)
                keyboard.setKeyboard(if (isSumire) KeyboardDefaultLayouts.createFinalLayout(mode,
                    emptyMap(), AppPreference.sumire_input_method, AppPreference.sumire_keyboard_style)
                    else KeyboardDefaultLayouts.defaultLayout())
            },
            reset = { mode ->
                if (isSumire) AppPreference.resetModeKeySizes("sumire", mode)
                if (mode == null) {
                    AppPreference.flick_key_width_scale_percent = 160
                    AppPreference.flick_key_height_scale_percent = 160
                    AppPreference.flick_key_icon_scale_percent = 80
                    AppPreference.flick_special_key_text_size_sp = 12f
                    // Shared legacy text settings also serve custom keyboards.
                    if (!isSumire) {
                        AppPreference.flick_key_text_size_sp = 16f
                        AppPreference.flick_guide_text_size_sp_preference = 9
                    } else {
                        com.kazumaproject.custom_keyboard.data.KeyboardInputMode.entries.forEach { inputMode ->
                            AppPreference.setModeKeySize("sumire", inputMode, false, 16f)
                            AppPreference.setModeKeySize("sumire", inputMode, true, 9f)
                        }
                    }
                }
            },
            inherited = { mode -> AppPreference.modeKeySize("sumire", mode, false) == null ||
                AppPreference.modeKeySize("sumire", mode, true) == null },
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        controls?.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        controls = null
        super.onDestroyView()
    }
}
