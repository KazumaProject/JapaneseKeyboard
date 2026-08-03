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
import androidx.core.graphics.toColorInt
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import kotlin.math.min

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
        strokeJoin = Paint.Join.ROUND
    }

    private val backgroundPath = Path()
    private val textBounds = Rect()
    private var currentDirection: FlickDirection = FlickDirection.TAP

    // カラーテーマから受け取る色のプレースホルダー
    private var defaultColor = "#455A64".toColorInt()
    private var highlightColor = "#37474F".toColorInt()

    // ▼▼▼ 追加: 枠線用の色を保持するプロパティ ▼▼▼
    private var separatorColor = Color.LTGRAY
    private var popupBackgroundColor: Int? = null
    private var popupTextColor: Int? = null

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
        // Viewのテキストカラー状態を更新する
        setTextColor(popupTextColor ?: theme.textColor)
    }

    fun setFlickDirection(direction: FlickDirection) {
        this.currentDirection = direction
        invalidate()
    }

    fun applyPopupViewStyle(style: PopupViewStyle) {
        popupBackgroundColor = style.backgroundColor
        popupTextColor = style.textColor
        setTextSize(TypedValue.COMPLEX_UNIT_SP, style.textSizeSp.coerceIn(8f, 48f))
        style.textColor?.let { setTextColor(it) }
        invalidate()
    }

    /**
     * ▼▼▼ 変更点: 背景の描画後に、枠線も描画する処理を追加 ▼▼▼
     */
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        backgroundPaint.color = popupBackgroundColor ?: if (currentDirection == FlickDirection.TAP) {
            this.highlightColor
        } else {
            this.highlightColor
        }
        // 枠線用のPaintに色を設定
        strokePaint.color = this.separatorColor

        val strokeInset = strokePaint.strokeWidth / 2f
        val pathWidth = (w - strokePaint.strokeWidth).coerceAtLeast(0f)
        val pathHeight = (h - strokePaint.strokeWidth).coerceAtLeast(0f)
        updatePath(pathWidth, pathHeight)

        val saveCount = canvas.save()
        canvas.translate(strokeInset, strokeInset)
        canvas.drawPath(backgroundPath, backgroundPaint)
        canvas.drawPath(backgroundPath, strokePaint)
        canvas.restoreToCount(saveCount)

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

    private fun updatePath(w: Float, h: Float) {
        backgroundPath.reset()

        val cornerRadius = min(24f, min(w, h) / 2f)
        val pointerLength = min(35f, min(w, h))

        when (currentDirection) {
            FlickDirection.TAP -> backgroundPath.addRoundRect(
                RectF(0f, 0f, w, h),
                cornerRadius,
                cornerRadius,
                Path.Direction.CW
            )

            FlickDirection.UP -> updateDownPointingPath(
                w = w,
                h = h,
                cornerRadius = cornerRadius,
                pointerLength = pointerLength
            )

            FlickDirection.DOWN -> updateUpPointingPath(
                w = w,
                h = h,
                cornerRadius = cornerRadius,
                pointerLength = pointerLength
            )

            FlickDirection.UP_LEFT_FAR,
            FlickDirection.UP_LEFT -> updateRightPointingPath(
                w = w,
                h = h,
                cornerRadius = cornerRadius,
                pointerLength = pointerLength
            )

            FlickDirection.UP_RIGHT_FAR,
            FlickDirection.UP_RIGHT -> updateLeftPointingPath(
                w = w,
                h = h,
                cornerRadius = cornerRadius,
                pointerLength = pointerLength
            )
        }

        backgroundPath.close()
    }

    private fun updateRightPointingPath(
        w: Float,
        h: Float,
        cornerRadius: Float,
        pointerLength: Float
    ) {
        val bodyRight = w - pointerLength
        val radius = min(cornerRadius, min(bodyRight, h) / 2f)
        backgroundPath.moveTo(radius, 0f)
        backgroundPath.lineTo(bodyRight, 0f)
        backgroundPath.lineTo(w, h / 2f)
        backgroundPath.lineTo(bodyRight, h)
        backgroundPath.lineTo(radius, h)
        backgroundPath.quadTo(0f, h, 0f, h - radius)
        backgroundPath.lineTo(0f, radius)
        backgroundPath.quadTo(0f, 0f, radius, 0f)
    }

    private fun updateLeftPointingPath(
        w: Float,
        h: Float,
        cornerRadius: Float,
        pointerLength: Float
    ) {
        val bodyLeft = pointerLength
        val bodyWidth = w - bodyLeft
        val radius = min(cornerRadius, min(bodyWidth, h) / 2f)
        backgroundPath.moveTo(w - radius, 0f)
        backgroundPath.lineTo(bodyLeft, 0f)
        backgroundPath.lineTo(0f, h / 2f)
        backgroundPath.lineTo(bodyLeft, h)
        backgroundPath.lineTo(w - radius, h)
        backgroundPath.quadTo(w, h, w, h - radius)
        backgroundPath.lineTo(w, radius)
        backgroundPath.quadTo(w, 0f, w - radius, 0f)
    }

    private fun updateDownPointingPath(
        w: Float,
        h: Float,
        cornerRadius: Float,
        pointerLength: Float
    ) {
        val bodyBottom = h - pointerLength
        val radius = min(cornerRadius, min(w, bodyBottom) / 2f)
        backgroundPath.moveTo(radius, 0f)
        backgroundPath.lineTo(w - radius, 0f)
        backgroundPath.quadTo(w, 0f, w, radius)
        backgroundPath.lineTo(w, bodyBottom)
        backgroundPath.lineTo(w / 2f, h)
        backgroundPath.lineTo(0f, bodyBottom)
        backgroundPath.lineTo(0f, radius)
        backgroundPath.quadTo(0f, 0f, radius, 0f)
    }

    private fun updateUpPointingPath(
        w: Float,
        h: Float,
        cornerRadius: Float,
        pointerLength: Float
    ) {
        val bodyTop = pointerLength
        val bodyHeight = h - bodyTop
        val radius = min(cornerRadius, min(w, bodyHeight) / 2f)
        backgroundPath.moveTo(w / 2f, 0f)
        backgroundPath.lineTo(w, bodyTop)
        backgroundPath.lineTo(w, h - radius)
        backgroundPath.quadTo(w, h, w - radius, h)
        backgroundPath.lineTo(radius, h)
        backgroundPath.quadTo(0f, h, 0f, h - radius)
        backgroundPath.lineTo(0f, bodyTop)
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
