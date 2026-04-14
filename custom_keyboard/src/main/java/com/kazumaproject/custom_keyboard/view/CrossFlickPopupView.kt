package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
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

    // 動的に設定される色を保持する変数
    private var useCustomColors = false
    private var customBackgroundColor: Int = 0
    private var customHighlightedBackgroundColor: Int = 0
    private var customTextColor: Int = Color.WHITE

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

    /**
     * 動的に色を設定する関数
     * @param backgroundColor 通常時の背景色
     * @param highlightedBackgroundColor ハイライト時の背景色
     * @param textColor テキストおよびアイコンの色
     */
    fun setColors(backgroundColor: Int, highlightedBackgroundColor: Int, textColor: Int) {
        this.useCustomColors = true
        this.customBackgroundColor = backgroundColor
        this.customHighlightedBackgroundColor = highlightedBackgroundColor
        this.customTextColor = textColor

        // テキスト色を適用
        textView.setTextColor(textColor)

        // アイコンにも同じ色を適用（アイコンが白などで作成されている前提）
        imageView.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)

        // 背景色を再適用
        updateBackgroundColor()
    }

    private fun updateBackgroundColor() {
        val cornerRadius = 20f
        backgroundShape.cornerRadius = cornerRadius

        val color = if (useCustomColors) {
            // カスタム色が設定されている場合
            if (isHighlighted) customHighlightedBackgroundColor else customBackgroundColor
        } else {
            // デフォルトのテーマ属性を使用する場合
            if (isHighlighted) {
                context.getColorFromAttr(materialR.attr.colorSecondaryContainer)
            } else {
                context.getColorFromAttr(materialR.attr.colorSurfaceContainer)
            }
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
                if (flickAction.drawableResId != null) {
                    imageView.setImageResource(flickAction.drawableResId)
                    imageView.visibility = View.VISIBLE
                    textView.visibility = View.GONE
                } else if (!flickAction.label.isNullOrEmpty()) {
                    textView.text = flickAction.label
                    textView.visibility = View.VISIBLE
                    imageView.visibility = View.GONE
                } else {
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
