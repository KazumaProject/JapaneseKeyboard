package com.kazumaproject.markdownhelperkeyboard.candidate_order.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.candidate_order.model.SavedCandidateOrderGroup
import com.kazumaproject.markdownhelperkeyboard.candidate_order.model.CandidateOrderScope
import com.kazumaproject.markdownhelperkeyboard.databinding.ListItemSavedCandidateOrderBinding

class SavedCandidateOrderAdapter(
    private val onEdit: (SavedCandidateOrderGroup) -> Unit,
    private val onDeleteRule: (String, CandidateOrderScope) -> Unit,
) : ListAdapter<SavedCandidateOrderGroup, SavedCandidateOrderAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemSavedCandidateOrderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ListItemSavedCandidateOrderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SavedCandidateOrderGroup) {
            binding.textSavedCandidateOrderInput.text = item.input
            binding.textSavedCandidateOrderScope.text = binding.root.context.getString(
                if (item.scope == CandidateOrderScope.EXACT_INPUT) {
                    com.kazumaproject.markdownhelperkeyboard.R.string.candidate_order_scope_exact_short
                } else {
                    com.kazumaproject.markdownhelperkeyboard.R.string.candidate_order_scope_lexical_short
                },
            )
            binding.textSavedCandidateOrderCandidates.text = item.candidates
                .mapIndexed { index, candidate -> "${index + 1}. $candidate" }
                .joinToString(separator = "\n")
            binding.root.setOnClickListener {
                onEdit(item)
            }
            binding.buttonDeleteSavedCandidateOrder.setOnClickListener {
                onDeleteRule(item.input, item.scope)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<SavedCandidateOrderGroup>() {
        override fun areItemsTheSame(
            oldItem: SavedCandidateOrderGroup,
            newItem: SavedCandidateOrderGroup
        ): Boolean = oldItem.input == newItem.input && oldItem.scope == newItem.scope

        override fun areContentsTheSame(
            oldItem: SavedCandidateOrderGroup,
            newItem: SavedCandidateOrderGroup
        ): Boolean = oldItem == newItem
    }
}
