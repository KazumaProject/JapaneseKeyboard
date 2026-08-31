package com.kazumaproject.markdownhelperkeyboard.text_macro.ui

import android.annotation.SuppressLint
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.ItemTextMacroEditorBlockBinding
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroEditorBlock
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroVariable

class TextMacroEditorBlockAdapter(
    private val onTextChanged: (Long, String) -> Unit,
    private val onPatternChanged: (Long, String) -> Unit,
    private val onMove: (Long, Int) -> Unit,
    private val onDelete: (Long) -> Unit,
    private val onSelectInsertion: (Int) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
) : ListAdapter<TextMacroDraftBlock, TextMacroEditorBlockAdapter.ViewHolder>(Diff) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).editorId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemTextMacroEditorBlockBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position, itemCount)
    }

    inner class ViewHolder(
        private val binding: ItemTextMacroEditorBlockBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private var textWatcher: TextWatcher? = null
        private var patternWatcher: TextWatcher? = null

        init {
            bindDragHandle()
        }

        // The adjacent Up/Down buttons expose the same operation to accessibility services.
        @SuppressLint("ClickableViewAccessibility")
        private fun bindDragHandle() {
            binding.textMacroBlockDrag.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) onStartDrag(this)
                false
            }
        }

        fun bind(item: TextMacroDraftBlock, position: Int, count: Int) = with(binding) {
            textMacroBlockUp.isEnabled = position > 0
            textMacroBlockDown.isEnabled = position < count - 1
            textMacroBlockUp.setOnClickListener { onMove(item.editorId, -1) }
            textMacroBlockDown.setOnClickListener { onMove(item.editorId, 1) }
            textMacroBlockDelete.setOnClickListener { onDelete(item.editorId) }
            textMacroInsertAfterButton.setOnClickListener {
                val currentPosition = bindingAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    onSelectInsertion(currentPosition + 1)
                }
            }

            textMacroTextBlockInput.removeTextChangedListener(textWatcher)
            textMacroPatternInput.removeTextChangedListener(patternWatcher)
            when (val block = item.block) {
                is TextMacroEditorBlock.Text -> bindText(item.editorId, block)
                is TextMacroEditorBlock.Token -> bindToken(item.editorId, block)
            }
        }

        private fun bindText(editorId: Long, block: TextMacroEditorBlock.Text) = with(binding) {
            textMacroBlockTitle.setText(R.string.text_macro_text_block_label)
            textMacroBlockSyntax.isVisible = false
            textMacroTextBlockLayout.isVisible = true
            textMacroBlockDescription.isVisible = false
            textMacroBlockExample.isVisible = false
            textMacroBlockRestriction.isVisible = false
            textMacroPatternLayout.isVisible = false
            if (textMacroTextBlockInput.text?.toString() != block.value) {
                textMacroTextBlockInput.setText(block.value)
            }
            textWatcher = SimpleTextWatcher { onTextChanged(editorId, it) }.also {
                textMacroTextBlockInput.addTextChangedListener(it)
            }
        }

        private fun bindToken(editorId: Long, block: TextMacroEditorBlock.Token) = with(binding) {
            val variable = TextMacroVariable.fromTokenName(block.name) ?: return@with
            val presentation = variable.presentation(root.context)
            textMacroBlockTitle.text = presentation.title
            textMacroBlockSyntax.text = variable.source(block.argument)
            textMacroBlockSyntax.isVisible = true
            textMacroTextBlockLayout.isVisible = false
            textMacroBlockDescription.text = presentation.description
            textMacroBlockDescription.isVisible = true
            textMacroBlockExample.text = presentation.example
            textMacroBlockExample.isVisible = true
            textMacroBlockRestriction.text = presentation.restriction
            textMacroBlockRestriction.isVisible = presentation.restriction != null
            textMacroPatternLayout.isVisible = variable.acceptsPattern
            if (textMacroPatternInput.text?.toString() != block.argument.orEmpty()) {
                textMacroPatternInput.setText(block.argument.orEmpty())
            }
            if (variable.acceptsPattern) {
                patternWatcher = SimpleTextWatcher { onPatternChanged(editorId, it) }.also {
                    textMacroPatternInput.addTextChangedListener(it)
                }
            }
        }
    }

    private class SimpleTextWatcher(
        private val onChanged: (String) -> Unit,
    ) : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = onChanged(s?.toString().orEmpty())
    }

    private object Diff : DiffUtil.ItemCallback<TextMacroDraftBlock>() {
        override fun areItemsTheSame(
            oldItem: TextMacroDraftBlock,
            newItem: TextMacroDraftBlock,
        ): Boolean = oldItem.editorId == newItem.editorId

        override fun areContentsTheSame(
            oldItem: TextMacroDraftBlock,
            newItem: TextMacroDraftBlock,
        ): Boolean = oldItem == newItem
    }
}
