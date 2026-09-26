package com.kazumaproject.core.ui.skin

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import kotlin.math.min
import kotlin.math.roundToInt

/** The light, bevelled keyboard shown in Apple's iOS 6 iPhone User Guide, page 22. */
internal class CupertinoClassicSkin : KeyboardSkin {
    override val id = KeyboardSkinId.CUPERTINO_CLASSIC
    override val palette = SkinPalette(
        background = 0xff89929f.toInt(),
        key = 0xffe9ebee.toInt(),
        text = 0xff17191d.toInt(),
        pressed = 0xffa8c4e9.toInt(),
        selection = 0xff2878cf.toInt(),
        selectionText = 0xffffffff.toInt(),
        specialKey = 0xff808b9a.toInt(),
        specialText = 0xfff8fbff.toInt(),
        spaceKey = 0xffc8cdd3.toInt(),
        spaceText = 0xff3e4550.toInt(),
    )

    override fun keyboardDrawable(resources: Resources, floating: Boolean): Drawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xffa9b0bb.toInt(), 0xff707a88.toInt(), 0xff555f6d.toInt())).apply {
            val r = if (floating) 12f * resources.displayMetrics.density else 0f
            cornerRadius = r
        }

    override fun keyDrawable(resources: Resources, qwerty: Boolean, role: SkinKeyRole): Drawable {
        val density = resources.displayMetrics.density
        val radius = (if (qwerty) 4f else 6f) * density
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), ClassicKeyDrawable(role, radius, density, pressed = true))
            addState(intArrayOf(android.R.attr.state_selected), ClassicKeyDrawable(role, radius, density, selected = true))
            addState(intArrayOf(), ClassicKeyDrawable(role, radius, density))
        }
    }

    override fun popupDrawable(resources: Resources, direction: PopupDirection, selected: Boolean): Drawable =
        ClassicPopupDrawable(direction, resources.displayMetrics.density, selected)

    override fun guideDrawable(resources: Resources, direction: PopupDirection, selected: Boolean): Drawable =
        popupDrawable(resources, direction, selected)

    override fun variationDrawable(resources: Resources): Drawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xffd4d7dc.toInt(), 0xff929ba8.toInt())).apply {
            cornerRadius = 7f * resources.displayMetrics.density
            setStroke((resources.displayMetrics.density).roundToInt().coerceAtLeast(1), 0xff4c5664.toInt())
        }

    override fun configurePopupText(view: TextView, flick: Boolean) {
        view.textSize = if (flick) 29f else 24f
        view.includeFontPadding = false
        view.gravity = Gravity.CENTER
        view.translationY = 0f
        view.paint.isFakeBoldText = false
    }

    override fun configurePreviewText(view: TextView) {
        view.textSize = 34f
        view.includeFontPadding = false
        view.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        view.paint.isFakeBoldText = true
        val baseline = 40f * view.resources.displayMetrics.density
        view.setPadding(0, (baseline + view.paint.fontMetricsInt.ascent).roundToInt().coerceAtLeast(0), 0, 0)
    }

    override fun keyPreview(resources: Resources, keyWidth: Int, keyHeight: Int, keyLeft: Int,
                            screenWidth: Int, lowerRow: Boolean): SkinKeyPreview {
        val width = (keyWidth * 1.8f).roundToInt().coerceAtLeast(keyWidth)
        val height = (keyHeight * 2.25f).roundToInt().coerceAtLeast(keyHeight)
        val xOffset = (-(width - keyWidth) / 2).coerceAtLeast(-keyLeft)
            .coerceAtMost(screenWidth - keyLeft - width)
        return SkinKeyPreview(width, height, xOffset, -height,
            ClassicKeyPreviewDrawable(resources.displayMetrics.density, keyWidth.toFloat(), -xOffset.toFloat()))
    }

    override fun showPopup(view: View) {
        view.animate().cancel()
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
    }

    override fun clearPopup(view: View) = showPopup(view)
}

/** A drawn bevel scales cleanly for QWERTY, kana and the user's resized keyboard. */
internal class ClassicKeyDrawable(
    private val role: SkinKeyRole,
    private val radius: Float,
    private val density: Float,
    private val pressed: Boolean = false,
    private val selected: Boolean = false,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var opacity = 255

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val edge = min(density.coerceAtLeast(1f), min(w, h) / 12f)
        val shadow = min(2f * density, h / 8f)
        val r = min(radius, min(w, h) / 4f)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = 0xff3e4856.toInt()
        paint.alpha = opacity
        canvas.drawRoundRect(RectF(0f, shadow, w, h), r, r, paint)

        val face = RectF(edge, edge, w - edge, h - shadow)
        val colors = when {
            selected -> intArrayOf(0xff70ade9.toInt(), 0xff2878cf.toInt(), 0xff17599e.toInt())
            pressed -> intArrayOf(0xffe4efff.toInt(), 0xffa8c4e9.toInt(), 0xff7897c3.toInt())
            role == SkinKeyRole.MODIFIER -> intArrayOf(0xffa1aab6.toInt(), 0xff858f9e.toInt(), 0xff697583.toInt())
            role == SkinKeyRole.SPACE -> intArrayOf(0xffd7dbe0.toInt(), 0xffc5cad0.toInt(), 0xffb1b8c0.toInt())
            else -> intArrayOf(0xfffcfcfd.toInt(), 0xffeceef0.toInt(), 0xffd2d7dc.toInt())
        }
        paint.shader = LinearGradient(0f, face.top, 0f, face.bottom, colors, null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(face, r, r, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = edge
        paint.color = if (selected) 0xff164c8c.toInt() else 0xff536071.toInt()
        paint.alpha = opacity
        canvas.drawRoundRect(face, r, r, paint)
        paint.color = if (role == SkinKeyRole.CHARACTER && !selected && !pressed)
            0xffffffff.toInt() else 0xffcbd6e2.toInt()
        paint.alpha = opacity
        paint.strokeWidth = min(edge, h / 24f)
        canvas.drawLine(face.left + r, face.top + edge, face.right - r, face.top + edge, paint)
        paint.style = Paint.Style.FILL
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) { opacity = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getConstantState(): ConstantState = object : ConstantState() {
        override fun newDrawable(): Drawable = ClassicKeyDrawable(role, radius, density, pressed, selected)
        override fun getChangingConfigurations(): Int = 0
    }
}

internal class ClassicPopupDrawable(
    private val direction: PopupDirection,
    private val density: Float,
    private val selected: Boolean,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var opacity = 255
    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val path = if (direction == PopupDirection.CENTER || direction == PopupDirection.PREVIEW) {
            Path().apply { addRoundRect(RectF(1f, 1f, w - 1f, h - 1f), 5f * density, 5f * density, Path.Direction.CW) }
        } else CupertinoPopupContours.path(w, h, direction)
        paint.style = Paint.Style.FILL
        paint.color = 0xffffffff.toInt()
        paint.alpha = opacity
        paint.shader = LinearGradient(0f, 0f, 0f, h,
            if (selected) 0xff69a9ea.toInt() else 0xfffdfdfe.toInt(),
            if (selected) 0xff2165b0.toInt() else 0xffb8c0ca.toInt(), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density.coerceAtLeast(1f)
        paint.color = if (selected) 0xff164b85.toInt() else 0xff4b5664.toInt()
        paint.alpha = opacity
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { opacity = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getConstantState(): ConstantState = object : ConstantState() {
        override fun newDrawable(): Drawable = ClassicPopupDrawable(direction, density, selected)
        override fun getChangingConfigurations(): Int = 0
    }
}

/** The 2012 enlarged key has a rounded cap and a narrow stem reaching the pressed key. */
internal class ClassicKeyPreviewDrawable(
    private val density: Float,
    private val stemWidth: Float,
    private val stemLeft: Float,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var opacity = 255
    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val r = 6f * density
        val edge = density.coerceAtLeast(1f)
        val capBottom = h * 0.68f
        val left = stemLeft.coerceIn(0f, w - stemWidth)
        val right = (left + stemWidth).coerceAtMost(w)
        val path = Path().apply {
            moveTo(r, edge)
            lineTo(w - r, edge)
            quadTo(w - edge, edge, w - edge, r)
            lineTo(w - edge, capBottom - r)
            quadTo(w - edge, capBottom, w - r, capBottom)
            lineTo(right + r / 2f, capBottom)
            quadTo(right, capBottom, right, capBottom + r / 2f)
            lineTo(right, h - r)
            quadTo(right, h - edge, right - r, h - edge)
            lineTo(left + r, h - edge)
            quadTo(left, h - edge, left, h - r)
            lineTo(left, capBottom + r / 2f)
            quadTo(left, capBottom, left - r / 2f, capBottom)
            lineTo(r, capBottom)
            quadTo(edge, capBottom, edge, capBottom - r)
            lineTo(edge, r)
            quadTo(edge, edge, r, edge)
            close()
        }
        paint.style = Paint.Style.FILL
        paint.color = 0xff3d4654.toInt()
        paint.alpha = opacity
        canvas.save()
        canvas.translate(0f, 2f * density)
        canvas.drawPath(path, paint)
        canvas.restore()
        paint.shader = LinearGradient(0f, 0f, 0f, h,
            intArrayOf(0xfffdfefe.toInt(), 0xffe3e7ec.toInt(), 0xffaeb7c2.toInt()),
            null, Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = edge
        paint.color = 0xff515b69.toInt()
        paint.alpha = opacity
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { opacity = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
