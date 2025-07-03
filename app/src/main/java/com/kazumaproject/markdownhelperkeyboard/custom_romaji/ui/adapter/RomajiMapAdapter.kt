package com.kazumaproject.markdownhelperkeyboard.custom_romaji.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.custom_romaji.database.RomajiMapEntity
import com.kazumaproject.markdownhelperkeyboard.databinding.ListItemRomajiMapBinding

class RomajiMapAdapter(
    private val onCardClicked: (RomajiMapEntity) -> Unit,
    private val onActivateClicked: (RomajiMapEntity) -> Unit,
    private val onEditClicked: (RomajiMapEntity) -> Unit,
    private val onDeleteClicked: (RomajiMapEntity) -> Unit
) : ListAdapter<RomajiMapEntity, RomajiMapAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemRomajiMapBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ListItemRomajiMapBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(romajiMap: RomajiMapEntity) {
            // Bind data to views
            binding.mapNameTextView.text = romajiMap.name
            binding.defaultLabel.visibility = if (romajiMap.isDeletable) View.GONE else View.VISIBLE

            // Set visibility based on deletable status
            binding.editButton.visibility = if (romajiMap.isDeletable) View.VISIBLE else View.GONE
            binding.deleteButton.visibility = if (romajiMap.isDeletable) View.VISIBLE else View.GONE

            // Set appearance based on active status
            if (romajiMap.isActive) {
                binding.root.setCardBackgroundColor(
                    ContextCompat.getColor(binding.root.context, com.kazumaproject.core.R.color.keyboard_boarder_color)
                )
                binding.activeIndicator.visibility = View.VISIBLE
                binding.activateButton.visibility = View.GONE
            } else {
                binding.root.setCardBackgroundColor(
                    ContextCompat.getColor(binding.root.context, com.kazumaproject.core.R.color.keyboard_bg)
                )
                binding.activeIndicator.visibility = View.GONE
                binding.activateButton.visibility = View.VISIBLE
            }

            // Set all click listeners
            binding.root.setOnClickListener { onCardClicked(romajiMap) }
            binding.activateButton.setOnClickListener { onActivateClicked(romajiMap) }
            binding.editButton.setOnClickListener { onEditClicked(romajiMap) }
            binding.deleteButton.setOnClickListener { onDeleteClicked(romajiMap) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<RomajiMapEntity>() {
        override fun areItemsTheSame(oldItem: RomajiMapEntity, newItem: RomajiMapEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: RomajiMapEntity,
            newItem: RomajiMapEntity
        ): Boolean {
            return oldItem == newItem
        }
    }
}
