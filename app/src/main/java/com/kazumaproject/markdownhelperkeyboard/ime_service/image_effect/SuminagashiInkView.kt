package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.util.SparseArray
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

class SuminagashiInkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

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
        val baseAlpha: Int
    )

    private class PointerInkState(
        @ColorInt val color: Int,
        var lastEmitTimeMillis: Long,
        var lastX: Float,
        var lastY: Float
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ovalRect = RectF()
    private val random = Random()
    private val hsv = FloatArray(3)
    private val drops = ArrayList<InkDrop>(MAX_DROPS)
    private val pointerStates = SparseArray<PointerInkState>()

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
    }

    fun onPointerDown(pointerId: Int, x: Float, y: Float) {
        if (!canEmitInk()) return

        val now = SystemClock.uptimeMillis()
        val color = resolveInkColor()
        pointerStates.put(
            pointerId,
            PointerInkState(
                color = color,
                lastEmitTimeMillis = now,
                lastX = x,
                lastY = y
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

        state.lastEmitTimeMillis = now
        state.lastX = x
        state.lastY = y
        emitTrail(x, y, state.color, now)
        scheduleNextFrame()
    }

    fun onPointerUp(pointerId: Int) {
        pointerStates.remove(pointerId)
    }

    fun onCancel() {
        pointerStates.clear()
    }

    fun clearInk() {
        drops.clear()
        pointerStates.clear()
        frameScheduled = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (!effectEnabled || visibility != VISIBLE) {
            frameScheduled = false
            return
        }

        frameScheduled = false
        val now = SystemClock.uptimeMillis()
        var index = drops.size - 1
        while (index >= 0) {
            val drop = drops[index]
            val ageMillis = now - drop.startTimeMillis
            if (ageMillis >= drop.lifetimeMillis) {
                drops.removeAt(index)
                index -= 1
                continue
            }

            drawDrop(canvas, drop, ageMillis)
            index -= 1
        }

        if (drops.isNotEmpty()) {
            scheduleNextFrame()
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView == this && visibility != VISIBLE) {
            clearInk()
        }
    }

    override fun onDetachedFromWindow() {
        clearInk()
        super.onDetachedFromWindow()
    }

    private fun drawDrop(canvas: Canvas, drop: InkDrop, ageMillis: Long) {
        val progress = (ageMillis.toFloat() / drop.lifetimeMillis).coerceIn(0f, 1f)
        val inverse = 1f - progress
        val eased = 1f - inverse * inverse
        val radius = drop.startRadius + (drop.endRadius - drop.startRadius) * eased
        val wave = sin(drop.wobblePhase + progress * drop.wobbleSpeed)
        val crossWave = cos(drop.wobblePhase * 0.7f + progress * drop.wobbleSpeed * 1.3f)
        val centerX = drop.x + drop.driftX * progress + wave * radius * 0.07f
        val centerY = drop.y + drop.driftY * progress + crossWave * radius * 0.05f
        val alpha = (drop.baseAlpha * inverse * inverse).toInt().coerceIn(0, 255)
        if (alpha <= 0) return

        val red = Color.red(drop.color)
        val green = Color.green(drop.color)
        val blue = Color.blue(drop.color)

        paint.color = Color.argb(alpha, red, green, blue)
        ovalRect.set(
            centerX - radius * drop.ovalScaleX,
            centerY - radius * drop.ovalScaleY,
            centerX + radius * drop.ovalScaleX,
            centerY + radius * drop.ovalScaleY
        )
        canvas.drawOval(ovalRect, paint)

        val innerRadius = radius * 0.42f
        val innerAlpha = (alpha * 0.72f).toInt().coerceIn(0, 255)
        if (innerAlpha <= 0) return

        paint.color = Color.argb(innerAlpha, red, green, blue)
        ovalRect.set(
            centerX - innerRadius * drop.ovalScaleY,
            centerY - innerRadius * drop.ovalScaleX,
            centerX + innerRadius * drop.ovalScaleY,
            centerY + innerRadius * drop.ovalScaleX
        )
        canvas.drawOval(ovalRect, paint)
    }

    private fun emitBurst(x: Float, y: Float, @ColorInt color: Int, now: Long) {
        repeat(10) {
            emitDrop(
                x = x,
                y = y,
                color = color,
                now = now,
                spreadPx = 18f,
                startRadiusMin = 8f,
                startRadiusMax = 16f,
                endRadiusMin = 48f,
                endRadiusMax = 112f,
                alphaMin = 36,
                alphaMax = 82,
                lifetimeMin = 1250L,
                lifetimeMax = 1800L
            )
        }
        repeat(5) {
            emitDrop(
                x = x,
                y = y,
                color = color,
                now = now,
                spreadPx = 10f,
                startRadiusMin = 4f,
                startRadiusMax = 9f,
                endRadiusMin = 20f,
                endRadiusMax = 44f,
                alphaMin = 72,
                alphaMax = 124,
                lifetimeMin = 1050L,
                lifetimeMax = 1450L
            )
        }
    }

    private fun emitTrail(x: Float, y: Float, @ColorInt color: Int, now: Long) {
        repeat(2) {
            emitDrop(
                x = x,
                y = y,
                color = color,
                now = now,
                spreadPx = 8f,
                startRadiusMin = 4f,
                startRadiusMax = 9f,
                endRadiusMin = 24f,
                endRadiusMax = 62f,
                alphaMin = 42,
                alphaMax = 92,
                lifetimeMin = 950L,
                lifetimeMax = 1450L
            )
        }
    }

    private fun emitDrop(
        x: Float,
        y: Float,
        @ColorInt color: Int,
        now: Long,
        spreadPx: Float,
        startRadiusMin: Float,
        startRadiusMax: Float,
        endRadiusMin: Float,
        endRadiusMax: Float,
        alphaMin: Int,
        alphaMax: Int,
        lifetimeMin: Long,
        lifetimeMax: Long
    ) {
        if (drops.size >= MAX_DROPS) {
            drops.removeAt(0)
        }

        val angle = random.nextFloat() * TWO_PI
        val distance = random.nextFloat() * spreadPx
        val driftAngle = random.nextFloat() * TWO_PI
        val driftDistance = 8f + random.nextFloat() * 34f
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
                wobbleSpeed = 3.5f + random.nextFloat() * 5.5f,
                ovalScaleX = 0.78f + random.nextFloat() * 0.58f,
                ovalScaleY = 0.72f + random.nextFloat() * 0.66f,
                baseAlpha = randomInt(alphaMin, alphaMax)
            )
        )
    }

    private fun canEmitInk(): Boolean {
        return effectEnabled && visibility == VISIBLE
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
        if (frameScheduled || drops.isEmpty()) return
        frameScheduled = true
        postInvalidateOnAnimation()
    }

    private fun randomFloat(min: Float, max: Float): Float {
        return min + random.nextFloat() * (max - min)
    }

    private fun randomInt(min: Int, max: Int): Int {
        return min + random.nextInt(max - min + 1)
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

    private companion object {
        private const val MAX_DROPS = 160
        private const val MOVE_EMIT_INTERVAL_MS = 20L
        private const val MOVE_EMIT_DISTANCE_PX = 10f
        private const val COLOR_MODE_RANDOM = "random"
        private const val COLOR_MODE_FIXED = "fixed"
        private const val TWO_PI = 6.2831855f

        @ColorInt
        private val DEFAULT_INK_COLOR = Color.rgb(17, 17, 17)
    }
}
