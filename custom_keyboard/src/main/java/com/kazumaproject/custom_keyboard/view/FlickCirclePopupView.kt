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
import android.util.Log
import android.view.View
import androidx.core.graphics.toColorInt
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import java.util.EnumSet
import kotlin.math.cos
import kotlin.math.sin

class FlickCirclePopupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- Paint Objects ---
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
        segmentColor = "#424242".toColorInt(),
        segmentHighlightGradientStartColor = "#00BCD4".toColorInt(),
        segmentHighlightGradientEndColor = "#80DEEA".toColorInt(),
        centerGradientStartColor = "#3F51B5".toColorInt(),
        centerGradientEndColor = "#7986CB".toColorInt(),
        centerHighlightGradientStartColor = "#00E676".toColorInt(),
        centerHighlightGradientEndColor = "#69F0AE".toColorInt(),
        separatorColor = Color.BLACK,
        textColor = Color.WHITE
    )

    init {
        applyThemeToPaints()
    }

    // --- State and Characters/Paths ---
    private var currentFlickDirection = FlickDirection.TAP
    private val characterMap = mutableMapOf<FlickDirection, String>()
    private val targetPositions = mutableMapOf<FlickDirection, PointF>()
    private val segmentPaths = mutableMapOf<FlickDirection, Path>()
    private var isFullUIModeActive = false
    private val segmentAngleMap =
        mutableMapOf<FlickDirection, Pair<Float, Float>>() // <Direction, <StartAngle, SweepAngle>>

    // --- UI Size Properties ---
    private var centerCircleRadius = 70f
    private var orbitRadius = 160f
    private var upperOrbitRadius: Float? = null

    var preferredWidth = (orbitRadius * 2).toInt()
        private set
    var preferredHeight = (orbitRadius * 2).toInt()
        private set

    // --- Public Methods ---

    fun setColors(theme: FlickPopupColorTheme) {
        this.colorTheme = theme
        applyThemeToPaints()
        if (width > 0 && height > 0) {
            updateShaders()
        }
        invalidate()
    }

    fun setUiSize(center: Float, target: Float, orbit: Float, newTextSize: Float) {
        this.centerCircleRadius = center
        this.orbitRadius = orbit
        this.textPaint.textSize = newTextSize
        updateSizesAndRequestLayout()
    }

    fun setUpperOrbit(newUpperOrbit: Float) {
        if (newUpperOrbit > centerCircleRadius) {
            this.upperOrbitRadius = newUpperOrbit
            updateSizesAndRequestLayout()
        } else {
            Log.w(
                "FlickCirclePopupView",
                "Upper orbit radius must be greater than the center circle radius."
            )
        }
    }

    fun updateFlickDirection(direction: FlickDirection) {
        if (currentFlickDirection != direction) {
            currentFlickDirection = direction
            invalidate()
        }
    }

    fun setFullUIMode(isActive: Boolean) {
        if (isFullUIModeActive != isActive) {
            isFullUIModeActive = isActive
            invalidate()
        }
    }

    fun setCharacterMap(map: Map<FlickDirection, String>) {
        characterMap.clear()
        characterMap.putAll(map)
        if (width > 0 && height > 0) {
            recalculateUiComponents()
        }
        invalidate()
    }

    /**
     * 指定された角度（0〜360度）が、現在表示されているどのフリックセグメントに該当するかを返します。
     * @param angle 判別する角度。
     * @return 対応するFlickDirection。どのセグメントにも該当しない場合はTAPを返します。
     */
    fun getDirectionForAngle(angle: Double): FlickDirection {
        val normalizedAngle = angle.toFloat()

        for ((direction, angles) in segmentAngleMap) {
            val start = angles.first
            val sweep = angles.second

            // セグメントが0度/360度の境界をまたぐかチェック (例: 開始340度, 幅40度)
            if (start + sweep > 360f) {
                val end = (start + sweep) % 360f
                // 境界をまたぐ場合、角度は「開始角度以上」または「終了角度未満」になる
                if (normalizedAngle >= start || normalizedAngle < end) {
                    return direction
                }
            } else { // 通常のセグメントの場合
                val end = start + sweep
                // 角度が「開始角度以上」かつ「終了角度未満」かチェック
                if (normalizedAngle >= start && normalizedAngle < end) {
                    return direction
                }
            }
        }
        // どのセグメントにも一致しなかった場合
        return FlickDirection.TAP
    }

    // --- Lifecycle and Drawing Methods ---

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(preferredWidth, preferredHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShaders()
        recalculateUiComponents()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isFullUIModeActive) {
            drawFullUI(canvas)
        } else {
            drawSingleTargetUI(canvas)
        }
    }

    // --- Private Helper Methods ---

    private fun applyThemeToPaints() {
        targetPaint.color = colorTheme.segmentColor
        separatorPaint.color = colorTheme.separatorColor
        separatorPaint.alpha = 80
        textPaint.color = colorTheme.textColor
    }

    private fun updateSizesAndRequestLayout() {
        val effectiveOrbit = this.upperOrbitRadius ?: this.orbitRadius
        preferredWidth = (effectiveOrbit * 2).toInt()
        preferredHeight = (effectiveOrbit * 2).toInt()
        if (width > 0 && height > 0) {
            recalculateUiComponents()
        }
        requestLayout()
        invalidate()
    }

    private fun updateShaders() {
        val w = width.toFloat()
        val h = height.toFloat()
        val centerX = w / 2f
        val centerY = h / 2f

        targetHighlightPaint.shader = LinearGradient(
            0f,
            0f,
            w,
            h,
            colorTheme.segmentHighlightGradientStartColor,
            colorTheme.segmentHighlightGradientEndColor,
            Shader.TileMode.CLAMP
        )
        centerCirclePaint.shader = LinearGradient(
            centerX - centerCircleRadius,
            centerY - centerCircleRadius,
            centerX + centerCircleRadius,
            centerY + centerCircleRadius,
            colorTheme.centerGradientStartColor,
            colorTheme.centerGradientEndColor,
            Shader.TileMode.CLAMP
        )
        centerHighlightPaint.shader = LinearGradient(
            centerX - centerCircleRadius,
            centerY - centerCircleRadius,
            centerX + centerCircleRadius,
            centerY + centerCircleRadius,
            colorTheme.centerHighlightGradientStartColor,
            colorTheme.centerHighlightGradientEndColor,
            Shader.TileMode.CLAMP
        )
    }

    private fun recalculateUiComponents() {
        segmentAngleMap.clear()
        segmentPaths.clear()
        targetPositions.clear()

        val centerX = width / 2f
        val centerY = height / 2f

        calculateSegmentAngles()
        createSegmentPaths(centerX, centerY)
        calculateTargetPositions(centerX, centerY)
    }

    // ▼▼▼【LOGIC REVERTED TO ORIGINAL】▼▼▼
    private fun calculateSegmentAngles() {
        val upperDirections = EnumSet.allOf(FlickDirection::class.java).filter {
            it != FlickDirection.TAP && it != FlickDirection.DOWN && characterMap.containsKey(it)
        }

        if (upperDirections.isNotEmpty()) {
            val totalAngleSpan = 220f
            val startAngleAt = 160f
            val sweepPerSegment = totalAngleSpan / upperDirections.size

            upperDirections.forEachIndexed { index, direction ->
                val segmentStartAngle = startAngleAt + (index * sweepPerSegment)
                segmentAngleMap[direction] = Pair(segmentStartAngle, sweepPerSegment)
            }
        }

        if (characterMap.containsKey(FlickDirection.DOWN)) {
            segmentAngleMap[FlickDirection.DOWN] = Pair(20f, 140f)
        }
    }
    // ▲▲▲【REVERT COMPLETE】▲▲▲

    private fun createSegmentPaths(centerX: Float, centerY: Float) {
        val innerRadius = centerCircleRadius

        segmentAngleMap.forEach { (direction, angles) ->
            val outerRadius = when (direction) {
                FlickDirection.DOWN -> orbitRadius
                else -> upperOrbitRadius ?: orbitRadius
            }
            val startAngle = angles.first
            val sweepAngle = angles.second

            val path = Path()
            val outerRect = RectF(
                centerX - outerRadius,
                centerY - outerRadius,
                centerX + outerRadius,
                centerY + outerRadius
            )
            val innerRect = RectF(
                centerX - innerRadius,
                centerY - innerRadius,
                centerX + innerRadius,
                centerY + innerRadius
            )

            path.arcTo(outerRect, startAngle, sweepAngle, false)
            path.arcTo(innerRect, startAngle + sweepAngle, -sweepAngle, false)
            path.close()
            segmentPaths[direction] = path
        }
    }

    private fun calculateTargetPositions(centerX: Float, centerY: Float) {
        targetPositions[FlickDirection.TAP] = PointF(centerX, centerY)
        val upperEffectiveOrbit = this.upperOrbitRadius ?: this.orbitRadius

        segmentAngleMap.forEach { (direction, angles) ->
            val angleRad = Math.toRadians((angles.first + angles.second / 2.0)).toFloat()
            val textRadius = when (direction) {
                FlickDirection.DOWN -> (centerCircleRadius + orbitRadius) / 2f
                else -> (centerCircleRadius + upperEffectiveOrbit) / 2f
            }
            val x = centerX + cos(angleRad) * textRadius
            val y = centerY + sin(angleRad) * textRadius
            targetPositions[direction] = PointF(x, y)
        }
    }

    private fun drawFullUI(canvas: Canvas) {
        segmentPaths.forEach { (direction, path) ->
            val paint =
                if (direction == currentFlickDirection) targetHighlightPaint else targetPaint
            canvas.drawPath(path, paint)
            canvas.drawPath(path, separatorPaint)
        }

        val centerPoint = targetPositions[FlickDirection.TAP] ?: PointF(width / 2f, height / 2f)
        val currentCenterPaint =
            if (currentFlickDirection == FlickDirection.TAP) centerHighlightPaint else centerCirclePaint
        canvas.drawCircle(centerPoint.x, centerPoint.y, centerCircleRadius, currentCenterPaint)

        characterMap.forEach { (direction, text) ->
            if (direction != FlickDirection.TAP) {
                drawTextOnTarget(canvas, text, direction)
            }
        }
        val centerText =
            characterMap[currentFlickDirection] ?: characterMap[FlickDirection.TAP] ?: ""
        drawTextOnTarget(canvas, centerText, FlickDirection.TAP)
    }

    private fun drawSingleTargetUI(canvas: Canvas) {
        val centerPoint = targetPositions[FlickDirection.TAP] ?: PointF(width / 2f, height / 2f)
        val currentCenterPaint =
            if (currentFlickDirection == FlickDirection.TAP) centerHighlightPaint else centerCirclePaint
        canvas.drawCircle(centerPoint.x, centerPoint.y, centerCircleRadius, currentCenterPaint)

        if (currentFlickDirection != FlickDirection.TAP) {
            segmentPaths[currentFlickDirection]?.let { path ->
                canvas.drawPath(path, targetHighlightPaint)
            }
        }

        val character = characterMap[currentFlickDirection] ?: ""
        if (currentFlickDirection != FlickDirection.TAP) {
            drawTextOnTarget(canvas, character, currentFlickDirection)
        }
        drawTextOnTarget(canvas, character, FlickDirection.TAP)
    }

    private fun drawTextOnTarget(canvas: Canvas, text: String, direction: FlickDirection) {
        targetPositions[direction]?.let { pos ->
            val textOffset = (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(text, pos.x, pos.y - textOffset, textPaint)
        }
    }
}
