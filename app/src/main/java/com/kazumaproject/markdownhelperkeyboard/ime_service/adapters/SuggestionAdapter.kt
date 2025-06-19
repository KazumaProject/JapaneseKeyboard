package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.core.domain.extensions.isDarkThemeOn
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.correctReading
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.debugPrintCodePoints
import timber.log.Timber

class SuggestionAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_EMPTY = 0
        private const val VIEW_TYPE_SUGGESTION = 1
    }

    enum class HelperIcon {
        UNDO, PASTE
    }

    // Listeners for clicks
    private var onItemClickListener: ((Candidate, Int) -> Unit)? = null
    private var onItemLongClickListener: ((Candidate, Int) -> Unit)? = null
    private var onItemHelperIconClickListener: ((HelperIcon) -> Unit)? = null
    private var onItemHelperIconLongClickListener: ((HelperIcon) -> Unit)? = null

    // Holds the text to show in the clipboard preview inside the empty state.
    private var clipboardText: String = ""
    private var undoText: String = ""

    // Internal flags to track enable/disable state
    private var isUndoEnabled: Boolean = false
    private var isPasteEnabled: Boolean = true

    fun setOnItemClickListener(onItemClick: (Candidate, Int) -> Unit) {
        this.onItemClickListener = onItemClick
    }

    fun setOnItemLongClickListener(onItemLongClick: (Candidate, Int) -> Unit) {
        this.onItemLongClickListener = onItemLongClick
    }

    fun setOnItemHelperIconClickListener(onItemHelperIconClickListener: (HelperIcon) -> Unit) {
        this.onItemHelperIconClickListener = onItemHelperIconClickListener
    }

    fun setOnItemHelperIconLongClickListener(onItemHelperIconLongClickListener: (HelperIcon) -> Unit) {
        this.onItemHelperIconLongClickListener = onItemHelperIconLongClickListener
    }

    fun release() {
        onItemClickListener = null
        onItemLongClickListener = null
        onItemHelperIconClickListener = null
        onItemHelperIconLongClickListener = null
    }

    /**
     * Enable or disable the undo icon in the empty state.
     */
    fun setUndoEnabled(enabled: Boolean) {
        isUndoEnabled = enabled
        if (suggestions.isEmpty()) {
            notifyItemChanged(0)
        }
    }

    /**
     * Enable or disable the paste icon in the empty state.
     */
    fun setPasteEnabled(enabled: Boolean) {
        isPasteEnabled = enabled
        if (suggestions.isEmpty()) {
            notifyItemChanged(0)
        }
    }

    /**
     * Public function to set the text of clipboardPreviewText in the empty state.
     * If currently showing empty state, forces a re‐bind to update the preview text.
     */
    fun setClipboardPreview(text: String) {
        clipboardText = text
        if (suggestions.isEmpty()) {
            notifyItemChanged(0)
        }
    }


    fun setUndoPreviewText(text: String) {
        undoText = text
        if (suggestions.isEmpty()) {
            notifyItemChanged(0)
        }
    }

    private val diffCallback = object : DiffUtil.ItemCallback<Candidate>() {
        override fun areItemsTheSame(oldItem: Candidate, newItem: Candidate): Boolean {
            return oldItem.string == newItem.string && oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: Candidate, newItem: Candidate): Boolean {
            return oldItem == newItem
        }
    }
    private val differ = AsyncListDiffer(this, diffCallback)

    var suggestions: List<Candidate>
        get() = differ.currentList
        set(value) = differ.submitList(value)

    // Track which suggestion is highlighted
    private var highlightedPosition: Int = RecyclerView.NO_POSITION

    /** ViewHolder for a normal suggestion row **/
    inner class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: MaterialTextView = itemView.findViewById(R.id.suggestion_item_text_view)
        val typeText: MaterialTextView = itemView.findViewById(R.id.suggestion_item_type_text_view)
    }

    /** ViewHolder for the “empty” state (showing icons + clipboard preview) **/
    inner class EmptyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val undoIconParent: ConstraintLayout? = itemView.findViewById(R.id.undo_icon_parent)
        val undoIcon: MaterialTextView? = itemView.findViewById(R.id.undo_icon)
        val pasteIcon: ConstraintLayout? = itemView.findViewById(R.id.paste_icon_patent)
        val clipboardPreviewText: MaterialTextView? =
            itemView.findViewById(R.id.clipboard_text_preview)
        val clipboardPreviewTextDescription: MaterialTextView? =
            itemView.findViewById(R.id.clipboard_preview_text_description)
    }

    override fun getItemViewType(position: Int): Int {
        return if (suggestions.isEmpty()) {
            VIEW_TYPE_EMPTY
        } else {
            VIEW_TYPE_SUGGESTION
        }
    }

    override fun getItemCount(): Int {
        return if (suggestions.isEmpty()) 1 else suggestions.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val isDynamicColorEnable = DynamicColors.isDynamicColorAvailable()
        return if (viewType == VIEW_TYPE_EMPTY) {
            val emptyView = LayoutInflater.from(parent.context)
                .inflate(R.layout.suggestion_empty_layout, parent, false)
            EmptyViewHolder(emptyView)
        } else {
            val itemView = LayoutInflater.from(parent.context)
                .inflate(R.layout.suggestion_item, parent, false)
            itemView.setBackgroundResource(
                if (isDynamicColorEnable) com.kazumaproject.core.R.drawable.recyclerview_item_bg_material else com.kazumaproject.core.R.drawable.recyclerview_item_bg
            )
            SuggestionViewHolder(itemView)
        }
    }


    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val isDynamicColorEnable = DynamicColors.isDynamicColorAvailable()
        if (holder is EmptyViewHolder) {
            holder.apply {
                // Set enabled/disabled state on icons
                undoIcon?.apply {
                    isVisible = isUndoEnabled
                    isFocusable = false
                    Timber.d("undo text: $undoText")
                    debugPrintCodePoints(undoText)
                    text = undoText.reversed()
                }
                pasteIcon?.apply {
                    isEnabled = isPasteEnabled
                    visibility = if (isPasteEnabled) {
                        View.VISIBLE
                    } else {
                        View.INVISIBLE
                    }
                    isFocusable = false
                }
                // Update the clipboard preview text
                clipboardPreviewText?.text = clipboardText

                undoIconParent?.apply {
                    if (isDynamicColorEnable) {
                        if (this.context.isDarkThemeOn()) {
                            setBackgroundResource(
                                com.kazumaproject.core.R.drawable.ten_keys_side_bg_material
                            )
                        } else {
                            setBackgroundResource(
                                com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light
                            )
                        }
                    }
                    isVisible = isUndoEnabled
                    setOnClickListener {
                        onItemHelperIconClickListener?.invoke(HelperIcon.UNDO)
                    }
                    setOnLongClickListener {
                        onItemHelperIconLongClickListener?.invoke(HelperIcon.UNDO)
                        true
                    }
                }

                clipboardPreviewTextDescription?.apply {
                    isVisible = isPasteEnabled
                }

                pasteIcon?.apply {
                    setOnClickListener {
                        onItemHelperIconClickListener?.invoke(HelperIcon.PASTE)
                    }
                    setOnLongClickListener {
                        onItemHelperIconLongClickListener?.invoke(HelperIcon.PASTE)
                        true
                    }
                }
            }
            return
        }

        // Otherwise, it's a real suggestion row:
        val suggestionHolder = holder as SuggestionViewHolder
        val suggestion = suggestions[position]

        // === (Existing padding + text logic) ===
        val paddingLength = when {
            position == 0 -> 4
            suggestion.string.length == 1 -> 4
            suggestion.string.length == 2 -> 2
            else -> 1
        }

        val readingCorrectionString =
            if (suggestion.type == (15).toByte()) suggestion.string.correctReading() else Pair(
                "",
                ""
            )

        suggestionHolder.text.text = if (suggestion.type == (15).toByte()) {
            readingCorrectionString.first
                .padStart(readingCorrectionString.first.length + paddingLength)
                .plus(" ".repeat(paddingLength))
        } else {
            suggestion.string
                .padStart(suggestion.string.length + paddingLength)
                .plus(" ".repeat(paddingLength))
        }

        suggestionHolder.typeText.text = when (suggestion.type) {
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
            /** User Dictionary **/
            (28).toByte() -> ""
            /** 英語 **/
            (29).toByte() -> ""
            else -> ""
        }

        // Highlight logic:
        suggestionHolder.itemView.isPressed = position == highlightedPosition

        suggestionHolder.itemView.setOnClickListener {
            onItemClickListener?.invoke(suggestion, position)
        }
        suggestionHolder.itemView.setOnLongClickListener {
            onItemLongClickListener?.invoke(suggestion, position)
            true
        }
    }

    /**
     * Call this to move the highlight to a new position; previous highlighted row will repaint.
     */
    fun updateHighlightPosition(newPosition: Int) {
        val previous = highlightedPosition
        highlightedPosition = newPosition
        if (previous != RecyclerView.NO_POSITION) {
            notifyItemChanged(previous)
        }
        notifyItemChanged(highlightedPosition)
    }
}
