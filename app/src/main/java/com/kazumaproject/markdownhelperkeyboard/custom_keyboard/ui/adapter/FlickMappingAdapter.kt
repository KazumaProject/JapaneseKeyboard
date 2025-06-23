package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter

import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickDirectionMapper
import com.kazumaproject.markdownhelperkeyboard.databinding.ListItemFlickMappingBinding
import java.util.UUID

data class FlickMappingItem(
    val id: String = UUID.randomUUID().toString(),
    var direction: FlickDirection,
    var output: String
)

class FlickMappingAdapter(
    private val onItemUpdated: (FlickMappingItem) -> Unit,
    // onItemDeleted は不要になったので削除
) : ListAdapter<FlickMappingItem, FlickMappingAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            ListItemFlickMappingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ListItemFlickMappingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FlickMappingItem) {
            // --- 1. 以前のリスナーを削除 ---
            val oldTextWatcher =
                binding.root.getTag(R.id.flick_action_value_edittext) as? TextWatcher
            binding.flickActionValueEdittext.removeTextChangedListener(oldTextWatcher)

            // --- 2. データをUIに反映 ---
            // TextViewに日本語の方向名を設定
            binding.flickDirectionLabel.text = FlickDirectionMapper.toDisplayName(item.direction)

            // 出力文字EditTextの設定
            if (binding.flickActionValueEdittext.text.toString() != item.output) {
                binding.flickActionValueEdittext.setText(item.output)
            }

            // --- 3. 新しいリスナーを設定 ---
            val newTextWatcher = binding.flickActionValueEdittext.doAfterTextChanged { editable ->
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    val currentItem = getItem(bindingAdapterPosition)
                    if (currentItem.output != editable.toString()) {
                        currentItem.output = editable.toString()
                        onItemUpdated(currentItem)
                    }
                }
            }
            binding.flickActionValueEdittext.addTextChangedListener(newTextWatcher)
            binding.root.setTag(R.id.flick_action_value_edittext, newTextWatcher)
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<FlickMappingItem>() {
        override fun areItemsTheSame(
            oldItem: FlickMappingItem,
            newItem: FlickMappingItem
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: FlickMappingItem,
            newItem: FlickMappingItem
        ): Boolean {
            return oldItem == newItem
        }
    }
}
