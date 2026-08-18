package com.kazumaproject.core.data.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

/** Shared popup factory used by all built-in keyboard implementations. */
object KeyboardSkinPopupRenderer {

    fun specFor(skinId: KeyboardSkinId): KeyboardSkinPopupSpec? =
        KeyboardSkinCatalog.specFor(skinId).popup

    fun specFor(skinRef: KeyboardSkinRef): KeyboardSkinPopupSpec? =
        KeyboardSkinCatalog.specFor(skinRef).popup

    fun isFixedCupertino(skinId: KeyboardSkinId): Boolean =
        specFor(skinId) != null

    fun isFixedCupertino(skinRef: KeyboardSkinRef): Boolean =
        specFor(skinRef) != null

    fun createDrawable(
        context: Context,
        skinId: KeyboardSkinId,
        kind: KeyboardSkinPopupKind,
        direction: KeyboardSkinPopupDirection = KeyboardSkinPopupDirection.CENTER,
        selected: Boolean = false,
    ): Drawable? {
        val popup = specFor(skinId) ?: return null
        return KeyboardSkinPopupDrawable(context, popup, kind, direction, selected)
    }

    fun createDrawable(
        context: Context,
        skinId: KeyboardSkinRef,
        kind: KeyboardSkinPopupKind,
        direction: KeyboardSkinPopupDirection = KeyboardSkinPopupDirection.CENTER,
        selected: Boolean = false,
    ): Drawable? {
        val popup = specFor(skinId) ?: return null
        val importedSurface = if (skinId is KeyboardSkinRef.Imported) {
            KeyboardSkinRendererRegistry.rendererFor(skinId)
                .createSurfaceDrawable(context, KeyboardSurfaceRole.POPUP)
        } else {
            null
        }
        return KeyboardSkinPopupDrawable(
            context = context,
            spec = popup,
            kind = kind,
            direction = direction,
            selected = selected,
            surfaceDrawable = importedSurface,
        )
    }

    /** Applies the fixed iOS typography. Returns false when the skin has no popup spec. */
    fun applyTextStyle(
        view: TextView,
        skinId: KeyboardSkinId,
        kind: KeyboardSkinPopupKind,
        selected: Boolean = false,
    ): Boolean {
        val popup = specFor(skinId) ?: return false
        val skin = KeyboardSkinCatalog.specFor(skinId)
        view.setTextColor(if (selected) popup.selectedTextColor else popup.textColor)
        view.typeface = Typeface.create(
            skin.typography.familyName,
            if (skin.typography.bold) Typeface.BOLD else Typeface.NORMAL,
        )
        view.includeFontPadding = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.letterSpacing = skin.typography.letterSpacing
        }
        view.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            when (kind) {
                KeyboardSkinPopupKind.KEY_PREVIEW -> popup.keyPreviewTextSizeSp
                KeyboardSkinPopupKind.VARIATION -> popup.variationTextSizeSp
                else -> popup.flickTextSizeSp
            },
        )
        val horizontal = dp(view.context, popup.contentPaddingHorizontalDp).toInt()
        val vertical = dp(view.context, popup.contentPaddingVerticalDp).toInt()
        view.setPadding(horizontal, vertical, horizontal, vertical)
        return true
    }

    fun applyTextStyle(
        view: TextView,
        skinId: KeyboardSkinRef,
        kind: KeyboardSkinPopupKind,
        selected: Boolean = false,
    ): Boolean {
        val popup = specFor(skinId) ?: return false
        val skin = KeyboardSkinCatalog.specFor(skinId)
        view.setTextColor(if (selected) popup.selectedTextColor else popup.textColor)
        view.typeface = Typeface.create(
            skin.typography.familyName,
            if (skin.typography.bold) Typeface.BOLD else Typeface.NORMAL,
        )
        view.includeFontPadding = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.letterSpacing = skin.typography.letterSpacing
        }
        view.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            when (kind) {
                KeyboardSkinPopupKind.KEY_PREVIEW -> popup.keyPreviewTextSizeSp
                KeyboardSkinPopupKind.VARIATION -> popup.variationTextSizeSp
                else -> popup.flickTextSizeSp
            },
        )
        val horizontal = dp(view.context, popup.contentPaddingHorizontalDp).toInt()
        val vertical = dp(view.context, popup.contentPaddingVerticalDp).toInt()
        view.setPadding(horizontal, vertical, horizontal, vertical)
        return true
    }

    fun popupTextSizeSp(skinId: KeyboardSkinId, kind: KeyboardSkinPopupKind): Float? {
        val popup = specFor(skinId) ?: return null
        return when (kind) {
            KeyboardSkinPopupKind.KEY_PREVIEW -> popup.keyPreviewTextSizeSp
            KeyboardSkinPopupKind.VARIATION -> popup.variationTextSizeSp
            else -> popup.flickTextSizeSp
        }
    }

    fun popupTextSizeSp(skinId: KeyboardSkinRef, kind: KeyboardSkinPopupKind): Float? {
        val popup = specFor(skinId) ?: return null
        return when (kind) {
            KeyboardSkinPopupKind.KEY_PREVIEW -> popup.keyPreviewTextSizeSp
            KeyboardSkinPopupKind.VARIATION -> popup.variationTextSizeSp
            else -> popup.flickTextSizeSp
        }
    }

    /** Draws a shared popup surface while preserving a caller-owned operation path. */
    @Suppress("UNUSED_PARAMETER")
    fun drawPath(
        canvas: Canvas,
        context: Context,
        skinId: KeyboardSkinId,
        _kind: KeyboardSkinPopupKind,
        path: Path,
        selected: Boolean = false,
    ): Boolean {
        val popup = specFor(skinId) ?: return false
        val density = context.resources.displayMetrics.density
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = popup.shadowColor
            alpha = (if (selected) popup.selectedShadowAlpha else popup.shadowAlpha)
            style = Paint.Style.FILL
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (selected) popup.selectedSurfaceColor else popup.surfaceColor
            style = Paint.Style.FILL
        }
        canvas.save()
        canvas.translate(0f, density)
        canvas.drawPath(path, shadowPaint)
        canvas.restore()
        canvas.drawPath(path, fillPaint)
        if (popup.strokeWidthDp > 0f) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (selected) popup.selectedSurfaceColor else popup.surfaceColor
                style = Paint.Style.STROKE
                strokeWidth = popup.strokeWidthDp * density
            }.also { canvas.drawPath(path, it) }
        }
        return true
    }

    fun drawPath(
        canvas: Canvas,
        context: Context,
        skinId: KeyboardSkinRef,
        _kind: KeyboardSkinPopupKind,
        path: Path,
        selected: Boolean = false,
    ): Boolean {
        val popup = specFor(skinId) ?: return false
        val density = context.resources.displayMetrics.density
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = popup.shadowColor
            alpha = if (selected) popup.selectedShadowAlpha else popup.shadowAlpha
            style = Paint.Style.FILL
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (selected) popup.selectedSurfaceColor else popup.surfaceColor
            style = Paint.Style.FILL
        }
        canvas.save()
        canvas.translate(0f, density)
        canvas.drawPath(path, shadowPaint)
        canvas.restore()
        canvas.drawPath(path, fillPaint)
        if (popup.strokeWidthDp > 0f) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (selected) popup.selectedSurfaceColor else popup.surfaceColor
                style = Paint.Style.STROKE
                strokeWidth = popup.strokeWidthDp * density
            }.also { canvas.drawPath(path, it) }
        }
        return true
    }

    /** Draws a shared rounded surface for Canvas-based custom popup variants. */
    fun drawRoundRect(
        canvas: Canvas,
        context: Context,
        skinId: KeyboardSkinId,
        kind: KeyboardSkinPopupKind,
        bounds: RectF,
        selected: Boolean = false,
        direction: KeyboardSkinPopupDirection = KeyboardSkinPopupDirection.CENTER,
    ): Boolean {
        val drawable = createDrawable(context, skinId, kind, direction, selected) ?: return false
        drawable.setBounds(
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.right.toInt(),
            bounds.bottom.toInt(),
        )
        drawable.draw(canvas)
        return true
    }

    fun drawRoundRect(
        canvas: Canvas,
        context: Context,
        skinId: KeyboardSkinRef,
        kind: KeyboardSkinPopupKind,
        bounds: RectF,
        selected: Boolean = false,
        direction: KeyboardSkinPopupDirection = KeyboardSkinPopupDirection.CENTER,
    ): Boolean {
        val drawable = createDrawable(context, skinId, kind, direction, selected) ?: return false
        drawable.setBounds(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
        drawable.draw(canvas)
        return true
    }

    /** Applies the shared popup typography to a Canvas paint. */
    fun applyPaintStyle(
        paint: Paint,
        context: Context,
        skinId: KeyboardSkinId,
        kind: KeyboardSkinPopupKind,
        selected: Boolean = false,
    ): Boolean {
        val popup = specFor(skinId) ?: return false
        val skin = KeyboardSkinCatalog.specFor(skinId)
        paint.color = if (selected) popup.selectedTextColor else popup.textColor
        paint.typeface = Typeface.create(
            skin.typography.familyName,
            if (skin.typography.bold) Typeface.BOLD else Typeface.NORMAL,
        )
        paint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            popupTextSizeSp(skinId, kind) ?: return false,
            context.resources.displayMetrics,
        )
        return true
    }

    fun applyPaintStyle(
        paint: Paint,
        context: Context,
        skinId: KeyboardSkinRef,
        kind: KeyboardSkinPopupKind,
        selected: Boolean = false,
    ): Boolean {
        val popup = specFor(skinId) ?: return false
        val skin = KeyboardSkinCatalog.specFor(skinId)
        paint.color = if (selected) popup.selectedTextColor else popup.textColor
        paint.typeface = Typeface.create(
            skin.typography.familyName,
            if (skin.typography.bold) Typeface.BOLD else Typeface.NORMAL,
        )
        paint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            popupTextSizeSp(skinId, kind) ?: return false,
            context.resources.displayMetrics,
        )
        return true
    }

    fun dp(context: Context, value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        context.resources.displayMetrics,
    )

    fun centeredXOffset(anchorWidth: Int, popupWidth: Int): Int =
        (anchorWidth - popupWidth) / 2

    fun aboveYOffset(popupHeight: Int, gapDp: Float, context: Context): Int =
        -popupHeight - dp(context, gapDp).toInt()

    fun clampXOffset(
        anchorLeft: Int,
        popupWidth: Int,
        viewportWidth: Int,
        marginDp: Float,
        context: Context,
    ): Int {
        val margin = dp(context, marginDp).toInt()
        return anchorLeft.coerceIn(margin, max(margin, viewportWidth - popupWidth - margin))
    }
}

/**
 * Canvas implementation of the Cupertino popup surfaces. The drawable deliberately does not use
 * Material gradients or the legacy KeyWindowLayout arrow renderer.
 */
class KeyboardSkinPopupDrawable(
    context: Context,
    private val spec: KeyboardSkinPopupSpec,
    private val kind: KeyboardSkinPopupKind,
    direction: KeyboardSkinPopupDirection,
    selected: Boolean,
    private val surfaceDrawable: Drawable? = null,
) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private val path = Path()
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val boundsF = RectF()
    private var direction = direction
    private var selected = selected
    private var drawableAlpha = 255
    private var drawableColorFilter: ColorFilter? = null

    override fun draw(canvas: Canvas) {
        if (boundsF.isEmpty) return
        surfaceDrawable?.let { importedSurface ->
            importedSurface.setBounds(bounds)
            importedSurface.alpha = drawableAlpha
            importedSurface.colorFilter = drawableColorFilter
            importedSurface.state = if (selected) {
                intArrayOf(android.R.attr.state_pressed, android.R.attr.state_enabled)
            } else {
                intArrayOf()
            }
            importedSurface.draw(canvas)
            return
        }
        val shadowAlpha = if (selected) spec.selectedShadowAlpha else spec.shadowAlpha
        shadowPaint.color = spec.shadowColor
        shadowPaint.alpha = shadowAlpha * drawableAlpha / 255
        shadowPaint.colorFilter = drawableColorFilter
        canvas.save()
        canvas.translate(0f, dp(1f))
        canvas.drawPath(path, shadowPaint)
        canvas.restore()

        fillPaint.color = if (selected) spec.selectedSurfaceColor else spec.surfaceColor
        fillPaint.alpha = drawableAlpha
        fillPaint.colorFilter = drawableColorFilter
        canvas.drawPath(path, fillPaint)

        if (spec.strokeWidthDp > 0f) {
            strokePaint.color = if (selected) spec.selectedSurfaceColor else spec.surfaceColor
            strokePaint.alpha = drawableAlpha
            strokePaint.strokeWidth = dp(spec.strokeWidthDp)
            strokePaint.colorFilter = drawableColorFilter
            canvas.drawPath(path, strokePaint)
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        boundsF.set(bounds)
        surfaceDrawable?.bounds = bounds
        rebuildPath()
    }

    fun setDirection(direction: KeyboardSkinPopupDirection) {
        if (this.direction == direction) return
        this.direction = direction
        rebuildPath()
        invalidateSelf()
    }

    fun setSelected(selected: Boolean) {
        if (this.selected == selected) return
        this.selected = selected
        surfaceDrawable?.state = if (selected) {
            intArrayOf(android.R.attr.state_pressed, android.R.attr.state_enabled)
        } else {
            intArrayOf()
        }
        invalidateSelf()
    }

    private fun rebuildPath() {
        path.reset()
        if (boundsF.isEmpty) return
        val radius = dp(spec.cornerRadiusDp)
        val stemWidth = dp(spec.stemWidthDp)
        val stemHeight = dp(spec.stemHeightDp)

        when {
            kind == KeyboardSkinPopupKind.KEY_PREVIEW -> {
                val bodyBottom = boundsF.bottom - stemHeight
                path.addRoundRect(
                    boundsF.left,
                    boundsF.top,
                    boundsF.right,
                    bodyBottom,
                    radius,
                    radius,
                    Path.Direction.CW,
                )
                addBottomStem(boundsF.centerX(), bodyBottom, stemWidth, stemHeight)
            }

            kind == KeyboardSkinPopupKind.FLICK_DIRECTIONAL -> {
                addDirectionalPath(radius, stemWidth, stemHeight)
            }

            else -> path.addRoundRect(boundsF, radius, radius, Path.Direction.CW)
        }
    }

    private fun addBottomStem(centerX: Float, bodyBottom: Float, width: Float, height: Float) {
        path.moveTo(centerX - width / 2f, bodyBottom - dp(0.5f))
        path.lineTo(centerX, bodyBottom + height)
        path.lineTo(centerX + width / 2f, bodyBottom - dp(0.5f))
        path.close()
    }

    private fun addDirectionalPath(radius: Float, pointerWidth: Float, pointerHeight: Float) {
        val left = boundsF.left
        val top = boundsF.top
        val right = boundsF.right
        val bottom = boundsF.bottom
        when (direction) {
            KeyboardSkinPopupDirection.UP -> {
                val bodyTop = top + pointerHeight
                path.addRoundRect(left, bodyTop, right, bottom, radius, radius, Path.Direction.CW)
                path.moveTo(boundsF.centerX() - pointerWidth / 2f, bodyTop)
                path.lineTo(boundsF.centerX(), top)
                path.lineTo(boundsF.centerX() + pointerWidth / 2f, bodyTop)
                path.close()
            }

            KeyboardSkinPopupDirection.DOWN -> {
                val bodyBottom = bottom - pointerHeight
                path.addRoundRect(left, top, right, bodyBottom, radius, radius, Path.Direction.CW)
                path.moveTo(boundsF.centerX() - pointerWidth / 2f, bodyBottom)
                path.lineTo(boundsF.centerX(), bottom)
                path.lineTo(boundsF.centerX() + pointerWidth / 2f, bodyBottom)
                path.close()
            }

            KeyboardSkinPopupDirection.LEFT -> {
                val bodyLeft = left + pointerHeight
                path.addRoundRect(bodyLeft, top, right, bottom, radius, radius, Path.Direction.CW)
                path.moveTo(bodyLeft, boundsF.centerY() - pointerWidth / 2f)
                path.lineTo(left, boundsF.centerY())
                path.lineTo(bodyLeft, boundsF.centerY() + pointerWidth / 2f)
                path.close()
            }

            KeyboardSkinPopupDirection.RIGHT -> {
                val bodyRight = right - pointerHeight
                path.addRoundRect(left, top, bodyRight, bottom, radius, radius, Path.Direction.CW)
                path.moveTo(bodyRight, boundsF.centerY() - pointerWidth / 2f)
                path.lineTo(right, boundsF.centerY())
                path.lineTo(bodyRight, boundsF.centerY() + pointerWidth / 2f)
                path.close()
            }

            KeyboardSkinPopupDirection.CENTER -> path.addRoundRect(
                boundsF,
                radius,
                radius,
                Path.Direction.CW,
            )
        }
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        surfaceDrawable?.alpha = drawableAlpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        drawableColorFilter = colorFilter
        surfaceDrawable?.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Drawable opacity is not used by the skin renderer")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun dp(value: Float): Float = value * density
}
