package com.kazumaproject.markdownhelperkeyboard.zeroquery.custom.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.ListItemCustomZeroQueryGroupBinding
import com.kazumaproject.markdownhelperkeyboard.repository.CustomZeroQueryGroup

class CustomZeroQueryGroupAdapter(
    private val onOpen: (CustomZeroQueryGroup) -> Unit,
    private val onAddCandidate: (CustomZeroQueryGroup) -> Unit,
    private val onMore: (CustomZeroQueryGroup, android.view.View) -> Unit,
) : ListAdapter<CustomZeroQueryGroup, CustomZeroQueryGroupAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemCustomZeroQueryGroupBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ListItemCustomZeroQueryGroupBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(group: CustomZeroQueryGroup) {
            val context = binding.root.context
            binding.textCustomZeroQueryGroupKey.text = group.displayKey
            binding.textCustomZeroQueryGroupSummary.text = context.getString(
                R.string.custom_zero_query_group_summary,
                group.entries.size,
                group.enabledCount,
            )
            binding.textCustomZeroQueryGroupPreview.text = group.entries
                .take(6)
                .joinToString(separator = context.getString(R.string.custom_zero_query_preview_separator)) {
                    it.candidate
                }
            binding.textCustomZeroQueryGroupStatus.text = if (group.enabledCount > 0) {
                context.getString(R.string.custom_zero_query_status_enabled)
            } else {
                context.getString(R.string.custom_zero_query_status_disabled)
            }
            binding.root.alpha = if (group.enabledCount > 0) 1f else 0.58f
            binding.root.setOnClickListener { onOpen(group) }
            binding.buttonCustomZeroQueryGroupAdd.setOnClickListener { onAddCandidate(group) }
            binding.buttonCustomZeroQueryGroupMore.setOnClickListener {
                onMore(group, binding.buttonCustomZeroQueryGroupMore)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<CustomZeroQueryGroup>() {
        override fun areItemsTheSame(
            oldItem: CustomZeroQueryGroup,
            newItem: CustomZeroQueryGroup,
        ): Boolean = oldItem.lookupKey == newItem.lookupKey

        override fun areContentsTheSame(
            oldItem: CustomZeroQueryGroup,
            newItem: CustomZeroQueryGroup,
        ): Boolean = oldItem == newItem
    }
}
