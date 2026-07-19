package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

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
