package com.kazumaproject.markdownhelperkeyboard.custom_romaji.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.databinding.ListItemRomajiPairBinding

typealias RomajiPair = Pair<String, Pair<String, Int>>

class RomajiMapDetailAdapter(
    private val onEditClicked: (RomajiPair) -> Unit,
    private val onDeleteClicked: (RomajiPair) -> Unit
) : ListAdapter<RomajiPair, RomajiMapDetailAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemRomajiPairBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ListItemRomajiPairBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(romajiPair: RomajiPair) {
            binding.romajiKeyTextView.text = romajiPair.first
            binding.kanaValueTextView.text = romajiPair.second.first

            binding.editButton.setOnClickListener { onEditClicked(romajiPair) }
            binding.deleteButton.setOnClickListener { onDeleteClicked(romajiPair) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<RomajiPair>() {
        override fun areItemsTheSame(oldItem: RomajiPair, newItem: RomajiPair): Boolean {
            return oldItem.first == newItem.first
        }
        override fun areContentsTheSame(oldItem: RomajiPair, newItem: RomajiPair): Boolean {
            return oldItem == newItem
        }
    }
}
