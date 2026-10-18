package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import androidx.preference.Preference
import com.kazumaproject.markdownhelperkeyboard.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GojuonPreferenceFragment : AsyncPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_gojuon

    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {
        findPreference<Preference>("gojuon_keyboard_size_preference")?.setOnPreferenceClickListener {
            navigateSafely(R.id.gojuonSizeSettingsFragment)
            true
        }
        applyLegacySearchResultFilterIfNeeded()
    }
}
