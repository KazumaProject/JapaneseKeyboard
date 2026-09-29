package com.kazumaproject.core.ui.font

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.PathParser
import com.kazumaproject.core.R

/**
 * Draws text-backed keyboard icons with the currently selected keyboard typeface while leaving
 * their resource dimensions and their standard-font vector artwork intact.
 */
class KeyboardFontGlyphDrawable private constructor(
    private val context: Context,
    @DrawableRes val resourceId: Int,
    private val delegate: Drawable,
    private val spec: IconSpec,
    initialSnapshot: KeyboardFontSnapshot,
) : Drawable(), KeyboardFontAware, Drawable.Callback {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textAlign = Paint.Align.LEFT
        style = Paint.Style.FILL
    }
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var snapshot = initialSnapshot
    private var alphaValue = 255
    private var explicitColorFilter: ColorFilter? = null
    private var tintList: ColorStateList? = null
    private var tintMode: PorterDuff.Mode = PorterDuff.Mode.SRC_IN
    private var resolvedTintColor: Int? = null

    /** True while this icon is rendered with the selected local keyboard typeface. */
    val usesCustomFont: Boolean
        get() = snapshot.typeface != null

    init {
        delegate.callback = this
        delegate.bounds = bounds
        delegate.state = state
        delegate.level = level
        KeyboardFontApplicator.track(this)
    }

    override fun draw(canvas: Canvas) {
        val typeface = snapshot.typeface
        if (typeface == null) {
            drawDelegate(canvas)
            return
        }

        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / spec.viewportWidth, bounds.height() / spec.viewportHeight)
        canvas.clipRect(0f, 0f, spec.viewportWidth, spec.viewportHeight)

        val baseColor = resolvedTintColor ?: spec.defaultColor(context)
        val color = Color.argb(
            Color.alpha(baseColor) * alphaValue / 255,
            Color.red(baseColor),
            Color.green(baseColor),
            Color.blue(baseColor),
        )
        spec.glyphs.forEach { glyph ->
            val viewportBounds = RectF(glyph.bounds).apply {
                left *= spec.viewportWidth
                right *= spec.viewportWidth
                top *= spec.viewportHeight
                bottom *= spec.viewportHeight
            }
            drawGlyph(
                canvas, textPaint, glyph.text, viewportBounds, color, typeface, glyph.style,
                explicitColorFilter,
            )
        }
        spec.decorations.forEach { decoration ->
            drawDecoration(canvas, decoration, color)
        }
        canvas.restoreToCount(save)
    }

    private fun drawDelegate(canvas: Canvas) {
        delegate.bounds = bounds
        delegate.state = state
        delegate.level = level
        delegate.alpha = alphaValue
        delegate.colorFilter = explicitColorFilter
        tintList?.let(delegate::setTintList)
        delegate.setTintMode(tintMode)
        delegate.draw(canvas)
    }

    private fun drawDecoration(canvas: Canvas, decoration: Decoration, color: Int) {
        val path = PathParser.createPathFromPathData(decoration.pathData) ?: return
        if (decoration.scaleX != 1f || decoration.scaleY != 1f ||
            decoration.translateX != 0f || decoration.translateY != 0f ||
            decoration.pivotX != 0f || decoration.pivotY != 0f
        ) {
            val tx = decoration.translateX + decoration.pivotX - decoration.scaleX * decoration.pivotX
            val ty = decoration.translateY + decoration.pivotY - decoration.scaleY * decoration.pivotY
            val matrix = Matrix().apply {
                setValues(
                    floatArrayOf(
                        decoration.scaleX, 0f, tx,
                        0f, decoration.scaleY, ty,
                        0f, 0f, 1f,
                    )
                )
            }
            path.transform(matrix)
        }
        pathPaint.color = color
        pathPaint.alpha = Color.alpha(color)
        pathPaint.colorFilter = explicitColorFilter
        path.fillType = decoration.fillType
        canvas.drawPath(path, pathPaint)
    }

    override fun setKeyboardFont(snapshot: KeyboardFontSnapshot) {
        this.snapshot = snapshot
        invalidateSelf()
    }

    /** Returns an independently stateful instance for a second ImageView. */
    fun newInstance(snapshot: KeyboardFontSnapshot = KeyboardFontApplicator.processSnapshot): KeyboardFontGlyphDrawable =
        create(context, resourceId, snapshot) as KeyboardFontGlyphDrawable

    override fun getIntrinsicWidth(): Int = delegate.intrinsicWidth

    override fun getIntrinsicHeight(): Int = delegate.intrinsicHeight

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setAlpha(alpha: Int) {
        alphaValue = alpha.coerceIn(0, 255)
        delegate.alpha = alphaValue
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        explicitColorFilter = colorFilter
        delegate.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun setTint(tintColor: Int) {
        setTintList(ColorStateList.valueOf(tintColor))
    }

    override fun setTintList(tint: ColorStateList?) {
        tintList = tint
        delegate.setTintList(tint)
        updateTintForState()
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        this.tintMode = tintMode ?: PorterDuff.Mode.SRC_IN
        delegate.setTintMode(this.tintMode)
        invalidateSelf()
    }

    override fun isStateful(): Boolean = delegate.isStateful || tintList?.isStateful == true

    override fun onStateChange(state: IntArray): Boolean {
        val delegateChanged = delegate.setState(state)
        val tintChanged = updateTintForState()
        return delegateChanged || tintChanged
    }

    private fun updateTintForState(): Boolean {
        val next = tintList?.getColorForState(state, tintList?.defaultColor ?: Color.TRANSPARENT)
        if (next == resolvedTintColor) {
            invalidateSelf()
            return false
        }
        resolvedTintColor = next
        invalidateSelf()
        return true
    }

    override fun onLevelChange(level: Int): Boolean = delegate.setLevel(level)

    override fun onBoundsChange(bounds: Rect) {
        delegate.bounds = bounds
    }

    override fun onLayoutDirectionChanged(layoutDirection: Int): Boolean =
        delegate.setLayoutDirection(layoutDirection)

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean =
        super.setVisible(visible, restart) or delegate.setVisible(visible, restart)

    override fun getPadding(padding: Rect): Boolean = delegate.getPadding(padding)

    override fun getCurrent(): Drawable = delegate.current

    override fun jumpToCurrentState() = delegate.jumpToCurrentState()

    override fun invalidateDrawable(who: Drawable) = invalidateSelf()

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) =
        scheduleSelf(what, `when`)

    override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)

    companion object {
        internal data class Glyph(
            val text: String,
            val bounds: RectF,
            val style: Int = Typeface.NORMAL,
        )

        internal data class Decoration(
            val pathData: String,
            val scaleX: Float = 1f,
            val scaleY: Float = 1f,
            val translateX: Float = 0f,
            val translateY: Float = 0f,
            val pivotX: Float = 0f,
            val pivotY: Float = 0f,
            val fillType: Path.FillType = Path.FillType.WINDING,
        )

        internal data class IconSpec(
            val viewportWidth: Float,
            val viewportHeight: Float,
            val glyphs: List<Glyph>,
            val decorations: List<Decoration> = emptyList(),
            val ink: Ink = Ink.KEYBOARD,
        ) {
            fun defaultColor(context: Context): Int = when (ink) {
                Ink.KEYBOARD -> androidx.core.content.ContextCompat.getColor(
                    context,
                    R.color.keyboard_icon_color,
                )
                Ink.BLACK -> Color.BLACK
                Ink.LIGHT -> Color.rgb(0xe3, 0xe3, 0xe3)
            }
        }

        internal enum class Ink { KEYBOARD, BLACK, LIGHT }

        private fun box(left: Float, top: Float, right: Float, bottom: Float) =
            RectF(left, top, right, bottom)

        private fun glyph(text: String, left: Float, top: Float, right: Float, bottom: Float,
                          style: Int = Typeface.NORMAL) = Glyph(
            text,
            box(left, top, right, bottom),
            style,
        )

        private val kanaArrow = Decoration(
            "M54.75,59.33l-2.45,2.38l0.01,-1.25l-4.54,0.01l0.01,1.28l-2.53,-2.36l2.47,-2.39l-0.02,1.23l4.53,-0.01l0.02,-1.25l2.52,2.35ZM54.44,59.36l-1.91,-1.82l-0.01,0.93l-5.1,0.01l0,-0.88l-1.82,1.8l1.87,1.77l-0,-0.93l5.11,-0.01l-0.02,0.96l1.87,-1.84Z",
            fillType = Path.FillType.EVEN_ODD,
        )

        internal val specs: Map<Int, IconSpec> = buildMap {
            put(R.drawable.henkan, IconSpec(96f, 46f, listOf(glyph("変換", 0.01f, 0.02f, 0.99f, 0.96f))))
            put(R.drawable.katakana, IconSpec(100f, 100f, listOf(glyph("カタ", 0.16f, 0.28f, 0.84f, 0.72f))))

            val modeBox = box(0.14f, 0.27f, 0.86f, 0.73f)
            put(R.drawable.input_mode_english_custom, IconSpec(100f, 100f, listOf(Glyph("ABC", modeBox))))
            put(R.drawable.input_mode_japanese_select_custom, IconSpec(100f, 100f, listOf(Glyph("あいう", modeBox))))
            put(R.drawable.input_mode_number_select_custom, IconSpec(100f, 100f, listOf(Glyph("123", modeBox))))

            val leftLanguageBox = box(0.18f, 0.20f, 0.55f, 0.80f)
            val rightLanguageBox = box(0.48f, 0.20f, 0.84f, 0.80f)
            put(R.drawable.language_japanese_kana_24px, IconSpec(960f, 960f, listOf(
                Glyph("あ", leftLanguageBox), Glyph("a", rightLanguageBox),
            )))
            put(R.drawable.language_japanese_kana_left_24px, IconSpec(960f, 960f, listOf(
                glyph("あ", 0.31f, 0.19f, 0.69f, 0.81f),
            )))
            put(R.drawable.language_japanese_kana_right_24px, IconSpec(960f, 960f, listOf(
                glyph("a", 0.31f, 0.19f, 0.69f, 0.81f),
            )))
            put(R.drawable.language_japanese_kana_left_bold_24px, IconSpec(960f, 960f, listOf(
                Glyph("あ", leftLanguageBox, Typeface.BOLD), Glyph("a", rightLanguageBox),
            )))
            put(R.drawable.language_japanese_kana_right_bold_24px, IconSpec(960f, 960f, listOf(
                Glyph("あ", leftLanguageBox), Glyph("a", rightLanguageBox, Typeface.BOLD),
            )))

            put(R.drawable.english_small, IconSpec(100f, 100f, listOf(glyph("a/A", 0.36f, 0.37f, 0.67f, 0.64f))))
            val kanaGlyphs = listOf(
                glyph("゛", 0.36f, 0.32f, 0.49f, 0.47f),
                glyph("゜", 0.51f, 0.32f, 0.65f, 0.47f),
                glyph("小", 0.34f, 0.51f, 0.47f, 0.68f),
                glyph("大", 0.56f, 0.51f, 0.68f, 0.68f),
            )
            put(R.drawable.kana_small, IconSpec(
                100f, 100f, kanaGlyphs, decorations = listOf(kanaArrow), ink = Ink.BLACK
            ))
            put(R.drawable.kana_small_custom, IconSpec(100f, 100f, kanaGlyphs, decorations = listOf(kanaArrow)))

            put(R.drawable.number_small, IconSpec(100f, 100f, listOf(
                glyph("()", 0.36f, 0.38f, 0.52f, 0.62f),
                glyph("[]", 0.51f, 0.38f, 0.65f, 0.62f),
            ), ink = Ink.BLACK))
            put(R.drawable.number_small_flick_guide, IconSpec(100f, 100f, listOf(
                glyph("(", 0.44f, 0.39f, 0.56f, 0.61f),
                glyph(")", 0.20f, 0.40f, 0.31f, 0.60f),
                glyph("[", 0.47f, 0.19f, 0.57f, 0.32f),
                glyph("]", 0.58f, 0.39f, 0.68f, 0.61f),
            ), ink = Ink.BLACK))

            put(R.drawable.custom_key_english_case_24, IconSpec(24f, 24f, listOf(
                glyph("a/A", 0.03f, 0.08f, 0.97f, 0.80f),
            )))
            put(R.drawable.custom_key_kana_case_24, IconSpec(24f, 24f, listOf(
                glyph("゛", 0.08f, 0.04f, 0.40f, 0.36f),
                glyph("゜", 0.58f, 0.04f, 0.92f, 0.36f),
                glyph("小", 0.07f, 0.70f, 0.32f, 0.93f),
                glyph("大", 0.64f, 0.70f, 0.92f, 0.93f),
            ), decorations = listOf(kanaArrow.copy(
                scaleX = 0.75f,
                scaleY = 0.75f,
                translateX = -25.8f,
                translateY = -24.5f,
            ))))

            put(R.drawable.symbol, IconSpec(100f, 100f, listOf(glyph("§", 0.29f, 0.12f, 0.71f, 0.88f)), ink = Ink.BLACK))
            put(R.drawable.open_bracket, IconSpec(100f, 100f, listOf(glyph("(", 0.41f, 0.37f, 0.59f, 0.63f)), ink = Ink.BLACK))
            put(R.drawable.qwerty_number, IconSpec(1281f, 609f, listOf(glyph("123", 0.01f, 0.01f, 0.99f, 0.99f)), ink = Ink.BLACK))
            put(R.drawable.qwerty_symbol, IconSpec(29f, 12f, listOf(glyph("#+=", 0.01f, 0.01f, 0.99f, 0.99f)), ink = Ink.BLACK))

            val lightning = Decoration("M18.2,2.7L15.4,8.6H18.3L16.4,14L21.9,6.7H18.9L20.4,2.7Z")
            put(R.drawable.live_conversion_24px, IconSpec(24f, 24f, listOf(
                glyph("あ", 0.17f, 0.14f, 0.66f, 0.84f),
            )))
            put(R.drawable.live_conversion_on_24px, IconSpec(24f, 24f, listOf(
                glyph("あ", 0.13f, 0.14f, 0.68f, 0.84f),
            ), decorations = listOf(lightning)))

            put(R.drawable.emoji_symbols, IconSpec(960f, 960f, listOf(
                glyph("〒", 0.06f, 0.05f, 0.46f, 0.33f),
                glyph("♪", 0.54f, 0.04f, 0.96f, 0.48f),
                glyph("&", 0.06f, 0.50f, 0.45f, 0.96f),
                glyph("%", 0.56f, 0.57f, 0.96f, 0.96f),
            ), ink = Ink.LIGHT))
            put(R.drawable.question_mark_24dp, IconSpec(960f, 960f, listOf(
                glyph("?", 0.28f, 0.16f, 0.72f, 0.88f),
            ), ink = Ink.LIGHT))
        }

        /** Returns the original resource for non-glyph icons and a font-aware wrapper otherwise. */
        fun create(
            context: Context,
            @DrawableRes resourceId: Int,
            snapshot: KeyboardFontSnapshot = KeyboardFontApplicator.processSnapshot,
        ): Drawable? {
            val original = AppCompatResources.getDrawable(context, resourceId) ?: return null
            val spec = specs[resourceId] ?: return original
            return KeyboardFontGlyphDrawable(
                context,
                resourceId,
                original,
                spec,
                snapshot,
            )
        }

        fun isSupported(@DrawableRes resourceId: Int): Boolean = resourceId in specs

        /** Assigns a fresh drawable so ImageViews never share mutable drawable bounds or callbacks. */
        fun setImageResource(
            imageView: ImageView,
            @DrawableRes resourceId: Int,
            snapshot: KeyboardFontSnapshot = KeyboardFontApplicator.processSnapshot,
        ) {
            imageView.setImageDrawable(create(imageView.context, resourceId, snapshot))
        }

        /** Assigns an independent copy when the supplied drawable is one of our cached prototypes. */
        fun setImageDrawable(
            imageView: ImageView,
            drawable: Drawable?,
            snapshot: KeyboardFontSnapshot = KeyboardFontApplicator.processSnapshot,
        ) {
            imageView.setImageDrawable(
                if (drawable is KeyboardFontGlyphDrawable) drawable.newInstance(snapshot) else drawable
            )
        }

        /** Draws one fitted label in the supplied viewport rectangle. */
        fun drawGlyph(
            canvas: Canvas,
            paint: Paint,
            text: String,
            region: RectF,
            color: Int,
            typeface: Typeface,
            style: Int = Typeface.NORMAL,
            colorFilter: ColorFilter? = null,
        ) {
            if (text.isEmpty() || region.width() <= 0f || region.height() <= 0f) return
            paint.color = color
            paint.colorFilter = colorFilter
            paint.typeface = Typeface.create(typeface, style)
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 100f
            val textBounds = Rect()
            paint.getTextBounds(text, 0, text.length, textBounds)
            val textWidth = textBounds.width().coerceAtLeast(1).toFloat()
            val textHeight = textBounds.height().coerceAtLeast(1).toFloat()
            val scale = minOf(region.width() / textWidth, region.height() / textHeight)
            paint.textSize = (100f * scale).coerceAtLeast(1f)
            paint.getTextBounds(text, 0, text.length, textBounds)
            val x = region.centerX() - (textBounds.left + textBounds.right) / 2f
            val y = region.centerY() - (textBounds.top + textBounds.bottom) / 2f
            canvas.drawText(text, x, y, paint)
        }
    }
}
