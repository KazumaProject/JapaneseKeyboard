package com.kazumaproject.symbol_keyboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.core.data.clipboard.ClipboardItem

class ClipboardAdapter :
    PagingDataAdapter<ClipboardItem, ClipboardAdapter.ClipboardViewHolder>(DIFF_CALLBACK) {

    private var onItemClickListener: ((ClipboardItem) -> Unit)? = null

    fun setOnItemClickListener(listener: (ClipboardItem) -> Unit) {
        this.onItemClickListener = listener
    }

    inner class ClipboardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.clipboard_image_view)
        val textView: MaterialTextView = itemView.findViewById(R.id.clipboard_text_view)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    getItem(position)?.let { item ->
                        onItemClickListener?.invoke(item)
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipboardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.clipboard_item_view, parent, false)
        return ClipboardViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClipboardViewHolder, position: Int) {
        val item = getItem(position)
        when (item) {
            is ClipboardItem.Image -> {
                holder.imageView.visibility = View.VISIBLE
                holder.textView.visibility = View.GONE
                holder.imageView.setImageBitmap(item.bitmap)
            }
            is ClipboardItem.Text -> {
                holder.imageView.visibility = View.GONE
                holder.textView.visibility = View.VISIBLE
                holder.textView.text = item.text
            }
            is ClipboardItem.Empty, null -> {
                holder.imageView.visibility = View.GONE
                holder.textView.visibility = View.GONE
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ClipboardItem>() {
            override fun areItemsTheSame(oldItem: ClipboardItem, newItem: ClipboardItem): Boolean {
                return if (oldItem is ClipboardItem.Text && newItem is ClipboardItem.Text) {
                    oldItem.text == newItem.text
                } else if (oldItem is ClipboardItem.Image && newItem is ClipboardItem.Image) {
                    oldItem.bitmap == newItem.bitmap
                } else {
                    false
                }
            }

            override fun areContentsTheSame(
                oldItem: ClipboardItem,
                newItem: ClipboardItem
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
