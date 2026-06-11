package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.os.SystemClock
import android.util.AttributeSet
import android.util.SparseArray
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting
import java.util.Random
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class SuminagashiInkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private enum class InkDropType {
        TouchBloom,
        MoveTrail,
        ReleaseBloom,
        Tendril
    }

    private data class InkDrop(
        val x: Float,
        val y: Float,
        @ColorInt val color: Int,
        val startTimeMillis: Long,
        val lifetimeMillis: Long,
        val startRadius: Float,
        val endRadius: Float,
        val driftX: Float,
        val driftY: Float,
        val wobblePhase: Float,
        val wobbleSpeed: Float,
        val ovalScaleX: Float,
        val ovalScaleY: Float,
        val baseAlpha: Int,
        val directionX: Float,
        val directionY: Float,
        val stretch: Float,
        val type: InkDropType,
        val depositStrength: Float,
        val tendrilCount: Int
    )

    private class PointerInkState(
        @ColorInt val color: Int,
        var lastEmitTimeMillis: Long,
        var lastX: Float,
        var lastY: Float,
        var directionX: Float,
        var directionY: Float
    )

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isFilterBitmap = true
    }
    private val blobPath = Path()
    private val strokePath = Path()
    private val random = Random()
    private val hsv = FloatArray(3)
    private val drops = ArrayList<InkDrop>(MAX_DROPS)
    private val pointerStates = SparseArray<PointerInkState>()

    private var residualBitmap: Bitmap? = null
    private var residualCanvas: Canvas? = null
    private var residualScratchBitmap: Bitmap? = null
    private var residualScratchCanvas: Canvas? = null
    private var residualInkEnergy = 0f

    private var effectEnabled = false
    private var colorMode = COLOR_MODE_RANDOM

    @ColorInt
    private var fixedInkColor = DEFAULT_INK_COLOR
    private var frameScheduled = false

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
    }

    fun configure(
        enabled: Boolean,
        colorMode: String,
        @ColorInt fixedColor: Int
    ) {
        this.colorMode = normalizeColorMode(colorMode)
        fixedInkColor = withoutAlpha(fixedColor)

        if (!enabled) {
            effectEnabled = false
            clearInk()
            visibility = GONE
            return
        }

        effectEnabled = true
        if (visibility != VISIBLE) {
            visibility = VISIBLE
        }
        ensureResidualSurface()
    }

    fun onPointerDown(pointerId: Int, x: Float, y: Float) {
        if (!canEmitInk()) return

        val now = SystemClock.uptimeMillis()
        val color = resolveInkColor()
        val angle = random.nextFloat() * TWO_PI
        pointerStates.put(
            pointerId,
            PointerInkState(
                color = color,
                lastEmitTimeMillis = now,
                lastX = x,
                lastY = y,
                directionX = cos(angle),
                directionY = sin(angle)
            )
        )
        emitBurst(x, y, color, now)
        scheduleNextFrame()
    }

    fun onPointerMove(pointerId: Int, x: Float, y: Float) {
        if (!canEmitInk()) return

        val state = pointerStates[pointerId] ?: return
        val now = SystemClock.uptimeMillis()
        val dx = x - state.lastX
        val dy = y - state.lastY
        val distanceSquared = dx * dx + dy * dy
        if (now - state.lastEmitTimeMillis < MOVE_EMIT_INTERVAL_MS &&
            distanceSquared < MOVE_EMIT_DISTANCE_PX * MOVE_EMIT_DISTANCE_PX
        ) {
            return
        }

        val distance = sqrt(distanceSquared)
        val directionX = if (distance > 0.01f) dx / distance else state.directionX
        val directionY = if (distance > 0.01f) dy / distance else state.directionY
        state.lastEmitTimeMillis = now
        state.lastX = x
        state.lastY = y
        state.directionX = directionX
        state.directionY = directionY
        emitTrail(x, y, state.color, now, directionX, directionY, distance)
        scheduleNextFrame()
    }

    fun onPointerUp(pointerId: Int, x: Float? = null, y: Float? = null) {
        val state = pointerStates[pointerId] ?: return
        pointerStates.remove(pointerId)
        if (!canEmitInk()) return

        val releaseX = x ?: state.lastX
        val releaseY = y ?: state.lastY
        emitReleaseBloom(
            x = releaseX,
            y = releaseY,
            color = state.color,
            now = SystemClock.uptimeMillis(),
            directionX = state.directionX,
            directionY = state.directionY
        )
        scheduleNextFrame()
    }

    fun onCancel() {
        if (canEmitInk()) {
            val now = SystemClock.uptimeMillis()
            for (index in 0 until pointerStates.size()) {
                val state = pointerStates.valueAt(index)
                emitReleaseBloom(
                    x = state.lastX,
                    y = state.lastY,
                    color = state.color,
                    now = now,
                    directionX = state.directionX,
                    directionY = state.directionY,
                    isCancel = true
                )
            }
        }
        pointerStates.clear()
        scheduleNextFrame()
    }

    fun clearInk() {
        drops.clear()
        pointerStates.clear()
        clearResidualSurface()
        residualInkEnergy = 0f
        frameScheduled = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (!effectEnabled || visibility != VISIBLE) {
            frameScheduled = false
            return
        }

        frameScheduled = false
        ensureResidualSurface()

        val now = SystemClock.uptimeMillis()
        animateResidualSurface(now)
        drawResidualSurface(canvas)

        var residualDeposits = 0
        var index = drops.size - 1
        while (index >= 0) {
            val drop = drops[index]
            val ageMillis = now - drop.startTimeMillis
            if (ageMillis >= drop.lifetimeMillis) {
                drops.removeAt(index)
                index -= 1
                continue
            }

            drawDrop(canvas, drop, ageMillis, alphaScale = 1f, residualPass = false)
            val surfaceCanvas = residualCanvas
            if (surfaceCanvas != null && residualDeposits < MAX_RESIDUAL_DEPOSITS_PER_FRAME) {
                drawDrop(
                    canvas = surfaceCanvas,
                    drop = drop,
                    ageMillis = ageMillis,
                    alphaScale = RESIDUAL_DEPOSIT_ALPHA_SCALE * drop.depositStrength,
                    residualPass = true
                )
                residualDeposits += 1
            }
            index -= 1
        }

        if (residualDeposits > 0) {
            residualInkEnergy = (residualInkEnergy + residualDeposits * RESIDUAL_ENERGY_PER_DEPOSIT)
                .coerceAtMost(1f)
        }

        if (hasRenderableInk()) {
            scheduleNextFrame()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildResidualSurface(w, h)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView == this && visibility != VISIBLE) {
            frameScheduled = false
        } else if (changedView == this && visibility == VISIBLE && hasRenderableInk()) {
            scheduleNextFrame()
        }
    }

    override fun onDetachedFromWindow() {
        clearInk()
        recycleResidualSurface()
        super.onDetachedFromWindow()
    }

    private fun drawDrop(
        canvas: Canvas,
        drop: InkDrop,
        ageMillis: Long,
        alphaScale: Float,
        residualPass: Boolean
    ) {
        val progress = (ageMillis.toFloat() / drop.lifetimeMillis).coerceIn(0f, 1f)
        val inverse = 1f - progress
        val eased = 1f - inverse * inverse * inverse
        val radius = drop.startRadius + (drop.endRadius - drop.startRadius) * eased
        val wave = sin(drop.wobblePhase + progress * drop.wobbleSpeed)
        val crossWave = cos(drop.wobblePhase * 0.7f + progress * drop.wobbleSpeed * 1.3f)
        val centerX = drop.x + drop.driftX * progress + wave * radius * 0.075f
        val centerY = drop.y + drop.driftY * progress + crossWave * radius * 0.055f
        val alpha = (drop.baseAlpha * alphaFade(progress) * alphaScale).toInt().coerceIn(0, 255)
        if (alpha <= 0) return

        if (drop.type == InkDropType.Tendril) {
            drawTendrilDrop(canvas, drop, centerX, centerY, radius, progress, alpha, residualPass)
            return
        }

        val orientation = atan2(drop.directionY, drop.directionX)
        val stretch = when (drop.type) {
            InkDropType.MoveTrail -> drop.stretch * (0.65f + inverse * 0.55f)
            InkDropType.ReleaseBloom -> drop.stretch * (0.45f + progress * 0.35f)
            InkDropType.TouchBloom -> drop.stretch * 0.36f
            InkDropType.Tendril -> 0f
        }
        val majorScale = drop.ovalScaleX + stretch
        val minorScale = drop.ovalScaleY * if (drop.type == InkDropType.MoveTrail) 0.68f else 1f
        val red = Color.red(drop.color)
        val green = Color.green(drop.color)
        val blue = Color.blue(drop.color)

        fillPaint.color = Color.argb(alpha, red, green, blue)
        buildOrganicBlobPath(
            path = blobPath,
            centerX = centerX,
            centerY = centerY,
            radius = radius,
            orientation = orientation,
            majorScale = majorScale,
            minorScale = minorScale,
            phase = drop.wobblePhase + progress * drop.wobbleSpeed
        )
        canvas.drawPath(blobPath, fillPaint)

        val innerAlpha = (alpha * if (residualPass) 0.44f else 0.68f).toInt().coerceIn(0, 255)
        if (innerAlpha > 0) {
            fillPaint.color = Color.argb(innerAlpha, red, green, blue)
            buildOrganicBlobPath(
                path = blobPath,
                centerX = centerX - drop.directionY * radius * 0.08f,
                centerY = centerY + drop.directionX * radius * 0.08f,
                radius = radius * 0.46f,
                orientation = orientation + 0.8f,
                majorScale = minorScale * 1.08f,
                minorScale = majorScale * 0.72f,
                phase = drop.wobblePhase * 1.7f - progress * drop.wobbleSpeed
            )
            canvas.drawPath(blobPath, fillPaint)
        }

        drawRipple(canvas, drop, centerX, centerY, radius, orientation, progress, alpha, residualPass)
        drawFilaments(
            canvas = canvas,
            drop = drop,
            centerX = centerX,
            centerY = centerY,
            radius = radius,
            orientation = orientation,
            progress = progress,
            alpha = alpha,
            residualPass = residualPass
        )
    }

    private fun drawRipple(
        canvas: Canvas,
        drop: InkDrop,
        centerX: Float,
        centerY: Float,
        radius: Float,
        orientation: Float,
        progress: Float,
        alpha: Int,
        residualPass: Boolean
    ) {
        if (progress < 0.08f) return

        val ringProgress = ((progress - 0.08f) / 0.92f).coerceIn(0f, 1f)
        val ringAlphaScale = if (residualPass) 0.22f else 0.36f
        val ringAlpha = (alpha * (1f - ringProgress) * ringAlphaScale).toInt().coerceIn(0, 255)
        if (ringAlpha <= 0) return

        strokePaint.color = Color.argb(
            ringAlpha,
            Color.red(drop.color),
            Color.green(drop.color),
            Color.blue(drop.color)
        )
        strokePaint.strokeWidth = max(1f, radius * if (residualPass) 0.012f else 0.018f)
        buildOrganicBlobPath(
            path = blobPath,
            centerX = centerX,
            centerY = centerY,
            radius = radius * (0.92f + ringProgress * 0.72f),
            orientation = orientation + ringProgress * 0.55f,
            majorScale = drop.ovalScaleX + drop.stretch * 0.34f,
            minorScale = drop.ovalScaleY,
            phase = drop.wobblePhase + ringProgress * TWO_PI
        )
        canvas.drawPath(blobPath, strokePaint)
    }

    private fun drawFilaments(
        canvas: Canvas,
        drop: InkDrop,
        centerX: Float,
        centerY: Float,
        radius: Float,
        orientation: Float,
        progress: Float,
        alpha: Int,
        residualPass: Boolean
    ) {
        if (drop.tendrilCount <= 0) return

        val filamentAlpha = (alpha * if (residualPass) 0.34f else 0.48f).toInt().coerceIn(0, 255)
        if (filamentAlpha <= 0) return

        strokePaint.color = Color.argb(
            filamentAlpha,
            Color.red(drop.color),
            Color.green(drop.color),
            Color.blue(drop.color)
        )
        strokePaint.strokeWidth = max(0.8f, radius * if (residualPass) 0.012f else 0.018f)

        val baseAngle = when (drop.type) {
            InkDropType.MoveTrail -> orientation + PI
            InkDropType.ReleaseBloom -> orientation + progress * 1.6f
            InkDropType.TouchBloom -> orientation + drop.wobblePhase * 0.33f
            InkDropType.Tendril -> orientation
        }
        repeat(drop.tendrilCount) { index ->
            val offset = (index - (drop.tendrilCount - 1) * 0.5f) * 0.42f
            val angle = baseAngle + offset + sin(drop.wobblePhase + index * 1.7f + progress * 4.2f) * 0.24f
            val directionX = cos(angle)
            val directionY = sin(angle)
            val normalX = -directionY
            val normalY = directionX
            val startDistance = radius * (0.36f + index * 0.045f)
            val endDistance = radius * (0.88f + progress * 0.82f + (index % 3) * 0.12f)
            val wobble = sin(drop.wobblePhase * 1.2f + index * 2.3f + progress * 5.4f) *
                radius * 0.22f

            strokePath.reset()
            strokePath.moveTo(
                centerX + directionX * startDistance,
                centerY + directionY * startDistance
            )
            strokePath.cubicTo(
                centerX + directionX * (startDistance + endDistance * 0.28f) + normalX * wobble,
                centerY + directionY * (startDistance + endDistance * 0.28f) + normalY * wobble,
                centerX + directionX * (startDistance + endDistance * 0.68f) - normalX * wobble * 0.5f,
                centerY + directionY * (startDistance + endDistance * 0.68f) - normalY * wobble * 0.5f,
                centerX + directionX * endDistance,
                centerY + directionY * endDistance
            )
            canvas.drawPath(strokePath, strokePaint)
        }
    }

    private fun drawTendrilDrop(
        canvas: Canvas,
        drop: InkDrop,
        centerX: Float,
        centerY: Float,
        radius: Float,
        progress: Float,
        alpha: Int,
        residualPass: Boolean
    ) {
        val directionX = drop.directionX
        val directionY = drop.directionY
        val normalX = -directionY
        val normalY = directionX
        val wave = sin(drop.wobblePhase + progress * drop.wobbleSpeed)
        val length = radius * (0.8f + progress * 0.62f)
        val startDistance = radius * 0.12f
        val width = max(0.9f, radius * if (residualPass) 0.018f else 0.03f)

        strokePaint.color = Color.argb(
            alpha,
            Color.red(drop.color),
            Color.green(drop.color),
            Color.blue(drop.color)
        )
        strokePaint.strokeWidth = width
        strokePath.reset()
        strokePath.moveTo(
            centerX - directionX * startDistance,
            centerY - directionY * startDistance
        )
        strokePath.cubicTo(
            centerX + directionX * length * 0.28f + normalX * wave * radius * 0.22f,
            centerY + directionY * length * 0.28f + normalY * wave * radius * 0.22f,
            centerX + directionX * length * 0.62f - normalX * wave * radius * 0.16f,
            centerY + directionY * length * 0.62f - normalY * wave * radius * 0.16f,
            centerX + directionX * length,
            centerY + directionY * length
        )
        canvas.drawPath(strokePath, strokePaint)

        val beadAlpha = (alpha * 0.55f).toInt().coerceIn(0, 255)
        if (beadAlpha <= 0) return

        fillPaint.color = Color.argb(
            beadAlpha,
            Color.red(drop.color),
            Color.green(drop.color),
            Color.blue(drop.color)
        )
        buildOrganicBlobPath(
            path = blobPath,
            centerX = centerX + directionX * length,
            centerY = centerY + directionY * length,
            radius = radius * 0.16f,
            orientation = atan2(directionY, directionX),
            majorScale = 1.2f,
            minorScale = 0.72f,
            phase = drop.wobblePhase + progress * TWO_PI
        )
        canvas.drawPath(blobPath, fillPaint)
    }

    private fun buildOrganicBlobPath(
        path: Path,
        centerX: Float,
        centerY: Float,
        radius: Float,
        orientation: Float,
        majorScale: Float,
        minorScale: Float,
        phase: Float
    ) {
        val orientationCos = cos(orientation)
        val orientationSin = sin(orientation)
        path.reset()

        for (index in 0..BLOB_SEGMENTS) {
            val theta = index * TWO_PI / BLOB_SEGMENTS
            val thetaCos = cos(theta)
            val thetaSin = sin(theta)
            val wobble =
                1f +
                    sin(theta * 2.3f + phase) * 0.11f +
                    cos(theta * 4.1f - phase * 0.72f) * 0.075f
            val localX = thetaCos * radius * wobble * majorScale
            val localY = thetaSin * radius * wobble * minorScale
            val rotatedX = localX * orientationCos - localY * orientationSin
            val rotatedY = localX * orientationSin + localY * orientationCos
            val pointX = centerX + rotatedX
            val pointY = centerY + rotatedY
            if (index == 0) {
                path.moveTo(pointX, pointY)
            } else {
                path.lineTo(pointX, pointY)
            }
        }
        path.close()
    }

    private fun alphaFade(progress: Float): Float {
        val inverse = 1f - progress
        val wetIn = (0.35f + (progress / 0.14f).coerceIn(0f, 1f) * 0.65f)
        return wetIn * inverse * (0.42f + inverse * 0.58f)
    }

    private fun animateResidualSurface(now: Long) {
        if (residualInkEnergy <= MIN_RESIDUAL_ENERGY) {
            clearResidualSurface()
            residualInkEnergy = 0f
            return
        }

        val bitmap = residualBitmap ?: return
        val canvas = residualCanvas ?: return
        val scratchBitmap = residualScratchBitmap ?: return
        val scratchCanvas = residualScratchCanvas ?: return
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val time = now * 0.001f
        val driftX = sin(time * 0.19f) * 0.75f + cos(time * 0.11f) * 0.45f
        val driftY = cos(time * 0.17f) * 0.68f + sin(time * 0.13f) * 0.38f

        scratchCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        bitmapPaint.alpha = 255
        scratchCanvas.drawBitmap(bitmap, driftX, driftY, bitmapPaint)

        bitmapPaint.alpha = 9
        scratchCanvas.save()
        scratchCanvas.scale(
            1.0024f,
            1.0018f,
            width * 0.5f + sin(time * 0.09f) * 9f,
            height * 0.5f + cos(time * 0.1f) * 7f
        )
        scratchCanvas.drawBitmap(bitmap, -driftY * 0.8f, driftX * 0.8f, bitmapPaint)
        scratchCanvas.restore()

        bitmapPaint.alpha = 5
        scratchCanvas.drawBitmap(bitmap, -driftX * 1.4f, -driftY * 1.2f, bitmapPaint)

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        bitmapPaint.alpha = 255
        canvas.drawBitmap(scratchBitmap, 0f, 0f, bitmapPaint)

        val decay = if (drops.isEmpty()) RESIDUAL_IDLE_DECAY else RESIDUAL_ACTIVE_DECAY
        residualInkEnergy = (residualInkEnergy * decay).coerceAtLeast(
            RESIDUAL_PERSISTENT_ENERGY_FLOOR
        )
        if (residualInkEnergy <= MIN_RESIDUAL_ENERGY) {
            clearResidualSurface()
            residualInkEnergy = 0f
        }
    }

    private fun drawResidualSurface(canvas: Canvas) {
        val bitmap = residualBitmap ?: return
        if (residualInkEnergy <= MIN_RESIDUAL_ENERGY) return

        bitmapPaint.alpha = (RESIDUAL_SCREEN_ALPHA * residualInkEnergy.coerceIn(0.35f, 1f))
            .toInt()
            .coerceIn(0, 255)
        canvas.drawBitmap(bitmap, 0f, 0f, bitmapPaint)
        bitmapPaint.alpha = 255
    }

    private fun ensureResidualSurface() {
        val viewWidth = width
        val viewHeight = height
        if (viewWidth <= 0 || viewHeight <= 0) return

        val current = residualBitmap
        if (current != null &&
            !current.isRecycled &&
            current.width == viewWidth &&
            current.height == viewHeight
        ) {
            return
        }
        rebuildResidualSurface(viewWidth, viewHeight)
    }

    private fun rebuildResidualSurface(viewWidth: Int, viewHeight: Int) {
        recycleResidualSurface()
        if (viewWidth <= 0 || viewHeight <= 0) {
            residualInkEnergy = 0f
            return
        }

        residualBitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888)
        residualCanvas = Canvas(residualBitmap!!)
        residualScratchBitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888)
        residualScratchCanvas = Canvas(residualScratchBitmap!!)
        residualInkEnergy = 0f
    }

    private fun clearResidualSurface() {
        residualCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        residualScratchCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    }

    private fun recycleResidualSurface() {
        residualBitmap?.takeIf { !it.isRecycled }?.recycle()
        residualScratchBitmap?.takeIf { !it.isRecycled }?.recycle()
        residualBitmap = null
        residualCanvas = null
        residualScratchBitmap = null
        residualScratchCanvas = null
    }

    private fun emitBurst(x: Float, y: Float, @ColorInt color: Int, now: Long) {
        repeat(7) {
            emitDrop(
                x = x,
                y = y,
                color = color,
                now = now,
                type = InkDropType.TouchBloom,
                spreadPx = 20f,
                startRadiusMin = 8f,
                startRadiusMax = 17f,
                endRadiusMin = 70f,
                endRadiusMax = 142f,
                alphaMin = 34,
                alphaMax = 84,
                lifetimeMin = 2600L,
                lifetimeMax = 4600L,
                stretchMin = 0.1f,
                stretchMax = 0.52f,
                depositStrength = 1f,
                tendrilCountMin = 1,
                tendrilCountMax = 3
            )
        }
        repeat(4) {
            emitDrop(
                x = x,
                y = y,
                color = color,
                now = now,
                type = InkDropType.TouchBloom,
                spreadPx = 11f,
                startRadiusMin = 4f,
                startRadiusMax = 9f,
                endRadiusMin = 30f,
                endRadiusMax = 64f,
                alphaMin = 72,
                alphaMax = 132,
                lifetimeMin = 2100L,
                lifetimeMax = 3400L,
                stretchMin = 0.05f,
                stretchMax = 0.32f,
                depositStrength = 1.12f,
                tendrilCountMin = 0,
                tendrilCountMax = 2
            )
        }
        repeat(3) {
            val angle = random.nextFloat() * TWO_PI
            emitDrop(
                x = x,
                y = y,
                color = color,
                now = now,
                type = InkDropType.Tendril,
                spreadPx = 8f,
                startRadiusMin = 18f,
                startRadiusMax = 28f,
                endRadiusMin = 48f,
                endRadiusMax = 82f,
                alphaMin = 38,
                alphaMax = 82,
                lifetimeMin = 1900L,
                lifetimeMax = 3300L,
                directionX = cos(angle),
                directionY = sin(angle),
                stretchMin = 0.6f,
                stretchMax = 1.1f,
                depositStrength = 0.78f,
                tendrilCountMin = 0,
                tendrilCountMax = 0
            )
        }
    }

    private fun emitTrail(
        x: Float,
        y: Float,
        @ColorInt color: Int,
        now: Long,
        directionX: Float,
        directionY: Float,
        distance: Float
    ) {
        val movementScale = (distance / 42f).coerceIn(0.65f, 1.6f)
        repeat(2) {
            emitDrop(
                x = x,
                y = y,
                color = color,
                now = now,
                type = InkDropType.MoveTrail,
                spreadPx = 7f,
                startRadiusMin = 4f,
                startRadiusMax = 9f,
                endRadiusMin = 34f * movementScale,
                endRadiusMax = 78f * movementScale,
                alphaMin = 42,
                alphaMax = 98,
                lifetimeMin = 1700L,
                lifetimeMax = 3100L,
                directionX = directionX,
                directionY = directionY,
                stretchMin = 0.72f,
                stretchMax = 1.48f,
                depositStrength = 0.95f,
                tendrilCountMin = 1,
                tendrilCountMax = 3
            )
        }
        emitDrop(
            x = x,
            y = y,
            color = color,
            now = now,
            type = InkDropType.Tendril,
            spreadPx = 5f,
            startRadiusMin = 14f,
            startRadiusMax = 24f,
            endRadiusMin = 38f * movementScale,
            endRadiusMax = 72f * movementScale,
            alphaMin = 34,
            alphaMax = 76,
            lifetimeMin = 1500L,
            lifetimeMax = 2700L,
            directionX = -directionX,
            directionY = -directionY,
            stretchMin = 0.8f,
            stretchMax = 1.4f,
            depositStrength = 0.72f,
            tendrilCountMin = 0,
            tendrilCountMax = 0
        )
    }

    private fun emitReleaseBloom(
        x: Float,
        y: Float,
        @ColorInt color: Int,
        now: Long,
        directionX: Float,
        directionY: Float,
        isCancel: Boolean = false
    ) {
        val alphaOffset = if (isCancel) -18 else 0
        repeat(if (isCancel) 3 else 5) {
            emitDrop(
                x = x,
                y = y,
                color = color,
                now = now,
                type = InkDropType.ReleaseBloom,
                spreadPx = if (isCancel) 9f else 13f,
                startRadiusMin = 5f,
                startRadiusMax = 11f,
                endRadiusMin = if (isCancel) 46f else 58f,
                endRadiusMax = if (isCancel) 96f else 126f,
                alphaMin = max(18, 42 + alphaOffset),
                alphaMax = max(42, 102 + alphaOffset),
                lifetimeMin = if (isCancel) 2100L else 2500L,
                lifetimeMax = if (isCancel) 3600L else 4700L,
                directionX = directionX,
                directionY = directionY,
                stretchMin = 0.18f,
                stretchMax = 0.68f,
                depositStrength = 1f,
                tendrilCountMin = 1,
                tendrilCountMax = 3
            )
        }
        repeat(if (isCancel) 1 else 2) {
            emitDrop(
                x = x,
                y = y,
                color = color,
                now = now,
                type = InkDropType.Tendril,
                spreadPx = 7f,
                startRadiusMin = 18f,
                startRadiusMax = 27f,
                endRadiusMin = 48f,
                endRadiusMax = 84f,
                alphaMin = max(18, 32 + alphaOffset),
                alphaMax = max(36, 72 + alphaOffset),
                lifetimeMin = 1800L,
                lifetimeMax = 3200L,
                directionX = directionX,
                directionY = directionY,
                stretchMin = 0.6f,
                stretchMax = 1.15f,
                depositStrength = 0.68f,
                tendrilCountMin = 0,
                tendrilCountMax = 0
            )
        }
    }

    private fun emitDrop(
        x: Float,
        y: Float,
        @ColorInt color: Int,
        now: Long,
        type: InkDropType,
        spreadPx: Float,
        startRadiusMin: Float,
        startRadiusMax: Float,
        endRadiusMin: Float,
        endRadiusMax: Float,
        alphaMin: Int,
        alphaMax: Int,
        lifetimeMin: Long,
        lifetimeMax: Long,
        directionX: Float? = null,
        directionY: Float? = null,
        stretchMin: Float,
        stretchMax: Float,
        depositStrength: Float,
        tendrilCountMin: Int,
        tendrilCountMax: Int
    ) {
        if (drops.size >= MAX_DROPS) {
            drops.removeAt(0)
        }

        val angle = random.nextFloat() * TWO_PI
        val distance = random.nextFloat() * spreadPx
        val driftAngle = random.nextFloat() * TWO_PI
        val driftDistance = 14f + random.nextFloat() * 48f
        val randomDirectionAngle = random.nextFloat() * TWO_PI
        val normalizedDirection = normalizeDirection(
            directionX ?: cos(randomDirectionAngle),
            directionY ?: sin(randomDirectionAngle)
        )
        drops.add(
            InkDrop(
                x = x + cos(angle) * distance,
                y = y + sin(angle) * distance,
                color = color,
                startTimeMillis = now,
                lifetimeMillis = randomLong(lifetimeMin, lifetimeMax),
                startRadius = randomFloat(startRadiusMin, startRadiusMax),
                endRadius = randomFloat(endRadiusMin, endRadiusMax),
                driftX = cos(driftAngle) * driftDistance,
                driftY = sin(driftAngle) * driftDistance,
                wobblePhase = random.nextFloat() * TWO_PI,
                wobbleSpeed = 3.4f + random.nextFloat() * 6.2f,
                ovalScaleX = 0.78f + random.nextFloat() * 0.54f,
                ovalScaleY = 0.68f + random.nextFloat() * 0.58f,
                baseAlpha = randomInt(alphaMin, alphaMax),
                directionX = normalizedDirection.first,
                directionY = normalizedDirection.second,
                stretch = randomFloat(stretchMin, stretchMax),
                type = type,
                depositStrength = depositStrength,
                tendrilCount = randomInt(tendrilCountMin, tendrilCountMax)
            )
        )
    }

    private fun canEmitInk(): Boolean {
        return effectEnabled && visibility == VISIBLE
    }

    private fun hasRenderableInk(): Boolean {
        return drops.isNotEmpty() || residualInkEnergy > MIN_RESIDUAL_ENERGY
    }

    @ColorInt
    private fun resolveInkColor(): Int {
        if (colorMode == COLOR_MODE_FIXED) {
            return fixedInkColor
        }

        hsv[0] = random.nextInt(360).toFloat()
        hsv[1] = 0.52f + random.nextFloat() * 0.38f
        hsv[2] = 0.28f + random.nextFloat() * 0.46f
        return withoutAlpha(Color.HSVToColor(hsv))
    }

    private fun scheduleNextFrame() {
        if (frameScheduled || !effectEnabled || visibility != VISIBLE || !hasRenderableInk()) {
            return
        }
        frameScheduled = true
        postInvalidateOnAnimation()
    }

    private fun normalizeDirection(x: Float, y: Float): Pair<Float, Float> {
        val length = sqrt(x * x + y * y)
        if (length <= 0.01f) {
            val angle = random.nextFloat() * TWO_PI
            return cos(angle) to sin(angle)
        }
        return x / length to y / length
    }

    private fun randomFloat(min: Float, max: Float): Float {
        return min + random.nextFloat() * (max - min)
    }

    private fun randomInt(min: Int, max: Int): Int {
        return if (max <= min) min else min + random.nextInt(max - min + 1)
    }

    private fun randomLong(min: Long, max: Long): Long {
        return min + (random.nextDouble() * (max - min)).toLong()
    }

    private fun normalizeColorMode(value: String): String {
        return if (value == COLOR_MODE_FIXED) COLOR_MODE_FIXED else COLOR_MODE_RANDOM
    }

    @ColorInt
    private fun withoutAlpha(@ColorInt color: Int): Int {
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
    }

    @VisibleForTesting
    internal fun dropCountForTesting(): Int = drops.size

    @VisibleForTesting
    internal fun pointerStateCountForTesting(): Int = pointerStates.size()

    @VisibleForTesting
    internal fun hasResidualInkForTesting(): Boolean = residualInkEnergy > MIN_RESIDUAL_ENERGY

    @VisibleForTesting
    internal fun clearActiveDropsForTesting() {
        drops.clear()
    }

    private companion object {
        private const val MAX_DROPS = 160
        private const val MAX_RESIDUAL_DEPOSITS_PER_FRAME = 36
        private const val MOVE_EMIT_INTERVAL_MS = 24L
        private const val MOVE_EMIT_DISTANCE_PX = 12f
        private const val COLOR_MODE_RANDOM = "random"
        private const val COLOR_MODE_FIXED = "fixed"
        private const val TWO_PI = 6.2831855f
        private const val PI = 3.1415927f
        private const val BLOB_SEGMENTS = 16
        private const val RESIDUAL_DEPOSIT_ALPHA_SCALE = 0.12f
        private const val RESIDUAL_ENERGY_PER_DEPOSIT = 0.0065f
        private const val RESIDUAL_SCREEN_ALPHA = 218f
        private const val RESIDUAL_ACTIVE_DECAY = 1f
        private const val RESIDUAL_IDLE_DECAY = 1f
        private const val RESIDUAL_PERSISTENT_ENERGY_FLOOR = 0.18f
        private const val MIN_RESIDUAL_ENERGY = 0.012f

        @ColorInt
        private val DEFAULT_INK_COLOR = Color.rgb(17, 17, 17)
    }
}
