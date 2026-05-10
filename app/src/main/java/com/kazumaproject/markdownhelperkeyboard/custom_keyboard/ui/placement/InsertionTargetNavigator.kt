package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement

import com.kazumaproject.custom_keyboard.data.KeyboardLayout

class InsertionTargetNavigator {

    fun nudge(layout: KeyboardLayout, target: InsertionTarget, direction: NudgeDirection): InsertionTarget {
        return when (direction) {
            NudgeDirection.Left -> horizontalSlots(layout).previousOrFirst(target)
            NudgeDirection.Right -> horizontalSlots(layout).nextOrLast(target)
            NudgeDirection.Up -> verticalSlots(layout).previousOrFirst(target)
            NudgeDirection.Down -> verticalSlots(layout).nextOrLast(target)
        }
    }

    fun cycle(layout: KeyboardLayout, target: InsertionTarget): InsertionTarget {
        return when (target) {
            is InsertionTarget.BeforeItem -> {
                InsertionTarget.AfterItem(target.itemId)
            }
            is InsertionTarget.AfterItem -> {
                val row = layout.items.firstOrNull { it.id == target.itemId }?.placement?.rowUnits ?: 0
                InsertionTarget.AboveRowGroup(row)
            }
            is InsertionTarget.AboveRowGroup -> {
                InsertionTarget.BelowRowGroup(target.topRowUnits)
            }
            is InsertionTarget.BelowRowGroup -> {
                val firstInRow = layout.items
                    .filter { it.placement.rowUnits == target.topRowUnits }
                    .minByOrNull { it.placement.columnUnits }
                if (firstInRow != null) {
                    InsertionTarget.BeforeItem(firstInRow.id)
                } else {
                    InsertionTarget.RowEnd(target.topRowUnits)
                }
            }
            is InsertionTarget.RowEnd -> {
                InsertionTarget.AboveRowGroup(target.topRowUnits)
            }
            is InsertionTarget.NewBottomRow -> {
                InsertionTarget.EmptyArea(
                    com.kazumaproject.custom_keyboard.data.GridPlacement(
                        rowUnits = layout.rowUnitCount,
                        columnUnits = target.columnUnits,
                        rowSpanUnits = 1,
                        columnSpanUnits = 1
                    )
                )
            }
            is InsertionTarget.EmptyArea -> {
                InsertionTarget.NewBottomRow(target.placement.columnUnits)
            }
        }
    }

    private fun horizontalSlots(layout: KeyboardLayout): List<InsertionTarget> {
        val slots = mutableListOf<InsertionTarget>()
        layout.items
            .groupBy { it.placement.rowUnits }
            .toSortedMap()
            .forEach { (row, items) ->
                items.sortedWith(compareBy({ it.placement.columnUnits }, { it.id })).forEach { item ->
                    slots += InsertionTarget.BeforeItem(item.id)
                    slots += InsertionTarget.AfterItem(item.id)
                }
                slots += InsertionTarget.RowEnd(row)
            }
        return slots
    }

    private fun verticalSlots(layout: KeyboardLayout): List<InsertionTarget> {
        val rows = layout.items.map { it.placement.rowUnits }.distinct().sorted()
        return buildList {
            rows.forEach { row ->
                add(InsertionTarget.AboveRowGroup(row))
                add(InsertionTarget.BelowRowGroup(row))
            }
            add(InsertionTarget.NewBottomRow(0))
        }
    }

    private fun List<InsertionTarget>.nextOrLast(target: InsertionTarget): InsertionTarget {
        if (isEmpty()) return target
        val index = indexOf(target)
        return when {
            index == -1 -> first()
            index >= lastIndex -> last()
            else -> get(index + 1)
        }
    }

    private fun List<InsertionTarget>.previousOrFirst(target: InsertionTarget): InsertionTarget {
        if (isEmpty()) return target
        val index = indexOf(target)
        return when {
            index <= 0 -> first()
            else -> get(index - 1)
        }
    }
}
