package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.data.TfbiFlickNode
import com.kazumaproject.custom_keyboard.data.hasPlacementIssues
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database.KeyboardLayoutDao
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.GridSpan
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.InsertionPolicy
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
    fun editorUiState_insertionPolicyDefaultsToPreferHorizontal() {
        assertEquals(InsertionPolicy.PreferHorizontal, EditorUiState().insertionPolicy)
    }

    @Test
    fun viewModel_updateInsertionPolicy_updatesState() {
        val vm = qwertyViewModel()
        vm.updateInsertionPolicy(InsertionPolicy.PreferVertical)
        assertEquals(InsertionPolicy.PreferVertical, vm.uiState.value.insertionPolicy)
    }

    @Test
    fun viewModel_enterPlacementMode_usesCurrentInsertionPolicyByDefault() {
        val vm = qwertyViewModel()
        vm.updateInsertionPolicy(InsertionPolicy.PreferVertical)

        vm.enterNewKeyPlacementMode(GridSpan(2, 2))
        val keyMode = vm.uiState.value.editorMode as KeyboardEditorMode.PlacingNewKey
        assertEquals(InsertionPolicy.PreferVertical, keyMode.policy)

        vm.enterSpacerPlacementMode(GridSpan(1, 1))
        val spacerMode = vm.uiState.value.editorMode as KeyboardEditorMode.PlacingSpacer
        assertEquals(InsertionPolicy.PreferVertical, spacerMode.policy)
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
    fun viewModel_deleteSelectedSpacer_compactsExpandedFlexibleBounds() {
        val vm = qwertyViewModel()
        val baseLayout = vm.uiState.value.layout
        assertEquals(20, baseLayout.columnUnitCount)
        assertTrue(vm.addSpacer(rowUnits = 0, columnUnits = 20, rowSpanUnits = 2, columnSpanUnits = 2))

        val expandedLayout = vm.uiState.value.layout
        val addedSpacer = expandedLayout.items.filterIsInstance<SpacerItem>()
            .first { it.placement.columnUnits == 20 }
        assertEquals(22, expandedLayout.columnUnitCount)

        vm.selectItem(addedSpacer.id)
        assertTrue(vm.deleteSelectedItem())

        val compacted = vm.uiState.value.layout
        assertTrue(compacted.items.none { it.id == addedSpacer.id })
        assertEquals(baseLayout.columnUnitCount, compacted.columnUnitCount)
        assertEquals(baseLayout.rowUnitCount, compacted.rowUnitCount)
        assertValidFlexibleLayout(compacted)
    }

    @Test
    fun viewModel_deleteSelectedSpacer_compactsHorizontalInternalGap() {
        val vm = viewModel()
        vm.applyTemplate(horizontalGapLayout())
        val before = vm.uiState.value.layout

        vm.updateInsertionPolicy(InsertionPolicy.PreferHorizontal)
        vm.selectItem("spacer")
        assertTrue(vm.deleteSelectedItem())

        val after = vm.uiState.value.layout
        assertTrue(after.items.none { it.id == "spacer" })
        assertEquals(GridPlacement(0, 0, 2, 2), item(after, "a").placement)
        assertEquals(GridPlacement(0, 2, 2, 2), item(after, "b").placement)
        assertEquals(GridPlacement(0, 4, 2, 2), item(after, "c").placement)
        assertEquals(GridPlacement(0, 6, 2, 2), item(after, "d").placement)
        assertEquals(before.columnUnitCount - 2, after.columnUnitCount)
        assertValidFlexibleLayout(after)
    }

    @Test
    fun viewModel_deleteSelectedSpacer_compactsVerticalInternalGap() {
        val vm = viewModel()
        vm.applyTemplate(verticalGapLayout())

        vm.updateInsertionPolicy(InsertionPolicy.PreferVertical)
        vm.selectItem("spacer")
        assertTrue(vm.deleteSelectedItem())

        val after = vm.uiState.value.layout
        assertTrue(after.items.none { it.id == "spacer" })
        assertEquals(GridPlacement(0, 0, 2, 2), item(after, "a").placement)
        assertEquals(GridPlacement(2, 0, 2, 2), item(after, "c").placement)
        assertEquals(GridPlacement(4, 0, 2, 2), item(after, "d").placement)
        assertEquals(GridPlacement(2, 4, 2, 2), item(after, "side").placement)
        assertEquals(6, after.rowUnitCount)
        assertValidFlexibleLayout(after)
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
    fun viewModel_deleteSelectedKey_removesEveryMappingType() {
        val vm = viewModel()
        val key = KeyData("x", 0, 0, false, KeyAction.Text("x"), keyType = KeyType.NORMAL, keyId = "x")
        vm.applyTemplate(
            KeyboardLayout(
                keys = listOf(key),
                flickKeyMaps = mapOf("x" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("x")))),
                circularFlickKeyMaps = mapOf("x" to listOf(mapOf(CircularFlickDirection.TAP to FlickAction.Input("x")))),
                twoStepFlickKeyMaps = mapOf("x" to mapOf(TfbiFlickDirection.TAP to mapOf(TfbiFlickDirection.RIGHT to "x"))),
                longPressFlickKeyMaps = mapOf("x" to mapOf(FlickDirection.UP to "x")),
                twoStepLongPressKeyMaps = mapOf("x" to mapOf(TfbiFlickDirection.TAP to mapOf(TfbiFlickDirection.LEFT to "x"))),
                hierarchicalFlickMaps = mapOf(
                    "x" to TfbiFlickNode.StatefulKey(
                        normalMap = mapOf(TfbiFlickDirection.TAP to TfbiFlickNode.Input("x")),
                        label = "x"
                    )
                ),
                columnCount = 1,
                rowCount = 1,
                items = listOf(KeyItem("x", key, GridPlacement(0, 0, 2, 2)))
            )
        )

        vm.selectItem("x")
        assertTrue(vm.deleteSelectedItem())

        val layout = vm.uiState.value.layout
        assertFalse(layout.flickKeyMaps.containsKey("x"))
        assertFalse(layout.circularFlickKeyMaps.containsKey("x"))
        assertFalse(layout.twoStepFlickKeyMaps.containsKey("x"))
        assertFalse(layout.longPressFlickKeyMaps.containsKey("x"))
        assertFalse(layout.twoStepLongPressKeyMaps.containsKey("x"))
        assertFalse(layout.hierarchicalFlickMaps.containsKey("x"))
    }

    @Test
    fun viewModel_deleteSelectedSpacer_keepsKeyMappings() {
        val vm = viewModel()
        val key = KeyData("x", 0, 0, false, KeyAction.Text("x"), keyType = KeyType.NORMAL, keyId = "x")
        val spacer = SpacerItem("spacer", GridPlacement(0, 2, 2, 2))
        vm.applyTemplate(
            KeyboardLayout(
                keys = listOf(key),
                flickKeyMaps = mapOf("x" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("x")))),
                columnCount = 2,
                rowCount = 1,
                items = listOf(KeyItem("x", key, GridPlacement(0, 0, 2, 2)), spacer)
            )
        )

        vm.selectItem(spacer.id)
        assertTrue(vm.deleteSelectedItem())

        assertTrue(vm.uiState.value.layout.items.none { it.id == spacer.id })
        assertTrue(vm.uiState.value.layout.flickKeyMaps.containsKey("x"))
    }

    @Test
    fun viewModel_deleteSelectedItem_matchesKeyItemId() {
        val vm = viewModel()
        vm.applyTemplate(singleKeyLayout(itemId = "item_x", keyId = "key_x"))
        val itemId = vm.uiState.value.layout.items.filterIsInstance<KeyItem>().single().id

        vm.selectItem(itemId)
        assertTrue(vm.deleteSelectedItem())

        assertTrue(vm.uiState.value.layout.items.none { it.id == itemId })
    }

    @Test
    fun viewModel_deleteSelectedItem_matchesKeyDataKeyId() {
        val vm = viewModel()
        vm.applyTemplate(singleKeyLayout(itemId = "item_x", keyId = "key_x"))

        vm.selectItem("key_x")
        assertTrue(vm.deleteSelectedItem())

        assertTrue(vm.uiState.value.layout.items.none { it.id == "item_x" })
    }

    @Test
    fun viewModel_deleteSelectedItem_matchesSpacerId() {
        val vm = viewModel()
        vm.applyTemplate(horizontalGapLayout())

        vm.selectItem("spacer")
        assertTrue(vm.deleteSelectedItem())

        assertTrue(vm.uiState.value.layout.items.none { it.id == "spacer" })
    }

    @Test
    fun viewModel_preferHorizontalInsertion_pushesOverlapRightForKeysAndSpacers() {
        val keyVm = qwertyViewModel()
        val beforeKey = keyVm.uiState.value.layout.items.filterIsInstance<KeyItem>()
            .first { it.id == "qwerty_key_q" }
        keyVm.enterNewKeyPlacementMode(GridSpan(2, 2), InsertionPolicy.PreferHorizontal)
        keyVm.holdPlacementCursorFromTap(InsertionTarget.EmptyArea(GridPlacement(0, 0, 2, 2)))
        val afterKey = keyVm.uiState.value.previewLayout!!.items.filterIsInstance<KeyItem>()
            .first { it.id == "qwerty_key_q" }
        assertEquals(beforeKey.placement.rowUnits, afterKey.placement.rowUnits)
        assertTrue(afterKey.placement.columnUnits > beforeKey.placement.columnUnits)

        val spacerVm = qwertyViewModel()
        spacerVm.enterSpacerPlacementMode(GridSpan(2, 2), InsertionPolicy.PreferHorizontal)
        spacerVm.holdPlacementCursorFromTap(InsertionTarget.EmptyArea(GridPlacement(0, 0, 2, 2)))
        val spacerAfterKey = spacerVm.uiState.value.previewLayout!!.items.filterIsInstance<KeyItem>()
            .first { it.id == "qwerty_key_q" }
        assertEquals(beforeKey.placement.rowUnits, spacerAfterKey.placement.rowUnits)
        assertTrue(spacerAfterKey.placement.columnUnits > beforeKey.placement.columnUnits)
    }

    @Test
    fun viewModel_preferVerticalInsertion_pushesOverlapDownForKeysAndSpacers() {
        val keyVm = qwertyViewModel()
        val beforeKey = keyVm.uiState.value.layout.items.filterIsInstance<KeyItem>()
            .first { it.id == "qwerty_key_q" }
        keyVm.enterNewKeyPlacementMode(GridSpan(2, 2), InsertionPolicy.PreferVertical)
        keyVm.holdPlacementCursorFromTap(InsertionTarget.EmptyArea(GridPlacement(0, 0, 2, 2)))
        val afterKey = keyVm.uiState.value.previewLayout!!.items.filterIsInstance<KeyItem>()
            .first { it.id == "qwerty_key_q" }
        assertTrue(afterKey.placement.rowUnits > beforeKey.placement.rowUnits)
        assertEquals(beforeKey.placement.columnUnits, afterKey.placement.columnUnits)

        val spacerVm = qwertyViewModel()
        spacerVm.enterSpacerPlacementMode(GridSpan(2, 2), InsertionPolicy.PreferVertical)
        spacerVm.holdPlacementCursorFromTap(InsertionTarget.EmptyArea(GridPlacement(0, 0, 2, 2)))
        val spacerAfterKey = spacerVm.uiState.value.previewLayout!!.items.filterIsInstance<KeyItem>()
            .first { it.id == "qwerty_key_q" }
        assertTrue(spacerAfterKey.placement.rowUnits > beforeKey.placement.rowUnits)
        assertEquals(beforeKey.placement.columnUnits, spacerAfterKey.placement.columnUnits)
    }

    @Test
    fun structuralControls_hiddenForFlexibleLayout_visibleForGridLayout() {
        assertFalse(shouldShowKeyboardEditorStructuralControls(KeyboardDefaultLayouts.createQwertyTemplateLayout()))
        assertTrue(
            shouldShowKeyboardEditorStructuralControls(
                KeyboardLayout(
                    keys = listOf(
                        KeyData("x", 0, 0, false, KeyAction.Text("x"), keyType = KeyType.NORMAL, keyId = "x")
                    ),
                    flickKeyMaps = emptyMap(),
                    columnCount = 1,
                    rowCount = 1
                )
            )
        )
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

    private fun horizontalGapLayout(): KeyboardLayout {
        val items = listOf(
            keyItem("a", GridPlacement(0, 0, 2, 2)),
            keyItem("b", GridPlacement(0, 2, 2, 2)),
            SpacerItem("spacer", GridPlacement(0, 4, 2, 2)),
            keyItem("c", GridPlacement(0, 6, 2, 2)),
            keyItem("d", GridPlacement(0, 8, 2, 2))
        )
        return layoutFromItems(items, columnUnitCount = 10, rowUnitCount = 2)
    }

    private fun verticalGapLayout(): KeyboardLayout {
        val items = listOf(
            keyItem("a", GridPlacement(0, 0, 2, 2)),
            SpacerItem("spacer", GridPlacement(2, 0, 2, 2)),
            keyItem("c", GridPlacement(4, 0, 2, 2)),
            keyItem("d", GridPlacement(6, 0, 2, 2)),
            keyItem("side", GridPlacement(2, 4, 2, 2))
        )
        return layoutFromItems(items, columnUnitCount = 6, rowUnitCount = 8)
    }

    private fun singleKeyLayout(itemId: String, keyId: String): KeyboardLayout {
        val key = keyItem(itemId, GridPlacement(0, 0, 2, 2), keyId = keyId)
        return layoutFromItems(listOf(key), columnUnitCount = 2, rowUnitCount = 2)
    }

    private fun layoutFromItems(
        items: List<com.kazumaproject.custom_keyboard.data.KeyboardLayoutItem>,
        columnUnitCount: Int,
        rowUnitCount: Int
    ): KeyboardLayout =
        KeyboardLayout(
            keys = items.filterIsInstance<KeyItem>().map { it.keyData },
            flickKeyMaps = emptyMap(),
            columnCount = (columnUnitCount + 1) / 2,
            rowCount = (rowUnitCount + 1) / 2,
            items = items,
            columnUnitCount = columnUnitCount,
            rowUnitCount = rowUnitCount
        )

    private fun keyItem(
        id: String,
        placement: GridPlacement,
        keyId: String = id
    ): KeyItem {
        val key = KeyData(
            label = id,
            row = placement.rowUnits / 2,
            column = placement.columnUnits / 2,
            isFlickable = false,
            action = KeyAction.Text(id),
            rowSpan = (placement.rowSpanUnits + 1) / 2,
            colSpan = (placement.columnSpanUnits + 1) / 2,
            keyType = KeyType.NORMAL,
            keyId = keyId
        )
        return KeyItem(id, key, placement)
    }

    private fun item(layout: KeyboardLayout, id: String) =
        layout.items.first { it.id == id }

    private fun assertValidFlexibleLayout(layout: KeyboardLayout) {
        assertEquals((layout.rowUnitCount + 1) / 2, layout.rowCount)
        assertEquals((layout.columnUnitCount + 1) / 2, layout.columnCount)
        assertFalse(hasPlacementIssues(layout.items, layout.rowUnitCount, layout.columnUnitCount))
    }
}
