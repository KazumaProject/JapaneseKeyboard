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
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.correctReading
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.debugPrintCodePoints
import timber.log.Timber

class SuggestionAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_EMPTY = 0
        private const val VIEW_TYPE_SUGGESTION = 1
        private const val VIEW_TYPE_CUSTOM_LAYOUT_PICKER = 2 // CHANGED: Add new view type
    }

    enum class HelperIcon {
        UNDO, PASTE
    }

    // Listeners for clicks
    private var onItemClickListener: ((Candidate, Int) -> Unit)? = null
    private var onItemLongClickListener: ((Candidate, Int) -> Unit)? = null
    private var onItemHelperIconClickListener: ((HelperIcon) -> Unit)? = null
    private var onItemHelperIconLongClickListener: ((HelperIcon) -> Unit)? = null
    private var onCustomLayoutItemClickListener: ((Int) -> Unit)? =
        null // CHANGED: Add new listener

    // Holds the text to show in the clipboard preview inside the empty state.
    private var clipboardText: String = ""
    private var undoText: String = ""

    // Internal flags to track enable/disable state
    private var isUndoEnabled: Boolean = false
    private var isPasteEnabled: Boolean = true

    // CHANGED: Add state for TenKeyQWERTYMode and custom layouts
    private var currentMode: TenKeyQWERTYMode = TenKeyQWERTYMode.Default
    private var customLayouts: List<CustomKeyboardLayout> = emptyList()


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

    // CHANGED: Add setter for new listener
    fun setOnCustomLayoutItemClickListener(listener: (Int) -> Unit) {
        this.onCustomLayoutItemClickListener = listener
    }

    fun release() {
        onItemClickListener = null
        onItemLongClickListener = null
        onItemHelperIconClickListener = null
        onItemHelperIconLongClickListener = null
        onCustomLayoutItemClickListener = null // CHANGED: Release listener
    }

    fun setUndoEnabled(enabled: Boolean) {
        isUndoEnabled = enabled
        if (suggestions.isEmpty()) {
            notifyItemChanged(0)
        }
    }

    fun setPasteEnabled(enabled: Boolean) {
        isPasteEnabled = enabled
        if (suggestions.isEmpty()) {
            notifyItemChanged(0)
        }
    }

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

    // These methods should be called from your IME Service when the mode or data changes.
    fun updateState(mode: TenKeyQWERTYMode, layouts: List<CustomKeyboardLayout>) {
        val needsFullRefresh = (currentMode != mode) || (customLayouts != layouts)
        currentMode = mode
        customLayouts = layouts
        if (needsFullRefresh) {
            notifyItemChanged(layouts.size)
        }
    }

    private val diffCallback = object : DiffUtil.ItemCallback<Candidate>() {
        override fun areItemsTheSame(oldItem: Candidate, newItem: Candidate): Boolean {
            return oldItem.leftId == newItem.leftId && oldItem.rightId == newItem.rightId
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

    // CHANGED: Add new ViewHolder for custom layouts
    inner class CustomLayoutViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: MaterialTextView = itemView.findViewById(R.id.custom_layout_name)
        val detailTextView: MaterialTextView = itemView.findViewById(R.id.custom_layout_details)
    }

    // CHANGED: Updated logic to select one of three view types
    override fun getItemViewType(position: Int): Int {
        return if (suggestions.isNotEmpty()) {
            VIEW_TYPE_SUGGESTION
        } else { // Suggestions are empty
            if (currentMode is TenKeyQWERTYMode.Custom && customLayouts.isNotEmpty()) {
                VIEW_TYPE_CUSTOM_LAYOUT_PICKER
            } else {
                VIEW_TYPE_EMPTY
            }
        }
    }

    // CHANGED: Updated logic for item count
    override fun getItemCount(): Int {
        return if (suggestions.isNotEmpty()) {
            suggestions.size
        } else { // Suggestions are empty
            if (currentMode is TenKeyQWERTYMode.Custom && customLayouts.isNotEmpty()) {
                customLayouts.size // Show a list of custom layouts
            } else {
                1 // Show the single empty view (Undo/Paste)
            }
        }
    }

    // CHANGED: Updated to handle the new view type
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val isDynamicColorEnable = DynamicColors.isDynamicColorAvailable()
        return when (viewType) {
            VIEW_TYPE_EMPTY -> {
                val emptyView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.suggestion_empty_layout, parent, false)
                EmptyViewHolder(emptyView)
            }

            VIEW_TYPE_CUSTOM_LAYOUT_PICKER -> {
                // You need to create this new layout file: R.layout.suggestion_custom_layout_item
                val customView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.suggestion_custom_layout_item, parent, false)
                CustomLayoutViewHolder(customView)
            }

            VIEW_TYPE_SUGGESTION -> {
                val itemView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.suggestion_item, parent, false)
                itemView.setBackgroundResource(
                    if (isDynamicColorEnable) com.kazumaproject.core.R.drawable.recyclerview_item_bg_material else com.kazumaproject.core.R.drawable.recyclerview_item_bg
                )
                SuggestionViewHolder(itemView)
            }

            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }


    // CHANGED: Updated to bind the new ViewHolder
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            VIEW_TYPE_EMPTY -> onBindEmptyViewHolder(holder as EmptyViewHolder)
            VIEW_TYPE_SUGGESTION -> onBindSuggestionViewHolder(
                holder as SuggestionViewHolder,
                position
            )

            VIEW_TYPE_CUSTOM_LAYOUT_PICKER -> onBindCustomLayoutViewHolder(
                holder as CustomLayoutViewHolder,
                position
            )
        }
    }

    // Helper method for binding EmptyViewHolder
    private fun onBindEmptyViewHolder(holder: EmptyViewHolder) {
        val isDynamicColorEnable = DynamicColors.isDynamicColorAvailable()
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
    }

    // Helper method for binding SuggestionViewHolder
    private fun onBindSuggestionViewHolder(holder: SuggestionViewHolder, position: Int) {
        val suggestionHolder = holder
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
            // ... (rest of your when block for suggestion type)
            (1).toByte() -> ""
            (9).toByte() -> ""
            (5).toByte() -> "[部]"
            (7).toByte() -> "[単]"
            (10).toByte() -> ""
            (11).toByte() -> "  "
            (12).toByte() -> "  "
            (13).toByte() -> "  "
            (14).toByte() -> "[日付]"
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

            (16).toByte() -> ""
            (17).toByte() -> ""
            (18).toByte() -> ""
            (19).toByte() -> ""
            (20).toByte() -> ""
            (21).toByte() -> ""
            (22).toByte() -> "[全]"
            (23).toByte() -> ""
            (24).toByte() -> ""
            (25).toByte() -> ""
            (26).toByte() -> ""
            (27).toByte() -> ""
            (28).toByte() -> ""
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

    // CHANGED: New helper method for binding the custom layout ViewHolder
    private fun onBindCustomLayoutViewHolder(holder: CustomLayoutViewHolder, position: Int) {
        val layoutItem = customLayouts[position]
        holder.nameTextView.text = layoutItem.name
        holder.detailTextView.text = "列: ${layoutItem.columnCount}, 行: ${layoutItem.rowCount}"

        // Set a click listener to notify the service
        holder.itemView.setOnClickListener {
            onCustomLayoutItemClickListener?.invoke(position)
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
