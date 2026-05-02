package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
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
                navigateSafely(R.id.action_navigation_setting_to_tenKeyCandidateLetterSizeFragment)
                true
            }
        }

        findPreference<Preference>("tenkey_popup_view_style_preference")?.apply {
            setOnPreferenceClickListener {
                navigateSafely(R.id.action_navigation_setting_to_tenKeyPopupStyleSettingFragment)
                true
            }
        }
    }
}
