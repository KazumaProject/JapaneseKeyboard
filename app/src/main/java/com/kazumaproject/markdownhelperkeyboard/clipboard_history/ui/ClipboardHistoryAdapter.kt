package com.kazumaproject.markdownhelperkeyboard.clipboard_history.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardHistoryItem
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ItemType
import com.kazumaproject.markdownhelperkeyboard.databinding.ItemClipboardImageBinding
import com.kazumaproject.markdownhelperkeyboard.databinding.ItemClipboardTextBinding
import java.text.SimpleDateFormat
import java.util.*

class ClipboardHistoryAdapter(
    private val onItemClicked: (ClipboardHistoryItem) -> Unit
) : ListAdapter<ClipboardHistoryItem, RecyclerView.ViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    companion object {
        private const val VIEW_TYPE_TEXT = 1
        private const val VIEW_TYPE_IMAGE = 2

        private val DiffCallback = object : DiffUtil.ItemCallback<ClipboardHistoryItem>() {
            override fun areItemsTheSame(
                oldItem: ClipboardHistoryItem,
                newItem: ClipboardHistoryItem
            ): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: ClipboardHistoryItem,
                newItem: ClipboardHistoryItem
            ): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position).itemType) {
            ItemType.TEXT -> VIEW_TYPE_TEXT
            ItemType.IMAGE -> VIEW_TYPE_IMAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_TEXT -> TextViewHolder(
                ItemClipboardTextBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )

            VIEW_TYPE_IMAGE -> ImageViewHolder(
                ItemClipboardImageBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        holder.itemView.setOnClickListener { onItemClicked(item) }

        when (holder) {
            is TextViewHolder -> holder.bind(item)
            is ImageViewHolder -> holder.bind(item)
        }
    }

    inner class TextViewHolder(private val binding: ItemClipboardTextBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ClipboardHistoryItem) {
            binding.textViewContent.text = item.textData
            binding.textViewTimestamp.text = dateFormat.format(Date(item.timestamp))
        }
    }

    inner class ImageViewHolder(private val binding: ItemClipboardImageBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ClipboardHistoryItem) {
            binding.imageViewContent.setImageBitmap(item.imageData)
            binding.textViewTimestamp.text = dateFormat.format(Date(item.timestamp))
        }
    }
}
