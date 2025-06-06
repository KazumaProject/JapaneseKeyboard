package com.kazumaproject.symbol_keyboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView

class SymbolAdapter :
    PagingDataAdapter<String, SymbolAdapter.SymbolViewHolder>(DIFF_CALLBACK) {

    // 外部から文字サイズを設定できるようにプロパティ化
    var symbolTextSize: Float = 16f

    inner class SymbolViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        val symbolTextView: MaterialTextView = itemView.findViewById(R.id.symbol_text)

        init {
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    getItem(pos)?.let { onItemClickListener?.invoke(it) }
                }
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
                // 文字列自体がユニークと仮定
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
        // getItem(pos) はロード前や空のとき null を返す
        val symbol = try {
            getItem(position)
        } catch (e: IndexOutOfBoundsException) {
            // 本来 PagingDataAdapter では発生しづらいはずですが、
            // 万が一アダプタ内部で不整合が起きた場合をキャッチして null にする
            null
        }
        if (symbol != null) {
            holder.symbolTextView.text = symbol
            holder.symbolTextView.textSize = symbolTextSize
        } else {
            // null のときは空状態にしておく（バインドしない）
            holder.symbolTextView.text = ""
        }
    }

}
