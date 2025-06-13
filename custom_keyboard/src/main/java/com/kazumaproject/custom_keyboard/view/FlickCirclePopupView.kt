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
import kotlin.math.cos
import kotlin.math.sin

/**
 * 切れ目のない完全な円形フリックUIを描画する専用ビュー。
 * 全てのフリック領域が均等な大きさ（60度）になるように修正済み。
 */
class FlickCirclePopupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- Drawing Paints ---
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#424242".toColorInt()
        style = Paint.Style.FILL
    }
    private val targetHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        setShadowLayer(30f, 0f, 0f, "#00BCD4".toColorInt())
    }
    private val centerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 50f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        alpha = 80
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    // --- State and Characters/Paths ---
    private var currentFlickDirection = FlickDirection.TAP
    private val characterMap = mutableMapOf<FlickDirection, String>()
    private val targetPositions = mutableMapOf<FlickDirection, PointF>()
    private val segmentPaths = mutableMapOf<FlickDirection, Path>()
    private var isFullUIModeActive = false

    private var centerCircleRadius = 70f
    private var orbitRadius = 160f
    private var upperOrbitRadius: Float? = null

    var preferredWidth = (orbitRadius * 2).toInt()
        private set
    var preferredHeight = (orbitRadius * 2).toInt()
        private set

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

    private fun updateSizesAndRequestLayout() {
        val effectiveOrbit = this.upperOrbitRadius ?: this.orbitRadius
        preferredWidth = (effectiveOrbit * 2).toInt()
        preferredHeight = (effectiveOrbit * 2).toInt()
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(preferredWidth, preferredHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val centerX = w / 2f
        val centerY = h / 2f

        val highlightShader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            "#00BCD4".toColorInt(), "#80DEEA".toColorInt(), Shader.TileMode.CLAMP
        )
        targetHighlightPaint.shader = highlightShader

        val centerShader = LinearGradient(
            centerX - centerCircleRadius, centerY - centerCircleRadius,
            centerX + centerCircleRadius, centerY + centerCircleRadius,
            "#3F51B5".toColorInt(), "#7986CB".toColorInt(), Shader.TileMode.CLAMP
        )
        centerCirclePaint.shader = centerShader

        calculateTargetPositions(centerX, centerY)
        createSegmentPaths(centerX, centerY)
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
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isFullUIModeActive) {
            drawFullUI(canvas)
        } else {
            drawSingleTargetUI(canvas)
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
        canvas.drawCircle(centerPoint.x, centerPoint.y, centerCircleRadius, centerCirclePaint)

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
        canvas.drawCircle(centerPoint.x, centerPoint.y, centerCircleRadius, centerCirclePaint)

        if (currentFlickDirection != FlickDirection.TAP) {
            val path = segmentPaths[currentFlickDirection]
            if (path != null) {
                canvas.drawPath(path, targetHighlightPaint)
            }
        }

        val character = characterMap[currentFlickDirection] ?: ""
        if (currentFlickDirection != FlickDirection.TAP) {
            drawTextOnTarget(canvas, character, currentFlickDirection)
        }
        drawTextOnTarget(canvas, character, FlickDirection.TAP)
    }

    /**
     * ▼▼▼【ロジック更新】360度を6つのセグメントで均等に分割（各60度）する▼▼▼
     */
    private fun createSegmentPaths(centerX: Float, centerY: Float) {
        segmentPaths.clear()
        val innerRadius = centerCircleRadius

        // 360度を6分割 = 各60度
        val sweepAngle = 60f

        // UPを-90度（真上）に配置し、そこから時計回りに各セグメントを定義
        val segmentDefinitions = mapOf(
            // UPは-90度中心なので、-120度から-60度まで
            FlickDirection.UP to Pair(-120f, sweepAngle),
            // UP_RIGHTは-60度から0度まで
            FlickDirection.UP_RIGHT to Pair(-60f, sweepAngle),
            // UP_RIGHT_FARは0度から60度まで
            FlickDirection.UP_RIGHT_FAR to Pair(0f, sweepAngle),
            // DOWNは60度から120度まで
            FlickDirection.DOWN to Pair(60f, sweepAngle),
            // UP_LEFT_FARは120度から180度まで
            FlickDirection.UP_LEFT_FAR to Pair(120f, sweepAngle),
            // UP_LEFTは180度から240度まで
            FlickDirection.UP_LEFT to Pair(180f, sweepAngle)
        )

        segmentDefinitions.forEach { (direction, angles) ->
            val outerRadius = when (direction) {
                FlickDirection.DOWN -> orbitRadius
                else -> upperOrbitRadius ?: orbitRadius
            }
            val startAngle = angles.first

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
        targetPositions.clear()
        val upperEffectiveOrbit = this.upperOrbitRadius ?: this.orbitRadius

        FlickDirection.values().forEach { dir ->
            val point = when (dir) {
                FlickDirection.TAP -> PointF(centerX, centerY)
                FlickDirection.DOWN -> {
                    val textRadiusForDown = (centerCircleRadius + orbitRadius) / 2
                    val angleRad = Math.toRadians(getAngleForDirection(dir)).toFloat()
                    val x = centerX + cos(angleRad) * textRadiusForDown
                    val y = centerY + sin(angleRad) * textRadiusForDown
                    PointF(x, y)
                }

                else -> {
                    val textOrbitRadius = (centerCircleRadius + upperEffectiveOrbit) / 2
                    val angleRad = Math.toRadians(getAngleForDirection(dir)).toFloat()
                    val x = centerX + cos(angleRad) * textOrbitRadius
                    val y = centerY + sin(angleRad) * textOrbitRadius
                    PointF(x, y)
                }
            }
            targetPositions[dir] = point
        }
    }

    /**
     * ▼▼▼【ロジック更新】文字表示位置を新しい均等なセグメントの中心に合わせる▼▼▼
     */
    private fun getAngleForDirection(direction: FlickDirection): Double {
        return when (direction) {
            FlickDirection.UP -> -90.0           // -120度と-60度の中心
            FlickDirection.UP_RIGHT -> -30.0     // -60度と0度の中心
            FlickDirection.UP_RIGHT_FAR -> 30.0  // 0度と60度の中心
            FlickDirection.DOWN -> 90.0          // 60度と120度の中心
            FlickDirection.UP_LEFT_FAR -> 150.0  // 120度と180度の中心
            FlickDirection.UP_LEFT -> -150.0     // 180度と240度の中心 (=-150度)
            FlickDirection.TAP -> 0.0
        }
    }

    private fun drawTextOnTarget(canvas: Canvas, text: String, direction: FlickDirection) {
        val pos = targetPositions[direction]
        if (pos != null) {
            val textOffset = (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(text, pos.x, pos.y - textOffset, textPaint)
        }
    }
}
