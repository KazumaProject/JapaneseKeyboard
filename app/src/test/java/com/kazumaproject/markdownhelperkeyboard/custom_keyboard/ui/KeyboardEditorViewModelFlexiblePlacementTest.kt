package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.data.hasPlacementIssues
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

class KeyboardEditorViewModelFlexiblePlacementTest {

    private fun viewModel(): KeyboardEditorViewModel =
        KeyboardEditorViewModel(KeyboardRepository(mock(KeyboardLayoutDao::class.java)))

    private fun qwertyViewModel(): KeyboardEditorViewModel =
        viewModel().apply { applyTemplate(KeyboardDefaultLayouts.createQwertyTemplateLayout()) }

    @Test
    fun qwertyKeyEdit_keepsHalfUnitPlacementAndDoesNotAddEmptyKeys() {
        val viewModel = viewModel()
        viewModel.applyTemplate(KeyboardDefaultLayouts.createQwertyTemplateLayout())

        val beforeLayout = viewModel.uiState.value.layout
        val beforeItem = beforeLayout.items.filterIsInstance<KeyItem>()
            .first { it.keyData.keyId == "qwerty_key_a" }
        val beforeSpacerCount = beforeLayout.items.count { it is SpacerItem }
        val beforeKeyCount = beforeLayout.keys.size

        viewModel.updateKeyAndMappings(
            newKeyData = beforeItem.keyData.copy(label = "A", action = KeyAction.Text("A")),
            flickMap = emptyMap(),
            twoStepMap = emptyMap(),
            longPressFlickMap = emptyMap(),
            twoStepLongPressMap = emptyMap()
        )

        val afterLayout = viewModel.uiState.value.layout
        val afterItem = afterLayout.items.filterIsInstance<KeyItem>()
            .first { it.keyData.keyId == "qwerty_key_a" }

        assertEquals(1, beforeItem.placement.columnUnits)
        assertEquals(beforeItem.placement.rowUnits, afterItem.placement.rowUnits)
        assertEquals(beforeItem.placement.columnUnits, afterItem.placement.columnUnits)
        assertEquals(beforeItem.placement.rowSpanUnits, afterItem.placement.rowSpanUnits)
        assertEquals(beforeItem.placement.columnSpanUnits, afterItem.placement.columnSpanUnits)
        assertEquals("A", afterItem.keyData.label)
        assertEquals(beforeSpacerCount, afterLayout.items.count { it is SpacerItem })
        assertEquals(beforeKeyCount, afterLayout.keys.size)
    }

    @Test
    fun qwertyKeyResize_rejectsSpacerOverlap() {
        val viewModel = viewModel()
        viewModel.applyTemplate(KeyboardDefaultLayouts.createQwertyTemplateLayout())

        val beforeLayout = viewModel.uiState.value.layout
        val shift = beforeLayout.items.filterIsInstance<KeyItem>()
            .first { it.keyData.keyId == "qwerty_shift" }

        viewModel.updateKeyAndMappings(
            newKeyData = shift.keyData.copy(colSpan = 2),
            flickMap = emptyMap(),
            twoStepMap = emptyMap(),
            longPressFlickMap = emptyMap(),
            twoStepLongPressMap = emptyMap()
        )

        val afterLayout = viewModel.uiState.value.layout
        val afterShift = afterLayout.items.filterIsInstance<KeyItem>()
            .first { it.keyData.keyId == "qwerty_shift" }

        assertEquals(shift.placement, afterShift.placement)
        assertEquals(beforeLayout.items, afterLayout.items)
    }

    @Test
    fun addSpacer_rejectsOverlapWithKey() {
        val viewModel = viewModel()
        val key = KeyData(
            label = "a",
            row = 0,
            column = 0,
            isFlickable = false,
            action = KeyAction.Text("a"),
            keyType = KeyType.NORMAL,
            keyId = "key_a"
        )
        viewModel.applyTemplate(
            KeyboardLayout(
                keys = listOf(key),
                flickKeyMaps = emptyMap(),
                columnCount = 2,
                rowCount = 1
            )
        )

        assertFalse(
            viewModel.addSpacer(
                rowUnits = 0,
                columnUnits = 0,
                rowSpanUnits = 1,
                columnSpanUnits = 1
            )
        )
    }

    @Test
    fun addUpdateDeleteSpacer_updatesItemsSourceOfTruth() {
        val viewModel = viewModel()
        viewModel.applyTemplate(
            KeyboardLayout(
                keys = emptyList(),
                flickKeyMaps = emptyMap(),
                columnCount = 2,
                rowCount = 1
            )
        )

        assertTrue(
            viewModel.addSpacer(
                rowUnits = 0,
                columnUnits = 0,
                rowSpanUnits = 1,
                columnSpanUnits = 1
            )
        )
        val addedSpacer = viewModel.uiState.value.layout.items.filterIsInstance<SpacerItem>().single()
        assertEquals(GridPlacement(0, 0, 1, 1), addedSpacer.placement)

        assertTrue(
            viewModel.updateSpacerPlacement(
                addedSpacer.id,
                GridPlacement(rowUnits = 1, columnUnits = 1, rowSpanUnits = 1, columnSpanUnits = 1)
            )
        )
        assertEquals(
            GridPlacement(1, 1, 1, 1),
            viewModel.uiState.value.layout.items.filterIsInstance<SpacerItem>().single().placement
        )

        assertTrue(viewModel.deleteSpacer(addedSpacer.id))
        assertTrue(viewModel.uiState.value.layout.items.none { it is SpacerItem })
    }

    @Test
    fun legacyLayout_keyEditStillUsesLegacyCellBehavior() {
        val viewModel = viewModel()
        viewModel.start(-1L)

        val beforeLayout = viewModel.uiState.value.layout
        val key = beforeLayout.keys.first()

        viewModel.updateKeyAndMappings(
            newKeyData = key.copy(label = "x", action = FlickAction.Input("x").let { KeyAction.Text("x") }),
            flickMap = mapOf(FlickDirection.TAP to FlickAction.Input("x")),
            twoStepMap = emptyMap(),
            longPressFlickMap = emptyMap(),
            twoStepLongPressMap = emptyMap()
        )

        val afterLayout = viewModel.uiState.value.layout
        val afterKey = afterLayout.keys.first { it.keyId == key.keyId }
        assertEquals("x", afterKey.label)
        assertEquals(beforeLayout.columnCount * 2, afterLayout.columnUnitCount)
        assertEquals(beforeLayout.rowCount * 2, afterLayout.rowUnitCount)
    }

    @Test
    fun viewModel_enterNewKeyPlacementMode_setsMode() {
        val vm = qwertyViewModel()
        vm.enterNewKeyPlacementMode(GridSpan(2, 2))
        assertTrue(vm.uiState.value.editorMode is KeyboardEditorMode.PlacingNewKey)
    }

    @Test
    fun viewModel_enterSpacePlacementMode_setsModeAndDoesNotMutateLayout() {
        val vm = qwertyViewModel()
        val before = vm.uiState.value.layout
        vm.enterSpacePlacementMode()
        assertTrue(vm.uiState.value.editorMode is KeyboardEditorMode.PlacingSpaceKey)
        assertEquals(before, vm.uiState.value.layout)
    }

    @Test
    fun viewModel_updatePlacementCursorFromPointer_createsCursorAndPreviewOnly() =
        assertPreviewOnly { it.updatePlacementCursorFromPointer(InsertionTarget.BeforeItem("qwerty_key_q")) }

    @Test
    fun viewModel_updatePlacementCursorFromPointer_previewLayoutContainsAllItems() {
        val vm = qwertyViewModel()
        vm.enterNewKeyPlacementMode(GridSpan(2, 2))
        vm.updatePlacementCursorFromPointer(InsertionTarget.RowEnd(0))
        val preview = vm.uiState.value.previewLayout
        assertNotNull(preview)
        assertValidFlexibleLayout(preview!!)
    }

    @Test
    fun viewModel_holdPlacementCursorFromTap_doesNotCommit() =
        assertPreviewOnly { it.holdPlacementCursorFromTap(InsertionTarget.BeforeItem("qwerty_key_q")) }

    @Test
    fun viewModel_holdPlacementCursorFromDrop_doesNotCommit() =
        assertPreviewOnly { it.holdPlacementCursorFromDrop(InsertionTarget.BeforeItem("qwerty_key_q")) }

    @Test
    fun viewModel_nudgePlacementCursor_updatesCursorAndPreviewOnly() =
        assertPreviewOnly { it.nudgePlacementCursor(NudgeDirection.Right) }

    @Test
    fun viewModel_cyclePlacementCursorTarget_updatesCursorAndPreviewOnly() =
        assertPreviewOnly { it.cyclePlacementCursorTarget() }

    @Test
    fun viewModel_confirmPlacementPreview_commitsPreviewLayout() {
        val vm = qwertyViewModel()
        vm.enterNewKeyPlacementMode(GridSpan(2, 2))
        vm.holdPlacementCursorFromTap(InsertionTarget.RowEnd(0))
        val preview = vm.uiState.value.previewLayout
        assertTrue(vm.confirmPlacementPreview())
        assertEquals(preview, vm.uiState.value.layout)
    }

    @Test
    fun viewModel_cancelPlacementPreview_discardsPreviewAndKeepsCommittedLayout() {
        val vm = qwertyViewModel()
        val before = vm.uiState.value.layout
        vm.enterNewKeyPlacementMode(GridSpan(2, 2))
        vm.holdPlacementCursorFromTap(InsertionTarget.RowEnd(0))
        vm.cancelPlacementPreview()
        assertEquals(before, vm.uiState.value.layout)
        assertNull(vm.uiState.value.previewLayout)
    }

    @Test
    fun viewModel_confirmAfterHorizontalInsertion_commitsExpandedColumnUnitCount() {
        val vm = qwertyViewModel()
        vm.enterNewKeyPlacementMode(GridSpan(2, 2))
        vm.holdPlacementCursorFromTap(InsertionTarget.RowEnd(0))
        vm.confirmPlacementPreview()
        assertTrue(vm.uiState.value.layout.columnUnitCount > 20)
    }

    @Test
    fun viewModel_cancelAfterHorizontalInsertion_discardsExpandedColumnUnitCount() {
        val vm = qwertyViewModel()
        vm.enterNewKeyPlacementMode(GridSpan(2, 2))
        vm.holdPlacementCursorFromTap(InsertionTarget.RowEnd(0))
        vm.cancelPlacementPreview()
        assertEquals(20, vm.uiState.value.layout.columnUnitCount)
    }

    @Test
    fun viewModel_confirmAfterVerticalInsertion_commitsExpandedRowUnitCount() {
        val vm = qwertyViewModel()
        vm.enterNewKeyPlacementMode(GridSpan(2, 2))
        vm.holdPlacementCursorFromTap(InsertionTarget.NewBottomRow(0))
        vm.confirmPlacementPreview()
        assertTrue(vm.uiState.value.layout.rowUnitCount > 8)
        assertValidFlexibleLayout(vm.uiState.value.layout)
    }

    @Test
    fun viewModel_cancelAfterVerticalInsertion_discardsExpandedRowUnitCount() {
        val vm = qwertyViewModel()
        vm.enterNewKeyPlacementMode(GridSpan(2, 2))
        vm.holdPlacementCursorFromTap(InsertionTarget.NewBottomRow(0))
        vm.cancelPlacementPreview()
        assertEquals(8, vm.uiState.value.layout.rowUnitCount)
    }

    @Test
    fun viewModel_spaceConfirm_doesNotEmitKeyEditorNavigation() {
        val vm = qwertyViewModel()
        vm.enterSpacePlacementMode()
        vm.holdPlacementCursorFromTap(InsertionTarget.RowEnd(0))
        vm.confirmPlacementPreview()
        assertNull(vm.uiState.value.selectedKeyIdentifier)
    }

    @Test
    fun viewModel_canAddKeyWithoutPreAddingRowsOrColumns() {
        val vm = qwertyViewModel()
        val beforeRows = vm.uiState.value.layout.rowUnitCount
        vm.enterNewKeyPlacementMode(GridSpan(2, 2))
        vm.holdPlacementCursorFromTap(InsertionTarget.EmptyArea(GridPlacement(beforeRows, 21, 2, 2)))
        assertNotNull(vm.uiState.value.previewLayout)
        assertTrue(vm.confirmPlacementPreview())
        assertValidFlexibleLayout(vm.uiState.value.layout)
        assertTrue(vm.uiState.value.layout.items.filterIsInstance<KeyItem>().any { it.id.startsWith("key_") })
    }

    @Test
    fun viewModel_canAddSpacerWithoutPreAddingRowsOrColumns() {
        val vm = qwertyViewModel()
        vm.enterSpacerPlacementMode(GridSpan(1, 1))
        vm.holdPlacementCursorFromTap(InsertionTarget.EmptyArea(GridPlacement(9, 22, 1, 1)))
        assertNotNull(vm.uiState.value.previewLayout)
        assertTrue(vm.confirmPlacementPreview())
        assertValidFlexibleLayout(vm.uiState.value.layout)
        assertTrue(vm.uiState.value.layout.items.filterIsInstance<SpacerItem>().any { it.id.startsWith("spacer_") })
    }

    @Test
    fun viewModel_previewTargetForUncommittedItemFallsBackSafely() {
        val vm = qwertyViewModel()
        vm.enterNewKeyPlacementMode(GridSpan(2, 2))
        vm.holdPlacementCursorFromTap(InsertionTarget.RowEnd(0))
        val previewId = vm.uiState.value.previewInsertedItemId
        assertNotNull(previewId)
        vm.updatePlacementCursorFromPointer(InsertionTarget.AfterItem(previewId!!))
        assertNotNull(vm.uiState.value.previewLayout)
        assertFalse(vm.uiState.value.placementCursor!!.target is InsertionTarget.AfterItem &&
                (vm.uiState.value.placementCursor!!.target as InsertionTarget.AfterItem).itemId == previewId)
        assertValidFlexibleLayout(vm.uiState.value.previewLayout!!)
    }

    @Test
    fun viewModel_removeAndDeleteRowColumnNeverPublishInvalidFlexibleLayout() {
        val vm = qwertyViewModel()
        vm.removeRow()
        assertValidFlexibleLayout(vm.uiState.value.layout)
        vm.removeColumn()
        assertValidFlexibleLayout(vm.uiState.value.layout)
        vm.deleteRowAt(0)
        assertValidFlexibleLayout(vm.uiState.value.layout)
        vm.deleteColumnAt(0)
        assertValidFlexibleLayout(vm.uiState.value.layout)
    }

    @Test
    fun viewModel_normalModeKeyTap_emitsKeyEditorNavigation() {
        val vm = qwertyViewModel()
        assertTrue(vm.onKeyTapped("qwerty_key_q"))
        assertEquals("qwerty_key_q", vm.uiState.value.selectedKeyIdentifier)
    }

    @Test
    fun viewModel_placementModeKeyTap_doesNotEmitKeyEditorNavigation() {
        val vm = qwertyViewModel()
        vm.enterNewKeyPlacementMode(GridSpan(2, 2))
        assertFalse(vm.onKeyTapped("qwerty_key_q"))
        assertNull(vm.uiState.value.selectedKeyIdentifier)
    }

    @Test
    fun viewModel_spacerTap_selectsSpacerAndDoesNotNavigate() {
        val vm = qwertyViewModel()
        val spacer = vm.uiState.value.layout.items.filterIsInstance<SpacerItem>().first()
        vm.onSpacerTapped(spacer.id)
        assertEquals(spacer.id, vm.uiState.value.selectedItemId)
        assertNull(vm.uiState.value.selectedKeyIdentifier)
    }

    @Test
    fun viewModel_deleteSelectedSpacer_removesSpacer() {
        val vm = qwertyViewModel()
        val spacer = vm.uiState.value.layout.items.filterIsInstance<SpacerItem>().first()
        vm.selectItem(spacer.id)
        assertTrue(vm.deleteSelectedItem())
        assertTrue(vm.uiState.value.layout.items.none { it.id == spacer.id })
    }

    @Test
    fun viewModel_deleteSelectedKey_removesKeyAndMappings() {
        val vm = viewModel()
        val key = KeyData("x", 0, 0, false, KeyAction.Text("x"), keyType = KeyType.NORMAL, keyId = "x")
        vm.applyTemplate(
            KeyboardLayout(
                keys = listOf(key),
                flickKeyMaps = mapOf("x" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("x")))),
                columnCount = 1,
                rowCount = 1,
                items = listOf(KeyItem("x", key, GridPlacement(0, 0, 2, 2)))
            )
        )
        vm.selectItem("x")
        assertTrue(vm.deleteSelectedItem())
        assertTrue(vm.uiState.value.layout.items.none { it.id == "x" })
        assertFalse(vm.uiState.value.layout.flickKeyMaps.containsKey("x"))
    }

    @Test
    fun viewModel_editKeyAfterMove_keepsExactGridPlacement() {
        val vm = qwertyViewModel()
        val before = vm.uiState.value.layout.items.filterIsInstance<KeyItem>()
            .first { it.id == "qwerty_key_p" }
        vm.enterMoveItemMode("qwerty_key_p")
        vm.holdPlacementCursorFromTap(InsertionTarget.BeforeItem("qwerty_key_q"))
        vm.confirmPlacementPreview()
        val moved = vm.uiState.value.layout.items.filterIsInstance<KeyItem>()
            .first { it.id == "qwerty_key_p" }
        assertNotEquals(before.placement, moved.placement)
        vm.updateKeyAndMappings(
            newKeyData = moved.keyData.copy(label = "P"),
            flickMap = emptyMap(),
            twoStepMap = emptyMap(),
            longPressFlickMap = emptyMap(),
            twoStepLongPressMap = emptyMap()
        )
        val edited = vm.uiState.value.layout.items.filterIsInstance<KeyItem>()
            .first { it.id == "qwerty_key_p" }
        assertEquals(moved.placement, edited.placement)
    }

    private fun assertPreviewOnly(action: (KeyboardEditorViewModel) -> Unit) {
        val vm = qwertyViewModel()
        val before = vm.uiState.value.layout
        vm.enterNewKeyPlacementMode(GridSpan(2, 2))
        vm.holdPlacementCursorFromTap(InsertionTarget.BeforeItem("qwerty_key_q"))
        action(vm)
        assertEquals(before, vm.uiState.value.layout)
        assertNotNull(vm.uiState.value.previewLayout)
        assertNotNull(vm.uiState.value.placementCursor)
    }

    private fun assertValidFlexibleLayout(layout: KeyboardLayout) {
        assertEquals((layout.rowUnitCount + 1) / 2, layout.rowCount)
        assertEquals((layout.columnUnitCount + 1) / 2, layout.columnCount)
        assertFalse(hasPlacementIssues(layout.items, layout.rowUnitCount, layout.columnUnitCount))
    }
}
