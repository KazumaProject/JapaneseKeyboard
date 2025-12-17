package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.kazumaproject.markdownhelperkeyboard.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SumirePreferenceFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_sumire, rootKey)

        // 先に sumireCustomAnglePreference を取得しておく（リスナー内で使うため）
        val sumireCustomAnglePreference =
            findPreference<Preference>("sumire_custom_angle_preference")

        val sumireStylePreference =
            findPreference<ListPreference>("sumire_keyboard_style_preference")

        sumireStylePreference?.apply {
            // Summaryの初期設定
            if (findIndexOfValue(value) >= 0) {
                summary = entries[findIndexOfValue(value)].toString()
            }

            // 【追加】初期表示状態の設定 ("sumire" の場合のみ表示)
            sumireCustomAnglePreference?.isVisible = (value == "sumire")

            setOnPreferenceChangeListener { preference, newValue ->
                val stringValue = newValue as String
                val listPreference = preference as ListPreference
                val index = listPreference.findIndexOfValue(stringValue)

                // Summaryの更新
                if (index >= 0) {
                    preference.summary = listPreference.entries[index].toString()
                }

                // 【追加】変更時の表示切り替え ("sumire" が選ばれたら表示、それ以外は非表示)
                sumireCustomAnglePreference?.isVisible = (stringValue == "sumire")

                true
            }
        }

        val sumireMethodPreference =
            findPreference<ListPreference>("sumire_input_method_preference")
        sumireMethodPreference?.apply {
            if (findIndexOfValue(value) >= 0) {
                summary = entries[findIndexOfValue(value)].toString()
            }
            setOnPreferenceChangeListener { preference, newValue ->
                val listPreference = preference as ListPreference
                val index = listPreference.findIndexOfValue(newValue as String)
                if (index >= 0) {
                    preference.summary = listPreference.entries[index].toString()
                }
                true
            }
        }

        sumireCustomAnglePreference?.apply {
            setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_navigation_setting_to_circularFlickSettingsFragment)
                true
            }
        }
    }
}
