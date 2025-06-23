package com.kazumaproject.markdownhelperkeyboard.user_template.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.user_template.database.UserTemplate

class UserTemplateAdapter(
    private val onItemClicked: (UserTemplate) -> Unit
) : ListAdapter<UserTemplate, UserTemplateAdapter.UserTemplateViewHolder>(UserTemplateDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserTemplateViewHolder {
        // This now correctly points to the new layout file.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_template, parent, false)
        return UserTemplateViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserTemplateViewHolder, position: Int) {
        val current = getItem(position)
        holder.bind(current)
        holder.itemView.setOnClickListener {
            onItemClicked(current)
        }
    }

    class UserTemplateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // These IDs match the views in item_user_template.xml
        private val wordTextView: TextView = itemView.findViewById(R.id.text_view_word)
        private val readingTextView: TextView = itemView.findViewById(R.id.text_view_reading)

        fun bind(template: UserTemplate) {
            wordTextView.text = template.word
            readingTextView.text = template.reading
        }
    }
}

class UserTemplateDiffCallback : DiffUtil.ItemCallback<UserTemplate>() {
    override fun areItemsTheSame(oldItem: UserTemplate, newItem: UserTemplate): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: UserTemplate, newItem: UserTemplate): Boolean {
        return oldItem == newItem
    }
}
