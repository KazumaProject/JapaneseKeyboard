package com.kazumaproject.markdownhelperkeyboard.delete_key_flick.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.databinding.ItemDeleteKeyFlickTargetBinding
import com.kazumaproject.markdownhelperkeyboard.delete_key_flick.database.DeleteKeyFlickDeleteTarget

class DeleteKeyFlickTargetsAdapter(
    private val onItemClick: (DeleteKeyFlickDeleteTarget) -> Unit,
    private val onDeleteClick: (DeleteKeyFlickDeleteTarget) -> Unit
) : ListAdapter<DeleteKeyFlickDeleteTarget, DeleteKeyFlickTargetsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDeleteKeyFlickTargetBinding.inflate(
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
        private val binding: ItemDeleteKeyFlickTargetBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(target: DeleteKeyFlickDeleteTarget) {
            binding.symbolText.text = target.symbol
            binding.root.setOnClickListener { onItemClick(target) }
            binding.deleteIcon.setOnClickListener { onDeleteClick(target) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<DeleteKeyFlickDeleteTarget>() {
        override fun areItemsTheSame(
            oldItem: DeleteKeyFlickDeleteTarget,
            newItem: DeleteKeyFlickDeleteTarget
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: DeleteKeyFlickDeleteTarget,
            newItem: DeleteKeyFlickDeleteTarget
        ): Boolean = oldItem == newItem
    }
}
