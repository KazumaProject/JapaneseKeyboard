package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.text.TextWatcher
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.markdownhelperkeyboard.databinding.ListItemFlickMappingBinding
import java.util.UUID

data class CircularFlickMappingItem(
    val id: String = UUID.randomUUID().toString(),
    val direction: CircularFlickDirection,
    var output: String,
    val isMapSwitch: Boolean = false
)

class CircularFlickMappingAdapter(
    private val onItemUpdated: (CircularFlickMappingItem) -> Unit
) : ListAdapter<CircularFlickMappingItem, CircularFlickMappingAdapter.ViewHolder>(DiffCallback) {

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

        fun bind(item: CircularFlickMappingItem) {
            val oldTextWatcher =
                binding.root.getTag(com.kazumaproject.markdownhelperkeyboard.R.id.flick_action_value_edittext) as? TextWatcher
            binding.flickActionValueEdittext.removeTextChangedListener(oldTextWatcher)

            binding.flickDirectionLabel.text = if (item.isMapSwitch) {
                "${item.direction.name}\nmap"
            } else {
                item.direction.name
            }
            binding.flickActionValueEdittext.isEnabled = !item.isMapSwitch
            binding.flickActionValueInputLayout.isEnabled = !item.isMapSwitch
            binding.flickActionValueInputLayout.helperText =
                if (item.isMapSwitch) "map切り替え用" else null
            binding.arrowText.isVisible = true

            if (binding.flickActionValueEdittext.text.toString() != item.output) {
                binding.flickActionValueEdittext.setText(item.output)
            }

            val newTextWatcher = binding.flickActionValueEdittext.doAfterTextChanged { editable ->
                if (bindingAdapterPosition == RecyclerView.NO_POSITION || item.isMapSwitch) return@doAfterTextChanged
                val currentItem = getItem(bindingAdapterPosition)
                val newValue = editable?.toString().orEmpty()
                if (currentItem.output != newValue) {
                    currentItem.output = newValue
                    onItemUpdated(currentItem)
                }
            }
            binding.root.setTag(
                com.kazumaproject.markdownhelperkeyboard.R.id.flick_action_value_edittext,
                newTextWatcher
            )
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CircularFlickMappingItem>() {
        override fun areItemsTheSame(
            oldItem: CircularFlickMappingItem,
            newItem: CircularFlickMappingItem
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: CircularFlickMappingItem,
            newItem: CircularFlickMappingItem
        ): Boolean = oldItem == newItem
    }
}
