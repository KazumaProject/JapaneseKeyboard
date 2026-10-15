package com.kazumaproject.core.ui.skin

import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.SystemClock
import java.util.WeakHashMap
import kotlin.math.*

/** Shared, transient lighting beneath key labels. Only FEVER drawables subscribe. UI thread only. */
object FeverKeyPulse {
    private val keys = WeakHashMap<FeverKeyDrawable, Boolean>()
    internal var started = 0L; private set
    internal var until = 0L; private set
    internal var color = Color.CYAN; private set
    internal var strength = 2; private set
    internal var sequence = 0; private set
    private var celebrationUntil = 0L
    internal fun register(key: FeverKeyDrawable) { keys[key] = true }
    fun pulse(accent: Int, intensity: Int, celebration: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        started = now; sequence++; strength = intensity.coerceIn(0, 2)
        if (celebration) celebrationUntil = now + 2000
        color = if (now < celebrationUntil && !celebration) color else accent
        until = maxOf(now + 560, celebrationUntil)
        keys.keys.toList().forEach { it.invalidateSelf() }
    }
    fun clear() {
        started = 0; until = 0; celebrationUntil = 0
        keys.keys.toList().forEach { it.invalidateSelf() }
    }
}

/** Seven simultaneous hues, closed seamlessly around each cap and popup. */
object FeverSpectrum {
    val colors = intArrayOf(0xffff497c.toInt(), 0xffff972e.toInt(), 0xffffec63.toInt(),
        0xff62faaa.toInt(), 0xff51deff.toInt(), 0xff6485ff.toInt(), 0xffcb73ff.toInt(), 0xffff497c.toInt())
}

/** A fine rainbow rim and travelling highlights. Uses frame time even when animator scale is zero. */
internal class FeverKeyDrawable(private val base: Drawable, private val dp: Float, private val radius: Float, private val popup: Boolean = false) : Drawable(), Drawable.Callback {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val frame = Runnable { invalidateSelf() }
    private var opacity = 255
    private val measure = PathMeasure()
    private val segment = Path()
    private val point = FloatArray(2)
    private val cap = RectF()
    private var cachedBounds = Rect()
    private var glint: Shader? = null
    private val rotation = Matrix()
    init { base.callback = this; FeverKeyPulse.register(this) }
    override fun onBoundsChange(bounds: Rect) { base.bounds = bounds; cachedBounds.setEmpty() }
    override fun getConstantState(): ConstantState? = base.constantState?.let { state ->
        object : ConstantState() {
            override fun newDrawable(): Drawable = FeverKeyDrawable(state.newDrawable().mutate(), dp, radius, popup)
            override fun newDrawable(resources: android.content.res.Resources?): Drawable {
                val density = resources?.displayMetrics?.density ?: dp
                return FeverKeyDrawable(state.newDrawable(resources).mutate(), density, radius * density / dp, popup)
            }
            override fun getChangingConfigurations() = state.changingConfigurations
        }
    }
    override fun mutate(): Drawable { base.mutate(); return this }
    override fun getPadding(padding: Rect) = base.getPadding(padding)
    override fun onLevelChange(level: Int) = base.setLevel(level)
    override fun onLayoutDirectionChanged(direction: Int) = base.setLayoutDirection(direction)
    override fun jumpToCurrentState() { base.jumpToCurrentState() }
    override fun isStateful() = base.isStateful
    override fun onStateChange(state: IntArray) = base.setState(state)
    override fun setAlpha(alpha: Int) { opacity = alpha; base.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { base.colorFilter = filter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun invalidateDrawable(who: Drawable) { invalidateSelf() }
    override fun scheduleDrawable(who: Drawable, what: Runnable, whenMillis: Long) { scheduleSelf(what, whenMillis) }
    override fun unscheduleDrawable(who: Drawable, what: Runnable) { unscheduleSelf(what) }
    override fun draw(canvas: Canvas) {
        unscheduleSelf(frame)
        if (bounds.isEmpty || !isVisible) return
        val now = SystemClock.uptimeMillis()
        val active = FeverKeyPulse.started > 0 && now < FeverKeyPulse.until
        val age = (now - FeverKeyPulse.started).coerceAtLeast(0)
        val energy = if (popup) 1f else if (active) minOf(1f, (FeverKeyPulse.until - now) / 180f) else 0f
        if (cachedBounds != bounds) {
            cachedBounds.set(bounds)
            cap.set(bounds); cap.inset(1.5f * dp, 1.5f * dp)
            path.reset(); path.addRoundRect(cap, radius, radius, Path.Direction.CW)
            measure.setPath(path, true)
            glint = SweepGradient(cap.centerX(), cap.centerY(), FeverSpectrum.colors, null)
        }
        canvas.save()
        if (popup && active && age < 240) {
            // Pop the painted cap only; popup geometry and touch ownership remain fixed.
            val t = age / 240f
            val scale = 1 - .14f * exp(-t * 6) * cos(t * 10)
            canvas.scale(scale, scale, cap.centerX(), cap.centerY())
        }
        base.draw(canvas)
        paint.style = Paint.Style.STROKE; paint.strokeJoin = Paint.Join.ROUND; paint.strokeCap = Paint.Cap.ROUND
        rotation.setRotate(if (active || popup) (now % 1600) / 1600f * 360 else 0f, cap.centerX(), cap.centerY())
        glint?.setLocalMatrix(rotation); paint.shader = glint
        // One fine core plus a low-opacity halo, never the former broad filled edge bands.
        paint.alpha = (opacity * energy * .16f).toInt(); paint.strokeWidth = 2.6f * dp
        if (energy > 0) canvas.drawPath(path, paint)
        paint.alpha = (opacity * (.9f + energy * .1f)).toInt(); paint.strokeWidth = (.75f + energy * .2f) * dp
        canvas.drawPath(path, paint); paint.shader = null
        if (active || popup) {
            val distance = ((now % 720) / 720f + FeverKeyPulse.sequence * .073f) % 1f * measure.length
            repeat(if (popup) 3 else 2) { index ->
                val head = (distance + index * measure.length / 2.7f) % measure.length
                val tail = head - minOf(measure.length * .17f, 35 * dp)
                segment.reset()
                if (tail < 0) measure.getSegment(measure.length + tail, measure.length, segment, true)
                measure.getSegment(maxOf(0f, tail), head, segment, true)
                paint.color = FeverSpectrum.colors[index * 2]; paint.alpha = (opacity * energy * .65f).toInt()
                paint.strokeWidth = dp; canvas.drawPath(segment, paint)
                measure.getPosTan(head, point, null)
                paint.color = Color.WHITE; paint.alpha = (opacity * energy * .85f).toInt(); paint.strokeWidth = .8f * dp
                canvas.drawLine(point[0] - 1.5f * dp, point[1], point[0] + 1.5f * dp, point[1], paint)
                if (popup) {
                    val size = (1.4f + .7f * sin(now / 100f + index)) * dp
                    canvas.drawLine(point[0], point[1] - size, point[0], point[1] + size, paint)
                }
            }
        }
        canvas.restore()
        if (active || popup) scheduleSelf(frame, now + 16)
    }
}
