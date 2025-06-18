package com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord

class UserWordAdapter(
    private val onItemClicked: (UserWord) -> Unit
) : ListAdapter<UserWord, UserWordAdapter.UserWordViewHolder>(UserWordDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserWordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_word, parent, false)
        return UserWordViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserWordViewHolder, position: Int) {
        val current = getItem(position)
        holder.bind(current)
        holder.itemView.setOnClickListener {
            onItemClicked(current)
        }
    }

    class UserWordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val wordTextView: TextView = itemView.findViewById(R.id.text_view_word)
        private val readingTextView: TextView = itemView.findViewById(R.id.text_view_reading)

        fun bind(userWord: UserWord) {
            wordTextView.text = userWord.word
            readingTextView.text = userWord.reading
        }
    }
}

class UserWordDiffCallback : DiffUtil.ItemCallback<UserWord>() {
    override fun areItemsTheSame(oldItem: UserWord, newItem: UserWord): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: UserWord, newItem: UserWord): Boolean {
        return oldItem == newItem
    }
}
