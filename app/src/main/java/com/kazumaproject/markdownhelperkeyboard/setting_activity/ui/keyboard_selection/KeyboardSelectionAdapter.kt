package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_selection

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.databinding.ListItemKeyboardBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType

class KeyboardSelectionAdapter(
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : ListAdapter<KeyboardType, KeyboardSelectionAdapter.KeyboardViewHolder>(DiffCallback()) {

    private var isEditing: Boolean = false

    @SuppressLint("NotifyDataSetChanged")
    fun setEditMode(isEditing: Boolean) {
        if (this.isEditing != isEditing) {
            this.isEditing = isEditing
            // This is a simple way to refresh all views to show/hide icons
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeyboardViewHolder {
        val binding =
            ListItemKeyboardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return KeyboardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: KeyboardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        // This logic is now handled in the Fragment/ViewModel
    }

    inner class KeyboardViewHolder(private val binding: ListItemKeyboardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.deleteIcon.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onDeleteClick(adapterPosition)
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        fun bind(keyboardType: KeyboardType) {
            binding.keyboardName.text = getKeyboardDisplayName(keyboardType)

            binding.dragHandle.visibility = if (isEditing) View.VISIBLE else View.GONE
            binding.deleteIcon.visibility = if (isEditing) View.VISIBLE else View.GONE

            if (isEditing) {
                binding.dragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        onStartDrag(this)
                    }
                    false
                }
            } else {
                binding.dragHandle.setOnTouchListener(null)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<KeyboardType>() {
        override fun areItemsTheSame(oldItem: KeyboardType, newItem: KeyboardType): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: KeyboardType, newItem: KeyboardType): Boolean {
            return oldItem == newItem
        }
    }

    private fun getKeyboardDisplayName(keyboardType: KeyboardType): String {
        return when (keyboardType) {
            KeyboardType.TENKEY -> "日本語 - かな"
            KeyboardType.QWERTY -> "英語(QWERTY)"
            KeyboardType.ROMAJI -> "日本語 - ローマ字"
            KeyboardType.SUMIRE -> "日本語 - スミレ入力"
        }
    }
}
