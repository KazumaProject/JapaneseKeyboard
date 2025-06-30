package com.kazumaproject.markdownhelperkeyboard.learning.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.markdownhelperkeyboard.R

class LearnDataOutputAdapter :
    RecyclerView.Adapter<LearnDataOutputAdapter.LearnDataOutputViewHolder>() {
    inner class LearnDataOutputViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvOutput: MaterialTextView = itemView.findViewById(R.id.learn_data_output_text)
    }

    private val diffCallback = object : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    var learnDataOutputList: List<String>
        get() = differ.currentList
        set(value) = differ.submitList(value)

    // 既存の長押しリスナー
    private var onItemLongClickListener: ((String) -> Unit)? = null

    fun setOnItemLongClickListener(listener: (String) -> Unit) {
        this.onItemLongClickListener = listener
    }

    // --- ここから追加 ---
    // クリックリスナーのプロパティとセッター
    private var onItemClickListener: ((String) -> Unit)? = null

    fun setOnItemClickListener(listener: (String) -> Unit) {
        this.onItemClickListener = listener
    }
    // --- ここまで追加 ---


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LearnDataOutputViewHolder {
        return LearnDataOutputViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.learn_data_output_item,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return learnDataOutputList.size
    }

    override fun onBindViewHolder(holder: LearnDataOutputViewHolder, position: Int) {
        // 現在のアイテムを変数に入れておくと分かりやすい
        val currentItem = learnDataOutputList[position]

        holder.tvOutput.apply {
            text = currentItem

            // --- ここから追加 ---
            // クリックリスナーを設定
            setOnClickListener {
                onItemClickListener?.invoke(currentItem)
            }
            // --- ここまで追加 ---

            // 既存の長押しリスナー
            setOnLongClickListener {
                onItemLongClickListener?.invoke(currentItem)
                true
            }
        }
    }
}
