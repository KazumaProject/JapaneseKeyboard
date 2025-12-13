package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
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
        private const val PREF_KEY_DEFAULT = "theme_default"
        private const val PREF_KEY_CUSTOM = "theme_custom"
        private const val PREF_KEY_CUSTOM_BG = "theme_custom_bg_color"
        private const val PREF_KEY_CUSTOM_KEY = "theme_custom_key_color"
        private const val PREF_KEY_CUSTOM_SPECIAL_KEY = "theme_custom_special_key_color"
        private const val PREF_KEY_CUSTOM_TEXT = "theme_custom_key_text_color"
        private const val PREF_KEY_CUSTOM_SPECIAL_TEXT = "theme_custom_special_key_text_color"

        private const val PREF_KEY_ROUND_CORNER = "round_corner_keyboard_preference"

        private const val MODE_DEFAULT = "default"
        private const val MODE_CUSTOM = "custom"

        private const val PREF_KEY_LIQUID_GLASS = "liquid_glass_preference"
        private const val PREF_KEY_LIQUID_GLASS_BLUR = "liquid_glass_blur_preference"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        // System Category
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

        // -------------------------------------------------------
        // Liquid Glass 設定 (Manual Dependency Implementation)
        // -------------------------------------------------------

        // 1. スイッチを作成
        val liquidGlassSwitch = SwitchPreferenceCompat(context).apply {
            key = PREF_KEY_LIQUID_GLASS
            title = getString(R.string.liquid_glass_effect)
            summary = getString(R.string.enable_glass_blur_effect)
            setDefaultValue(false)
        }
        systemCategory.addPreference(liquidGlassSwitch)

        // 2. シークバーを作成 (dependencyプロパティは使用しない)
        val liquidGlassBlurPref = SeekBarPreference(context).apply {
            key = PREF_KEY_LIQUID_GLASS_BLUR
            title = getString(R.string.blur_radius)
            min = 0
            max = 255
            setDefaultValue(220)
            showSeekBarValue = true
        }
        systemCategory.addPreference(liquidGlassBlurPref)

        // 3. 手動で依存関係を制御する (クラッシュ回避策)
        // 初期状態の同期: スイッチがOFFならシークバーを無効化(グレーアウト)
        liquidGlassBlurPref.isEnabled = liquidGlassSwitch.isChecked

        // リスナーを設定: スイッチが切り替わったらシークバーの状態を変更
        liquidGlassSwitch.setOnPreferenceChangeListener { _, newValue ->
            val isEnabled = newValue as Boolean
            liquidGlassBlurPref.isEnabled = isEnabled
            true // 設定変更を受け入れる
        }
        // -------------------------------------------------------


        // Default Theme
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

        // Custom Category
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

        // Custom Color Preferences
        val customBgPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_BG,
            getString(R.string.theme_custom_bg_color)
        ) {
            appPreference.custom_theme_bg_color
        }
        customCategory.addPreference(customBgPref)

        val customKeyPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_KEY,
            getString(R.string.theme_custom_key_color)
        ) {
            appPreference.custom_theme_key_color
        }
        customCategory.addPreference(customKeyPref)

        val customSpecialKeyPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_SPECIAL_KEY,
            getString(R.string.theme_custom_special_key_color)
        ) {
            appPreference.custom_theme_special_key_color
        }
        customCategory.addPreference(customSpecialKeyPref)

        val customTextPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_TEXT,
            getString(R.string.theme_custom_key_text_color)
        ) {
            appPreference.custom_theme_key_text_color
        }
        customCategory.addPreference(customTextPref)

        val customSpecialTextPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_SPECIAL_TEXT,
            getString(R.string.theme_custom_special_key_text_color)
        ) {
            appPreference.custom_theme_special_key_text_color
        }
        customCategory.addPreference(customSpecialTextPref)

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
        // requireActivity().recreate()
    }

    private fun updateCheckStates(selectedMode: String) {
        findPreference<CheckBoxPreference>(PREF_KEY_DEFAULT)?.isChecked =
            selectedMode == MODE_DEFAULT
        findPreference<CheckBoxPreference>(PREF_KEY_CUSTOM)?.isChecked = selectedMode == MODE_CUSTOM
    }

    private fun updateCustomColorsVisibility(isVisible: Boolean) {
        findPreference<Preference>(PREF_KEY_CUSTOM_BG)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_KEY)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_SPECIAL_KEY)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_TEXT)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_SPECIAL_TEXT)?.isVisible = isVisible
    }

    private fun saveCustomColor(key: String, color: Int) {
        when (key) {
            PREF_KEY_CUSTOM_BG -> appPreference.custom_theme_bg_color = color
            PREF_KEY_CUSTOM_KEY -> appPreference.custom_theme_key_color = color
            PREF_KEY_CUSTOM_SPECIAL_KEY -> appPreference.custom_theme_special_key_color = color
            PREF_KEY_CUSTOM_TEXT -> appPreference.custom_theme_key_text_color = color
            PREF_KEY_CUSTOM_SPECIAL_TEXT -> appPreference.custom_theme_special_key_text_color =
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
                    android.graphics.Color.LTGRAY
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
