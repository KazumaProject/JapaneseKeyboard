package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.tenkey_letter_size_setting

import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit.KeyboardLayoutEditConstraints
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.KeyTextSizePreviewSizing
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.KeyboardSizingControls
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.KeyboardSizingValues
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.ModeKeySizing
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.SizingField
import com.kazumaproject.tenkey.TenKey

class TenKeyCandidateLetterSizeFragment : Fragment(R.layout.fragment_tenkey_candidate_letter_size) {
    private var controls: KeyboardSizingControls? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val keyboard = view.findViewById<TenKey>(R.id.tenkeyLetterSizePreview)
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        keyboard.layoutParams = keyboard.layoutParams.apply {
            val widthPercent = KeyboardLayoutEditConstraints.normalizeWidthPercent(
                (if (landscape) AppPreference.keyboard_width_landscape else AppPreference.keyboard_width) ?: 100,
            )
            width = if (widthPercent == 100) ViewGroup.LayoutParams.MATCH_PARENT
                else (resources.displayMetrics.widthPixels * widthPercent / 100f).toInt()
            val alignEnd = (if (landscape) AppPreference.keyboard_position_landscape else AppPreference.keyboard_position) != false
            (this as? LinearLayout.LayoutParams)?.gravity = if (alignEnd) Gravity.END else Gravity.START
            height = KeyTextSizePreviewSizing.heightPx(
                (if (landscape) AppPreference.keyboard_height_landscape else AppPreference.keyboard_height)
                    ?: if (landscape) 220 else 280, resources.displayMetrics.density,
            )
        }
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
        keyboard.setUseThreeStateKeyboard(AppPreference.tenkey_use_three_state_keyboard_preference,
            AppPreference.tenkey_number_symbol_key_gap_preference)
        // Preview always shows the requested TenKey mode, including its numeric keys.
        keyboard.setUseQwertyNumberWhenThreeStateOff(false)
        keyboard.setOnTouchListener { _, _ -> true }
        controls = KeyboardSizingControls(view, savedInstanceState, tenkey = true,
            read = { mode, guides -> KeyboardSizingValues(
                ModeKeySizing.tenkeyCenter(mode, guides), ModeKeySizing.tenkeyGuide(mode),
                (AppPreference.tenkey_key_width_scale_percent ?: 100).toFloat(),
                (AppPreference.tenkey_key_height_scale_percent ?: 100).toFloat(),
                centerOverride = AppPreference.modeKeySize("tenkey", mode, false),
                guideOverride = AppPreference.modeKeySize("tenkey", mode, true),
            ) },
            save = { mode, field, value -> when (field) {
                SizingField.CENTER -> AppPreference.setModeKeySize("tenkey", mode, false, value)
                SizingField.GUIDE -> AppPreference.setModeKeySize("tenkey", mode, true, value)
                SizingField.WIDTH -> AppPreference.tenkey_key_width_scale_percent = value.toInt()
                SizingField.HEIGHT -> AppPreference.tenkey_key_height_scale_percent = value.toInt()
                else -> Unit
            } },
            render = { mode, guides, values ->
                keyboard.setKeyLetterSizeDelta((AppPreference.key_letter_size ?: 0f).toInt())
                keyboard.setCurrentMode(ModeKeySizing.inputMode(mode))
                keyboard.setFlickGuideEnabled(guides, guides, guides)
                keyboard.setModeLetterSizes(
                    values.centerOverride?.let { mapOf(ModeKeySizing.inputMode(mode) to it) } ?: emptyMap(),
                    values.guideOverride?.let { mapOf(ModeKeySizing.inputMode(mode) to it) } ?: emptyMap(),
                )
                keyboard.setKeySizeScale(values.width.toInt(), values.height.toInt())
            },
            reset = { mode ->
                AppPreference.resetModeKeySizes("tenkey", mode)
                if (mode == null) {
                    AppPreference.key_letter_size = 0f
                    AppPreference.tenkey_key_width_scale_percent = 100
                    AppPreference.tenkey_key_height_scale_percent = 100
                }
            },
            inherited = { mode -> AppPreference.modeKeySize("tenkey", mode, false) == null ||
                AppPreference.modeKeySize("tenkey", mode, true) == null },
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
