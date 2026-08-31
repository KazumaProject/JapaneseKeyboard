package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.content.DialogInterface
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.checkbox.MaterialCheckBox
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.Shadows.shadowOf
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

    @Test
    fun deletionDialogSpecs_warnForEveryTargetAndOnlyButtonHasSessionOptOut() {
        val rowSpec = keyboardEditorDeletionDialogSpec(KeyboardEditorDeletionTarget.ROW)
        val columnSpec = keyboardEditorDeletionDialogSpec(KeyboardEditorDeletionTarget.COLUMN)
        val buttonSpec = keyboardEditorDeletionDialogSpec(KeyboardEditorDeletionTarget.BUTTON)

        assertEquals(R.string.editor_delete_row_confirmation_title, rowSpec.titleRes)
        assertEquals(R.string.editor_delete_column_confirmation_title, columnSpec.titleRes)
        assertEquals(R.string.editor_delete_button_confirmation_title, buttonSpec.titleRes)
        assertFalse(rowSpec.showsButtonWarningOptOut)
        assertFalse(columnSpec.showsButtonWarningOptOut)
        assertTrue(buttonSpec.showsButtonWarningOptOut)
    }

    @Test
    fun buttonDeletionDialog_showsSessionOptOutAndAppliesItOnlyWhenConfirmed() {
        var deletionConfirmed = false
        var warningSuppressed = false
        val dialog = showKeyboardEditorDeletionConfirmationDialog(
            context = themedContext(),
            target = KeyboardEditorDeletionTarget.BUTTON,
            onDeletionConfirmed = { deletionConfirmed = true },
            onButtonWarningSuppressed = { warningSuppressed = true }
        )
        val optOut = dialog.window?.decorView
            ?.findViewWithTag<MaterialCheckBox>(BUTTON_DELETION_WARNING_OPT_OUT_TAG)

        assertEquals(
            themedContext().getString(R.string.editor_hide_button_delete_warning_during_editing),
            optOut?.text
        )
        requireNotNull(optOut).isChecked = true
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(deletionConfirmed)
        assertTrue(warningSuppressed)
    }

    @Test
    fun rowAndColumnDeletionDialogs_doNotShowButtonWarningOptOut() {
        listOf(
            KeyboardEditorDeletionTarget.ROW,
            KeyboardEditorDeletionTarget.COLUMN
        ).forEach { target ->
            val dialog = showKeyboardEditorDeletionConfirmationDialog(
                context = themedContext(),
                target = target,
                onDeletionConfirmed = {},
                onButtonWarningSuppressed = {}
            )

            assertNull(
                dialog.window?.decorView
                    ?.findViewWithTag<View>(BUTTON_DELETION_WARNING_OPT_OUT_TAG)
            )
            dialog.dismiss()
        }
    }

    private fun inflateBinding(): FragmentKeyboardEditorBinding {
        return FragmentKeyboardEditorBinding.inflate(LayoutInflater.from(themedContext()))
    }

    private fun themedContext(): ContextThemeWrapper =
        ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_MarkdownKeyboard
        )

    private fun viewModel(): KeyboardEditorViewModel =
        KeyboardEditorViewModel(KeyboardRepository(mock(KeyboardLayoutDao::class.java)))
}
