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
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database.KeyboardLayoutDao
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class KeyboardEditorViewModelFlexiblePlacementTest {

    private fun viewModel(): KeyboardEditorViewModel =
        KeyboardEditorViewModel(KeyboardRepository(mock(KeyboardLayoutDao::class.java)))

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
}
