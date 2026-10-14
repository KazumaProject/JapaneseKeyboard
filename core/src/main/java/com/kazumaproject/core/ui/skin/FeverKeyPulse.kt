package com.kazumaproject.core.ui.skin

import android.animation.ValueAnimator
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.SystemClock
import java.util.WeakHashMap

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

/** Animated color stays at the cap perimeter; the central label surface never changes. */
internal class FeverKeyDrawable(private val base: Drawable, private val dp: Float, private val radius: Float) : Drawable(), Drawable.Callback {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val frame = Runnable { invalidateSelf() }
    private var opacity = 255
    private var edgeColor: Int? = null
    private var edgeShaders = emptyList<Shader>()
    private var glint: Shader? = null
    private val rotation = Matrix()
    init { base.callback = this; FeverKeyPulse.register(this) }
    override fun onBoundsChange(bounds: Rect) { base.bounds = bounds; edgeColor = null }
    override fun getConstantState(): ConstantState? = base.constantState?.let { state ->
        object : ConstantState() {
            override fun newDrawable(): Drawable = FeverKeyDrawable(state.newDrawable().mutate(), dp, radius)
            override fun newDrawable(resources: android.content.res.Resources?): Drawable {
                val density = resources?.displayMetrics?.density ?: dp
                return FeverKeyDrawable(state.newDrawable(resources).mutate(), density, radius * density / dp)
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
        base.draw(canvas)
        val now = SystemClock.uptimeMillis()
        unscheduleSelf(frame)
        if (FeverKeyPulse.started == 0L || now >= FeverKeyPulse.until || bounds.isEmpty) return
        val moving = android.os.Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
        val age = (now - FeverKeyPulse.started).coerceAtLeast(0)
        val fade = minOf(1f, (FeverKeyPulse.until - now) / 240f)
        val rect = RectF(bounds)
        canvas.save()
        path.reset(); path.addRoundRect(rect, radius, radius, Path.Direction.CW); canvas.clipPath(path)
        // Four edge gradients leave the middle 56% completely unchanged, including small flick labels.
        paint.style = Paint.Style.FILL
        val accent = FeverKeyPulse.color
        val amount = fade * (.19f + FeverKeyPulse.strength * .11f)
        val insetX = rect.width() * .22f; val insetY = rect.height() * .22f
        if (edgeColor != accent) {
            edgeColor = accent
            glint = SweepGradient(rect.centerX(), rect.centerY(), intArrayOf(Color.TRANSPARENT, accent, Color.WHITE, accent, Color.TRANSPARENT), null)
            edgeShaders = listOf(
                LinearGradient(rect.left, rect.centerY(), rect.left + insetX, rect.centerY(), accent, Color.TRANSPARENT, Shader.TileMode.CLAMP),
                LinearGradient(rect.right, rect.centerY(), rect.right - insetX, rect.centerY(), accent, Color.TRANSPARENT, Shader.TileMode.CLAMP),
                LinearGradient(rect.centerX(), rect.top, rect.centerX(), rect.top + insetY, accent, Color.TRANSPARENT, Shader.TileMode.CLAMP),
                LinearGradient(rect.centerX(), rect.bottom, rect.centerX(), rect.bottom - insetY, accent, Color.TRANSPARENT, Shader.TileMode.CLAMP))
        }
        paint.alpha = (opacity * amount).toInt()
        edgeShaders.forEach { shader -> paint.shader = shader; canvas.drawRect(rect, paint) }
        paint.shader = null; paint.style = Paint.Style.STROKE
        val edge = RectF(rect).apply { inset(1.2f * dp, 1.2f * dp) }
        paint.color = accent; paint.alpha = (opacity * fade * .95f).toInt(); paint.strokeWidth = 2.4f * dp
        canvas.drawRoundRect(edge, radius, radius, paint)
        if (moving) {
            val phase = (age / 560f + FeverKeyPulse.sequence * .13f) % 1f
            rotation.setRotate(phase * 360, rect.centerX(), rect.centerY())
            glint?.setLocalMatrix(rotation); paint.shader = glint
            paint.alpha = (opacity * fade).toInt(); paint.strokeWidth = dp
            canvas.drawRoundRect(edge, radius, radius, paint)
        }
        paint.shader = null; canvas.restore()
        scheduleSelf(frame, if (moving) now + 16 else FeverKeyPulse.until)
    }
}
