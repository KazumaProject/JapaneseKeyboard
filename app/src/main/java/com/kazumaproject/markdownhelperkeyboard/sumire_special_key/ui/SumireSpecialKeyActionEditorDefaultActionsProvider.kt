package com.kazumaproject.markdownhelperkeyboard.sumire_special_key.ui

import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyDefaultActionResolver
import javax.inject.Inject

interface SumireSpecialKeyActionEditorDefaultActionsProvider {
    fun buildDefaultActions(
        layoutType: String,
        inputMode: String,
        keyId: String
    ): Map<SumireSpecialKeyDirection, KeyAction?>
}

class AppSumireSpecialKeyActionEditorDefaultActionsProvider @Inject constructor(
    private val appPreference: AppPreference
) : SumireSpecialKeyActionEditorDefaultActionsProvider {
    override fun buildDefaultActions(
        layoutType: String,
        inputMode: String,
        keyId: String
    ): Map<SumireSpecialKeyDirection, KeyAction?> {
        val mode = runCatching { KeyboardInputMode.valueOf(inputMode) }
            .getOrDefault(KeyboardInputMode.HIRAGANA)
        val layout = KeyboardDefaultLayouts.createFinalLayout(
            mode = mode,
            dynamicKeyStates = previewDynamicStates,
            inputLayoutType = layoutType.ifBlank { "toggle" },
            inputStyle = appPreference.sumire_keyboard_style,
            deleteKeyFlickSettings = KeyboardDefaultLayouts.DeleteKeyFlickSettings(
                left = appPreference.delete_key_left_flick_preference,
                up = appPreference.delete_key_up_flick_preference,
                down = appPreference.delete_key_down_flick_preference
            )
        )
        return SumireSpecialKeyDirection.entries.associateWith { direction ->
            SumireSpecialKeyDefaultActionResolver.resolve(layout, keyId, direction)
        }
    }

    private companion object {
        val previewDynamicStates = mapOf(
            "enter_key" to 0,
            "dakuten_toggle_key" to 0,
            "katakana_toggle_key" to 0,
            "space_convert_key" to 0
        )
    }
}
