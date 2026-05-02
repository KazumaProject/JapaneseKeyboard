package com.kazumaproject.custom_keyboard.data

import androidx.annotation.DrawableRes
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection

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
    data object Cancel : KeyAction()

    // 文字列入力系
    data class InputText(val text: String) : KeyAction()
    /** 旧 onKey に対応する通常文字入力 */
    data class Text(val text: String) : KeyAction()

    // 機能系
    data object Delete : KeyAction()
    data object Backspace : KeyAction() // 1文字戻す（Deleteと実質同じことが多い）
    data object Space : KeyAction()
    data object NewLine : KeyAction() // 改行
    data object Enter : KeyAction()   // 確定（文脈によってNewLineと使い分ける）
    data object Convert : KeyAction() // 変換
    data object Confirm : KeyAction() // 確定
    data object DeleteUntilSymbol : KeyAction()
    data object DeleteAfterCursorUntilSymbol : KeyAction()
    data object UndoLastDelete : KeyAction()

    // カーソル操作系
    data object MoveCursorLeft : KeyAction()
    data object MoveCursorRight : KeyAction()
    data object MoveCursorUp : KeyAction()
    data object MoveCursorDown : KeyAction()
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
    data object SwitchToKanaLayout : KeyAction()
    data object SwitchToEnglishLayout : KeyAction()
    data object SwitchToNumberLayout : KeyAction()
    data object ShiftKey : KeyAction()
    data object MoveCustomKeyboardTab : KeyAction()
    data class MoveToCustomKeyboard(val stableId: String) : KeyAction()

    // ひらがな・英語用
    data object ToggleDakuten : KeyAction() // 濁点・半濁点・小文字化
    data object ToggleCase : KeyAction()    // 英語の大文字・小文字切り替え
    data object ToggleKatakana : KeyAction()    // カタカナ切り替え

    data object VoiceInput : KeyAction()
}

/**
 * キーの上下左右の余白を表すモデル。
 *
 * - `null` のフィールドは「テンプレートのデフォルト余白を使う」という意味になる。
 * - 値が入っているフィールドだけ、デフォルトを上書きする。
 *
 * ここに直接 px を入れたり scale をかけたりはしない。
 * 描画側で `KeyVisualStyleResolver` を経由して dp を解決し、
 * 既存の DP→PX 変換ロジック・既存のキースケール処理に渡す。
 */
data class KeyMargin(
    val leftDp: Int? = null,
    val topDp: Int? = null,
    val rightDp: Int? = null,
    val bottomDp: Int? = null
)

/**
 * キーの「見た目用」情報をまとめるモデル。
 *
 * 入力動作（`KeyAction` / `FlickAction`）や位置情報（`row`/`column`/`rowSpan`/`colSpan`）と分離し、
 * 将来的に key padding / text size / icon scale / key color / corner radius などを
 * 追加するときに、この `KeyVisualStyle` を拡張するだけで済むようにしておく。
 *
 * 既存キーは `KeyVisualStyle()` のまま使うので、見た目に変化は出ない。
 */
data class KeyVisualStyle(
    val margin: KeyMargin = KeyMargin()
)

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
    val isHiLighted: Boolean = false,
    val keyId: String? = null,
    val keyType: KeyType = if (isFlickable) KeyType.CIRCULAR_FLICK else KeyType.NORMAL,
    /**
     * キー単位の見た目用情報。
     *
     * 既存呼び出しを壊さないように、KeyData の末尾にデフォルト値付きで追加している。
     * default の `KeyVisualStyle()` はすべての margin が null なので、
     * 描画側のフォールバックで「従来と完全に同じ default margin」が使われる。
     */
    val visualStyle: KeyVisualStyle = KeyVisualStyle()
)


data class KeyboardLayout(
    val keys: List<KeyData>,
    val flickKeyMaps: Map<String, List<Map<FlickDirection, FlickAction>>>,
    val columnCount: Int,
    val rowCount: Int,
    val isRomaji: Boolean = false,
    val circularFlickKeyMaps: Map<String, List<Map<CircularFlickDirection, FlickAction>>> =
        flickKeyMaps.toCircularFlickKeyMaps(),
    val twoStepFlickKeyMaps: Map<String, Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>>> = emptyMap(),
    val longPressFlickKeyMaps: Map<String, Map<FlickDirection, String>> = emptyMap(),
    val twoStepLongPressKeyMaps: Map<String, Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>>> = emptyMap(),
    val hierarchicalFlickMaps: Map<String, TfbiFlickNode.StatefulKey> = emptyMap()
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

    STANDARD_FLICK,

    PETAL_FLICK,

    TWO_STEP_FLICK,

    STICKY_TWO_STEP_FLICK,

    HIERARCHICAL_FLICK
}

enum class ShapeType {
    CIRCLE,
    ROUNDED_SQUARE
}
