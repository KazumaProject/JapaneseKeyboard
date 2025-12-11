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

class ShortcutAdapter : ListAdapter<ShortcutType, ShortcutAdapter.ViewHolder>(DiffCallback) {

    /**
     * A listener that gets called when an item is clicked.
     * The listener receives the resource ID of the clicked item.
     */
    var onItemClicked: ((ShortcutType) -> Unit)? = null

    // ★追加: アイコンの色を保持する変数 (nullの場合はデフォルトの色)
    private var iconColor: Int? = null

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
            .inflate(R.layout.item_shortcut, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.imageView.setImageResource(item.iconResId) // Enumからアイコン取得

        // ★追加: 色が設定されていれば適用し、なければ解除する
        iconColor?.let { color ->
            holder.imageView.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        } ?: run {
            holder.imageView.clearColorFilter()
        }
    }

    // ★追加: 外部から色を設定するメソッド
    fun setIconColor(color: Int) {
        if (iconColor == color) return
        iconColor = color
        notifyItemRangeChanged(0, itemCount)
    }

    private object DiffCallback : DiffUtil.ItemCallback<ShortcutType>() {
        override fun areItemsTheSame(oldItem: ShortcutType, newItem: ShortcutType): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ShortcutType, newItem: ShortcutType): Boolean =
            oldItem == newItem
    }
}
