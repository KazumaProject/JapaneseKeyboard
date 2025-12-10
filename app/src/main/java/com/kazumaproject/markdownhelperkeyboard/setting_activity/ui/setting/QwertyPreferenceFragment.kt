package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.kazumaproject.markdownhelperkeyboard.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class QwertyPreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_qwerty, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val qwertyButtonMarginPreference =
            findPreference<Preference>("qwerty_button_size_preference")
        qwertyButtonMarginPreference?.apply {
            setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_navigation_setting_to_qwertyMarginSettingFragment)
                true
            }
        }
    }
}
