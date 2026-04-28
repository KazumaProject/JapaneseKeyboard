package com.kazumaproject.markdownhelperkeyboard.setting_activity.circular_slot

import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode

data class CircularSlotActionSetting(
    val mode: KeyboardInputMode,
    val keyIdentifier: String,
    val slot: CircularFlickDirection,
    val actionType: CircularSlotActionType,
    val value: String? = null
)

enum class CircularSlotActionType {
    NONE,
    INPUT_TEXT,
    SHOW_EMOJI_KEYBOARD,
    SWITCH_TO_NEXT_IME,
    SWITCH_TO_KANA_LAYOUT,
    SWITCH_TO_ENGLISH_LAYOUT,
    SWITCH_TO_NUMBER_LAYOUT,
    SWITCH_MAP
}
