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
import android.widget.Button
import android.widget.GridLayout
import androidx.annotation.AttrRes
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import com.kazumaproject.core.domain.extensions.isDarkThemeOn
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.layout.SegmentedBackgroundDrawable
import com.google.android.material.R as MaterialR

@SuppressLint("ClickableViewAccessibility")
class EditableFlickKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : GridLayout(context, attrs, defStyleAttr) {

    /**
     * リスナーインターフェースを拡張し、キーの交換イベントも通知できるようにする
     */
    interface OnKeyEditListener {
        fun onKeySelected(keyId: String)
        fun onKeysSwapped(draggedKeyId: String, targetKeyId: String)
    }

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
        this.columnCount = layout.columnCount
        this.rowCount = layout.rowCount
        this.isFocusable = false

        // 各キーにドラッグリスナーを設定
        val dragListener = createDragListener()

        layout.keys.forEach { keyData ->
            val keyView: View = createKeyView(keyData)

            keyView.tag = keyData.keyId // ViewにkeyIdをタグとして保持させる

            // ドラッグ＆ドロップのためのリスナーを設定
            keyView.setOnDragListener(dragListener)

            // クリックでキー編集画面へ
            keyView.setOnClickListener {
                keyData.keyId?.let { keyId ->
                    listener?.onKeySelected(keyId)
                }
            }

            // 長押しでドラッグ開始
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
    }

    /**
     * ドラッグイベントを処理するリスナーを生成
     */
    private fun createDragListener(): OnDragListener {
        return OnDragListener { view, event ->
            val targetKeyId = view.tag as? String ?: return@OnDragListener false

            when (event.action) {
                // ドロップを受け付ける準備ができた
                DragEvent.ACTION_DRAG_STARTED -> {
                    true
                }
                // ドラッグ中のビューがドロップ先に入った
                DragEvent.ACTION_DRAG_ENTERED -> {
                    view.alpha = 0.5f // ドロップ可能であることを視覚的に示す
                    true
                }
                // ドラッグ中のビューがドロップ先から出た
                DragEvent.ACTION_DRAG_EXITED -> {
                    view.alpha = 1.0f // 元に戻す
                    true
                }
                // ドロップされた
                DragEvent.ACTION_DROP -> {
                    val item = event.clipData.getItemAt(0)
                    val draggedKeyId = item.text.toString().removePrefix("keyId:")

                    // 自分自身の上にはドロップしない
                    if (draggedKeyId != targetKeyId) {
                        listener?.onKeysSwapped(draggedKeyId, targetKeyId)
                    }
                    true
                }
                // ドラッグ終了（成功・失敗問わず）
                DragEvent.ACTION_DRAG_ENDED -> {
                    view.alpha = 1.0f // 必ず元の見た目に戻す
                    true
                }

                else -> false
            }
        }
    }

    /**
     * キーのViewを生成する（元のロジックを別メソッドに分離）
     */
    private fun createKeyView(keyData: com.kazumaproject.custom_keyboard.data.KeyData): View {
        val isDarkTheme = context.isDarkThemeOn()
        val keyView: View = if (keyData.isSpecialKey && keyData.drawableResId != null) {
            AppCompatImageButton(context).apply {
                isFocusable = false; isClickable = true
                elevation = 2f
                setImageResource(keyData.drawableResId!!)
                contentDescription = keyData.label
                setBackgroundResource(if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_side_bg_material else com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light)
            }
        } else {
            Button(context).apply {
                isFocusable = false; isClickable = true
                isAllCaps = false
                if (keyData.label.contains("\n")) {
                    // ... (元のテキストスタイリングロジックは変更なし)
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
                    val englishOnlyRegex = Regex("^[a-zA-Z@#/_'\"().,?! ]+$")
                    val symbolRegex = Regex("^[()\\[\\],./ -]+$")
                    if (englishOnlyRegex.matches(keyData.label)) setTextSize(
                        TypedValue.COMPLEX_UNIT_SP,
                        14f
                    )
                    else if (symbolRegex.matches(keyData.label)) setTextSize(
                        TypedValue.COMPLEX_UNIT_SP,
                        14f
                    )
                    else setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                }

                when {
                    // ... (元の背景スタイリングロジックは変更なし)
                    keyData.isSpecialKey -> {
                        elevation = 2f
                        setBackgroundResource(if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_side_bg_material else com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    }

                    keyData.keyType == KeyType.STANDARD_FLICK -> {
                        val keyBaseColor =
                            if (isDarkTheme) context.getColorFromAttr(MaterialR.attr.colorSurfaceContainerHighest) else context.getColorFromAttr(
                                MaterialR.attr.colorSurface
                            )
                        val keyHighlightColor =
                            context.getColorFromAttr(MaterialR.attr.colorSecondaryContainer)
                        val keyTextColor = context.getColorFromAttr(MaterialR.attr.colorOnSurface)
                        val segmentedDrawable = SegmentedBackgroundDrawable(
                            label = keyData.label,
                            baseColor = keyBaseColor,
                            highlightColor = keyHighlightColor,
                            textColor = keyTextColor,
                            cornerRadius = 20f
                        )
                        background = segmentedDrawable
                        setTextColor(Color.TRANSPARENT)
                    }

                    else -> {
                        setBackgroundResource(if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_center_bg_material else com.kazumaproject.core.R.drawable.ten_keys_center_bg_material_light)
                    }
                }
            }
        }

        val params = LayoutParams().apply {
            // ... (元のレイアウトパラメータ設定は変更なし)
            rowSpec = spec(keyData.row, keyData.rowSpan, FILL, 1f)
            columnSpec = spec(keyData.column, keyData.colSpan, FILL, 1f)
            width = 0
            height = 0
            elevation = 2f
            if (keyData.isSpecialKey) setMargins(6, 12, 6, 6)
            else setMargins(6, 9, 6, 9)
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
