package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.view

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.View.OnDragListener
import android.widget.GridLayout
import android.widget.ImageButton
import androidx.annotation.AttrRes
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import com.kazumaproject.core.domain.extensions.isDarkThemeOn
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.layout.SegmentedBackgroundDrawable
import com.kazumaproject.custom_keyboard.view.AutoSizeButton
import com.google.android.material.R as MaterialR

@SuppressLint("ClickableViewAccessibility")
class EditableFlickKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : GridLayout(context, attrs, defStyleAttr) {

    // ▼▼▼ インターフェースに削除イベントを追加 ▼▼▼
    interface OnKeyEditListener {
        fun onKeySelected(keyId: String)
        fun onKeysSwapped(draggedKeyId: String, targetKeyId: String)
        fun onRowDeleted(rowIndex: Int)
        fun onColumnDeleted(columnIndex: Int)
    }
    // ▲▲▲ インターフェースに削除イベントを追加 ▲▲▲

    private var listener: OnKeyEditListener? = null

    fun setOnKeyEditListener(listener: OnKeyEditListener?) {
        this.listener = listener
    }

    fun removeOnKeyEditListener() {
        this.listener = null
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setKeyboard(layout: KeyboardLayout) {
        this.removeAllViews()

        // ▼▼▼ 削除ボタン用に列と行を1つずつ増やす ▼▼▼
        this.columnCount = layout.columnCount + 1
        this.rowCount = layout.rowCount + 1
        // ▲▲▲ 削除ボタン用に列と行を1つずつ増やす ▲▲▲

        this.isFocusable = false

        val dragListener = createDragListener()

        // キーの描画
        layout.keys.forEach { keyData ->
            // ▼▼▼ 削除ボタン用にオフセット(1,1)をかけて描画 ▼▼▼
            val keyView: View = createKeyView(keyData, 1, 1)
            keyView.tag = keyData.keyId
            keyView.setOnDragListener(dragListener)
            keyView.setOnClickListener { listener?.onKeySelected(keyData.keyId!!) }
            keyView.setOnLongClickListener { view ->
                keyData.keyId?.let { keyId ->
                    val clipText = "keyId:$keyId"
                    val item = ClipData.Item(clipText)
                    val mimeTypes = arrayOf("text/plain")
                    val data = ClipData(clipText, mimeTypes, item)
                    val dragShadow = DragShadowBuilder(view)
                    view.startDragAndDrop(data, dragShadow, view, 0)
                }
                true
            }
            this.addView(keyView)
        }

        // ▼▼▼ ここから削除ボタンの描画を追加 ▼▼▼
        // 行削除ボタンの描画
        for (i in 0 until layout.rowCount) {
            val deleteButton = createDeleteButton { listener?.onRowDeleted(i) }
            val params = LayoutParams().apply {
                rowSpec = spec(i + 1, 1, FILL, 1f) // キーの行に対応 (+1はオフセット)
                columnSpec = spec(0, 1, FILL, 0.5f)  // 最初の列(インデックス0)に配置
                width = 0
                height = 0
                setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            }
            deleteButton.layoutParams = params
            this.addView(deleteButton)
        }

        // 列削除ボタンの描画
        for (i in 0 until layout.columnCount) {
            val deleteButton = createDeleteButton { listener?.onColumnDeleted(i) }
            val params = LayoutParams().apply {
                rowSpec = spec(0, 1, FILL, 0.5f) // 最初の行(インデックス0)に配置
                columnSpec = spec(i + 1, 1, FILL, 1f) // キーの列に対応 (+1はオフセット)
                width = 0
                height = 0
                setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            }
            deleteButton.layoutParams = params
            this.addView(deleteButton)
        }
        // ▲▲▲ ここまで削除ボタンの描画を追加 ▲▲▲
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
            val targetKeyId = view.tag as? String ?: return@OnDragListener false
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

    // createKeyViewにオフセット引数を追加
    private fun createKeyView(
        keyData: KeyData,
        rowOffset: Int,
        colOffset: Int
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

        val params = LayoutParams().apply {
            rowSpec = spec(keyData.row + rowOffset, keyData.rowSpan, FILL, 1f)
            columnSpec = spec(keyData.column + colOffset, keyData.colSpan, FILL, 1f)
            width = 0
            height = 0
            elevation = 2f
            // ▼▼▼ 変更点4: setMargins の呼び出しを完全に削除 ▼▼▼
            // if (keyData.isSpecialKey) setMargins(6, 12, 6, 6)
            // else setMargins(6, 9, 6, 9)
        }
        keyView.layoutParams = params
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
