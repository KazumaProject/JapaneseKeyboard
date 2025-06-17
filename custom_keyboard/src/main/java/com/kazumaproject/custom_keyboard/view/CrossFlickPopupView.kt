package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.google.android.material.R as materialR

/**
 * 十字フリック用のポップアップビュー。文字とアイコンの両方に対応。
 */
class CrossFlickPopupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val backgroundShape = GradientDrawable()
    private var isHighlighted = false

    private lateinit var textView: TextView
    private lateinit var imageView: ImageView

    private fun Context.getColorFromAttr(attrRes: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attrRes, typedValue, true)
        return this.getColor(typedValue.resourceId)
    }

    init {
        initViews()
        updateBackgroundColor()
        background = backgroundShape
    }

    private fun initViews() {
        // TextView for characters
        textView = AppCompatTextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            visibility = View.GONE
        }
        // ImageView for icons
        imageView = AppCompatImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            visibility = View.GONE
        }
        val padding = (8 * resources.displayMetrics.density).toInt()
        imageView.setPadding(padding, padding, padding, padding)

        addView(textView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(imageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun updateBackgroundColor() {
        val cornerRadius = 20f
        backgroundShape.cornerRadius = cornerRadius
        val color = if (isHighlighted) {
            context.getColorFromAttr(materialR.attr.colorSecondaryContainer)
        } else {
            context.getColorFromAttr(materialR.attr.colorSurfaceContainer)
        }
        backgroundShape.setColor(color)
    }

    /**
     * 表示するコンテンツを設定する
     * @param flickAction 表示するFlickAction (文字またはアクション)
     */
    fun setContent(flickAction: FlickAction) {
        when (flickAction) {
            is FlickAction.Input -> {
                textView.text = flickAction.char
                textView.visibility = View.VISIBLE
                imageView.visibility = View.GONE
            }

            is FlickAction.Action -> {
                // THE FIX: Check for drawable, then label, then fallback.
                if (flickAction.drawableResId != null) {
                    // If a drawable resource is provided, use the ImageView.
                    imageView.setImageResource(flickAction.drawableResId)
                    imageView.visibility = View.VISIBLE
                    textView.visibility = View.GONE
                } else if (!flickAction.label.isNullOrEmpty()) {
                    // If no drawable, but a label exists, use the TextView to show the label.
                    textView.text = flickAction.label
                    textView.visibility = View.VISIBLE
                    imageView.visibility = View.GONE
                } else {
                    // Fallback for actions with no drawable and no label.
                    textView.text = flickAction.action.javaClass.simpleName.first().toString()
                    textView.visibility = View.VISIBLE
                    imageView.visibility = View.GONE
                }
            }
        }
    }

    fun setHighlight(highlighted: Boolean) {
        if (isHighlighted != highlighted) {
            isHighlighted = highlighted
            updateBackgroundColor()
        }
    }
}
