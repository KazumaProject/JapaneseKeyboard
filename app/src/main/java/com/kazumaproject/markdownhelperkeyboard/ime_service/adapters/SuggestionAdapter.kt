package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.correctReading

class SuggestionAdapter : RecyclerView.Adapter<SuggestionAdapter.SuggestionViewHolder>() {

    inner class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: MaterialTextView = itemView.findViewById(R.id.suggestion_item_text_view)
        val typeText: MaterialTextView = itemView.findViewById(R.id.suggestion_item_type_text_view)
    }

    private var highlightedPosition: Int = RecyclerView.NO_POSITION

    private val diffCallback = object : DiffUtil.ItemCallback<Candidate>() {
        override fun areItemsTheSame(oldItem: Candidate, newItem: Candidate): Boolean {
            return false
        }

        override fun areContentsTheSame(oldItem: Candidate, newItem: Candidate): Boolean {
            return false
        }
    }

    private var onItemClickListener: ((Candidate, Int) -> Unit)? = null

    fun setOnItemClickListener(onItemClick: (Candidate, Int) -> Unit) {
        this.onItemClickListener = onItemClick
    }

    private var onItemLongClickListener: ((Candidate, Int) -> Unit)? = null

    fun setOnItemLongClickListener(onItemLongClick: (Candidate, Int) -> Unit) {
        this.onItemLongClickListener = onItemLongClick
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    var suggestions: List<Candidate>
        get() = differ.currentList
        set(value) = differ.submitList(value)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.suggestion_item, parent, false)
        return SuggestionViewHolder(itemView)
    }

    override fun getItemCount(): Int = suggestions.size

    override fun getItemId(position: Int): Long = position.toLong()

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        val suggestion = suggestions[position]
        val paddingLength = when {
            position == 0 -> 4
            suggestion.string.length == 1 -> 4
            suggestion.string.length == 2 -> 2
            else -> 0
        }

        val readingCorrectionString =
            if (suggestion.type == (15).toByte()) suggestion.string.correctReading() else Pair(
                "",
                ""
            )
        holder.text.text = if (suggestion.type == (15).toByte()) {
            readingCorrectionString.first.padStart(readingCorrectionString.first.length + paddingLength)
                .plus(" ".repeat(paddingLength))
        } else {
            suggestion.string.padStart(suggestion.string.length + paddingLength)
                .plus(" ".repeat(paddingLength))
        }
        holder.typeText.text = when (suggestion.type) {
            (1).toByte() -> ""
            /** 予測 **/
            (9).toByte() -> ""
            (5).toByte() -> "[部]"
            (7).toByte() -> "[単]"
            /** 最長 **/
            (10).toByte() -> ""
            (11).toByte() -> "  "
            (12).toByte() -> "  "
            (13).toByte() -> "  "
            (14).toByte() -> "[日付]"
            /** 修正 **/
            (15).toByte() -> {
                val spannable = SpannableString("[読] ${readingCorrectionString.second}")
                spannable.setSpan(
                    RelativeSizeSpan(1.25f),
                    4,
                    spannable.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable
            }
            /** ことわざ **/
            (16).toByte() -> ""
            /** 数 漢字混じり **/
            (17).toByte() -> ""
            /** 数 カンマあり**/
            (18).toByte() -> ""
            /** 数 **/
            (19).toByte() -> ""
            /** 学習 **/
            (20).toByte() -> ""
            /** 記号半角 **/
            (21).toByte() -> ""
            /** 全角数字 **/
            (22).toByte() -> "[全]"
            /** Mozc UT Names **/
            (23).toByte() -> ""
            /** Mozc UT Places **/
            (24).toByte() -> ""
            /** Mozc UT Wiki **/
            (25).toByte() -> ""
            /** Mozc UT Neologd **/
            (26).toByte() -> ""
            /** Mozc UT Web **/
            (27).toByte() -> ""
            /** クリップボード **/
            (28).toByte() -> "[タップでペースト] [長押しで削除]"
            else -> ""
        }
        holder.itemView.isPressed = position == highlightedPosition
        holder.itemView.setOnClickListener {
            onItemClickListener?.invoke(suggestion, position)
        }
        holder.itemView.setOnLongClickListener {
            onItemLongClickListener?.invoke(suggestion, position)
            true
        }
    }

    fun updateHighlightPosition(newPosition: Int) {
        val previousPosition = highlightedPosition
        highlightedPosition = newPosition
        if (previousPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(previousPosition)
        }
        notifyItemChanged(highlightedPosition)
    }
}
