package com.kazumaproject.markdownhelperkeyboard.sumire_special_key.ui

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.InsertionTarget
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.view.EditableFlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentSumireSpecialKeyEditorBinding
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyActionDisplayMetadata
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyActionDisplayOverrideApplier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SumireSpecialKeyEditorFragment : Fragment(R.layout.fragment_sumire_special_key_editor),
    EditableFlickKeyboardView.OnKeyEditListener {
    private val viewModel: SumireSpecialKeyEditorViewModel by viewModels()
    private var _binding: FragmentSumireSpecialKeyEditorBinding? = null
    private val binding get() = _binding!!

    private val layoutTypes = listOf("toggle", "flick", "switch-mode-effective")
    private val inputModes = KeyboardInputMode.entries.toList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSumireSpecialKeyEditorBinding.bind(view)
        (activity as? AppCompatActivity)?.supportActionBar?.title =
            getString(R.string.sumire_special_key_editor_title)
        setupSpinners()
        binding.sumireSpecialKeyKeyboard.setOnKeyEditListener(this)
        binding.resetPlacementButton.setOnClickListener { viewModel.resetPlacement() }
        binding.resetAllButton.setOnClickListener { viewModel.resetAll() }
        observeViewModel()
    }

    override fun onDestroyView() {
        binding.sumireSpecialKeyKeyboard.removeOnKeyEditListener()
        _binding = null
        super.onDestroyView()
    }

    private fun setupSpinners() {
        binding.layoutTypeSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            layoutTypes
        )
        binding.inputModeSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            inputModes.map { it.name }
        )
        binding.layoutTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.updateLayoutType(layoutTypes[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.inputModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.updateInputMode(inputModes[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val layoutIndex = layoutTypes.indexOf(state.layoutType)
                    if (layoutIndex >= 0 && binding.layoutTypeSpinner.selectedItemPosition != layoutIndex) {
                        binding.layoutTypeSpinner.setSelection(layoutIndex)
                    }
                    val inputIndex = inputModes.indexOf(state.inputMode)
                    if (inputIndex >= 0 && binding.inputModeSpinner.selectedItemPosition != inputIndex) {
                        binding.inputModeSpinner.setSelection(inputIndex)
                    }
                    state.previewLayout?.let { layout ->
                        val displayLayout = SumireSpecialKeyActionDisplayOverrideApplier.apply(
                            layout = layout,
                            layoutType = state.layoutType,
                            inputMode = state.inputMode.name,
                            overrides = state.actionOverrides,
                            displayMetadata = sumireSpecialKeyActionDisplayMetadata()
                        )
                        binding.sumireSpecialKeyKeyboard.setKeyboard(
                            layout = displayLayout,
                            editTargetMode = EditableFlickKeyboardView.EditTargetMode.SUMIRE_SPECIAL_KEYS
                        )
                    }
                }
            }
        }
    }

    override fun onKeySelected(keyId: String) {
        val state = viewModel.uiState.value
        findNavController().navigate(
            R.id.action_sumireSpecialKeyEditorFragment_to_sumireSpecialKeyActionEditorFragment,
            bundleOf(
                "layoutType" to state.layoutType,
                "inputMode" to state.inputMode.name,
                "keyId" to keyId
            )
        )
    }

    override fun onKeysSwapped(draggedKeyId: String, targetKeyId: String) {
        viewModel.swapSpecialKeys(draggedKeyId, targetKeyId)
    }

    override fun onSpacerSelected(spacerId: String) = Unit
    override fun onRowDeleted(rowIndex: Int) = Unit
    override fun onColumnDeleted(columnIndex: Int) = Unit
    override fun onPlacementPointerTarget(target: InsertionTarget) = Unit
    override fun onPlacementTapTarget(target: InsertionTarget) = Unit
    override fun onPlacementDropTarget(target: InsertionTarget) = Unit

    private fun sumireSpecialKeyActionDisplayMetadata(): List<SumireSpecialKeyActionDisplayMetadata> {
        return KeyActionMapper.getDisplayActions(requireContext()).map {
            SumireSpecialKeyActionDisplayMetadata(
                action = it.action,
                displayName = it.displayName,
                iconResId = it.iconResId
            )
        }
    }
}
