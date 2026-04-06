package com.kazumaproject.markdownhelperkeyboard.gemma.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.databinding.ItemGemmaPromptTemplateBinding
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaPromptTemplate

class GemmaPromptTemplateAdapter(
    private val onToggle: (GemmaPromptTemplate, Boolean) -> Unit,
    private val onEdit: (GemmaPromptTemplate) -> Unit,
    private val onDelete: (GemmaPromptTemplate) -> Unit,
) : ListAdapter<GemmaPromptTemplate, GemmaPromptTemplateAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(
        val binding: ItemGemmaPromptTemplateBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGemmaPromptTemplateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            textTitle.text = item.title
            textPrompt.text = item.prompt

            switchEnable.setOnCheckedChangeListener(null)
            switchEnable.isChecked = item.isEnabled
            switchEnable.setOnCheckedChangeListener { _, isChecked ->
                onToggle(item, isChecked)
            }

            buttonEdit.setOnClickListener {
                onEdit(item)
            }
            buttonDelete.setOnClickListener {
                onDelete(item)
            }
            root.setOnClickListener {
                onEdit(item)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<GemmaPromptTemplate>() {
        override fun areItemsTheSame(
            oldItem: GemmaPromptTemplate,
            newItem: GemmaPromptTemplate
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: GemmaPromptTemplate,
            newItem: GemmaPromptTemplate
        ): Boolean {
            return oldItem == newItem
        }
    }
}
