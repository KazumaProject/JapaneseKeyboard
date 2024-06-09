package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

class SuggestionAdapter : RecyclerView.Adapter<SuggestionAdapter.SuggestionViewHolder>(){
    inner class SuggestionViewHolder(itemView: View): RecyclerView.ViewHolder(itemView)

    private val diffCallback = object : DiffUtil.ItemCallback<Candidate>() {
        override fun areItemsTheSame(oldItem: Candidate, newItem: Candidate): Boolean {
            return oldItem.string == newItem.string
        }

        override fun areContentsTheSame(oldItem: Candidate, newItem: Candidate): Boolean {
            return oldItem.hashCode() == newItem.hashCode()
        }
    }

    private var onItemClickListener: ((Candidate) -> Unit)? = null

    fun setOnItemClickListener(onItemClick: (Candidate) -> Unit) {
        this.onItemClickListener = onItemClick
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    var suggestions: List<Candidate>
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

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        val suggestion = suggestions[position]
        holder.itemView.apply {
            val text = findViewById<MaterialTextView>(R.id.suggestion_item_text_view)
            val typeText = findViewById<MaterialTextView>(R.id.suggestion_item_type_text_view)
            text.text = suggestion.string
            typeText.text = when(suggestion.type){
                (1).toByte() -> " [N-Best] [${suggestion.score}]"
                (2).toByte() -> " [部分] [${suggestion.score}] [${suggestion.leftId}] "
                (3).toByte() -> " [ひらがな] [${suggestion.score}] "
                (4).toByte() -> " [カタカナ] [${suggestion.score}] "
                (5).toByte() -> " [最長] [${suggestion.score}] [${suggestion.leftId}] "
                (6).toByte() -> " [候補] [${suggestion.score}] "
                else -> ""
            }
            setOnClickListener {
                onItemClickListener?.let { click ->
                    click(suggestion)
                }
            }
        }
    }

}