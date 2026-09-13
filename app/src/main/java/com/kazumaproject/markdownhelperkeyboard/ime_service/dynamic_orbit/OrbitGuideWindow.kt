package com.kazumaproject.markdownhelperkeyboard.ime_service.dynamic_orbit

import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.*
import com.kazumaproject.markdownhelperkeyboard.R
import kotlin.math.roundToInt

internal data class OrbitColors(val background: Int, val key: Int, val text: Int, val accent: Int) {
    val onAccent = if (androidx.core.graphics.ColorUtils.calculateLuminance(accent) > 0.179) Color.BLACK else Color.WHITE
}

/** A display-only IME child window: it cannot steal a pointer or the editor's input connection. */
internal class OrbitGuideWindow(private val anchor: View, private val windowAnchor: () -> View?) {
    private val manager = anchor.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val guide = OrbitGuideView(anchor.context)
    private var params: WindowManager.LayoutParams? = null

    fun show(snapshot: OrbitSnapshot, originX: Float, originY: Float, colors: OrbitColors): Boolean {
        if (!anchor.isAttachedToWindow || snapshot.phase == OrbitPhase.IDLE) { hide(); return false }
        val host = windowAnchor()?.takeIf { it.isAttachedToWindow } ?: return false
        val density = anchor.resources.displayMetrics.density
        val area = safeArea()
        val size = (216 * density).roundToInt().coerceAtMost(area.width()).coerceAtMost(area.height())
        if (size <= 0) return false
        guide.snapshot = snapshot
        guide.colors = colors
        val p = params ?: WindowManager.LayoutParams(size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT).apply {
            token = host.windowToken
            gravity = Gravity.TOP or Gravity.LEFT
            setTitle("Dynamic Orbit guide")
        }
        val x = (originX - size / 2).roundToInt().coerceIn(area.left, area.right - size)
        val y = (originY - size - 24 * density).roundToInt().coerceIn(area.top, area.bottom - size)
        val changed = p.x != x || p.y != y || p.width != size || p.height != size
        p.x = x; p.y = y; p.width = size; p.height = size
        return try {
            if (guide.parent == null) manager.addView(guide, p)
            else if (changed) manager.updateViewLayout(guide, p)
            params = p
            guide.invalidate()
            true
        } catch (_: WindowManager.BadTokenException) { hide(); false }
        catch (_: IllegalArgumentException) { hide(); false }
    }

    fun hide() {
        if (guide.parent != null) runCatching { manager.removeViewImmediate(guide) }
        params = null
    }

    @Suppress("DEPRECATION")
    private fun safeArea(): Rect {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = manager.currentWindowMetrics
            val inset = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return Rect(metrics.bounds).apply { left += inset.left; top += inset.top; right -= inset.right; bottom -= inset.bottom }
        }
        val size = Point().also { manager.defaultDisplay.getRealSize(it) }
        val inset = anchor.rootWindowInsets
        return Rect(inset?.stableInsetLeft ?: 0, inset?.stableInsetTop ?: 0,
            size.x - (inset?.stableInsetRight ?: 0), size.y - (inset?.stableInsetBottom ?: 0))
    }
}

internal class OrbitGuideView(context: Context) : View(context) {
    var snapshot = OrbitSnapshot()
    var colors = OrbitColors(Color.WHITE, Color.LTGRAY, Color.BLACK, Color.BLUE)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = width / 216f
        canvas.save(); canvas.scale(scale, scale)
        paint.color = colors.background; paint.style = Paint.Style.FILL
        canvas.drawRoundRect(1f, 1f, 215f, 215f, 22f, 22f, paint)
        paint.color = colors.accent; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f
        canvas.drawRoundRect(1f, 1f, 215f, 215f, 22f, 22f, paint)
        canvas.translate(108f, 108f)
        for (radius in listOf(14f, 36f, 72f)) {
            paint.color = if (radius == 72f) colors.accent else colors.key
            canvas.drawCircle(0f, 0f, radius * 1.2f, paint)
        }
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER
        val returning = snapshot.phase == OrbitPhase.RETURN
        if (snapshot.phase == OrbitPhase.VOWEL || returning) {
            OrbitGeometry.vowelAngles.forEachIndexed { vowel, angle ->
                val letter = OrbitGeometry.letter(snapshot.row, vowel)
                val p = OrbitGeometry.point(55f * 1.2f, angle)
                label(canvas, letter?.toString() ?: "·", p, snapshot.vowel == vowel, letter != null)
            }
        } else {
            OrbitGeometry.rows.forEachIndexed { row, chars ->
                label(canvas, chars.first().toString(), OrbitGeometry.point(55f * 1.2f, row * 36f), snapshot.row == row, true)
            }
        }
        paint.color = colors.accent; paint.textSize = 21f; paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(if (returning) "↩" else snapshot.preview?.toString() ?: "·", 0f, 7f, paint)
        val point = snapshot.point
        val radius = point.radius
        val factor = if (radius > 78f) 78f / radius else 1f
        paint.color = colors.accent
        canvas.drawCircle(point.x * factor * 1.2f, point.y * factor * 1.2f, 3f, paint)
        paint.color = colors.text; paint.textSize = 10f; paint.typeface = Typeface.DEFAULT
        canvas.drawText(context.getString(if (returning) R.string.orbit_return_hint else if (snapshot.phase == OrbitPhase.VOWEL)
            R.string.orbit_vowel_hint else R.string.orbit_row_hint), 0f, -94f, paint)
        canvas.restore()
    }
    private fun label(canvas: Canvas, text: String, point: OrbitPoint, selected: Boolean, valid: Boolean) {
        if (selected) {
            paint.color = colors.accent
            canvas.drawCircle(point.x, point.y, 14f, paint)
        }
        paint.textSize = 17f; paint.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        paint.color = if (selected) colors.onAccent else colors.text
        paint.alpha = if (valid) 255 else 85
        canvas.drawText(text, point.x, point.y + 6f, paint)
        paint.alpha = 255
    }
}
