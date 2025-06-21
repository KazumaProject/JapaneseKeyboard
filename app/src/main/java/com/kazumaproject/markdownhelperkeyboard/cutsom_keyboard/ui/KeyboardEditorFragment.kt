package com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.ui.view.EditableFlickKeyboardView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as AppCompatActivity).supportActionBar?.apply {
            hide()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentKeyboardEditorBinding.bind(view)

        viewModel.start(args.layoutId)

        setupUIListeners()
        observeViewModel()
    }

    private fun setupUIListeners() {
        binding.flickKeyboardView.setOnKeyEditListener(this)
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_save -> {
                    // ▼▼▼ Fragmentが保持しているargs.layoutIdを直接渡す ▼▼▼
                    val idToSave = if (args.layoutId == -1L) null else args.layoutId
                    viewModel.saveLayout(idToSave)
                    true
                }

                else -> false
            }
        }

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
                launch {
                    viewModel.uiState.collect { state ->
                        updateUi(state)
                    }
                }
                launch {
                    viewModel.uiState.collect {
                        if (it.navigateBack) {
                            findNavController().popBackStack()
                            viewModel.onDoneNavigating()
                        }
                    }
                }
            }
        }
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
        binding.flickKeyboardView.removeOnKeyEditListener()
        _binding = null
    }
}
