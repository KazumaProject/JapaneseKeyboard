package com.kazumaproject.markdownhelperkeyboard.text_macro.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentTextMacroEditorBinding
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroVariable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TextMacroEditorFragment : Fragment() {
    private var _binding: FragmentTextMacroEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TextMacroEditorViewModel by viewModels()
    private lateinit var blockAdapter: TextMacroEditorBlockAdapter
    private lateinit var variableAdapter: TextMacroVariableAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var rendering = false
    private var shownRemovedBlockId: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTextMacroEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBlockEditor()
        setupVariableCatalog()
        setupInputs()
        setupBackHandling()
        collectState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupBlockEditor() {
        blockAdapter = TextMacroEditorBlockAdapter(
            onTextChanged = viewModel::updateTextBlock,
            onPatternChanged = viewModel::updateTokenPattern,
            onMove = viewModel::moveBlockBy,
            onDelete = viewModel::removeBlock,
            onSelectInsertion = ::selectInsertionAndShowCatalog,
            onStartDrag = { itemTouchHelper.startDrag(it) },
        )
        binding.textMacroBlocksRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = blockAdapter
        }
        itemTouchHelper = ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0,
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean = viewModel.moveBlock(
                    viewHolder.bindingAdapterPosition,
                    target.bindingAdapterPosition,
                )

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

                override fun isLongPressDragEnabled(): Boolean = false
            }
        ).also { it.attachToRecyclerView(binding.textMacroBlocksRecyclerView) }
    }

    private fun setupVariableCatalog() {
        variableAdapter = TextMacroVariableAdapter(::insertVariable)
        binding.textMacroVariableRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = variableAdapter
        }
    }

    private fun setupInputs() = with(binding) {
        textMacroNameInput.doAfterTextChanged {
            if (!rendering) viewModel.setName(it?.toString().orEmpty())
        }
        textMacroCallKeywordInput.doAfterTextChanged {
            if (!rendering) viewModel.setReading(it?.toString().orEmpty())
        }
        textMacroSourceInput.doAfterTextChanged {
            if (!rendering) viewModel.setSource(it?.toString().orEmpty())
        }
        textMacroEnabledSwitch.setOnCheckedChangeListener { _, checked ->
            if (!rendering) viewModel.setEnabled(checked)
        }
        textMacroEditorModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || rendering) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.text_macro_blocks_mode_button -> {
                    if (!viewModel.showBlockMode()) focusSyntaxError()
                }
                R.id.text_macro_source_mode_button -> viewModel.showSourceMode()
            }
        }
        textMacroInsertAtStartButton.setOnClickListener {
            selectInsertionAndShowCatalog(0)
        }
        textMacroAddTextButton.setOnClickListener { viewModel.addTextBlock() }
        textMacroSaveButton.setOnClickListener {
            viewModel.save()
            root.post {
                if (
                    viewModel.uiState.value.mode == TextMacroEditorMode.SOURCE &&
                    viewModel.uiState.value.syntaxErrorMessage != null
                ) {
                    focusSyntaxError()
                }
            }
        }
        textMacroDeleteButton.setOnClickListener { confirmDelete() }
    }

    private fun setupBackHandling() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (viewModel.uiState.value.dirty) confirmDiscard()
                    else findNavController().popBackStack()
                }
            }
        )
    }

    private fun collectState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: TextMacroEditorUiState) = with(binding) {
        if (state.saved || state.deleted || state.loadFailed) {
            if (state.loadFailed) {
                Snackbar.make(root, R.string.text_macro_load_failed, Snackbar.LENGTH_LONG).show()
            }
            viewModel.consumeNavigationEvent()
            findNavController().popBackStack()
            return@with
        }

        rendering = true
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.setTitle(
            if (state.macroId == 0L) R.string.text_macro_editor_new_title
            else R.string.text_macro_editor_edit_title
        )
        if (textMacroNameInput.text?.toString() != state.name) textMacroNameInput.setText(state.name)
        if (textMacroCallKeywordInput.text?.toString() != state.reading) {
            textMacroCallKeywordInput.setText(state.reading)
        }
        textMacroEnabledSwitch.isChecked = state.enabled
        textMacroNameLayout.error = state.nameError
        textMacroCallKeywordLayout.error = state.readingError
        val sourceMode = state.mode == TextMacroEditorMode.SOURCE
        textMacroEditorModeGroup.check(
            if (sourceMode) R.id.text_macro_source_mode_button
            else R.id.text_macro_blocks_mode_button
        )
        textMacroBlocksContainer.isVisible = !sourceMode
        textMacroSourceLayout.isVisible = sourceMode
        if (textMacroSourceInput.text?.toString() != state.source) {
            textMacroSourceInput.setText(state.source)
            textMacroSourceInput.setSelection(state.source.length)
        }
        textMacroSyntaxError.isVisible = state.syntaxErrorMessage != null
        textMacroSyntaxError.text = state.syntaxErrorMessage?.let {
            getString(
                R.string.text_macro_syntax_error_position,
                state.syntaxErrorPosition ?: 1,
                it,
            )
        }
        textMacroPreviewText.text = state.preview
        textMacroDeleteButton.isVisible = state.macroId != 0L
        textMacroSaveButton.isEnabled = !state.loading && !state.saving
        textMacroEditorProgress.isVisible = state.loading || state.saving
        textMacroEditorScroll.isVisible = !state.loading
        blockAdapter.submitList(state.blocks)
        variableAdapter.setCursorAlreadyAdded(state.containsCursor)
        rendering = false

        if (state.removedBlock == null) {
            shownRemovedBlockId = null
        } else {
            showUndo(state.removedBlock)
        }
    }

    private fun insertVariable(variable: TextMacroVariable) {
        val state = viewModel.uiState.value
        if (state.mode == TextMacroEditorMode.BLOCKS) {
            viewModel.addVariable(variable)
            return
        }
        val editor = binding.textMacroSourceInput
        val start = editor.selectionStart.coerceAtLeast(0)
        val end = editor.selectionEnd.coerceAtLeast(start)
        val token = variable.source()
        editor.text?.replace(start, end, token)
        editor.setSelection(start + token.length)
        editor.requestFocus()
    }

    private fun selectInsertionAndShowCatalog(index: Int) {
        viewModel.selectInsertionIndex(index)
        binding.textMacroEditorScroll.post {
            binding.textMacroEditorScroll.smoothScrollTo(
                0,
                binding.textMacroVariableRecyclerView.top,
            )
        }
    }

    private fun focusSyntaxError() {
        val position = (viewModel.uiState.value.syntaxErrorPosition ?: 1) - 1
        binding.textMacroSourceInput.requestFocus()
        binding.textMacroSourceInput.setSelection(
            position.coerceIn(0, binding.textMacroSourceInput.text?.length ?: 0)
        )
    }

    private fun showUndo(removed: RemovedTextMacroBlock) {
        if (shownRemovedBlockId == removed.item.editorId) return
        shownRemovedBlockId = removed.item.editorId
        Snackbar.make(binding.root, R.string.text_macro_block_removed, Snackbar.LENGTH_LONG)
            .setAction(R.string.text_macro_undo) { viewModel.undoRemove() }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (viewModel.uiState.value.removedBlock?.item?.editorId ==
                        removed.item.editorId
                    ) {
                        viewModel.consumeRemovedBlock()
                    }
                }
            })
            .show()
    }

    private fun confirmDelete() {
        val state = viewModel.uiState.value
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_delete_title)
            .setMessage(getString(R.string.text_macro_delete_confirm, state.name))
            .setPositiveButton(R.string.delete_string) { _, _ -> viewModel.delete() }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }

    private fun confirmDiscard() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.text_macro_discard_title)
            .setMessage(R.string.text_macro_discard_message)
            .setPositiveButton(R.string.text_macro_discard_action) { _, _ ->
                findNavController().popBackStack()
            }
            .setNegativeButton(R.string.cancel_string, null)
            .show()
    }
}
