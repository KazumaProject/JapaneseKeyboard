package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_tab_order.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.databinding.ListItemCandidateTabBinding // 後で作成します
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.CandidateTab

class CandidateTabOrderAdapter(
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : ListAdapter<CandidateTab, CandidateTabOrderAdapter.CandidateTabViewHolder>(DiffCallback()) {

    private var isEditing: Boolean = false

    @SuppressLint("NotifyDataSetChanged")
    fun setEditMode(isEditing: Boolean) {
        if (this.isEditing != isEditing) {
            this.isEditing = isEditing
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CandidateTabViewHolder {
        val binding =
            ListItemCandidateTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CandidateTabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CandidateTabViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CandidateTabViewHolder(private val binding: ListItemCandidateTabBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.deleteIcon.setOnClickListener {
                if (absoluteAdapterPosition != RecyclerView.NO_POSITION) {
                    onDeleteClick(absoluteAdapterPosition)
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        fun bind(candidateTab: CandidateTab) {
            binding.tabName.text = getCandidateTabDisplayName(candidateTab) // XMLのIDを変更

            binding.dragHandle.visibility = if (isEditing) View.VISIBLE else View.GONE
            binding.deleteIcon.visibility = if (isEditing) View.VISIBLE else View.GONE

            if (isEditing) {
                binding.dragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        onStartDrag(this)
                    }
                    false
                }
            } else {
                binding.dragHandle.setOnTouchListener(null)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<CandidateTab>() {
        override fun areItemsTheSame(oldItem: CandidateTab, newItem: CandidateTab): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: CandidateTab, newItem: CandidateTab): Boolean {
            return oldItem == newItem
        }
    }

    // このヘルパー関数はFragmentにもありますが、Adapter内でも必要です
    private fun getCandidateTabDisplayName(candidateTab: CandidateTab): String {
        return when (candidateTab) {
            CandidateTab.PREDICTION -> "予測変換"
            CandidateTab.CONVERSION -> "通常変換"
            CandidateTab.EISUKANA -> "英数・かな"
        }
    }
}
