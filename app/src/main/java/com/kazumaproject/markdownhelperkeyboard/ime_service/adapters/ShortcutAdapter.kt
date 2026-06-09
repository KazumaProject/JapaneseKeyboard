package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType

internal class ShortcutIconColorState {
    var iconColor: Int? = null
        private set

    fun setIconColor(color: Int): Boolean {
        if (iconColor == color) return false
        iconColor = color
        return true
    }
}

class ShortcutAdapter : ListAdapter<ShortcutType, ShortcutAdapter.ViewHolder>(DiffCallback) {

    /**
     * A listener that gets called when an item is clicked.
     * The listener receives the resource ID of the clicked item.
     */
    var onItemClicked: ((ShortcutType) -> Unit)? = null

    private val iconColorState = ShortcutIconColorState()
    private var activeShortcutTypes: Set<ShortcutType> = emptySet()

    /**
     * ViewHolder now captures clicks and calls the adapter's listener.
     * It's an 'inner class' to access the adapter's onItemClicked property.
     */
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.item_image)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClicked?.invoke(getItem(position))
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shortcut_toolbar, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.imageView.setImageResource(item.resolveIconResId()) // Enumからアイコン取得

        // ★追加: 色が設定されていれば適用し、なければ解除する
        iconColorState.iconColor?.let { color ->
            holder.imageView.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        } ?: run {
            holder.imageView.clearColorFilter()
        }
    }

    // ★追加: 外部から色を設定するメソッド
    fun setIconColor(color: Int) {
        if (!iconColorState.setIconColor(color)) return
        notifyItemRangeChanged(0, itemCount)
    }

    fun setActiveShortcutTypes(activeTypes: Set<ShortcutType>) {
        if (activeShortcutTypes == activeTypes) return
        val oldActive = activeShortcutTypes
        activeShortcutTypes = activeTypes

        (oldActive union activeTypes).forEach { type ->
            val index = currentList.indexOf(type)
            if (index >= 0) {
                notifyItemChanged(index)
            }
        }
    }

    fun setKeyboardLayoutEditActive(active: Boolean) {
        setActiveShortcutTypes(
            if (active) {
                activeShortcutTypes + ShortcutType.KEYBOARD_LAYOUT_EDIT
            } else {
                activeShortcutTypes - ShortcutType.KEYBOARD_LAYOUT_EDIT
            }
        )
    }

    private fun ShortcutType.resolveIconResId(): Int {
        return if (this in activeShortcutTypes) {
            activeIconResId ?: iconResId
        } else {
            iconResId
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ShortcutType>() {
        override fun areItemsTheSame(oldItem: ShortcutType, newItem: ShortcutType): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ShortcutType, newItem: ShortcutType): Boolean =
            oldItem == newItem
    }
}
