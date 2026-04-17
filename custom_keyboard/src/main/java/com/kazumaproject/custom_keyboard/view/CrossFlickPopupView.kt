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
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.google.android.material.R as materialR

class CrossFlickPopupView(context: Context) : FrameLayout(context) {

    private class CellView(context: Context) : FrameLayout(context) {
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

        fun setContent(action: FlickAction) {
            when (action) {
                is FlickAction.Input -> {
                    textView.text = action.char
                    textView.visibility = View.VISIBLE
                    imageView.visibility = View.GONE
                }
                is FlickAction.Action -> {
                    if (action.drawableResId != null) {
                        imageView.setImageResource(action.drawableResId)
                        imageView.visibility = View.VISIBLE
                        textView.visibility = View.GONE
                    } else if (!action.label.isNullOrEmpty()) {
                        textView.text = action.label
                        textView.visibility = View.VISIBLE
                        imageView.visibility = View.GONE
                    } else {
                        textView.text = action.action.javaClass.simpleName.first().toString()
                        textView.visibility = View.VISIBLE
                        imageView.visibility = View.GONE
                    }
                }
            }
        }

        fun applyColors(theme: FlickPopupColorTheme, highlighted: Boolean) {
            textView.setTextColor(theme.textColor)
            imageView.setColorFilter(theme.textColor, PorterDuff.Mode.SRC_IN)
            backgroundShape.cornerRadius = 24f
            backgroundShape.setColor(
                if (highlighted) theme.segmentHighlightGradientStartColor
                else theme.centerGradientStartColor
            )
            backgroundShape.setStroke(
                (1 * resources.displayMetrics.density).toInt(),
                theme.separatorColor
            )
        }

        fun applyFallbackColors(context: Context, highlighted: Boolean) {
            val typedValue = TypedValue()
            val color = if (highlighted) {
                context.theme.resolveAttribute(materialR.attr.colorSecondaryContainer, typedValue, true)
                context.getColor(typedValue.resourceId)
            } else {
                context.theme.resolveAttribute(materialR.attr.colorSurfaceContainer, typedValue, true)
                context.getColor(typedValue.resourceId)
            }
            backgroundShape.cornerRadius = 24f
            backgroundShape.setColor(color)
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

    init {
        addView(gridLayout, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun setColors(theme: FlickPopupColorTheme) {
        colorTheme = theme
        cells.forEach { (dir, cell) ->
            cell.applyColors(theme, dir == highlightedDirection)
        }
    }

    fun setCells(map: Map<FlickDirection, FlickAction>, keyWidth: Int, keyHeight: Int) {
        gridLayout.removeAllViews()
        cells.clear()

        val gridPositions = mapOf(
            FlickDirection.UP to Pair(0, 1),
            FlickDirection.DOWN to Pair(2, 1),
            FlickDirection.UP_LEFT_FAR to Pair(1, 0),
            FlickDirection.UP_RIGHT_FAR to Pair(1, 2),
            FlickDirection.TAP to Pair(1, 1)
        )

        gridPositions.forEach { (direction, pos) ->
            val action = map[direction]
            val margin = (1 * context.resources.displayMetrics.density).toInt()

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
                    setContent(action)
                    val theme = colorTheme
                    if (theme != null) {
                        applyColors(theme, direction == highlightedDirection)
                    } else {
                        applyFallbackColors(context, direction == highlightedDirection)
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
        val theme = colorTheme
        cells.forEach { (dir, cell) ->
            if (theme != null) {
                cell.applyColors(theme, dir == direction)
            } else {
                cell.applyFallbackColors(context, dir == direction)
            }
        }
    }
}
