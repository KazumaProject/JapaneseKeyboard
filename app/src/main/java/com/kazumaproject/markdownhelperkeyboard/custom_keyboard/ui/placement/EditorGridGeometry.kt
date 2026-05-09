package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement

import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.flexibleBounds
import com.kazumaproject.custom_keyboard.data.usesFlexiblePlacement
import com.kazumaproject.custom_keyboard.data.withCanonicalFlexibleBounds

private const val EDITOR_CHROME_OFFSET_UNITS = 2

data class EditorGridBounds(
    val keyboardRowUnitCount: Int,
    val keyboardColumnUnitCount: Int,
    val gridRowCount: Int,
    val gridColumnCount: Int,
    val rowOffsetUnits: Int = EDITOR_CHROME_OFFSET_UNITS,
    val columnOffsetUnits: Int = EDITOR_CHROME_OFFSET_UNITS,
    val showRowColumnDeleteChrome: Boolean
)

data class UnitGridSpec(
    val startUnits: Int,
    val spanUnits: Int
) {
    val endUnits: Int get() = startUnits + spanUnits
}

fun KeyboardLayout.canonicalLayoutForEditor(
    placementCursor: PlacementCursor? = null
): KeyboardLayout {
    if (!usesFlexiblePlacement()) return this
    val canonical = withCanonicalFlexibleBounds()
    val cursorPlacement = placementCursor?.displayPlacement(canonical)
    val minimumRowUnits = cursorPlacement?.let { it.rowUnits + it.rowSpanUnits } ?: 1
    val minimumColumnUnits = cursorPlacement?.let { it.columnUnits + it.columnSpanUnits } ?: 1
    return canonical.withCanonicalFlexibleBounds(
        minimumRowUnits = minimumRowUnits,
        minimumColumnUnits = minimumColumnUnits
    )
}

fun KeyboardLayout.editorGridBounds(): EditorGridBounds {
    val flexible = usesFlexiblePlacement()
    val bounds = if (flexible) {
        flexibleBounds()
    } else {
        flexibleBounds(
            minimumRowUnits = rowUnitCount.coerceAtLeast(rowCount * 2).coerceAtLeast(1),
            minimumColumnUnits = columnUnitCount.coerceAtLeast(columnCount * 2).coerceAtLeast(1)
        )
    }
    return EditorGridBounds(
        keyboardRowUnitCount = bounds.rowUnitCount,
        keyboardColumnUnitCount = bounds.columnUnitCount,
        gridRowCount = bounds.rowUnitCount + EDITOR_CHROME_OFFSET_UNITS,
        gridColumnCount = bounds.columnUnitCount + EDITOR_CHROME_OFFSET_UNITS,
        showRowColumnDeleteChrome = !flexible
    )
}

fun PlacementCursor.displayPlacement(layout: KeyboardLayout): GridPlacement? {
    return when (val insertionTarget = target) {
        is InsertionTarget.BeforeItem -> {
            val item = layout.items.firstOrNull { it.id == insertionTarget.itemId } ?: return null
            GridPlacement(
                rowUnits = item.placement.rowUnits,
                columnUnits = item.placement.columnUnits,
                rowSpanUnits = item.placement.rowSpanUnits,
                columnSpanUnits = 1
            )
        }

        is InsertionTarget.AfterItem -> {
            val item = layout.items.firstOrNull { it.id == insertionTarget.itemId } ?: return null
            GridPlacement(
                rowUnits = item.placement.rowUnits,
                columnUnits = item.placement.columnUnits + item.placement.columnSpanUnits,
                rowSpanUnits = item.placement.rowSpanUnits,
                columnSpanUnits = 1
            )
        }

        is InsertionTarget.RowEnd -> {
            val rowItems = layout.items.filter { it.placement.rowUnits == insertionTarget.topRowUnits }
            GridPlacement(
                rowUnits = insertionTarget.topRowUnits,
                columnUnits = rowItems.maxOfOrNull {
                    it.placement.columnUnits + it.placement.columnSpanUnits
                } ?: 0,
                rowSpanUnits = rowItems.maxOfOrNull { it.placement.rowSpanUnits } ?: span.rowSpanUnits,
                columnSpanUnits = 1
            )
        }

        is InsertionTarget.AboveRowGroup -> {
            GridPlacement(
                rowUnits = insertionTarget.topRowUnits,
                columnUnits = 0,
                rowSpanUnits = 1,
                columnSpanUnits = layout.columnUnitCount
            )
        }

        is InsertionTarget.BelowRowGroup -> {
            val bottom = layout.items
                .filter { it.placement.rowUnits == insertionTarget.topRowUnits }
                .maxOfOrNull { it.placement.rowUnits + it.placement.rowSpanUnits }
                ?: insertionTarget.topRowUnits
            GridPlacement(
                rowUnits = bottom,
                columnUnits = 0,
                rowSpanUnits = 1,
                columnSpanUnits = layout.columnUnitCount
            )
        }

        is InsertionTarget.NewBottomRow -> {
            val bottom = layout.items.maxOfOrNull {
                it.placement.rowUnits + it.placement.rowSpanUnits
            } ?: layout.rowUnitCount
            GridPlacement(
                rowUnits = bottom,
                columnUnits = 0,
                rowSpanUnits = span.rowSpanUnits,
                columnSpanUnits = maxOf(
                    layout.columnUnitCount,
                    insertionTarget.columnUnits + span.columnSpanUnits
                )
            )
        }

        is InsertionTarget.EmptyArea -> {
            insertionTarget.placement.copy(
                rowSpanUnits = span.rowSpanUnits,
                columnSpanUnits = span.columnSpanUnits
            )
        }
    }
}

fun EditorGridBounds.rowDeleteSpecs(rowGroupCount: Int): List<UnitGridSpec> {
    if (!showRowColumnDeleteChrome) return emptyList()
    return (0 until rowGroupCount).map { rowIndex ->
        UnitGridSpec(
            startUnits = rowIndex * 2 + rowOffsetUnits,
            spanUnits = 2
        )
    }
}

fun EditorGridBounds.columnDeleteSpecs(columnGroupCount: Int): List<UnitGridSpec> {
    if (!showRowColumnDeleteChrome) return emptyList()
    return (0 until columnGroupCount).map { columnIndex ->
        UnitGridSpec(
            startUnits = columnIndex * 2 + columnOffsetUnits,
            spanUnits = 2
        )
    }
}

fun UnitGridSpec.fitsWithin(unitCount: Int): Boolean =
    startUnits >= 0 && spanUnits > 0 && endUnits <= unitCount
