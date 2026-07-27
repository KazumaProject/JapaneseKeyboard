package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.kazumaproject.markdownhelperkeyboard.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class KanaPreferenceFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_kana, rootKey)

        val letterSizePreference =
            findPreference<Preference>("kana_keyboard_letter_size_preference")
        letterSizePreference?.apply {
            setOnPreferenceClickListener {
                navigateSafely(R.id.tenKeyCandidateLetterSizeFragment)
                true
            }
        }

        findPreference<Preference>("tenkey_popup_view_style_preference")?.apply {
            setOnPreferenceClickListener {
                navigateSafely(R.id.tenKeyPopupStyleSettingFragment)
                true
            }
        }

        val englishQwertyPreference =
            findPreference<SwitchPreferenceCompat>("tenkey_kana_english_qwerty_preference")
        val englishGuidePreference =
            findPreference<SwitchPreferenceCompat>("tenkey_keymap_guide_english")

        fun updateEnglishGuideAvailability(usesQwerty: Boolean) {
            englishGuidePreference?.apply {
                isEnabled = !usesQwerty
                setSummary(
                    if (usesQwerty) {
                        R.string.keymap_guide_qwerty_disabled_summary
                    } else {
                        R.string.tenkey_keymap_guide_summary
                    }
                )
            }
        }

        updateEnglishGuideAvailability(englishQwertyPreference?.isChecked == true)
        englishQwertyPreference?.setOnPreferenceChangeListener { _, newValue ->
            updateEnglishGuideAvailability(newValue as Boolean)
            true
        }

        applyLegacySearchResultFilterIfNeeded()
    }
}
