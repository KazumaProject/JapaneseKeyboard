package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.kazumaproject.markdownhelperkeyboard.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HardwareKeyboardPreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_hardware_keyboard, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        findPreference<Preference>("physical_keyboard_shortcut_setting_preference")
            ?.setOnPreferenceClickListener {
                navigateSafely(R.id.physicalKeyboardShortcutListFragment)
                true
            }
    }
}
