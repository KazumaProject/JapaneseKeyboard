package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.kazumaproject.markdownhelperkeyboard.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CustomKeyboardPreferenceFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_custom, rootKey)

        val customKeyboardSizePreference =
            findPreference<Preference>("custom_keyboard_size_preference")

        customKeyboardSizePreference?.apply {
            setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_navigation_setting_to_flickKeyboardSizeSettingsFragment)
                true
            }
        }
    }
}
