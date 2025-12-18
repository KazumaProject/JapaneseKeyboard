package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepMappingItem
import com.kazumaproject.markdownhelperkeyboard.databinding.ListItemTwoStepMappingBinding

class TwoStepMappingAdapter(
    private val onItemUpdated: (TwoStepMappingItem) -> Unit
) : ListAdapter<TwoStepMappingItem, TwoStepMappingAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TwoStepMappingItem>() {
            override fun areItemsTheSame(oldItem: TwoStepMappingItem, newItem: TwoStepMappingItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: TwoStepMappingItem, newItem: TwoStepMappingItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    class VH(val binding: ListItemTwoStepMappingBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ListItemTwoStepMappingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)

        holder.binding.textDirections.text = "${item.first.name} → ${item.second.name}"
        holder.binding.editOutput.setText(item.output)

        holder.binding.editOutput.doAfterTextChanged { editable ->
            val newText = editable?.toString() ?: ""
            if (newText == item.output) return@doAfterTextChanged
            onItemUpdated(item.copy(output = newText))
        }
    }
}
