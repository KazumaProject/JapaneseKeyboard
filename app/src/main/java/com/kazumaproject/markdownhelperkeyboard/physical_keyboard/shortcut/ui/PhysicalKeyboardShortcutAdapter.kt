package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.PhysicalKeyboardShortcutAction
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.PhysicalKeyboardShortcutContext
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.PhysicalShortcutFormatter
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.database.PhysicalKeyboardShortcutItem

class PhysicalKeyboardShortcutAdapter(
    private val onClick: (PhysicalKeyboardShortcutItem) -> Unit,
    private val onEnabledChange: (PhysicalKeyboardShortcutItem, Boolean) -> Unit
) : ListAdapter<PhysicalKeyboardShortcutItem, PhysicalKeyboardShortcutAdapter.ViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 20, 32, 20)
        }
        val text = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val enabled = Switch(context)
        root.addView(text)
        root.addView(enabled)
        return ViewHolder(root, text, enabled)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val root: LinearLayout,
        private val text: TextView,
        private val enabled: Switch
    ) : RecyclerView.ViewHolder(root) {
        fun bind(item: PhysicalKeyboardShortcutItem) {
            val contextLabel = root.context.getString(
                PhysicalKeyboardShortcutContext.fromId(item.context).labelResId
            )
            val actionLabel = PhysicalKeyboardShortcutAction.fromId(item.actionId)
                ?.let { root.context.getString(it.labelResId) }
                ?: item.actionId
            text.text = "$contextLabel / ${PhysicalShortcutFormatter.format(root.context, item)}\n$actionLabel"
            enabled.setOnCheckedChangeListener(null)
            enabled.isChecked = item.enabled
            enabled.setOnCheckedChangeListener { _, checked -> onEnabledChange(item, checked) }
            root.setOnClickListener { onClick(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<PhysicalKeyboardShortcutItem>() {
        override fun areItemsTheSame(
            oldItem: PhysicalKeyboardShortcutItem,
            newItem: PhysicalKeyboardShortcutItem
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: PhysicalKeyboardShortcutItem,
            newItem: PhysicalKeyboardShortcutItem
        ): Boolean = oldItem == newItem
    }
}
