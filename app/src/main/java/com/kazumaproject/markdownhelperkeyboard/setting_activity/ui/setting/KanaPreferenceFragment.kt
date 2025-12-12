package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import androidx.navigation.fragment.findNavController
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
                findNavController().navigate(R.id.action_navigation_setting_to_tenKeyCandidateLetterSizeFragment)
                true
            }
        }
    }
}
