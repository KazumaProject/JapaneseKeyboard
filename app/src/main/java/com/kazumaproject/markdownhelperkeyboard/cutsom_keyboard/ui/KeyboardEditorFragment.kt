package com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyboardEditorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class KeyboardEditorFragment : Fragment(R.layout.fragment_keyboard_editor),
    FlickKeyboardView.OnKeyboardActionListener {

    // Use hiltNavGraphViewModels to get a ViewModel instance scoped to the navigation graph.
    // This allows this Fragment and KeyEditorFragment to share the same ViewModel instance.
    // Make sure your NavGraph has an ID, e.g., android:id="@+id/keyboard_editor_nav_graph"
    private val viewModel: KeyboardEditorViewModel by viewModels()

    private var _binding: FragmentKeyboardEditorBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as AppCompatActivity).supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentKeyboardEditorBinding.bind(view)

        setupUIListeners()
        observeViewModel()
    }

    private fun setupUIListeners() {
        binding.flickKeyboardView.setOnKeyboardActionListener(this)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_save -> {
                    viewModel.saveLayout()
                    true
                }

                R.id.action_toggle_mode -> {
                    //viewModel.toggleEditMode()
                    true
                }

                else -> false
            }
        }

        binding.keyboardNameEdittext.doAfterTextChanged { text ->
            // Only update if the text is different from the state to prevent infinite loops
            if (text.toString() != viewModel.uiState.value.name) {
                viewModel.updateName(text.toString())
            }
        }

        binding.buttonAddRow.setOnClickListener { viewModel.addRow() }
        binding.buttonRemoveRow.setOnClickListener { viewModel.removeRow() }
        binding.buttonAddCol.setOnClickListener { viewModel.addColumn() }
        binding.buttonRemoveCol.setOnClickListener { viewModel.removeColumn() }
    }

    private fun observeViewModel() {
        viewModel.uiState.onEach { state ->
            updateUi(state)
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun updateUi(state: EditorUiState) {
        // Update keyboard name, preventing cursor jumps
        if (binding.keyboardNameEdittext.text.toString() != state.name) {
            binding.keyboardNameEdittext.setText(state.name)
            binding.keyboardNameEdittext.setSelection(state.name.length)
        }

        // Update the keyboard view
        binding.flickKeyboardView.setKeyboard(state.layout)

        // Update UI based on edit/preview mode
        binding.editModePanel.isVisible = state.isEditMode
        binding.previewOutputText.isVisible = !state.isEditMode

        val menu = binding.toolbar.menu
        val toggleItem = menu.findItem(R.id.action_toggle_mode)
        toggleItem?.title = if (state.isEditMode) {
            "プレビュー"
        } else {
            "編集"
        }

        // Handle navigation triggers
        if (state.selectedKeyIdentifier != null) {
            findNavController().navigate(R.id.action_keyboardEditorFragment_to_keyEditorFragment)
            // Reset the trigger to prevent re-navigation
            // viewModel.doneNavigatingToKeyEditor()
        }

        if (state.navigateBack) {
            Snackbar.make(binding.root, "Layout saved!", Snackbar.LENGTH_SHORT).show()
            findNavController().popBackStack()
            // Reset the trigger
            viewModel.onDoneNavigating()
        }
    }

    //region FlickKeyboardView.OnKeyboardActionListener Implementation
    override fun onKey(text: String) {
        if (viewModel.uiState.value.isEditMode) {
            // In Edit Mode, tapping a key selects it for editing
            //viewModel.selectKeyForEditing(keyData.keyId)
        } else {
            // In Preview Mode, type the character into the preview box
            binding.previewOutputText.append(text)
        }
    }

    override fun onAction(action: KeyAction) {
        val isEditMode = viewModel.uiState.value.isEditMode
        if (isEditMode) {
            //viewModel.selectKeyForEditing(keyData.keyId)
        } else {
            // Handle special actions in preview mode
            when (action) {
                is KeyAction.Delete, is KeyAction.Backspace -> {
                    val currentText = binding.previewOutputText.text
                    if (currentText.isNotEmpty()) {
                        binding.previewOutputText.text =
                            currentText.substring(0, currentText.length - 1)
                    }
                }

                is KeyAction.Space -> binding.previewOutputText.append(" ")
                is KeyAction.NewLine -> binding.previewOutputText.append("\n")
                else -> {
                    // Other actions can be ignored in preview
                }
            }
        }
    }

    // Other listener methods can be left empty for this screen's purpose
    override fun onActionLongPress(action: KeyAction) {}
    override fun onActionUpAfterLongPress(action: KeyAction) {}
    override fun onFlickDirectionChanged(direction: com.kazumaproject.custom_keyboard.data.FlickDirection) {}
    override fun onFlickActionLongPress(action: KeyAction) {}
    override fun onFlickActionUpAfterLongPress(action: KeyAction) {}
    //endregion

    override fun onDestroyView() {
        super.onDestroyView()
        // Clear the listener to avoid leaks
        binding.flickKeyboardView.removeKeyboardActionListener()
        _binding = null
    }
}
