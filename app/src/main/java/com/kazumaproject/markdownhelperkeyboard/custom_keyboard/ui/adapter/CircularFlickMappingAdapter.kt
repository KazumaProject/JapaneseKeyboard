package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter

import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CircularFlickSlotActionType
import com.kazumaproject.markdownhelperkeyboard.databinding.ListItemCircularFlickMappingBinding
import java.util.UUID

data class CircularFlickMappingItem(
    val id: String = UUID.randomUUID().toString(),
    val direction: CircularFlickDirection,
    var actionType: CircularFlickSlotActionType,
    var output: String
)

class CircularFlickMappingAdapter(
    private val onItemUpdated: (CircularFlickMappingItem) -> Unit
) : ListAdapter<CircularFlickMappingItem, CircularFlickMappingAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            ListItemCircularFlickMappingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ListItemCircularFlickMappingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CircularFlickMappingItem) {
            val oldTextWatcher =
                binding.root.getTag(R.id.flick_action_value_edittext) as? TextWatcher
            binding.flickActionValueEdittext.removeTextChangedListener(oldTextWatcher)
            binding.circularActionTypeSpinner.onItemSelectedListener = null

            binding.flickDirectionLabel.text = item.direction.name
            setupActionTypeSpinner(item)

            val isInput = item.actionType == CircularFlickSlotActionType.INPUT
            binding.flickActionValueEdittext.isEnabled = isInput
            binding.flickActionValueInputLayout.isEnabled = isInput
            binding.flickActionValueInputLayout.isVisible = isInput
            binding.flickActionValueInputLayout.helperText =
                if (isInput) null else actionTypeDisplayName(item.actionType)
            binding.arrowText.isVisible = true

            if (binding.flickActionValueEdittext.text.toString() != item.output) {
                binding.flickActionValueEdittext.setText(item.output)
            }

            val newTextWatcher = binding.flickActionValueEdittext.doAfterTextChanged { editable ->
                if (bindingAdapterPosition == RecyclerView.NO_POSITION) return@doAfterTextChanged
                val currentItem = getItem(bindingAdapterPosition)
                if (currentItem.actionType != CircularFlickSlotActionType.INPUT) return@doAfterTextChanged
                val newValue = editable?.toString().orEmpty()
                if (currentItem.output != newValue) {
                    currentItem.output = newValue
                    onItemUpdated(currentItem)
                }
            }
            binding.root.setTag(
                R.id.flick_action_value_edittext,
                newTextWatcher
            )
        }

        private fun setupActionTypeSpinner(item: CircularFlickMappingItem) {
            val context = binding.root.context
            binding.circularActionTypeSpinner.adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_item,
                CircularFlickSlotActionType.values().map(::actionTypeDisplayName)
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            binding.circularActionTypeSpinner.setSelection(
                CircularFlickSlotActionType.values().indexOf(item.actionType).coerceAtLeast(0),
                false
            )
            binding.circularActionTypeSpinner.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: android.view.View?,
                        position: Int,
                        id: Long
                    ) {
                        if (bindingAdapterPosition == RecyclerView.NO_POSITION) return
                        val selected = CircularFlickSlotActionType.values().getOrNull(position)
                            ?: CircularFlickSlotActionType.NONE
                        val currentItem = getItem(bindingAdapterPosition)
                        if (currentItem.actionType != selected) {
                            currentItem.actionType = selected
                            onItemUpdated(currentItem)
                            notifyItemChanged(bindingAdapterPosition)
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CircularFlickMappingItem>() {
        fun actionTypeDisplayName(actionType: CircularFlickSlotActionType): String {
            return when (actionType) {
                CircularFlickSlotActionType.NONE -> "なし"
                CircularFlickSlotActionType.INPUT -> "文字入力"
                CircularFlickSlotActionType.SWITCH_MAP -> "map切り替え"
                CircularFlickSlotActionType.EMOJI_KEYBOARD -> "絵文字キーボード"
            }
        }

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
