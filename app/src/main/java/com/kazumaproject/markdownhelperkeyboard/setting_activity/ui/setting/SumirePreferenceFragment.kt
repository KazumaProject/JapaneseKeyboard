package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.kazumaproject.markdownhelperkeyboard.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SumirePreferenceFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_sumire, rootKey)

        val sumireStylePreference =
            findPreference<ListPreference>("sumire_keyboard_style_preference")
        sumireStylePreference?.apply {
            val originalEntries = this.entries
            val originalEntryValues = this.entryValues
            setOnPreferenceClickListener {
                entries = originalEntries.dropLast(1).toTypedArray()
                entryValues = originalEntryValues.dropLast(1).toTypedArray()
                return@setOnPreferenceClickListener true
            }
            summary = entries[findIndexOfValue(value)].toString()
            setOnPreferenceChangeListener { preference, newValue ->
                val listPreference = preference as ListPreference
                val index = listPreference.findIndexOfValue(newValue as String)
                if (index >= 0) {
                    preference.summary = listPreference.entries[index].toString()
                }
                true
            }
        }

        val sumireMethodPreference =
            findPreference<ListPreference>("sumire_input_method_preference")
        sumireMethodPreference?.apply {
            summary = entries[findIndexOfValue(value)].toString()
            setOnPreferenceChangeListener { preference, newValue ->
                val listPreference = preference as ListPreference
                val index = listPreference.findIndexOfValue(newValue as String)
                if (index >= 0) {
                    preference.summary = listPreference.entries[index].toString()
                }
                true
            }
        }
    }
}
