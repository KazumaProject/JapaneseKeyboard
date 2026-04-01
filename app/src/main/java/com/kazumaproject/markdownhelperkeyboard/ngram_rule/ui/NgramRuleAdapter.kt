package com.kazumaproject.markdownhelperkeyboard.ngram_rule.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.ItemNgramRuleBinding

class NgramRuleAdapter(
    private val onItemClick: (NgramRuleListItem) -> Unit,
) : ListAdapter<NgramRuleListItem, NgramRuleAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNgramRuleBinding.inflate(
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
        private val binding: ItemNgramRuleBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: NgramRuleListItem) {
            binding.textRuleTitle.text = item.title
            binding.textRuleDetail.text = item.detail
            binding.textAdjustment.text = binding.root.context.getString(
                R.string.ngram_rule_adjustment_format,
                item.adjustment,
            )
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<NgramRuleListItem>() {
            override fun areItemsTheSame(oldItem: NgramRuleListItem, newItem: NgramRuleListItem): Boolean {
                return oldItem.stableId == newItem.stableId
            }

            override fun areContentsTheSame(oldItem: NgramRuleListItem, newItem: NgramRuleListItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}

data class NgramRuleListItem(
    val stableId: String,
    val title: String,
    val detail: String,
    val adjustment: Int,
    val kind: NgramRuleKind,
    val id: Int,
)

enum class NgramRuleKind {
    TWO_NODE,
    THREE_NODE,
}

