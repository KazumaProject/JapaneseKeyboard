package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.view.EditableFlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyboardEditorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class KeyboardEditorFragment : Fragment(R.layout.fragment_keyboard_editor),
    EditableFlickKeyboardView.OnKeyEditListener {

    private val viewModel: KeyboardEditorViewModel by hiltNavGraphViewModels(R.id.mobile_navigation)
    private val args: KeyboardEditorFragmentArgs by navArgs()

    private var _binding: FragmentKeyboardEditorBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentKeyboardEditorBinding.bind(view)
        setupToolbarAndMenu()
        viewModel.start(args.layoutId)
        setupUIListeners()
        observeViewModel()
    }

    private fun setupToolbarAndMenu() {
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = getString(R.string.edit_keyboard)
            setDisplayHomeAsUpEnabled(true)
        }
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_keyboard_editor, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        findNavController().popBackStack()
                        viewModel.onCancelEditing()
                        true
                    }

                    R.id.action_save -> {
                        viewModel.saveLayout()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupUIListeners() {
        binding.flickKeyboardView.setOnKeyEditListener(this)
        binding.keyboardNameEdittext.doAfterTextChanged { text ->
            if (text.toString() != viewModel.uiState.value.name) {
                viewModel.updateName(text.toString())
            }
        }
        binding.switchRomaji.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != viewModel.uiState.value.isRomaji) {
                viewModel.updateIsRomaji(isChecked)
            }
        }
        binding.buttonAddRow.setOnClickListener { viewModel.addRow() }
        binding.buttonRemoveRow.setOnClickListener { viewModel.removeRow() }
        binding.buttonAddCol.setOnClickListener { viewModel.addColumn() }
        binding.buttonRemoveCol.setOnClickListener { viewModel.removeColumn() }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        updateUi(state)
                        if (state.navigateBack) {
                            findNavController().popBackStack()
                            viewModel.onDoneNavigating()
                        }
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        if (state.duplicateNameError) {
                            showDuplicateNameDialog()
                            viewModel.clearDuplicateNameError()
                        }
                    }
                }
            }
        }
    }

    private fun showDuplicateNameDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("キーボード名が重複しています")
            .setMessage("別のキーボード名に修正してください。")
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun updateUi(state: EditorUiState) {
        if (state.isLoading) {
            binding.editModePanel.isVisible = false
            return
        }
        binding.editModePanel.isVisible = true
        if (binding.keyboardNameEdittext.text.toString() != state.name) {
            binding.keyboardNameEdittext.setText(state.name)
            binding.keyboardNameEdittext.setSelection(state.name.length)
        }
        if (binding.switchRomaji.isChecked != state.isRomaji) {
            binding.switchRomaji.isChecked = state.isRomaji
        }
        binding.flickKeyboardView.setKeyboard(state.layout)
    }

    override fun onKeySelected(keyId: String) {
        Timber.d("onKeySelected: keyId = $keyId")
        viewModel.selectKeyForEditing(keyId)
        findNavController().navigate(R.id.action_keyboardEditorFragment_to_keyEditorFragment)
    }

    override fun onKeysSwapped(draggedKeyId: String, targetKeyId: String) {
        Timber.d("onKeysSwapped: dragged=$draggedKeyId, target=$targetKeyId")
        viewModel.swapKeys(draggedKeyId, targetKeyId)
    }

    // ▼▼▼ ここから追加 ▼▼▼
    override fun onRowDeleted(rowIndex: Int) {
        Timber.d("onRowDeleted: rowIndex = $rowIndex")
        viewModel.deleteRowAt(rowIndex)
    }

    override fun onColumnDeleted(columnIndex: Int) {
        Timber.d("onColumnDeleted: columnIndex = $columnIndex")
        viewModel.deleteColumnAt(columnIndex)
    }
    // ▲▲▲ ここまで追加 ▲▲▲

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = null
            setDisplayHomeAsUpEnabled(false)
        }
        binding.flickKeyboardView.removeOnKeyEditListener()
        _binding = null
    }
}
