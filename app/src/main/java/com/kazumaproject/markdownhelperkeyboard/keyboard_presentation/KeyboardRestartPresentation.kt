package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import com.kazumaproject.markdownhelperkeyboard.ime_service.RestartInputModePreference
import com.kazumaproject.markdownhelperkeyboard.ime_service.RestartInputModeState
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference

/** Shared saved-mode/time-limit policy. Rendering a preview never refreshes the saved timestamp. */
internal object KeyboardRestartPresentation {
    fun resolve(type: KeyboardType, now: Long = System.currentTimeMillis()): RestartInputModeState? = with(AppPreference) {
        val allowed = when (type) {
            KeyboardType.TENKEY, KeyboardType.GOJUON -> RestartInputModePreference.isRestoreAllowedByTimeLimit(
                tenkey_restore_input_mode_only_within_time_preference, tenkey_restore_input_mode_timeout_minutes_preference,
                tenkey_last_input_mode_saved_at_epoch_millis_preference, now)
            KeyboardType.SUMIRE -> RestartInputModePreference.isRestoreAllowedByTimeLimit(
                sumire_restore_input_mode_only_within_time_preference, sumire_restore_input_mode_timeout_minutes_preference,
                sumire_last_input_mode_saved_at_epoch_millis_preference, now)
            else -> true
        }
        if (!allowed) return null
        RestartInputModePreference.resolveRestoredState(type,
            tenkey_restore_input_mode_on_restart_preference, sumire_restore_input_mode_on_restart_preference,
            tenkey_last_input_mode_preference, tenkey_last_input_mode_presentation_preference,
            tenkey_last_qwerty_number_return_target_preference, sumire_last_input_mode_preference,
            sumire_last_input_mode_presentation_preference)
    }
    fun customDirect(layoutId: Long, stableId: String, default: Boolean): Boolean = with(AppPreference) {
        if (remember_custom_keyboard_input_mode_preference != true) default
        else getCustomKeyboardLastDirectMode(stableId.takeIf { it.isNotBlank() } ?: layoutId.toString()) ?: default
    }
    fun customRomaji(layoutId: Long, stableId: String, default: Boolean): Boolean = with(AppPreference) {
        if (remember_custom_keyboard_input_mode_preference != true) default
        else getCustomKeyboardLastRomajiMode(stableId.takeIf { it.isNotBlank() } ?: layoutId.toString()) ?: default
    }
}
