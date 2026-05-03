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
    data object ForceNewLine : KeyAction() // 改行を強制
    data object Enter : KeyAction()   // 確定（文脈によってNewLineと使い分ける）
    data object Convert : KeyAction() // 変換
    data object Confirm : KeyAction() // 確定
    data object DeleteUntilSymbol : KeyAction()
    data object DeleteAfterCursorUntilSymbol : KeyAction()
    data object UndoLastDelete : KeyAction()

    data object SwitchDirectMode : KeyAction()

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
    data object CapLockKey : KeyAction()
    data object SwitchRomajiEnglish : KeyAction()
    data object MoveCustomKeyboardTab : KeyAction()
    data class MoveToCustomKeyboard(val stableId: String) : KeyAction()

    // ひらがな・英語用
    data object ToggleDakuten : KeyAction() // 濁点・半濁点・小文字化
    data object ToggleCase : KeyAction()    // 英語の大文字・小文字切り替え
    data object ToggleKatakana : KeyAction()    // カタカナ切り替え

    data object VoiceInput : KeyAction()
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
    val isHiLighted: Boolean = false,
    val keyId: String? = null,
    val keyType: KeyType = if (isFlickable) KeyType.CIRCULAR_FLICK else KeyType.NORMAL
)

data class GridPlacement(
    val rowUnits: Int,
    val columnUnits: Int,
    val rowSpanUnits: Int = 2,
    val columnSpanUnits: Int = 2
)

sealed interface KeyboardLayoutItem {
    val id: String
    val placement: GridPlacement
}

data class KeyItem(
    override val id: String,
    val keyData: KeyData,
    override val placement: GridPlacement
) : KeyboardLayoutItem

data class SpacerItem(
    override val id: String,
    override val placement: GridPlacement
) : KeyboardLayoutItem

private fun KeyData.layoutItemId(): String =
    keyId?.takeIf { it.isNotBlank() } ?: "key_${row}_${column}_${label}"

fun KeyData.toKeyItem(): KeyItem =
    KeyItem(
        id = layoutItemId(),
        keyData = this,
        placement = GridPlacement(
            rowUnits = row * 2,
            columnUnits = column * 2,
            rowSpanUnits = rowSpan * 2,
            columnSpanUnits = colSpan * 2
        )
    )

fun halfColumnSpacer(id: String, rowUnits: Int, columnUnits: Int): SpacerItem =
    SpacerItem(
        id = id,
        placement = GridPlacement(rowUnits, columnUnits, rowSpanUnits = 2, columnSpanUnits = 1)
    )

fun oneColumnSpacer(id: String, rowUnits: Int, columnUnits: Int): SpacerItem =
    SpacerItem(
        id = id,
        placement = GridPlacement(rowUnits, columnUnits, rowSpanUnits = 2, columnSpanUnits = 2)
    )

fun halfRowSpacer(
    id: String,
    rowUnits: Int,
    columnUnits: Int,
    columnSpanUnits: Int
): SpacerItem =
    SpacerItem(
        id = id,
        placement = GridPlacement(rowUnits, columnUnits, rowSpanUnits = 1, columnSpanUnits = columnSpanUnits)
    )

fun oneRowSpacer(
    id: String,
    rowUnits: Int,
    columnUnits: Int,
    columnSpanUnits: Int
): SpacerItem =
    SpacerItem(
        id = id,
        placement = GridPlacement(rowUnits, columnUnits, rowSpanUnits = 2, columnSpanUnits = columnSpanUnits)
    )

fun KeyboardLayout.itemsForKeys(updatedKeys: List<KeyData>): List<KeyboardLayoutItem> {
    val keysByItemId = updatedKeys.associateBy { it.layoutItemId() }
    val usedIds = mutableSetOf<String>()

    val updatedItems = items.mapNotNull { item ->
        when (item) {
            is SpacerItem -> item
            is KeyItem -> {
                val updatedKeyData = keysByItemId[item.id]
                if (updatedKeyData != null) {
                    usedIds += item.id
                    if (
                        updatedKeyData.row == item.keyData.row &&
                        updatedKeyData.column == item.keyData.column &&
                        updatedKeyData.rowSpan == item.keyData.rowSpan &&
                        updatedKeyData.colSpan == item.keyData.colSpan
                    ) {
                        item.copy(keyData = updatedKeyData)
                    } else {
                        updatedKeyData.toKeyItem()
                    }
                } else {
                    null
                }
            }
        }
    }

    return updatedItems + updatedKeys
        .filter { it.layoutItemId() !in usedIds }
        .map { it.toKeyItem() }
}

fun KeyboardLayout.copyWithKeys(
    keys: List<KeyData>,
    columnCount: Int = this.columnCount,
    rowCount: Int = this.rowCount
): KeyboardLayout =
    copy(
        keys = keys,
        columnCount = columnCount,
        rowCount = rowCount,
        items = itemsForKeys(keys),
        columnUnitCount = columnCount * 2,
        rowUnitCount = rowCount * 2
    )

// =====================================================================
// Item-based helpers (long-term source of truth: items + GridPlacement)
//
// Use these helpers whenever the layout uses half-cell offsets or
// SpacerItems (QWERTY/AZERTY/Dvorak/Colemak templates and any layout
// where items contain non-(row*2,column*2) placements).
//
// Avoid `copyWithKeys()` for those layouts because it routes through
// KeyData.row/column, which cannot represent half-cell offsets.
// =====================================================================

/**
 * Replace the item list of this layout while keeping every other field
 * intact. `keys` is regenerated from the new items so the two stay in sync,
 * and column/row unit counts are kept as the current layout's values.
 *
 * This is the preferred update primitive when a layout's source of truth is
 * `items` + `GridPlacement` (e.g. QWERTY-family templates with half-cell
 * offsets and SpacerItems).
 */
fun KeyboardLayout.copyWithItems(
    newItems: List<KeyboardLayoutItem>
): KeyboardLayout {
    val newKeys = newItems.filterIsInstance<KeyItem>().map { it.keyData }
    return copy(
        keys = newKeys,
        items = newItems
    )
}

/**
 * Update the [KeyData] of a single [KeyItem] while leaving its placement
 * (and every other item) untouched.
 *
 * Use this when the user edits a key's label / action / flick map etc.
 * but the cell it occupies should not change.
 */
fun KeyboardLayout.updateKeyDataKeepingPlacement(
    keyId: String,
    transform: (KeyData) -> KeyData
): KeyboardLayout {
    var changed = false
    val newItems = items.map { item ->
        when {
            item is KeyItem && (item.id == keyId || item.keyData.keyId == keyId) -> {
                changed = true
                item.copy(keyData = transform(item.keyData))
            }
            else -> item
        }
    }
    if (!changed) return this
    return copyWithItems(newItems)
}

/**
 * Swap the placements of two [KeyItem]s identified by id (KeyItem.id or
 * KeyData.keyId). Item order in the list is preserved; only their
 * [GridPlacement]s are exchanged. SpacerItems are left untouched.
 *
 * This is the placement-based replacement for the legacy "copy KeyData
 * with new row/column" swap, which cannot represent half-cell offsets.
 *
 * Returns the original layout if either id can't be matched, if both ids
 * resolve to the same item, or if swapping would produce overlaps /
 * out-of-bounds placements (caller can detect "no-op" by reference equality).
 */
fun KeyboardLayout.swapKeyPlacements(
    draggedKeyId: String,
    targetKeyId: String
): KeyboardLayout {
    if (draggedKeyId == targetKeyId) return this

    val keyItems = items.filterIsInstance<KeyItem>()
    val dragged = keyItems.firstOrNull {
        it.id == draggedKeyId || it.keyData.keyId == draggedKeyId
    } ?: return this
    val target = keyItems.firstOrNull {
        it.id == targetKeyId || it.keyData.keyId == targetKeyId
    } ?: return this
    if (dragged.id == target.id) return this

    val newItems = items.map { item ->
        when {
            item is KeyItem && item.id == dragged.id -> item.copy(placement = target.placement)
            item is KeyItem && item.id == target.id -> item.copy(placement = dragged.placement)
            else -> item
        }
    }

    if (hasPlacementIssues(newItems, rowUnitCount, columnUnitCount)) {
        return this
    }
    return copyWithItems(newItems)
}

/**
 * Returns true if any KeyItem placement overlaps another KeyItem / SpacerItem
 * placement, or any item's placement extends outside [rowUnitCount] /
 * [columnUnitCount].
 *
 * Spacer ⇆ Spacer overlaps are tolerated (they are visually transparent).
 */
fun hasPlacementIssues(
    items: List<KeyboardLayoutItem>,
    rowUnitCount: Int,
    columnUnitCount: Int
): Boolean {
    items.forEach { item ->
        val p = item.placement
        if (p.rowUnits < 0 || p.columnUnits < 0) return true
        if (p.rowUnits + p.rowSpanUnits > rowUnitCount) return true
        if (p.columnUnits + p.columnSpanUnits > columnUnitCount) return true
        if (p.rowSpanUnits <= 0 || p.columnSpanUnits <= 0) return true
    }

    for (i in items.indices) {
        for (j in i + 1 until items.size) {
            val a = items[i]
            val b = items[j]
            if (a is SpacerItem && b is SpacerItem) continue
            if (isPlacementOverlapping(a.placement, b.placement)) return true
        }
    }
    return false
}

/**
 * Two GridPlacements overlap if their integer-unit rectangles intersect.
 *
 * Rectangle:
 *   left   = columnUnits
 *   right  = columnUnits + columnSpanUnits
 *   top    = rowUnits
 *   bottom = rowUnits + rowSpanUnits
 */
fun isPlacementOverlapping(p1: GridPlacement, p2: GridPlacement): Boolean {
    val p1Left = p1.columnUnits
    val p1Right = p1.columnUnits + p1.columnSpanUnits
    val p1Top = p1.rowUnits
    val p1Bottom = p1.rowUnits + p1.rowSpanUnits

    val p2Left = p2.columnUnits
    val p2Right = p2.columnUnits + p2.columnSpanUnits
    val p2Top = p2.rowUnits
    val p2Bottom = p2.rowUnits + p2.rowSpanUnits

    return !(
        p1Right <= p2Left ||
        p1Left >= p2Right ||
        p1Bottom <= p2Top ||
        p1Top >= p2Bottom
    )
}


data class KeyboardLayout(
    val keys: List<KeyData>,
    val flickKeyMaps: Map<String, List<Map<FlickDirection, FlickAction>>>,
    val columnCount: Int,
    val rowCount: Int,
    val isRomaji: Boolean = false,
    val isDirectMode: Boolean = false,
    val circularFlickKeyMaps: Map<String, List<Map<CircularFlickDirection, FlickAction>>> =
        flickKeyMaps.toCircularFlickKeyMaps(),
    val twoStepFlickKeyMaps: Map<String, Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>>> = emptyMap(),
    val longPressFlickKeyMaps: Map<String, Map<FlickDirection, String>> = emptyMap(),
    val twoStepLongPressKeyMaps: Map<String, Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>>> = emptyMap(),
    val hierarchicalFlickMaps: Map<String, TfbiFlickNode.StatefulKey> = emptyMap(),
    val items: List<KeyboardLayoutItem> = keys.map { it.toKeyItem() },
    val columnUnitCount: Int = columnCount * 2,
    val rowUnitCount: Int = rowCount * 2
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
