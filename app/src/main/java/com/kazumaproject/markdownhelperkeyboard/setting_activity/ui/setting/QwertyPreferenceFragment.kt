package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.kazumaproject.markdownhelperkeyboard.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
open class QwertyPreferenceFragment : PreferenceFragmentCompat() {
    protected open val preferenceResource: Int = R.xml.pref_qwerty

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(preferenceResource, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val qwertyButtonMarginPreference =
            findPreference<Preference>("qwerty_button_size_preference")
        qwertyButtonMarginPreference?.apply {
            setOnPreferenceClickListener {
                navigateSafely(R.id.qwertyMarginSettingFragment)
                true
            }
        }

        findPreference<Preference>("qwerty_popup_view_style_preference")?.apply {
            setOnPreferenceClickListener {
                navigateSafely(R.id.qwertyPopupStyleSettingFragment)
                true
            }
        }

        findPreference<Preference>(QWERTY_NUMBER_KEY_FLICK_SETTING_PREFERENCE)?.apply {
            setOnPreferenceClickListener {
                navigateSafely(
                    R.id.qwertyNumberKeyFlickSettingFragment
                )
                true
            }
        }

        applyLegacySearchResultFilterIfNeeded()
    }

    companion object {
        private const val QWERTY_NUMBER_KEY_FLICK_SETTING_PREFERENCE =
            "qwerty_number_key_flick_setting_preference"
    }
}

@AndroidEntryPoint
class QwertyEnglishPreferenceFragment : QwertyPreferenceFragment() {
    override val preferenceResource: Int = R.xml.pref_qwerty_english
}

@AndroidEntryPoint
class QwertyRomajiPreferenceFragment : QwertyPreferenceFragment() {
    override val preferenceResource: Int = R.xml.pref_qwerty_romaji
}
