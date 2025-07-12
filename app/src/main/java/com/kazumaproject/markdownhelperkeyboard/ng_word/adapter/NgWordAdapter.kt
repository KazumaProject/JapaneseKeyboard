package com.kazumaproject.markdownhelperkeyboard.ng_word.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWord

class NgWordAdapter(
    private val onClick: (NgWord) -> Unit
) : ListAdapter<NgWord, NgWordAdapter.VH>(object : DiffUtil.ItemCallback<NgWord>() {
    override fun areItemsTheSame(a: NgWord, b: NgWord) = a.id == b.id
    override fun areContentsTheSame(a: NgWord, b: NgWord) = a == b
}) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ng_word, parent, false)
        return VH(v, onClick)
    }

    override fun onBindViewHolder(holder: VH, pos: Int) {
        holder.bind(getItem(pos))
    }

    class VH(itemView: View, val onClick: (NgWord) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val tvYomi: TextView = itemView.findViewById(R.id.text_view_yomi)
        private val tvTango: TextView = itemView.findViewById(R.id.text_view_tango)
        fun bind(item: NgWord) {
            tvYomi.text = item.yomi
            tvTango.text = item.tango
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
