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

    private var onItemLongClickListener: ((String) -> Unit)? = null

    fun setOnItemLongClickListener(listener: (String) -> Unit) {
        this.onItemLongClickListener = listener
    }

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
        holder.tvOutput.apply {
            text = learnDataOutputList[position]
            setOnLongClickListener {
                onItemLongClickListener?.invoke(learnDataOutputList[position])
                true
            }
        }

    }

}