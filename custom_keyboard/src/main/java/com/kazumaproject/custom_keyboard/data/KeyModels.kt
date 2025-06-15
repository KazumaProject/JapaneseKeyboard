package com.kazumaproject.custom_keyboard.data

import androidx.annotation.DrawableRes

// キーボードの見た目ではなく、入力の「モード」を定義する
enum class KeyboardInputMode {
    HIRAGANA,
    ENGLISH,
    SYMBOLS
}

/**
 * キーボードの特殊なアクションを定義する Sealed Class
 * これにより、Stringでの判定をなくし、型安全なアクション処理を実現する
 */
sealed class KeyAction {
    // 文字列入力系
    data class InputText(val text: String) : KeyAction()

    // 機能系
    data object Delete : KeyAction()
    data object Backspace : KeyAction() // 1文字戻す（Deleteと実質同じことが多い）
    data object Space : KeyAction()
    data object NewLine : KeyAction() // 改行
    data object Enter : KeyAction()   // 確定（文脈によってNewLineと使い分ける）
    data object Convert : KeyAction() // 変換
    data object Confirm : KeyAction() // 確定

    // カーソル操作系
    data object MoveCursorLeft : KeyAction()
    data object MoveCursorRight : KeyAction()
    data object SelectLeft : KeyAction()
    data object SelectRight : KeyAction()
    data object SelectAll : KeyAction()

    // クリップボード系
    data object Paste : KeyAction()
    data object Copy : KeyAction()

    // UI変更系
    data object ChangeInputMode : KeyAction()
    data object ShowEmojiKeyboard : KeyAction()
    data object SwitchToNextIme : KeyAction() // 次のキーボード（IME）へ切り替え

    // ひらがな・英語用
    data object ToggleDakuten : KeyAction() // 濁点・半濁点・小文字化
    data object ToggleCase : KeyAction()    // 英語の大文字・小文字切り替え
}

data class KeyData(
    val label: String,
    val row: Int,
    val column: Int,
    @Deprecated("Use keyType instead") val isFlickable: Boolean, // isFlickableは将来的に削除を検討
    val action: KeyAction? = null,
    val dynamicStates: List<FlickAction.Action>? = null,
    val rowSpan: Int = 1,
    val colSpan: Int = 1,
    @DrawableRes val drawableResId: Int? = null,
    val isSpecialKey: Boolean = false,
    val keyId: String? = null,
    val keyType: KeyType = if (isFlickable) KeyType.CIRCULAR_FLICK else KeyType.NORMAL
)


data class KeyboardLayout(
    val keys: List<KeyData>,
    val flickKeyMaps: Map<String, List<Map<FlickDirection, FlickAction>>>,
    val columnCount: Int,
    val rowCount: Int
)

/**
 * キーの種類を定義する
 */
enum class KeyType {
    /** 通常のクリック/長押しキー */
    NORMAL,

    /** 円形フリックキー */
    CIRCULAR_FLICK,

    /** 十字フリックキー */
    CROSS_FLICK,

    /** 動的タップとフリックを両立するハイブリッドキー */
    DYNAMIC_FLICK
}


