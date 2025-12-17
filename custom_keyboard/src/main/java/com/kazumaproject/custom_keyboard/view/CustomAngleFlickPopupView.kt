package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.kazumaproject.custom_keyboard.data.ShapeType
import kotlin.math.cos
import kotlin.math.sin

class CustomAngleFlickPopupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val targetHighlightPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val centerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val centerHighlightPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private var colorTheme: FlickPopupColorTheme = FlickPopupColorTheme(
        segmentColor = "#E0E0E0".toColorInt(),
        segmentHighlightGradientStartColor = "#00BCD4".toColorInt(),
        segmentHighlightGradientEndColor = "#4DD0E1".toColorInt(),
        centerGradientStartColor = "#FAFAFA".toColorInt(),
        centerGradientEndColor = "#F5F5F5".toColorInt(),
        centerHighlightGradientStartColor = "#00E676".toColorInt(),
        centerHighlightGradientEndColor = "#69F0AE".toColorInt(),
        separatorColor = "#BDBDBD".toColorInt(),
        textColor = Color.BLACK
    )

    init {
        applyThemeToPaints()
    }

    private var currentFlickDirection = FlickDirection.TAP
    private val characterMap = mutableMapOf<FlickDirection, String>()
    private val directionRanges = mutableMapOf<FlickDirection, Pair<Float, Float>>()
    private val targetPositions = mutableMapOf<FlickDirection, PointF>()
    private val segmentPaths = mutableMapOf<FlickDirection, Path>()
    private var isFullUIModeActive = false
    private var shapeType: ShapeType = ShapeType.CIRCLE

    // --- Size Properties ---
    // デフォルト値は入れますが、ControllerからsetFlickSensitivityで上書きされます
    private var centerCircleRadius = 60f
    private var orbitRadius = 160f

    var preferredWidth = (orbitRadius * 2).toInt()
        private set
    var preferredHeight = (orbitRadius * 2).toInt()
        private set

    // --- Configuration Methods ---

    /**
     * フリック感度（閾値）を設定し、中心円（TAPエリア）のサイズに反映させます。
     */
    fun setFlickSensitivity(sensitivity: Float) {
        if (this.centerCircleRadius != sensitivity) {
            this.centerCircleRadius = sensitivity
            updateSizesAndRequestLayout()
        }
    }

    fun setCustomRanges(ranges: Map<FlickDirection, Pair<Float, Float>>) {
        directionRanges.clear()
        directionRanges.putAll(ranges)
        if (width > 0 && height > 0) {
            recalculateUiComponents()
        }
        invalidate()
    }

    fun setShapeType(type: ShapeType) {
        if (this.shapeType != type) {
            this.shapeType = type
            recalculateUiComponents()
            invalidate()
        }
    }

    fun setColors(theme: FlickPopupColorTheme) {
        this.colorTheme = theme
        applyThemeToPaints()
        if (width > 0) updateShaders()
        invalidate()
    }

    // 変更: centerRadius は setFlickSensitivity で管理するため、引数から削除した形に対応
    fun setUiSize(orbit: Float, newTextSize: Float) {
        this.orbitRadius = orbit
        this.textPaint.textSize = newTextSize
        updateSizesAndRequestLayout()
    }

    fun setCharacterMap(map: Map<FlickDirection, String>) {
        characterMap.clear()
        characterMap.putAll(map)
        invalidate()
    }

    fun updateFlickDirection(direction: FlickDirection) {
        if (currentFlickDirection != direction) {
            currentFlickDirection = direction
            invalidate()
        }
    }

    fun setFullUIMode(isActive: Boolean) {
        isFullUIModeActive = isActive
        invalidate()
    }

    // --- Drawing Logic ---

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // UIサイズに基づいた固定サイズを返す
        setMeasuredDimension(preferredWidth, preferredHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShaders()
        recalculateUiComponents()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val centerPoint = PointF(cx, cy)

        if (isFullUIModeActive || currentFlickDirection != FlickDirection.TAP) {
            directionRanges.forEach { (direction, _) ->
                if (isFullUIModeActive || direction == currentFlickDirection) {
                    val path = segmentPaths[direction] ?: return@forEach
                    val isSelected = (direction == currentFlickDirection)
                    val paint = if (isSelected) targetHighlightPaint else targetPaint

                    canvas.drawPath(path, paint)
                    canvas.drawPath(path, separatorPaint)

                    val text = characterMap[direction] ?: ""
                    if (text.isNotEmpty()) {
                        val pos = targetPositions[direction] ?: centerPoint
                        drawCenteredText(canvas, text, pos.x, pos.y)
                    }
                }
            }
        }

        // 中心円の描画 (半径は flickSensitivity と一致するようになっている)
        val isCenterSelected = (currentFlickDirection == FlickDirection.TAP)
        val centerPaint = if (isCenterSelected) centerHighlightPaint else centerCirclePaint
        canvas.drawCircle(centerPoint.x, centerPoint.y, centerCircleRadius, centerPaint)

        val centerText =
            characterMap[currentFlickDirection] ?: characterMap[FlickDirection.TAP] ?: ""
        drawCenteredText(canvas, centerText, centerPoint.x, centerPoint.y)
    }

    private fun drawCenteredText(canvas: Canvas, text: String, x: Float, y: Float) {
        val textOffset = (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text, x, y - textOffset, textPaint)
    }

    private fun recalculateUiComponents() {
        if (width == 0 || height == 0) return

        segmentPaths.clear()
        targetPositions.clear()

        val centerX = width / 2f
        val centerY = height / 2f

        directionRanges.forEach { (direction, range) ->
            createPathForDirection(direction, range.first, range.second, centerX, centerY)
        }

        targetPositions[FlickDirection.TAP] = PointF(centerX, centerY)
    }

    private fun createPathForDirection(
        direction: FlickDirection,
        startAngle: Float,
        sweepAngle: Float,
        cx: Float,
        cy: Float
    ) {
        val path = Path()

        when (shapeType) {
            ShapeType.CIRCLE -> {
                // 外側の円（orbitRadius）
                val outerRect =
                    RectF(cx - orbitRadius, cy - orbitRadius, cx + orbitRadius, cy + orbitRadius)
                // 内側の円（centerCircleRadius = flickSensitivity）
                // これにより、中心円の外側から花びらが始まる
                val innerRect = RectF(
                    cx - centerCircleRadius,
                    cy - centerCircleRadius,
                    cx + centerCircleRadius,
                    cy + centerCircleRadius
                )

                path.arcTo(outerRect, startAngle, sweepAngle, false)
                path.arcTo(innerRect, startAngle + sweepAngle, -sweepAngle, false)
                path.close()
            }

            ShapeType.ROUNDED_SQUARE -> {
                // 簡易実装: 円形ベースで内側をくり抜くロジック
                val r = orbitRadius * 1.5f
                path.moveTo(cx, cy)
                path.lineTo(
                    cx + r * cos(Math.toRadians(startAngle.toDouble())).toFloat(),
                    cy + r * sin(Math.toRadians(startAngle.toDouble())).toFloat()
                )
                path.arcTo(RectF(cx - r, cy - r, cx + r, cy + r), startAngle, sweepAngle, false)
                path.close()

                val centerPath = Path().apply {
                    addCircle(cx, cy, centerCircleRadius, Path.Direction.CCW)
                }
                path.op(centerPath, Path.Op.DIFFERENCE)
            }
        }

        segmentPaths[direction] = path

        val midAngleRad = Math.toRadians((startAngle + sweepAngle / 2).toDouble())
        // 文字位置は中心円と外周の中間に配置
        val textRadius = (centerCircleRadius + orbitRadius) / 2

        val tx = cx + textRadius * cos(midAngleRad).toFloat()
        val ty = cy + textRadius * sin(midAngleRad).toFloat()

        targetPositions[direction] = PointF(tx, ty)
    }

    fun getDirectionForAngle(angle: Double): FlickDirection {
        directionRanges.forEach { (direction, range) ->
            if (isAngleInSegment(angle, range)) return direction
        }
        return FlickDirection.TAP
    }

    private fun isAngleInSegment(angle: Double, segment: Pair<Float, Float>): Boolean {
        val start = segment.first
        val sweep = segment.second
        val end = start + sweep
        val normalizedAngle = (angle + 360) % 360

        return if (end > 360f) {
            val wrappedEnd = end % 360f
            normalizedAngle >= start || normalizedAngle < wrappedEnd
        } else {
            normalizedAngle >= start && normalizedAngle < end
        }
    }

    private fun applyThemeToPaints() {
        targetPaint.color = colorTheme.segmentColor
        separatorPaint.color = colorTheme.separatorColor
        textPaint.color = colorTheme.textColor
    }

    private fun updateSizesAndRequestLayout() {
        // centerCircleRadius か orbitRadius が変わるとView全体のサイズも変わる可能性があるため計算
        // 実際には orbitRadius でサイズが決まるが、描画パスが変わるため更新が必要
        preferredWidth = (orbitRadius * 2).toInt()
        preferredHeight = (orbitRadius * 2).toInt()

        if (width > 0) {
            updateShaders()
            recalculateUiComponents()
        }
        requestLayout()
        invalidate()
    }

    private fun updateShaders() {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f

        // シェーダーの範囲も centerCircleRadius に依存させる
        targetHighlightPaint.shader = LinearGradient(
            0f, 0f, w, h,
            colorTheme.segmentHighlightGradientStartColor,
            colorTheme.segmentHighlightGradientEndColor,
            Shader.TileMode.CLAMP
        )
        centerCirclePaint.shader = LinearGradient(
            cx - centerCircleRadius, cy - centerCircleRadius,
            cx + centerCircleRadius, cy + centerCircleRadius,
            colorTheme.centerGradientStartColor,
            colorTheme.centerGradientEndColor,
            Shader.TileMode.CLAMP
        )
        centerHighlightPaint.shader = LinearGradient(
            cx - centerCircleRadius, cy - centerCircleRadius,
            cx + centerCircleRadius, cy + centerCircleRadius,
            colorTheme.centerHighlightGradientStartColor,
            colorTheme.centerHighlightGradientEndColor,
            Shader.TileMode.CLAMP
        )
    }
}
