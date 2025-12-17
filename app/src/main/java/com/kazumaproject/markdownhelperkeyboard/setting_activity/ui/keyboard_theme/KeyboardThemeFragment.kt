package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.color.colorChooser
import com.google.android.material.color.DynamicColors
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class KeyboardThemeFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var appPreference: AppPreference

    companion object {
        // System Theme Keys
        private const val PREF_KEY_DEFAULT = "theme_default"
        private const val PREF_KEY_ROUND_CORNER = "round_corner_keyboard_preference"

        // Liquid Glass Keys
        private const val PREF_KEY_LIQUID_GLASS = "liquid_glass_preference"
        private const val PREF_KEY_LIQUID_GLASS_BLUR = "liquid_glass_blur_preference"
        private const val PREF_KEY_LIQUID_GLASS_KEY_ALPHA = "liquid_glass_key_alpha_preference"

        // Custom Theme Keys
        private const val PREF_KEY_CUSTOM = "theme_custom"
        private const val PREF_KEY_CUSTOM_BG = "theme_custom_bg_color"
        private const val PREF_KEY_CUSTOM_KEY = "theme_custom_key_color"
        private const val PREF_KEY_CUSTOM_SPECIAL_KEY = "theme_custom_special_key_color"
        private const val PREF_KEY_CUSTOM_TEXT = "theme_custom_key_text_color"
        private const val PREF_KEY_CUSTOM_SPECIAL_TEXT = "theme_custom_special_key_text_color"

        // Custom Border Keys
        private const val PREF_KEY_CUSTOM_BORDER_ENABLE = "theme_custom_border_enable"
        private const val PREF_KEY_CUSTOM_BORDER_COLOR = "theme_custom_border_color"
        private const val PREF_KEY_CUSTOM_BORDER_WIDTH = "theme_custom_border_width"

        // Custom Input Text Keys
        private const val CATEGORY_KEY_CUSTOM_INPUT = "category_custom_input"
        private const val PREF_KEY_CUSTOM_INPUT_ENABLE = "theme_custom_input_color_enable"
        private const val PREF_KEY_CUSTOM_PRE_EDIT_BG = "theme_custom_pre_edit_bg_color"
        private const val PREF_KEY_CUSTOM_PRE_EDIT_TEXT = "theme_custom_pre_edit_text_color"
        private const val PREF_KEY_CUSTOM_POST_EDIT_BG = "theme_custom_post_edit_bg_color"
        private const val PREF_KEY_CUSTOM_POST_EDIT_TEXT = "theme_custom_post_edit_text_color"

        // Modes
        private const val MODE_DEFAULT = "default"
        private const val MODE_CUSTOM = "custom"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        // -------------------------------------------------------
        // System Category
        // -------------------------------------------------------
        val systemCategory = PreferenceCategory(context).apply {
            title = getString(R.string.theme_category_system)
        }
        screen.addPreference(systemCategory)

        // Round Corner Preference
        val roundCornerPref = SwitchPreferenceCompat(context).apply {
            key = PREF_KEY_ROUND_CORNER
            title = getString(R.string.pref_round_corner_keyboard_title)
            summary = getString(R.string.pref_round_corner_keyboard_summary)
            setDefaultValue(false)
        }
        systemCategory.addPreference(roundCornerPref)

        // Liquid Glass Settings
        val liquidGlassSwitch = SwitchPreferenceCompat(context).apply {
            key = PREF_KEY_LIQUID_GLASS
            title = getString(R.string.liquid_glass_effect)
            summary = getString(R.string.enable_glass_blur_effect)
            setDefaultValue(false)
        }
        systemCategory.addPreference(liquidGlassSwitch)

        val liquidGlassBlurPref = SeekBarPreference(context).apply {
            key = PREF_KEY_LIQUID_GLASS_BLUR
            title = getString(R.string.blur_radius)
            min = 0
            max = 255
            setDefaultValue(220)
            showSeekBarValue = true
        }
        systemCategory.addPreference(liquidGlassBlurPref)

        val liquidGlassKeyAlphaPref = SeekBarPreference(context).apply {
            key = PREF_KEY_LIQUID_GLASS_KEY_ALPHA
            title = getString(R.string.key_transparency)
            min = 0
            max = 255
            setDefaultValue(255)
            showSeekBarValue = true
        }
        systemCategory.addPreference(liquidGlassKeyAlphaPref)

        // Liquid Glass Dependency Logic
        val isLiquidGlassEnabled = liquidGlassSwitch.isChecked
        liquidGlassBlurPref.isEnabled = isLiquidGlassEnabled
        liquidGlassKeyAlphaPref.isEnabled = isLiquidGlassEnabled

        liquidGlassSwitch.setOnPreferenceChangeListener { _, newValue ->
            val isEnabled = newValue as Boolean
            liquidGlassBlurPref.isEnabled = isEnabled
            liquidGlassKeyAlphaPref.isEnabled = isEnabled
            true
        }

        // Default Theme Checkbox
        val defaultPref = CheckBoxPreference(context).apply {
            key = PREF_KEY_DEFAULT
            title = getString(R.string.theme_default)
            summary = getString(R.string.keyboard_theme_summary)
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                handleThemeSelection(MODE_DEFAULT)
                if (DynamicColors.isDynamicColorAvailable()) {
                    showColorPickerDialog(
                        initialColor = appPreference.seedColor,
                        onColorSelected = { color ->
                            appPreference.seedColor = color
                            requireActivity().recreate()
                        }
                    )
                }
                true
            }
        }
        systemCategory.addPreference(defaultPref)


        // -------------------------------------------------------
        // Custom Category (Keyboard Appearance)
        // -------------------------------------------------------
        val customCategory = PreferenceCategory(context).apply {
            title = getString(R.string.theme_category_custom)
        }
        screen.addPreference(customCategory)

        // Custom Theme Mode Selection
        val customPref = CheckBoxPreference(context).apply {
            key = PREF_KEY_CUSTOM
            title = getString(R.string.theme_custom)
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                handleThemeSelection(MODE_CUSTOM)
                true
            }
        }
        customCategory.addPreference(customPref)

        // Custom Background Color
        val customBgPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_BG,
            getString(R.string.theme_custom_bg_color)
        ) { appPreference.custom_theme_bg_color }
        customCategory.addPreference(customBgPref)

        // Custom Key Color
        val customKeyPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_KEY,
            getString(R.string.theme_custom_key_color)
        ) { appPreference.custom_theme_key_color }
        customCategory.addPreference(customKeyPref)

        // Custom Special Key Color
        val customSpecialKeyPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_SPECIAL_KEY,
            getString(R.string.theme_custom_special_key_color)
        ) { appPreference.custom_theme_special_key_color }
        customCategory.addPreference(customSpecialKeyPref)

        // Custom Text Color
        val customTextPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_TEXT,
            getString(R.string.theme_custom_key_text_color)
        ) { appPreference.custom_theme_key_text_color }
        customCategory.addPreference(customTextPref)

        // Custom Special Text Color
        val customSpecialTextPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_SPECIAL_TEXT,
            getString(R.string.theme_custom_special_key_text_color)
        ) { appPreference.custom_theme_special_key_text_color }
        customCategory.addPreference(customSpecialTextPref)

        // Custom Border Settings
        val customBorderEnablePref = SwitchPreferenceCompat(context).apply {
            key = PREF_KEY_CUSTOM_BORDER_ENABLE
            title = getString(R.string.custom_border_enable)
            setDefaultValue(false)
        }
        customCategory.addPreference(customBorderEnablePref)

        val customBorderColorPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_BORDER_COLOR,
            getString(R.string.custom_border_color)
        ) { appPreference.custom_theme_border_color }
        customCategory.addPreference(customBorderColorPref)

        // Custom Border Width (修正: 初期値の設定とリスナーの追加)
        val customBorderWidthPref = SeekBarPreference(context).apply {
            key = PREF_KEY_CUSTOM_BORDER_WIDTH
            title = getString(R.string.custom_border_width)
            min = 1
            max = 8
            showSeekBarValue = true

            // AppPreferenceの現在の値をUIにセット
            value = appPreference.custom_theme_border_width

            // 値が変更されたときにAppPreferenceを更新するリスナー
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val width = newValue as Int
                appPreference.custom_theme_border_width = width
                true
            }
        }
        customCategory.addPreference(customBorderWidthPref)

        // Link Border Color & Width capability to switch
        val isBorderEnabled = customBorderEnablePref.isChecked
        customBorderColorPref.isEnabled = isBorderEnabled
        customBorderWidthPref.isEnabled = isBorderEnabled

        customBorderEnablePref.setOnPreferenceChangeListener { _, newValue ->
            val isEnabled = newValue as Boolean
            customBorderColorPref.isEnabled = isEnabled
            customBorderWidthPref.isEnabled = isEnabled
            true
        }


        // -------------------------------------------------------
        // Input Text Category
        // -------------------------------------------------------
        val inputCategory = PreferenceCategory(context).apply {
            key = CATEGORY_KEY_CUSTOM_INPUT
            title = getString(R.string.composing_text)
        }
        screen.addPreference(inputCategory)

        // 0. Enable Custom Input Colors
        val inputColorEnablePref = SwitchPreferenceCompat(context).apply {
            key = PREF_KEY_CUSTOM_INPUT_ENABLE
            title = getString(R.string.custom_input_color_enable_title)
            setDefaultValue(false)
        }
        inputCategory.addPreference(inputColorEnablePref)

        // 1. Pre-Edit Background
        val preEditBgPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_PRE_EDIT_BG,
            getString(R.string.pre_edit)
        ) { appPreference.custom_theme_pre_edit_bg_color }
        inputCategory.addPreference(preEditBgPref)

        // 2. Pre-Edit Text Color
        val preEditTextPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_PRE_EDIT_TEXT,
            getString(R.string.pre_edit_text)
        ) { appPreference.custom_theme_pre_edit_text_color }
        inputCategory.addPreference(preEditTextPref)

        // 3. Post-Edit/Highlight Background
        val postEditBgPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_POST_EDIT_BG,
            getString(R.string.conversion_text_color)
        ) { appPreference.custom_theme_post_edit_bg_color }
        inputCategory.addPreference(postEditBgPref)

        // 4. Post-Edit/Highlight Text Color
        val postEditTextPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_POST_EDIT_TEXT,
            getString(R.string.conversion_text_color_)
        ) { appPreference.custom_theme_post_edit_text_color }
        inputCategory.addPreference(postEditTextPref)

        // Link Color Preferences to Enable Switch
        fun updateInputColorPrefsState(enabled: Boolean) {
            preEditBgPref.isEnabled = enabled
            preEditTextPref.isEnabled = enabled
            postEditBgPref.isEnabled = enabled
            postEditTextPref.isEnabled = enabled
        }

        // Initialize state
        updateInputColorPrefsState(inputColorEnablePref.isChecked)

        // Listener
        inputColorEnablePref.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean
            updateInputColorPrefsState(enabled)
            true
        }

        preferenceScreen = screen

        // Initialize state based on current preference
        updateCheckStates(appPreference.theme_mode)
        updateCustomColorsVisibility(appPreference.theme_mode == MODE_CUSTOM)
    }

    private fun createColorPreference(
        context: Context,
        key: String,
        titleStr: String,
        colorProvider: () -> Int
    ): Preference {
        return Preference(context).apply {
            this.key = key
            title = titleStr
            summary = String.format("#%08X", colorProvider())
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                showColorPickerDialog(
                    initialColor = colorProvider(),
                    onColorSelected = { color ->
                        saveCustomColor(key, color)
                        summary = String.format("#%08X", color)
                    }
                )
                true
            }
        }
    }

    private fun handleThemeSelection(mode: String) {
        appPreference.theme_mode = mode
        updateCheckStates(mode)
        updateCustomColorsVisibility(mode == MODE_CUSTOM)
    }

    private fun updateCheckStates(selectedMode: String) {
        findPreference<CheckBoxPreference>(PREF_KEY_DEFAULT)?.isChecked =
            selectedMode == MODE_DEFAULT
        findPreference<CheckBoxPreference>(PREF_KEY_CUSTOM)?.isChecked = selectedMode == MODE_CUSTOM
    }

    private fun updateCustomColorsVisibility(isVisible: Boolean) {
        // Base Custom Colors
        findPreference<Preference>(PREF_KEY_CUSTOM_BG)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_KEY)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_SPECIAL_KEY)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_TEXT)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_SPECIAL_TEXT)?.isVisible = isVisible

        // Border Settings
        findPreference<Preference>(PREF_KEY_CUSTOM_BORDER_ENABLE)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_BORDER_COLOR)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_BORDER_WIDTH)?.isVisible = isVisible

        // Input Category Visibility
        //findPreference<PreferenceCategory>(CATEGORY_KEY_CUSTOM_INPUT)?.isVisible = isVisible
    }

    private fun saveCustomColor(key: String, color: Int) {
        when (key) {
            // Base Colors
            PREF_KEY_CUSTOM_BG -> appPreference.custom_theme_bg_color = color
            PREF_KEY_CUSTOM_KEY -> appPreference.custom_theme_key_color = color
            PREF_KEY_CUSTOM_SPECIAL_KEY -> appPreference.custom_theme_special_key_color = color
            PREF_KEY_CUSTOM_TEXT -> appPreference.custom_theme_key_text_color = color
            PREF_KEY_CUSTOM_SPECIAL_TEXT -> appPreference.custom_theme_special_key_text_color =
                color

            // Border Color
            PREF_KEY_CUSTOM_BORDER_COLOR -> appPreference.custom_theme_border_color = color

            // Input Colors
            PREF_KEY_CUSTOM_PRE_EDIT_BG -> appPreference.custom_theme_pre_edit_bg_color = color
            PREF_KEY_CUSTOM_PRE_EDIT_TEXT -> appPreference.custom_theme_pre_edit_text_color = color
            PREF_KEY_CUSTOM_POST_EDIT_BG -> appPreference.custom_theme_post_edit_bg_color = color
            PREF_KEY_CUSTOM_POST_EDIT_TEXT -> appPreference.custom_theme_post_edit_text_color =
                color
        }
    }

    @SuppressLint("CheckResult")
    private fun showColorPickerDialog(initialColor: Int, onColorSelected: (Int) -> Unit) {
        MaterialDialog(requireContext()).show {
            title(text = getString(R.string.keyboard_theme_dialog_title_2))
            colorChooser(
                colors = intArrayOf(
                    0x00000000,
                    ContextCompat.getColor(requireContext(), com.kazumaproject.core.R.color.blue),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.char_in_edit_color
                    ),
                    ContextCompat.getColor(requireContext(), com.kazumaproject.core.R.color.orange),
                    ContextCompat.getColor(requireContext(), com.kazumaproject.core.R.color.violet),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.violet_light
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.violet_dark
                    ),
                    ContextCompat.getColor(requireContext(), com.kazumaproject.core.R.color.mint),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.mint_light
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.mint_dark
                    ),
                    ContextCompat.getColor(requireContext(), com.kazumaproject.core.R.color.sky),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.sky_light
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.sky_dark
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.orange2
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.orange_light
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.orange_dark
                    ),
                    android.graphics.Color.WHITE,
                    android.graphics.Color.BLACK,
                    android.graphics.Color.DKGRAY,
                    android.graphics.Color.LTGRAY,
                    "#1C1C1E".toColorInt(),
                    "#1E2022".toColorInt()
                ),
                initialSelection = initialColor,
                allowCustomArgb = true
            ) { _, color ->
                onColorSelected(color)
            }
            positiveButton(android.R.string.ok)
            negativeButton(android.R.string.cancel)
        }
    }
}
