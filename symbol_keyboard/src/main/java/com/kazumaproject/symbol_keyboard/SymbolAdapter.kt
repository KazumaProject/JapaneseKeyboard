package com.kazumaproject.symbol_keyboard

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView

class SymbolAdapter :
    PagingDataAdapter<String, SymbolAdapter.SymbolViewHolder>(DIFF_CALLBACK) {

    /** シンボル文字列をクリックしたとき */
    private var onItemClickListener: ((String) -> Unit)? = null

    /** シンボル文字列を長押ししたとき */
    private var onItemLongClickListener: ((String, Int) -> Unit)? = null

    // 外部から設定するためのメソッド
    fun setOnItemClickListener(onItemClick: (String) -> Unit) {
        this.onItemClickListener = onItemClick
    }

    // 外部から設定するためのメソッド（長押し用）
    fun setOnItemLongClickListener(onItemLongClick: (String, Int) -> Unit) {
        this.onItemLongClickListener = onItemLongClick
    }

    // 外部から文字サイズを設定できるようにプロパティ化
    var symbolTextSize: Float = 16f

    // マージン値をピクセル単位で保持するプロパティ
    private var horizontalMarginPx: Int = 0
    private var verticalMarginPx: Int = 0

    /**
     * 外部からマージン値をDP単位で設定するためのメソッド
     * @param horizontalDp 水平マージン (DP)
     * @param verticalDp 垂直マージン (DP)
     * @param context Contextオブジェクト
     */
    fun setItemMargins(horizontalDp: Int, verticalDp: Int, context: Context) {
        val metrics = context.resources.displayMetrics
        horizontalMarginPx =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, horizontalDp.toFloat(), metrics)
                .toInt()
        verticalMarginPx =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, verticalDp.toFloat(), metrics)
                .toInt()
    }

    inner class SymbolViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        val symbolTextView: MaterialTextView = itemView.findViewById(R.id.symbol_text)

        init {
            // 通常クリック
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    getItem(pos)?.let { onItemClickListener?.invoke(it) }
                }
            }
            // 長押しクリック
            itemView.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    getItem(pos)?.let { onItemLongClickListener?.invoke(it, pos) }
                }
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SymbolViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.symbol_item_view, parent, false)
        return SymbolViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: SymbolViewHolder, position: Int) {
        val symbol = try {
            getItem(position)
        } catch (e: IndexOutOfBoundsException) {
            null
        }
        if (symbol != null) {
            holder.symbolTextView.text = symbol
            holder.symbolTextView.textSize = symbolTextSize

            // Viewのレイアウトパラメータにマージンを適用する
            (holder.itemView.layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(
                horizontalMarginPx, verticalMarginPx, horizontalMarginPx, verticalMarginPx
            )
        } else {
            holder.symbolTextView.text = ""
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
                return oldItem == newItem
            }
        }
    }
}
