package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import android.view.View
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
                navigateSafely(R.id.action_navigation_setting_to_qwertyMarginSettingFragment)
                true
            }
        }

        findPreference<Preference>("qwerty_popup_view_style_preference")?.apply {
            setOnPreferenceClickListener {
                navigateSafely(R.id.action_navigation_setting_to_qwertyPopupStyleSettingFragment)
                true
            }
        }

        findPreference<Preference>(QWERTY_NUMBER_KEY_FLICK_SETTING_PREFERENCE)?.apply {
            setOnPreferenceClickListener {
                navigateSafely(
                    R.id.action_navigation_setting_to_qwertyNumberKeyFlickSettingFragment
                )
                true
            }
        }
    }

    companion object {
        private const val QWERTY_NUMBER_KEY_FLICK_SETTING_PREFERENCE =
            "qwerty_number_key_flick_setting_preference"
    }
}
