package com.kazumaproject.markdownhelperkeyboard.morse

import android.os.Bundle
import androidx.preference.Preference
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.AsyncPreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.navigateSafely
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.applyLegacySearchResultFilterIfNeeded
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MorsePreferenceFragment : AsyncPreferenceFragment() {
    override val preferencesXmlRes = R.xml.pref_morse
    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {
        findPreference<Preference>("morse_maps")?.setOnPreferenceClickListener {
            navigateSafely(R.id.morseMapListFragment); true
        }
        findPreference<Preference>("morse_key_layout")?.setOnPreferenceClickListener {
            navigateSafely(R.id.morseLayoutFragment); true
        }
        applyLegacySearchResultFilterIfNeeded()
    }
}
