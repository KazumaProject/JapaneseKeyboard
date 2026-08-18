package com.kazumaproject.core.data.keyboard

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Renderer for validated v1 definitions. It only consumes immutable compiled data. */
internal class ImportedKeyboardSkinRenderer(
    private val skinSpec: KeyboardSkinSpec,
) : KeyboardSkinRenderer {
    override val spec: KeyboardSkinSpec = skinSpec

    override fun createKeyDrawable(
        context: Context,
        role: KeyboardElementRole,
        stableKey: Int,
    ): Drawable = ImportedKeyboardSkinDrawable(
        context = context.applicationContext,
        spec = spec,
        style = spec.keyStyles[role] ?: spec.keyStyles[KeyboardElementRole.CHARACTER]
            ?: KeyboardSkinShapeStyle(),
        stableKey = stableKey,
    )

    override fun createSurfaceDrawable(
        context: Context,
        role: KeyboardSurfaceRole,
    ): Drawable = ImportedKeyboardSkinDrawable(
        context = context.applicationContext,
        spec = spec,
        style = spec.surfaceStyles[role]
            ?: spec.surfaceStyles[KeyboardSurfaceRole.DECK]
            ?: KeyboardSkinShapeStyle(),
        stableKey = role.ordinal,
    )

    override fun createPopupDrawable(
        context: Context,
        kind: KeyboardSkinPopupKind,
        direction: KeyboardSkinPopupDirection,
        selected: Boolean,
    ): Drawable = checkNotNull(
        KeyboardSkinPopupRenderer.createDrawable(
            context = context,
            skinId = spec.reference,
            kind = kind,
            direction = direction,
            selected = selected,
        )
    )
}

private class ImportedKeyboardSkinDrawable(
    private val context: Context,
    private val spec: KeyboardSkinSpec,
    private val style: KeyboardSkinShapeStyle,
    private val stableKey: Int,
) : Drawable(), PhasedKeyboardSkinDrawable {
    private val density = context.resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val decorationPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val boundsF = RectF()
    private val workBounds = RectF()
    private val shaderMatrix = Matrix()
    private var shader: Shader? = null
    private var pressed = false
    private var enabled = true
    private var phase = 0f
    private var drawableAlpha = 255
    private var colorFilter: ColorFilter? = null

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val nextPressed = state.contains(android.R.attr.state_pressed)
        val nextEnabled = state.isEmpty() || state.contains(android.R.attr.state_enabled)
        if (nextPressed == pressed && nextEnabled == enabled) return false
        pressed = nextPressed
        enabled = nextEnabled
        invalidateSelf()
        return true
    }

    override fun onBoundsChange(bounds: Rect) {
        boundsF.set(bounds)
        rebuildPath()
        shader = createShader()
    }

    override fun draw(canvas: Canvas) {
        if (boundsF.isEmpty) return
        val baseAlpha = if (enabled) drawableAlpha else (drawableAlpha * 0.48f).toInt()
        fillPaint.alpha = baseAlpha
        fillPaint.colorFilter = colorFilter
        strokePaint.alpha = baseAlpha
        strokePaint.colorFilter = colorFilter

        style.shadows.forEach { shadow ->
            canvas.save()
            canvas.translate(dp(shadow.offsetXDp), dp(shadow.offsetYDp))
            fillPaint.shader = null
            fillPaint.color = shadow.color
            fillPaint.alpha = baseAlpha * Color.alpha(shadow.color) / 255
            canvas.drawPath(path, fillPaint)
            canvas.restore()
        }

        fillPaint.shader = shader
        fillPaint.color = baseColor()
        canvas.drawPath(path, fillPaint)
        fillPaint.shader = null
        if (pressed) {
            fillPaint.color = spec.palette.accentColor
            fillPaint.alpha = (baseAlpha * PRESSED_ACCENT_ALPHA).toInt().coerceIn(0, 255)
            canvas.drawPath(path, fillPaint)
        }
        if (!pressed && spec.motion.backgroundAnimation == KeyboardSkinBackgroundAnimation.PULSE) {
            fillPaint.color = spec.palette.accentColor
            fillPaint.alpha = (baseAlpha * (0.08f + 0.10f * kotlin.math.sin(phase * Math.PI * 2.0).toFloat())).toInt().coerceIn(0, 255)
            canvas.drawPath(path, fillPaint)
        }
        drawDecoration(canvas, baseAlpha)

        style.stroke?.let { stroke ->
            strokePaint.color = stroke.color
            strokePaint.strokeWidth = dp(stroke.widthDp)
            canvas.drawPath(path, strokePaint)
        }
    }

    override fun setPhase(value: Float) {
        phase = value.coerceIn(0f, 1f)
        shader = createShader()
        invalidateSelf()
    }

    private fun baseColor(): Int = when (val fill = style.fill) {
        is KeyboardSkinFill.Solid -> fill.color
        is KeyboardSkinFill.LinearGradient -> fill.colors[fill.colors.size / 2]
        is KeyboardSkinFill.RadialGradient -> fill.colors[fill.colors.size / 2]
    }

    private fun createShader(): Shader? {
        val left = boundsF.left
        val top = boundsF.top
        return when (val fill = style.fill) {
            is KeyboardSkinFill.Solid -> null
            is KeyboardSkinFill.LinearGradient -> {
                val angle = Math.toRadians(fill.angleDegrees.toDouble())
                val vectorX = cos(angle).toFloat()
                val vectorY = sin(angle).toFloat()
                val length = min(boundsF.width(), boundsF.height()).coerceAtLeast(1f)
                val movement = if (
                    spec.motion.backgroundAnimation == KeyboardSkinBackgroundAnimation.SWEEP ||
                    spec.motion.backgroundAnimation == KeyboardSkinBackgroundAnimation.SHIFT
                ) phase - 0.5f else 0f
                val centerX = boundsF.centerX() + movement * boundsF.width() * 0.35f
                val centerY = boundsF.centerY()
                val halfX = vectorX * length
                val halfY = vectorY * length
                LinearGradient(
                    centerX - halfX,
                    centerY - halfY,
                    centerX + halfX,
                    centerY + halfY,
                    fill.colors.toIntArray(),
                    fill.stops.toFloatArray(),
                    Shader.TileMode.CLAMP,
                )
            }
            is KeyboardSkinFill.RadialGradient -> RadialGradient(
                left + boundsF.width() * fill.centerX,
                top + boundsF.height() * fill.centerY,
                min(boundsF.width(), boundsF.height()) * fill.radius,
                fill.colors.toIntArray(),
                fill.stops.toFloatArray(),
                Shader.TileMode.CLAMP,
            ).also {
                shaderMatrix.reset()
                val movement = if (spec.motion.backgroundAnimation == KeyboardSkinBackgroundAnimation.SHIFT) phase - 0.5f else 0f
                shaderMatrix.setTranslate(movement * boundsF.width() * 0.15f, 0f)
                it.setLocalMatrix(shaderMatrix)
            }
        }
    }

    private fun drawDecoration(canvas: Canvas, baseAlpha: Int) {
        val decoration = style.decoration ?: return
        if (decoration.type == KeyboardSkinDecorationType.NONE || decoration.opacity <= 0f) return
        canvas.save()
        canvas.clipPath(path)
        decorationPaint.color = decoration.color
        decorationPaint.alpha = (baseAlpha * decoration.opacity).toInt().coerceIn(0, 255)
        decorationPaint.style = if (
            decoration.type == KeyboardSkinDecorationType.DOTS ||
            decoration.type == KeyboardSkinDecorationType.SPECKLES
        ) Paint.Style.FILL else Paint.Style.STROKE
        decorationPaint.strokeWidth = dp(decoration.sizeDp.coerceAtMost(2f))
        val step = dp(decoration.spacingDp.coerceAtLeast(1f))
        when (decoration.type) {
            KeyboardSkinDecorationType.DOTS,
            KeyboardSkinDecorationType.SPECKLES -> {
                var row = 0
                var y = boundsF.top
                while (y <= boundsF.bottom) {
                    var x = boundsF.left + if (row % 2 == 0) 0f else step / 2f
                    while (x <= boundsF.right) {
                        val radius = dp(
                            if (decoration.type == KeyboardSkinDecorationType.SPECKLES) {
                                0.45f + (stableKey % 3) * 0.2f
                            } else {
                                decoration.sizeDp / 2f
                            },
                        )
                        canvas.drawCircle(x, y, radius, decorationPaint)
                        x += step
                    }
                    y += step
                    row++
                }
            }
            KeyboardSkinDecorationType.GRID,
            KeyboardSkinDecorationType.SCANLINES,
            KeyboardSkinDecorationType.WEAVE -> {
                var y = boundsF.top
                while (y <= boundsF.bottom) {
                    canvas.drawLine(boundsF.left, y, boundsF.right, y, decorationPaint)
                    y += step
                }
                if (decoration.type != KeyboardSkinDecorationType.SCANLINES) {
                    var x = boundsF.left
                    while (x <= boundsF.right) {
                        canvas.drawLine(x, boundsF.top, x, boundsF.bottom, decorationPaint)
                        x += step
                    }
                }
            }
            KeyboardSkinDecorationType.STRIPES -> {
                val angle = Math.toRadians(decoration.angleDegrees.toDouble())
                val dx = cos(angle).toFloat() * step
                val dy = sin(angle).toFloat() * step
                var x = boundsF.left - boundsF.height()
                while (x < boundsF.right + boundsF.height()) {
                    canvas.drawLine(x, boundsF.bottom, x + boundsF.height(), boundsF.top, decorationPaint)
                    x += maxOf(abs(dx), abs(dy), dp(1f))
                }
            }
            KeyboardSkinDecorationType.NONE -> Unit
        }
        canvas.restore()
    }

    private fun rebuildPath() {
        path.reset()
        val inset = dp(style.insetDp)
        workBounds.set(boundsF)
        workBounds.inset(inset, inset)
        when (style.shape) {
            KeyboardSkinShape.ROUNDED_RECT -> path.addRoundRect(workBounds, dp(style.cornerRadiusDp), dp(style.cornerRadiusDp), Path.Direction.CW)
            KeyboardSkinShape.CAPSULE -> path.addRoundRect(workBounds, workBounds.height() / 2f, workBounds.height() / 2f, Path.Direction.CW)
            KeyboardSkinShape.CUT_CORNER -> addCutCornerPath(workBounds, dp(style.cutSizeDp.coerceAtLeast(style.cornerRadiusDp)))
            KeyboardSkinShape.HEXAGON -> addHexagonPath(workBounds)
            KeyboardSkinShape.PIXEL_NOTCHED -> addPixelNotchedPath(workBounds)
            KeyboardSkinShape.ROUGH_RECT -> addRoughRectPath(workBounds)
        }
    }

    private fun addCutCornerPath(rect: RectF, cut: Float) {
        val c = cut.coerceAtMost(min(rect.width(), rect.height()) / 2f)
        path.moveTo(rect.left + c, rect.top)
        path.lineTo(rect.right - c, rect.top)
        path.lineTo(rect.right, rect.top + c)
        path.lineTo(rect.right, rect.bottom - c)
        path.lineTo(rect.right - c, rect.bottom)
        path.lineTo(rect.left + c, rect.bottom)
        path.lineTo(rect.left, rect.bottom - c)
        path.lineTo(rect.left, rect.top + c)
        path.close()
    }

    private fun addHexagonPath(rect: RectF) {
        val inset = rect.width() * 0.18f
        path.moveTo(rect.left + inset, rect.top)
        path.lineTo(rect.right - inset, rect.top)
        path.lineTo(rect.right, rect.centerY())
        path.lineTo(rect.right - inset, rect.bottom)
        path.lineTo(rect.left + inset, rect.bottom)
        path.lineTo(rect.left, rect.centerY())
        path.close()
    }

    private fun addPixelNotchedPath(rect: RectF) {
        val notch = dp(style.cutSizeDp.coerceAtLeast(2f)).coerceAtMost(min(rect.width(), rect.height()) / 3f)
        path.moveTo(rect.left + notch, rect.top)
        path.lineTo(rect.right - notch, rect.top)
        path.lineTo(rect.right - notch, rect.top + notch)
        path.lineTo(rect.right, rect.top + notch)
        path.lineTo(rect.right, rect.bottom - notch)
        path.lineTo(rect.right - notch, rect.bottom - notch)
        path.lineTo(rect.right - notch, rect.bottom)
        path.lineTo(rect.left + notch, rect.bottom)
        path.lineTo(rect.left + notch, rect.bottom - notch)
        path.lineTo(rect.left, rect.bottom - notch)
        path.lineTo(rect.left, rect.top + notch)
        path.lineTo(rect.left + notch, rect.top + notch)
        path.close()
    }

    private fun addRoughRectPath(rect: RectF) {
        val rough = dp(style.roughnessDp)
        val n = (stableKey and 3) * rough * 0.2f
        path.moveTo(rect.left + n, rect.top + rough)
        path.lineTo(rect.right - rough, rect.top + n)
        path.lineTo(rect.right - n, rect.bottom - rough)
        path.lineTo(rect.left + rough, rect.bottom - n)
        path.close()
    }

    private fun dp(value: Float): Float = value * density

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        this.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Drawable opacity is not used by the skin renderer")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getConstantState(): ConstantState = State(context, spec, style, stableKey)

    private companion object {
        const val PRESSED_ACCENT_ALPHA = 0.24f
    }

    private class State(
        private val context: Context,
        private val spec: KeyboardSkinSpec,
        private val style: KeyboardSkinShapeStyle,
        private val stableKey: Int,
    ) : ConstantState() {
        override fun newDrawable(): Drawable = ImportedKeyboardSkinDrawable(context, spec, style, stableKey)
        override fun newDrawable(resources: Resources?): Drawable = newDrawable()
        override fun getChangingConfigurations(): Int = 0
    }
}
