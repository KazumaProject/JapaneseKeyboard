package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
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
class CommonPreferenceFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var appPreference: AppPreference

    private var count = 0

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_common, rootKey)

        val packageInfo = requireContext().packageManager.getPackageInfo(
            requireContext().packageName, 0
        )

        val languageSwitchPreference =
            findPreference<SwitchPreferenceCompat>("app_setting_language_preference")
        languageSwitchPreference?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                val state = newValue as Boolean
                if (state) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ja"))
                } else {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                }
                true
            }
        }

        val candidateColumnListPreference =
            findPreference<ListPreference>("candidate_column_preference")
        candidateColumnListPreference?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                if (newValue is String) {
                    when (newValue) {
                        "1" -> {
                            appPreference.candidate_view_height_dp = 110
                        }

                        "2" -> {
                            appPreference.candidate_view_height_dp = 165
                        }

                        "3" -> {
                            appPreference.candidate_view_height_dp = 230
                        }
                    }
                }
                true
            }
        }

        val appVersionPreference = findPreference<Preference>("app_version_preference")
        appVersionPreference?.apply {
            summary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                "version name: ${packageInfo.versionName}\nversion code: ${packageInfo.longVersionCode}"
            } else {
                "version name: ${packageInfo.versionName}\nversion code: ${packageInfo.versionCode}"
            }
            setOnPreferenceClickListener {
                count += 1
                true
            }
        }

        val customRomajiPreference = findPreference<Preference>("custom_romaji_preference")
        customRomajiPreference?.setOnPreferenceClickListener {
            findNavController().navigate(
                R.id.action_navigation_setting_to_romajiMapFragment
            )
            true
        }

        val shortCutToolbarItemSettingPreference = findPreference<Preference>(
            "shortcut_toolbar_item_preference"
        )
        shortCutToolbarItemSettingPreference?.apply {
            setOnPreferenceClickListener {
                findNavController().navigate(
                    R.id.action_navigation_setting_to_shortcutSettingFragment
                )
                true
            }
        }

        val candidateTabOrderPreference =
            findPreference<Preference>("candidate_tab_order_preference")
        candidateTabOrderPreference?.apply {
            setOnPreferenceClickListener {
                findNavController().navigate(
                    R.id.action_navigation_setting_to_candidateTabOrderFragment
                )
                true
            }
        }

        val keyboardSelectionPreference =
            findPreference<Preference>("keyboard_selection_preference")

        keyboardSelectionPreference?.setOnPreferenceClickListener {
            findNavController().navigate(
                R.id.action_navigation_setting_to_keyboardSelectionFragment
            )
            true
        }

        val keyboardLetterSizePreference =
            findPreference<Preference>("keyboard_key_letter_size_fragment_preference")

        keyboardLetterSizePreference?.setOnPreferenceClickListener {
            findNavController().navigate(
                R.id.action_navigation_setting_to_keyCandidateLetterSizeFragment
            )
            true
        }

        val keyboardSizeLandscapePreference =
            findPreference<Preference>("keyboard_screen_landscape_preference")

        keyboardSizeLandscapePreference?.setOnPreferenceClickListener {
            findNavController().navigate(
                R.id.action_navigation_setting_to_keyboardSizeLandscapeFragment
            )
            true
        }

        val candidateHeightFragmentSetting =
            findPreference<Preference>("candidate_view_height_setting_fragment_preference")
        candidateHeightFragmentSetting?.apply {
            setOnPreferenceClickListener {
                findNavController().navigate(
                    R.id.action_navigation_setting_to_candidateViewHeightSettingFragment
                )
                true
            }
        }

        val candidateHeightLandscapeFragmentSetting =
            findPreference<Preference>("candidate_view_height_landscape_setting_fragment_preference")
        candidateHeightLandscapeFragmentSetting?.apply {
            setOnPreferenceClickListener {
                findNavController().navigate(
                    R.id.action_navigation_setting_to_candidateHeightLandscapeSettingFragment
                )
                true
            }
        }

        val clipBoardHistoryPreference =
            findPreference<Preference>("clipboard_history_preference_fragment")
        clipBoardHistoryPreference?.apply {
            setOnPreferenceClickListener {
                findNavController().navigate(
                    R.id.action_navigation_setting_to_clipboardHistoryFragment
                )
                true
            }
        }

        val symbolKeyboardOrderPreference = findPreference<ListPreference>("symbol_mode_preference")
        symbolKeyboardOrderPreference?.apply {
            summary = when (value) {
                "EMOJI" -> getString(R.string.emoji)
                "EMOTICON" -> getString(R.string.emoticon)
                "SYMBOL" -> getString(R.string.symbol)
                "CLIPBOARD" -> getString(R.string.clipboard_history)
                else -> getString(R.string.choose_initial_symbol_keyboard_open)
            }
            setOnPreferenceChangeListener { _, newValue ->
                summary = when (newValue) {
                    "EMOJI" -> getString(R.string.emoji)
                    "EMOTICON" -> getString(R.string.emoticon)
                    "SYMBOL" -> getString(R.string.symbol)
                    "CLIPBOARD" -> getString(R.string.clipboard_history)
                    else -> getString(R.string.choose_initial_symbol_keyboard_open)
                }
                true
            }
        }

        findPreference<SeekBarPreference>("flick_sensitivity_preference")?.apply {
            summary = when (this.value) {
                in 0..50 -> getString(R.string.sensitivity_very_high)
                in 51..90 -> getString(R.string.sensitivity_high)
                in 91..110 -> getString(R.string.sensitivity_normal)
                in 111..150 -> getString(R.string.sensitivity_less)
                in 151..200 -> getString(R.string.sensitivity_low)
                else -> ""
            }
            setOnPreferenceChangeListener { pref, newValue ->
                val sbp = pref as SeekBarPreference
                val raw = (newValue as Int)
                val inc = sbp.seekBarIncrement
                val rounded = (raw + inc / 2) / inc * inc
                summary = when (rounded) {
                    in 0..50 -> getString(R.string.sensitivity_very_high)
                    in 51..90 -> getString(R.string.sensitivity_high)
                    in 91..110 -> getString(R.string.sensitivity_normal)
                    in 111..150 -> getString(R.string.sensitivity_less)
                    in 151..200 -> getString(R.string.sensitivity_low)
                    else -> ""
                }
                return@setOnPreferenceChangeListener if (rounded != raw) {
                    sbp.value = rounded
                    false
                } else {
                    true
                }
            }
        }

        val keyboardUndoEnablePreference =
            findPreference<SwitchPreferenceCompat>("undo_enable_preference")
        keyboardUndoEnablePreference?.apply {
            appPreference.undo_enable_preference?.let {
                this.summary = if (it) {
                    resources.getString(R.string.undo_enable_summary_on)
                } else {
                    resources.getString(R.string.undo_enable_summary_off)
                }
            }
            this.setOnPreferenceChangeListener { _, newValue ->
                this.summary = if (newValue == true) {
                    resources.getString(R.string.undo_enable_summary_on)
                } else {
                    resources.getString(R.string.undo_enable_summary_off)
                }
                true
            }
        }

        val keyboardSettingPreference = findPreference<Preference>("keyboard_screen_preference")

        keyboardSettingPreference?.setOnPreferenceClickListener {
            findNavController().navigate(
                R.id.action_navigation_setting_to_keyboardSettingFragment
            )
            true
        }

        val openSourcePreference = findPreference<Preference>("preference_open_source")

        openSourcePreference?.setOnPreferenceClickListener {
            findNavController().navigate(
                R.id.action_navigation_dashboard_to_openSourceFragment
            )
            true
        }

        val seedColorPickerPreference =
            findPreference<Preference>("keyboard_theme_fragment_preference")
        seedColorPickerPreference?.apply {
            isVisible = DynamicColors.isDynamicColorAvailable()
            setOnPreferenceClickListener {
                showColorPickerDialog()
                true
            }
        }
    }

    @SuppressLint("CheckResult")
    private fun showColorPickerDialog() {
        val initialColor = appPreference.seedColor
        MaterialDialog(requireContext()).show {
            title(text = getString(R.string.keyboard_theme_dialog_title))
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
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.mint
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.mint_light
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.mint_dark
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.sky
                    ),
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
                ),
                initialSelection = initialColor,
                allowCustomArgb = true
            ) { _, color ->
                appPreference.seedColor = color
                requireActivity().recreate()
            }
            positiveButton(android.R.string.ok)
            negativeButton(android.R.string.cancel)
        }
    }
}
