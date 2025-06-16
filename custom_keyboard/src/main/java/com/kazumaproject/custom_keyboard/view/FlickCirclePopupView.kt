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
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.kazumaproject.custom_keyboard.data.ShapeType
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
        alpha = 0.9f
    }

    // --- State and Properties ---
    private var currentFlickDirection = FlickDirection.TAP
    private val characterMap = mutableMapOf<FlickDirection, String>()
    private val targetPositions = mutableMapOf<FlickDirection, PointF>()
    private val segmentPaths = mutableMapOf<FlickDirection, Path>()
    private var isFullUIModeActive = false
    private val segmentAngleMap =
        mutableMapOf<FlickDirection, Pair<Float, Float>>()

    private var shapeType: ShapeType = ShapeType.CIRCLE

    // --- UI Size Properties ---
    private var centerCircleRadius = 60f
    private var orbitRadius = 160f
    private var upperOrbitRadius: Float? = null
    private var cornerRadius = 60f

    var preferredWidth = (orbitRadius * 2).toInt()
        private set
    var preferredHeight = (orbitRadius * 2).toInt()
        private set

    // --- Public Methods ---

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
        if (width > 0 && height > 0) {
            updateShaders()
        }
        invalidate()
    }

    fun setUiSize(
        center: Float,
        target: Float,
        orbit: Float,
        newTextSize: Float,
        newCornerRadius: Float? = null
    ) {
        this.centerCircleRadius = center
        this.orbitRadius = orbit
        this.textPaint.textSize = newTextSize
        newCornerRadius?.let { this.cornerRadius = it }
        updateSizesAndRequestLayout()
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

    fun getDirectionForAngle(angle: Double): FlickDirection {
        // Check DOWN direction first
        segmentAngleMap[FlickDirection.DOWN]?.let { angles ->
            if (isAngleInSegment(angle, angles)) return FlickDirection.DOWN
        }

        // Then check all other directions
        segmentAngleMap.forEach { (direction, angles) ->
            if (direction != FlickDirection.DOWN) {
                if (isAngleInSegment(angle, angles)) return direction
            }
        }

        return FlickDirection.TAP
    }

    /**
     * FIX: Corrected the logic to handle angle segments that wrap around the 360-degree boundary.
     */
    private fun isAngleInSegment(angle: Double, segment: Pair<Float, Float>): Boolean {
        val start = segment.first
        val sweep = segment.second
        val end = start + sweep

        // Normalize the input angle to the [0, 360) range
        val normalizedAngle = (angle + 360) % 360

        return if (end > 360f) {
            // Segment wraps around 360, e.g., starts at 330 and ends at 30.
            // The angle must be greater than the start OR less than the wrapped end.
            val wrappedEnd = end % 360f
            normalizedAngle >= start || normalizedAngle < wrappedEnd
        } else {
            // Segment is a simple continuous block, e.g., starts at 15 and ends at 165.
            normalizedAngle >= start && normalizedAngle < end
        }
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

    // --- Core Logic ---

    private fun recalculateUiComponents() {
        if (width == 0 || height == 0) return

        segmentPaths.clear()
        targetPositions.clear()
        segmentAngleMap.clear()

        val centerX = width / 2f
        val centerY = height / 2f

        calculateSegmentAngles()
        createSegmentPaths(centerX, centerY)
        calculateTargetPositions(centerX, centerY)
    }

    private fun createSegmentPaths(centerX: Float, centerY: Float) {
        when (shapeType) {
            ShapeType.CIRCLE -> {
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

            ShapeType.ROUNDED_SQUARE -> {
                val w = width.toFloat()
                val h = height.toFloat() - 50
                val outerPath = Path().apply {
                    addRoundRect(
                        0f,
                        0f,
                        w,
                        h,
                        cornerRadius,
                        cornerRadius,
                        Path.Direction.CW
                    )
                }
                val innerCirclePath = Path().apply {
                    addCircle(
                        centerX,
                        centerY,
                        centerCircleRadius,
                        Path.Direction.CCW
                    )
                }
                val donutPath = Path().apply { op(outerPath, innerCirclePath, Path.Op.DIFFERENCE) }

                segmentAngleMap.forEach { (direction, angles) ->
                    val startAngle = angles.first
                    val sweepAngle = angles.second
                    val wedgePath = Path()
                    val r = w + h
                    wedgePath.moveTo(centerX, centerY)
                    wedgePath.lineTo(
                        centerX + r * cos(Math.toRadians(startAngle.toDouble())).toFloat(),
                        centerY + r * sin(Math.toRadians(startAngle.toDouble())).toFloat()
                    )
                    wedgePath.arcTo(
                        centerX - r,
                        centerY - r,
                        centerX + r,
                        centerY + r,
                        startAngle,
                        sweepAngle,
                        false
                    )
                    wedgePath.close()

                    val segmentPath = Path().apply { op(donutPath, wedgePath, Path.Op.INTERSECT) }
                    segmentPaths[direction] = segmentPath
                }
            }
        }
    }

    private fun calculateTargetPositions(centerX: Float, centerY: Float) {
        targetPositions[FlickDirection.TAP] = PointF(centerX, centerY)
        segmentPaths.forEach { (direction, path) ->
            val bounds = RectF()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // R is API 30, BAKLAVA is 35
                path.computeBounds(bounds, true)
            } else {
                path.computeBounds(bounds, true)
            }
            if (direction == FlickDirection.DOWN && shapeType == ShapeType.ROUNDED_SQUARE) {
                val bottomOfCenterCircle = centerY + centerCircleRadius
                val bottomOfView = height.toFloat()
                val targetY = (bottomOfCenterCircle + bottomOfView) / 2f
                targetPositions[direction] = PointF(bounds.centerX(), targetY)
            } else {
                targetPositions[direction] = PointF(bounds.centerX(), bounds.centerY())
            }
        }
    }

    private fun drawFullUI(canvas: Canvas) {
        segmentPaths.forEach { (direction, path) ->
            val paint =
                if (direction == currentFlickDirection) targetHighlightPaint else targetPaint
            canvas.drawPath(path, paint)
        }

        segmentPaths.values.forEach { path ->
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

    /**
     * Corrected drawing logic for single target mode.
     */
    private fun drawSingleTargetUI(canvas: Canvas) {
        val centerPoint = targetPositions[FlickDirection.TAP] ?: PointF(width / 2f, height / 2f)

        // Draw the center circle, highlighting it if TAP is the selected direction
        val currentCenterPaint =
            if (currentFlickDirection == FlickDirection.TAP) centerHighlightPaint else centerCirclePaint
        canvas.drawCircle(centerPoint.x, centerPoint.y, centerCircleRadius, currentCenterPaint)

        // If flicking (not tapping), draw the highlighted segment path and the character within it
        if (currentFlickDirection != FlickDirection.TAP) {
            segmentPaths[currentFlickDirection]?.let { canvas.drawPath(it, targetHighlightPaint) }

            val flickedCharacter = characterMap[currentFlickDirection] ?: ""
            drawTextOnTarget(canvas, flickedCharacter, currentFlickDirection)
        }

        // Determine the correct character for the center and draw it.
        // This ensures the center text is always correct, showing the flicked character if available,
        // or the default tap character otherwise. It prevents the center from being cleared
        // when flicking to a direction with no assigned character.
        val centerText =
            characterMap[currentFlickDirection] ?: characterMap[FlickDirection.TAP] ?: ""
        drawTextOnTarget(canvas, centerText, FlickDirection.TAP)
    }

    private fun drawTextOnTarget(canvas: Canvas, text: String, direction: FlickDirection) {
        targetPositions[direction]?.let { pos ->
            val textOffset = (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(text, pos.x, pos.y - textOffset, textPaint)
        }
    }

    // --- Helper Methods ---

    private fun applyThemeToPaints() {
        targetPaint.color = colorTheme.segmentColor
        separatorPaint.color = colorTheme.separatorColor
        separatorPaint.alpha = 100
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
            centerX - centerCircleRadius, centerY - centerCircleRadius,
            centerX + centerCircleRadius, centerY + centerCircleRadius,
            colorTheme.centerGradientStartColor, colorTheme.centerGradientEndColor,
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

    private fun calculateSegmentAngles() {
        val upperDirections = EnumSet.allOf(FlickDirection::class.java).filter {
            it != FlickDirection.TAP && it != FlickDirection.DOWN && characterMap.containsKey(it)
        }

        val downStartAngle = 15f
        val downSweepAngle = 150f

        val upperStartAngle = 165f
        val upperSweepAngle = 210f

        if (upperDirections.isNotEmpty()) {
            val sweepPerSegment = upperSweepAngle / upperDirections.size
            upperDirections.forEachIndexed { index, direction ->
                val segmentStartAngle = upperStartAngle + (index * sweepPerSegment)
                segmentAngleMap[direction] = Pair(segmentStartAngle, sweepPerSegment)
            }
        }

        if (characterMap.containsKey(FlickDirection.DOWN)) {
            segmentAngleMap[FlickDirection.DOWN] = Pair(downStartAngle, downSweepAngle)
        }
    }
}
