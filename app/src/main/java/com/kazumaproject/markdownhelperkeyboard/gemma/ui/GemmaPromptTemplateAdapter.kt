package com.kazumaproject.markdownhelperkeyboard.gemma.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.databinding.ItemGemmaPromptTemplateBinding
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaPromptTemplate
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaInputModality
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaOutputMode
import com.kazumaproject.markdownhelperkeyboard.gemma.database.modality
import com.kazumaproject.markdownhelperkeyboard.gemma.database.output

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
            val modalityLabel = when (item.modality()) {
                GemmaInputModality.TEXT -> root.context.getString(R.string.gemma_action_modality_text)
                GemmaInputModality.IMAGE -> root.context.getString(R.string.gemma_action_modality_image)
                GemmaInputModality.AUDIO -> root.context.getString(R.string.gemma_action_modality_audio)
            }
            val outputLabel = when (item.output()) {
                GemmaOutputMode.SINGLE_TEXT -> root.context.getString(R.string.gemma_action_output_single)
                GemmaOutputMode.MULTILINE_TEXT -> root.context.getString(R.string.gemma_action_output_multiline)
                GemmaOutputMode.CANDIDATE_LIST -> root.context.getString(R.string.gemma_action_output_candidates)
            }
            val builtInLabel = if (item.isBuiltIn) {
                " · ${root.context.getString(R.string.gemma_action_built_in)}"
            } else {
                ""
            }
            textModality.text = "$modalityLabel · $outputLabel$builtInLabel"

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
            buttonDelete.isVisible = !item.isBuiltIn
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
