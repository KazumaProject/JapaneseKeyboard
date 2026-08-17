package com.kazumaproject.core.data.keyboard

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import androidx.core.graphics.ColorUtils
import com.kazumaproject.core.R
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

interface KeyboardSkinRenderer {
    val spec: KeyboardSkinSpec

    fun createKeyDrawable(
        context: Context,
        role: KeyboardElementRole,
        stableKey: Int = 0,
    ): Drawable

    fun createSurfaceDrawable(
        context: Context,
        role: KeyboardSurfaceRole = KeyboardSurfaceRole.DECK,
    ): Drawable
}

/** One registered renderer per skin. Shared classes below are low-level drawing primitives only. */
object KeyboardSkinRendererRegistry {
    private val renderers: Map<KeyboardSkinId, KeyboardSkinRenderer> = listOf(
        DefaultSkinRenderer,
        FlatSkinRenderer,
        GlassSkinRenderer,
        NeumorphismSkinRenderer,
        MechanicalSkinRenderer,
        WashiSkinRenderer,
        NeonSkinRenderer,
        TerminalSkinRenderer,
        CupertinoSkinRenderer,
        CupertinoDarkSkinRenderer,
        SumiHanshiSkinRenderer,
        LetterpressSkinRenderer,
        PorcelainSkinRenderer,
        UrushiSkinRenderer,
        ChalkboardSkinRenderer,
        LinenSkinRenderer,
        MonochromeLcdSkinRenderer,
    ).associateBy { it.spec.id }

    fun rendererFor(id: KeyboardSkinId): KeyboardSkinRenderer =
        checkNotNull(renderers[id]) { "Missing renderer for $id" }

    private abstract class DedicatedRenderer(id: KeyboardSkinId) : KeyboardSkinRenderer {
        final override val spec: KeyboardSkinSpec = KeyboardSkinCatalog.specFor(id)

        override fun createKeyDrawable(
            context: Context,
            role: KeyboardElementRole,
            stableKey: Int,
        ): Drawable = KeyboardSkinKeyDrawable(context.applicationContext, spec, role, stableKey)

        override fun createSurfaceDrawable(
            context: Context,
            role: KeyboardSurfaceRole,
        ): Drawable = KeyboardSkinSurfaceDrawable(context.applicationContext, spec, role)
    }

    private object DefaultSkinRenderer : DedicatedRenderer(KeyboardSkinId.DEFAULT)
    private object FlatSkinRenderer : DedicatedRenderer(KeyboardSkinId.FLAT)
    private object GlassSkinRenderer : DedicatedRenderer(KeyboardSkinId.GLASS)
    private object NeumorphismSkinRenderer : DedicatedRenderer(KeyboardSkinId.NEUMORPHISM)
    private object MechanicalSkinRenderer : DedicatedRenderer(KeyboardSkinId.MECHANICAL)
    private object WashiSkinRenderer : DedicatedRenderer(KeyboardSkinId.WASHI)
    private object NeonSkinRenderer : DedicatedRenderer(KeyboardSkinId.NEON)
    private object TerminalSkinRenderer : DedicatedRenderer(KeyboardSkinId.TERMINAL)
    private object CupertinoSkinRenderer : DedicatedRenderer(KeyboardSkinId.CUPERTINO)
    private object CupertinoDarkSkinRenderer : DedicatedRenderer(KeyboardSkinId.CUPERTINO_DARK)
    private object SumiHanshiSkinRenderer : DedicatedRenderer(KeyboardSkinId.SUMI_HANSHI)
    private object LetterpressSkinRenderer : DedicatedRenderer(KeyboardSkinId.LETTERPRESS)
    private object PorcelainSkinRenderer : DedicatedRenderer(KeyboardSkinId.PORCELAIN)
    private object UrushiSkinRenderer : DedicatedRenderer(KeyboardSkinId.URUSHI)
    private object ChalkboardSkinRenderer : DedicatedRenderer(KeyboardSkinId.CHALKBOARD)
    private object LinenSkinRenderer : DedicatedRenderer(KeyboardSkinId.LINEN)
    private object MonochromeLcdSkinRenderer : DedicatedRenderer(KeyboardSkinId.MONOCHROME_LCD)
}

/**
 * Compatibility facade for existing module APIs. New code should supply a semantic role through
 * [KeyboardSkinRendererRegistry]; unlike the previous implementation, non-default colors are never
 * derived from the active theme.
 */
object KeyboardSkinDrawableFactory {
    fun keyCornerRadiusDp(skinId: KeyboardSkinId): Float =
        KeyboardSkinCatalog.specFor(skinId).geometry.cornerRadiusDp

    fun createKeyDrawable(
        context: Context,
        skinId: KeyboardSkinId,
        baseColor: Int,
        cornerRadiusDp: Float = keyCornerRadiusDp(skinId),
    ): Drawable {
        if (skinId == KeyboardSkinId.DEFAULT) {
            return createLegacyNeumorphismDrawable(context, baseColor, cornerRadiusDp)
        }
        val palette = KeyboardSkinCatalog.specFor(skinId).palette
        val role = when (baseColor) {
            palette.actionKeyColor -> KeyboardElementRole.ACTION
            palette.specialKeyColor -> KeyboardElementRole.MODIFIER
            palette.candidateSurfaceColor -> KeyboardElementRole.CANDIDATE
            else -> KeyboardElementRole.CHARACTER
        }
        return KeyboardSkinRendererRegistry.rendererFor(skinId)
            .createKeyDrawable(context, role, baseColor)
    }

    fun createSurfaceDrawable(
        context: Context,
        skinId: KeyboardSkinId,
        baseColor: Int,
    ): Drawable {
        if (skinId == KeyboardSkinId.DEFAULT) {
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(context.resources, 10f)
                setColor(baseColor)
            }
        }
        return KeyboardSkinRendererRegistry.rendererFor(skinId)
            .createSurfaceDrawable(context, KeyboardSurfaceRole.DECK)
    }

    private fun createLegacyNeumorphismDrawable(
        context: Context,
        baseColor: Int,
        cornerRadiusDp: Float,
    ): Drawable {
        val radius = dp(context.resources, cornerRadiusDp)
        val offset = dp(context.resources, 4f).toInt()
        val inset = dp(context.resources, 2f).toInt()
        val shadow = rounded(adjust(baseColor, 0.8f), radius)
        val highlight = rounded(adjust(baseColor, 1.2f), radius)
        val face = rounded(baseColor, radius)
        val idle = LayerDrawable(arrayOf(shadow, highlight, face)).apply {
            setLayerInset(0, offset, offset, 0, 0)
            setLayerInset(1, 0, 0, offset, offset)
            setLayerInset(2, inset, inset, inset, inset)
        }
        val pressed = LayerDrawable(arrayOf(rounded(adjust(baseColor, 0.95f), radius))).apply {
            setLayerInset(0, inset, inset, inset, inset)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), idle)
        }
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
    }
}

internal interface PhasedKeyboardSkinDrawable {
    fun setPhase(value: Float)
}

private class KeyboardSkinKeyDrawable(
    private val context: Context,
    private val spec: KeyboardSkinSpec,
    private val role: KeyboardElementRole,
    private val stableKey: Int,
) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val texturePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val workRect = RectF()
    private val irregularPath = Path()
    private val popupPath = Path()
    private val roundedPath = Path()
    private val pixelPath = Path()
    private val shaderMatrix = Matrix()
    private var faceGradient: Shader? = null
    private var textureShader: BitmapShader? = null
    private var pressed = false
    private var enabled = true
    private var drawableAlpha = 255
    private var drawableColorFilter: ColorFilter? = null

    init {
        textureShader = textureResourceFor(spec.id)?.let { resourceId ->
            BitmapShader(
                KeyboardSkinTextureStore.get(context, resourceId),
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT,
            )
        }
    }

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
        val inset = spec.geometry.visualInsetDp * density
        rect.set(bounds)
        rect.inset(inset, inset)
        roundedPath.reset()
        roundedPath.addRoundRect(rect, radius(), radius(), Path.Direction.CW)
        buildIrregularPath()
        buildPopupPath()
        buildPixelPath()
        faceGradient = when (spec.material) {
            KeyboardSkinMaterial.GLASS -> LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(withAlpha(spec.palette.accentColor, 175), roleColor(), withAlpha(spec.palette.secondaryAccentColor, 155)),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP,
            )

            KeyboardSkinMaterial.MECHANICAL -> LinearGradient(
                rect.left,
                rect.top,
                rect.left,
                rect.bottom,
                intArrayOf(adjust(roleColor(), 1.32f), roleColor(), adjust(roleColor(), 0.72f)),
                null,
                Shader.TileMode.CLAMP,
            )

            KeyboardSkinMaterial.PORCELAIN -> LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(
                    adjust(roleColor(), 1.08f),
                    roleColor(),
                    ColorUtils.blendARGB(roleColor(), spec.palette.secondaryAccentColor, 0.15f),
                ),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP,
            )

            KeyboardSkinMaterial.URUSHI -> LinearGradient(
                rect.left,
                rect.top,
                rect.left,
                rect.bottom,
                intArrayOf(
                    adjust(roleColor(), 1.55f),
                    roleColor(),
                    adjust(roleColor(), 0.58f),
                ),
                floatArrayOf(0f, 0.42f, 1f),
                Shader.TileMode.CLAMP,
            )

            else -> null
        }
        textureShader?.let {
            shaderMatrix.reset()
            shaderMatrix.setScale(0.55f, 0.55f)
            it.setLocalMatrix(shaderMatrix)
        }
    }

    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        fillPaint.alpha = if (enabled) drawableAlpha else (drawableAlpha * 0.48f).toInt()
        fillPaint.colorFilter = drawableColorFilter
        strokePaint.alpha = fillPaint.alpha
        strokePaint.colorFilter = drawableColorFilter
        when (spec.material) {
            KeyboardSkinMaterial.DEFAULT -> drawDefault(canvas)
            KeyboardSkinMaterial.FLAT -> drawFlat(canvas)
            KeyboardSkinMaterial.GLASS -> drawGlass(canvas)
            KeyboardSkinMaterial.SOFT_EXTRUSION -> drawNeumorphism(canvas)
            KeyboardSkinMaterial.MECHANICAL -> drawMechanical(canvas)
            KeyboardSkinMaterial.WASHI -> drawWashi(canvas)
            KeyboardSkinMaterial.NEON -> drawNeon(canvas)
            KeyboardSkinMaterial.TERMINAL -> drawTerminal(canvas)
            KeyboardSkinMaterial.CUPERTINO -> drawCupertino(canvas)
            KeyboardSkinMaterial.SUMI_HANSHI -> drawSumiHanshi(canvas)
            KeyboardSkinMaterial.LETTERPRESS -> drawLetterpress(canvas)
            KeyboardSkinMaterial.PORCELAIN -> drawPorcelain(canvas)
            KeyboardSkinMaterial.URUSHI -> drawUrushi(canvas)
            KeyboardSkinMaterial.CHALKBOARD -> drawChalkboard(canvas)
            KeyboardSkinMaterial.LINEN -> drawLinen(canvas)
            KeyboardSkinMaterial.MONOCHROME_LCD -> drawMonochromeLcd(canvas)
        }
    }

    private fun drawDefault(canvas: Canvas) {
        val radius = radius()
        workRect.set(rect)
        fillPaint.shader = null
        fillPaint.color = withAlpha(Color.BLACK, if (pressed) 52 else 30)
        workRect.offset(0f, dp(1.5f))
        canvas.drawRoundRect(workRect, radius, radius, fillPaint)
        workRect.set(rect)
        fillPaint.color = if (pressed) adjust(roleColor(), 0.92f) else roleColor()
        canvas.drawRoundRect(workRect, radius, radius, fillPaint)
    }

    private fun drawFlat(canvas: Canvas) {
        fillPaint.shader = null
        fillPaint.color = when {
            !pressed -> roleColor()
            role == KeyboardElementRole.CHARACTER || role == KeyboardElementRole.SPACE -> spec.palette.backgroundColor
            else -> adjust(roleColor(), 0.78f)
        }
        canvas.drawRoundRect(rect, radius(), radius(), fillPaint)
        if (role == KeyboardElementRole.ACTION) {
            strokePaint.strokeWidth = dp(1f)
            strokePaint.color = withAlpha(Color.WHITE, if (pressed) 120 else 70)
            canvas.drawLine(rect.left + dp(5f), rect.top + dp(5f), rect.right - dp(5f), rect.top + dp(5f), strokePaint)
        }
    }

    private fun drawGlass(canvas: Canvas) {
        val radius = radius()
        fillPaint.shader = null
        fillPaint.color = withAlpha(roleColor(), if (pressed) 205 else 145)
        canvas.drawRoundRect(rect, radius, radius, fillPaint)

        strokePaint.shader = null
        strokePaint.color = withAlpha(spec.palette.accentColor, if (pressed) 150 else 60)
        strokePaint.strokeWidth = dp(if (pressed) 4f else 3f)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
        strokePaint.shader = faceGradient
        strokePaint.strokeWidth = dp(spec.geometry.strokeWidthDp)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
        strokePaint.shader = null

        textureShader?.let { shader ->
            val save = canvas.save()
            canvas.clipPath(roundedPath)
            texturePaint.shader = shader
            texturePaint.alpha = if (pressed) 44 else 27
            canvas.drawRect(rect, texturePaint)
            texturePaint.shader = null
            canvas.restoreToCount(save)
        }
        strokePaint.color = withAlpha(Color.WHITE, if (pressed) 205 else 125)
        strokePaint.strokeWidth = dp(0.8f)
        canvas.drawLine(rect.left + radius, rect.top + dp(1.2f), rect.right - radius, rect.top + dp(1.2f), strokePaint)
    }

    private fun drawNeumorphism(canvas: Canvas) {
        val radius = radius()
        val offset = dp(if (pressed) 1.5f else 3.2f)
        fillPaint.shader = null
        if (!pressed) {
            workRect.set(rect)
            workRect.offset(offset, offset)
            fillPaint.color = adjust(roleColor(), 0.70f)
            canvas.drawRoundRect(workRect, radius, radius, fillPaint)
            workRect.set(rect)
            workRect.offset(-offset, -offset)
            fillPaint.color = adjust(roleColor(), 1.18f)
            canvas.drawRoundRect(workRect, radius, radius, fillPaint)
            workRect.set(rect)
            fillPaint.color = roleColor()
            canvas.drawRoundRect(workRect, radius, radius, fillPaint)
        } else {
            fillPaint.color = adjust(roleColor(), 0.95f)
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
            strokePaint.strokeWidth = dp(2.2f)
            strokePaint.color = withAlpha(adjust(roleColor(), 0.55f), 145)
            canvas.drawArc(rect, 195f, 150f, false, strokePaint)
            strokePaint.color = withAlpha(Color.WHITE, 165)
            canvas.drawArc(rect, 15f, 150f, false, strokePaint)
        }
    }

    private fun drawMechanical(canvas: Canvas) {
        val radius = radius()
        val depth = dp(spec.geometry.depthDp)
        fillPaint.shader = null
        workRect.set(rect)
        workRect.offset(0f, if (pressed) depth * 0.25f else depth)
        fillPaint.color = 0xFF090A0D.toInt()
        canvas.drawRoundRect(workRect, radius, radius, fillPaint)

        workRect.set(rect)
        if (pressed) workRect.offset(0f, depth * 0.72f)
        fillPaint.shader = faceGradient
        canvas.drawRoundRect(workRect, radius, radius, fillPaint)
        fillPaint.shader = null
        strokePaint.strokeWidth = dp(1f)
        strokePaint.color = withAlpha(Color.WHITE, if (pressed) 50 else 95)
        canvas.drawRoundRect(workRect, radius, radius, strokePaint)
        strokePaint.color = withAlpha(spec.palette.accentColor, if (pressed) 210 else 80)
        strokePaint.strokeWidth = dp(if (pressed) 2f else 1f)
        canvas.drawLine(workRect.left + radius, workRect.bottom - dp(1f), workRect.right - radius, workRect.bottom - dp(1f), strokePaint)
    }

    private fun drawWashi(canvas: Canvas) {
        fillPaint.shader = null
        fillPaint.color = if (pressed) ColorUtils.blendARGB(roleColor(), spec.palette.accentColor, 0.28f) else roleColor()
        canvas.drawPath(irregularPath, fillPaint)
        textureShader?.let { shader ->
            val save = canvas.save()
            canvas.clipPath(irregularPath)
            texturePaint.shader = shader
            texturePaint.alpha = if (role == KeyboardElementRole.ACTION) 34 else 105
            canvas.drawRect(rect, texturePaint)
            texturePaint.shader = null
            canvas.restoreToCount(save)
        }
        strokePaint.shader = null
        strokePaint.strokeWidth = dp(1f)
        strokePaint.color = withAlpha(spec.palette.normalKeyTextColor, 82)
        canvas.drawPath(irregularPath, strokePaint)
        if (pressed) {
            fillPaint.shader = RadialGradient(
                rect.centerX(), rect.centerY(), rect.width() * 0.48f,
                withAlpha(spec.palette.accentColor, 92), Color.TRANSPARENT, Shader.TileMode.CLAMP,
            )
            canvas.drawPath(irregularPath, fillPaint)
            fillPaint.shader = null
        }
    }

    private fun drawNeon(canvas: Canvas) {
        val radius = radius()
        val accent = if ((stableKey and 1) == 0 || role == KeyboardElementRole.CHARACTER) {
            spec.palette.accentColor
        } else {
            spec.palette.secondaryAccentColor
        }
        fillPaint.shader = null
        fillPaint.color = if (pressed) ColorUtils.blendARGB(roleColor(), accent, 0.34f) else roleColor()
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        strokePaint.shader = null
        drawNeonStroke(canvas, radius, accent, 5f, 36)
        drawNeonStroke(canvas, radius, accent, 3f, 72)
        drawNeonStroke(
            canvas,
            radius,
            accent,
            spec.geometry.strokeWidthDp,
            if (pressed) 255 else 220,
        )
        strokePaint.strokeWidth = dp(0.8f)
        strokePaint.color = withAlpha(Color.WHITE, if (pressed) 220 else 130)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
    }

    private fun drawNeonStroke(
        canvas: Canvas,
        radius: Float,
        accent: Int,
        widthDp: Float,
        alpha: Int,
    ) {
        strokePaint.strokeWidth = dp(widthDp)
        strokePaint.color = withAlpha(
            accent,
            if (pressed) (alpha * 1.25f).toInt().coerceAtMost(255) else alpha,
        )
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
    }

    private fun drawTerminal(canvas: Canvas) {
        fillPaint.shader = null
        fillPaint.color = if (pressed) spec.palette.accentColor else roleColor()
        canvas.drawRect(rect, fillPaint)
        strokePaint.shader = null
        strokePaint.strokeWidth = dp(0.75f)
        strokePaint.color = withAlpha(if (pressed) spec.palette.backgroundColor else spec.palette.accentColor, 185)
        canvas.drawRect(rect, strokePaint)
        strokePaint.color = withAlpha(if (pressed) spec.palette.backgroundColor else spec.palette.accentColor, 44)
        val step = dp(6f).coerceAtLeast(2f)
        var x = rect.left + step
        while (x < rect.right) {
            canvas.drawLine(x, rect.top, x, rect.bottom, strokePaint)
            x += step
        }
        var y = rect.top + step
        while (y < rect.bottom) {
            canvas.drawLine(rect.left, y, rect.right, y, strokePaint)
            y += step
        }
    }

    private fun drawCupertino(canvas: Canvas) {
        val radius = radius()
        fillPaint.shader = null
        if (role == KeyboardElementRole.POPUP) {
            fillPaint.color = withAlpha(Color.BLACK, if (pressed) 28 else 42)
            canvas.save()
            canvas.translate(0f, dp(1f))
            canvas.drawPath(popupPath, fillPaint)
            canvas.restore()
            fillPaint.color = roleColor()
            canvas.drawPath(popupPath, fillPaint)
            return
        }
        fillPaint.color = if (pressed) {
            ColorUtils.blendARGB(roleColor(), spec.palette.backgroundColor, 0.42f)
        } else {
            roleColor()
        }
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
    }

    private fun drawSumiHanshi(canvas: Canvas) {
        val shape = materialShape(irregular = true)
        fillPaint.shader = null
        fillPaint.color = withAlpha(Color.BLACK, if (pressed) 20 else 14)
        canvas.save()
        canvas.translate(0f, dp(if (pressed) 0.35f else 0.8f))
        canvas.drawPath(shape, fillPaint)
        canvas.restore()

        fillPaint.color = when {
            pressed && role == KeyboardElementRole.ACTION -> adjust(roleColor(), 0.78f)
            pressed -> ColorUtils.blendARGB(roleColor(), spec.palette.accentColor, 0.12f)
            else -> roleColor()
        }
        canvas.drawPath(shape, fillPaint)
        drawPaperFibers(canvas, shape, spec.palette.accentColor, 7, 14)

        strokePaint.shader = null
        strokePaint.pathEffect = DashPathEffect(
            floatArrayOf(dp(8f), dp(0.9f), dp(2.8f), dp(1.1f)),
            (stableKey and 3) * dp(0.65f),
        )
        strokePaint.strokeWidth = dp(spec.geometry.strokeWidthDp)
        strokePaint.color = withAlpha(
            if (role == KeyboardElementRole.ACTION) Color.BLACK else spec.palette.normalKeyTextColor,
            if (pressed) 108 else 70,
        )
        canvas.drawPath(shape, strokePaint)
        strokePaint.pathEffect = null

        if (pressed && role != KeyboardElementRole.ACTION) {
            fillPaint.shader = RadialGradient(
                rect.centerX(),
                rect.centerY(),
                rect.width() * 0.46f,
                withAlpha(spec.palette.accentColor, 105),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawPath(shape, fillPaint)
            fillPaint.shader = null
        }
    }

    private fun drawLetterpress(canvas: Canvas) {
        val shape = materialShape()
        fillPaint.shader = null
        if (role == KeyboardElementRole.POPUP) {
            fillPaint.color = withAlpha(Color.BLACK, 28)
            canvas.save()
            canvas.translate(0f, dp(1.1f))
            canvas.drawPath(shape, fillPaint)
            canvas.restore()
        }
        fillPaint.color = if (pressed) adjust(roleColor(), 0.94f) else roleColor()
        canvas.drawPath(shape, fillPaint)
        drawPaperFibers(canvas, shape, spec.palette.accentColor, 7, 18)

        strokePaint.shader = null
        strokePaint.pathEffect = null
        strokePaint.strokeWidth = dp(if (pressed) 2.2f else 1.25f)
        strokePaint.color = withAlpha(
            if (role == KeyboardElementRole.ACTION) Color.BLACK else spec.palette.accentColor,
            if (pressed) 165 else 112,
        )
        canvas.drawPath(shape, strokePaint)
        canvas.save()
        canvas.scale(0.94f, 0.9f, rect.centerX(), rect.centerY())
        strokePaint.strokeWidth = dp(0.75f)
        strokePaint.color = withAlpha(Color.WHITE, if (pressed) 52 else 105)
        canvas.drawPath(shape, strokePaint)
        canvas.restore()
    }

    private fun drawPorcelain(canvas: Canvas) {
        val shape = materialShape()
        fillPaint.shader = null
        fillPaint.color = withAlpha(Color.BLACK, if (pressed) 48 else 78)
        canvas.save()
        canvas.translate(0f, dp(if (pressed) 0.9f else spec.geometry.depthDp))
        canvas.drawPath(shape, fillPaint)
        canvas.restore()

        fillPaint.shader = null
        fillPaint.color = roleColor()
        canvas.drawPath(shape, fillPaint)
        fillPaint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.left,
            rect.bottom,
            withAlpha(Color.WHITE, 90),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(shape, fillPaint)
        fillPaint.shader = null
        if (pressed && role != KeyboardElementRole.ACTION) {
            fillPaint.shader = RadialGradient(
                rect.centerX(),
                rect.centerY(),
                rect.width() * 0.5f,
                withAlpha(spec.palette.secondaryAccentColor, 155),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawPath(shape, fillPaint)
            fillPaint.shader = null
        }

        val edge = if (role == KeyboardElementRole.ACTION) {
            spec.palette.actionKeyTextColor
        } else {
            spec.palette.accentColor
        }
        strokePaint.shader = null
        strokePaint.pathEffect = null
        strokePaint.strokeWidth = dp(if (pressed) 1.6f else spec.geometry.strokeWidthDp)
        strokePaint.color = withAlpha(edge, if (pressed) 245 else 215)
        canvas.drawPath(shape, strokePaint)
        canvas.save()
        canvas.scale(0.94f, 0.88f, rect.centerX(), rect.centerY())
        strokePaint.strokeWidth = dp(0.55f)
        strokePaint.color = withAlpha(edge, 105)
        canvas.drawPath(shape, strokePaint)
        canvas.restore()
        if (role != KeyboardElementRole.POPUP) drawPorcelainCornerMarks(canvas, edge)
    }

    private fun drawUrushi(canvas: Canvas) {
        val shape = materialShape()
        fillPaint.shader = null
        fillPaint.color = withAlpha(Color.BLACK, 160)
        canvas.save()
        canvas.translate(0f, dp(if (pressed) 0.8f else spec.geometry.depthDp))
        canvas.drawPath(shape, fillPaint)
        canvas.restore()

        fillPaint.shader = faceGradient
        canvas.drawPath(shape, fillPaint)
        fillPaint.shader = null
        if (pressed && role != KeyboardElementRole.ACTION) {
            fillPaint.color = withAlpha(spec.palette.secondaryAccentColor, 72)
            canvas.drawPath(shape, fillPaint)
        }
        strokePaint.shader = null
        strokePaint.pathEffect = null
        strokePaint.strokeWidth = dp(if (pressed) 1.25f else spec.geometry.strokeWidthDp)
        strokePaint.color = withAlpha(spec.palette.accentColor, if (pressed) 235 else 188)
        canvas.drawPath(shape, strokePaint)
        strokePaint.strokeWidth = dp(0.7f)
        strokePaint.color = withAlpha(Color.WHITE, if (pressed) 70 else 125)
        canvas.drawLine(
            rect.left + radius(),
            rect.top + dp(1.4f),
            rect.right - radius(),
            rect.top + dp(1.4f),
            strokePaint,
        )
    }

    private fun drawChalkboard(canvas: Canvas) {
        val shape = materialShape(irregular = true)
        fillPaint.shader = null
        fillPaint.color = if (pressed && role != KeyboardElementRole.ACTION) {
            ColorUtils.blendARGB(roleColor(), spec.palette.normalKeyTextColor, 0.08f)
        } else if (pressed) {
            adjust(roleColor(), 0.82f)
        } else {
            roleColor()
        }
        canvas.drawPath(shape, fillPaint)
        if (pressed && role != KeyboardElementRole.ACTION) {
            fillPaint.shader = RadialGradient(
                rect.centerX(),
                rect.centerY(),
                rect.width() * 0.52f,
                withAlpha(spec.palette.normalKeyTextColor, 86),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawPath(shape, fillPaint)
            fillPaint.shader = null
        }

        val chalk = when (role) {
            KeyboardElementRole.MODIFIER, KeyboardElementRole.SPACE -> spec.palette.secondaryAccentColor
            KeyboardElementRole.ACTION -> spec.palette.backgroundColor
            else -> spec.palette.accentColor
        }
        strokePaint.shader = null
        strokePaint.pathEffect = null
        strokePaint.strokeWidth = dp(if (pressed) 1.55f else spec.geometry.strokeWidthDp)
        strokePaint.color = withAlpha(chalk, if (pressed) 245 else 210)
        canvas.drawPath(shape, strokePaint)
        canvas.save()
        canvas.scale(0.94f, 0.88f, rect.centerX(), rect.centerY())
        strokePaint.strokeWidth = dp(0.45f)
        strokePaint.color = withAlpha(chalk, 78)
        canvas.drawPath(shape, strokePaint)
        canvas.restore()
        strokePaint.pathEffect = null
    }

    private fun drawLinen(canvas: Canvas) {
        val shape = materialShape()
        fillPaint.shader = null
        fillPaint.color = withAlpha(Color.BLACK, if (pressed) 32 else 50)
        canvas.save()
        canvas.translate(0f, dp(if (pressed) 0.65f else spec.geometry.depthDp))
        canvas.drawPath(shape, fillPaint)
        canvas.restore()

        fillPaint.color = if (pressed) adjust(roleColor(), 0.91f) else roleColor()
        canvas.drawPath(shape, fillPaint)
        drawLinenWeave(canvas, shape)
        if (pressed) {
            fillPaint.shader = RadialGradient(
                rect.centerX(),
                rect.centerY(),
                rect.width() * 0.5f,
                withAlpha(Color.BLACK, 48),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawPath(shape, fillPaint)
            fillPaint.shader = null
        }

        val thread = if (role == KeyboardElementRole.ACTION) {
            spec.palette.actionKeyTextColor
        } else {
            spec.palette.accentColor
        }
        strokePaint.shader = null
        strokePaint.pathEffect = DashPathEffect(floatArrayOf(dp(2.1f), dp(1.7f)), dp((stableKey and 3) * 0.4f))
        strokePaint.strokeWidth = dp(0.9f)
        strokePaint.color = withAlpha(thread, if (pressed) 235 else 190)
        canvas.save()
        canvas.scale(0.91f, 0.82f, rect.centerX(), rect.centerY())
        canvas.drawPath(shape, strokePaint)
        canvas.restore()
        strokePaint.pathEffect = null
    }

    private fun drawMonochromeLcd(canvas: Canvas) {
        val shape = if (role == KeyboardElementRole.POPUP) popupPath else pixelPath
        fillPaint.shader = null
        fillPaint.color = when {
            pressed && role == KeyboardElementRole.ACTION -> adjust(roleColor(), 0.72f)
            pressed -> spec.palette.normalKeyTextColor
            else -> roleColor()
        }
        canvas.drawPath(shape, fillPaint)
        val lineColor = if (pressed && role != KeyboardElementRole.ACTION) {
            spec.palette.normalKeyColor
        } else {
            spec.palette.accentColor
        }
        strokePaint.shader = null
        strokePaint.pathEffect = null
        strokePaint.strokeWidth = dp(if (pressed) 1.5f else spec.geometry.strokeWidthDp)
        strokePaint.color = withAlpha(lineColor, 245)
        canvas.drawPath(shape, strokePaint)
        canvas.save()
        canvas.scale(0.94f, 0.84f, rect.centerX(), rect.centerY())
        strokePaint.strokeWidth = dp(0.55f)
        strokePaint.color = withAlpha(lineColor, 92)
        canvas.drawPath(shape, strokePaint)
        canvas.restore()
    }

    private fun materialShape(irregular: Boolean = false): Path = when {
        role == KeyboardElementRole.POPUP -> popupPath
        irregular -> irregularPath
        else -> roundedPath
    }

    private fun drawPaperFibers(
        canvas: Canvas,
        clip: Path,
        color: Int,
        count: Int,
        alpha: Int,
    ) {
        val save = canvas.save()
        canvas.clipPath(clip)
        strokePaint.shader = null
        strokePaint.pathEffect = null
        strokePaint.strokeWidth = dp(0.45f)
        strokePaint.color = withAlpha(color, alpha)
        repeat(count) { index ->
            val fraction = (index + 1f) / (count + 1f)
            val y = rect.top + rect.height() * fraction + jitterValue(100 + index, dp(0.8f))
            val startFraction = ((index * 37 + stableKey * 11) and 0x7F) / 160f
            val start = rect.left + rect.width() * startFraction.coerceIn(0.04f, 0.78f)
            val length = rect.width() * (0.08f + (index % 4) * 0.045f)
            val end = (start + length).coerceAtMost(rect.right - dp(2f))
            canvas.drawLine(start, y, end, y + jitterValue(160 + index, dp(0.7f)), strokePaint)
        }
        canvas.restoreToCount(save)
    }

    private fun drawPorcelainCornerMarks(canvas: Canvas, color: Int) {
        val inset = dp(5f)
        val length = dp(3.5f)
        strokePaint.pathEffect = null
        strokePaint.strokeWidth = dp(0.8f)
        strokePaint.color = withAlpha(color, 145)
        canvas.drawLine(rect.left + inset, rect.top + inset, rect.left + inset + length, rect.top + inset, strokePaint)
        canvas.drawLine(rect.left + inset, rect.top + inset, rect.left + inset, rect.top + inset + length, strokePaint)
        canvas.drawLine(rect.right - inset, rect.bottom - inset, rect.right - inset - length, rect.bottom - inset, strokePaint)
        canvas.drawLine(rect.right - inset, rect.bottom - inset, rect.right - inset, rect.bottom - inset - length, strokePaint)
    }

    private fun drawLinenWeave(canvas: Canvas, clip: Path) {
        val save = canvas.save()
        canvas.clipPath(clip)
        strokePaint.shader = null
        strokePaint.pathEffect = null
        strokePaint.strokeWidth = dp(0.45f)
        val step = dp(3.6f).coerceAtLeast(2f)
        strokePaint.color = withAlpha(spec.palette.accentColor, 23)
        var x = rect.left
        while (x <= rect.right) {
            canvas.drawLine(x, rect.top, x, rect.bottom, strokePaint)
            x += step
        }
        strokePaint.color = withAlpha(Color.WHITE, 30)
        var y = rect.top
        while (y <= rect.bottom) {
            canvas.drawLine(rect.left, y, rect.right, y, strokePaint)
            y += step
        }
        canvas.restoreToCount(save)
    }

    private fun buildIrregularPath() {
        irregularPath.reset()
        if (rect.isEmpty) return
        val jitter = spec.geometry.irregularityDp * density
        val steps = 8
        irregularPath.moveTo(rect.left + jitterValue(0, jitter), rect.top + jitterValue(1, jitter))
        for (i in 1..steps) {
            val x = rect.left + rect.width() * i / steps
            irregularPath.lineTo(x + jitterValue(i * 2, jitter), rect.top + jitterValue(i * 2 + 1, jitter))
        }
        for (i in 1..steps) {
            val y = rect.top + rect.height() * i / steps
            irregularPath.lineTo(rect.right + jitterValue(20 + i * 2, jitter), y + jitterValue(21 + i * 2, jitter))
        }
        for (i in 1..steps) {
            val x = rect.right - rect.width() * i / steps
            irregularPath.lineTo(x + jitterValue(40 + i * 2, jitter), rect.bottom + jitterValue(41 + i * 2, jitter))
        }
        for (i in 1..steps) {
            val y = rect.bottom - rect.height() * i / steps
            irregularPath.lineTo(rect.left + jitterValue(60 + i * 2, jitter), y + jitterValue(61 + i * 2, jitter))
        }
        irregularPath.close()
    }

    private fun buildPopupPath() {
        popupPath.reset()
        if (rect.isEmpty) return
        val stem = dp(5f)
        val radius = radius()
        popupPath.addRoundRect(rect.left, rect.top, rect.right, rect.bottom - stem, radius, radius, Path.Direction.CW)
        popupPath.moveTo(rect.centerX() - stem, rect.bottom - stem)
        popupPath.lineTo(rect.centerX(), rect.bottom)
        popupPath.lineTo(rect.centerX() + stem, rect.bottom - stem)
        popupPath.close()
    }

    private fun buildPixelPath() {
        pixelPath.reset()
        if (rect.isEmpty) return
        val notch = dp(2.5f).coerceAtMost(minOf(rect.width(), rect.height()) * 0.18f)
        pixelPath.moveTo(rect.left + notch, rect.top)
        pixelPath.lineTo(rect.right - notch, rect.top)
        pixelPath.lineTo(rect.right - notch, rect.top + notch)
        pixelPath.lineTo(rect.right, rect.top + notch)
        pixelPath.lineTo(rect.right, rect.bottom - notch)
        pixelPath.lineTo(rect.right - notch, rect.bottom - notch)
        pixelPath.lineTo(rect.right - notch, rect.bottom)
        pixelPath.lineTo(rect.left + notch, rect.bottom)
        pixelPath.lineTo(rect.left + notch, rect.bottom - notch)
        pixelPath.lineTo(rect.left, rect.bottom - notch)
        pixelPath.lineTo(rect.left, rect.top + notch)
        pixelPath.lineTo(rect.left + notch, rect.top + notch)
        pixelPath.close()
    }

    private fun jitterValue(index: Int, amplitude: Float): Float {
        if (amplitude == 0f) return 0f
        var value = stableKey * 1103515245 + index * 12345 + 0x6D2B79F5
        value = value xor (value ushr 16)
        return (((value and 0xFFFF) / 65535f) * 2f - 1f) * amplitude
    }

    private fun roleColor(): Int = spec.palette.keyColor(role)
    private fun radius(): Float = dp(spec.geometry.cornerRadiusDp)
    private fun dp(value: Float): Float = value * density

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        drawableColorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Drawable opacity is not used by the skin renderer")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getConstantState(): ConstantState = KeyConstantState(context, spec, role, stableKey)

    private class KeyConstantState(
        private val context: Context,
        private val spec: KeyboardSkinSpec,
        private val role: KeyboardElementRole,
        private val stableKey: Int,
    ) : ConstantState() {
        override fun newDrawable(): Drawable = KeyboardSkinKeyDrawable(context, spec, role, stableKey)
        override fun newDrawable(res: Resources?): Drawable = newDrawable()
        override fun getChangingConfigurations(): Int = 0
    }
}

internal class KeyboardSkinSurfaceDrawable(
    private val context: Context,
    private val spec: KeyboardSkinSpec,
    private val role: KeyboardSurfaceRole,
) : Drawable(), PhasedKeyboardSkinDrawable {
    private val density = context.resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val texturePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val wavePath = Path()
    private val shaderMatrix = Matrix()
    private val fastenerPoints = FloatArray(8)
    private val fastenerShader by lazy {
        RadialGradient(
            0f,
            0f,
            dp(3f),
            Color.WHITE,
            0xFF33363D.toInt(),
            Shader.TileMode.CLAMP,
        )
    }
    private var baseShader: Shader? = null
    private var textureShader: BitmapShader? = null
    private var phase = 0f
    private var drawableAlpha = 255
    private var drawableColorFilter: ColorFilter? = null

    init {
        textureShader = textureResourceFor(spec.id)?.let { resourceId ->
            BitmapShader(
                KeyboardSkinTextureStore.get(context, resourceId),
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT,
            )
        }
    }

    override fun setPhase(value: Float) {
        val normalized = value - value.toInt()
        if (normalized == phase) return
        phase = normalized
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds)
        baseShader = when (spec.material) {
            KeyboardSkinMaterial.GLASS -> SweepGradient(
                rect.centerX(), rect.centerY(),
                intArrayOf(
                    spec.palette.backgroundColor,
                    withAlpha(spec.palette.accentColor, 190),
                    0xFF7C3AED.toInt(),
                    withAlpha(spec.palette.secondaryAccentColor, 210),
                    spec.palette.backgroundColor,
                ),
                floatArrayOf(0f, 0.24f, 0.5f, 0.76f, 1f),
            )

            KeyboardSkinMaterial.MECHANICAL -> LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(0xFF0E0F12.toInt(), spec.palette.backgroundColor, 0xFF262930.toInt()),
                null, Shader.TileMode.CLAMP,
            )

            KeyboardSkinMaterial.NEON -> LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(0xFF03020A.toInt(), spec.palette.backgroundColor, 0xFF15032A.toInt()),
                null, Shader.TileMode.CLAMP,
            )

            KeyboardSkinMaterial.PORCELAIN -> LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(
                    adjust(spec.palette.backgroundColor, 0.78f),
                    spec.palette.backgroundColor,
                    adjust(spec.palette.backgroundColor, 1.18f),
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )

            KeyboardSkinMaterial.URUSHI -> LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(
                    0xFF050505.toInt(),
                    spec.palette.backgroundColor,
                    0xFF261815.toInt(),
                    0xFF080706.toInt(),
                ),
                floatArrayOf(0f, 0.38f, 0.7f, 1f),
                Shader.TileMode.CLAMP,
            )

            else -> null
        }
        textureShader?.let {
            shaderMatrix.reset()
            shaderMatrix.setScale(if (spec.id == KeyboardSkinId.GLASS) 0.5f else 0.72f, if (spec.id == KeyboardSkinId.GLASS) 0.5f else 0.72f)
            it.setLocalMatrix(shaderMatrix)
        }
    }

    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        fillPaint.alpha = drawableAlpha
        fillPaint.colorFilter = drawableColorFilter
        strokePaint.alpha = drawableAlpha
        strokePaint.colorFilter = drawableColorFilter
        when (spec.material) {
            KeyboardSkinMaterial.DEFAULT -> drawSimple(canvas, surfaceColor())
            KeyboardSkinMaterial.FLAT -> drawFlat(canvas)
            KeyboardSkinMaterial.GLASS -> drawGlass(canvas)
            KeyboardSkinMaterial.SOFT_EXTRUSION -> drawNeumorphism(canvas)
            KeyboardSkinMaterial.MECHANICAL -> drawMechanical(canvas)
            KeyboardSkinMaterial.WASHI -> drawWashi(canvas)
            KeyboardSkinMaterial.NEON -> drawNeon(canvas)
            KeyboardSkinMaterial.TERMINAL -> drawTerminal(canvas)
            KeyboardSkinMaterial.CUPERTINO -> drawCupertino(canvas)
            KeyboardSkinMaterial.SUMI_HANSHI -> drawSumiHanshi(canvas)
            KeyboardSkinMaterial.LETTERPRESS -> drawLetterpress(canvas)
            KeyboardSkinMaterial.PORCELAIN -> drawPorcelain(canvas)
            KeyboardSkinMaterial.URUSHI -> drawUrushi(canvas)
            KeyboardSkinMaterial.CHALKBOARD -> drawChalkboard(canvas)
            KeyboardSkinMaterial.LINEN -> drawLinen(canvas)
            KeyboardSkinMaterial.MONOCHROME_LCD -> drawMonochromeLcd(canvas)
        }
    }

    private fun drawSimple(canvas: Canvas, color: Int) {
        fillPaint.shader = null
        fillPaint.color = color
        canvas.drawRect(rect, fillPaint)
    }

    private fun drawFlat(canvas: Canvas) {
        drawSimple(canvas, surfaceColor())
        if (role == KeyboardSurfaceRole.DECK) {
            fillPaint.color = withAlpha(spec.palette.secondaryAccentColor, 52)
            wavePath.reset()
            wavePath.moveTo(rect.left, rect.bottom)
            wavePath.lineTo(rect.right * 0.36f, rect.top)
            wavePath.lineTo(rect.right * 0.58f, rect.top)
            wavePath.lineTo(rect.left + rect.width() * 0.25f, rect.bottom)
            wavePath.close()
            canvas.drawPath(wavePath, fillPaint)
        }
    }

    private fun drawGlass(canvas: Canvas) {
        drawSimple(canvas, spec.palette.backgroundColor)
        val shader = baseShader
        if (shader != null) {
            shaderMatrix.reset()
            shaderMatrix.setRotate(phase * 360f, rect.centerX(), rect.centerY())
            shader.setLocalMatrix(shaderMatrix)
            fillPaint.shader = shader
            fillPaint.alpha = (drawableAlpha * 0.82f).toInt()
            canvas.drawRect(rect, fillPaint)
            fillPaint.shader = null
            fillPaint.alpha = drawableAlpha
        }
        textureShader?.let {
            texturePaint.shader = it
            texturePaint.alpha = 25
            canvas.drawRect(rect, texturePaint)
            texturePaint.shader = null
        }
        strokePaint.strokeWidth = dp(1f)
        strokePaint.color = withAlpha(Color.WHITE, 65)
        canvas.drawRect(rect, strokePaint)
    }

    private fun drawNeumorphism(canvas: Canvas) {
        drawSimple(canvas, surfaceColor())
        strokePaint.strokeWidth = dp(2f)
        strokePaint.color = withAlpha(Color.WHITE, 135)
        canvas.drawLine(rect.left, rect.top + dp(1f), rect.right, rect.top + dp(1f), strokePaint)
        strokePaint.color = withAlpha(Color.BLACK, 42)
        canvas.drawLine(rect.left, rect.bottom - dp(1f), rect.right, rect.bottom - dp(1f), strokePaint)
    }

    private fun drawMechanical(canvas: Canvas) {
        fillPaint.shader = baseShader
        canvas.drawRect(rect, fillPaint)
        fillPaint.shader = null
        textureShader?.let { shader ->
            shaderMatrix.reset()
            shaderMatrix.setScale(0.72f, 0.72f)
            shader.setLocalMatrix(shaderMatrix)
            texturePaint.shader = shader
            texturePaint.alpha = 150
            canvas.drawRect(rect, texturePaint)
            texturePaint.shader = null
        }
        val pulse = (0.5f + 0.5f * sin(phase * 2f * PI).toFloat())
        strokePaint.strokeWidth = dp(2f)
        strokePaint.color = withAlpha(spec.palette.accentColor, (45 + pulse * 110).toInt())
        canvas.drawLine(rect.left, rect.bottom - dp(2f), rect.centerX(), rect.bottom - dp(2f), strokePaint)
        strokePaint.color = withAlpha(spec.palette.secondaryAccentColor, (45 + (1f - pulse) * 110).toInt())
        canvas.drawLine(rect.centerX(), rect.bottom - dp(2f), rect.right, rect.bottom - dp(2f), strokePaint)
        drawCornerFasteners(canvas)
    }

    private fun drawWashi(canvas: Canvas) {
        drawSimple(canvas, surfaceColor())
        textureShader?.let { shader ->
            shaderMatrix.reset()
            shaderMatrix.setScale(0.72f, 0.72f)
            shaderMatrix.postTranslate(phase * dp(12f), phase * dp(5f))
            shader.setLocalMatrix(shaderMatrix)
            texturePaint.shader = shader
            texturePaint.alpha = if (role == KeyboardSurfaceRole.DECK) 52 else 95
            texturePaint.colorFilter = if (role == KeyboardSurfaceRole.DECK) {
                PorterDuffColorFilter(0xFF28466A.toInt(), PorterDuff.Mode.MULTIPLY)
            } else {
                null
            }
            canvas.drawRect(rect, texturePaint)
            texturePaint.shader = null
            texturePaint.colorFilter = null
        }
        if (role == KeyboardSurfaceRole.DECK) drawSeigaiha(canvas)
    }

    private fun drawNeon(canvas: Canvas) {
        fillPaint.shader = baseShader
        canvas.drawRect(rect, fillPaint)
        fillPaint.shader = null
        val yBase = rect.top + rect.height() * (0.45f + 0.08f * sin(phase * 2f * PI).toFloat())
        repeat(2) { wave ->
            wavePath.reset()
            wavePath.moveTo(rect.left, yBase + wave * dp(10f))
            var x = rect.left
            val step = dp(8f)
            while (x <= rect.right) {
                val normalized = (x - rect.left) / rect.width().coerceAtLeast(1f)
                val y = yBase + wave * dp(10f) + sin((normalized * 4f + phase) * 2f * PI).toFloat() * dp(5f)
                wavePath.lineTo(x, y)
                x += step
            }
            strokePaint.strokeWidth = dp(if (wave == 0) 1.6f else 1.2f)
            strokePaint.color = withAlpha(if (wave == 0) spec.palette.accentColor else spec.palette.secondaryAccentColor, 105)
            canvas.drawPath(wavePath, strokePaint)
        }
        strokePaint.strokeWidth = dp(2f)
        strokePaint.color = withAlpha(spec.palette.accentColor, 125)
        canvas.drawRect(rect, strokePaint)
    }

    private fun drawTerminal(canvas: Canvas) {
        drawSimple(canvas, surfaceColor())
        strokePaint.strokeWidth = dp(0.6f)
        strokePaint.color = withAlpha(spec.palette.accentColor, 42)
        val step = dp(12f).coerceAtLeast(4f)
        var x = rect.left
        while (x < rect.right) {
            canvas.drawLine(x, rect.top, x, rect.bottom, strokePaint)
            x += step
        }
        var y = rect.top
        while (y < rect.bottom) {
            canvas.drawLine(rect.left, y, rect.right, y, strokePaint)
            y += step
        }
        strokePaint.color = withAlpha(spec.palette.accentColor, 35)
        strokePaint.strokeWidth = dp(1f)
        y = rect.top + dp(3f)
        while (y < rect.bottom) {
            canvas.drawLine(rect.left, y, rect.right, y, strokePaint)
            y += dp(4f)
        }
        fillPaint.color = withAlpha(spec.palette.accentColor, 52)
        fillPaint.shader = null
        val scanY = rect.top + rect.height() * phase
        canvas.drawRect(rect.left, scanY - dp(6f), rect.right, scanY + dp(6f), fillPaint)
        strokePaint.color = withAlpha(spec.palette.accentColor, 150)
        strokePaint.strokeWidth = dp(1f)
        canvas.drawRect(rect, strokePaint)
    }

    private fun drawCupertino(canvas: Canvas) {
        fillPaint.shader = null
        fillPaint.color = surfaceColor()
        if (role == KeyboardSurfaceRole.DECK) {
            val radius = dp(24f)
            wavePath.reset()
            wavePath.moveTo(rect.left, rect.bottom)
            wavePath.lineTo(rect.left, rect.top + radius)
            wavePath.quadTo(rect.left, rect.top, rect.left + radius, rect.top)
            wavePath.lineTo(rect.right - radius, rect.top)
            wavePath.quadTo(rect.right, rect.top, rect.right, rect.top + radius)
            wavePath.lineTo(rect.right, rect.bottom)
            wavePath.close()
            canvas.drawPath(wavePath, fillPaint)
        } else {
            canvas.drawRect(rect, fillPaint)
        }
        fillPaint.shader = null
    }

    private fun drawSumiHanshi(canvas: Canvas) {
        drawSimple(canvas, surfaceColor())
        drawPaperSurfaceFibers(canvas, spec.palette.accentColor, lineAlpha = 12, fiberCount = 24)
        if (role == KeyboardSurfaceRole.CANDIDATE_STRIP) {
            strokePaint.pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(3f)), 0f)
            strokePaint.strokeWidth = dp(0.7f)
            strokePaint.color = withAlpha(spec.palette.accentColor, 62)
            canvas.drawLine(rect.left + dp(8f), rect.bottom - dp(2f), rect.right - dp(8f), rect.bottom - dp(2f), strokePaint)
            strokePaint.pathEffect = null
        }
    }

    private fun drawLetterpress(canvas: Canvas) {
        drawSimple(canvas, surfaceColor())
        drawPaperSurfaceFibers(canvas, spec.palette.accentColor, lineAlpha = 18, fiberCount = 25)
        if (role != KeyboardSurfaceRole.DECK) {
            strokePaint.pathEffect = null
            strokePaint.strokeWidth = dp(0.8f)
            strokePaint.color = withAlpha(spec.palette.accentColor, 86)
            canvas.drawRect(rect, strokePaint)
            strokePaint.color = withAlpha(Color.WHITE, 80)
            canvas.drawLine(rect.left, rect.bottom - dp(1f), rect.right, rect.bottom - dp(1f), strokePaint)
        }
    }

    private fun drawPorcelain(canvas: Canvas) {
        if (role == KeyboardSurfaceRole.DECK) {
            fillPaint.shader = baseShader
            canvas.drawRect(rect, fillPaint)
            fillPaint.shader = null
            drawCeramicSpeckles(canvas, withAlpha(Color.WHITE, 18))
        } else {
            drawSimple(canvas, surfaceColor())
            strokePaint.pathEffect = null
            strokePaint.strokeWidth = dp(1f)
            strokePaint.color = withAlpha(spec.palette.accentColor, 185)
            canvas.drawRect(rect, strokePaint)
        }
    }

    private fun drawUrushi(canvas: Canvas) {
        fillPaint.shader = baseShader
        if (baseShader == null) fillPaint.color = surfaceColor()
        canvas.drawRect(rect, fillPaint)
        fillPaint.shader = null
        val sheen = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            intArrayOf(Color.TRANSPARENT, withAlpha(Color.WHITE, 22), Color.TRANSPARENT),
            floatArrayOf(0.25f, 0.52f, 0.76f),
            Shader.TileMode.CLAMP,
        )
        fillPaint.shader = sheen
        canvas.drawRect(rect, fillPaint)
        fillPaint.shader = null
        if (role != KeyboardSurfaceRole.DECK) {
            strokePaint.pathEffect = null
            strokePaint.strokeWidth = dp(0.8f)
            strokePaint.color = withAlpha(spec.palette.accentColor, 155)
            canvas.drawRect(rect, strokePaint)
        }
    }

    private fun drawChalkboard(canvas: Canvas) {
        drawSimple(canvas, surfaceColor())
        strokePaint.shader = null
        strokePaint.strokeWidth = dp(0.8f)
        strokePaint.pathEffect = DashPathEffect(floatArrayOf(dp(1.5f), dp(4.5f)), dp(1f))
        repeat(14) { index ->
            val y = rect.top + rect.height() * ((index * 37 % 101) / 101f)
            val start = rect.left + rect.width() * ((index * 19 % 83) / 100f)
            val length = rect.width() * (0.08f + (index % 5) * 0.025f)
            strokePaint.color = withAlpha(spec.palette.normalKeyTextColor, 13 + index % 3 * 5)
            canvas.drawLine(start, y, (start + length).coerceAtMost(rect.right), y + dp((index % 3 - 1) * 0.4f), strokePaint)
        }
        strokePaint.pathEffect = null
        if (role == KeyboardSurfaceRole.CANDIDATE_STRIP) {
            strokePaint.strokeWidth = dp(0.8f)
            strokePaint.color = withAlpha(spec.palette.normalKeyTextColor, 52)
            canvas.drawLine(rect.left + dp(7f), rect.bottom - dp(2f), rect.right - dp(7f), rect.bottom - dp(2f), strokePaint)
        }
    }

    private fun drawLinen(canvas: Canvas) {
        drawSimple(canvas, surfaceColor())
        strokePaint.shader = null
        strokePaint.pathEffect = null
        strokePaint.strokeWidth = dp(0.5f)
        val step = dp(5.2f).coerceAtLeast(3f)
        strokePaint.color = withAlpha(spec.palette.accentColor, 25)
        var x = rect.left
        while (x <= rect.right) {
            canvas.drawLine(x, rect.top, x, rect.bottom, strokePaint)
            x += step
        }
        strokePaint.color = withAlpha(Color.WHITE, 35)
        var y = rect.top
        while (y <= rect.bottom) {
            canvas.drawLine(rect.left, y, rect.right, y, strokePaint)
            y += step
        }
    }

    private fun drawMonochromeLcd(canvas: Canvas) {
        drawSimple(canvas, surfaceColor())
        strokePaint.shader = null
        strokePaint.pathEffect = null
        strokePaint.strokeWidth = dp(0.45f)
        strokePaint.color = withAlpha(spec.palette.accentColor, 22)
        val step = dp(5f).coerceAtLeast(3f)
        var x = rect.left
        while (x <= rect.right) {
            canvas.drawLine(x, rect.top, x, rect.bottom, strokePaint)
            x += step
        }
        var y = rect.top
        while (y <= rect.bottom) {
            canvas.drawLine(rect.left, y, rect.right, y, strokePaint)
            y += step
        }
        strokePaint.strokeWidth = dp(1f)
        strokePaint.color = withAlpha(spec.palette.accentColor, 205)
        canvas.drawRect(rect, strokePaint)
    }

    private fun drawPaperSurfaceFibers(
        canvas: Canvas,
        color: Int,
        lineAlpha: Int,
        fiberCount: Int,
    ) {
        strokePaint.shader = null
        strokePaint.pathEffect = null
        strokePaint.strokeWidth = dp(0.45f)
        strokePaint.color = withAlpha(color, lineAlpha)
        repeat(fiberCount) { index ->
            val y = rect.top + rect.height() * ((index * 43 % 101) / 101f)
            val start = rect.left + rect.width() * ((index * 17 % 71) / 100f)
            val length = rect.width() * (0.12f + (index % 7) * 0.025f)
            canvas.drawLine(
                start,
                y,
                (start + length).coerceAtMost(rect.right),
                y + dp((index % 5 - 2) * 0.18f),
                strokePaint,
            )
        }
    }

    private fun drawCeramicSpeckles(canvas: Canvas, color: Int) {
        fillPaint.shader = null
        fillPaint.color = color
        repeat(30) { index ->
            val x = rect.left + rect.width() * ((index * 29 % 97) / 97f)
            val y = rect.top + rect.height() * ((index * 47 % 103) / 103f)
            canvas.drawCircle(x, y, dp(if ((index and 1) == 0) 0.35f else 0.55f), fillPaint)
        }
    }

    private fun drawSeigaiha(canvas: Canvas) {
        strokePaint.strokeWidth = dp(0.8f)
        strokePaint.color = withAlpha(0xFFF2E3C1.toInt(), 38)
        val radius = dp(14f)
        var row = 0
        var cy = rect.top + radius
        while (cy < rect.bottom + radius) {
            var cx = rect.left + if ((row and 1) == 0) 0f else radius
            while (cx < rect.right + radius * 2) {
                canvas.drawArc(cx - radius, cy - radius, cx + radius, cy + radius, 180f, 180f, false, strokePaint)
                canvas.drawArc(cx - radius * 0.66f, cy - radius * 0.66f, cx + radius * 0.66f, cy + radius * 0.66f, 180f, 180f, false, strokePaint)
                cx += radius * 2f
            }
            cy += radius
            row += 1
        }
    }

    private fun drawCornerFasteners(canvas: Canvas) {
        fillPaint.shader = fastenerShader
        val inset = dp(6f)
        val radius = dp(2.5f)
        fastenerPoints[0] = rect.left + inset
        fastenerPoints[1] = rect.top + inset
        fastenerPoints[2] = rect.right - inset
        fastenerPoints[3] = rect.top + inset
        fastenerPoints[4] = rect.left + inset
        fastenerPoints[5] = rect.bottom - inset
        fastenerPoints[6] = rect.right - inset
        fastenerPoints[7] = rect.bottom - inset
        var i = 0
        while (i < fastenerPoints.size) {
            shaderMatrix.reset()
            shaderMatrix.setTranslate(fastenerPoints[i], fastenerPoints[i + 1])
            fillPaint.shader?.setLocalMatrix(shaderMatrix)
            canvas.drawCircle(fastenerPoints[i], fastenerPoints[i + 1], radius, fillPaint)
            i += 2
        }
        fillPaint.shader = null
    }

    private fun surfaceColor(): Int = when (role) {
        KeyboardSurfaceRole.DECK -> spec.palette.backgroundColor
        KeyboardSurfaceRole.CANDIDATE_STRIP,
        KeyboardSurfaceRole.CANDIDATE_PANEL,
        KeyboardSurfaceRole.TOOLBAR -> spec.palette.candidateSurfaceColor
        KeyboardSurfaceRole.POPUP -> spec.palette.specialKeyColor
    }

    private fun dp(value: Float): Float = value * density

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        drawableColorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Drawable opacity is not used by the skin renderer")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getConstantState(): ConstantState = SurfaceConstantState(context, spec, role)

    private class SurfaceConstantState(
        private val context: Context,
        private val spec: KeyboardSkinSpec,
        private val role: KeyboardSurfaceRole,
    ) : ConstantState() {
        override fun newDrawable(): Drawable = KeyboardSkinSurfaceDrawable(context, spec, role)
        override fun newDrawable(res: Resources?): Drawable = newDrawable()
        override fun getChangingConfigurations(): Int = 0
    }
}

private object KeyboardSkinTextureStore {
    private val cache = ConcurrentHashMap<Int, Bitmap>()

    fun get(context: Context, resourceId: Int): Bitmap = cache.getOrPut(resourceId) {
        BitmapFactory.decodeResource(
            context.resources,
            resourceId,
            BitmapFactory.Options().apply { inScaled = false },
        )
    }
}

private fun textureResourceFor(id: KeyboardSkinId): Int? = when (id) {
    KeyboardSkinId.GLASS -> R.drawable.keyboard_skin_glass_frost

    KeyboardSkinId.MECHANICAL -> R.drawable.keyboard_skin_mechanical_metal
    KeyboardSkinId.WASHI -> R.drawable.keyboard_skin_washi_fiber
    else -> null
}

private fun dp(resources: Resources, value: Float): Float = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    value,
    resources.displayMetrics,
)

private fun adjust(color: Int, factor: Float): Int = Color.argb(
    Color.alpha(color),
    (Color.red(color) * factor).toInt().coerceIn(0, 255),
    (Color.green(color) * factor).toInt().coerceIn(0, 255),
    (Color.blue(color) * factor).toInt().coerceIn(0, 255),
)

private fun withAlpha(color: Int, alpha: Int): Int =
    ColorUtils.setAlphaComponent(color, alpha.coerceIn(0, 255))
