package com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.databinding.ListItemKeyboardLayoutBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KeyboardLayoutAdapter(
    private val onItemClick: (CustomKeyboardLayout) -> Unit,
    private val onDeleteClick: (CustomKeyboardLayout) -> Unit,
    private val onDuplicateClick: (CustomKeyboardLayout) -> Unit,
) : ListAdapter<CustomKeyboardLayout, KeyboardLayoutAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ListItemKeyboardLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(layout: CustomKeyboardLayout) {
            binding.keyboardNameText.text = layout.name
            val context = binding.root.context
            binding.keyboardDateText.text = context.getString(
                com.kazumaproject.core.R.string.created_at_date,
                formatTimestamp(layout.createdAt)
            )

            // リスト項目全体がクリックされた場合
            binding.root.setOnClickListener { onItemClick(layout) }

            // メニューボタンがクリックされた場合
            binding.keyboardMenuButton.setOnClickListener { view ->
                PopupMenu(view.context, view).apply {
                    menuInflater.inflate(R.menu.menu_list_item, menu)
                    setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.action_delete_layout -> {
                                onDeleteClick(layout)
                                true
                            }

                            R.id.action_duplicate_layout -> {
                                onDuplicateClick(layout)
                                true
                            }

                            else -> false
                        }
                    }
                    show()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemKeyboardLayoutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CustomKeyboardLayout>() {
        override fun areItemsTheSame(
            oldItem: CustomKeyboardLayout,
            newItem: CustomKeyboardLayout
        ): Boolean {
            return oldItem.layoutId == newItem.layoutId
        }

        override fun areContentsTheSame(
            oldItem: CustomKeyboardLayout,
            newItem: CustomKeyboardLayout
        ): Boolean {
            return oldItem == newItem
        }
    }
}
