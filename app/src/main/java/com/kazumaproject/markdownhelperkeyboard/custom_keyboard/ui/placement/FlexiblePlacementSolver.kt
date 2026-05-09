package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement

import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.KeyboardLayoutItem
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.data.copyWithItems
import com.kazumaproject.custom_keyboard.data.hasPlacementIssues
import com.kazumaproject.custom_keyboard.data.isPlacementOverlapping
import com.kazumaproject.custom_keyboard.data.withCanonicalFlexibleBounds
import kotlin.math.ceil

sealed interface PlacementOperation {
    data class Insert(val candidate: KeyboardLayoutItem) : PlacementOperation
    data class MoveExisting(val itemId: String) : PlacementOperation
}

data class PlacementSolveResult(
    val layout: KeyboardLayout,
    val movedItemIds: Set<String>,
    val insertedItemId: String?,
    val strategy: PlacementStrategy
)

class FlexiblePlacementSolver {

    fun solve(
        committedLayout: KeyboardLayout,
        operation: PlacementOperation,
        target: InsertionTarget,
        policy: InsertionPolicy = InsertionPolicy.Auto2D
    ): PlacementSolveResult {
        val canonicalCommittedLayout = committedLayout.withCanonicalFlexibleBounds()
        require(canonicalCommittedLayout.items.all { it.placement.rowSpanUnits > 0 && it.placement.columnSpanUnits > 0 }) {
            "Broken layout contains non-positive item span."
        }
        val originalById = canonicalCommittedLayout.items.associateBy { it.id }
        val movingItem = when (operation) {
            is PlacementOperation.Insert -> operation.candidate
            is PlacementOperation.MoveExisting -> originalById[operation.itemId]
                ?: error("Cannot move missing item '${operation.itemId}'.")
        }
        val baseItems = when (operation) {
            is PlacementOperation.Insert -> canonicalCommittedLayout.items
            is PlacementOperation.MoveExisting -> canonicalCommittedLayout.items.filterNot { it.id == operation.itemId }
        }
        val span = GridSpan(
            rowSpanUnits = movingItem.placement.rowSpanUnits,
            columnSpanUnits = movingItem.placement.columnSpanUnits
        )
        require(span.rowSpanUnits > 0 && span.columnSpanUnits > 0) {
            "Placement operation contains non-positive span."
        }

        val constructed = constructItems(baseItems, movingItem, span, target, policy)
        val normalizedItems = constructed.items
            .map { it.withPlacementAndApproximateKeyData(it.placement) }
            .sortedWith(compareBy({ it.placement.rowUnits }, { it.placement.columnUnits }, { it.id }))

        val layout = canonicalCommittedLayout
            .copyWithItems(normalizedItems)
            .withCanonicalFlexibleBounds()

        check(!hasPlacementIssues(normalizedItems, layout.rowUnitCount, layout.columnUnitCount)) {
            "Constructed flexible placement failed internal consistency check."
        }

        val moved = normalizedItems
            .filter { item -> originalById[item.id]?.placement != null && originalById[item.id]?.placement != item.placement }
            .map { it.id }
            .toMutableSet()
        if (operation is PlacementOperation.MoveExisting) {
            moved += operation.itemId
        }

        return PlacementSolveResult(
            layout = layout,
            movedItemIds = moved,
            insertedItemId = (operation as? PlacementOperation.Insert)?.candidate?.id,
            strategy = constructed.strategy
        )
    }

    private fun constructItems(
        baseItems: List<KeyboardLayoutItem>,
        candidate: KeyboardLayoutItem,
        span: GridSpan,
        target: InsertionTarget,
        policy: InsertionPolicy
    ): ConstructedItems {
        val shifted = baseItems.toMutableList()
        val candidatePlacement: GridPlacement
        val strategy: PlacementStrategy

        when (target) {
            is InsertionTarget.BeforeItem -> {
                val targetItem = shifted.firstOrNull { it.id == target.itemId }
                    ?: error("Missing target item '${target.itemId}'.")
                val row = targetItem.placement.rowUnits
                val column = targetItem.placement.columnUnits
                shiftRight(shifted, row, column, span.columnSpanUnits)
                candidatePlacement = GridPlacement(row, column, span.rowSpanUnits, span.columnSpanUnits)
                strategy = PlacementStrategy.BeforeItemInsertion
            }

            is InsertionTarget.AfterItem -> {
                val targetItem = shifted.firstOrNull { it.id == target.itemId }
                    ?: error("Missing target item '${target.itemId}'.")
                val row = targetItem.placement.rowUnits
                val column = targetItem.placement.columnUnits + targetItem.placement.columnSpanUnits
                shiftRight(shifted, row, column, span.columnSpanUnits)
                candidatePlacement = GridPlacement(row, column, span.rowSpanUnits, span.columnSpanUnits)
                strategy = PlacementStrategy.AfterItemInsertion
            }

            is InsertionTarget.AboveRowGroup -> {
                shiftDown(shifted, target.topRowUnits, span.rowSpanUnits)
                candidatePlacement = GridPlacement(target.topRowUnits, 0, span.rowSpanUnits, span.columnSpanUnits)
                strategy = PlacementStrategy.AboveRowInsertion
            }

            is InsertionTarget.BelowRowGroup -> {
                val rowBottom = shifted
                    .filter { it.placement.rowUnits == target.topRowUnits }
                    .maxOfOrNull { it.placement.rowUnits + it.placement.rowSpanUnits }
                    ?: target.topRowUnits
                shiftDown(shifted, rowBottom, span.rowSpanUnits)
                candidatePlacement = GridPlacement(rowBottom, 0, span.rowSpanUnits, span.columnSpanUnits)
                strategy = PlacementStrategy.BelowRowInsertion
            }

            is InsertionTarget.RowEnd -> {
                val rowItems = shifted.filter { it.placement.rowUnits == target.topRowUnits }
                val column = rowItems.maxOfOrNull { it.placement.columnUnits + it.placement.columnSpanUnits } ?: 0
                candidatePlacement = GridPlacement(target.topRowUnits, column, span.rowSpanUnits, span.columnSpanUnits)
                strategy = PlacementStrategy.RowEndInsertion
            }

            is InsertionTarget.NewBottomRow -> {
                val row = shifted.maxOfOrNull { it.placement.rowUnits + it.placement.rowSpanUnits } ?: 0
                candidatePlacement = GridPlacement(
                    row,
                    target.columnUnits.coerceAtLeast(0),
                    span.rowSpanUnits,
                    span.columnSpanUnits
                )
                strategy = PlacementStrategy.NewBottomRowInsertion
            }

            is InsertionTarget.EmptyArea -> {
                candidatePlacement = target.placement.copy(
                    rowSpanUnits = span.rowSpanUnits,
                    columnSpanUnits = span.columnSpanUnits
                )
                strategy = if (baseItems.none { isPlacementOverlapping(it.placement, candidatePlacement) }) {
                    PlacementStrategy.ExactEmptyPlacement
                } else {
                    when (policy) {
                        InsertionPolicy.PreferVertical -> PlacementStrategy.VerticalInsertion
                        InsertionPolicy.PreferHorizontal -> PlacementStrategy.HorizontalInsertion
                        InsertionPolicy.Auto2D -> PlacementStrategy.Mixed2D
                    }
                }
            }
        }

        val withCandidate = listOf(candidate.withPlacementAndApproximateKeyData(candidatePlacement)) + shifted
        val resolved = resolveOverlaps(withCandidate, lockedItemId = candidate.id, policy = policy)
        return ConstructedItems(resolved, strategy)
    }

    private fun shiftRight(
        items: MutableList<KeyboardLayoutItem>,
        rowUnits: Int,
        fromColumnUnits: Int,
        deltaUnits: Int
    ) {
        replaceAll(items) { item ->
            val p = item.placement
            if (p.rowUnits == rowUnits && p.columnUnits >= fromColumnUnits) {
                item.withPlacementAndApproximateKeyData(p.copy(columnUnits = p.columnUnits + deltaUnits))
            } else {
                item
            }
        }
    }

    private fun shiftDown(
        items: MutableList<KeyboardLayoutItem>,
        fromRowUnits: Int,
        deltaUnits: Int
    ) {
        replaceAll(items) { item ->
            val p = item.placement
            if (p.rowUnits >= fromRowUnits) {
                item.withPlacementAndApproximateKeyData(p.copy(rowUnits = p.rowUnits + deltaUnits))
            } else {
                item
            }
        }
    }

    private fun resolveOverlaps(
        items: List<KeyboardLayoutItem>,
        lockedItemId: String,
        policy: InsertionPolicy
    ): List<KeyboardLayoutItem> {
        val mutable = items.toMutableList()
        var guard = 0
        while (true) {
            var changed = false
            val sorted = mutable.sortedWith(compareBy({ it.placement.rowUnits }, { it.placement.columnUnits }, { it.id }))
            loop@ for (i in sorted.indices) {
                for (j in i + 1 until sorted.size) {
                    val a = sorted[i]
                    val b = sorted[j]
                    if (!isPlacementOverlapping(a.placement, b.placement)) continue
                    val move = when {
                        a.id == lockedItemId -> b
                        b.id == lockedItemId -> a
                        else -> b
                    }
                    val anchor = if (move.id == a.id) b else a
                    val nextPlacement = when (policy) {
                        InsertionPolicy.PreferVertical -> move.placement.copy(
                            rowUnits = anchor.placement.rowUnits + anchor.placement.rowSpanUnits
                        )
                        else -> move.placement.copy(
                            columnUnits = anchor.placement.columnUnits + anchor.placement.columnSpanUnits
                        )
                    }
                    val index = mutable.indexOfFirst { it.id == move.id }
                    mutable[index] = move.withPlacementAndApproximateKeyData(nextPlacement)
                    changed = true
                    break@loop
                }
            }
            if (!changed) return mutable
            guard++
            check(guard < 100_000) {
                "Flexible placement overlap resolution exceeded safety guard."
            }
        }
    }

    private fun replaceAll(
        items: MutableList<KeyboardLayoutItem>,
        transform: (KeyboardLayoutItem) -> KeyboardLayoutItem
    ) {
        for (index in items.indices) {
            items[index] = transform(items[index])
        }
    }

    private fun KeyboardLayoutItem.withPlacementAndApproximateKeyData(
        placement: GridPlacement
    ): KeyboardLayoutItem {
        return when (this) {
            is SpacerItem -> copy(placement = placement)
            is KeyItem -> copy(
                keyData = keyData.copy(
                    row = placement.rowUnits / 2,
                    column = placement.columnUnits / 2,
                    rowSpan = ceil(placement.rowSpanUnits / 2.0).toInt(),
                    colSpan = ceil(placement.columnSpanUnits / 2.0).toInt()
                ),
                placement = placement
            )
        }
    }

    private data class ConstructedItems(
        val items: List<KeyboardLayoutItem>,
        val strategy: PlacementStrategy
    )
}
