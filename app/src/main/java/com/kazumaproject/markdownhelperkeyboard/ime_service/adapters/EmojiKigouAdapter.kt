package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.Emoji
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertUnicode

class EmojiKigouAdapter : RecyclerView.Adapter<EmojiKigouAdapter.EmojiKigouViewHolder>(){

    inner class EmojiKigouViewHolder(itemView: View): RecyclerView.ViewHolder(itemView)

    private val diffCallback = object : DiffUtil.ItemCallback<Emoji>() {
        override fun areItemsTheSame(oldItem: Emoji, newItem: Emoji): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Emoji, newItem: Emoji): Boolean {
            return oldItem.hashCode() == newItem.hashCode()
        }
    }

    private var onItemClickListener: ((Emoji) -> Unit)? = null

    fun setOnItemClickListener(onItemClick: (Emoji) -> Unit) {
        this.onItemClickListener = onItemClick
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    var emojiList: List<Emoji>
        get() = differ.currentList
        set(value) = differ.submitList(value)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiKigouViewHolder {
        return EmojiKigouViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.emoji_kigou_item_layout,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return emojiList.size
    }

    override fun onBindViewHolder(holder: EmojiKigouViewHolder, position: Int) {
        val emoji = emojiList[position]
        holder.itemView.apply {
            val text = findViewById<MaterialTextView>(R.id.emoji_kigou_textview)
            text.text = emoji.unicode.convertUnicode()

            setOnClickListener {
                onItemClickListener?.let { click ->
                    click(emoji)
                }
            }
        }
    }
}