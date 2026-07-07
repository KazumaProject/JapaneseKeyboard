package com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.database.SystemUserDictionaryEntry

class SystemUserDictionaryEntryAdapter(
    private val onClick: (SystemUserDictionaryEntry) -> Unit,
) : ListAdapter<SystemUserDictionaryEntry, SystemUserDictionaryEntryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_system_user_dictionary_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val primary: TextView = itemView.findViewById(R.id.text_view_primary)
        private val secondary: TextView = itemView.findViewById(R.id.text_view_secondary)

        fun bind(item: SystemUserDictionaryEntry) {
            primary.text = "${item.yomi} -> ${item.tango}"
            secondary.text = "score=${item.score}  leftId=${item.leftId}  rightId=${item.rightId}"
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<SystemUserDictionaryEntry>() {
        override fun areItemsTheSame(
            oldItem: SystemUserDictionaryEntry,
            newItem: SystemUserDictionaryEntry,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: SystemUserDictionaryEntry,
            newItem: SystemUserDictionaryEntry,
        ): Boolean = oldItem == newItem
    }
}
