package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.gojuon_size_setting

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
import com.kazumaproject.gojuon_keyboard.GojuonKeyboardView

class GojuonSizeSettingsFragment : Fragment(R.layout.fragment_gojuon_size_settings) {
    private var controls: KeyboardSizingControls? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val keyboard = view.findViewById<GojuonKeyboardView>(R.id.gojuonSizePreview)
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
        // Keep the preview fixed on the mode selected above, without sending key events.
        fun blockKeyTouches(group: ViewGroup) {
            for (index in 0 until group.childCount) {
                val child = group.getChildAt(index)
                if (child is ViewGroup) blockKeyTouches(child)
                else child.setOnTouchListener { _, _ -> true }
            }
        }
        blockKeyTouches(keyboard)
        controls = KeyboardSizingControls(view, savedInstanceState, tenkey = true, guideControls = false, specialKeyControls = true,
            read = { mode, _ -> KeyboardSizingValues(
                ModeKeySizing.gojuonCenter(mode), 0f,
                AppPreference.gojuon_key_width_scale_percent.toFloat(),
                AppPreference.gojuon_key_height_scale_percent.toFloat(),
                icon = AppPreference.gojuon_special_icon_scale_percent.toFloat(),
                special = AppPreference.gojuon_special_text_size_sp,
                specialWidth = AppPreference.gojuon_special_key_width_scale_percent.toFloat(),
                specialHeight = AppPreference.gojuon_special_key_height_scale_percent.toFloat(),
                centerOverride = AppPreference.modeKeySize("gojuon", mode, false),
            ) },
            save = { mode, field, value -> when (field) {
                SizingField.CENTER -> AppPreference.setModeKeySize("gojuon", mode, false, value)
                SizingField.WIDTH -> AppPreference.gojuon_key_width_scale_percent = value.toInt()
                SizingField.HEIGHT -> AppPreference.gojuon_key_height_scale_percent = value.toInt()
                SizingField.SPECIAL_WIDTH -> AppPreference.gojuon_special_key_width_scale_percent = value.toInt()
                SizingField.SPECIAL_HEIGHT -> AppPreference.gojuon_special_key_height_scale_percent = value.toInt()
                SizingField.ICON -> AppPreference.gojuon_special_icon_scale_percent = value.toInt()
                SizingField.SPECIAL -> AppPreference.gojuon_special_text_size_sp = value
                else -> Unit
            } },
            render = { mode, _, values ->
                keyboard.currentInputMode.set(ModeKeySizing.inputMode(mode))
                keyboard.setInputModeSwitchState()
                keyboard.setModeLetterSizes(mapOf(ModeKeySizing.inputMode(mode) to values.center))
                keyboard.setKeySizeScale(values.width.toInt(), values.height.toInt())
                keyboard.setSpecialKeySizing(values.specialWidth.toInt(), values.specialHeight.toInt(),
                    values.icon.toInt(), values.special)
            },
            reset = { mode ->
                AppPreference.resetModeKeySizes("gojuon", mode)
                if (mode == null) {
                    AppPreference.gojuon_key_width_scale_percent = 100
                    AppPreference.gojuon_key_height_scale_percent = 100
                    AppPreference.gojuon_special_key_width_scale_percent = 100
                    AppPreference.gojuon_special_key_height_scale_percent = 100
                    AppPreference.gojuon_special_icon_scale_percent = 100
                    AppPreference.gojuon_special_text_size_sp = 18f
                }
            },
            inherited = { mode -> AppPreference.modeKeySize("gojuon", mode, false) == null },
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
