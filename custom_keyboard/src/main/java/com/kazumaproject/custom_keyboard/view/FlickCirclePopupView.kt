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
import kotlin.math.cos
import kotlin.math.sin

/**
 * 切れ目のない完全な円形フリックUIを描画する専用ビュー。
 * 全てのフリック領域が均等な大きさ（60度）になるように修正済み。
 * ▼▼▼【改修】指が中央に戻った際に円の色が変わるように修正 ▼▼▼
 */
class FlickCirclePopupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- ▼▼▼【ロジック更新】Paintの初期化方法を変更 ▼▼▼ ---
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

    // デフォルトのカラーテーマを適用
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
        // 初期色をPaintに適用
        applyThemeToPaints()
    }

    // ▼▼▼【更新】setColorsメソッドを更新 ▼▼▼
    fun setColors(theme: FlickPopupColorTheme) {
        this.colorTheme = theme
        applyThemeToPaints()
        // サイズが既に確定している場合はShaderも更新する
        if (width > 0 && height > 0) {
            updateShaders()
        }
        invalidate()
    }

    // ▼▼▼【追加】テーマの色をPaintオブジェクトに適用するヘルパー関数 ▼▼▼
    private fun applyThemeToPaints() {
        targetPaint.color = colorTheme.segmentColor
        separatorPaint.color = colorTheme.separatorColor
        separatorPaint.alpha = 80
        textPaint.color = colorTheme.textColor
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
        updateShaders() // Shaderを更新

        val centerX = w / 2f
        val centerY = h / 2f
        calculateTargetPositions(centerX, centerY)
        createSegmentPaths(centerX, centerY)
    }

    private fun updateShaders() {
        val w = width.toFloat()
        val h = height.toFloat()
        val centerX = w / 2f
        val centerY = h / 2f

        // テーマの色を使ってハイライト用のShaderを生成
        targetHighlightPaint.shader = LinearGradient(
            0f, 0f, w, h,
            colorTheme.segmentHighlightGradientStartColor,
            colorTheme.segmentHighlightGradientEndColor,
            Shader.TileMode.CLAMP
        )

        // テーマの色を使って中央の円用のShaderを生成
        centerCirclePaint.shader = LinearGradient(
            centerX - centerCircleRadius, centerY - centerCircleRadius,
            centerX + centerCircleRadius, centerY + centerCircleRadius,
            colorTheme.centerGradientStartColor,
            colorTheme.centerGradientEndColor,
            Shader.TileMode.CLAMP
        )

        // テーマの色を使って中央のハイライト用のShaderを生成
        centerHighlightPaint.shader = LinearGradient(
            centerX - centerCircleRadius, centerY - centerCircleRadius,
            centerX + centerCircleRadius, centerY + centerCircleRadius,
            colorTheme.centerHighlightGradientStartColor,
            colorTheme.centerHighlightGradientEndColor,
            Shader.TileMode.CLAMP
        )
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

        // ▼▼▼【ロジック更新】現在の選択方向に応じて中央の円のPaintを切り替える ▼▼▼
        val currentCenterPaint = if (currentFlickDirection == FlickDirection.TAP) {
            centerHighlightPaint
        } else {
            centerCirclePaint
        }
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

        // ▼▼▼【ロジック更新】現在の選択方向に応じて中央の円のPaintを切り替える ▼▼▼
        val currentCenterPaint = if (currentFlickDirection == FlickDirection.TAP) {
            centerHighlightPaint
        } else {
            centerCirclePaint
        }
        canvas.drawCircle(centerPoint.x, centerPoint.y, centerCircleRadius, currentCenterPaint)

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

        // UPを-90度（真上）に配置し、そこから時計回りに各セグメントを定義
        val segmentDefinitions = mapOf(
            // DOWN: Starts at 20°, sweeps 140°
            FlickDirection.DOWN to Pair(20f, 140f),
            // The remaining 5 segments each sweep 44°
            FlickDirection.UP_LEFT_FAR to Pair(160f, 44f),
            FlickDirection.UP_LEFT to Pair(204f, 44f),
            FlickDirection.UP to Pair(248f, 44f),
            FlickDirection.UP_RIGHT to Pair(292f, 44f),
            FlickDirection.UP_RIGHT_FAR to Pair(336f, 44f)
        )

        segmentDefinitions.forEach { (direction, angles) ->
            // ... (The rest of the function remains the same)
            val outerRadius = when (direction) {
                FlickDirection.DOWN -> orbitRadius
                else -> upperOrbitRadius ?: orbitRadius
            }
            val startAngle = angles.first
            val sweepAngle = angles.second // Use the new sweep angle from the map

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

        FlickDirection.entries.forEach { dir ->
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
            FlickDirection.DOWN -> 90.0           // Center of 20-160
            FlickDirection.UP_LEFT_FAR -> 182.0   // Center of 160-204
            FlickDirection.UP_LEFT -> 226.0       // Center of 204-248
            FlickDirection.UP -> 270.0            // Center of 248-292
            FlickDirection.UP_RIGHT -> 314.0      // Center of 292-336
            FlickDirection.UP_RIGHT_FAR -> 358.0  // Center of 336-20 (same as -2.0)
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
