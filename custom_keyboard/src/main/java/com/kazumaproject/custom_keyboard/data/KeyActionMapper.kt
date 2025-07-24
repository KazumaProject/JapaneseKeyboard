package com.kazumaproject.custom_keyboard.data

data class DisplayAction(
    val action: KeyAction,
    val displayName: String,
    val iconResId: Int? = null // アイコンがない場合はnull
)

object KeyActionMapper {

    val displayActions = listOf(
        DisplayAction(KeyAction.Delete, "削除", com.kazumaproject.core.R.drawable.backspace_24px),
        DisplayAction(
            KeyAction.Space,
            "空白/変換",
            com.kazumaproject.core.R.drawable.baseline_space_bar_24
        ),
        DisplayAction(
            KeyAction.Enter,
            "エンター",
            com.kazumaproject.core.R.drawable.baseline_keyboard_return_24
        ),
        DisplayAction(
            KeyAction.Paste,
            "貼り付け",
            com.kazumaproject.core.R.drawable.content_paste_24px
        ),
        DisplayAction(
            KeyAction.Copy,
            "コピー",
            com.kazumaproject.core.R.drawable.content_copy_24dp
        ),
        DisplayAction(
            KeyAction.SwitchToNextIme,
            "IME切替",
            com.kazumaproject.core.R.drawable.language_24dp
        ),
        DisplayAction(
            KeyAction.ToggleDakuten,
            "濁点、小文字",
            com.kazumaproject.core.R.drawable.kana_small
        ),
        // --- アイコンがないアクション ---
        DisplayAction(
            KeyAction.MoveCursorLeft,
            "カーソル左",
            com.kazumaproject.core.R.drawable.baseline_arrow_left_24
        ),
        DisplayAction(
            KeyAction.MoveCursorRight,
            "カーソル右",
            com.kazumaproject.core.R.drawable.baseline_arrow_right_24
        ),
//        DisplayAction(KeyAction.SelectLeft, "左を選択"),
//        DisplayAction(KeyAction.SelectRight, "右を選択"),
        DisplayAction(
            KeyAction.SelectAll,
            "すべて選択",
            com.kazumaproject.core.R.drawable.text_select_start_24dp
        ),
    )

    // KeyActionオブジェクトをDB保存用の文字列に変換
    fun fromKeyAction(keyAction: KeyAction?): String? {
        return when (keyAction) {
            is KeyAction.Delete -> "Delete"
            is KeyAction.Backspace -> "Backspace"
            is KeyAction.Space -> "Space"
            is KeyAction.NewLine -> "NewLine"
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
            is KeyAction.ShowEmojiKeyboard -> "ShowEmojiKeyboard"
            is KeyAction.SwitchToNextIme -> "SwitchToNextIme"
            else -> null
        }
    }

    // DBから読み込んだ文字列をKeyActionオブジェクトに変換
    fun toKeyAction(actionString: String?): KeyAction? {
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
            "ShowEmojiKeyboard" -> KeyAction.ShowEmojiKeyboard
            "SwitchToNextIme" -> KeyAction.SwitchToNextIme
            else -> null
        }
    }

    // UI表示用のKeyActionリスト
    val specialActions = listOf(
        KeyAction.Delete, KeyAction.Backspace, KeyAction.Space, KeyAction.NewLine,
        KeyAction.Enter, KeyAction.Convert, KeyAction.Confirm, KeyAction.MoveCursorLeft,
        KeyAction.MoveCursorRight, KeyAction.SelectLeft, KeyAction.SelectRight,
        KeyAction.SelectAll, KeyAction.Paste, KeyAction.Copy, KeyAction.ChangeInputMode,
        KeyAction.ShowEmojiKeyboard, KeyAction.SwitchToNextIme
    )
}
