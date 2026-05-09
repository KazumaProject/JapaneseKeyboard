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
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import com.kazumaproject.core.domain.extensions.isDarkThemeOn
import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.KeyboardLayoutItem
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.layout.SegmentedBackgroundDrawable
import com.kazumaproject.custom_keyboard.view.AutoSizeButton
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.canonicalLayoutForEditor
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.columnDeleteSpecs
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.displayPlacement
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.editorGridBounds
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.InsertionTarget
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.InsertionTargetMapper
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.PlacementCursor
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.rowDeleteSpecs
import com.google.android.material.R as MaterialR

@SuppressLint("ClickableViewAccessibility")
class EditableFlickKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : GridLayout(context, attrs, defStyleAttr) {

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
        selectedItemId: String? = null,
        previewInsertedItemId: String? = null,
        previewMovedItemIds: Set<String> = emptySet()
    ) {
        this.removeAllViews()
        val displayLayout = layout.canonicalLayoutForEditor(placementCursor)
        this.currentLayout = displayLayout
        this.placementMode = placementMode

        val editorBounds = displayLayout.editorGridBounds()
        this.columnCount = editorBounds.gridColumnCount
        this.rowCount = editorBounds.gridRowCount

        this.isFocusable = false

        val dragListener = createDragListener()
        setPlacementTouchListener()

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

        addInsertionCursor(displayLayout, placementCursor)

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
        val keyView: View = createKeyView(keyData)
        keyView.layoutParams = createLayoutParams(item.placement, rowOffsetUnits = 2, columnOffsetUnits = 2)
        keyView.tag = keyData.keyId
        keyView.setOnDragListener(dragListener)
        keyView.setOnClickListener {
            if (placementMode) {
                mapTargetFromCenter(item)?.let { target -> listener?.onPlacementTapTarget(target) }
            } else {
                keyData.keyId?.let { keyId -> listener?.onKeySelected(keyId) }
            }
        }
        keyView.setOnLongClickListener { view ->
            if (placementMode) return@setOnLongClickListener true
            keyData.keyId?.let { keyId ->
                val clipText = "keyId:$keyId"
                val clipItem = ClipData.Item(clipText)
                val mimeTypes = arrayOf("text/plain")
                val data = ClipData(clipText, mimeTypes, clipItem)
                val dragShadow = DragShadowBuilder(view)
                view.startDragAndDrop(data, dragShadow, view, 0)
            }
            true
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
            setOnClickListener {
                if (placementMode) {
                    mapTargetFromCenter(item)?.let { target -> listener?.onPlacementTapTarget(target) }
                } else {
                    listener?.onSpacerSelected(item.id)
                }
            }
        }
        spacerView.layoutParams = createLayoutParams(item.placement, rowOffsetUnits = 2, columnOffsetUnits = 2)
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
            item.id == selectedItemId -> {
                view.foreground = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.TRANSPARENT)
                    setStroke(dpToPx(2), Color.rgb(33, 150, 243))
                }
            }
        }
    }

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
            heightPx = unitHeight * editorBounds.keyboardRowUnitCount
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
            heightPx = layout.rowUnitCount.toFloat()
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

    private fun createKeyView(
        keyData: KeyData
    ): View {
        val isDarkTheme = context.isDarkThemeOn()

        // ▼▼▼ 変更点1: マージン値をここで定義 ▼▼▼
        val (leftMargin, topMargin, rightMargin, bottomMargin) = if (keyData.isSpecialKey) {
            // isSpecialKey の場合のマージン
            listOf(dpToPx(2), dpToPx(6), dpToPx(2), dpToPx(6))
        } else {
            // 通常キーの場合のマージin
            listOf(dpToPx(2), dpToPx(3), dpToPx(2), dpToPx(3))
        }

        val keyView: View = if (keyData.isSpecialKey && keyData.drawableResId != null) {
            AppCompatImageButton(context).apply {
                isFocusable = false; isClickable = true
                elevation = 2f
                setImageResource(keyData.drawableResId!!)
                contentDescription = keyData.label

                // ▼▼▼ 変更点2: InsetDrawable を使用 ▼▼▼
                val originalBg = ContextCompat.getDrawable(
                    context,
                    if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_side_bg_material else com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light
                )
                val insetBg = android.graphics.drawable.InsetDrawable(
                    originalBg,
                    leftMargin,
                    topMargin,
                    rightMargin,
                    bottomMargin
                )
                background = insetBg
            }
        } else {
            AutoSizeButton(context).apply {
                isFocusable = false; isClickable = true
                isAllCaps = false
                if (keyData.label.contains("\n")) {
                    val parts = keyData.label.split("\n", limit = 2)
                    val primaryText = parts[0]
                    val secondaryText = parts.getOrNull(1) ?: ""
                    val spannable = SpannableString(keyData.label)
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
                            keyData.label.length,
                            Spannable.SPAN_INCLUSIVE_INCLUSIVE
                        )
                    }
                    this.maxLines = 2
                    this.setLineSpacing(0f, 0.9f)
                    this.setPadding(0, dpToPx(4), 0, dpToPx(4))
                    this.gravity = Gravity.CENTER
                    this.text = spannable
                } else {
                    text = keyData.label
                    gravity = Gravity.CENTER
                }

                // ▼▼▼ 変更点3: 背景設定ロジックを InsetDrawable を使うように変更 ▼▼▼
                val originalBg = when {
                    keyData.isSpecialKey -> {
                        elevation = 2f
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        ContextCompat.getDrawable(
                            context,
                            if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_side_bg_material else com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light
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
                    topMargin,
                    rightMargin,
                    bottomMargin
                )
                background = insetBg
            }
        }

        return keyView
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
