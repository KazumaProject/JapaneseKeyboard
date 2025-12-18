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
            override fun areItemsTheSame(
                oldItem: TwoStepMappingItem,
                newItem: TwoStepMappingItem
            ): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: TwoStepMappingItem,
                newItem: TwoStepMappingItem
            ): Boolean {
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

        val displayText = when {
            item.first.name == "TAP" && item.second.name == "TAP" -> {
                "タップ"
            }

            item.first.name == "UP_LEFT" && item.second.name == "UP_LEFT" -> {
                "左上フリック"
            }

            item.first.name == "DOWN_LEFT" && item.second.name == "DOWN_LEFT" -> {
                "左下フリック"
            }

            item.first.name == "UP_RIGHT" && item.second.name == "UP_RIGHT" -> {
                "右上フリック"
            }

            item.first.name == "DOWN_RIGHT" && item.second.name == "DOWN_RIGHT" -> {
                "右下フリック"
            }

            item.first.name == "LEFT" && item.second.name == "LEFT" -> {
                "左フリック"
            }

            item.first.name == "LEFT" && item.second.name == "DOWN_LEFT" -> {
                "左 + 下フリック"
            }

            item.first.name == "LEFT" && item.second.name == "UP_LEFT" -> {
                "左 + 上フリック"
            }

            item.first.name == "RIGHT" && item.second.name == "RIGHT" -> {
                "右フリック"
            }

            item.first.name == "RIGHT" && item.second.name == "DOWN_RIGHT" -> {
                "右 + 下フリック"
            }

            item.first.name == "RIGHT" && item.second.name == "UP_RIGHT" -> {
                "右 + 上フリック"
            }

            item.first.name == "UP" && item.second.name == "UP" -> {
                "上フリック"
            }

            item.first.name == "UP" && item.second.name == "UP_LEFT" -> {
                "上 + 右フリック"
            }

            item.first.name == "UP" && item.second.name == "UP_RIGHT" -> {
                "上 + 左フリック"
            }

            item.first.name == "DOWN" && item.second.name == "DOWN" -> {
                "下フリック"
            }

            item.first.name == "DOWN" && item.second.name == "DOWN_LEFT" -> {
                "下 + 右フリック"
            }

            item.first.name == "DOWN" && item.second.name == "DOWN_RIGHT" -> {
                "下 + 左フリック"
            }

            else -> {
                "タップ"
            }
        }

        holder.binding.textDirections.text = displayText
        holder.binding.editOutput.setText(item.output)

        holder.binding.editOutput.doAfterTextChanged { editable ->
            val newText = editable?.toString() ?: ""
            if (newText == item.output) return@doAfterTextChanged
            onItemUpdated(item.copy(output = newText))
        }
    }
}
