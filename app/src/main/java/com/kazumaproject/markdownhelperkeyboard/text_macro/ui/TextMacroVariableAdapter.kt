package com.kazumaproject.markdownhelperkeyboard.text_macro.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.ItemTextMacroVariableBinding
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroVariable

class TextMacroVariableAdapter(
    private val onVariableClick: (TextMacroVariable) -> Unit,
) : RecyclerView.Adapter<TextMacroVariableAdapter.ViewHolder>() {
    private val variables = TextMacroVariable.entries
    private var cursorAlreadyAdded = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemTextMacroVariableBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(variables[position])
    }

    override fun getItemCount(): Int = variables.size

    fun setCursorAlreadyAdded(value: Boolean) {
        if (cursorAlreadyAdded == value) return
        cursorAlreadyAdded = value
        val cursorPosition = variables.indexOf(TextMacroVariable.CURSOR)
        if (cursorPosition >= 0) notifyItemChanged(cursorPosition)
    }

    inner class ViewHolder(
        private val binding: ItemTextMacroVariableBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(variable: TextMacroVariable) = with(binding) {
            val presentation = variable.presentation(root.context)
            val enabled = variable != TextMacroVariable.CURSOR || !cursorAlreadyAdded
            textMacroVariableTitle.text = presentation.title
            textMacroVariableSyntax.text = presentation.syntax
            textMacroVariableDescription.text = presentation.description
            textMacroVariableExample.text = presentation.example
            textMacroVariableRestriction.text = presentation.restriction
            textMacroVariableRestriction.isVisible = presentation.restriction != null
            textMacroVariableAdd.isEnabled = enabled
            textMacroVariableAdd.setText(
                if (enabled) R.string.text_macro_add_variable_short
                else R.string.text_macro_variable_added
            )
            root.isEnabled = enabled
            root.alpha = if (enabled) 1f else 0.55f
            val click = if (enabled) View.OnClickListener { onVariableClick(variable) } else null
            root.setOnClickListener(click)
            textMacroVariableAdd.setOnClickListener(click)
        }
    }
}
