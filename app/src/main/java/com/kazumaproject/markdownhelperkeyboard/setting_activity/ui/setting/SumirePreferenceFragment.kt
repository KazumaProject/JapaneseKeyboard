package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.kazumaproject.markdownhelperkeyboard.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SumirePreferenceFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_sumire, rootKey)

        val sumireKeyboardSizePreference =
            findPreference<Preference>("sumire_keyboard_size_preference")

        // 入力スタイルに応じて表示を切り替える設定項目を先に取得しておく
        val hierarchicalFlickAngleMarginPreference =
            findPreference<Preference>("hierarchical_flick_angle_margin_preference")
        val sumireCustomAnglePreference =
            findPreference<Preference>("sumire_custom_angle_preference")
        val circularSlotActionSettingPreference =
            findPreference<Preference>("circular_slot_action_setting_preference")

        val sumireStylePreference =
            findPreference<ListPreference>("sumire_keyboard_style_preference")

        sumireStylePreference?.apply {
            // Summaryの初期設定
            if (findIndexOfValue(value) >= 0) {
                summary = entries[findIndexOfValue(value)].toString()
            }

            // 【追加】初期表示状態の設定
            hierarchicalFlickAngleMarginPreference?.isVisible = (value == "third-flick")
            sumireCustomAnglePreference?.isVisible = (value == "sumire")
            circularSlotActionSettingPreference?.isVisible = (value == "sumire")

            setOnPreferenceChangeListener { preference, newValue ->
                val stringValue = newValue as String
                val listPreference = preference as ListPreference
                val index = listPreference.findIndexOfValue(stringValue)

                // Summaryの更新
                if (index >= 0) {
                    preference.summary = listPreference.entries[index].toString()
                }

                // 【追加】変更時の表示切り替え
                hierarchicalFlickAngleMarginPreference?.isVisible = (stringValue == "third-flick")
                sumireCustomAnglePreference?.isVisible = (stringValue == "sumire")
                circularSlotActionSettingPreference?.isVisible = (stringValue == "sumire")

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

        sumireKeyboardSizePreference?.apply {
            setOnPreferenceClickListener {
                navigateSafely(R.id.flickKeyboardSizeSettingsFragment)
                true
            }
        }

        findPreference<Preference>("flick_keyboard_popup_view_style_preference")?.apply {
            setOnPreferenceClickListener {
                navigateSafely(R.id.flickKeyboardPopupStyleListFragment)
                true
            }
        }

        hierarchicalFlickAngleMarginPreference?.apply {
            setOnPreferenceClickListener {
                navigateSafely(R.id.hierarchicalFlickAngleMarginFragment)
                true
            }
        }

        sumireCustomAnglePreference?.apply {
            setOnPreferenceClickListener {
                navigateSafely(R.id.circularFlickSettingsFragment)
                true
            }
        }

        circularSlotActionSettingPreference?.apply {
            setOnPreferenceClickListener {
                navigateSafely(R.id.circularSlotActionSettingFragment)
                true
            }
        }

        findPreference<Preference>("sumire_special_key_editor_preference")?.apply {
            setOnPreferenceClickListener {
                navigateSafely(R.id.sumireSpecialKeyEditorFragment)
                true
            }
        }

        applyLegacySearchResultFilterIfNeeded()
    }
}
