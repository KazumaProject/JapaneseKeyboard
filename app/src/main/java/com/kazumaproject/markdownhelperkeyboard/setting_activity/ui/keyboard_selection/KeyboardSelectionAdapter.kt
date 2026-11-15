package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_selection

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.ListItemKeyboardBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType

internal class KeyboardSelectionAdapter(
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onToggle: (KeyboardType, Boolean) -> Unit,
    private val onSettings: (KeyboardType) -> Unit,
) : ListAdapter<KeyboardType, KeyboardSelectionAdapter.KeyboardViewHolder>(DiffCallback()) {
    private var enabled = emptyList<KeyboardType>()
    private var editing = false
    private var selected: KeyboardType? = null
    private var selectionLabel = R.string.settings_keyboard_selected
    private var customCount = 0

    @SuppressLint("NotifyDataSetChanged")
    fun render(order: List<KeyboardType>, edit: Boolean, selectedType: KeyboardType?, label: Int, count: Int) {
        enabled = order; editing = edit; selected = selectedType; selectionLabel = label; customCount = count
        submitList(order + KeyboardType.entries.filter { it !in order }) { notifyDataSetChanged() }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = KeyboardViewHolder(
        ListItemKeyboardBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: KeyboardViewHolder, position: Int) = holder.bind(getItem(position), position)

    inner class KeyboardViewHolder(private val binding: ListItemKeyboardBinding) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("ClickableViewAccessibility")
        fun bind(type: KeyboardType, position: Int) {
            val context = binding.root.context
            val name = context.getKeyboardDisplayName(type)
            binding.keyboardName.text = name
            binding.keyboardAvailableHeading.isVisible = position == enabled.size
            binding.keyboardSelectedBadge.isVisible = selected == type && type in enabled
            binding.keyboardSelectedBadge.setText(selectionLabel)
            binding.keyboardSummary.isVisible = type == KeyboardType.CUSTOM
            binding.keyboardSummary.text = context.getString(R.string.settings_keyboard_custom_count, customCount)
            binding.keyboardSettings.setText(if (type == KeyboardType.CUSTOM) R.string.settings_manage_short else R.string.settings_settings_short)
            binding.keyboardSettings.contentDescription = name + ". " + binding.keyboardSettings.text
            binding.keyboardSettings.setOnClickListener { onSettings(type) }
            binding.keyboardEnabled.setOnCheckedChangeListener(null)
            binding.keyboardEnabled.isChecked = type in enabled
            binding.keyboardEnabled.isEnabled = !editing
            binding.keyboardEnabled.contentDescription = name + ". " + context.getString(R.string.settings_keyboard_use)
            binding.keyboardEnabled.setOnCheckedChangeListener { _, checked -> onToggle(type, checked) }
            binding.dragHandle.isVisible = editing && type in enabled
            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN && editing && type in enabled) onStartDrag(this)
                false
            }
        }
    }
    private class DiffCallback : DiffUtil.ItemCallback<KeyboardType>() {
        override fun areItemsTheSame(oldItem: KeyboardType, newItem: KeyboardType) = oldItem == newItem
        override fun areContentsTheSame(oldItem: KeyboardType, newItem: KeyboardType) = oldItem == newItem
    }
}
