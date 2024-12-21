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
    }

    private val diffCallback = object : DiffUtil.ItemCallback<Pair<String, List<String>>>() {
        override fun areItemsTheSame(
            oldItem: Pair<String, List<String>>,
            newItem: Pair<String, List<String>>
        ): Boolean {
            return oldItem.first == newItem.first && oldItem.second == newItem.second
        }

        override fun areContentsTheSame(
            oldItem: Pair<String, List<String>>,
            newItem: Pair<String, List<String>>
        ): Boolean {
            return oldItem.hashCode() == newItem.hashCode()
        }
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    var learnDataList: List<Pair<String, List<String>>>
        get() = differ.currentList
        set(value) = differ.submitList(value)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LearnDictionaryViewHolder {
        return LearnDictionaryViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.learn_dictionary_item,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return learnDataList.size
    }

    override fun onBindViewHolder(holder: LearnDictionaryViewHolder, position: Int) {
        val adapter = LearnDataOutputAdapter()
        holder.tvInput.text = learnDataList[position].first
        holder.rvOutput.layoutManager =
            LinearLayoutManager(holder.rvOutput.context, LinearLayoutManager.HORIZONTAL, false)
        holder.rvOutput.adapter = adapter
        adapter.learnDataOutputList = learnDataList[position].second
    }

}