package com.kazumaproject.markdownhelperkeyboard.learning.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.markdownhelperkeyboard.R

class LearnDictionaryAdapter :
    RecyclerView.Adapter<LearnDictionaryAdapter.LearnDictionaryViewHolder>() {

    inner class LearnDictionaryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvInput: MaterialTextView = itemView.findViewById(R.id.tvInput)
        val rvOutput: RecyclerView = itemView.findViewById(R.id.rvOutput)
        val outputAdapter = LearnDataOutputAdapter()

        init {
            rvOutput.layoutManager =
                LinearLayoutManager(itemView.context, LinearLayoutManager.HORIZONTAL, false)
            rvOutput.adapter = outputAdapter
        }
    }

    private val diffCallback = object : DiffUtil.ItemCallback<Pair<String, List<String>>>() {
        override fun areItemsTheSame(
            oldItem: Pair<String, List<String>>,
            newItem: Pair<String, List<String>>
        ): Boolean = oldItem.first == newItem.first

        override fun areContentsTheSame(
            oldItem: Pair<String, List<String>>,
            newItem: Pair<String, List<String>>
        ): Boolean = oldItem == newItem
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    var learnDataList: List<Pair<String, List<String>>>
        get() = differ.currentList
        set(value) = differ.submitList(value)

    private var onItemLongClickListener: ((String) -> Unit)? = null
    fun setOnItemLongClickListener(listener: (String) -> Unit) {
        this.onItemLongClickListener = listener
    }

    private var onItemChildrenClickListener: ((String, String) -> Unit)? = null
    fun setOnItemChildrenClickListener(listener: (String, String) -> Unit) {
        this.onItemChildrenClickListener = listener
    }

    private var onItemChildrenLongClickListener: ((String, String) -> Unit)? = null
    fun setOnItemChildrenLongClickListener(listener: (String, String) -> Unit) {
        this.onItemChildrenLongClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LearnDictionaryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.learn_dictionary_item, parent, false)
        return LearnDictionaryViewHolder(view)
    }

    override fun getItemCount(): Int = learnDataList.size

    override fun onBindViewHolder(holder: LearnDictionaryViewHolder, position: Int) {
        val item = learnDataList[position]

        holder.tvInput.apply {
            text = item.first
            setOnLongClickListener {
                onItemLongClickListener?.invoke(item.first)
                true
            }
        }

        holder.outputAdapter.apply {
            learnDataOutputList = item.second
            this.setOnItemClickListener { child ->
                onItemChildrenClickListener?.invoke(item.first, child)
            }
            this.setOnItemLongClickListener { child ->
                onItemChildrenLongClickListener?.invoke(item.first, child)
            }
        }
    }
}
