package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.core.domain.extensions.isAllFullWidthNumericSymbol
import com.kazumaproject.core.domain.extensions.isAllHalfWidthNumericSymbol
import com.kazumaproject.core.domain.extensions.isDarkThemeOn
import com.kazumaproject.core.domain.extensions.setDrawableSolidColor
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.QWERTY_GLIDE_CANDIDATE_TYPE
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import com.kazumaproject.markdownhelperkeyboard.ime_service.measureDebugSection
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.correctReading
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.debugPrintCodePoints
import com.kazumaproject.markdownhelperkeyboard.ime_service.traceDebugSection
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal class CandidateItemColorState {
    var backgroundColor: Int? = null
        private set
    var pressedBackgroundColor: Int? = null
        private set

    fun setBackgroundColor(color: Int): Boolean {
        if (backgroundColor == color) return false
        backgroundColor = color
        return true
    }

    fun setPressedBackgroundColor(color: Int): Boolean {
        if (pressedBackgroundColor == color) return false
        pressedBackgroundColor = color
        return true
    }

    fun setColors(backgroundColor: Int, pressedBackgroundColor: Int): Boolean {
        if (
            this.backgroundColor == backgroundColor &&
            this.pressedBackgroundColor == pressedBackgroundColor
        ) {
            return false
        }
        this.backgroundColor = backgroundColor
        this.pressedBackgroundColor = pressedBackgroundColor
        return true
    }
}

internal data class CandidateYomiPresentation(
    val isVisible: Boolean,
    val text: String,
    val textSize: Float
)

internal fun resolveCandidateYomiPresentation(
    showCandidateYomiForLiveConversion: Boolean,
    isFirstCandidate: Boolean,
    suggestion: Candidate,
    candidateTextSize: Float
): CandidateYomiPresentation {
    val yomi = suggestion.yomi
    val shouldShowYomi =
        showCandidateYomiForLiveConversion &&
            isFirstCandidate &&
            !yomi.isNullOrBlank() &&
            yomi != suggestion.string
    return CandidateYomiPresentation(
        isVisible = shouldShowYomi,
        text = if (shouldShowYomi) yomi.orEmpty() else "",
        textSize = candidateTextSize * 0.72f
    )
}

class SuggestionAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_EMPTY = 0
        const val VIEW_TYPE_SUGGESTION = 1
        const val VIEW_TYPE_CUSTOM_LAYOUT_PICKER = 2
        const val VIEW_TYPE_GEMMA_ACTION = 3
        const val VIEW_TYPE_SHORTCUT = 4

        private val diffThreadIndex = AtomicInteger(0)
        private val diffExecutor: Executor = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "SuggestionAdapterDiff-${diffThreadIndex.incrementAndGet()}").apply {
                isDaemon = true
            }
        }
    }

    enum class HelperIcon {
        UNDO, REDO, RECONVERT, PASTE
    }

    private sealed class SuggestionDisplayItem {
        data class CandidateItem(
            val candidate: Candidate,
            val candidateIndex: Int,
        ) : SuggestionDisplayItem()

        data class GemmaActionItem(
            val candidate: Candidate,
            val candidateIndex: Int,
        ) : SuggestionDisplayItem()

        data class HelperActionsItem(
            val state: HelperActionsState,
        ) : SuggestionDisplayItem()

        data class ShortcutItem(
            val shortcutType: ShortcutType,
        ) : SuggestionDisplayItem()

        data class CustomLayoutItem(
            val layout: CustomKeyboardLayout,
            val layoutIndex: Int,
        ) : SuggestionDisplayItem()
    }

    private data class HelperActionsState(
        val undoEnabled: Boolean,
        val redoEnabled: Boolean,
        val reconvertEnabled: Boolean,
        val pasteEnabled: Boolean,
        val clipboardDescriptionShown: Boolean,
        val clipboardText: String,
        val clipboardBitmap: Bitmap?,
        val undoText: String,
        val redoText: String,
        val incognitoIconDrawable: android.graphics.drawable.Drawable?,
    ) {
        val hasClipboardPreview: Boolean
            get() = pasteEnabled && (clipboardBitmap != null || clipboardText.isNotBlank())

        val hasVisibleAction: Boolean
            get() = undoEnabled ||
                redoEnabled ||
                reconvertEnabled ||
                pasteEnabled ||
                incognitoIconDrawable != null
    }

    // Listeners for clicks
    private var onItemClickListener: ((Candidate, Int) -> Unit)? = null
    private var onItemLongClickListener: ((Candidate, Int) -> Unit)? = null
    private var onItemHelperIconClickListener: ((HelperIcon) -> Unit)? = null
    private var onItemHelperIconLongClickListener: ((HelperIcon) -> Unit)? = null
    private var onCustomLayoutItemClickListener: ((Int) -> Unit)? = null
    private var onShortcutItemClickListener: ((ShortcutType) -> Unit)? = null
    private var onShowSoftKeyboardClick: (() -> Unit)? = null

    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    var onListUpdated: (() -> Unit)? = null

    // Holds the preview content for the empty state.
    private var clipboardText: String = ""
    private var clipboardBitmap: Bitmap? = null // ★追加: Bitmapを保持するフィールド
    private var undoText: String = ""
    private var redoText: String = ""
    private var isReconvertEnabled: Boolean = false

    // Internal flags to track enable/disable state
    private var isUndoEnabled: Boolean = false
    private var isRedoEnabled: Boolean = false
    private var isPasteEnabled: Boolean = true
    private var isClipboardDescriptionShow: Boolean = true

    private var currentMode: TenKeyQWERTYMode = TenKeyQWERTYMode.Default
    private var customLayouts: List<CustomKeyboardLayout> = emptyList()

    private var showCustomTab: Boolean = true

    private var shortcutItems: List<ShortcutType> = emptyList()
    private var showIntegratedShortcuts: Boolean = false
    private var shortcutIconColor: Int? = null
    private var activeShortcutTypes: Set<ShortcutType> = emptySet()

    private var incognitoIconDrawable: android.graphics.drawable.Drawable? = null

    private var candidateTextSize: Float = 14f
    private var candidateTextColor: Int? = null
    private var showCandidateYomiForLiveConversion: Boolean = false
    private val candidateItemColorState = CandidateItemColorState()

    private var candidateEmptyDrawableColor: Int? = null
    private var candidateEmptyDrawableTextColor: Int? = null
    private var released: Boolean = false
    private var displayGeneration: Int = 0

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

    fun setOnShortcutItemClickListener(listener: (ShortcutType) -> Unit) {
        this.onShortcutItemClickListener = listener
    }

    fun setOnPhysicalKeyboardListener(listener: () -> Unit) {
        this.onShowSoftKeyboardClick = listener
    }

    fun release() {
        released = true
        onItemClickListener = null
        onItemLongClickListener = null
        onItemHelperIconClickListener = null
        onItemHelperIconLongClickListener = null
        onCustomLayoutItemClickListener = null
        onShortcutItemClickListener = null
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
        if (incognitoIconDrawable === drawable) return
        this.incognitoIconDrawable = drawable
        rebuildDisplayItems()
    }

    fun setUndoEnabled(enabled: Boolean) {
        if (isUndoEnabled == enabled) return
        isUndoEnabled = enabled
        rebuildDisplayItems()
    }

    fun setPasteEnabled(enabled: Boolean) {
        if (isPasteEnabled == enabled) return
        isPasteEnabled = enabled
        rebuildDisplayItems()
    }

    fun setRedoEnabled(enabled: Boolean) {
        if (isRedoEnabled == enabled) return
        isRedoEnabled = enabled
        rebuildDisplayItems()
    }

    fun setReconvertEnabled(enabled: Boolean) {
        if (isReconvertEnabled == enabled) return
        isReconvertEnabled = enabled
        rebuildDisplayItems()
    }

    fun setClipboardDescriptionTextVisibility(visibility: Boolean) {
        if (isClipboardDescriptionShow == visibility) return
        isClipboardDescriptionShow = visibility
        rebuildDisplayItems()
    }

    /**
     * テキストのクリップボードプレビューを設定します。
     * このとき、画像のプレビューはクリアされます。
     */
    fun setClipboardPreview(text: String) {
        if (clipboardText == text && clipboardBitmap == null) return
        clipboardText = text
        clipboardBitmap = null // ★追加: テキスト設定時に画像はクリア
        rebuildDisplayItems()
    }

    /**
     * ★新しい関数: 画像のクリップボードプレビューを設定します。
     * このとき、テキストのプレビューはクリアされます。
     */
    fun setClipboardImagePreview(bitmap: Bitmap?) {
        if (clipboardBitmap == bitmap && clipboardText.isEmpty()) return
        clipboardBitmap = bitmap
        clipboardText = "" // 画像設定時にテキストはクリア
        rebuildDisplayItems()
    }

    fun isShowingClipboardPreviewForEmptyState(): Boolean {
        return currentHelperActionsState().hasClipboardPreview
    }

    fun isShowingCustomLayoutPicker(): Boolean {
        return currentMode is TenKeyQWERTYMode.Custom && customLayouts.isNotEmpty() && showCustomTab
    }

    fun setShortcutItems(items: List<ShortcutType>) {
        if (shortcutItems == items) return
        shortcutItems = items
        rebuildDisplayItems()
    }

    fun setIntegratedShortcutVisibility(visible: Boolean) {
        if (showIntegratedShortcuts == visible) return
        showIntegratedShortcuts = visible
        rebuildDisplayItems()
    }

    fun setShortcutIconColor(color: Int) {
        if (shortcutIconColor == color) return
        shortcutIconColor = color
        if (showIntegratedShortcuts && suggestions.isEmpty()) {
            notifyItemRangeChanged(0, itemCount)
        }
    }

    fun setActiveShortcutTypes(activeTypes: Set<ShortcutType>) {
        if (activeShortcutTypes == activeTypes) return
        val oldActive = activeShortcutTypes
        activeShortcutTypes = activeTypes
        (oldActive union activeTypes).forEach { type ->
            notifyShortcutItemChanged(type)
        }
    }

    fun setKeyboardLayoutEditActive(active: Boolean) {
        setActiveShortcutTypes(
            if (active) {
                activeShortcutTypes + ShortcutType.KEYBOARD_LAYOUT_EDIT
            } else {
                activeShortcutTypes - ShortcutType.KEYBOARD_LAYOUT_EDIT
            }
        )
    }


    fun setUndoPreviewText(text: String) {
        if (undoText == text) return
        undoText = text
        rebuildDisplayItems()
    }

    fun setRedoPreviewText(text: String) {
        if (redoText == text) return
        redoText = text
        rebuildDisplayItems()
    }

    fun updateState(mode: TenKeyQWERTYMode, layouts: List<CustomKeyboardLayout>) {
        val needsFullRefresh = (currentMode != mode) || (customLayouts != layouts)
        currentMode = mode
        customLayouts = layouts
        if (needsFullRefresh) {
            rebuildDisplayItems()
        }
    }

    fun updateCustomTabVisibility(visibility: Boolean) {
        if (showCustomTab == visibility) return
        showCustomTab = visibility
        rebuildDisplayItems()
    }

    private var candidateSuggestions: List<Candidate> = emptyList()
    private val displayItemCallback = object : DiffUtil.ItemCallback<SuggestionDisplayItem>() {
        override fun areItemsTheSame(
            oldItem: SuggestionDisplayItem,
            newItem: SuggestionDisplayItem
        ): Boolean {
            return when {
                oldItem is SuggestionDisplayItem.CandidateItem &&
                    newItem is SuggestionDisplayItem.CandidateItem ->
                    oldItem.candidateIndex == newItem.candidateIndex &&
                        oldItem.candidate.string == newItem.candidate.string &&
                        oldItem.candidate.type == newItem.candidate.type

                oldItem is SuggestionDisplayItem.GemmaActionItem &&
                    newItem is SuggestionDisplayItem.GemmaActionItem ->
                    oldItem.candidateIndex == newItem.candidateIndex &&
                        oldItem.candidate.string == newItem.candidate.string &&
                        oldItem.candidate.type == newItem.candidate.type

                oldItem is SuggestionDisplayItem.HelperActionsItem &&
                    newItem is SuggestionDisplayItem.HelperActionsItem -> true

                oldItem is SuggestionDisplayItem.ShortcutItem &&
                    newItem is SuggestionDisplayItem.ShortcutItem ->
                    oldItem.shortcutType == newItem.shortcutType

                oldItem is SuggestionDisplayItem.CustomLayoutItem &&
                    newItem is SuggestionDisplayItem.CustomLayoutItem ->
                    oldItem.layout.stableId == newItem.layout.stableId

                else -> false
            }
        }

        override fun areContentsTheSame(
            oldItem: SuggestionDisplayItem,
            newItem: SuggestionDisplayItem
        ): Boolean = oldItem == newItem
    }

    private val displayListUpdateCallback = object : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) {
            if (!released) notifyItemRangeInserted(position, count)
        }

        override fun onRemoved(position: Int, count: Int) {
            if (!released) notifyItemRangeRemoved(position, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            if (!released) notifyItemMoved(fromPosition, toPosition)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            if (!released) notifyItemRangeChanged(position, count, payload)
        }
    }

    private val differ = AsyncListDiffer(
        displayListUpdateCallback,
        AsyncDifferConfig.Builder(displayItemCallback)
            .setBackgroundThreadExecutor { command ->
                diffExecutor.execute {
                    measureDebugSection("SuggestionAdapter.DiffUtil.calculateDiff") {
                        command.run()
                    }
                }
            }
            .build()
    )

    private val displayItems: List<SuggestionDisplayItem>
        get() = differ.currentList

    var suggestions: List<Candidate>
        get() = candidateSuggestions
        set(value) {
            traceDebugSection("SuggestionAdapter.suggestions.set") {
                if (candidateSuggestions == value) return

                candidateSuggestions = value
                rebuildDisplayItems {
                    onListUpdated?.invoke()
                }
            }
        }

    private var highlightedPosition: Int = RecyclerView.NO_POSITION

    init {
        differ.submitList(buildDisplayItems())
    }

    private fun rebuildDisplayItems(onCommitted: (() -> Unit)? = null) {
        if (released) return

        measureDebugSection("SuggestionAdapter.rebuildDisplayItems") {
            val newItems = buildDisplayItems()
            if (displayItems == newItems) return@measureDebugSection

            val generation = ++displayGeneration
            differ.submitList(newItems) {
                if (released || generation != displayGeneration) return@submitList
                onCommitted?.invoke()
            }
        }
    }

    private fun buildDisplayItems(): List<SuggestionDisplayItem> {
        if (candidateSuggestions.isNotEmpty()) {
            return candidateSuggestions.mapIndexed { index, candidate ->
                if (candidate.isSelectedTextGemmaActionCandidate()) {
                    SuggestionDisplayItem.GemmaActionItem(candidate, index)
                } else {
                    SuggestionDisplayItem.CandidateItem(candidate, index)
                }
            }
        }

        if (isShowingCustomLayoutPicker()) {
            return customLayouts.mapIndexed { index, layout ->
                SuggestionDisplayItem.CustomLayoutItem(layout, index)
            }
        }

        return buildList {
            val helperState = currentHelperActionsState()
            if (helperState.hasVisibleAction) {
                add(SuggestionDisplayItem.HelperActionsItem(helperState))
            }
            if (shouldShowIntegratedShortcuts()) {
                shortcutItems.forEach { shortcutType ->
                    add(SuggestionDisplayItem.ShortcutItem(shortcutType))
                }
            }
        }
    }

    private fun currentHelperActionsState(): HelperActionsState =
        HelperActionsState(
            undoEnabled = isUndoEnabled,
            redoEnabled = isRedoEnabled,
            reconvertEnabled = isReconvertEnabled,
            pasteEnabled = isPasteEnabled,
            clipboardDescriptionShown = isClipboardDescriptionShow,
            clipboardText = clipboardText,
            clipboardBitmap = clipboardBitmap,
            undoText = undoText,
            redoText = redoText,
            incognitoIconDrawable = incognitoIconDrawable,
        )

    inner class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: MaterialTextView = itemView.findViewById(R.id.suggestion_item_text_view)
        val yomiText: MaterialTextView = itemView.findViewById(R.id.suggestion_item_yomi_text_view)
        val typeText: MaterialTextView = itemView.findViewById(R.id.suggestion_item_type_text_view)
    }

    inner class GemmaActionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val badgeText: MaterialTextView = itemView.findViewById(R.id.suggestion_gemma_action_badge)
        val actionText: MaterialTextView = itemView.findViewById(R.id.suggestion_gemma_action_text)
    }

    inner class EmptyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val undoIconParent: ConstraintLayout? = itemView.findViewById(R.id.undo_icon_parent)
        val undoImageView: ImageView? = itemView.findViewById(R.id.imageView)
        val undoIcon: MaterialTextView? = itemView.findViewById(R.id.undo_icon)
        val redoIconParent: ConstraintLayout? = itemView.findViewById(R.id.redo_icon_parent)
        val redoImageView: ImageView? = itemView.findViewById(R.id.redo_image_view)
        val redoIcon: MaterialTextView? = itemView.findViewById(R.id.redo_icon)
        val reconvertIconParent: ConstraintLayout? = itemView.findViewById(R.id.reconvert_icon_parent)
        val reconvertIcon: MaterialTextView? = itemView.findViewById(R.id.reconvert_icon)
        val reconvertImageView: ImageView? = itemView.findViewById(R.id.reconvert_image_view)
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

    inner class ShortcutViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.item_image)
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayItems[position]) {
            is SuggestionDisplayItem.CandidateItem -> VIEW_TYPE_SUGGESTION
            is SuggestionDisplayItem.GemmaActionItem -> VIEW_TYPE_GEMMA_ACTION
            is SuggestionDisplayItem.HelperActionsItem -> VIEW_TYPE_EMPTY
            is SuggestionDisplayItem.ShortcutItem -> VIEW_TYPE_SHORTCUT
            is SuggestionDisplayItem.CustomLayoutItem -> VIEW_TYPE_CUSTOM_LAYOUT_PICKER
        }
    }

    override fun getItemCount(): Int {
        return displayItems.size
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

            VIEW_TYPE_GEMMA_ACTION -> {
                val itemView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.suggestion_gemma_action_item, parent, false)
                itemView.setBackgroundResource(
                    if (isDynamicColorEnable) com.kazumaproject.core.R.drawable.recyclerview_item_bg_material else com.kazumaproject.core.R.drawable.recyclerview_item_bg
                )
                GemmaActionViewHolder(itemView)
            }

            VIEW_TYPE_SHORTCUT -> {
                val itemView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_shortcut, parent, false)
                ShortcutViewHolder(itemView)
            }

            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = displayItems.getOrNull(position) ?: return
        when (holder.itemViewType) {
            VIEW_TYPE_EMPTY -> onBindEmptyViewHolder(
                holder as EmptyViewHolder,
                (item as SuggestionDisplayItem.HelperActionsItem).state,
            )
            VIEW_TYPE_SUGGESTION -> onBindSuggestionViewHolder(
                holder as SuggestionViewHolder,
                item as SuggestionDisplayItem.CandidateItem,
            )
            VIEW_TYPE_GEMMA_ACTION -> onBindGemmaActionViewHolder(
                holder as GemmaActionViewHolder,
                item as SuggestionDisplayItem.GemmaActionItem,
            )

            VIEW_TYPE_SHORTCUT -> onBindShortcutViewHolder(
                holder as ShortcutViewHolder,
                item as SuggestionDisplayItem.ShortcutItem,
            )

            VIEW_TYPE_CUSTOM_LAYOUT_PICKER -> onBindCustomLayoutViewHolder(
                holder as CustomLayoutViewHolder,
                item as SuggestionDisplayItem.CustomLayoutItem,
            )
        }
    }

    private fun onBindEmptyViewHolder(holder: EmptyViewHolder, state: HelperActionsState) {
        val isDynamicColorEnable = DynamicColors.isDynamicColorAvailable()
        Timber.d("SuggestionAdapter onBindEmptyViewHolder: ${state.clipboardText} ${state.pasteEnabled}")
        holder.apply {
            incognitoIcon?.apply {
                if (state.incognitoIconDrawable != null) {
                    visibility = View.VISIBLE
                    setImageDrawable(state.incognitoIconDrawable)
                } else {
                    visibility = View.GONE
                }
            }

            undoIcon?.apply {
                isVisible = state.undoEnabled
                isFocusable = false
                Timber.d("undo text: ${state.undoText}")
                debugPrintCodePoints(state.undoText)
                text = state.undoText
            }
            redoIcon?.apply {
                isVisible = state.redoEnabled
                isFocusable = false
                text = state.redoText
            }
            reconvertIcon?.apply {
                isVisible = state.reconvertEnabled
                isFocusable = false
            }
            pasteIconParent?.apply {
                isEnabled = state.pasteEnabled
                visibility = if (state.pasteEnabled) View.VISIBLE else View.GONE
                isFocusable = false
            }

            pasteIcon?.apply {
                if (state.clipboardBitmap != null) {
                    setImageBitmap(state.clipboardBitmap)
                    clearColorFilter()
                    scaleType = ImageView.ScaleType.CENTER_CROP
                } else {
                    setImageResource(com.kazumaproject.core.R.drawable.content_paste_24px)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
            }

            clipboardPreviewText?.text =
                if (state.clipboardBitmap == null) state.clipboardText else ""

            applyEmptyHelperButtonStyle(
                parent = undoIconParent,
                text = undoIcon,
                icon = undoImageView,
                isDynamicColorEnable = isDynamicColorEnable,
            )
            applyEmptyHelperButtonStyle(
                parent = redoIconParent,
                text = redoIcon,
                icon = redoImageView,
                isDynamicColorEnable = isDynamicColorEnable,
            )
            applyEmptyHelperButtonStyle(
                parent = reconvertIconParent,
                text = reconvertIcon,
                icon = reconvertImageView,
                isDynamicColorEnable = isDynamicColorEnable,
            )
            applyEmptyHelperButtonStyle(
                parent = pasteIconParent,
                text = clipboardPreviewText,
                icon = if (state.clipboardBitmap == null) pasteIcon else null,
                isDynamicColorEnable = isDynamicColorEnable,
            )
            applyEmptyHelperTextColor(clipboardPreviewTextDescription)

            undoIconParent?.apply {
                isVisible = state.undoEnabled
                setOnClickListener {
                    onItemHelperIconClickListener?.invoke(HelperIcon.UNDO)
                }
                setOnLongClickListener {
                    onItemHelperIconLongClickListener?.invoke(HelperIcon.UNDO)
                    true
                }
            }

            redoIconParent?.apply {
                isVisible = state.redoEnabled
                setOnClickListener {
                    onItemHelperIconClickListener?.invoke(HelperIcon.REDO)
                }
                setOnLongClickListener {
                    onItemHelperIconLongClickListener?.invoke(HelperIcon.REDO)
                    true
                }
            }

            reconvertIconParent?.apply {
                isVisible = state.reconvertEnabled
                setOnClickListener {
                    onItemHelperIconClickListener?.invoke(HelperIcon.RECONVERT)
                }
                setOnLongClickListener {
                    false
                }
            }

            clipboardPreviewTextDescription?.isVisible = false

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

    private fun applyEmptyHelperButtonStyle(
        parent: ConstraintLayout?,
        text: MaterialTextView?,
        icon: ImageView?,
        isDynamicColorEnable: Boolean,
    ) {
        applyEmptyHelperButtonBackground(parent, isDynamicColorEnable)
        applyEmptyHelperTextColor(text)
        candidateEmptyDrawableTextColor?.let { color ->
            icon?.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        } ?: icon?.clearColorFilter()
    }

    private fun applyEmptyHelperTextColor(text: MaterialTextView?) {
        text ?: return
        text.setTextColor(
            candidateEmptyDrawableTextColor ?: ContextCompat.getColor(
                text.context,
                com.kazumaproject.core.R.color.keyboard_icon_color,
            )
        )
    }

    private fun applyEmptyHelperButtonBackground(
        parent: ConstraintLayout?,
        isDynamicColorEnable: Boolean,
    ) {
        parent ?: return
        val customBackgroundColor = candidateEmptyDrawableColor
        if (customBackgroundColor != null) {
            parent.setBackgroundResource(com.kazumaproject.core.R.drawable.ten_keys_center_bg)
            parent.setDrawableSolidColor(customBackgroundColor)
            return
        }
        if (isDynamicColorEnable) {
            parent.setBackgroundResource(
                if (parent.context.isDarkThemeOn()) {
                    com.kazumaproject.core.R.drawable.ten_keys_side_bg_material
                } else {
                    com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light
                }
            )
        } else {
            parent.setBackgroundResource(com.kazumaproject.core.R.drawable.ten_keys_center_bg)
        }
    }

    private fun shouldShowIntegratedShortcuts(): Boolean {
        return showIntegratedShortcuts &&
            shortcutItems.isNotEmpty() &&
            !isShowingCustomLayoutPicker() &&
            !isShowingClipboardPreviewForEmptyState()
    }

    private fun onBindShortcutViewHolder(
        holder: ShortcutViewHolder,
        item: SuggestionDisplayItem.ShortcutItem,
    ) {
        val shortcutType = item.shortcutType
        holder.imageView.apply {
            setImageResource(shortcutType.resolveShortcutIconResId())
            contentDescription = shortcutType.description
            shortcutIconColor?.let { color ->
                setColorFilter(color, PorterDuff.Mode.SRC_IN)
            } ?: clearColorFilter()
        }
        holder.itemView.contentDescription = shortcutType.description
        holder.itemView.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                val currentItem = displayItems.getOrNull(adapterPosition)
                if (currentItem is SuggestionDisplayItem.ShortcutItem) {
                    onShortcutItemClickListener?.invoke(currentItem.shortcutType)
                }
            }
        }
    }

    private fun ShortcutType.resolveShortcutIconResId(): Int {
        return if (this in activeShortcutTypes) {
            activeIconResId ?: iconResId
        } else {
            iconResId
        }
    }

    private fun notifyShortcutItemChanged(shortcutType: ShortcutType) {
        val index = displayItems.indexOfFirst {
            it is SuggestionDisplayItem.ShortcutItem && it.shortcutType == shortcutType
        }
        if (index >= 0) {
            notifyItemChanged(index)
        }
    }

    fun setCandidateTextSize(size: Float) {
        if (candidateTextSize == size) return
        candidateTextSize = size
        notifyItemRangeChanged(0, itemCount)
    }

    fun setShowCandidateYomiForLiveConversion(enabled: Boolean) {
        if (showCandidateYomiForLiveConversion == enabled) return
        showCandidateYomiForLiveConversion = enabled
        notifyItemRangeChanged(0, itemCount)
    }

    fun setCandidateTextColor(color: Int) {
        if (candidateTextColor == color) return
        candidateTextColor = color
        // 全アイテムを更新して色を反映させる
        notifyItemRangeChanged(0, itemCount)
    }

    fun setCandidateItemBackgroundColor(color: Int) {
        if (!candidateItemColorState.setBackgroundColor(color)) return
        notifyItemRangeChanged(0, itemCount)
    }

    fun setCandidateItemPressedBackgroundColor(color: Int) {
        if (!candidateItemColorState.setPressedBackgroundColor(color)) return
        notifyItemRangeChanged(0, itemCount)
    }

    fun setCandidateItemColors(backgroundColor: Int, pressedColor: Int) {
        if (!candidateItemColorState.setColors(backgroundColor, pressedColor)) return
        notifyItemRangeChanged(0, itemCount)
    }

    fun setCandidateEmptyDrawableColor(color: Int) {
        if (candidateEmptyDrawableColor == color) return
        candidateEmptyDrawableColor = color
        // 全アイテムを更新して色を反映させる
        notifyItemRangeChanged(0, itemCount)
    }

    fun setCandidateEmptyDrawableTextColor(color: Int) {
        if (candidateEmptyDrawableTextColor == color) return
        candidateEmptyDrawableTextColor = color
        // 全アイテムを更新して色を反映させる
        notifyItemRangeChanged(0, itemCount)
    }

    fun setCandidateEmptyPopupColors(backgroundColor: Int, textColor: Int) {
        if (
            candidateEmptyDrawableColor == backgroundColor &&
            candidateEmptyDrawableTextColor == textColor
        ) {
            return
        }
        candidateEmptyDrawableColor = backgroundColor
        candidateEmptyDrawableTextColor = textColor
        notifyItemRangeChanged(0, itemCount)
    }

    fun clearCandidateEmptyPopupColors() {
        if (candidateEmptyDrawableColor == null && candidateEmptyDrawableTextColor == null) return
        candidateEmptyDrawableColor = null
        candidateEmptyDrawableTextColor = null
        notifyItemRangeChanged(0, itemCount)
    }

    private fun onBindSuggestionViewHolder(
        holder: SuggestionViewHolder,
        item: SuggestionDisplayItem.CandidateItem,
    ) {
        applyCandidateItemBackground(holder.itemView)
        val suggestion = item.candidate
        val position = item.candidateIndex
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

        holder.text.textSize = candidateTextSize
        val yomiPresentation = resolveCandidateYomiPresentation(
            showCandidateYomiForLiveConversion = showCandidateYomiForLiveConversion,
            isFirstCandidate = position == 0,
            suggestion = suggestion,
            candidateTextSize = candidateTextSize
        )
        holder.yomiText.isVisible = yomiPresentation.isVisible
        holder.yomiText.text = yomiPresentation.text
        holder.yomiText.textSize = yomiPresentation.textSize
        holder.yomiText.translationX = if (yomiPresentation.isVisible) {
            holder.text.paint.measureText(" ".repeat(paddingLength))
        } else {
            0f
        }

        candidateTextColor?.let { color ->
            holder.text.setTextColor(color)
            // 必要であれば typeText（[半]などの補足テキスト）にも同じ色、またはその色の薄い版などを適用
            holder.typeText.setTextColor(color)
            holder.yomiText.setTextColor(color)
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
            /** Zenz **/
            (33).toByte() -> "[AI]"
            (34).toByte() -> "[履歴]"
            /** Typo Correction QWERTY **/
            (35).toByte() -> "[修正]"

            (36).toByte() -> ""
            (37).toByte() -> "[AI]"
            (38).toByte() -> ""
            (39).toByte() -> ""
            (40).toByte() -> "[AI]"
            QWERTY_GLIDE_CANDIDATE_TYPE -> ""
            GemmaTranslationManager.TRANSLATED_CANDIDATE_TYPE.toByte() -> "[訳]"
            GemmaTranslationManager.PROMPT_RESULT_CANDIDATE_TYPE.toByte() -> "[AI]"
            GemmaTranslationManager.SELECTION_TRANSLATE_ACTION_CANDIDATE_TYPE.toByte() -> "[訳]"
            GemmaTranslationManager.SELECTION_PROMPT_ACTION_CANDIDATE_TYPE.toByte() -> "[AI]"
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

    private fun onBindGemmaActionViewHolder(
        holder: GemmaActionViewHolder,
        item: SuggestionDisplayItem.GemmaActionItem,
    ) {
        applyCandidateItemBackground(holder.itemView)
        val suggestion = item.candidate
        val position = item.candidateIndex
        holder.actionText.text = suggestion.string
        holder.actionText.textSize = candidateTextSize
        holder.badgeText.text = when (suggestion.type) {
            GemmaTranslationManager.SELECTION_TRANSLATE_ACTION_CANDIDATE_TYPE.toByte() -> "訳"
            GemmaTranslationManager.SELECTION_PROMPT_ACTION_CANDIDATE_TYPE.toByte() -> "AI"
            else -> ""
        }

        candidateTextColor?.let { color ->
            holder.actionText.setTextColor(color)
            holder.badgeText.setTextColor(color)
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

    private fun onBindCustomLayoutViewHolder(
        holder: CustomLayoutViewHolder,
        item: SuggestionDisplayItem.CustomLayoutItem,
    ) {
        holder.nameTextView.text = item.layout.name
        holder.itemView.setOnClickListener {
            onCustomLayoutItemClickListener?.invoke(item.layoutIndex)
        }
    }

    private fun applyCandidateItemBackground(itemView: View) {
        val backgroundColor = candidateItemColorState.backgroundColor
        val pressedColor = candidateItemColorState.pressedBackgroundColor
        if (backgroundColor == null && pressedColor == null) {
            itemView.setBackgroundResource(defaultCandidateItemBackgroundRes())
            return
        }

        itemView.background = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                createCandidateItemDrawable(
                    pressedColor ?: ContextCompat.getColor(
                        itemView.context,
                        com.kazumaproject.core.R.color.qwety_key_bg_color
                    ),
                    itemView.context.resources.displayMetrics.density
                )
            )
            addState(
                intArrayOf(),
                createCandidateItemDrawable(
                    backgroundColor ?: Color.TRANSPARENT,
                    itemView.context.resources.displayMetrics.density
                )
            )
        }
    }

    private fun defaultCandidateItemBackgroundRes(): Int {
        return if (DynamicColors.isDynamicColorAvailable()) {
            com.kazumaproject.core.R.drawable.recyclerview_item_bg_material
        } else {
            com.kazumaproject.core.R.drawable.recyclerview_item_bg
        }
    }

    private fun createCandidateItemDrawable(color: Int, density: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = 16f * density
        }
    }

    fun updateHighlightPosition(newPosition: Int) {
        val previous = highlightedPosition
        highlightedPosition = newPosition
        if (previous != RecyclerView.NO_POSITION) {
            notifyCandidateDisplayItemChanged(previous)
        }
        if (highlightedPosition != RecyclerView.NO_POSITION) {
            notifyCandidateDisplayItemChanged(highlightedPosition)
        }
    }

    private fun notifyCandidateDisplayItemChanged(candidateIndex: Int) {
        val displayIndex = displayItems.indexOfFirst { item ->
            when (item) {
                is SuggestionDisplayItem.CandidateItem -> item.candidateIndex == candidateIndex
                is SuggestionDisplayItem.GemmaActionItem -> item.candidateIndex == candidateIndex
                else -> false
            }
        }
        if (displayIndex != -1) {
            notifyItemChanged(displayIndex)
        }
    }

    private fun Candidate.isSelectedTextGemmaActionCandidate(): Boolean {
        return type == GemmaTranslationManager.SELECTION_TRANSLATE_ACTION_CANDIDATE_TYPE.toByte() ||
            type == GemmaTranslationManager.SELECTION_PROMPT_ACTION_CANDIDATE_TYPE.toByte()
    }

}
