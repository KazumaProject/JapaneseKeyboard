package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database.KeyboardLayoutDao
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.GridSpan
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.InsertionTarget
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.KeyboardEditorMode
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.NudgeDirection
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class PlacementCursorStateTest {

    @Test fun cursor_paletteHalfKey_entersPlacementModeWithoutMutatingLayout(): Unit =
        assertPaletteDoesNotMutate { it.enterNewKeyPlacementMode(GridSpan(1, 1)) }
    @Test fun cursor_paletteOneKey_entersPlacementModeWithoutMutatingLayout(): Unit =
        assertPaletteDoesNotMutate { it.enterNewKeyPlacementMode(GridSpan(2, 2)) }
    @Test fun cursor_paletteHalfSpacer_entersPlacementModeWithoutMutatingLayout(): Unit =
        assertPaletteDoesNotMutate { it.enterSpacerPlacementMode(GridSpan(1, 1)) }
    @Test fun cursor_paletteOneSpacer_entersPlacementModeWithoutMutatingLayout(): Unit =
        assertPaletteDoesNotMutate { it.enterSpacerPlacementMode(GridSpan(2, 2)) }
    @Test fun cursor_placeSpace_entersSpacePlacementModeWithoutMutatingLayout(): Unit =
        assertPaletteDoesNotMutate { it.enterSpacePlacementMode() }

    @Test fun cursor_tapGrid_updatesPlacementCursorAndPreviewOnly(): Unit = previewOnly { it.holdPlacementCursorFromTap(beforeQ()) }
    @Test fun cursor_dragPointer_updatesPlacementCursorAndPreviewOnly(): Unit = previewOnly { it.updatePlacementCursorFromPointer(beforeQ()) }
    @Test fun cursor_drop_updatesPlacementCursorAndPreviewOnly(): Unit = previewOnly { it.holdPlacementCursorFromDrop(beforeQ()) }
    @Test fun cursor_dropDoesNotCommit(): Unit = previewOnly { it.holdPlacementCursorFromDrop(beforeQ()) }
    @Test fun cursor_tapDoesNotCommit(): Unit = previewOnly { it.holdPlacementCursorFromTap(beforeQ()) }
    @Test fun cursor_dragEndDoesNotCommit(): Unit = previewOnly { it.updatePlacementCursorFromPointer(beforeQ()) }

    @Test fun cursor_confirmCommitsPreview() {
        val vm = qwertyVm()
        vm.enterNewKeyPlacementMode(GridSpan(2, 2))
        vm.holdPlacementCursorFromTap(beforeQ())
        val preview = vm.uiState.value.previewLayout
        assertTrue(vm.confirmPlacementPreview())
        assertEquals(preview, vm.uiState.value.layout)
        assertNull(vm.uiState.value.previewLayout)
    }

    @Test fun cursor_cancelClearsCursorAndPreviewWithoutMutatingLayout() {
        val vm = qwertyVm()
        val before = vm.uiState.value.layout
        vm.enterNewKeyPlacementMode(GridSpan(2, 2))
        vm.holdPlacementCursorFromTap(beforeQ())
        vm.cancelPlacementPreview()
        assertEquals(before, vm.uiState.value.layout)
        assertNull(vm.uiState.value.previewLayout)
        assertNull(vm.uiState.value.placementCursor)
    }

    @Test fun cursor_stateSurvivesPreviewUpdatesUntilConfirmOrCancel() {
        val vm = qwertyVm()
        vm.enterNewKeyPlacementMode(GridSpan(2, 2))
        vm.holdPlacementCursorFromTap(beforeQ())
        vm.updatePlacementCursorFromPointer(InsertionTarget.AfterItem("qwerty_key_q"))
        assertNotNull(vm.uiState.value.placementCursor)
        assertNotNull(vm.uiState.value.previewLayout)
        assertTrue(vm.uiState.value.editorMode is KeyboardEditorMode.PlacingNewKey)
    }

    @Test fun nudgeRight_beforeItem_movesToAfterSameItem(): Unit = assertNudge(beforeQ(), NudgeDirection.Right, InsertionTarget.AfterItem("qwerty_key_q"))
    @Test fun nudgeRight_afterItem_movesToBeforeNextItem(): Unit = assertNudge(InsertionTarget.AfterItem("qwerty_key_q"), NudgeDirection.Right, InsertionTarget.BeforeItem("qwerty_key_w"))
    @Test fun nudgeLeft_beforeItem_movesToAfterPreviousItem(): Unit = assertNudge(InsertionTarget.BeforeItem("qwerty_key_w"), NudgeDirection.Left, InsertionTarget.AfterItem("qwerty_key_q"))
    @Test fun nudgeLeft_atRowStart_staysDeterministicAndUpdatesPreview(): Unit = assertNudge(beforeQ(), NudgeDirection.Left, beforeQ())
    @Test fun nudgeRight_atRowEnd_movesToRowEndOrExpandsPreview() {
        val vm = cursorAt(InsertionTarget.AfterItem("qwerty_key_p"))
        vm.nudgePlacementCursor(NudgeDirection.Right)
        assertEquals(InsertionTarget.RowEnd(0), vm.uiState.value.placementCursor!!.target)
        assertNotNull(vm.uiState.value.previewLayout)
    }
    @Test fun nudgeDown_aboveRow_movesToBelowSameRowOrAboveNextRow(): Unit = assertNudge(InsertionTarget.AboveRowGroup(0), NudgeDirection.Down, InsertionTarget.BelowRowGroup(0))
    @Test fun nudgeDown_reachesNewBottomRow() {
        val vm = cursorAt(InsertionTarget.BelowRowGroup(6))
        vm.nudgePlacementCursor(NudgeDirection.Down)
        assertEquals(InsertionTarget.NewBottomRow(0), vm.uiState.value.placementCursor!!.target)
    }
    @Test fun nudgeUp_fromBelowRow_movesToAboveSameRowOrPreviousRow(): Unit = assertNudge(InsertionTarget.BelowRowGroup(0), NudgeDirection.Up, InsertionTarget.AboveRowGroup(0))
    @Test fun nudge_updatesPreviewLayout() {
        val vm = cursorAt(beforeQ())
        vm.nudgePlacementCursor(NudgeDirection.Right)
        assertNotNull(vm.uiState.value.previewLayout)
    }
    @Test fun nudge_doesNotCommitLayout(): Unit = previewOnly { it.nudgePlacementCursor(NudgeDirection.Right) }
    @Test fun repeatedNudgeRight_movesAcrossMultipleInsertionSlots() {
        val vm = cursorAt(beforeQ())
        repeat(4) { vm.nudgePlacementCursor(NudgeDirection.Right) }
        assertEquals(InsertionTarget.BeforeItem("qwerty_key_e"), vm.uiState.value.placementCursor!!.target)
    }
    @Test fun repeatedNudgeDown_movesAcrossMultipleRowTargets() {
        val vm = cursorAt(InsertionTarget.AboveRowGroup(0))
        repeat(3) { vm.nudgePlacementCursor(NudgeDirection.Down) }
        assertEquals(InsertionTarget.BelowRowGroup(2), vm.uiState.value.placementCursor!!.target)
    }

    @Test fun cycle_itemBasedCursor_beforeAfterAboveBelowLoop() {
        val vm = cursorAt(beforeQ())
        vm.cyclePlacementCursorTarget()
        assertEquals(InsertionTarget.AfterItem("qwerty_key_q"), vm.uiState.value.placementCursor!!.target)
        vm.cyclePlacementCursorTarget()
        assertEquals(InsertionTarget.AboveRowGroup(0), vm.uiState.value.placementCursor!!.target)
        vm.cyclePlacementCursorTarget()
        assertEquals(InsertionTarget.BelowRowGroup(0), vm.uiState.value.placementCursor!!.target)
    }
    @Test fun cycle_beforeItem_toAfterItem_updatesPreview(): Unit = assertCycle(beforeQ(), InsertionTarget.AfterItem("qwerty_key_q"))
    @Test fun cycle_afterItem_toAboveRowGroup_updatesPreview(): Unit = assertCycle(InsertionTarget.AfterItem("qwerty_key_q"), InsertionTarget.AboveRowGroup(0))
    @Test fun cycle_aboveRowGroup_toBelowRowGroup_updatesPreview(): Unit = assertCycle(InsertionTarget.AboveRowGroup(0), InsertionTarget.BelowRowGroup(0))
    @Test fun cycle_doesNotCommitLayout(): Unit = previewOnly { it.cyclePlacementCursorTarget() }
    @Test fun repeatedCycle_isDeterministic() {
        val a = cursorAt(beforeQ())
        val b = cursorAt(beforeQ())
        repeat(6) { a.cyclePlacementCursorTarget(); b.cyclePlacementCursorTarget() }
        assertEquals(a.uiState.value.placementCursor!!.target, b.uiState.value.placementCursor!!.target)
    }
    @Test fun cycle_rowBasedCursor_cyclesAboveBelowRowEnd() {
        val vm = cursorAt(InsertionTarget.RowEnd(0))
        vm.cyclePlacementCursorTarget()
        assertEquals(InsertionTarget.AboveRowGroup(0), vm.uiState.value.placementCursor!!.target)
    }
    @Test fun cycle_preservesPlacementModeAndSpan() {
        val vm = cursorAt(beforeQ())
        val mode = vm.uiState.value.editorMode
        vm.cyclePlacementCursorTarget()
        assertEquals(mode, vm.uiState.value.editorMode)
        assertEquals(GridSpan(2, 2), vm.uiState.value.placementCursor!!.span)
    }

    @Test fun regression_noCannotPlaceHereForOccupiedQwertyPosition(): Unit = previewOnly { it.holdPlacementCursorFromTap(InsertionTarget.EmptyArea(GridPlacement(0, 0, 1, 1))) }
    @Test fun regression_noOneItemOnlyInsertionLimit() {
        val vm = qwertyVm()
        repeat(3) {
            vm.enterNewKeyPlacementMode(GridSpan(2, 2))
            vm.holdPlacementCursorFromTap(InsertionTarget.RowEnd(0))
            assertTrue(vm.confirmPlacementPreview())
        }
        assertTrue(vm.uiState.value.layout.items.filterIsInstance<KeyItem>().size >= 34)
    }
    @Test fun regression_noHorizontalOnlyInsertionDesign(): Unit = previewOnly { it.holdPlacementCursorFromTap(InsertionTarget.AboveRowGroup(0)) }
    @Test fun regression_dropNeverCommits(): Unit = cursor_dropDoesNotCommit()
    @Test fun regression_tapNeverCommits(): Unit = cursor_tapDoesNotCommit()
    @Test fun regression_nudgeNeverCommits(): Unit = nudge_doesNotCommitLayout()
    @Test fun regression_cycleNeverCommits(): Unit = cycle_doesNotCommitLayout()
    @Test fun regression_confirmIsOnlyCommitPath(): Unit = cursor_confirmCommitsPreview()
    @Test fun regression_cancelRestoresOriginalCommittedLayout(): Unit = cursor_cancelClearsCursorAndPreviewWithoutMutatingLayout()
    @Test fun regression_paletteButtonDoesNotImmediatelyAddItem(): Unit = cursor_paletteOneKey_entersPlacementModeWithoutMutatingLayout()
    @Test fun regression_rawGridPlacementOnlyIsNotUsedForInsertionIntent() {
        val vm = cursorAt(beforeQ())
        assertTrue(vm.uiState.value.placementCursor!!.target is InsertionTarget.BeforeItem)
    }
    @Test fun regression_insertionTargetIsPreservedInState() {
        val vm = cursorAt(InsertionTarget.RowEnd(0))
        assertEquals(InsertionTarget.RowEnd(0), vm.uiState.value.placementCursor!!.target)
    }
    @Test fun regression_spacePlacementDoesNotAutoNavigateToEditor() {
        val vm = qwertyVm()
        vm.enterSpacePlacementMode()
        vm.holdPlacementCursorFromTap(beforeQ())
        vm.confirmPlacementPreview()
        assertNull(vm.uiState.value.selectedKeyIdentifier)
    }

    private fun assertPaletteDoesNotMutate(action: (KeyboardEditorViewModel) -> Unit) {
        val vm = qwertyVm()
        val before = vm.uiState.value.layout
        action(vm)
        assertEquals(before, vm.uiState.value.layout)
        assertNull(vm.uiState.value.previewLayout)
        assertTrue(vm.uiState.value.editorMode != KeyboardEditorMode.Normal)
    }

    private fun previewOnly(action: (KeyboardEditorViewModel) -> Unit) {
        val vm = cursorAt(beforeQ())
        val before = vm.uiState.value.layout
        action(vm)
        assertEquals(before, vm.uiState.value.layout)
        assertNotNull(vm.uiState.value.previewLayout)
    }

    private fun assertNudge(start: InsertionTarget, direction: NudgeDirection, expected: InsertionTarget) {
        val vm = cursorAt(start)
        vm.nudgePlacementCursor(direction)
        assertEquals(expected, vm.uiState.value.placementCursor!!.target)
        assertNotNull(vm.uiState.value.previewLayout)
    }

    private fun assertCycle(start: InsertionTarget, expected: InsertionTarget) {
        val vm = cursorAt(start)
        vm.cyclePlacementCursorTarget()
        assertEquals(expected, vm.uiState.value.placementCursor!!.target)
        assertNotNull(vm.uiState.value.previewLayout)
    }

    private fun cursorAt(target: InsertionTarget): KeyboardEditorViewModel =
        qwertyVm().apply {
            enterNewKeyPlacementMode(GridSpan(2, 2))
            holdPlacementCursorFromTap(target)
        }

    private fun qwertyVm(): KeyboardEditorViewModel =
        KeyboardEditorViewModel(KeyboardRepository(mock(KeyboardLayoutDao::class.java))).apply {
            applyTemplate(KeyboardDefaultLayouts.createQwertyTemplateLayout())
        }

    private fun beforeQ(): InsertionTarget = InsertionTarget.BeforeItem("qwerty_key_q")
}
