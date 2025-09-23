package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R

class ShortcutAdapter : ListAdapter<Int, ShortcutAdapter.ViewHolder>(DiffCallback) {

    /**
     * A listener that gets called when an item is clicked.
     * The listener receives the resource ID of the clicked item.
     */
    var onItemClicked: ((Int) -> Unit)? = null

    /**
     * ViewHolder now captures clicks and calls the adapter's listener.
     * It's an 'inner class' to access the adapter's onItemClicked property.
     */
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.item_image)

        init {
            itemView.setOnClickListener {
                // Check for a valid position to avoid crashes on list changes
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val resourceId = getItem(position)
                    // Safely invoke the listener if it has been set
                    onItemClicked?.invoke(resourceId)
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
        val drawableResId = getItem(position)
        holder.imageView.setImageResource(drawableResId)
    }

    private object DiffCallback : DiffUtil.ItemCallback<Int>() {
        override fun areItemsTheSame(oldItem: Int, newItem: Int): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Int, newItem: Int): Boolean {
            return oldItem == newItem
        }
    }
}
