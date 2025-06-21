package com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.markdownhelperkeyboard.databinding.ListItemFlickMappingBinding
import java.util.UUID

data class FlickMappingItem(
    val id: String = UUID.randomUUID().toString(),
    var direction: FlickDirection,
    var output: String
)

class FlickMappingAdapter(
    private val onItemUpdated: (FlickMappingItem) -> Unit,
    private val onItemDeleted: (FlickMappingItem) -> Unit
) : ListAdapter<FlickMappingItem, FlickMappingAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ListItemFlickMappingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val textWatcher =
            binding.flickActionValueEdittext.doOnTextChanged { text, _, _, _ ->
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    val currentItem = getItem(bindingAdapterPosition)
                    if (currentItem.output != text.toString()) {
                        onItemUpdated(currentItem.copy(output = text.toString()))
                    }
                }
            }

        private val spinnerListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    val selectedDirection = FlickDirection.values()[position]
                    val currentItem = getItem(bindingAdapterPosition)
                    if (currentItem.direction != selectedDirection) {
                        onItemUpdated(currentItem.copy(direction = selectedDirection))
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        init {
            val directionAdapter = ArrayAdapter(
                binding.root.context,
                android.R.layout.simple_spinner_item,
                FlickDirection.values().map { it.name }
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            binding.flickDirectionSpinner.adapter = directionAdapter

            binding.flickDeleteButton.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onItemDeleted(getItem(bindingAdapterPosition))
                }
            }
        }

        fun bind(item: FlickMappingItem) {
            binding.flickActionValueEdittext.removeTextChangedListener(textWatcher)
            binding.flickDirectionSpinner.onItemSelectedListener = null

            if (binding.flickActionValueEdittext.text.toString() != item.output) {
                binding.flickActionValueEdittext.setText(item.output)
            }
            val selection = FlickDirection.values().indexOf(item.direction)
            if (binding.flickDirectionSpinner.selectedItemPosition != selection) {
                binding.flickDirectionSpinner.setSelection(selection)
            }

            binding.flickActionValueEdittext.addTextChangedListener(textWatcher)
            binding.flickDirectionSpinner.onItemSelectedListener = spinnerListener
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
