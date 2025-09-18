package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.graphics.Bitmap
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.core.domain.extensions.isAllFullWidthNumericSymbol
import com.kazumaproject.core.domain.extensions.isAllHalfWidthNumericSymbol
import com.kazumaproject.core.domain.extensions.isDarkThemeOn
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.correctReading
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.debugPrintCodePoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber

class SuggestionAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_EMPTY = 0
        private const val VIEW_TYPE_SUGGESTION = 1
        private const val VIEW_TYPE_CUSTOM_LAYOUT_PICKER = 2
    }

    enum class HelperIcon {
        UNDO, PASTE
    }

    // Listeners for clicks
    private var onItemClickListener: ((Candidate, Int) -> Unit)? = null
    private var onItemLongClickListener: ((Candidate, Int) -> Unit)? = null
    private var onItemHelperIconClickListener: ((HelperIcon) -> Unit)? = null
    private var onItemHelperIconLongClickListener: ((HelperIcon) -> Unit)? = null
    private var onCustomLayoutItemClickListener: ((Int) -> Unit)? = null
    private var onShowSoftKeyboardClick: (() -> Unit)? = null

    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    var onListUpdated: (() -> Unit)? = null

    // Holds the preview content for the empty state.
    private var clipboardText: String = ""
    private var clipboardBitmap: Bitmap? = null // ★追加: Bitmapを保持するフィールド
    private var undoText: String = ""

    // Internal flags to track enable/disable state
    private var isUndoEnabled: Boolean = false
    private var isPasteEnabled: Boolean = true

    private var currentMode: TenKeyQWERTYMode = TenKeyQWERTYMode.Default
    private var customLayouts: List<CustomKeyboardLayout> = emptyList()

    private var showCustomTab: Boolean = true

    private var incognitoIconDrawable: android.graphics.drawable.Drawable? = null

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

    fun setOnCustomLayoutItemClickListener(listener: (Int) -> Unit) {
        this.onCustomLayoutItemClickListener = listener
    }

    fun setOnPhysicalKeyboardListener(listener: () -> Unit) {
        this.onShowSoftKeyboardClick = listener
    }

    fun release() {
        onItemClickListener = null
        onItemLongClickListener = null
        onItemHelperIconClickListener = null
        onItemHelperIconLongClickListener = null
        onCustomLayoutItemClickListener = null
        onShowSoftKeyboardClick = null
        onListUpdated = null
        incognitoIconDrawable = null
        adapterScope.cancel()
    }

    /**
     * ★新しい関数: シークレットモードのアイコンを設定します。
     * Drawableがnullでなければアイコンを表示し、nullなら非表示にします。
     */
    fun setIncognitoIcon(drawable: android.graphics.drawable.Drawable?) {
        this.incognitoIconDrawable = drawable
        if (suggestions.isEmpty()) {
            notifyItemChanged(0)
        }
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

    /**
     * テキストのクリップボードプレビューを設定します。
     * このとき、画像のプレビューはクリアされます。
     */
    fun setClipboardPreview(text: String) {
        clipboardText = text
        clipboardBitmap = null // ★追加: テキスト設定時に画像はクリア
        if (suggestions.isEmpty()) {
            notifyItemChanged(0)
        }
    }

    /**
     * ★新しい関数: 画像のクリップボードプレビューを設定します。
     * このとき、テキストのプレビューはクリアされます。
     */
    fun setClipboardImagePreview(bitmap: Bitmap?) {
        clipboardBitmap = bitmap
        clipboardText = "" // 画像設定時にテキストはクリア
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

    fun updateState(mode: TenKeyQWERTYMode, layouts: List<CustomKeyboardLayout>) {
        val needsFullRefresh = (currentMode != mode) || (customLayouts != layouts)
        currentMode = mode
        customLayouts = layouts
        if (needsFullRefresh) {
            notifyItemChanged(layouts.size)
        }
    }

    fun updateCustomTabVisibility(visibility: Boolean) {
        showCustomTab = visibility
    }

    private val diffCallback = object : DiffUtil.ItemCallback<Candidate>() {
        override fun areItemsTheSame(oldItem: Candidate, newItem: Candidate): Boolean {
            return oldItem.string == newItem.string
        }

        override fun areContentsTheSame(oldItem: Candidate, newItem: Candidate): Boolean {
            return oldItem == newItem
        }
    }
    private val differ = AsyncListDiffer(this, diffCallback)

    var suggestions: List<Candidate>
        get() = differ.currentList
        set(value) {
            // submitListの第2引数にコールバックを渡す
            differ.submitList(value) {
                onListUpdated?.invoke()
            }
        }

    private var highlightedPosition: Int = RecyclerView.NO_POSITION

    inner class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: MaterialTextView = itemView.findViewById(R.id.suggestion_item_text_view)
        val typeText: MaterialTextView = itemView.findViewById(R.id.suggestion_item_type_text_view)
    }

    inner class EmptyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val undoIconParent: ConstraintLayout? = itemView.findViewById(R.id.undo_icon_parent)
        val undoIcon: MaterialTextView? = itemView.findViewById(R.id.undo_icon)
        val pasteIconParent: ConstraintLayout? = itemView.findViewById(R.id.paste_icon_patent)
        val pasteIcon: ImageView? = itemView.findViewById(R.id.paste_icon)
        val clipboardPreviewText: MaterialTextView? =
            itemView.findViewById(R.id.clipboard_text_preview)
        val clipboardPreviewTextDescription: MaterialTextView? =
            itemView.findViewById(R.id.clipboard_preview_text_description)
        val incognitoIcon: AppCompatImageButton? = itemView.findViewById(R.id.incognito_icon)
    }

    inner class CustomLayoutViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: MaterialTextView = itemView.findViewById(R.id.custom_layout_name)
    }

    override fun getItemViewType(position: Int): Int {
        return if (suggestions.isNotEmpty()) {
            VIEW_TYPE_SUGGESTION
        } else {
            if (currentMode is TenKeyQWERTYMode.Custom && customLayouts.isNotEmpty() && showCustomTab) {
                VIEW_TYPE_CUSTOM_LAYOUT_PICKER
            } else {
                VIEW_TYPE_EMPTY
            }
        }
    }

    override fun getItemCount(): Int {
        return if (suggestions.isNotEmpty()) {
            suggestions.size
        } else {
            if (currentMode is TenKeyQWERTYMode.Custom && customLayouts.isNotEmpty()) {
                customLayouts.size
            } else {
                1
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val isDynamicColorEnable = DynamicColors.isDynamicColorAvailable()
        return when (viewType) {
            VIEW_TYPE_EMPTY -> {
                val emptyView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.suggestion_empty_layout, parent, false)
                EmptyViewHolder(emptyView)
            }

            VIEW_TYPE_CUSTOM_LAYOUT_PICKER -> {
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

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            VIEW_TYPE_EMPTY -> onBindEmptyViewHolder(holder as EmptyViewHolder)
            VIEW_TYPE_SUGGESTION -> onBindSuggestionViewHolder(
                holder as SuggestionViewHolder, position
            )

            VIEW_TYPE_CUSTOM_LAYOUT_PICKER -> onBindCustomLayoutViewHolder(
                holder as CustomLayoutViewHolder, position
            )
        }
    }

    private fun onBindEmptyViewHolder(holder: EmptyViewHolder) {
        val isDynamicColorEnable = DynamicColors.isDynamicColorAvailable()
        holder.apply {
            incognitoIcon?.apply {
                if (incognitoIconDrawable != null) {
                    visibility = View.VISIBLE
                    setImageDrawable(incognitoIconDrawable)
                } else {
                    visibility = View.GONE
                }
            }

            undoIcon?.apply {
                isVisible = isUndoEnabled
                isFocusable = false
                Timber.d("undo text: $undoText")
                debugPrintCodePoints(undoText)
                text = undoText.reversed()
            }
            pasteIconParent?.apply {
                isEnabled = isPasteEnabled
                visibility = if (isPasteEnabled) View.VISIBLE else View.INVISIBLE
                isFocusable = false
            }

            // ★修正: 画像プレビューのロジック
            pasteIcon?.apply {
                if (clipboardBitmap != null) {
                    // Bitmapがあればそれを設定
                    setImageBitmap(clipboardBitmap)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                } else {
                    // なければデフォルトのアイコンを設定
                    setImageResource(com.kazumaproject.core.R.drawable.content_paste_24px)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
            }

            // テキストプレビューは、画像がない場合にのみ表示
            clipboardPreviewText?.text = if (clipboardBitmap == null) clipboardText else ""

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

            // テキスト用の説明は、画像がない場合にのみ表示
            clipboardPreviewTextDescription?.isVisible = isPasteEnabled && clipboardBitmap == null

            pasteIconParent?.apply {
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

    private fun onBindSuggestionViewHolder(holder: SuggestionViewHolder, position: Int) {
        val suggestion = suggestions[position]
        val paddingLength = when {
            position == 0 -> 4
            suggestion.string.length == 1 -> 4
            suggestion.string.length == 2 -> 2
            else -> 1
        }
        val readingCorrectionString =
            if (suggestion.type == (15).toByte()) suggestion.string.correctReading() else Pair(
                "", ""
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
            (7).toByte() -> ""
            /** 最長 **/
            (10).toByte() -> ""
            /** 絵文字 **/
            (11).toByte() -> "  "
            /** 顔文字 **/
            (12).toByte() -> "  "
            /** 記号 **/
            (13).toByte() -> {
                when {
                    suggestion.string.isAllHalfWidthNumericSymbol() -> "[半]"
                    suggestion.string.isAllFullWidthNumericSymbol() -> "[全]"
                    else -> "  "
                }
            }
            /** 日付 **/
            (14).toByte() -> "[日付]"
            /** 修正 **/
            (15).toByte() -> {
                val spannable = SpannableString("[読] ${readingCorrectionString.second}")
                spannable.setSpan(
                    RelativeSizeSpan(1.25f), 4, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
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
            /** 記号 **/
            (21).toByte() -> when {
                suggestion.string.isAllHalfWidthNumericSymbol() -> "[半]"
                suggestion.string.isAllFullWidthNumericSymbol() -> "[全]"
                else -> "  "
            }
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
            (28).toByte() -> ""
            /** 英語 **/
            (29).toByte() -> ""
            /** 全角 **/
            (30).toByte() -> "[全]"
            /** 半角 **/
            (31).toByte() -> "[半]"
            /** 漢数字 **/
            (32).toByte() -> ""
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

    private fun onBindCustomLayoutViewHolder(holder: CustomLayoutViewHolder, position: Int) {
        val layoutItem = customLayouts[position]
        holder.nameTextView.text = layoutItem.name
        holder.itemView.setOnClickListener {
            onCustomLayoutItemClickListener?.invoke(position)
        }
    }

    fun updateHighlightPosition(newPosition: Int) {
        val previous = highlightedPosition
        highlightedPosition = newPosition
        if (previous != RecyclerView.NO_POSITION) {
            notifyItemChanged(previous)
        }
        notifyItemChanged(highlightedPosition)
    }
}
