package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement

import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.withCanonicalFlexibleBounds
import kotlin.math.floor

class InsertionTargetMapper {

    fun mapPointer(
        layout: KeyboardLayout,
        xPx: Float,
        yPx: Float,
        widthPx: Float,
        heightPx: Float,
        policy: InsertionPolicy = InsertionPolicy.PreferHorizontal
    ): InsertionTarget {
        val canonicalLayout = layout.withCanonicalFlexibleBounds()
        if (canonicalLayout.items.isEmpty() || widthPx <= 0f || heightPx <= 0f) {
            return InsertionTarget.EmptyArea(GridPlacement(0, 0, 1, 1))
        }

        val xUnits = (xPx / widthPx) * canonicalLayout.columnUnitCount
        val yUnits = (yPx / heightPx) * canonicalLayout.rowUnitCount
        val rows = canonicalLayout.items
            .groupBy { it.placement.rowUnits }
            .toSortedMap()
            .map { (top, items) ->
                RowGroup(
                    topRowUnits = top,
                    bottomRowUnits = items.maxOf { it.placement.rowUnits + it.placement.rowSpanUnits },
                    items = items.sortedWith(compareBy({ it.placement.columnUnits }, { it.id }))
                )
            }

        rows.firstOrNull { yUnits >= it.topRowUnits && yUnits < it.bottomRowUnits }?.let { row ->
            row.items.firstOrNull { item ->
                val p = item.placement
                xUnits >= p.columnUnits &&
                        xUnits < p.columnUnits + p.columnSpanUnits &&
                        yUnits >= p.rowUnits &&
                        yUnits < p.rowUnits + p.rowSpanUnits
            }?.let { item ->
                val p = item.placement
                val localY = (yUnits - p.rowUnits) / p.rowSpanUnits
                val localX = (xUnits - p.columnUnits) / p.columnSpanUnits
                return if (policy == InsertionPolicy.PreferVertical) {
                    if (localY < 0.5f) {
                        InsertionTarget.BeforeItem(item.id)
                    } else {
                        InsertionTarget.AfterItem(item.id)
                    }
                } else {
                    when {
                        localY < 0.2f -> InsertionTarget.AboveRowGroup(p.rowUnits)
                        localY >= 0.8f -> InsertionTarget.BelowRowGroup(p.rowUnits)
                        localX < 0.5f -> InsertionTarget.BeforeItem(item.id)
                        else -> InsertionTarget.AfterItem(item.id)
                    }
                }
            }

            val maxRight = row.items.maxOf { it.placement.columnUnits + it.placement.columnSpanUnits }
            if (xUnits >= maxRight) {
                return InsertionTarget.RowEnd(row.topRowUnits)
            }

            val next = row.items.firstOrNull { xUnits < it.placement.columnUnits }
            val previous = row.items.lastOrNull {
                xUnits >= it.placement.columnUnits + it.placement.columnSpanUnits
            }
            if (previous != null && next != null) {
                return InsertionTarget.BeforeItem(next.id)
            }
        }

        rows.zipWithNext().firstOrNull { (upper, lower) ->
            yUnits >= upper.bottomRowUnits && yUnits < lower.topRowUnits
        }?.let { (_, lower) ->
            return InsertionTarget.AboveRowGroup(lower.topRowUnits)
        }

        val bottom = rows.maxOf { it.bottomRowUnits }
        if (yUnits >= bottom) {
            return InsertionTarget.NewBottomRow(snapHalfCellColumn(xUnits))
        }

        return InsertionTarget.EmptyArea(
            GridPlacement(
                rowUnits = floor(yUnits).toInt().coerceAtLeast(0),
                columnUnits = snapHalfCellColumn(xUnits),
                rowSpanUnits = 1,
                columnSpanUnits = 1
            )
        )
    }

    private fun snapHalfCellColumn(xUnits: Float): Int =
        floor(xUnits).toInt().coerceAtLeast(0)

    private data class RowGroup(
        val topRowUnits: Int,
        val bottomRowUnits: Int,
        val items: List<com.kazumaproject.custom_keyboard.data.KeyboardLayoutItem>
    )
}
