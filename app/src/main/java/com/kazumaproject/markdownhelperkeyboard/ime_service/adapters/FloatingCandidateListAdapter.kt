package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import timber.log.Timber

private const val VIEW_TYPE_SUGGESTION = 1
private const val VIEW_TYPE_PAGER = 2

class FloatingCandidateListAdapter(
    private val pageSize: Int,
) : ListAdapter<String, RecyclerView.ViewHolder>(DiffCallback()) {

    // --- Public Callbacks ---
    var onSuggestionClicked: ((suggestion: String) -> Unit)? = null
    var onPagerClicked: (() -> Unit)? = null

    // --- Highlight State ---
    private var highlightedPosition: Int = RecyclerView.NO_POSITION

    // --- Public methods to control highlight ---
    fun updateHighlightPosition(newPosition: Int) {
        val previousPosition = highlightedPosition
        highlightedPosition = newPosition

        Timber.d("updateHighlightPosition: $newPosition")

        if (previousPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(previousPosition)
        }
        if (newPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(newPosition)
        }
    }

    fun getHighlightedItem(): String? {
        return if (highlightedPosition in 0 until itemCount) {
            getItem(highlightedPosition)
        } else {
            null
        }
    }

    // --- Suggestion ViewHolder ---
    inner class SuggestionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView: TextView = view.findViewById(R.id.text_view_item)

        init {
            itemView.setOnClickListener {
                if (absoluteAdapterPosition != RecyclerView.NO_POSITION) {
                    onSuggestionClicked?.invoke(getItem(absoluteAdapterPosition))
                }
            }
        }

        fun bind(text: String) {
            textView.text = text
        }
    }

    // --- Pager ViewHolder ---
    inner class PagerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView: TextView = view.findViewById(R.id.text_view_item)

        init {
            itemView.setOnClickListener { onPagerClicked?.invoke() }
        }

        fun bind(text: String) {
            textView.text = text
        }
    }

    // --- Adapter Overrides ---
    override fun getItemViewType(position: Int): Int {
        return if (position == pageSize) VIEW_TYPE_PAGER else VIEW_TYPE_SUGGESTION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SUGGESTION -> SuggestionViewHolder(
                inflater.inflate(
                    R.layout.floating_candidate_list_item_string,
                    parent,
                    false
                )
            )

            VIEW_TYPE_PAGER -> PagerViewHolder(
                inflater.inflate(
                    R.layout.floating_candidate_list_item_pager,
                    parent,
                    false
                )
            )

            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // Set activation state for background drawable
        holder.itemView.isActivated = (position == highlightedPosition)

        val currentItem = getItem(position)
        when (holder) {
            is SuggestionViewHolder -> holder.bind(currentItem)
            is PagerViewHolder -> holder.bind(currentItem)
        }
    }

    // --- DiffUtil Callback ---
    private class DiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean =
            oldItem == newItem
    }
}
