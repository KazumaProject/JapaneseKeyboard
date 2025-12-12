package com.kazumaproject.symbol_keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.core.graphics.toColorInt
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

    // ★追加: テーマカラー (初期値はnullにしておき、設定がなければデフォルトを使用)
    @ColorInt
    private var themeTextColor: Int? = null

    @ColorInt
    private var themeHighlightColor: Int? = null

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

    /**
     * ★追加: テキストカラーとハイライトカラーを動的に設定するメソッド
     * @param textColor 通常時の文字色
     * @param highlightColor タップ時の波紋(Ripple)の色
     */
    fun setThemeColors(@ColorInt textColor: Int, @ColorInt highlightColor: Int) {
        this.themeTextColor = textColor
        this.themeHighlightColor = highlightColor
        // 既存の表示を更新するために再描画を通知
        notifyDataSetChanged()
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

            // ★追加: テキストカラーの適用
            themeTextColor?.let { color ->
                holder.symbolTextView.setTextColor(color)
            }

            // ★追加: ハイライト（Ripple）カラーの適用
            val rippleColor = themeHighlightColor ?: "#33808080".toColorInt()
            holder.itemView.background = createRippleDrawable(rippleColor)

            // Viewのレイアウトパラメータにマージンを適用する
            (holder.itemView.layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(
                horizontalMarginPx, verticalMarginPx, horizontalMarginPx, verticalMarginPx
            )
        } else {
            holder.symbolTextView.text = ""
        }
    }

    /**
     * ★追加: 指定された色で RippleDrawable を作成するヘルパー関数
     */
    private fun createRippleDrawable(@ColorInt rippleColor: Int): RippleDrawable {
        // Rippleの色状態リストを作成
        val colorStateList = ColorStateList.valueOf(rippleColor)

        // マスク（タップ領域の形状）を作成
        // 背景を透明にしつつ、Rippleの及ぶ範囲を定義します。
        // 必要に応じて角丸(cornerRadius)を設定してください。
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE) // 色は実際には表示されず、マスク領域として機能します
            cornerRadius = dpToPx(8f) // 例: 8dpの角丸
        }

        // contentをnullにすると通常時は透明背景、タップ時のみrippleColorが表示されます
        // もし通常時の背景色も変えたい場合は、nullの代わりにDrawableを渡します
        return RippleDrawable(colorStateList, null, mask)
    }

    private fun dpToPx(dp: Float): Float {
        // Contextにアクセスできない場合は概算値か、0fを設定するか、
        // または onBindViewHolder 内で Context経由で計算して渡す設計にします。
        // ここでは簡易的に Resources.getSystem() を使用しますが、
        // 本来は View の Context を使うのがベストです。
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            android.content.res.Resources.getSystem().displayMetrics
        )
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
