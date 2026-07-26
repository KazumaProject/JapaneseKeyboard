package com.kazumaproject.markdownhelperkeyboard.learning.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity

class LearnDataOutputAdapter :
    RecyclerView.Adapter<LearnDataOutputAdapter.LearnDataOutputViewHolder>() {
    inner class LearnDataOutputViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvOutput: MaterialTextView = itemView.findViewById(R.id.learn_data_output_text)
        val tvMetadata: MaterialTextView = itemView.findViewById(R.id.learn_data_output_metadata)
    }

    private val diffCallback = object : DiffUtil.ItemCallback<LearnEntity>() {
        override fun areItemsTheSame(oldItem: LearnEntity, newItem: LearnEntity): Boolean {
            return oldItem.id == newItem.id && oldItem.input == newItem.input && oldItem.out == newItem.out
        }

        override fun areContentsTheSame(oldItem: LearnEntity, newItem: LearnEntity): Boolean {
            return oldItem == newItem
        }
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    var learnDataOutputList: List<LearnEntity>
        get() = differ.currentList
        set(value) = differ.submitList(value)

    // 既存の長押しリスナー
    private var onItemLongClickListener: ((LearnEntity) -> Unit)? = null

    fun setOnItemLongClickListener(listener: (LearnEntity) -> Unit) {
        this.onItemLongClickListener = listener
    }

    // --- ここから追加 ---
    // クリックリスナーのプロパティとセッター
    private var onItemClickListener: ((LearnEntity) -> Unit)? = null

    fun setOnItemClickListener(listener: (LearnEntity) -> Unit) {
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
            text = currentItem.out

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
        holder.tvMetadata.text = holder.itemView.context.getString(
            R.string.learn_dictionary_entry_metadata,
            currentItem.score,
            currentItem.usageCount,
            holder.itemView.context.getString(
                if (currentItem.isPhrase) {
                    R.string.learn_dictionary_entry_phrase
                } else {
                    R.string.learn_dictionary_entry_segment
                }
            ),
        )
    }
}
