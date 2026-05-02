package com.kazumaproject.custom_keyboard.data

import android.content.Context
import com.kazumaproject.custom_keyboard.R

data class DisplayAction(
    val action: KeyAction,
    val displayName: String,
    val iconResId: Int? = null // アイコンがない場合はnull
)

object KeyActionMapper {
    private const val MOVE_TO_CUSTOM_KEYBOARD_PREFIX = "MoveToCustomKeyboard:"

    /**
     * Generates a list of DisplayAction objects using localized strings.
     * @param context The context needed to access string resources.
     * @return A list of DisplayAction objects.
     */
    fun getDisplayActions(context: Context): List<DisplayAction> {
        return listOf(
            DisplayAction(
                KeyAction.Delete,
                context.getString(R.string.action_delete),
                com.kazumaproject.core.R.drawable.backspace_24px
            ),
            DisplayAction(
                KeyAction.DeleteUntilSymbol,
                context.getString(R.string.action_delete_until_symbol),
                com.kazumaproject.core.R.drawable.backspace_24px_until_symbol
            ),
            DisplayAction(
                KeyAction.DeleteAfterCursorUntilSymbol,
                context.getString(R.string.action_delete_after_cursor),
                com.kazumaproject.core.R.drawable.backspace_24px_after_cursor
            ),
            DisplayAction(
                KeyAction.Space,
                context.getString(R.string.action_space),
                com.kazumaproject.core.R.drawable.baseline_space_bar_24
            ),
            DisplayAction(
                KeyAction.Convert,
                context.getString(R.string.action_convert),
                com.kazumaproject.core.R.drawable.henkan
            ),
            DisplayAction(
                KeyAction.Enter,
                context.getString(R.string.action_enter),
                com.kazumaproject.core.R.drawable.baseline_keyboard_return_24
            ),
            DisplayAction(KeyAction.ForceNewLine, context.getString(R.string.action_new_line)),
            DisplayAction(
                KeyAction.Paste,
                context.getString(R.string.action_paste),
                com.kazumaproject.core.R.drawable.content_paste_24px
            ),
            DisplayAction(
                KeyAction.Copy,
                context.getString(R.string.action_copy),
                com.kazumaproject.core.R.drawable.content_copy_24dp
            ),
            DisplayAction(
                KeyAction.SwitchToNextIme,
                context.getString(R.string.action_switch_to_next_ime),
                com.kazumaproject.core.R.drawable.language_24dp
            ),
            DisplayAction(
                KeyAction.ShowEmojiKeyboard,
                context.getString(R.string.action_show_emoji_keyboard),
                com.kazumaproject.core.R.drawable.baseline_emoji_emotions_24
            ),
            DisplayAction(
                KeyAction.ToggleDakuten,
                context.getString(R.string.action_toggle_dakuten),
                com.kazumaproject.core.R.drawable.kana_small
            ),
            DisplayAction(
                KeyAction.ToggleCase,
                context.getString(R.string.action_toggle_case),
                com.kazumaproject.core.R.drawable.english_small
            ),
            DisplayAction(
                KeyAction.ShiftKey,
                context.getString(R.string.action_shift_key),
                com.kazumaproject.core.R.drawable.shift_24px
            ),
            DisplayAction(
                KeyAction.SwitchDirectMode,
                context.getString(R.string.action_switch_direct_mode_key),
                com.kazumaproject.core.R.drawable.language_japanese_kana_left_24px
            ),
            DisplayAction(
                KeyAction.SwitchRomajiEnglish,
                context.getString(R.string.action_switch_romaji_english),
                com.kazumaproject.core.R.drawable.language_japanese_kana_24px
            ),
            DisplayAction(
                KeyAction.MoveCustomKeyboardTab,
                context.getString(R.string.action_move_custom_keyboard_tab),
                com.kazumaproject.core.R.drawable.keyboard_command_key_24px
            ),
            DisplayAction(
                KeyAction.MoveToCustomKeyboard(""),
                context.getString(R.string.action_move_to_custom_keyboard),
                com.kazumaproject.core.R.drawable.keyboard_command_key_24px
            ),
            DisplayAction(
                KeyAction.MoveCursorLeft,
                context.getString(R.string.action_move_cursor_left),
                com.kazumaproject.core.R.drawable.baseline_arrow_left_24
            ),
            DisplayAction(
                KeyAction.MoveCursorRight,
                context.getString(R.string.action_move_cursor_right),
                com.kazumaproject.core.R.drawable.baseline_arrow_right_24
            ),
            DisplayAction(
                KeyAction.SelectAll,
                context.getString(R.string.action_select_all),
                com.kazumaproject.core.R.drawable.text_select_start_24dp
            ),
            DisplayAction(
                KeyAction.SwitchToEnglishLayout,
                context.getString(R.string.switch_qwerty),
                com.kazumaproject.core.R.drawable.input_mode_english_custom
            ),
            DisplayAction(
                KeyAction.SwitchToNumberLayout,
                context.getString(R.string.switch_number),
                com.kazumaproject.core.R.drawable.input_mode_number_select_custom
            ),
            DisplayAction(
                KeyAction.ToggleKatakana,
                "カタカナ",
                com.kazumaproject.core.R.drawable.katakana
            ),
            DisplayAction(
                KeyAction.VoiceInput,
                context.getString(R.string.voice_input),
                com.kazumaproject.core.R.drawable.settings_voice_24px
            )
        )
    }

    // KeyActionオブジェクトをDB保存用の文字列に変換
    fun fromKeyAction(keyAction: KeyAction?): String? {
        return when (keyAction) {
            is KeyAction.Delete -> "Delete"
            is KeyAction.Backspace -> "Backspace"
            is KeyAction.Space -> "Space"
            is KeyAction.NewLine -> "NewLine"
            is KeyAction.ForceNewLine -> "ForceNewLine"
            is KeyAction.Enter -> "Enter"
            is KeyAction.Convert -> "Convert"
            is KeyAction.Confirm -> "Confirm"
            is KeyAction.MoveCursorLeft -> "MoveCursorLeft"
            is KeyAction.MoveCursorRight -> "MoveCursorRight"
            is KeyAction.SelectLeft -> "SelectLeft"
            is KeyAction.SelectRight -> "SelectRight"
            is KeyAction.SelectAll -> "SelectAll"
            is KeyAction.Paste -> "Paste"
            is KeyAction.Copy -> "Copy"
            is KeyAction.ChangeInputMode -> "ChangeInputMode"
            is KeyAction.ShowEmojiKeyboard -> "^_^"
            is KeyAction.SwitchToNextIme -> "SwitchToNextIme"
            is KeyAction.ToggleDakuten -> "小゛゜"
            is KeyAction.ToggleCase -> "a/A"
            is KeyAction.SwitchToKanaLayout -> "SwitchToKana"
            is KeyAction.SwitchToEnglishLayout -> "SwitchToEnglish"
            is KeyAction.SwitchToNumberLayout -> "SwitchToNumber"
            is KeyAction.ShiftKey -> "ShiftKeyPressed"
            is KeyAction.SwitchRomajiEnglish -> "SwitchRomajiEnglish"
            is KeyAction.MoveCustomKeyboardTab -> "MoveCustomKeyboardTab"
            is KeyAction.MoveToCustomKeyboard -> keyAction.stableId
                .takeIf { it.isNotBlank() }
                ?.let { "$MOVE_TO_CUSTOM_KEYBOARD_PREFIX$it" }

            is KeyAction.DeleteUntilSymbol -> "DeleteUntilSymbol"
            is KeyAction.DeleteAfterCursorUntilSymbol -> "DeleteAfterCursorUntilSymbol"
            is KeyAction.ToggleKatakana -> "SwitchKatakana"
            is KeyAction.VoiceInput -> "VoiceInput"
            is KeyAction.SwitchDirectMode -> "SwitchDirectMode"
            else -> null
        }
    }

    // DBから読み込んだ文字列をKeyActionオブジェクトに変換
    fun toKeyAction(actionString: String?): KeyAction? {
        if (actionString?.startsWith(MOVE_TO_CUSTOM_KEYBOARD_PREFIX) == true) {
            val stableId = actionString.removePrefix(MOVE_TO_CUSTOM_KEYBOARD_PREFIX)
            return stableId.takeIf { it.isNotBlank() }?.let { KeyAction.MoveToCustomKeyboard(it) }
        }
        return when (actionString) {
            "Delete" -> KeyAction.Delete
            "Backspace" -> KeyAction.Backspace
            "Space" -> KeyAction.Space
            "NewLine" -> KeyAction.NewLine
            "Enter" -> KeyAction.Enter
            "Convert" -> KeyAction.Convert
            "Confirm" -> KeyAction.Confirm
            "MoveCursorLeft" -> KeyAction.MoveCursorLeft
            "MoveCursorRight" -> KeyAction.MoveCursorRight
            "SelectLeft" -> KeyAction.SelectLeft
            "SelectRight" -> KeyAction.SelectRight
            "SelectAll" -> KeyAction.SelectAll
            "Paste" -> KeyAction.Paste
            "Copy" -> KeyAction.Copy
            "ChangeInputMode" -> KeyAction.ChangeInputMode
            "^_^" -> KeyAction.ShowEmojiKeyboard
            "SwitchToNextIme" -> KeyAction.SwitchToNextIme
            "小゛゜" -> KeyAction.ToggleDakuten
            "a/A" -> KeyAction.ToggleCase
            "SwitchToKana" -> KeyAction.SwitchToKanaLayout
            "SwitchToEnglish" -> KeyAction.SwitchToEnglishLayout
            "SwitchToNumber" -> KeyAction.SwitchToNumberLayout
            "ShiftKeyPressed" -> KeyAction.ShiftKey
            "MoveCustomKeyboardTab" -> KeyAction.MoveCustomKeyboardTab
            "DeleteUntilSymbol" -> KeyAction.DeleteUntilSymbol
            "DeleteAfterCursorUntilSymbol" -> KeyAction.DeleteAfterCursorUntilSymbol
            "SwitchKatakana" -> KeyAction.ToggleKatakana
            "SwitchRomajiEnglish" -> KeyAction.SwitchRomajiEnglish
            "VoiceInput" -> KeyAction.VoiceInput
            "ForceNewLine" -> KeyAction.ForceNewLine
            "SwitchDirectMode" -> KeyAction.SwitchDirectMode
            else -> null
        }
    }

}
