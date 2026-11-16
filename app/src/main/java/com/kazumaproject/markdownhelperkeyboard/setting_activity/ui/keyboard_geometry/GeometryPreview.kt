package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_geometry

import android.content.Context
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.GeometryKeyboard
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry.KeyboardGeometryPreference
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.keyboard_presentation.KeyboardPreviewTarget
import com.kazumaproject.markdownhelperkeyboard.keyboard_presentation.KeyboardPreviewView
import com.kazumaproject.markdownhelperkeyboard.setting_activity.keyboard_size.IndependentKeyboardSize

internal enum class PreviewKeyboard { QWERTY, ROMAJI, TENKEY, SUMIRE, GOJUON, CUSTOM, MORSE }

/** Settings resize handles wrap the same native presentation used by the home and theme picker. */
internal class GeometryPreview(context: Context, keyboard: PreviewKeyboard) : FrameLayout(context) {
    constructor(context: Context, keyboard: GeometryKeyboard) : this(context, PreviewKeyboard.valueOf(keyboard.name))
    private val preview = KeyboardPreviewView(context, KeyboardPreviewTarget(KeyboardType.valueOf(keyboard.name)))
    private var previousBody: RectF? = null
    private var previousAdjustment: RectF? = null
    private var previousHeight: Float? = null
    var onPresentationLayout: (() -> Unit)? = null
    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        preview.respectOrientationOverride = false
        preview.respectSavedInputMode = false
        addView(preview, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        preview.onReady = { requestLayout(); invalidate() }
    }
    fun update(value: KeyboardGeometryPreference, landscape: Boolean, widthPercent: Int, alignEnd: Boolean,
        sizeOverride: IndependentKeyboardSize? = null, customLayout: KeyboardLayout? = null) {
        preview.landscape = landscape
        preview.geometryOverride = value
        preview.sizeOverride = sizeOverride
        preview.widthOverride = widthPercent
        preview.alignEndOverride = alignEnd
        preview.customOverride = customLayout
        preview.reload()
    }
    internal fun bodyBounds(): RectF? = preview.bodyBounds() ?: previousBody?.takeIf { preview.loading }?.let(::RectF)
    internal fun adjustmentBounds(): RectF? = preview.adjustmentBounds() ?: previousAdjustment?.takeIf { preview.loading }?.let(::RectF)
    internal fun surfaceWidth(): Float? = adjustmentBounds()?.width()
    internal fun bodyHeight(): Float? = if (preview.ready) preview.logicalBodyBounds().height() else previousHeight?.takeIf { preview.loading }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Resize handles use physical keyboard coordinates, including content above the viewport.
        preview.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        setMeasuredDimension(preview.measuredWidth, preview.measuredHeight)
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (preview.ready) {
            previousBody = preview.bodyBounds()
            previousAdjustment = preview.adjustmentBounds()
            previousHeight = preview.logicalBodyBounds().height()
            onPresentationLayout?.invoke()
        }
    }

    @VisibleForTesting
    internal fun snapshotForTesting(): GeometryPreviewSnapshot {
        check(preview.ready) { "Wait for the native preview to finish loading" }
        val body = preview.logicalBodyBounds()
        val native = requireNotNull(preview.nativeBody)
        val frames = mutableListOf<GeometryPreviewKeyFrame>()
        fun visit(view: View) {
            if (view.visibility != VISIBLE) return
            if ((view is TextView || view is ImageView) && view.width > 0 && view.height > 0) {
                val bounds = android.graphics.Rect(0, 0, view.width, view.height)
                (native as ViewGroup).offsetDescendantRectToMyCoords(view, bounds)
                frames += GeometryPreviewKeyFrame(view.id, (view as? TextView)?.text?.toString(),
                    bounds.exactCenterX(), bounds.exactCenterY(), view.rotation, bounds.width() / 2f, bounds.height() / 2f)
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(native)
        return GeometryPreviewSnapshot(preview.logicalScreenWidth(), body.height(), body.left, body.width(), frames)
    }
}

@VisibleForTesting
internal data class GeometryPreviewSnapshot(val screenWidth: Float, val bodyHeight: Float, val bodyLeft: Float,
    val bodyWidth: Float, val keys: List<GeometryPreviewKeyFrame>)

@VisibleForTesting
internal data class GeometryPreviewKeyFrame(val id: Int, val text: String?, val x: Float, val y: Float,
    val angle: Float, val halfWidth: Float, val halfHeight: Float)
