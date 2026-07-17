package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.view

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.View.OnDragListener
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import com.kazumaproject.core.domain.extensions.isDarkThemeOn
import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyIconResolver
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyVisualStyleResolver
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.KeyboardLayoutItem
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.layout.SegmentedBackgroundDrawable
import com.kazumaproject.custom_keyboard.view.AutoSizeButton
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.canonicalLayoutForEditor
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.columnDeleteSpecs
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.displayPlacement
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.EditorGridBounds
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.editorGridBounds
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.InsertionPolicy
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.InsertionTarget
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.InsertionTargetMapper
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.PlacementCursor
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.rowDeleteSpecs
import com.google.android.material.R as MaterialR

@SuppressLint("ClickableViewAccessibility")
class EditableFlickKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : GridLayout(context, attrs, defStyleAttr) {

    enum class EditTargetMode {
        CUSTOM_KEYBOARD,
        SUMIRE_SPECIAL_KEYS
    }

    // ▼▼▼ インターフェースに削除イベントを追加 ▼▼▼
    interface OnKeyEditListener {
        fun onKeySelected(keyId: String)
        fun onSpacerSelected(spacerId: String)
        fun onKeysSwapped(draggedKeyId: String, targetKeyId: String)
        fun onRowDeleted(rowIndex: Int)
        fun onColumnDeleted(columnIndex: Int)
        fun onPlacementPointerTarget(target: InsertionTarget)
        fun onPlacementTapTarget(target: InsertionTarget)
        fun onPlacementDropTarget(target: InsertionTarget)
    }
    // ▲▲▲ インターフェースに削除イベントを追加 ▲▲▲

    private var listener: OnKeyEditListener? = null
    private val insertionTargetMapper = InsertionTargetMapper()
    private var currentLayout: KeyboardLayout? = null
    private var placementMode: Boolean = false
    private var currentInsertionPolicy: InsertionPolicy = InsertionPolicy.PreferHorizontal
    private var editTargetMode: EditTargetMode = EditTargetMode.CUSTOM_KEYBOARD

    fun setOnKeyEditListener(listener: OnKeyEditListener?) {
        this.listener = listener
    }

    fun removeOnKeyEditListener() {
        this.listener = null
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setKeyboard(
        layout: KeyboardLayout,
        placementMode: Boolean = false,
        placementCursor: PlacementCursor? = null,
        insertionPolicy: InsertionPolicy = placementCursor?.policy ?: InsertionPolicy.PreferHorizontal,
        selectedItemId: String? = null,
        previewInsertedItemId: String? = null,
        previewMovedItemIds: Set<String> = emptySet(),
        editTargetMode: EditTargetMode = EditTargetMode.CUSTOM_KEYBOARD,
        showRowColumnDeleteButtons: Boolean = true
    ) {
        this.removeAllViews()
        this.editTargetMode = editTargetMode
        val displayLayout = when (editTargetMode) {
            EditTargetMode.CUSTOM_KEYBOARD -> layout.canonicalLayoutForEditor(placementCursor)
            EditTargetMode.SUMIRE_SPECIAL_KEYS -> layout
        }
        this.currentLayout = displayLayout
        this.placementMode = placementMode && editTargetMode == EditTargetMode.CUSTOM_KEYBOARD
        this.currentInsertionPolicy = placementCursor?.policy ?: insertionPolicy

        val editorBounds = displayLayout.editorGridBounds()
        this.columnCount = if (editTargetMode == EditTargetMode.SUMIRE_SPECIAL_KEYS) {
            displayLayout.columnUnitCount
        } else {
            editorBounds.gridColumnCount
        }
        this.rowCount = if (editTargetMode == EditTargetMode.SUMIRE_SPECIAL_KEYS) {
            displayLayout.rowUnitCount
        } else {
            editorBounds.gridRowCount
        }

        this.isFocusable = false

        val dragListener = createDragListener()
        setPlacementTouchListener()
        if (
            editTargetMode == EditTargetMode.CUSTOM_KEYBOARD &&
            !editorBounds.showRowColumnDeleteChrome
        ) {
            addFlexibleRowUnitAnchors(editorBounds)
        }

        // キーの描画
        if (displayLayout.items.isNotEmpty()) {
            displayLayout.items.forEach { item ->
                when (item) {
                    is KeyItem -> addKeyItem(item, dragListener, selectedItemId, previewInsertedItemId, previewMovedItemIds)
                    is SpacerItem -> addSpacerItem(item, selectedItemId, previewInsertedItemId, previewMovedItemIds)
                }
            }
        } else {
            displayLayout.keys.forEach { keyData ->
                addKeyItem(
                    KeyItem(
                        id = keyData.keyId ?: "legacy_${keyData.row}_${keyData.column}_${keyData.label}",
                        keyData = keyData,
                        placement = GridPlacement(
                            rowUnits = keyData.row,
                            columnUnits = keyData.column,
                            rowSpanUnits = keyData.rowSpan,
                            columnSpanUnits = keyData.colSpan
                        )
                    ),
                    dragListener,
                    selectedItemId,
                    previewInsertedItemId,
                    previewMovedItemIds
                )
            }
        }

        if (editTargetMode == EditTargetMode.CUSTOM_KEYBOARD) {
            addInsertionCursor(displayLayout, placementCursor)
        }

        if (editTargetMode != EditTargetMode.CUSTOM_KEYBOARD) return
        if (!showRowColumnDeleteButtons) return

        editorBounds.rowDeleteSpecs(displayLayout.rowCount).forEachIndexed { i, rowSpecBounds ->
            val deleteButton = createDeleteButton { listener?.onRowDeleted(i) }
            val params = LayoutParams().apply {
                rowSpec = spec(rowSpecBounds.startUnits, rowSpecBounds.spanUnits, FILL, 1f)
                columnSpec = spec(0, editorBounds.columnOffsetUnits, FILL, 0.5f)
                width = 0
                height = 0
                setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            }
            deleteButton.layoutParams = params
            this.addView(deleteButton)
        }

        editorBounds.columnDeleteSpecs(displayLayout.columnCount).forEachIndexed { i, columnSpecBounds ->
            val deleteButton = createDeleteButton { listener?.onColumnDeleted(i) }
            val params = LayoutParams().apply {
                rowSpec = spec(0, editorBounds.rowOffsetUnits, FILL, 0.5f)
                columnSpec = spec(columnSpecBounds.startUnits, columnSpecBounds.spanUnits, FILL, 1f)
                width = 0
                height = 0
                setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            }
            deleteButton.layoutParams = params
            this.addView(deleteButton)
        }
    }

    private fun addKeyItem(
        item: KeyItem,
        dragListener: OnDragListener,
        selectedItemId: String?,
        previewInsertedItemId: String?,
        previewMovedItemIds: Set<String>
    ) {
        val keyData = item.keyData
        val keyView: View = createKeyView(keyData, item.placement)
        val offsetUnits = if (editTargetMode == EditTargetMode.CUSTOM_KEYBOARD) 2 else 0
        keyView.layoutParams = createLayoutParams(
            item.placement,
            rowOffsetUnits = offsetUnits,
            columnOffsetUnits = offsetUnits
        )
        val editableSumireKeyId = keyData.keyId
            ?.takeIf { keyData.isSpecialKey && it.isNotBlank() }
        if (editTargetMode == EditTargetMode.SUMIRE_SPECIAL_KEYS) {
            if (editableSumireKeyId != null) {
                keyView.tag = editableSumireKeyId
                keyView.setOnDragListener(dragListener)
                keyView.setOnClickListener { listener?.onKeySelected(editableSumireKeyId) }
                keyView.setOnLongClickListener { view ->
                    val clipText = "keyId:$editableSumireKeyId"
                    val data = ClipData(
                        clipText,
                        arrayOf("text/plain"),
                        ClipData.Item(clipText)
                    )
                    view.startDragAndDrop(data, DragShadowBuilder(view), view, 0)
                    true
                }
            } else {
                keyView.isClickable = false
                keyView.isLongClickable = false
            }
        } else {
            // Source of truth for flexible-layout selection / drag-swap is
            // KeyboardLayoutItem.id. KeyData.keyId is intentionally NOT used here
            // because half-cell keys / spacers may have a different (or null) keyId.
            keyView.tag = item.id
            keyView.setOnDragListener(dragListener)
            keyView.setOnClickListener {
                if (placementMode) {
                    mapTargetFromCenter(item)?.let { target -> listener?.onPlacementTapTarget(target) }
                } else {
                    listener?.onKeySelected(item.id)
                }
            }
            keyView.setOnLongClickListener { view ->
                if (placementMode) return@setOnLongClickListener true
                val clipText = "keyId:${item.id}"
                val clipItem = ClipData.Item(clipText)
                val mimeTypes = arrayOf("text/plain")
                val data = ClipData(clipText, mimeTypes, clipItem)
                val dragShadow = DragShadowBuilder(view)
                view.startDragAndDrop(data, dragShadow, view, 0)
                true
            }
        }
        decoratePreviewItem(keyView, item, selectedItemId, previewInsertedItemId, previewMovedItemIds)
        this.addView(keyView)
    }

    private fun addSpacerItem(
        item: SpacerItem,
        selectedItemId: String?,
        previewInsertedItemId: String?,
        previewMovedItemIds: Set<String>
    ) {
        val spacerView = TextView(context).apply {
            isClickable = true
            isFocusable = false
            text = ""
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(4).toFloat()
                setColor(Color.argb(28, 96, 125, 139))
                setStroke(dpToPx(1), Color.argb(150, 96, 125, 139), dpToPx(4).toFloat(), dpToPx(3).toFloat())
            }
            contentDescription = "Spacer"
            if (editTargetMode == EditTargetMode.CUSTOM_KEYBOARD) {
                setOnClickListener {
                    if (placementMode) {
                        mapTargetFromCenter(item)?.let { target -> listener?.onPlacementTapTarget(target) }
                    } else {
                        listener?.onSpacerSelected(item.id)
                    }
                }
            } else {
                isClickable = false
            }
        }
        val offsetUnits = if (editTargetMode == EditTargetMode.CUSTOM_KEYBOARD) 2 else 0
        spacerView.layoutParams = createLayoutParams(
            item.placement,
            rowOffsetUnits = offsetUnits,
            columnOffsetUnits = offsetUnits
        )
        decoratePreviewItem(spacerView, item, selectedItemId, previewInsertedItemId, previewMovedItemIds)
        this.addView(spacerView)
    }

    private fun decoratePreviewItem(
        view: View,
        item: KeyboardLayoutItem,
        selectedItemId: String?,
        previewInsertedItemId: String?,
        previewMovedItemIds: Set<String>
    ) {
        when {
            item.id == previewInsertedItemId -> {
                view.alpha = 0.76f
                view.foreground = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.TRANSPARENT)
                    setStroke(dpToPx(2), Color.rgb(0, 150, 136))
                }
            }
            item.id in previewMovedItemIds -> {
                view.alpha = 0.86f
                view.foreground = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.TRANSPARENT)
                    setStroke(dpToPx(2), Color.rgb(255, 152, 0))
                }
            }
            selectedItemId != null && item.matchesSelectedItemId(selectedItemId) -> {
                view.foreground = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.TRANSPARENT)
                    setStroke(dpToPx(2), Color.rgb(33, 150, 243))
                }
            }
        }
    }

    private fun KeyboardLayoutItem.matchesSelectedItemId(selectedId: String): Boolean =
        id == selectedId || (this is KeyItem && keyData.keyId == selectedId)

    // ▼▼▼ 削除ボタンを生成するヘルパー関数を追加 ▼▼▼
    private fun createDeleteButton(onClick: () -> Unit): ImageButton {
        return ImageButton(
            context,
            null,
            0,
            androidx.appcompat.R.style.Widget_AppCompat_ImageButton
        ).apply {
            setImageResource(com.kazumaproject.core.R.drawable.remove)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { onClick() }
        }
    }
    // ▲▲▲ 削除ボタンを生成するヘルパー関数を追加 ▲▲▲

    private fun createDragListener(): OnDragListener {
        return OnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> {
                    view.alpha = 0.5f
                    true
                }

                DragEvent.ACTION_DRAG_EXITED -> {
                    view.alpha = 1.0f
                    true
                }

                DragEvent.ACTION_DROP -> {
                    if (placementMode) {
                        mapTarget(event.x, event.y)?.let { listener?.onPlacementDropTarget(it) }
                        return@OnDragListener true
                    }
                    val targetKeyId = view.tag as? String ?: return@OnDragListener false
                    if (editTargetMode == EditTargetMode.SUMIRE_SPECIAL_KEYS) {
                        currentLayout
                            ?.items
                            ?.filterIsInstance<KeyItem>()
                            ?.firstOrNull {
                                it.keyData.isSpecialKey &&
                                        it.keyData.keyId?.takeIf { id -> id.isNotBlank() } == targetKeyId
                            } ?: return@OnDragListener false
                    }
                    val item = event.clipData.getItemAt(0)
                    val draggedKeyId = item.text.toString().removePrefix("keyId:")
                    if (draggedKeyId != targetKeyId) {
                        listener?.onKeysSwapped(draggedKeyId, targetKeyId)
                    }
                    true
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    view.alpha = 1.0f
                    true
                }

                else -> false
            }
        }
    }

    private fun setPlacementTouchListener() {
        setOnTouchListener { _, event ->
            if (!placementMode) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE -> {
                    mapTarget(event.x, event.y)?.let { listener?.onPlacementPointerTarget(it) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    mapTarget(event.x, event.y)?.let { listener?.onPlacementTapTarget(it) }
                    true
                }
                else -> false
            }
        }
        setOnDragListener { _, event ->
            if (!placementMode) return@setOnDragListener false
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_LOCATION -> {
                    mapTarget(event.x, event.y)?.let { listener?.onPlacementPointerTarget(it) }
                    true
                }
                DragEvent.ACTION_DROP -> {
                    mapTarget(event.x, event.y)?.let { listener?.onPlacementDropTarget(it) }
                    true
                }
                else -> true
            }
        }
    }

    private fun mapTarget(x: Float, y: Float): InsertionTarget? {
        val layout = currentLayout ?: return null
        val editorBounds = layout.editorGridBounds()
        val totalColumnUnits = editorBounds.gridColumnCount
        val totalRowUnits = editorBounds.gridRowCount
        if (totalColumnUnits <= 0 || totalRowUnits <= 0) return null
        val unitWidth = width.toFloat() / totalColumnUnits
        val unitHeight = height.toFloat() / totalRowUnits
        val editableX = x - unitWidth * editorBounds.columnOffsetUnits
        val editableY = y - unitHeight * editorBounds.rowOffsetUnits
        return insertionTargetMapper.mapPointer(
            layout = layout,
            xPx = editableX,
            yPx = editableY,
            widthPx = unitWidth * editorBounds.keyboardColumnUnitCount,
            heightPx = unitHeight * editorBounds.keyboardRowUnitCount,
            policy = currentInsertionPolicy
        )
    }

    private fun mapTargetFromCenter(item: KeyboardLayoutItem): InsertionTarget? {
        val layout = currentLayout ?: return null
        val x = item.placement.columnUnits + item.placement.columnSpanUnits / 2f
        val y = item.placement.rowUnits + item.placement.rowSpanUnits / 2f
        return insertionTargetMapper.mapPointer(
            layout = layout,
            xPx = x,
            yPx = y,
            widthPx = layout.columnUnitCount.toFloat(),
            heightPx = layout.rowUnitCount.toFloat(),
            policy = currentInsertionPolicy
        )
    }

    private fun addInsertionCursor(layout: KeyboardLayout, placementCursor: PlacementCursor?) {
        val cursor = placementCursor ?: return
        val cursorPlacement = cursor.displayPlacement(layout) ?: return
        val cursorView = TextView(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.argb(180, 0, 150, 136))
                setStroke(dpToPx(1), Color.rgb(0, 121, 107))
            }
            elevation = 8f
        }
        cursorView.layoutParams = createLayoutParams(cursorPlacement, rowOffsetUnits = 2, columnOffsetUnits = 2)
        this.addView(cursorView)
    }

    private fun createLayoutParams(
        placement: GridPlacement,
        rowOffsetUnits: Int,
        columnOffsetUnits: Int
    ): LayoutParams {
        return LayoutParams().apply {
            rowSpec = spec(
                placement.rowUnits + rowOffsetUnits,
                placement.rowSpanUnits,
                FILL,
                placement.rowSpanUnits.toFloat()
            )
            columnSpec = spec(
                placement.columnUnits + columnOffsetUnits,
                placement.columnSpanUnits,
                FILL,
                placement.columnSpanUnits.toFloat()
            )
            width = 0
            height = 0
            elevation = 2f
        }
    }

    private fun addFlexibleRowUnitAnchors(editorBounds: EditorGridBounds) {
        repeat(editorBounds.keyboardRowUnitCount) { rowUnit ->
            val anchor = View(context).apply {
                visibility = INVISIBLE
                isFocusable = false
                isClickable = false
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            anchor.layoutParams = LayoutParams().apply {
                rowSpec = spec(
                    editorBounds.rowOffsetUnits + rowUnit,
                    1,
                    FILL,
                    1f
                )
                columnSpec = spec(0, 1, FILL, 0f)
                width = 0
                height = 0
            }
            addView(anchor)
        }
    }

    private fun createKeyView(
        keyData: KeyData,
        placement: GridPlacement
    ): View {
        val isDarkTheme = context.isDarkThemeOn()
        val isHalfHeight = placement.rowSpanUnits == 1

        val leftMargin = dpToPx(2)
        val rightMargin = dpToPx(2)
        val verticalMargin = when {
            isHalfHeight -> dpToPx(1)
            keyData.isSpecialKey -> dpToPx(6)
            else -> dpToPx(3)
        }

        val keyView: View = if (KeyIconResolver.hasIcon(keyData)) {
            AppCompatImageButton(context).apply {
                isFocusable = false; isClickable = true
                minimumHeight = 0
                minimumWidth = 0
                setMinimumHeight(0)
                setMinimumWidth(0)
                setPadding(0, 0, 0, 0)
                adjustViewBounds = false
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                elevation = 2f
                KeyIconResolver.setImage(this, keyData)
                contentDescription = keyData.label

                val originalBg = ContextCompat.getDrawable(
                    context,
                    defaultKeyBackgroundDrawableRes(keyData, isDarkTheme)
                )
                val insetBg = android.graphics.drawable.InsetDrawable(
                    originalBg,
                    leftMargin,
                    verticalMargin,
                    rightMargin,
                    verticalMargin
                )
                background = insetBg
            }
        } else {
            AutoSizeButton(context).apply {
                val label = KeyIconResolver.resolvedLabelForRendering(keyData)
                isFocusable = false; isClickable = true
                isAllCaps = false
                minimumHeight = 0
                minimumWidth = 0
                minHeight = 0
                minWidth = 0
                setMinHeight(0)
                setMinWidth(0)
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                if (label.contains("\n")) {
                    val parts = label.split("\n", limit = 2)
                    val primaryText = parts[0]
                    val secondaryText = parts.getOrNull(1) ?: ""
                    val spannable = SpannableString(label)
                    spannable.setSpan(
                        AbsoluteSizeSpan(spToPx(16f)),
                        0,
                        primaryText.length,
                        Spannable.SPAN_INCLUSIVE_INCLUSIVE
                    )
                    if (secondaryText.isNotEmpty()) {
                        spannable.setSpan(
                            AbsoluteSizeSpan(spToPx(10f)),
                            primaryText.length + 1,
                            label.length,
                            Spannable.SPAN_INCLUSIVE_INCLUSIVE
                        )
                    }
                    this.maxLines = 2
                    this.setLineSpacing(0f, 0.9f)
                    this.gravity = Gravity.CENTER
                    this.text = spannable
                } else {
                    text = label
                    gravity = Gravity.CENTER
                }

                val originalBg = when {
                    KeyVisualStyleResolver.usesSpecialSurface(keyData) -> {
                        elevation = 2f
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        ContextCompat.getDrawable(
                            context,
                            defaultKeyBackgroundDrawableRes(keyData, isDarkTheme)
                        )
                    }

                    keyData.keyType == KeyType.STANDARD_FLICK -> {
                        val keyBaseColor =
                            if (isDarkTheme) context.getColorFromAttr(MaterialR.attr.colorSurfaceContainerHighest) else context.getColorFromAttr(
                                MaterialR.attr.colorSurface
                            )
                        val keyHighlightColor =
                            context.getColorFromAttr(MaterialR.attr.colorSecondaryContainer)
                        val keyTextColor = context.getColorFromAttr(MaterialR.attr.colorOnSurface)
                        setTextColor(Color.TRANSPARENT)
                        SegmentedBackgroundDrawable(
                            label = keyData.label,
                            baseColor = keyBaseColor,
                            highlightColor = keyHighlightColor,
                            textColor = keyTextColor,
                            cornerRadius = 20f
                        )
                    }

                    else -> {
                        ContextCompat.getDrawable(
                            context,
                            if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_center_bg_material else com.kazumaproject.core.R.drawable.ten_keys_center_bg_material_light
                        )
                    }
                }
                val insetBg = android.graphics.drawable.InsetDrawable(
                    originalBg,
                    leftMargin,
                    verticalMargin,
                    rightMargin,
                    verticalMargin
                )
                background = insetBg
            }
        }

        return keyView
    }

    private fun defaultKeyBackgroundDrawableRes(keyData: KeyData, isDarkTheme: Boolean): Int {
        return if (KeyVisualStyleResolver.usesSpecialSurface(keyData)) {
            if (isDarkTheme) {
                com.kazumaproject.core.R.drawable.ten_keys_side_bg_material
            } else {
                com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light
            }
        } else {
            if (isDarkTheme) {
                com.kazumaproject.core.R.drawable.ten_keys_center_bg_material
            } else {
                com.kazumaproject.core.R.drawable.ten_keys_center_bg_material_light
            }
        }
    }

    private fun Context.getColorFromAttr(@AttrRes attrRes: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attrRes, typedValue, true)
        return ContextCompat.getColor(this, typedValue.resourceId)
    }

    private fun spToPx(sp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
            .toInt()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
