package com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.markdownhelperkeyboard.databinding.ListItemFlickMappingBinding
import java.util.UUID

// RecyclerViewの各項目を表すデータクラス
data class FlickMappingItem(
    val id: String = UUID.randomUUID().toString(),
    var direction: FlickDirection,
    var output: String
)

class FlickMappingAdapter(
    private val onDeleteClick: (FlickMappingItem) -> Unit
) : ListAdapter<FlickMappingItem, FlickMappingAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ListItemFlickMappingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            // Spinnerのセットアップ
            val directionAdapter = ArrayAdapter(
                binding.root.context,
                android.R.layout.simple_spinner_item,
                FlickDirection.values().map { it.name }
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            binding.flickDirectionSpinner.adapter = directionAdapter

            // リスナーの設定
            binding.flickActionValueEdittext.doOnTextChanged { text, _, _, _ ->
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    getItem(bindingAdapterPosition).output = text.toString()
                }
            }

            binding.flickDeleteButton.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onDeleteClick(getItem(bindingAdapterPosition))
                }
            }
        }

        fun bind(item: FlickMappingItem) {
            binding.flickActionValueEdittext.setText(item.output)
            val selection = FlickDirection.values().indexOf(item.direction)
            binding.flickDirectionSpinner.setSelection(selection)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemFlickMappingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
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
