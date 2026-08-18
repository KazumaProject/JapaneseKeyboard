package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.data.keyboard.KeyboardSkinId
import com.kazumaproject.core.data.keyboard.KeyboardSkinPopupKind
import com.kazumaproject.core.data.keyboard.KeyboardSkinPopupRenderer
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.google.android.material.R as materialR

private data class PopupCellContent(
    val text: String?,
    val label: String?,
    val drawableResId: Int?,
    val isInputText: Boolean
)

private fun FlickAction.toPopupCellContent(): PopupCellContent = when (this) {
    is FlickAction.Input -> PopupCellContent(
        text = char.takeUnless { it.isEmpty() },
        label = label?.takeUnless { it.isEmpty() },
        drawableResId = drawableResId,
        isInputText = true
    )

    is FlickAction.Action -> {
        val text = when (val keyAction = action) {
            is KeyAction.Text -> keyAction.text
            is KeyAction.InputText -> keyAction.text
            else -> null
        }?.takeUnless { it.isEmpty() }

        PopupCellContent(
            text = text,
            label = label?.takeUnless { it.isEmpty() },
            drawableResId = drawableResId,
            isInputText = action is KeyAction.Text
        )
    }
}

class CrossFlickPopupView(context: Context) : FrameLayout(context) {

    private inner class CellView(context: Context) : FrameLayout(context) {
        val textView: TextView = AppCompatTextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            visibility = View.GONE
        }
        val imageView: ImageView = AppCompatImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            visibility = View.GONE
            val padding = (8 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val backgroundShape = GradientDrawable()

        init {
            addView(textView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(imageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            background = backgroundShape
        }

        fun setContent(action: FlickAction, inputTextTransform: (String) -> String) {
            val content = action.toPopupCellContent()
            if (content.drawableResId != null) {
                imageView.setImageResource(content.drawableResId)
                imageView.visibility = View.VISIBLE
                textView.visibility = View.GONE
                return
            }

            val resolvedText = content.label ?: content.text
            val text = if (content.isInputText && resolvedText != null) {
                inputTextTransform(resolvedText)
            } else {
                resolvedText
            }
            if (text.isNullOrEmpty()) {
                textView.visibility = View.GONE
                imageView.visibility = View.GONE
                return
            }

            textView.text = text
            textView.visibility = View.VISIBLE
            imageView.visibility = View.GONE
        }

        fun applyColors(
            theme: FlickPopupColorTheme,
            highlighted: Boolean,
            backgroundColor: Int?,
            textColor: Int?
        ) {
            val popup = KeyboardSkinPopupRenderer.specFor(keyboardSkinId)
            if (popup != null) {
                background = KeyboardSkinPopupRenderer.createDrawable(
                    context,
                    keyboardSkinId,
                    KeyboardSkinPopupKind.FLICK_CROSS,
                    selected = highlighted,
                )
                textView.setTextColor(
                    if (highlighted) popup.selectedTextColor else popup.textColor
                )
                imageView.setColorFilter(
                    if (highlighted) popup.selectedTextColor else popup.textColor,
                    PorterDuff.Mode.SRC_IN,
                )
                applyTextSize(popup.flickTextSizeSp)
                return
            }
            val resolvedTextColor = textColor ?: theme.textColor
            textView.setTextColor(resolvedTextColor)
            imageView.setColorFilter(resolvedTextColor, PorterDuff.Mode.SRC_IN)
            backgroundShape.cornerRadius = 24f
            backgroundShape.setColor(
                backgroundColor ?: if (highlighted) theme.segmentHighlightGradientStartColor
                else theme.centerGradientStartColor
            )
            backgroundShape.setStroke(
                (1 * resources.displayMetrics.density).toInt(),
                theme.separatorColor
            )
        }

        fun applyFallbackColors(
            context: Context,
            highlighted: Boolean,
            backgroundColor: Int?,
            textColor: Int?
        ) {
            val popup = KeyboardSkinPopupRenderer.specFor(keyboardSkinId)
            if (popup != null) {
                background = KeyboardSkinPopupRenderer.createDrawable(
                    context,
                    keyboardSkinId,
                    KeyboardSkinPopupKind.FLICK_CROSS,
                    selected = highlighted,
                )
                val resolvedTextColor = if (highlighted) {
                    popup.selectedTextColor
                } else {
                    popup.textColor
                }
                textView.setTextColor(resolvedTextColor)
                imageView.setColorFilter(resolvedTextColor, PorterDuff.Mode.SRC_IN)
                applyTextSize(popup.flickTextSizeSp)
                return
            }
            val typedValue = TypedValue()
            val color = if (highlighted) {
                context.theme.resolveAttribute(materialR.attr.colorSecondaryContainer, typedValue, true)
                context.getColor(typedValue.resourceId)
            } else {
                context.theme.resolveAttribute(materialR.attr.colorSurfaceContainer, typedValue, true)
                context.getColor(typedValue.resourceId)
            }
            backgroundShape.cornerRadius = 24f
            backgroundShape.setColor(backgroundColor ?: color)
            textColor?.let { resolvedTextColor ->
                textView.setTextColor(resolvedTextColor)
                imageView.setColorFilter(resolvedTextColor, PorterDuff.Mode.SRC_IN)
            }
        }

        fun applyTextSize(textSizeSp: Float) {
            val fixedSize = KeyboardSkinPopupRenderer.specFor(keyboardSkinId)?.flickTextSizeSp
            textView.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                (fixedSize ?: textSizeSp).coerceIn(8f, 48f),
            )
        }
    }

    private val gridLayout = GridLayout(context).apply {
        columnCount = 3
        rowCount = 3
        alignmentMode = GridLayout.ALIGN_BOUNDS
    }

    private val cells = mutableMapOf<FlickDirection, CellView>()
    private var colorTheme: FlickPopupColorTheme? = null
    private var highlightedDirection: FlickDirection? = null
    private var popupTextSizeSp: Float = 18f
    private var popupBackgroundColor: Int? = null
    private var popupTextColor: Int? = null
    private var inputTextTransform: (String) -> String = { it }
    private var keyboardSkinId: KeyboardSkinId = KeyboardSkinId.DEFAULT

    init {
        addView(gridLayout, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun setColors(theme: FlickPopupColorTheme) {
        colorTheme = theme
        updateCellColors()
    }

    fun setKeyboardSkin(skinId: KeyboardSkinId) {
        keyboardSkinId = skinId
        updateCellColors()
        updateCellTextSizes()
        invalidate()
    }

    fun applyPopupViewStyle(style: PopupViewStyle) {
        val popup = KeyboardSkinPopupRenderer.specFor(keyboardSkinId)
        popupTextSizeSp = popup?.flickTextSizeSp ?: style.textSizeSp.coerceIn(8f, 48f)
        popupBackgroundColor = if (popup == null) style.backgroundColor else null
        popupTextColor = if (popup == null) style.textColor else popup.textColor
        updateCellTextSizes()
        updateCellColors()
        invalidate()
    }

    fun setInputTextTransform(transform: (String) -> String) {
        inputTextTransform = transform
    }

    fun setCells(map: Map<FlickDirection, FlickAction>, keyWidth: Int, keyHeight: Int) {
        gridLayout.removeAllViews()
        cells.clear()

        if (map.size == 1) {
            gridLayout.columnCount = 1
            gridLayout.rowCount = 1

            val (direction, action) = map.entries.first()
            val params = GridLayout.LayoutParams(
                GridLayout.spec(0),
                GridLayout.spec(0)
            ).apply {
                width = keyWidth
                height = keyHeight
            }

            val cell = CellView(context).apply {
                setContent(action, inputTextTransform)
                applyTextSize(popupTextSizeSp)
                val theme = colorTheme
                if (theme != null) {
                    applyColors(
                        theme,
                        direction == highlightedDirection,
                        popupBackgroundColor,
                        popupTextColor
                    )
                } else {
                    applyFallbackColors(
                        context,
                        direction == highlightedDirection,
                        popupBackgroundColor,
                        popupTextColor
                    )
                }
            }
            cell.layoutParams = params
            gridLayout.addView(cell)
            cells[direction] = cell
            return
        }

        gridLayout.columnCount = 3
        gridLayout.rowCount = 3

        val gridPositions = mapOf(
            FlickDirection.UP to Pair(0, 1),
            FlickDirection.DOWN to Pair(2, 1),
            FlickDirection.UP_LEFT_FAR to Pair(1, 0),
            FlickDirection.UP_RIGHT_FAR to Pair(1, 2),
            FlickDirection.TAP to Pair(1, 1)
        )

        gridPositions.forEach { (direction, pos) ->
            val action = map[direction]
            val margin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                KeyboardSkinPopupRenderer.specFor(keyboardSkinId)?.itemGapDp?.div(2f) ?: 1f,
                resources.displayMetrics,
            ).toInt()

            val params = GridLayout.LayoutParams(
                GridLayout.spec(pos.first),
                GridLayout.spec(pos.second)
            ).apply {
                width = keyWidth
                height = keyHeight
                setMargins(margin, margin, margin, margin)
            }

            if (action != null) {
                val cell = CellView(context).apply {
                    setContent(action, inputTextTransform)
                    applyTextSize(popupTextSizeSp)
                    val theme = colorTheme
                    if (theme != null) {
                        applyColors(
                            theme,
                            direction == highlightedDirection,
                            popupBackgroundColor,
                            popupTextColor
                        )
                    } else {
                        applyFallbackColors(
                            context,
                            direction == highlightedDirection,
                            popupBackgroundColor,
                            popupTextColor
                        )
                    }
                }
                cell.layoutParams = params
                gridLayout.addView(cell)
                cells[direction] = cell
            } else {
                val placeholder = View(context)
                placeholder.layoutParams = params
                gridLayout.addView(placeholder)
            }
        }
    }

    fun highlightDirection(direction: FlickDirection?) {
        highlightedDirection = direction
        updateCellColors()
    }

    private fun updateCellTextSizes() {
        cells.values.forEach { it.applyTextSize(popupTextSizeSp) }
    }

    private fun updateCellColors() {
        val theme = colorTheme
        cells.forEach { (dir, cell) ->
            if (theme != null) {
                cell.applyColors(
                    theme,
                    dir == highlightedDirection,
                    popupBackgroundColor,
                    popupTextColor
                )
            } else {
                cell.applyFallbackColors(
                    context,
                    dir == highlightedDirection,
                    popupBackgroundColor,
                    popupTextColor
                )
            }
        }
    }
}
