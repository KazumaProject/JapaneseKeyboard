package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.voice_input.VoiceInputSpaceReplacement
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference

class VoiceInputPreferenceFragment : AsyncPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_voice_input

    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {
        findPreference<SwitchPreferenceCompat>(AppPreference.VOICE_INPUT_SPACE_REPLACEMENT_ENABLED_KEY)
            ?.setOnPreferenceChangeListener { _, newValue ->
                updateCustomTextState(enabled = newValue as Boolean)
                true
            }
        findPreference<ListPreference>(AppPreference.VOICE_INPUT_SPACE_REPLACEMENT_KEY)
            ?.setOnPreferenceChangeListener { _, newValue ->
                updateCustomTextState(replacement = newValue as String)
                true
            }
        findPreference<EditTextPreference>(AppPreference.VOICE_INPUT_SPACE_CUSTOM_TEXT_KEY)?.apply {
            setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                editText.setSingleLine(true)
            }
            summaryProvider = androidx.preference.Preference.SummaryProvider<EditTextPreference> {
                if (it.text.isNullOrEmpty()) {
                    getString(R.string.voice_input_custom_text_empty_summary)
                } else {
                    getString(R.string.voice_input_custom_text_value_summary, it.text)
                }
            }
        }
        applyLegacySearchResultFilterIfNeeded()
        updateCustomTextState()
    }

    override fun onPreferencesResumed() {
        updateCustomTextState()
    }

    private fun updateCustomTextState(
        enabled: Boolean = AppPreference.voice_input_space_replacement_enabled,
        replacement: String = AppPreference.voice_input_space_replacement,
    ) {
        findPreference<EditTextPreference>(AppPreference.VOICE_INPUT_SPACE_CUSTOM_TEXT_KEY)?.isEnabled =
            enabled && replacement == VoiceInputSpaceReplacement.CUSTOM.preferenceValue
    }
}
