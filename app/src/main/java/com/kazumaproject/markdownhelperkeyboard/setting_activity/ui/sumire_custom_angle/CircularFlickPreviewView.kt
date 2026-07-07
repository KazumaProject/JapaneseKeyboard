package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.sumire_custom_angle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import kotlin.math.cos
import kotlin.math.sin

class CircularFlickPreviewView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 方向ごとの色定義
    private val directionColors = mapOf(
        CircularFlickDirection.SLOT_0 to Color.parseColor("#FF5252"),
        CircularFlickDirection.SLOT_1 to Color.parseColor("#448AFF"),
        CircularFlickDirection.SLOT_2 to Color.parseColor("#69F0AE"),
        CircularFlickDirection.SLOT_3 to Color.parseColor("#FFAB40"),
        CircularFlickDirection.SLOT_4 to Color.parseColor("#7E57C2"),
        CircularFlickDirection.SLOT_5 to Color.parseColor("#26A69A"),
        CircularFlickDirection.SLOT_6 to Color.parseColor("#EC407A")
    )

    // 現在の設定値 (FlickKeyboardViewと同じデータ構造)
    private var ranges: Map<CircularFlickDirection, Pair<Float, Float>> = emptyMap()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.GRAY
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    private val rectF = RectF()

    fun setRanges(newRanges: Map<CircularFlickDirection, Pair<Float, Float>>) {
        this.ranges = newRanges
        invalidate() // 再描画
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = (Math.min(width, height) / 2f) * 0.8f

        // 背景円の枠線
        canvas.drawCircle(cx, cy, radius, borderPaint)

        rectF.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // AndroidのCanvasは0度が「3時(右)」で時計回り
        // FlickKeyboardViewの実装に合わせるため、渡された角度をそのまま描画に使用します
        ranges.forEach { (direction, range) ->
            val startAngle = range.first
            val sweepAngle = range.second

            paint.color = directionColors[direction] ?: Color.LTGRAY
            // アルファ値を少し下げて重なりを見やすくする
            paint.alpha = 150

            // 扇形を描画
            canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)

            // ラベル（方向名）の描画位置計算
            // 扇形の中心角度
            val midAngleRad = Math.toRadians((startAngle + sweepAngle / 2).toDouble())
            val labelRadius = radius * 0.6f
            val labelX = cx + (labelRadius * cos(midAngleRad)).toFloat()
            val labelY = cy + (labelRadius * sin(midAngleRad)).toFloat() + (textPaint.textSize / 3)

            // 方向名の略称などを描画
            val label = when (direction) {
                CircularFlickDirection.SLOT_0 -> "SLOT_0"
                CircularFlickDirection.SLOT_1 -> "SLOT_1"
                CircularFlickDirection.SLOT_2 -> "SLOT_2"
                CircularFlickDirection.SLOT_3 -> "SLOT_3"
                CircularFlickDirection.SLOT_4 -> "SLOT_4"
                CircularFlickDirection.SLOT_5 -> "SLOT_5"
                CircularFlickDirection.SLOT_6 -> "SLOT_6"
                CircularFlickDirection.TAP -> ""
            }
            canvas.drawText(label, labelX, labelY, textPaint)
        }
    }
}
