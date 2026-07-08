package com.kazumaproject.markdownhelperkeyboard.zeroquery.custom.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.databinding.ListItemCustomZeroQueryCandidateBinding
import com.kazumaproject.markdownhelperkeyboard.zeroquery.custom.CustomZeroQueryEntry

class CustomZeroQueryCandidateAdapter(
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onEnabledChanged: (CustomZeroQueryEntry, Boolean) -> Unit,
    private val onMore: (CustomZeroQueryEntry, View) -> Unit,
) : ListAdapter<CustomZeroQueryEntry, CustomZeroQueryCandidateAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemCustomZeroQueryCandidateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ViewHolder(
        private val binding: ListItemCustomZeroQueryCandidateBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun bind(entry: CustomZeroQueryEntry, position: Int) {
            binding.textCustomZeroQueryCandidateRank.text = (position + 1).toString()
            binding.textCustomZeroQueryCandidate.text = entry.candidate
            binding.switchCustomZeroQueryCandidateEnabled.setOnCheckedChangeListener(null)
            binding.switchCustomZeroQueryCandidateEnabled.isChecked = entry.enabled
            binding.switchCustomZeroQueryCandidateEnabled.setOnCheckedChangeListener { _, checked ->
                onEnabledChanged(entry, checked)
            }
            binding.root.alpha = if (entry.enabled) 1f else 0.58f
            binding.buttonCustomZeroQueryCandidateMore.setOnClickListener {
                onMore(entry, binding.buttonCustomZeroQueryCandidateMore)
            }
            binding.imageCustomZeroQueryCandidateDrag.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                }
                false
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<CustomZeroQueryEntry>() {
        override fun areItemsTheSame(
            oldItem: CustomZeroQueryEntry,
            newItem: CustomZeroQueryEntry,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: CustomZeroQueryEntry,
            newItem: CustomZeroQueryEntry,
        ): Boolean = oldItem == newItem
    }
}
