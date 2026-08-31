package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.data.isDeletedKeySlot
import com.kazumaproject.custom_keyboard.data.usesFlexiblePlacement
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database.KeyboardLayoutDao
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class KeyboardEditorIssue934Test {

    @Test
    fun deleteSelectedKey_keepsRestorableEmptySlotAtSamePlacement() {
        val viewModel = viewModel()
        viewModel.applyTemplate(singleKeyLayout())
        val originalKey = viewModel.uiState.value.layout.items.filterIsInstance<KeyItem>().single()
        viewModel.selectItem(originalKey.id)

        assertTrue(viewModel.deleteSelectedItem())

        val deletedLayout = viewModel.uiState.value.layout
        val emptySlot = deletedLayout.items.filterIsInstance<SpacerItem>().single()
        assertTrue(emptySlot.isDeletedKeySlot())
        assertEquals(originalKey.placement, emptySlot.placement)
        assertTrue(deletedLayout.items.none { it is KeyItem && it.id == originalKey.id })
    }

    @Test
    fun restoreDeletedKeySlot_recreatesEmptyKeyAtSamePlacement() {
        val viewModel = viewModel()
        viewModel.applyTemplate(singleKeyLayout())
        val originalKey = viewModel.uiState.value.layout.items.filterIsInstance<KeyItem>().single()
        viewModel.selectItem(originalKey.id)
        assertTrue(viewModel.deleteSelectedItem())
        val emptySlot = viewModel.uiState.value.layout.items
            .filterIsInstance<SpacerItem>()
            .single { it.isDeletedKeySlot() }

        assertTrue(viewModel.restoreDeletedKeySlot(emptySlot.id))

        val restoredLayout = viewModel.uiState.value.layout
        val restoredKey = restoredLayout.items.filterIsInstance<KeyItem>().single()
        assertEquals(originalKey.placement, restoredKey.placement)
        assertTrue(restoredKey.id != originalKey.id)
        assertEquals("", restoredKey.keyData.label)
        assertTrue(restoredLayout.items.none { it is SpacerItem && it.isDeletedKeySlot() })
    }

    @Test
    fun deleteSelectedKey_preservesImplicitFlexiblePlacementMode() {
        val viewModel = viewModel()
        val key = KeyData(
            label = "A",
            row = 0,
            column = 0,
            isFlickable = false,
            keyId = "offset_key",
            keyType = KeyType.NORMAL
        )
        viewModel.applyTemplate(
            KeyboardLayout(
                keys = listOf(key),
                flickKeyMaps = emptyMap(),
                columnCount = 2,
                rowCount = 1,
                items = listOf(
                    KeyItem("offset_key", key, GridPlacement(0, 1, 2, 2))
                ),
                columnUnitCount = 3,
                rowUnitCount = 2,
                isFlexiblePlacementLayout = false
            )
        )
        assertTrue(viewModel.uiState.value.layout.usesFlexiblePlacement())
        viewModel.selectItem("offset_key")

        assertTrue(viewModel.deleteSelectedItem())

        assertTrue(viewModel.uiState.value.layout.usesFlexiblePlacement())
    }

    @Test
    fun unsavedChanges_tracksPersistedEditorContentAndDeletion() {
        val viewModel = viewModel()
        viewModel.start(-1L)
        assertFalse(viewModel.hasUnsavedChanges())

        val originalName = viewModel.uiState.value.name
        viewModel.updateName("edited")
        assertTrue(viewModel.hasUnsavedChanges())
        viewModel.updateName(originalName)
        assertFalse(viewModel.hasUnsavedChanges())

        val firstKeyId = viewModel.uiState.value.layout.items.first().id
        viewModel.selectItem(firstKeyId)
        assertTrue(viewModel.deleteSelectedItem())
        assertTrue(viewModel.hasUnsavedChanges())
    }

    @Test
    fun unsavedChanges_tracksInputModeSettingsButIgnoresEditorOnlyState() {
        val viewModel = viewModel()
        viewModel.start(-1L)

        viewModel.selectItem(viewModel.uiState.value.layout.items.first().id)
        viewModel.updateShowRowColumnDeleteButtons(false)
        assertFalse(viewModel.hasUnsavedChanges())

        viewModel.updateIsRomaji(true)
        assertTrue(viewModel.hasUnsavedChanges())
        viewModel.updateIsRomaji(false)
        assertFalse(viewModel.hasUnsavedChanges())

        viewModel.updateIsDirectMode(true)
        assertTrue(viewModel.hasUnsavedChanges())
    }

    private fun viewModel(): KeyboardEditorViewModel =
        KeyboardEditorViewModel(KeyboardRepository(mock(KeyboardLayoutDao::class.java)))

    private fun singleKeyLayout(): KeyboardLayout {
        val key = KeyData(
            label = "A",
            row = 0,
            column = 0,
            isFlickable = true,
            keyId = "key_a",
            keyType = KeyType.PETAL_FLICK
        )
        return KeyboardLayout(
            keys = listOf(key),
            flickKeyMaps = mapOf(
                "A" to listOf(
                    mapOf(FlickDirection.TAP to FlickAction.Input("A"))
                )
            ),
            columnCount = 1,
            rowCount = 1
        )
    }
}
