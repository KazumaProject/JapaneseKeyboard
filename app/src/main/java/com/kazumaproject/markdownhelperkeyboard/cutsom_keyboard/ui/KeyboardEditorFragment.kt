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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
                    viewModel.saveLayout()
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
                // UI更新とナビゲーションの監視
                launch {
                    viewModel.uiState.collect { state ->
                        updateUi(state)
                        if (state.navigateBack) {
                            findNavController().popBackStack()
                            viewModel.onDoneNavigating()
                        }
                    }
                }

                // ▼▼▼ 重複エラーを監視する処理を追加 ▼▼▼
                launch {
                    viewModel.uiState.collect { state ->
                        if (state.duplicateNameError) {
                            showDuplicateNameDialog()
                            viewModel.clearDuplicateNameError() // ダイアログ表示後にエラー状態をリセット
                        }
                    }
                }
            }
        }
    }

    // ▼▼▼ ダイアログを表示する関数を追加 ▼▼▼
    private fun showDuplicateNameDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("名前が重複しています")
            .setMessage("同じ名前のキーボードレイアウトが既に存在します。別の名前を入力してください。")
            .setPositiveButton("OK") { dialog, _ ->
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
        binding.flickKeyboardView.removeOnKeyEditListener()
        _binding = null
    }
}
