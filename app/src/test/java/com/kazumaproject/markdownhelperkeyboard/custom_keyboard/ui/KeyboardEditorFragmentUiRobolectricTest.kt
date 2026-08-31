package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.data.deletedKeySlot
import com.kazumaproject.custom_keyboard.data.usesFlexiblePlacement
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database.KeyboardLayoutDao
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.view.EditableFlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyboardEditorBinding
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardEditorFragmentUiRobolectricTest {

    @Test
    fun tenKeyTemplateUiVisibility_hidesFlexibleControlsAndShowsGridControls() {
        val binding = inflateBinding()
        val layout = KeyboardDefaultLayouts.createNumberTemplateLayout()

        applyKeyboardEditorCapabilityVisibility(binding, keyboardEditorCapabilities(layout))

        assertFalse(binding.placementSizeControlsGroup.isVisible)
        assertFalse(binding.insertDirectionPanel.isVisible)
        assertTrue(binding.rowControlsGroup.isVisible)
        assertTrue(binding.columnControlsGroup.isVisible)
    }

    @Test
    fun tenKeyWithSpacerUiVisibility_isNotTreatedAsQwertyFlexibleUi() {
        val binding = inflateBinding()
        val tenKey = KeyboardDefaultLayouts.createNumberTemplateLayout()
        val layout = tenKey.copy(
            items = tenKey.items + SpacerItem("number_template_spacer", GridPlacement(8, 0, 2, 2)),
            rowUnitCount = 10,
            rowCount = 5
        )
        assertTrue(layout.usesFlexiblePlacement())

        applyKeyboardEditorCapabilityVisibility(binding, keyboardEditorCapabilities(layout))

        assertFalse(binding.placementSizeControlsGroup.isVisible)
        assertFalse(binding.insertDirectionPanel.isVisible)
        assertTrue(binding.rowControlsGroup.isVisible)
        assertTrue(binding.columnControlsGroup.isVisible)
    }

    @Test
    fun qwertyTemplateUiVisibility_showsFlexibleControlsAndHidesGridControls() {
        val binding = inflateBinding()
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()

        applyKeyboardEditorCapabilityVisibility(binding, keyboardEditorCapabilities(layout))

        assertTrue(binding.placementSizeControlsGroup.isVisible)
        assertTrue(binding.insertDirectionPanel.isVisible)
        assertFalse(binding.rowControlsGroup.isVisible)
        assertFalse(binding.columnControlsGroup.isVisible)
    }

    @Test
    fun deleteSelectedButtonState_keyAndSpacerSelectionEnableDeletion() {
        val binding = inflateBinding()
        val viewModel = viewModel()
        viewModel.applyTemplate(KeyboardDefaultLayouts.createQwertyTemplateLayout())

        assertFalse(viewModel.onKeyTappedForSelectionOrEdit("qwerty_key_q"))
        val keyState = viewModel.uiState.value
        applyKeyboardEditorDeleteSelectionState(binding, keyState, isPlacementMode = false)
        assertEquals("qwerty_key_q", keyState.selectedItemId)
        assertTrue(keyState.hasDeletableSelection())
        assertTrue(binding.buttonDeleteSelectedItem.isEnabled)

        val spacer = keyState.layout.items.filterIsInstance<SpacerItem>().first()
        viewModel.onSpacerTapped(spacer.id)
        val spacerState = viewModel.uiState.value
        applyKeyboardEditorDeleteSelectionState(binding, spacerState, isPlacementMode = false)
        assertEquals(spacer.id, spacerState.selectedItemId)
        assertTrue(spacerState.hasDeletableSelection())
        assertTrue(binding.buttonDeleteSelectedItem.isEnabled)
    }

    @Test
    fun editorLayout_hasNoUndoDeleteControlThatCanShiftButtons() {
        val binding = inflateBinding()

        val undoControlId = binding.root.resources.getIdentifier(
            "button_undo_delete",
            "id",
            binding.root.context.packageName
        )
        assertEquals(0, undoControlId)
    }

    @Test
    fun deletedKeySlotUi_showsAddButtonAtEmptyPosition() {
        val binding = inflateBinding()
        val listener = mock(EditableFlickKeyboardView.OnKeyEditListener::class.java)
        binding.flickKeyboardView.setOnKeyEditListener(listener)
        val slot = deletedKeySlot("slot", GridPlacement(0, 0, 2, 2))
        val layout = KeyboardDefaultLayouts.createNumberTemplateLayout().copy(
            keys = emptyList(),
            items = listOf(slot),
            columnCount = 1,
            rowCount = 1,
            columnUnitCount = 2,
            rowUnitCount = 2
        )
        assertFalse(layout.usesFlexiblePlacement())

        binding.flickKeyboardView.setKeyboard(layout)

        val addButton = binding.flickKeyboardView.findViewWithTag<View>("deleted-key-slot:${slot.id}")
        assertEquals(View.VISIBLE, addButton.visibility)
        assertEquals(
            binding.root.context.getString(R.string.editor_add_key_to_empty_slot),
            addButton.contentDescription
        )
        assertTrue(addButton.performClick())
        verify(listener).onDeletedKeySlotSelected(slot.id)
    }

    private fun inflateBinding(): FragmentKeyboardEditorBinding {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_MarkdownKeyboard
        )
        return FragmentKeyboardEditorBinding.inflate(LayoutInflater.from(context))
    }

    private fun viewModel(): KeyboardEditorViewModel =
        KeyboardEditorViewModel(KeyboardRepository(mock(KeyboardLayoutDao::class.java)))
}
