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

    // The deprecated setHasOptionsMenu call in onCreate has been removed.

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentKeyboardEditorBinding.bind(view)

        setupToolbarAndMenu()

        viewModel.start(args.layoutId)
        setupUIListeners()
        observeViewModel()
    }

    private fun setupToolbarAndMenu() {
        // Set up the activity's action bar
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = getString(R.string.edit_keyboard) // Set a title for the screen
            setDisplayHomeAsUpEnabled(true) // Show the back arrow
        }

        // Add the menu provider, which is the modern way to handle menus in fragments.
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Add menu items here
                menuInflater.inflate(R.menu.menu_keyboard_editor, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle the menu selection
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

    // The deprecated onCreateOptionsMenu and onOptionsItemSelected overrides have been removed.

    private fun setupUIListeners() {
        binding.flickKeyboardView.setOnKeyEditListener(this)

        binding.keyboardNameEdittext.doAfterTextChanged { text ->
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
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // UI update and navigation observation
                launch {
                    viewModel.uiState.collect { state ->
                        updateUi(state)
                        if (state.navigateBack) {
                            findNavController().popBackStack()
                            viewModel.onDoneNavigating()
                        }
                    }
                }

                // Observe for duplicate name errors
                launch {
                    viewModel.uiState.collect { state ->
                        if (state.duplicateNameError) {
                            showDuplicateNameDialog()
                            viewModel.clearDuplicateNameError() // Reset the error state after showing the dialog
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

        binding.flickKeyboardView.setKeyboard(state.layout)
    }

    override fun onKeySelected(keyId: String) {
        Timber.d("onKeySelected: keyId = $keyId")
        viewModel.selectKeyForEditing(keyId)
        findNavController().navigate(R.id.action_keyboardEditorFragment_to_keyEditorFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Good practice to clean up action bar modifications.
        // The MenuProvider is automatically removed by the lifecycle owner, so no need for manual removal.
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = null
            setDisplayHomeAsUpEnabled(false)
        }
        binding.flickKeyboardView.removeOnKeyEditListener()
        _binding = null
    }
}
