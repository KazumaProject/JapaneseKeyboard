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
import com.kazumaproject.custom_keyboard.data.usesFlexiblePlacement
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database.KeyboardLayoutDao
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.GridSpan
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.HalfRowPlacement
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.InsertionPolicy
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.InsertionTarget
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.KeyboardEditorMode
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.NudgeDirection
import com.kazumaproject.markdownhelperkeyboard.R
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
    fun qwertyKeyEdit_doesNotResizePlacementFromKeyDataSpan() {
        // Flexible-layout edits go through GridPlacement as the source of
        // truth. Editing KeyData.rowSpan / colSpan via the key editor
        // (e.g. label/action edits that happen to also pass span values)
        // must NOT mutate the item's GridPlacement, otherwise half-cell
        // keys (rowSpanUnits = 1) would silently collapse to full-cell
        // keys (rowSpanUnits = 2).
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
        // Placement of every other item is preserved as well.
        beforeLayout.items.zip(afterLayout.items).forEach { (before, after) ->
            assertEquals(before.id, after.id)
            assertEquals(before.placement, after.placement)
        }
    }

    @Test
    fun flexibleKeyEdit_resizesOneByOneKeyToTwoByOneUsingPlacementUnits() {
        val vm = viewModel()
        vm.applyTemplate(
            singleKeyLayout(
                "key_a",
                "key_a",
                columnUnitCount = 4,
                rowUnitCount = 2,
                isFlexiblePlacementLayout = true
            )
        )
        val before = vm.uiState.value.layout.items.filterIsInstance<KeyItem>().single()

        vm.updateKeyAndMappings(
            newKeyData = before.keyData.copy(label = "A", colSpan = 2),
            flickMap = emptyMap(),
            twoStepMap = emptyMap(),
            longPressFlickMap = emptyMap(),
            twoStepLongPressMap = emptyMap(),
            flexibleRowSpanUnits = 2,
            flexibleColumnSpanUnits = 4
        )

        val after = vm.uiState.value.layout.items.filterIsInstance<KeyItem>().single()
        assertEquals(2, before.placement.columnSpanUnits)
        assertEquals(4, after.placement.columnSpanUnits)
        assertEquals(2, after.keyData.colSpan)
        assertEquals(2, after.placement.rowSpanUnits)
        assertEquals("A", after.keyData.label)
    }

    @Test
    fun flexibleKeyEdit_resizesOneByOneKeyToOneByTwoUsingPlacementUnits() {
        val vm = viewModel()
        vm.applyTemplate(
            singleKeyLayout(
                "key_a",
                "key_a",
                columnUnitCount = 2,
                rowUnitCount = 4,
                isFlexiblePlacementLayout = true
            )
        )
        val before = vm.uiState.value.layout.items.filterIsInstance<KeyItem>().single()

        vm.updateKeyAndMappings(
            newKeyData = before.keyData.copy(label = "A", rowSpan = 2),
            flickMap = emptyMap(),
            twoStepMap = emptyMap(),
            longPressFlickMap = emptyMap(),
            twoStepLongPressMap = emptyMap(),
            flexibleRowSpanUnits = 4,
            flexibleColumnSpanUnits = 2
        )

        val after = vm.uiState.value.layout.items.filterIsInstance<KeyItem>().single()
        assertEquals(2, before.placement.rowSpanUnits)
        assertEquals(4, after.placement.rowSpanUnits)
        assertEquals(2, after.keyData.rowSpan)
        assertEquals(2, after.placement.columnSpanUnits)
    }

    @Test
    fun flexibleKeyEdit_keepsHalfWidthPlacementAfterLabelOnlyEdit() {
        val vm = viewModel()
        vm.applyTemplate(
            layoutFromItems(
                items = listOf(keyItem("half_width", GridPlacement(0, 0, 2, 1))),
                columnUnitCount = 2,
                rowUnitCount = 2
            )
        )
        val before = vm.uiState.value.layout.items.filterIsInstance<KeyItem>().single()

        vm.updateKeyAndMappings(
            newKeyData = before.keyData.copy(label = "H", action = KeyAction.Text("H")),
            flickMap = emptyMap(),
            twoStepMap = emptyMap(),
            longPressFlickMap = emptyMap(),
            twoStepLongPressMap = emptyMap()
        )

        val after = vm.uiState.value.layout.items.filterIsInstance<KeyItem>().single()
        assertEquals(1, before.placement.columnSpanUnits)
        assertEquals(1, after.placement.columnSpanUnits)
        assertEquals(before.placement, after.placement)
        assertEquals("H", after.keyData.label)
    }

    @Test
    fun flexibleKeyEdit_keepsHalfHeightPlacementAfterLabelOnlyEdit() {
        val vm = viewModel()
        vm.applyTemplate(
            layoutFromItems(
                items = listOf(keyItem("half_height", GridPlacement(0, 0, 1, 2))),
                columnUnitCount = 2,
                rowUnitCount = 2
            )
        )
        val before = vm.uiState.value.layout.items.filterIsInstance<KeyItem>().single()

        vm.updateKeyAndMappings(
            newKeyData = before.keyData.copy(label = "H", action = KeyAction.Text("H")),
            flickMap = emptyMap(),
            twoStepMap = emptyMap(),
            longPressFlickMap = emptyMap(),
            twoStepLongPressMap = emptyMap()
        )

        val after = vm.uiState.value.layout.items.filterIsInstance<KeyItem>().single()
        assertEquals(1, before.placement.rowSpanUnits)
        assertEquals(1, after.placement.rowSpanUnits)
        assertEquals(before.placement, after.placement)
        assertEquals("H", after.keyData.label)
    }

    @Test
    fun flexibleKeyEdit_rejectsResizeThatOverlapsAnotherItemAndKeepsMappingsUntouched() {
        val vm = viewModel()
        vm.applyTemplate(
            layoutFromItems(
                items = listOf(
                    keyItem("a", GridPlacement(0, 0, 2, 2)),
                    keyItem("b", GridPlacement(0, 2, 2, 2))
                ),
                columnUnitCount = 4,
                rowUnitCount = 2,
                isFlexiblePlacementLayout = true
            ).copy(flickKeyMaps = mapOf("a" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("a")))))
        )
        val beforeLayout = vm.uiState.value.layout
        val target = beforeLayout.items.filterIsInstance<KeyItem>().first { it.id == "a" }

        vm.updateKeyAndMappings(
            newKeyData = target.keyData.copy(label = "A"),
            flickMap = mapOf(FlickDirection.TAP to FlickAction.Input("A")),
            twoStepMap = emptyMap(),
            longPressFlickMap = emptyMap(),
            twoStepLongPressMap = emptyMap(),
            flexibleRowSpanUnits = 2,
            flexibleColumnSpanUnits = 4
        )

        assertEquals(beforeLayout, vm.uiState.value.layout)
    }

    @Test
    fun flexibleKeyEdit_rejectsResizeOutsideCurrentBounds() {
        val vm = viewModel()
        vm.applyTemplate(
            singleKeyLayout(
                "key_a",
                "key_a",
                columnUnitCount = 2,
                rowUnitCount = 2,
                isFlexiblePlacementLayout = true
            )
        )
        val beforeLayout = vm.uiState.value.layout
        val target = beforeLayout.items.filterIsInstance<KeyItem>().single()

        vm.updateKeyAndMappings(
            newKeyData = target.keyData.copy(label = "A", colSpan = 2),
            flickMap = emptyMap(),
            twoStepMap = emptyMap(),
            longPressFlickMap = emptyMap(),
            twoStepLongPressMap = emptyMap(),
            flexibleRowSpanUnits = 2,
            flexibleColumnSpanUnits = 4
        )

        assertEquals(beforeLayout, vm.uiState.value.layout)
    }

    @Test
    fun flexibleKeyEdit_rejectsNonPositiveSpanUnits() {
        val vm = viewModel()
        vm.applyTemplate(
            singleKeyLayout(
                "key_a",
                "key_a",
                columnUnitCount = 2,
                rowUnitCount = 2,
                isFlexiblePlacementLayout = true
            )
        )
        val beforeLayout = vm.uiState.value.layout
        val target = beforeLayout.items.filterIsInstance<KeyItem>().single()

        vm.updateKeyAndMappings(
            newKeyData = target.keyData.copy(label = "A"),
            flickMap = emptyMap(),
            twoStepMap = emptyMap(),
            longPressFlickMap = emptyMap(),
            twoStepLongPressMap = emptyMap(),
            flexibleRowSpanUnits = 0,
            flexibleColumnSpanUnits = 2
        )

        assertEquals(beforeLayout, vm.uiState.value.layout)
    }

    @Test
    fun flexibleKeyEdit_preservesSpacerPlacementWhenResizingKey() {
        val vm = viewModel()
        val spacer = SpacerItem("spacer", GridPlacement(0, 4, 2, 2))
        vm.applyTemplate(
            layoutFromItems(
                items = listOf(keyItem("a", GridPlacement(0, 0, 2, 2)), spacer),
                columnUnitCount = 6,
                rowUnitCount = 2
            )
        )
        val target = vm.uiState.value.layout.items.filterIsInstance<KeyItem>().single()

        vm.updateKeyAndMappings(
            newKeyData = target.keyData.copy(label = "A", colSpan = 2),
            flickMap = emptyMap(),
            twoStepMap = emptyMap(),
            longPressFlickMap = emptyMap(),
            twoStepLongPressMap = emptyMap(),
            flexibleRowSpanUnits = 2,
            flexibleColumnSpanUnits = 4
        )

        val afterLayout = vm.uiState.value.layout
        val afterKey = afterLayout.items.filterIsInstance<KeyItem>().single()
        val afterSpacer = afterLayout.items.filterIsInstance<SpacerItem>().single()
        assertEquals(4, afterKey.placement.columnSpanUnits)
        assertEquals(spacer.placement, afterSpacer.placement)
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
    fun legacyLayout_keyResizeStillUsesRowSpanAndColSpanCells() {
        val vm = viewModel()
        val key = KeyData(
            label = "a",
            row = 0,
            column = 0,
            isFlickable = false,
            action = KeyAction.Text("a"),
            keyType = KeyType.NORMAL,
            keyId = "key_a"
        )
        vm.applyTemplate(
            KeyboardLayout(
                keys = listOf(key),
                flickKeyMaps = emptyMap(),
                columnCount = 3,
                rowCount = 2
            )
        )

        vm.updateKeyAndMappings(
            newKeyData = key.copy(label = "A", rowSpan = 2, colSpan = 2),
            flickMap = emptyMap(),
            twoStepMap = emptyMap(),
            longPressFlickMap = emptyMap(),
            twoStepLongPressMap = emptyMap()
        )

        val afterKey = vm.uiState.value.layout.keys.single()
        assertEquals(2, afterKey.rowSpan)
        assertEquals(2, afterKey.colSpan)
        assertEquals(4, vm.uiState.value.layout.items.filterIsInstance<KeyItem>().single().placement.rowSpanUnits)
        assertEquals(4, vm.uiState.value.layout.items.filterIsInstance<KeyItem>().single().placement.columnSpanUnits)
    }

    @Test
    fun imeRuntimeLayoutItemsContainUpdatedFlexiblePlacementUnitsAfterResize() {
        val vm = viewModel()
        vm.applyTemplate(
            singleKeyLayout(
                "key_a",
                "key_a",
                columnUnitCount = 4,
                rowUnitCount = 2,
                isFlexiblePlacementLayout = true
            )
        )
        val target = vm.uiState.value.layout.items.filterIsInstance<KeyItem>().single()

        vm.updateKeyAndMappings(
            newKeyData = target.keyData.copy(label = "A", colSpan = 2),
            flickMap = emptyMap(),
            twoStepMap = emptyMap(),
            longPressFlickMap = emptyMap(),
            twoStepLongPressMap = emptyMap(),
            flexibleRowSpanUnits = 2,
            flexibleColumnSpanUnits = 4
        )

        val runtimeLayout = vm.uiState.value.layout
        val runtimeItem = runtimeLayout.items.filterIsInstance<KeyItem>().single()
        assertEquals(4, runtimeItem.placement.columnSpanUnits)
        assertEquals(runtimeItem.keyData, runtimeLayout.keys.single())
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
    fun viewModel_halfRowSpacerPlacement_upperAndLowerUseSelectedHalf() {
        val target = InsertionTarget.EmptyArea(GridPlacement(2, 0, 1, 1))

        val upperVm = viewModel()
        upperVm.applyTemplate(KeyboardDefaultLayouts.createEmpty5x4FlexibleTemplateLayout())
        upperVm.enterSpacerPlacementMode(GridSpan(1, 1), InsertionPolicy.PreferHorizontal)
        upperVm.setHalfRowPlacement(HalfRowPlacement.Upper)
        upperVm.holdPlacementCursorFromTap(target)
        val upperPreviewSpacer = upperVm.uiState.value.previewLayout!!.items
            .filterIsInstance<SpacerItem>()
            .single()
        assertEquals(2, upperPreviewSpacer.placement.rowUnits)
        assertTrue(upperVm.confirmPlacementPreview())
        assertEquals(
            2,
            upperVm.uiState.value.layout.items.filterIsInstance<SpacerItem>().single().placement.rowUnits
        )

        val lowerVm = viewModel()
        lowerVm.applyTemplate(KeyboardDefaultLayouts.createEmpty5x4FlexibleTemplateLayout())
        lowerVm.enterSpacerPlacementMode(GridSpan(1, 1), InsertionPolicy.PreferHorizontal)
        lowerVm.setHalfRowPlacement(HalfRowPlacement.Lower)
        lowerVm.holdPlacementCursorFromTap(target)
        val lowerPreviewSpacer = lowerVm.uiState.value.previewLayout!!.items
            .filterIsInstance<SpacerItem>()
            .single()
        assertEquals(3, lowerPreviewSpacer.placement.rowUnits)
        assertTrue(lowerVm.confirmPlacementPreview())
        assertEquals(
            3,
            lowerVm.uiState.value.layout.items.filterIsInstance<SpacerItem>().single().placement.rowUnits
        )
    }

    @Test
    fun viewModel_halfRowKeyPlacement_lowerUsesSelectedHalf() {
        val vm = viewModel()
        vm.applyTemplate(KeyboardDefaultLayouts.createEmpty5x4FlexibleTemplateLayout())
        vm.enterNewKeyPlacementMode(GridSpan(1, 1), InsertionPolicy.PreferHorizontal)
        vm.setHalfRowPlacement(HalfRowPlacement.Lower)
        vm.holdPlacementCursorFromTap(InsertionTarget.EmptyArea(GridPlacement(2, 0, 1, 1)))

        val previewKey = vm.uiState.value.previewLayout!!.items
            .filterIsInstance<KeyItem>()
            .single()
        assertEquals(3, previewKey.placement.rowUnits)
    }

    @Test
    fun viewModel_enterPlacementModeClearsDeletionSelection() {
        val vm = qwertyViewModel()
        vm.selectItem("qwerty_key_q")
        assertEquals("qwerty_key_q", vm.uiState.value.selectedItemId)

        vm.enterNewKeyPlacementMode(GridSpan(1, 1))
        assertNull(vm.uiState.value.selectedItemId)

        vm.cancelPlacementPreview()
        vm.selectItem("qwerty_key_w")
        assertEquals("qwerty_key_w", vm.uiState.value.selectedItemId)

        vm.enterSpacerPlacementMode(GridSpan(1, 1))
        assertNull(vm.uiState.value.selectedItemId)
    }

    @Test
    fun viewModel_clearSelectedItemForDeletionKeepsSelectedKeyIdentifier() {
        val vm = qwertyViewModel()
        vm.selectKeyForEditing("qwerty_key_q")
        vm.selectItem("qwerty_key_q")

        vm.clearSelectedItemForDeletion()

        assertEquals("qwerty_key_q", vm.uiState.value.selectedKeyIdentifier)
        assertNull(vm.uiState.value.selectedItemId)
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
    fun viewModel_normalModeFirstKeyTap_selectsItemForDeletionWithoutNavigation() {
        val vm = qwertyViewModel()
        assertFalse(vm.onKeyTappedForSelectionOrEdit("qwerty_key_q"))
        assertNull(vm.uiState.value.selectedKeyIdentifier)
        assertEquals("qwerty_key_q", vm.uiState.value.selectedItemId)
    }

    @Test
    fun viewModel_normalModeSecondTapOnSelectedKey_requestsKeyEditorNavigation() {
        val vm = qwertyViewModel()
        assertFalse(vm.onKeyTappedForSelectionOrEdit("qwerty_key_q"))

        assertTrue(vm.onKeyTappedForSelectionOrEdit("qwerty_key_q"))

        assertEquals("qwerty_key_q", vm.uiState.value.selectedKeyIdentifier)
        assertEquals("qwerty_key_q", vm.uiState.value.selectedItemId)
    }

    @Test
    fun viewModel_placementModeKeyTap_doesNotEmitKeyEditorNavigation() {
        val vm = qwertyViewModel()
        vm.enterNewKeyPlacementMode(GridSpan(2, 2))
        assertFalse(vm.onKeyTappedForSelectionOrEdit("qwerty_key_q"))
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
    fun viewModel_deleteSelectedKeyItemId_removesKeyAndMapping() {
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

        val layout = vm.uiState.value.layout
        assertTrue(layout.items.none { it.id == "x" })
        assertFalse(layout.flickKeyMaps.containsKey("x"))
        assertNull(vm.uiState.value.selectedItemId)
    }

    @Test
    fun viewModel_deleteSelectedKeyAfterTap_removesKeyAndClearsSelection() {
        val vm = qwertyViewModel()
        assertFalse(vm.onKeyTappedForSelectionOrEdit("qwerty_key_q"))

        assertTrue(vm.deleteSelectedItem())

        assertTrue(vm.uiState.value.layout.items.none { it.matchesTestItemId("qwerty_key_q") })
        assertNull(vm.uiState.value.selectedItemId)
    }

    @Test
    fun viewModel_deleteSelectedKey_removesEveryMappingTypeAndKeepsUnrelatedMappings() {
        val vm = viewModel()
        val key = KeyData("x", 0, 0, false, KeyAction.Text("x"), keyType = KeyType.NORMAL, keyId = "x")
        val unrelatedKey = KeyData("y", 0, 1, false, KeyAction.Text("y"), keyType = KeyType.NORMAL, keyId = "y")
        vm.applyTemplate(
            KeyboardLayout(
                keys = listOf(key, unrelatedKey),
                flickKeyMaps = mapOf(
                    "x" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("x"))),
                    "y" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("y")))
                ),
                circularFlickKeyMaps = mapOf(
                    "x" to listOf(mapOf(CircularFlickDirection.TAP to FlickAction.Input("x"))),
                    "y" to listOf(mapOf(CircularFlickDirection.TAP to FlickAction.Input("y")))
                ),
                twoStepFlickKeyMaps = mapOf(
                    "x" to mapOf(TfbiFlickDirection.TAP to mapOf(TfbiFlickDirection.RIGHT to "x")),
                    "y" to mapOf(TfbiFlickDirection.TAP to mapOf(TfbiFlickDirection.RIGHT to "y"))
                ),
                longPressFlickKeyMaps = mapOf(
                    "x" to mapOf(FlickDirection.UP to "x"),
                    "y" to mapOf(FlickDirection.UP to "y")
                ),
                twoStepLongPressKeyMaps = mapOf(
                    "x" to mapOf(TfbiFlickDirection.TAP to mapOf(TfbiFlickDirection.LEFT to "x")),
                    "y" to mapOf(TfbiFlickDirection.TAP to mapOf(TfbiFlickDirection.LEFT to "y"))
                ),
                hierarchicalFlickMaps = mapOf(
                    "x" to TfbiFlickNode.StatefulKey(
                        normalMap = mapOf(TfbiFlickDirection.TAP to TfbiFlickNode.Input("x")),
                        label = "x"
                    ),
                    "y" to TfbiFlickNode.StatefulKey(
                        normalMap = mapOf(TfbiFlickDirection.TAP to TfbiFlickNode.Input("y")),
                        label = "y"
                    )
                ),
                columnCount = 2,
                rowCount = 1,
                items = listOf(
                    KeyItem("x", key, GridPlacement(0, 0, 2, 2)),
                    KeyItem("y", unrelatedKey, GridPlacement(0, 2, 2, 2))
                )
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
        assertTrue(layout.flickKeyMaps.containsKey("y"))
        assertTrue(layout.circularFlickKeyMaps.containsKey("y"))
        assertTrue(layout.twoStepFlickKeyMaps.containsKey("y"))
        assertTrue(layout.longPressFlickKeyMaps.containsKey("y"))
        assertTrue(layout.twoStepLongPressKeyMaps.containsKey("y"))
        assertTrue(layout.hierarchicalFlickMaps.containsKey("y"))
        assertNull(vm.uiState.value.selectedItemId)
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
        assertNull(vm.uiState.value.selectedItemId)
    }

    @Test
    fun viewModel_deleteSelectedItem_matchesKeyDataKeyId() {
        val vm = viewModel()
        vm.applyTemplate(singleKeyLayout(itemId = "item_x", keyId = "key_x"))

        vm.selectItem("key_x")
        assertTrue(vm.deleteSelectedItem())

        assertTrue(vm.uiState.value.layout.items.filterIsInstance<KeyItem>().none { it.keyData.keyId == "key_x" })
        assertNull(vm.uiState.value.selectedItemId)
    }

    @Test
    fun viewModel_deleteSelectedItem_matchesSpacerId() {
        val vm = viewModel()
        vm.applyTemplate(horizontalGapLayout())

        vm.selectItem("spacer")
        assertTrue(vm.deleteSelectedItem())

        assertTrue(vm.uiState.value.layout.items.none { it.id == "spacer" })
        assertNull(vm.uiState.value.selectedItemId)
    }

    @Test
    fun viewModel_deleteSelectedItem_withoutSelectionReturnsFalseAndKeepsLayout() {
        val vm = viewModel()
        vm.applyTemplate(horizontalGapLayout())
        val before = vm.uiState.value.layout

        vm.selectItem(null)
        assertFalse(vm.deleteSelectedItem())

        assertEquals(before, vm.uiState.value.layout)
        assertNull(vm.uiState.value.selectedItemId)
    }

    @Test
    fun viewModel_deleteSelectedItem_withUnknownSelectionReturnsFalseAndKeepsLayout() {
        val vm = viewModel()
        vm.applyTemplate(horizontalGapLayout())
        val before = vm.uiState.value.layout

        vm.selectItem("missing")
        assertFalse(vm.deleteSelectedItem())

        assertEquals(before, vm.uiState.value.layout)
        assertNull(vm.uiState.value.selectedItemId)
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
    fun viewModel_preferVerticalPolicyPropagatesThroughModeCursorAndPreviewForKeyAndSpacer() {
        val keyVm = qwertyViewModel()
        keyVm.updateInsertionPolicy(InsertionPolicy.PreferVertical)
        keyVm.enterNewKeyPlacementMode(GridSpan(2, 2))
        keyVm.updatePlacementCursorFromPointer(InsertionTarget.BeforeItem("qwerty_key_q"))

        val keyMode = keyVm.uiState.value.editorMode as KeyboardEditorMode.PlacingNewKey
        val keyCursor = keyVm.uiState.value.placementCursor
        val keyInserted = keyVm.uiState.value.previewLayout!!.items
            .first { it.id == keyVm.uiState.value.previewInsertedItemId }
        assertEquals(InsertionPolicy.PreferVertical, keyMode.policy)
        assertEquals(InsertionPolicy.PreferVertical, keyCursor!!.policy)
        assertEquals(0, keyInserted.placement.columnUnits)
        assertEquals(0, keyInserted.placement.rowUnits)
        assertEquals(GridPlacement(2, 0, 2, 2), item(keyVm.uiState.value.previewLayout!!, "qwerty_key_q").placement)

        val spacerVm = qwertyViewModel()
        spacerVm.updateInsertionPolicy(InsertionPolicy.PreferVertical)
        spacerVm.enterSpacerPlacementMode(GridSpan(1, 1))
        spacerVm.updatePlacementCursorFromPointer(InsertionTarget.BeforeItem("qwerty_key_q"))

        val spacerMode = spacerVm.uiState.value.editorMode as KeyboardEditorMode.PlacingSpacer
        val spacerCursor = spacerVm.uiState.value.placementCursor
        val spacerInserted = spacerVm.uiState.value.previewLayout!!.items
            .first { it.id == spacerVm.uiState.value.previewInsertedItemId }
        assertEquals(InsertionPolicy.PreferVertical, spacerMode.policy)
        assertEquals(InsertionPolicy.PreferVertical, spacerCursor!!.policy)
        assertEquals(0, spacerInserted.placement.columnUnits)
        assertEquals(0, spacerInserted.placement.rowUnits)
        assertEquals(GridPlacement(1, 0, 2, 2), item(spacerVm.uiState.value.previewLayout!!, "qwerty_key_q").placement)
    }

    @Test
    fun viewModel_updatingInsertionPolicyWhilePlacingRecomputesPreviewVertically() {
        val vm = qwertyViewModel()
        vm.enterNewKeyPlacementMode(GridSpan(2, 2), InsertionPolicy.PreferHorizontal)
        vm.updatePlacementCursorFromPointer(InsertionTarget.BeforeItem("qwerty_key_q"))
        assertEquals(GridPlacement(0, 2, 2, 2), item(vm.uiState.value.previewLayout!!, "qwerty_key_q").placement)

        vm.updateInsertionPolicy(InsertionPolicy.PreferVertical)

        val mode = vm.uiState.value.editorMode as KeyboardEditorMode.PlacingNewKey
        assertEquals(InsertionPolicy.PreferVertical, mode.policy)
        assertEquals(InsertionPolicy.PreferVertical, vm.uiState.value.placementCursor!!.policy)
        assertEquals(GridPlacement(2, 0, 2, 2), item(vm.uiState.value.previewLayout!!, "qwerty_key_q").placement)
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
    fun keyboardEditorCapabilities_tenKeyKeepsGridControlsEvenWithSpacer() {
        val tenKey = KeyboardDefaultLayouts.createNumberTemplateLayout()
        val tenKeyCapabilities = keyboardEditorCapabilities(tenKey)
        assertFalse(tenKeyCapabilities.showHalfCellControls)
        assertFalse(tenKeyCapabilities.showInsertionDirectionControls)
        assertTrue(tenKeyCapabilities.showGridStructuralControls)

        val tenKeyWithSpacer = tenKey.copy(
            items = tenKey.items + SpacerItem("number_template_spacer", GridPlacement(8, 0, 2, 2)),
            rowUnitCount = 10,
            rowCount = 5
        )
        assertTrue(tenKeyWithSpacer.usesFlexiblePlacement())

        val tenKeyWithSpacerCapabilities = keyboardEditorCapabilities(tenKeyWithSpacer)
        assertFalse(tenKeyWithSpacerCapabilities.showHalfCellControls)
        assertFalse(tenKeyWithSpacerCapabilities.showInsertionDirectionControls)
        assertTrue(tenKeyWithSpacerCapabilities.showGridStructuralControls)
    }

    @Test
    fun keyboardEditorCapabilities_qwertyShowsFlexibleControls() {
        val capabilities = keyboardEditorCapabilities(KeyboardDefaultLayouts.createQwertyTemplateLayout())
        assertTrue(capabilities.showHalfCellControls)
        assertTrue(capabilities.showInsertionDirectionControls)
        assertFalse(capabilities.showGridStructuralControls)
    }

    @Test
    fun empty5x4FlexibleTemplate_isEmptyFlexibleEditorLayout() {
        val vm = viewModel()
        val template = vm.availableTemplates.first { it.nameResId == R.string.template_empty_5x4_flexible }
        val layout = template.layout

        assertEquals(5, layout.columnCount)
        assertEquals(4, layout.rowCount)
        assertEquals(10, layout.columnUnitCount)
        assertEquals(8, layout.rowUnitCount)
        assertTrue(layout.items.isEmpty())
        assertTrue(layout.usesFlexiblePlacement())

        val flexibleCapabilities = keyboardEditorCapabilities(layout)
        assertTrue(flexibleCapabilities.showHalfCellControls)
        assertTrue(flexibleCapabilities.showInsertionDirectionControls)
        assertFalse(flexibleCapabilities.showGridStructuralControls)

        val tenKeyCapabilities = keyboardEditorCapabilities(KeyboardDefaultLayouts.createNumberTemplateLayout())
        assertFalse(tenKeyCapabilities.showHalfCellControls)
        assertFalse(tenKeyCapabilities.showInsertionDirectionControls)
        assertTrue(tenKeyCapabilities.showGridStructuralControls)
    }

    @Test
    fun placementAddModeButtonId_restoresCheckedButtonFromEditorMode() {
        assertEquals(
            R.id.button_place_half_key,
            placementAddModeButtonId(KeyboardEditorMode.PlacingNewKey(GridSpan(1, 1)))
        )
        assertEquals(
            R.id.button_place_one_key,
            placementAddModeButtonId(KeyboardEditorMode.PlacingNewKey(GridSpan(2, 2)))
        )
        assertEquals(
            R.id.button_place_half_spacer,
            placementAddModeButtonId(KeyboardEditorMode.PlacingSpacer(GridSpan(1, 1)))
        )
        assertEquals(
            R.id.button_place_one_spacer,
            placementAddModeButtonId(KeyboardEditorMode.PlacingSpacer(GridSpan(2, 2)))
        )
        assertEquals(android.view.View.NO_ID, placementAddModeButtonId(KeyboardEditorMode.Normal))
    }

    @Test
    fun shouldShowHalfRowPlacementControls_onlyForHalfCellHorizontalPlacementOnFlexibleLayout() {
        val flexibleCapabilities = keyboardEditorCapabilities(KeyboardDefaultLayouts.createEmpty5x4FlexibleTemplateLayout())
        val gridCapabilities = keyboardEditorCapabilities(KeyboardDefaultLayouts.createNumberTemplateLayout())

        assertTrue(
            shouldShowHalfRowPlacementControls(
                EditorUiState(
                    layout = KeyboardDefaultLayouts.createEmpty5x4FlexibleTemplateLayout(),
                    editorMode = KeyboardEditorMode.PlacingNewKey(GridSpan(1, 1), InsertionPolicy.PreferHorizontal)
                ),
                flexibleCapabilities
            )
        )
        assertTrue(
            shouldShowHalfRowPlacementControls(
                EditorUiState(
                    layout = KeyboardDefaultLayouts.createEmpty5x4FlexibleTemplateLayout(),
                    editorMode = KeyboardEditorMode.PlacingSpacer(GridSpan(1, 1), InsertionPolicy.PreferHorizontal)
                ),
                flexibleCapabilities
            )
        )
        assertFalse(
            shouldShowHalfRowPlacementControls(
                EditorUiState(
                    layout = KeyboardDefaultLayouts.createEmpty5x4FlexibleTemplateLayout(),
                    editorMode = KeyboardEditorMode.PlacingNewKey(GridSpan(2, 2), InsertionPolicy.PreferHorizontal)
                ),
                flexibleCapabilities
            )
        )
        assertFalse(
            shouldShowHalfRowPlacementControls(
                EditorUiState(
                    layout = KeyboardDefaultLayouts.createEmpty5x4FlexibleTemplateLayout(),
                    editorMode = KeyboardEditorMode.PlacingSpacer(GridSpan(2, 2), InsertionPolicy.PreferHorizontal)
                ),
                flexibleCapabilities
            )
        )
        assertFalse(
            shouldShowHalfRowPlacementControls(
                EditorUiState(
                    layout = KeyboardDefaultLayouts.createEmpty5x4FlexibleTemplateLayout(),
                    editorMode = KeyboardEditorMode.PlacingNewKey(GridSpan(1, 1), InsertionPolicy.PreferVertical)
                ),
                flexibleCapabilities
            )
        )
        assertFalse(
            shouldShowHalfRowPlacementControls(
                EditorUiState(
                    layout = KeyboardDefaultLayouts.createEmpty5x4FlexibleTemplateLayout(),
                    editorMode = KeyboardEditorMode.PlacingSpacer(GridSpan(1, 1), InsertionPolicy.PreferVertical)
                ),
                flexibleCapabilities
            )
        )
        assertFalse(
            shouldShowHalfRowPlacementControls(
                EditorUiState(
                    layout = KeyboardDefaultLayouts.createNumberTemplateLayout(),
                    editorMode = KeyboardEditorMode.PlacingSpacer(GridSpan(1, 1), InsertionPolicy.PreferHorizontal)
                ),
                gridCapabilities
            )
        )
        assertFalse(
            shouldShowHalfRowPlacementControls(
                EditorUiState(
                    layout = KeyboardDefaultLayouts.createEmpty5x4FlexibleTemplateLayout(),
                    editorMode = KeyboardEditorMode.Normal
                ),
                flexibleCapabilities
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

    @Test
    fun viewModel_firstTapOnUnselectedKey_selectsWithoutNavigation() {
        val vm = qwertyViewModel()
        val first = vm.uiState.value.layout.items.filterIsInstance<KeyItem>()
            .first { it.id == "qwerty_key_q" }

        val result = vm.onKeyTappedForSelectionOrEdit(first.id)

        assertFalse(result)
        assertEquals(first.id, vm.uiState.value.selectedItemId)
        assertNull(vm.uiState.value.selectedKeyIdentifier)
    }

    @Test
    fun viewModel_secondTapOnSelectedKey_returnsTrueAndSetsEditingTarget() {
        val vm = qwertyViewModel()
        val target = vm.uiState.value.layout.items.filterIsInstance<KeyItem>()
            .first { it.id == "qwerty_key_a" }

        assertFalse(vm.onKeyTappedForSelectionOrEdit(target.id))
        val second = vm.onKeyTappedForSelectionOrEdit(target.id)

        assertTrue(second)
        assertEquals(target.id, vm.uiState.value.selectedItemId)
        assertEquals(target.id, vm.uiState.value.selectedKeyIdentifier)
    }

    @Test
    fun viewModel_tapOnDifferentKey_switchesSelectionWithoutNavigation() {
        val vm = qwertyViewModel()
        val a = vm.uiState.value.layout.items.filterIsInstance<KeyItem>()
            .first { it.id == "qwerty_key_a" }
        val s = vm.uiState.value.layout.items.filterIsInstance<KeyItem>()
            .first { it.id == "qwerty_key_s" }

        assertFalse(vm.onKeyTappedForSelectionOrEdit(a.id))
        val secondToOther = vm.onKeyTappedForSelectionOrEdit(s.id)

        assertFalse(secondToOther)
        assertEquals(s.id, vm.uiState.value.selectedItemId)
        assertNull(vm.uiState.value.selectedKeyIdentifier)
    }

    @Test
    fun viewModel_spacerTap_neverNavigatesEvenWhenAlreadySelected() {
        val vm = qwertyViewModel()
        val spacer = vm.uiState.value.layout.items.filterIsInstance<SpacerItem>().first()

        vm.onSpacerTapped(spacer.id)
        // Even routing the spacer id through the key-tap function must not
        // navigate or set the editing target.
        val result = vm.onKeyTappedForSelectionOrEdit(spacer.id)

        assertFalse(result)
        assertEquals(spacer.id, vm.uiState.value.selectedItemId)
        assertNull(vm.uiState.value.selectedKeyIdentifier)
    }

    @Test
    fun viewModel_deleteSelectedSpacerAfterTap_removesSpacerAndKeepsLayoutValid() {
        val vm = qwertyViewModel()
        val spacer = vm.uiState.value.layout.items.filterIsInstance<SpacerItem>().first()

        vm.onSpacerTapped(spacer.id)
        assertTrue(vm.deleteSelectedItem())

        assertTrue(vm.uiState.value.layout.items.none { it.id == spacer.id })
        assertNull(vm.uiState.value.selectedItemId)
        assertValidFlexibleLayout(vm.uiState.value.layout)
    }

    @Test
    fun viewModel_halfKeyPlacement_keepsRowSpanUnits1AfterLabelEdit() {
        // Place a lower-half key at row 0, then edit only its label/action
        // and confirm the GridPlacement (especially rowSpanUnits) is
        // preserved. Without the fix this collapses to rowSpanUnits = 2.
        val vm = viewModel()
        vm.applyTemplate(KeyboardDefaultLayouts.createEmpty5x4FlexibleTemplateLayout())
        vm.enterNewKeyPlacementMode(GridSpan(1, 1), InsertionPolicy.PreferHorizontal)
        vm.setHalfRowPlacement(HalfRowPlacement.Lower)
        vm.holdPlacementCursorFromTap(InsertionTarget.EmptyArea(GridPlacement(0, 0, 1, 1)))
        assertTrue(vm.confirmPlacementPreview())

        val placedKey = vm.uiState.value.layout.items.filterIsInstance<KeyItem>().single()
        assertEquals(1, placedKey.placement.rowUnits) // lower half of row 0
        assertEquals(1, placedKey.placement.rowSpanUnits)
        assertEquals(1, placedKey.placement.columnSpanUnits)

        // Edit only label / action — must NOT change placement.
        vm.updateKeyAndMappings(
            newKeyData = placedKey.keyData.copy(label = "X", action = KeyAction.Text("X")),
            flickMap = emptyMap(),
            twoStepMap = emptyMap(),
            longPressFlickMap = emptyMap(),
            twoStepLongPressMap = emptyMap()
        )

        val edited = vm.uiState.value.layout.items.filterIsInstance<KeyItem>().single()
        assertEquals("X", edited.keyData.label)
        assertEquals(placedKey.placement, edited.placement)
        assertEquals(1, edited.placement.rowUnits)
        assertEquals(1, edited.placement.rowSpanUnits)
    }

    @Test
    fun viewModel_halfKeyPlacement_upperKeepsRowSpanUnits1AfterLabelEdit() {
        val vm = viewModel()
        vm.applyTemplate(KeyboardDefaultLayouts.createEmpty5x4FlexibleTemplateLayout())
        vm.enterNewKeyPlacementMode(GridSpan(1, 1), InsertionPolicy.PreferHorizontal)
        vm.setHalfRowPlacement(HalfRowPlacement.Upper)
        vm.holdPlacementCursorFromTap(InsertionTarget.EmptyArea(GridPlacement(2, 0, 1, 1)))
        assertTrue(vm.confirmPlacementPreview())

        val placedKey = vm.uiState.value.layout.items.filterIsInstance<KeyItem>().single()
        assertEquals(2, placedKey.placement.rowUnits) // upper half of row 1
        assertEquals(1, placedKey.placement.rowSpanUnits)

        vm.updateKeyAndMappings(
            newKeyData = placedKey.keyData.copy(label = "Y"),
            flickMap = emptyMap(),
            twoStepMap = emptyMap(),
            longPressFlickMap = emptyMap(),
            twoStepLongPressMap = emptyMap()
        )

        val edited = vm.uiState.value.layout.items.filterIsInstance<KeyItem>().single()
        assertEquals(2, edited.placement.rowUnits)
        assertEquals(1, edited.placement.rowSpanUnits)
    }

    @Test
    fun viewModel_oneCellKeyPlacement_keepsRowSpanUnits2AfterLabelEdit() {
        val vm = viewModel()
        vm.applyTemplate(KeyboardDefaultLayouts.createEmpty5x4FlexibleTemplateLayout())
        vm.enterNewKeyPlacementMode(GridSpan(2, 2))
        vm.holdPlacementCursorFromTap(InsertionTarget.EmptyArea(GridPlacement(0, 0, 2, 2)))
        assertTrue(vm.confirmPlacementPreview())

        val placedKey = vm.uiState.value.layout.items.filterIsInstance<KeyItem>().single()
        assertEquals(GridPlacement(0, 0, 2, 2), placedKey.placement)

        vm.updateKeyAndMappings(
            newKeyData = placedKey.keyData.copy(label = "Z"),
            flickMap = emptyMap(),
            twoStepMap = emptyMap(),
            longPressFlickMap = emptyMap(),
            twoStepLongPressMap = emptyMap()
        )

        val edited = vm.uiState.value.layout.items.filterIsInstance<KeyItem>().single()
        assertEquals(GridPlacement(0, 0, 2, 2), edited.placement)
    }

    @Test
    fun viewModel_halfKeyAndOneKey_remainDistinguishableAfterEdit() {
        val vm = viewModel()
        vm.applyTemplate(KeyboardDefaultLayouts.createEmpty5x4FlexibleTemplateLayout())

        vm.enterNewKeyPlacementMode(GridSpan(2, 2))
        vm.holdPlacementCursorFromTap(InsertionTarget.EmptyArea(GridPlacement(0, 0, 2, 2)))
        assertTrue(vm.confirmPlacementPreview())

        vm.enterNewKeyPlacementMode(GridSpan(1, 1), InsertionPolicy.PreferHorizontal)
        vm.setHalfRowPlacement(HalfRowPlacement.Upper)
        vm.holdPlacementCursorFromTap(InsertionTarget.EmptyArea(GridPlacement(0, 4, 1, 1)))
        assertTrue(vm.confirmPlacementPreview())

        val keys = vm.uiState.value.layout.items.filterIsInstance<KeyItem>()
        assertEquals(2, keys.size)
        val full = keys.first { it.placement.rowSpanUnits == 2 }
        val half = keys.first { it.placement.rowSpanUnits == 1 }

        vm.updateKeyAndMappings(
            newKeyData = full.keyData.copy(label = "F"),
            flickMap = emptyMap(),
            twoStepMap = emptyMap(),
            longPressFlickMap = emptyMap(),
            twoStepLongPressMap = emptyMap()
        )
        vm.updateKeyAndMappings(
            newKeyData = half.keyData.copy(label = "H"),
            flickMap = emptyMap(),
            twoStepMap = emptyMap(),
            longPressFlickMap = emptyMap(),
            twoStepLongPressMap = emptyMap()
        )

        val after = vm.uiState.value.layout.items.filterIsInstance<KeyItem>()
        val afterFull = after.first { it.id == full.id }
        val afterHalf = after.first { it.id == half.id }
        assertEquals(2, afterFull.placement.rowSpanUnits)
        assertEquals(1, afterHalf.placement.rowSpanUnits)
        assertNotEquals(afterFull.placement.rowSpanUnits, afterHalf.placement.rowSpanUnits)
    }

    @Test
    fun viewModel_keyItemSelectionUsesItemIdFirst() {
        // The flexible-layout selection path matches against item.id first
        // (and falls back to keyData.keyId for backwards compat). Confirm
        // that tapping with item.id selects the right key and that a second
        // tap (still with item.id) navigates with item.id as the editing
        // target.
        val vm = qwertyViewModel()
        val target = vm.uiState.value.layout.items.filterIsInstance<KeyItem>()
            .first { it.id == "qwerty_key_a" }
        // The QWERTY template uses item.id == keyData.keyId; this test is
        // primarily about ensuring the public API accepts item.id.
        assertEquals(target.id, target.keyData.keyId)

        assertFalse(vm.onKeyTappedForSelectionOrEdit(target.id))
        assertEquals(target.id, vm.uiState.value.selectedItemId)

        assertTrue(vm.onKeyTappedForSelectionOrEdit(target.id))
        assertEquals(target.id, vm.uiState.value.selectedKeyIdentifier)
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

    private fun singleKeyLayout(
        itemId: String,
        keyId: String,
        columnUnitCount: Int = 2,
        rowUnitCount: Int = 2,
        isFlexiblePlacementLayout: Boolean = false
    ): KeyboardLayout {
        val key = keyItem(itemId, GridPlacement(0, 0, 2, 2), keyId = keyId)
        return layoutFromItems(
            listOf(key),
            columnUnitCount = columnUnitCount,
            rowUnitCount = rowUnitCount,
            isFlexiblePlacementLayout = isFlexiblePlacementLayout
        )
    }

    private fun layoutFromItems(
        items: List<com.kazumaproject.custom_keyboard.data.KeyboardLayoutItem>,
        columnUnitCount: Int,
        rowUnitCount: Int,
        isFlexiblePlacementLayout: Boolean = false
    ): KeyboardLayout =
        KeyboardLayout(
            keys = items.filterIsInstance<KeyItem>().map { it.keyData },
            flickKeyMaps = emptyMap(),
            columnCount = (columnUnitCount + 1) / 2,
            rowCount = (rowUnitCount + 1) / 2,
            items = items,
            columnUnitCount = columnUnitCount,
            rowUnitCount = rowUnitCount,
            isFlexiblePlacementLayout = isFlexiblePlacementLayout
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

    private fun com.kazumaproject.custom_keyboard.data.KeyboardLayoutItem.matchesTestItemId(selectedId: String): Boolean =
        id == selectedId || (this is KeyItem && keyData.keyId == selectedId)

    private fun assertValidFlexibleLayout(layout: KeyboardLayout) {
        assertEquals((layout.rowUnitCount + 1) / 2, layout.rowCount)
        assertEquals((layout.columnUnitCount + 1) / 2, layout.columnCount)
        assertFalse(hasPlacementIssues(layout.items, layout.rowUnitCount, layout.columnUnitCount))
    }
}
