package com.kazumaproject.markdownhelperkeyboard.text_macro.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.databinding.ItemTextMacroBinding
import com.kazumaproject.markdownhelperkeyboard.text_macro.database.TextMacro

class TextMacroAdapter(
    private val onEdit: (TextMacro) -> Unit,
    private val onEnabledChanged: (TextMacro, Boolean) -> Unit,
) : ListAdapter<TextMacro, TextMacroAdapter.ViewHolder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemTextMacroBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemTextMacroBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(macro: TextMacro) = with(binding) {
            textMacroName.text = macro.name
            textMacroReading.text = macro.reading?.let { root.context.getString(
                com.kazumaproject.markdownhelperkeyboard.R.string.text_macro_reading_format,
                it,
            ) } ?: root.context.getString(
                com.kazumaproject.markdownhelperkeyboard.R.string.text_macro_no_reading
            )
            textMacroBody.text = macro.body
            textMacroEnabled.setOnCheckedChangeListener(null)
            textMacroEnabled.isChecked = macro.enabled
            textMacroEnabled.setOnCheckedChangeListener { _, checked ->
                if (checked != macro.enabled) onEnabledChanged(macro, checked)
            }
            root.setOnClickListener { onEdit(macro) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<TextMacro>() {
        override fun areItemsTheSame(oldItem: TextMacro, newItem: TextMacro): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: TextMacro, newItem: TextMacro): Boolean =
            oldItem == newItem
    }
}

