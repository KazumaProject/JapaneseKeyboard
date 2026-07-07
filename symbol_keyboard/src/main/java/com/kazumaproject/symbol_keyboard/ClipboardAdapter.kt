package com.kazumaproject.symbol_keyboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.core.data.clipboard.ClipboardItem
import com.kazumaproject.listeners.ClipboardItemAction

class ClipboardAdapter :
    PagingDataAdapter<ClipboardListItem, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    private var onItemClickListener: ((ClipboardItem) -> Unit)? = null
    private var onItemActionListener: ((ClipboardItem, ClipboardItemAction) -> Unit)? = null

    fun setOnItemClickListener(listener: (ClipboardItem) -> Unit) {
        this.onItemClickListener = listener
    }

    fun setOnItemActionListener(listener: (ClipboardItem, ClipboardItemAction) -> Unit) {
        this.onItemActionListener = listener
    }

    fun isHeader(position: Int): Boolean {
        return peek(position) is ClipboardListItem.Header
    }

    inner class ClipboardHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: MaterialTextView = itemView.findViewById(R.id.clipboard_header_text)

        fun bind(item: ClipboardListItem.Header) {
            titleView.text = item.title
        }
    }

    inner class ClipboardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.clipboard_image_view)
        private val textCardView: MaterialCardView =
            itemView.findViewById(R.id.clipboard_text_card_view)
        private val textView: MaterialTextView = itemView.findViewById(R.id.clipboard_text_view)

        init {
            itemView.setOnClickListener {
                val item = getContentItem() ?: return@setOnClickListener
                onItemClickListener?.invoke(item)
            }
            itemView.setOnLongClickListener {
                val item = getContentItem() ?: return@setOnLongClickListener true
                showClipboardActionMenu(itemView, item)
                true
            }
        }

        fun bind(item: ClipboardItem) {
            when (item) {
                is ClipboardItem.Image -> {
                    imageView.visibility = View.VISIBLE
                    textCardView.visibility = View.GONE
                    imageView.setImageBitmap(item.bitmap)
                    imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                }

                is ClipboardItem.Text -> {
                    imageView.visibility = View.GONE
                    textCardView.visibility = View.VISIBLE
                    textView.text = item.text
                }

                is ClipboardItem.Empty -> {
                    imageView.visibility = View.GONE
                    textCardView.visibility = View.GONE
                }
            }
        }

        private fun getContentItem(): ClipboardItem? {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return null
            return (getItem(position) as? ClipboardListItem.Content)?.item
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (peek(position)) {
            is ClipboardListItem.Header -> VIEW_TYPE_HEADER
            else -> VIEW_TYPE_CONTENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.clipboard_section_header, parent, false)
                ClipboardHeaderViewHolder(view)
            }

            else -> {
                val view = inflater.inflate(R.layout.clipboard_item_view, parent, false)
                ClipboardViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ClipboardListItem.Header -> (holder as ClipboardHeaderViewHolder).bind(item)
            is ClipboardListItem.Content -> (holder as ClipboardViewHolder).bind(item.item)
            null -> Unit
        }
    }

    private fun showClipboardActionMenu(anchor: View, item: ClipboardItem) {
        PopupMenu(anchor.context, anchor).apply {
            menu.add(0, ClipboardItemAction.PASTE.ordinal, 0, R.string.symbol_clipboard_action_paste)
            menu.add(
                0,
                if (item.isPinned()) ClipboardItemAction.UNPIN.ordinal else ClipboardItemAction.PIN.ordinal,
                1,
                if (item.isPinned()) {
                    R.string.symbol_clipboard_action_unpin
                } else {
                    R.string.symbol_clipboard_action_pin
                }
            )
            menu.add(0, ClipboardItemAction.DELETE.ordinal, 2, R.string.symbol_clipboard_action_delete)
            setOnMenuItemClickListener { menuItem ->
                val action = ClipboardItemAction.values()[menuItem.itemId]
                onItemActionListener?.invoke(item, action)
                true
            }
            show()
        }
    }

    private fun ClipboardItem.isPinned(): Boolean {
        return when (this) {
            is ClipboardItem.Image -> isPinned
            is ClipboardItem.Text -> isPinned
            ClipboardItem.Empty -> false
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CONTENT = 1

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ClipboardListItem>() {
            override fun areItemsTheSame(
                oldItem: ClipboardListItem,
                newItem: ClipboardListItem
            ): Boolean {
                return when {
                    oldItem is ClipboardListItem.Header && newItem is ClipboardListItem.Header ->
                        oldItem.title == newItem.title

                    oldItem is ClipboardListItem.Content && newItem is ClipboardListItem.Content ->
                        oldItem.item.itemId() == newItem.item.itemId()

                    else -> false
                }
            }

            override fun areContentsTheSame(
                oldItem: ClipboardListItem,
                newItem: ClipboardListItem
            ): Boolean {
                return oldItem == newItem
            }

            private fun ClipboardItem.itemId(): Long {
                return when (this) {
                    is ClipboardItem.Image -> id
                    is ClipboardItem.Text -> id
                    ClipboardItem.Empty -> Long.MIN_VALUE
                }
            }
        }
    }
}
