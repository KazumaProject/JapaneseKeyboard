package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import com.kazumaproject.markdownhelperkeyboard.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class KeyboardDisplayPreferenceFragment : CommonPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_keyboard_display
}

@AndroidEntryPoint
class InputMethodPreferenceFragment : CommonPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_input_method
}

@AndroidEntryPoint
class CandidateConversionPreferenceFragment : CommonPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_candidate_conversion
}

@AndroidEntryPoint
class ConversionEnginePreferenceFragment : CommonPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_conversion_engine

    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {
        super.onPreferencesReady(savedInstanceState, rootKey)
        listOf(
            "learn_prediction_preference" to R.string.learn_dictionary_prefix_match_summary,
            "user_dictionary_prefix_match_number" to R.string.user_dictionary_prefix_match_summary,
        ).forEach { (key, summaryResource) ->
            findPreference<SeekBarPreference>(key)?.apply {
                summary = getString(summaryResource, value)
                setOnPreferenceChangeListener { _, newValue ->
                    summary = getString(summaryResource, newValue as Int)
                    true
                }
            }
        }
        listOf(
            "settings_engine_enable_learning_dictionary_link" to "learn_dictionary_preference",
            "settings_engine_enable_user_dictionary_link" to "user_dictionary_preference",
        ).forEach { (link, parent) ->
            findPreference<Preference>(link)?.setOnPreferenceClickListener {
                navigateSafely(R.id.dictionaryPreferenceFragment, bundleOf(ARG_HIGHLIGHT_PREFERENCE_KEY to parent))
                true
            }
        }
        syncDictionaryDependencies()
    }

    override fun onPreferencesResumed() {
        super.onPreferencesResumed()
        syncDictionaryDependencies()
    }

    private val dictionaryListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "learn_dictionary_preference" || key == "user_dictionary_preference") {
            syncDictionaryDependencies()
        }
    }

    override fun onStart() {
        super.onStart()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(dictionaryListener)
    }

    override fun onStop() {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(dictionaryListener)
        super.onStop()
    }

    private fun syncDictionaryDependencies() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val learningEnabled = preferences.getBoolean("learn_dictionary_preference", true)
        val userEnabled = preferences.getBoolean("user_dictionary_preference", true)
        findPreference<Preference>("enable_prediction_search_learn_dictionary_preference")?.isEnabled = learningEnabled
        findPreference<Preference>("user_dictionary_prefix_match_number")?.isEnabled = userEnabled
        findPreference<Preference>("user_dictionary_prediction_candidate_limit_preference")?.isEnabled = userEnabled
        findPreference<Preference>("settings_engine_enable_learning_dictionary_link")?.conditionVisible = !learningEnabled
        findPreference<Preference>("settings_engine_enable_user_dictionary_link")?.conditionVisible = !userEnabled
    }
}

@AndroidEntryPoint
class AiConversionPreferenceFragment : CommonPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_ai_conversion
}

@AndroidEntryPoint
class OperationFeedbackPreferenceFragment : CommonPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_operation_feedback
}

@AndroidEntryPoint
class ClipboardShortcutPreferenceFragment : CommonPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_clipboard_shortcut
}

@AndroidEntryPoint
class GeneralInfoPreferenceFragment : CommonPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_general_info
}

@AndroidEntryPoint
class AdvancedPreferenceFragment : CommonPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_advanced
}

@AndroidEntryPoint
class LegacyCommonPreferenceFragment : CommonPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_common_legacy
}
