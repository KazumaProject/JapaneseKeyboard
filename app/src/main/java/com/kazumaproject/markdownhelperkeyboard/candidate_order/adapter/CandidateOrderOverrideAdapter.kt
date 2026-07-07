package com.kazumaproject.markdownhelperkeyboard.candidate_order.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.candidate_order.model.CandidateOrderItem
import com.kazumaproject.markdownhelperkeyboard.databinding.ListItemCandidateOrderBinding

class CandidateOrderOverrideAdapter(
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : ListAdapter<CandidateOrderItem, CandidateOrderOverrideAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemCandidateOrderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ViewHolder(
        private val binding: ListItemCandidateOrderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun bind(item: CandidateOrderItem, position: Int) {
            binding.textCandidateOrderRank.text = (position + 1).toString()
            binding.textCandidateOrderCandidate.text = item.candidate
            binding.imageCandidateOrderDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                }
                false
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<CandidateOrderItem>() {
        override fun areItemsTheSame(
            oldItem: CandidateOrderItem,
            newItem: CandidateOrderItem
        ): Boolean = oldItem.candidate == newItem.candidate

        override fun areContentsTheSame(
            oldItem: CandidateOrderItem,
            newItem: CandidateOrderItem
        ): Boolean = oldItem == newItem
    }
}
