package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement

import com.kazumaproject.custom_keyboard.data.GridPlacement

data class GridSpan(
    val rowSpanUnits: Int,
    val columnSpanUnits: Int
)

enum class InsertionPolicy {
    Auto2D,
    PreferHorizontal,
    PreferVertical
}

sealed interface KeyboardEditorMode {
    data object Normal : KeyboardEditorMode
    data class PlacingNewKey(
        val span: GridSpan,
        val policy: InsertionPolicy = InsertionPolicy.Auto2D
    ) : KeyboardEditorMode

    data class PlacingSpacer(
        val span: GridSpan,
        val policy: InsertionPolicy = InsertionPolicy.Auto2D
    ) : KeyboardEditorMode

    data class PlacingSpaceKey(
        val span: GridSpan,
        val policy: InsertionPolicy = InsertionPolicy.Auto2D
    ) : KeyboardEditorMode

    data class MovingExistingItem(
        val itemId: String,
        val policy: InsertionPolicy = InsertionPolicy.Auto2D
    ) : KeyboardEditorMode
}

sealed interface InsertionTarget {
    data class BeforeItem(val itemId: String) : InsertionTarget
    data class AfterItem(val itemId: String) : InsertionTarget
    data class AboveRowGroup(val topRowUnits: Int) : InsertionTarget
    data class BelowRowGroup(val topRowUnits: Int) : InsertionTarget
    data class RowEnd(val topRowUnits: Int) : InsertionTarget
    data class NewBottomRow(val columnUnits: Int) : InsertionTarget
    data class EmptyArea(val placement: GridPlacement) : InsertionTarget
}

data class PlacementCursor(
    val target: InsertionTarget,
    val span: GridSpan,
    val policy: InsertionPolicy,
    val source: CursorSource
)

enum class CursorSource {
    PointerMove,
    Tap,
    Drop,
    Nudge,
    CycleTarget
}

sealed interface PlacementPreviewStatus {
    data object None : PlacementPreviewStatus
    data class Previewing(
        val strategy: PlacementStrategy,
        val insertedItemId: String?,
        val movedItemIds: Set<String>
    ) : PlacementPreviewStatus
}

enum class PlacementStrategy {
    ExactEmptyPlacement,
    BeforeItemInsertion,
    AfterItemInsertion,
    AboveRowInsertion,
    BelowRowInsertion,
    RowEndInsertion,
    NewBottomRowInsertion,
    EmptyAreaPlacement,
    HorizontalInsertion,
    VerticalInsertion,
    RowInsertionDown,
    ColumnInsertionRight,
    CanvasExpansion,
    Mixed2D
}

enum class NudgeDirection {
    Left,
    Right,
    Up,
    Down
}
