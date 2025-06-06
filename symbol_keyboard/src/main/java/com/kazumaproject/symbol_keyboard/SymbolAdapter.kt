package com.kazumaproject.symbol_keyboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.google.android.material.textview.MaterialTextView

class SymbolAdapter : PagingDataAdapter<String, SymbolAdapter.SymbolViewHolder>(DIFF_CALLBACK) {

    var symbolTextSize: Float = 16f

    inner class SymbolViewHolder(itemView: View) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val symbolTextView: MaterialTextView = itemView.findViewById(R.id.symbol_text)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                    val symbol = getItem(position)
                    symbol?.let { onItemClickListener?.invoke(it) }
                }
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
                // Assuming each symbol is unique
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
                return oldItem == newItem
            }
        }
    }

    private var onItemClickListener: ((String) -> Unit)? = null

    fun setOnItemClickListener(onItemClick: (String) -> Unit) {
        this.onItemClickListener = onItemClick
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SymbolViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.symbol_item_view, parent, false)
        return SymbolViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: SymbolViewHolder, position: Int) {
        val symbol = getItem(position)
        symbol?.let {
            holder.symbolTextView.text = it
            holder.symbolTextView.textSize = symbolTextSize
        }
    }
}
