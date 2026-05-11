package com.kazumaproject.markdownhelperkeyboard.sumire_special_key.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentSumireSpecialKeyActionEditorBinding
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyOverrideType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SumireSpecialKeyActionEditorFragment :
    Fragment(R.layout.fragment_sumire_special_key_action_editor) {
    private val viewModel: SumireSpecialKeyActionEditorViewModel by viewModels()
    private var _binding: FragmentSumireSpecialKeyActionEditorBinding? = null
    private val binding get() = _binding!!

    private val rows = mutableMapOf<SumireSpecialKeyDirection, TextView>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSumireSpecialKeyActionEditorBinding.bind(view)
        (activity as? AppCompatActivity)?.supportActionBar?.title =
            getString(R.string.sumire_special_key_action_editor_title)
        setupRows()
        binding.saveButton.setOnClickListener { viewModel.save() }
        binding.resetKeyButton.setOnClickListener { viewModel.resetThisKey() }
        observeViewModel()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun setupRows() {
        SumireSpecialKeyDirection.entries.forEach { direction ->
            val row = TextView(requireContext()).apply {
                textSize = 18f
                setPadding(16, 24, 16, 24)
                setOnClickListener { showDirectionDialog(direction) }
            }
            rows[direction] = row
            binding.actionRowsContainer.addView(row)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.keyIdText.text = state.keyId
                    rows.forEach { (direction, row) ->
                        val draft = state.drafts[direction] ?: SumireSpecialKeyActionDraft()
                        row.text = "${direction.displayLabel()}: ${draft.displayText()}"
                    }
                    if (state.navigateBack) {
                        findNavController().popBackStack()
                        viewModel.onDoneNavigating()
                    }
                }
            }
        }
    }

    private fun showDirectionDialog(direction: SumireSpecialKeyDirection) {
        val options = arrayOf(
            getString(R.string.sumire_special_key_default),
            getString(R.string.sumire_special_key_none),
            getString(R.string.sumire_special_key_input_text),
            getString(R.string.sumire_special_key_key_action)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(direction.displayLabel())
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> viewModel.setDefault(direction)
                    1 -> viewModel.setNone(direction)
                    2 -> showInputTextDialog(direction)
                    3 -> showActionDialog(direction)
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun showInputTextDialog(direction: SumireSpecialKeyDirection) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            hint = getString(R.string.sumire_special_key_input_text_hint)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.sumire_special_key_input_text))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                viewModel.setInputText(direction, input.text.toString())
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showActionDialog(direction: SumireSpecialKeyDirection) {
        val actions = KeyActionMapper.getDisplayActions(requireContext())
            .filter { KeyActionMapper.fromKeyAction(it.action) != null }
        val labels = actions.map { it.displayName }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.sumire_special_key_key_action))
            .setItems(labels) { dialog, which ->
                viewModel.setKeyAction(direction, actions[which].action)
                dialog.dismiss()
            }
            .show()
    }

    private fun SumireSpecialKeyDirection.displayLabel(): String {
        return when (this) {
            SumireSpecialKeyDirection.TAP -> "Tap"
            SumireSpecialKeyDirection.UP -> "上"
            SumireSpecialKeyDirection.RIGHT -> "右"
            SumireSpecialKeyDirection.DOWN -> "下"
            SumireSpecialKeyDirection.LEFT -> "左"
        }
    }

    private fun SumireSpecialKeyActionDraft.displayText(): String {
        return when (overrideType) {
            SumireSpecialKeyOverrideType.DEFAULT ->
                getString(R.string.sumire_special_key_default)

            SumireSpecialKeyOverrideType.NONE ->
                getString(R.string.sumire_special_key_none)

            SumireSpecialKeyOverrideType.INPUT_TEXT ->
                inputText.orEmpty()

            SumireSpecialKeyOverrideType.KEY_ACTION ->
                actionString.orEmpty()
        }
    }
}

