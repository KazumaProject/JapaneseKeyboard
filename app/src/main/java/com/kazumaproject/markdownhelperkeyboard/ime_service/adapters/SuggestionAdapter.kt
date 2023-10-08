package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.markdownhelperkeyboard.R

class SuggestionAdapter : RecyclerView.Adapter<SuggestionAdapter.SuggestionViewHolder>(){
    inner class SuggestionViewHolder(itemView: View): RecyclerView.ViewHolder(itemView)

    private val diffCallback = object : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem.hashCode() == newItem.hashCode()
        }
    }

    private var onItemClickListener: ((String) -> Unit)? = null

    fun setOnItemClickListener(onItemClick: (String) -> Unit) {
        this.onItemClickListener = onItemClick
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    var suggestions: List<String>
        get() = differ.currentList
        set(value) = differ.submitList(value)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        return SuggestionViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.suggestion_item,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return suggestions.size
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        val suggestion = suggestions[position]
        holder.itemView.apply {
            val text = findViewById<MaterialTextView>(R.id.suggestion_item_text_view)
            text.text = suggestion
            setOnClickListener {
                onItemClickListener?.let { click ->
                    click(suggestion)
                }
            }
        }
    }

}