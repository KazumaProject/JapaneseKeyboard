package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement

import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.data.hasPlacementIssues
import com.kazumaproject.custom_keyboard.data.isPlacementOverlapping
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlexiblePlacementSolverTest {
    private val solver = FlexiblePlacementSolver()

    @Test fun solver_initialOverlap_isTreatedAsInsertion_notInvalid() { occupiedPreviewCreatesLayout() }
    @Test fun solver_doesNotRejectOccupiedQwertyPosition() { occupiedPreviewCreatesLayout() }
    @Test fun solver_insertOneKey_onOccupiedQwertyPosition_createsPreviewLayout() { occupiedPreviewCreatesLayout() }
    @Test fun solver_insertHalfKey_onOccupiedHalfCellPosition_createsPreviewLayout() {
        assertPreview(candidate("half", 1, 1), InsertionTarget.EmptyArea(GridPlacement(0, 0, 1, 1)))
    }
    @Test fun solver_insertSpacer_onOccupiedQwertyPosition_createsPreviewLayout() {
        assertPreview(spacer("sp", 2, 2), InsertionTarget.EmptyArea(GridPlacement(0, 0, 1, 1)))
    }
    @Test fun solver_insertSpace_onOccupiedQwertyPosition_createsPreviewLayout() {
        assertPreview(space("space", 2, 14), InsertionTarget.EmptyArea(GridPlacement(0, 0, 1, 1)))
    }

    @Test fun solver_beforeItem_insertsBeforeTargetAndShiftsRight() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        val before = keyItem(layout, "qwerty_key_q")
        val result = solve(layout, candidate("new"), InsertionTarget.BeforeItem("qwerty_key_q"))
        val inserted = item(result.layout, result.insertedItemId!!)
        val after = keyItem(result.layout, "qwerty_key_q")
        assertEquals(before.placement.columnUnits, inserted.placement.columnUnits)
        assertEquals(before.placement.columnUnits + 2, after.placement.columnUnits)
        assertNoOverlaps(result.layout)
    }

    @Test fun solver_afterItem_insertsAfterTargetAndShiftsRight() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        val q = keyItem(layout, "qwerty_key_q")
        val result = solve(layout, candidate("new"), InsertionTarget.AfterItem("qwerty_key_q"))
        val inserted = item(result.layout, result.insertedItemId!!)
        assertEquals(q.placement.columnUnits + q.placement.columnSpanUnits, inserted.placement.columnUnits)
        assertNoOverlaps(result.layout)
    }

    @Test fun solver_preferVerticalBeforeItem_insertsInSameColumnLaneAndShiftsLaneDownOnly() {
        val layout = laneLayout()
        val result = solve(
            layout,
            candidate("new"),
            InsertionTarget.BeforeItem("target"),
            InsertionPolicy.PreferVertical
        )

        assertEquals(PlacementStrategy.VerticalInsertion, result.strategy)
        assertEquals(GridPlacement(2, 0, 2, 2), item(result.layout, result.insertedItemId!!).placement)
        assertEquals(GridPlacement(4, 0, 2, 2), item(result.layout, "target").placement)
        assertEquals(GridPlacement(6, 0, 2, 2), item(result.layout, "below").placement)
        assertEquals(GridPlacement(2, 4, 2, 2), item(result.layout, "side").placement)
        assertNoOverlaps(result.layout)
    }

    @Test fun solver_preferVerticalAfterItem_insertsBelowTargetAndShiftsLaneDownOnly() {
        val layout = laneLayout()
        val result = solve(
            layout,
            candidate("new"),
            InsertionTarget.AfterItem("target"),
            InsertionPolicy.PreferVertical
        )

        assertEquals(PlacementStrategy.VerticalInsertion, result.strategy)
        assertEquals(GridPlacement(4, 0, 2, 2), item(result.layout, result.insertedItemId!!).placement)
        assertEquals(GridPlacement(2, 0, 2, 2), item(result.layout, "target").placement)
        assertEquals(GridPlacement(6, 0, 2, 2), item(result.layout, "below").placement)
        assertEquals(GridPlacement(2, 4, 2, 2), item(result.layout, "side").placement)
        assertNoOverlaps(result.layout)
    }

    @Test fun solver_preferHorizontalBeforeItem_keepsRowInsertionBehavior() {
        val layout = horizontalLaneLayout()
        val result = solve(
            layout,
            candidate("new"),
            InsertionTarget.BeforeItem("target"),
            InsertionPolicy.PreferHorizontal
        )

        assertEquals(PlacementStrategy.BeforeItemInsertion, result.strategy)
        assertEquals(GridPlacement(0, 2, 2, 2), item(result.layout, result.insertedItemId!!).placement)
        assertEquals(GridPlacement(0, 4, 2, 2), item(result.layout, "target").placement)
        assertEquals(GridPlacement(0, 6, 2, 2), item(result.layout, "right").placement)
        assertEquals(GridPlacement(2, 2, 2, 2), item(result.layout, "below").placement)
        assertNoOverlaps(result.layout)
    }

    @Test fun solver_preferHorizontalAfterItem_keepsRowInsertionBehavior() {
        val layout = horizontalLaneLayout()
        val result = solve(
            layout,
            candidate("new"),
            InsertionTarget.AfterItem("target"),
            InsertionPolicy.PreferHorizontal
        )

        assertEquals(PlacementStrategy.AfterItemInsertion, result.strategy)
        assertEquals(GridPlacement(0, 4, 2, 2), item(result.layout, result.insertedItemId!!).placement)
        assertEquals(GridPlacement(0, 2, 2, 2), item(result.layout, "target").placement)
        assertEquals(GridPlacement(0, 6, 2, 2), item(result.layout, "right").placement)
        assertEquals(GridPlacement(2, 2, 2, 2), item(result.layout, "below").placement)
        assertNoOverlaps(result.layout)
    }

    @Test fun solver_aboveRowGroup_insertsRowAboveAndShiftsRowsDown() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        val result = solve(layout, candidate("new"), InsertionTarget.AboveRowGroup(2))
        assertEquals(2, item(result.layout, result.insertedItemId!!).placement.rowUnits)
        assertEquals(4, keyItem(result.layout, "qwerty_key_a").placement.rowUnits)
        assertNoOverlaps(result.layout)
    }

    @Test fun solver_belowRowGroup_insertsRowBelowAndShiftsRowsDown() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        val result = solve(layout, candidate("new"), InsertionTarget.BelowRowGroup(0))
        assertEquals(2, item(result.layout, result.insertedItemId!!).placement.rowUnits)
        assertEquals(4, keyItem(result.layout, "qwerty_key_a").placement.rowUnits)
        assertNoOverlaps(result.layout)
    }

    @Test fun solver_rowEnd_appendsToEndAndExpandsColumnUnitCount() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        val result = solve(layout, candidate("new"), InsertionTarget.RowEnd(0))
        assertEquals(20, item(result.layout, result.insertedItemId!!).placement.columnUnits)
        assertTrue(result.layout.columnUnitCount > layout.columnUnitCount)
        assertEquals((result.layout.columnUnitCount + 1) / 2, result.layout.columnCount)
        assertNoOverlaps(result.layout)
    }

    @Test fun solver_newBottomRow_createsNewRowAndExpandsRowUnitCount() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        val result = solve(layout, candidate("new"), InsertionTarget.NewBottomRow(3))
        assertEquals(8, item(result.layout, result.insertedItemId!!).placement.rowUnits)
        assertEquals(10, result.layout.rowUnitCount)
        assertEquals(5, result.layout.rowCount)
        assertNoOverlaps(result.layout)
    }

    @Test fun solver_emptyArea_placesCandidateAndConstructsPreview() {
        val result = solve(KeyboardDefaultLayouts.createQwertyTemplateLayout(), candidate("new"), InsertionTarget.EmptyArea(GridPlacement(9, 4, 1, 1)))
        assertEquals(9, item(result.layout, result.insertedItemId!!).placement.rowUnits)
        assertNoOverlaps(result.layout)
    }

    @Test fun solver_emptyAreaSpacer_expandsWithoutManualRowOrColumn() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        val result = solve(
            layout,
            spacer("spacer_far", rowSpan = 1, columnSpan = 1),
            InsertionTarget.EmptyArea(GridPlacement(10, 23, 1, 1))
        )
        val inserted = item(result.layout, result.insertedItemId!!)
        assertEquals(GridPlacement(10, 23, 1, 1), inserted.placement)
        assertTrue(result.layout.rowUnitCount >= 11)
        assertTrue(result.layout.columnUnitCount >= 24)
        assertNoOverlaps(result.layout)
    }

    @Test fun solver_halfCellPlacement_expandsBoundsWithoutRoundingPlacement() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        val result = solve(
            layout,
            candidate("half", rowSpan = 1, columnSpan = 1),
            InsertionTarget.EmptyArea(GridPlacement(9, 21, 1, 1))
        )
        val inserted = item(result.layout, result.insertedItemId!!)
        assertEquals(GridPlacement(9, 21, 1, 1), inserted.placement)
        assertEquals(10, result.layout.rowUnitCount)
        assertEquals(22, result.layout.columnUnitCount)
        assertNoOverlaps(result.layout)
    }

    @Test fun solver_repeatedHorizontalInsertion_firstSecondThird_allSucceed(): Unit = repeatedHorizontal().let { assertNoOverlaps(it) }
    @Test fun solver_repeatedHorizontalInsertion_expandsColumnUnitCountEachTimeAsNeeded() {
        var layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        repeat(3) { index ->
            val before = layout.columnUnitCount
            layout = solve(layout, candidate("h$index"), InsertionTarget.RowEnd(0)).layout
            assertTrue(layout.columnUnitCount >= before)
        }
    }
    @Test fun solver_repeatedHorizontalInsertion_treatsPreviouslyInsertedItemsAsNormal() {
        val layout = repeatedHorizontal()
        assertTrue(layout.items.any { it.id == "h0" })
        assertTrue(layout.items.any { it.id == "h1" })
        assertTrue(layout.items.any { it.id == "h2" })
        assertNoOverlaps(layout)
    }

    @Test fun solver_insertBelowExistingRows_createsPreviewLayout() {
        assertPreview(candidate("new"), InsertionTarget.NewBottomRow(0))
    }
    @Test fun solver_insertBetweenRows_usesRowInsertionDown(): Unit =
        assertEquals(PlacementStrategy.AboveRowInsertion, solve(KeyboardDefaultLayouts.createQwertyTemplateLayout(), candidate("new"), InsertionTarget.AboveRowGroup(2)).strategy)
    @Test fun solver_repeatedVerticalInsertion_firstSecondThird_allSucceed(): Unit = repeatedVertical().let { assertNoOverlaps(it) }
    @Test fun solver_repeatedVerticalInsertion_expandsRowUnitCountEachTimeAsNeeded() {
        var layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        repeat(3) { index ->
            val before = layout.rowUnitCount
            layout = solve(layout, candidate("v$index"), InsertionTarget.NewBottomRow(0)).layout
            assertTrue(layout.rowUnitCount > before)
        }
    }
    @Test fun solver_repeatedVerticalInsertion_treatsPreviouslyInsertedItemsAsNormal() {
        val layout = repeatedVertical()
        assertTrue(layout.items.any { it.id == "v0" })
        assertTrue(layout.items.any { it.id == "v1" })
        assertTrue(layout.items.any { it.id == "v2" })
        assertNoOverlaps(layout)
    }
    @Test fun solver_verticalInsertion_preservesRowGroupHorizontalPositions() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        val before = keyItem(layout, "qwerty_key_a").placement.columnUnits
        val result = solve(layout, candidate("new"), InsertionTarget.AboveRowGroup(2))
        assertEquals(before, keyItem(result.layout, "qwerty_key_a").placement.columnUnits)
    }
    @Test fun solver_mixed2DInsertion_createsPreviewWhenHorizontalOnlyWouldBeBad(): Unit =
        assertEquals(PlacementStrategy.Mixed2D, solve(KeyboardDefaultLayouts.createQwertyTemplateLayout(), candidate("new"), InsertionTarget.EmptyArea(GridPlacement(0, 0, 1, 1))).strategy)
    @Test fun solver_doesNotMutateInputLayout() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        val before = layout.items
        solve(layout, candidate("new"), InsertionTarget.BeforeItem("qwerty_key_q"))
        assertEquals(before, layout.items)
    }
    @Test fun solver_outputHasNoOverlaps(): Unit = assertNoOverlaps(occupiedPreviewCreatesLayout())
    @Test fun solver_outputContainsCandidateItem() {
        val result = solve(KeyboardDefaultLayouts.createQwertyTemplateLayout(), candidate("new"), InsertionTarget.RowEnd(0))
        assertNotNull(result.layout.items.firstOrNull { it.id == result.insertedItemId })
    }
    @Test fun solver_touchingEdgesAreNotOverlap(): Unit =
        assertFalse(isPlacementOverlapping(GridPlacement(0, 0, 2, 2), GridPlacement(0, 2, 2, 2)))
    @Test fun solver_finalValidationRunsOnlyAfterConstruction() { occupiedPreviewCreatesLayout() }
    @Test fun solver_moveExistingItem_toOccupiedPosition_createsPreviewLayout() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        val result = solver.solve(layout, PlacementOperation.MoveExisting("qwerty_key_p"), InsertionTarget.BeforeItem("qwerty_key_q"))
        assertNoOverlaps(result.layout)
    }
    @Test fun solver_moveExistingItem_toNewBottomRow_staysWithinCanonicalBounds() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        val result = solver.solve(
            layout,
            PlacementOperation.MoveExisting("qwerty_key_p"),
            InsertionTarget.NewBottomRow(0)
        )
        val moved = keyItem(result.layout, "qwerty_key_p")
        assertTrue(moved.placement.rowUnits + moved.placement.rowSpanUnits <= result.layout.rowUnitCount)
        assertNoOverlaps(result.layout)
    }
    @Test fun solver_moveExistingItem_preservesIdentityAndSpan() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        val before = keyItem(layout, "qwerty_key_p")
        val result = solver.solve(layout, PlacementOperation.MoveExisting("qwerty_key_p"), InsertionTarget.BeforeItem("qwerty_key_q"))
        val after = keyItem(result.layout, "qwerty_key_p")
        assertEquals(before.id, after.id)
        assertEquals(before.placement.rowSpanUnits, after.placement.rowSpanUnits)
        assertEquals(before.placement.columnSpanUnits, after.placement.columnSpanUnits)
    }
    @Test fun solver_cancelPreviewDoesNotKeepColumnExpansion() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        solve(layout, candidate("new"), InsertionTarget.RowEnd(0))
        assertEquals(20, layout.columnUnitCount)
    }
    @Test fun solver_cancelPreviewDoesNotKeepRowExpansion() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        solve(layout, candidate("new"), InsertionTarget.NewBottomRow(0))
        assertEquals(8, layout.rowUnitCount)
    }

    private fun occupiedPreviewCreatesLayout(): KeyboardLayout =
        assertPreview(candidate("new"), InsertionTarget.EmptyArea(GridPlacement(0, 0, 1, 1)))

    private fun assertPreview(candidate: com.kazumaproject.custom_keyboard.data.KeyboardLayoutItem, target: InsertionTarget): KeyboardLayout {
        val result = solve(KeyboardDefaultLayouts.createQwertyTemplateLayout(), candidate, target)
        assertNotNull(result.layout.items.firstOrNull { it.id == result.insertedItemId })
        assertNoOverlaps(result.layout)
        return result.layout
    }

    private fun repeatedHorizontal(): KeyboardLayout {
        var layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        repeat(3) { index ->
            layout = solve(layout, candidate("h$index"), InsertionTarget.RowEnd(0)).layout
        }
        return layout
    }

    private fun repeatedVertical(): KeyboardLayout {
        var layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        repeat(3) { index ->
            layout = solve(layout, candidate("v$index"), InsertionTarget.NewBottomRow(0)).layout
        }
        return layout
    }

    private fun solve(
        layout: KeyboardLayout,
        candidate: com.kazumaproject.custom_keyboard.data.KeyboardLayoutItem,
        target: InsertionTarget,
        policy: InsertionPolicy = InsertionPolicy.Auto2D
    ): PlacementSolveResult =
        solver.solve(layout, PlacementOperation.Insert(candidate), target, policy)

    private fun laneLayout(): KeyboardLayout =
        KeyboardLayout(
            keys = emptyList(),
            flickKeyMaps = emptyMap(),
            columnCount = 3,
            rowCount = 4,
            items = listOf(
                candidate("top").copy(placement = GridPlacement(0, 0, 2, 2)),
                candidate("target").copy(placement = GridPlacement(2, 0, 2, 2)),
                candidate("below").copy(placement = GridPlacement(4, 0, 2, 2)),
                candidate("side").copy(placement = GridPlacement(2, 4, 2, 2))
            ),
            columnUnitCount = 6,
            rowUnitCount = 8
        )

    private fun horizontalLaneLayout(): KeyboardLayout =
        KeyboardLayout(
            keys = emptyList(),
            flickKeyMaps = emptyMap(),
            columnCount = 4,
            rowCount = 2,
            items = listOf(
                candidate("left").copy(placement = GridPlacement(0, 0, 2, 2)),
                candidate("target").copy(placement = GridPlacement(0, 2, 2, 2)),
                candidate("right").copy(placement = GridPlacement(0, 4, 2, 2)),
                candidate("below").copy(placement = GridPlacement(2, 2, 2, 2))
            ),
            columnUnitCount = 8,
            rowUnitCount = 4
        )

    private fun candidate(id: String, rowSpan: Int = 2, columnSpan: Int = 2): KeyItem {
        val key = KeyData(
            label = id,
            row = 0,
            column = 0,
            isFlickable = false,
            action = KeyAction.Text(id),
            keyType = KeyType.NORMAL,
            keyId = id
        )
        return KeyItem(id, key, GridPlacement(0, 0, rowSpan, columnSpan))
    }

    private fun space(id: String, rowSpan: Int, columnSpan: Int): KeyItem {
        val key = KeyData(
            label = "",
            row = 0,
            column = 0,
            isFlickable = false,
            action = KeyAction.Space,
            keyType = KeyType.NORMAL,
            isSpecialKey = true,
            keyId = id
        )
        return KeyItem(id, key, GridPlacement(0, 0, rowSpan, columnSpan))
    }

    private fun spacer(id: String, rowSpan: Int, columnSpan: Int): SpacerItem =
        SpacerItem(id, GridPlacement(0, 0, rowSpan, columnSpan))

    private fun keyItem(layout: KeyboardLayout, id: String): KeyItem =
        layout.items.filterIsInstance<KeyItem>().first { it.id == id || it.keyData.keyId == id }

    private fun item(layout: KeyboardLayout, id: String) =
        layout.items.first { it.id == id }

    private fun assertNoOverlaps(layout: KeyboardLayout) {
        assertEquals((layout.rowUnitCount + 1) / 2, layout.rowCount)
        assertEquals((layout.columnUnitCount + 1) / 2, layout.columnCount)
        layout.items.forEach { layoutItem ->
            assertTrue(layoutItem.placement.rowUnits + layoutItem.placement.rowSpanUnits <= layout.rowUnitCount)
            assertTrue(layoutItem.placement.columnUnits + layoutItem.placement.columnSpanUnits <= layout.columnUnitCount)
        }
        assertFalse(hasPlacementIssues(layout.items, layout.rowUnitCount, layout.columnUnitCount))
    }
}
