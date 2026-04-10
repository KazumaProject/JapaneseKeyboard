package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withRotation
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme

/**
 * 通常フリック時に表示される、方向を示す矢印型のポップアップ
 */
class DirectionalKeyPopupView(context: Context) : AppCompatTextView(context) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // 初期色は設定するが、onDrawで動的に上書きされる
        color = "#37474F".toColorInt()
        style = Paint.Style.FILL
    }

    // ▼▼▼ 追加: 枠線描画用のPaintオブジェクト ▼▼▼
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val backgroundPath = Path()
    private val textBounds = Rect()
    private var viewRotation = 0f
    private var currentDirection: FlickDirection = FlickDirection.TAP

    // カラーテーマから受け取る色のプレースホルダー
    private var defaultColor = "#455A64".toColorInt()
    private var highlightColor = "#37474F".toColorInt()

    // ▼▼▼ 追加: 枠線用の色を保持するプロパティ ▼▼▼
    private var separatorColor = Color.LTGRAY

    // アイコン表示用（setAction時にキャッシュ）
    private var cachedIconDrawable: android.graphics.drawable.Drawable? = null

    init {
        // init時のテキスト色はテーマで上書きされる前提
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        // ▼▼▼ 追加: 枠線の太さを設定 ▼▼▼
        strokePaint.strokeWidth = dpToPx(1f)
    }

    /**
     * ▼▼▼ 変更点: 枠線用の色もテーマから受け取るように変更 ▼▼▼
     */
    fun setColors(theme: FlickPopupColorTheme) {
        this.defaultColor = theme.centerGradientStartColor
        this.highlightColor = theme.centerGradientStartColor
        this.separatorColor = theme.separatorColor
        setTextColor(theme.textColor)
        // テキストカラー確定後にキャッシュ済みDrawableのtintを更新
        cachedIconDrawable?.setTint(theme.textColor)
    }

    fun setFlickDirection(direction: FlickDirection) {
        this.currentDirection = direction
        this.viewRotation = when (direction) {
            FlickDirection.UP -> 90f
            FlickDirection.DOWN -> -90f
            FlickDirection.UP_LEFT_FAR -> 0f
            FlickDirection.UP_RIGHT_FAR -> 180f
            else -> 0f
        }
        invalidate()
    }

    /**
     * FlickAction を受け取り、表示内容を設定する。
     * FlickAction.Input の場合はテキスト表示。
     * FlickAction.Action の場合は drawableResId があればアイコン、なければ label を表示。
     */
    fun setAction(action: FlickAction) {
        when (action) {
            is FlickAction.Input -> {
                cachedIconDrawable = null
                text = action.char
            }
            is FlickAction.Action -> {
                if (action.drawableResId != null) {
                    cachedIconDrawable = ContextCompat.getDrawable(context, action.drawableResId)
                        ?.mutate()
                        ?.also { it.setTint(currentTextColor) }
                    text = ""
                } else {
                    cachedIconDrawable = null
                    text = action.label ?: ""
                }
            }
        }
        invalidate()
    }

    /**
     * ▼▼▼ 変更点: 背景の描画後に、枠線も描画する処理を追加 ▼▼▼
     */
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        backgroundPaint.color = if (currentDirection == FlickDirection.TAP) {
            this.highlightColor
        } else {
            this.highlightColor
        }
        // 枠線用のPaintに色を設定
        strokePaint.color = this.separatorColor

        updatePath(w, h)

        // 背景と枠線の両方が回転されるように、withRotationブロック内で描画
        canvas.withRotation(viewRotation, w / 2f, h / 2f) {
            // 1. 背景（塗りつぶし）を描画
            drawPath(backgroundPath, backgroundPaint)
            // 2. 枠線を描画
            drawPath(backgroundPath, strokePaint)
        }

        val drawable = cachedIconDrawable
        if (drawable != null) {
            // アイコン描画（setAction時にキャッシュ済み）
            val iconSize = (minOf(w, h) * 0.5f).toInt()
            val left = ((w - iconSize) / 2f).toInt()
            val top = ((h - iconSize) / 2f).toInt()
            drawable.setBounds(left, top, left + iconSize, top + iconSize)
            drawable.draw(canvas)
        } else {
            val textToDraw = this.text.toString()
            val textPaint = this.paint

            // ▼▼▼ FIX: Paintオブジェクトの色を、現在のテキストカラーで明示的に設定する ▼▼▼
            // setTextColorで設定された色を描画直前にPaintオブジェクトへ確実に適用します。
            textPaint.color = this.currentTextColor

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.getTextBounds(textToDraw, 0, textToDraw.length, textBounds)

            val textX = w / 2f
            val textY = h / 2f + textBounds.height() / 2f

            canvas.drawText(textToDraw, textX, textY, textPaint)
        }
    }

    private fun updatePath(w: Float, h: Float) {
        backgroundPath.reset()

        val cornerRadius = 24f
        val pointerHeight = 35f
        val showPointer = currentDirection != FlickDirection.TAP

        if (showPointer) {
            val rectWidth = w - pointerHeight

            backgroundPath.moveTo(cornerRadius, 0f)
            backgroundPath.lineTo(rectWidth, 0f)
            backgroundPath.lineTo(w, h / 2f)
            backgroundPath.lineTo(rectWidth, h)
            backgroundPath.lineTo(cornerRadius, h)
            backgroundPath.quadTo(0f, h, 0f, h - cornerRadius)
            backgroundPath.lineTo(0f, cornerRadius)
            backgroundPath.quadTo(0f, 0f, cornerRadius, 0f)

        } else {
            backgroundPath.addRoundRect(
                RectF(0f, 0f, w, h),
                cornerRadius,
                cornerRadius,
                Path.Direction.CW
            )
        }

        backgroundPath.close()
    }

    // ▼▼▼ 追加: dpをピクセルに変換するヘルパー関数 ▼▼▼
    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }
}
