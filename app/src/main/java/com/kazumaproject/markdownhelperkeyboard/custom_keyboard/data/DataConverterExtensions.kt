package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data


import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.KeyAction

private fun KeyAction.toDrawableResId(): Int? = when (this) {
    KeyAction.Delete -> com.kazumaproject.core.R.drawable.backspace_24px
    KeyAction.DeleteUntilSymbol -> com.kazumaproject.core.R.drawable.backspace_24px_until_symbol
    KeyAction.Space -> com.kazumaproject.core.R.drawable.baseline_space_bar_24
    KeyAction.Convert -> com.kazumaproject.core.R.drawable.henkan
    KeyAction.Enter -> com.kazumaproject.core.R.drawable.baseline_keyboard_return_24
    KeyAction.Paste -> com.kazumaproject.core.R.drawable.content_paste_24px
    KeyAction.Copy -> com.kazumaproject.core.R.drawable.content_copy_24dp
    KeyAction.SwitchToNextIme -> com.kazumaproject.core.R.drawable.language_24dp
    KeyAction.ShowEmojiKeyboard -> com.kazumaproject.core.R.drawable.baseline_emoji_emotions_24
    KeyAction.ToggleDakuten -> com.kazumaproject.core.R.drawable.kana_small
    KeyAction.ToggleCase -> com.kazumaproject.core.R.drawable.english_small
    KeyAction.ShiftKey -> com.kazumaproject.core.R.drawable.shift_24px
    KeyAction.MoveCustomKeyboardTab -> com.kazumaproject.core.R.drawable.keyboard_command_key_24px
    KeyAction.MoveCursorLeft -> com.kazumaproject.core.R.drawable.baseline_arrow_left_24
    KeyAction.MoveCursorRight -> com.kazumaproject.core.R.drawable.baseline_arrow_right_24
    KeyAction.SelectAll -> com.kazumaproject.core.R.drawable.text_select_start_24dp
    KeyAction.SwitchToEnglishLayout -> com.kazumaproject.core.R.drawable.input_mode_english_custom
    KeyAction.SwitchToNumberLayout -> com.kazumaproject.core.R.drawable.input_mode_number_select_custom
    KeyAction.ToggleKatakana -> com.kazumaproject.core.R.drawable.katakana
    KeyAction.VoiceInput -> com.kazumaproject.core.R.drawable.settings_voice_24px
    else -> null
}

/**
 * DBから取得したFlickMappingを、UIで扱うFlickActionに変換します。
 */
fun FlickMapping.toFlickAction(): FlickAction {
    val action = when (this.actionType) {
        "INPUT_TEXT" -> return FlickAction.Input(this.actionValue ?: "")
        "DELETE" -> KeyAction.Delete
        "BACKSPACE" -> KeyAction.Backspace
        "SPACE" -> KeyAction.Space
        "NEW_LINE" -> KeyAction.NewLine
        "ENTER" -> KeyAction.Enter
        "CONVERT" -> KeyAction.Convert
        "CONFIRM" -> KeyAction.Confirm
        "MOVE_CURSOR_LEFT" -> KeyAction.MoveCursorLeft
        "MOVE_CURSOR_RIGHT" -> KeyAction.MoveCursorRight
        "SELECT_ALL" -> KeyAction.SelectAll
        "PASTE" -> KeyAction.Paste
        "COPY" -> KeyAction.Copy
        "CHANGE_INPUT_MODE" -> KeyAction.ChangeInputMode
        "SWITCH_TO_NEXT_IME" -> KeyAction.SwitchToNextIme
        "TOGGLE_DAKUTEN" -> KeyAction.ToggleDakuten
        "TOGGLE_CASE" -> KeyAction.ToggleCase
        "ShowEmojiKeyboard" -> KeyAction.ShowEmojiKeyboard
        "SwitchToEnglish" -> KeyAction.SwitchToEnglishLayout
        "SwitchToNumber" -> KeyAction.SwitchToNumberLayout
        "DeleteUntilSymbol" -> KeyAction.DeleteUntilSymbol
        "SwitchKatakana" -> KeyAction.ToggleKatakana
        "VoiceInput" -> KeyAction.VoiceInput
        "ShiftKey" -> KeyAction.ShiftKey
        "MoveCustomKeyboardTab" -> KeyAction.MoveCustomKeyboardTab
        else -> null
    }
    return if (action != null) {
        FlickAction.Action(action, drawableResId = action.toDrawableResId())
    } else if (this.actionType.startsWith("INPUT_")) {
        // 将来的なINPUT_*アクションのために
        FlickAction.Input(this.actionValue ?: "")
    } else {
        FlickAction.Input("") // 不明な場合はデフォルトのアクション
    }
}

/**
 * UIのFlickActionを、DBに保存可能な2つの文字列に変換します。
 * @return Pair<ActionType: String, ActionValue: String?>
 */
fun FlickAction.toDbStrings(): Pair<String, String?> {
    return when (this) {
        is FlickAction.Input -> "INPUT_TEXT" to this.char
        is FlickAction.Action -> when (val action = this.action) {
            is KeyAction.InputText -> "INPUT_TEXT" to action.text
            KeyAction.Delete -> "DELETE" to null
            KeyAction.Backspace -> "BACKSPACE" to null
            KeyAction.Space -> "SPACE" to null
            KeyAction.NewLine -> "NEW_LINE" to null
            KeyAction.Enter -> "ENTER" to null
            KeyAction.Convert -> "CONVERT" to null
            KeyAction.Confirm -> "CONFIRM" to null
            KeyAction.MoveCursorLeft -> "MOVE_CURSOR_LEFT" to null
            KeyAction.MoveCursorRight -> "MOVE_CURSOR_RIGHT" to null
            KeyAction.SelectAll -> "SELECT_ALL" to null
            KeyAction.Paste -> "PASTE" to null
            KeyAction.Copy -> "COPY" to null
            KeyAction.ChangeInputMode -> "CHANGE_INPUT_MODE" to null
            KeyAction.SwitchToNextIme -> "SWITCH_TO_NEXT_IME" to null
            KeyAction.ToggleDakuten -> "TOGGLE_DAKUTEN" to null
            KeyAction.ToggleCase -> "TOGGLE_CASE" to null
            KeyAction.ShowEmojiKeyboard -> "ShowEmojiKeyboard" to null
            KeyAction.SwitchToEnglishLayout -> "SwitchToEnglish" to null
            KeyAction.SwitchToNumberLayout -> "SwitchToNumber" to null
            KeyAction.DeleteUntilSymbol -> "DeleteUntilSymbol" to null
            KeyAction.ToggleKatakana -> "SwitchKatakana" to null
            KeyAction.VoiceInput -> "VoiceInput" to null
            KeyAction.ShiftKey -> "ShiftKey" to null
            KeyAction.MoveCustomKeyboardTab -> "MoveCustomKeyboardTab" to null
            else -> "UNKNOWN" to null // 未対応のアクション
        }
    }
}
