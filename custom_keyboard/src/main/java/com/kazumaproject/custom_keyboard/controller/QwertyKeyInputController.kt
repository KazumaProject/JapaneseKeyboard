package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import com.kazumaproject.core.data.popup.QwertyPopupViewStyleSet
import com.kazumaproject.core.domain.flick.FlickGestureMath
import com.kazumaproject.core.domain.flick.GestureSessionConfig
import com.kazumaproject.core.domain.flick.GestureSessionConfigSource
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.core.ui.skin.SkinPopupWindowHost
import com.kazumaproject.custom_keyboard.data.QwertyKeyLayer
import com.kazumaproject.custom_keyboard.view.QwertyAlternativesView

/** Owns one key's gesture; the keyboard already routes each pointer to its original key. */
class QwertyKeyInputController(
    private val gestureConfigSource: GestureSessionConfigSource,
    private val layerProvider: () -> QwertyKeyLayer,
    private val styleProvider: () -> QwertyPopupViewStyleSet,
    private val fontProvider: () -> KeyboardFontSnapshot,
    private val showPreview: () -> Boolean,
    private val windowAnchorProvider: () -> View?,
    private val onPress: (String) -> Unit,
    private val onCommit: (String) -> Unit
) {
    private var anchor: View? = null
    private var activeLayer: QwertyKeyLayer? = null
    private var gesture: GestureSessionConfig? = null
    private var initialX = 0f
    private var initialY = 0f
    private var canceled = false
    private var choosing = false
    private var popup: PopupWindow? = null
    private var alternatives: QwertyAlternativesView? = null
    private var popupBounds = Rect()
    private var observedTree: ViewTreeObserver? = null
    private val preDraw = ViewTreeObserver.OnPreDrawListener { positionPopup(); true }
    private val longPress = Runnable {
        val layer = activeLayer ?: return@Runnable
        if (!canceled && layer.slots.any { it.output.isNotEmpty() }) {
            dismissPopup()
            val key = anchor ?: return@Runnable
            val view = QwertyAlternativesView(key.context).apply {
                setContent(layer, styleProvider().variation, fontProvider())
            }
            alternatives = view
            choosing = true
            if (!showPopup(view)) {
                choosing = false
                alternatives = null
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(view: View) {
        cancel()
        anchor = view
        view.setOnClickListener { layerProvider().output.takeIf { it.isNotEmpty() }?.let(onCommit) }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dismissPopup()
                    activeLayer = layerProvider()
                    gesture = gestureConfigSource.snapshot()
                    initialX = event.rawX
                    initialY = event.rawY
                    canceled = false
                    choosing = false
                    view.isPressed = true
                    onPress(activeLayer!!.output)
                    if (showPreview()) showKeyPreview()
                    view.postDelayed(longPress, gesture!!.longPressTimeoutMillis)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (choosing) {
                        alternatives?.let { content ->
                            val location = IntArray(2).also(content::getLocationOnScreen)
                            content.updateSelection(event.rawX - location[0], event.rawY - location[1])
                        }
                    } else gesture?.let { config ->
                        if (FlickGestureMath.isThresholdCrossed(event.rawX - initialX,
                                event.rawY - initialY, config.flickThresholdPx, config.flickThresholdShape)) {
                            view.removeCallbacks(longPress)
                        }
                        if (event.x < -config.flickThresholdPx || event.x > view.width + config.flickThresholdPx ||
                            event.y < -config.flickThresholdPx || event.y > view.height + config.flickThresholdPx) {
                            canceled = true
                            view.isPressed = false
                            view.removeCallbacks(longPress)
                            dismissPopup()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val output = if (canceled) null else if (choosing) alternatives?.selectedOutput else activeLayer?.output
                    endGesture()
                    output?.takeIf { it.isNotEmpty() }?.let(onCommit)
                    true
                }
                MotionEvent.ACTION_CANCEL -> { endGesture(); true }
                else -> false
            }
        }
    }

    private fun showKeyPreview() {
        val key = anchor ?: return
        val layer = activeLayer ?: return
        val style = styleProvider().keyPreview
        val skin = KeyboardSkinRegistry.find(style.skinId)
        val content = TextView(key.context).apply {
            text = layer.previewLabel
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            includeFontPadding = false
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(style.textColor ?: skin?.palette?.text ?: ContextCompat.getColor(context,
                com.kazumaproject.core.R.color.keyboard_icon_color))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, style.textSizeSp)
            val location = IntArray(2).also(key::getLocationOnScreen)
            val geometry = skin?.keyPreview(resources, key.width, key.height, location[0],
                resources.displayMetrics.widthPixels, key.top > 0)
            if (geometry != null) background = geometry.background else {
                setBackgroundResource(com.kazumaproject.core.R.drawable.key_preview_bubble)
                style.backgroundColor?.let { backgroundTintList = ColorStateList.valueOf(it) }
            }
            skin?.configurePreviewText(this)
            setPadding(paddingLeft, (6 * resources.displayMetrics.density).toInt(), paddingRight, paddingBottom)
        }
        KeyboardFontApplicator.apply(content, fontProvider())
        showPopup(content)
    }

    private fun showPopup(content: View): Boolean {
        val key = anchor ?: return false
        if (!key.isAttachedToWindow) return false
        val window = PopupWindow(content, 1, 1, false).apply {
            isTouchable = false
            isClippingEnabled = false
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 6f
            animationStyle = 0
            if (Build.VERSION.SDK_INT >= 29) setIsLaidOutInScreen(true)
        }
        popup = window
        positionPopup()
        if (!window.isShowing) { popup = null; return false }
        observedTree = key.viewTreeObserver.also { it.addOnPreDrawListener(preDraw) }
        KeyboardSkinRegistry.find(styleProvider().variation.skinId)?.showPopup(content)
        return true
    }

    private fun positionPopup() {
        val key = anchor ?: return
        val window = popup ?: return
        if (!key.isAttachedToWindow) { endGesture(); return }
        val layer = activeLayer ?: return
        val style = if (choosing) styleProvider().variation else styleProvider().keyPreview
        val scale = style.sizeScalePercent.coerceIn(50, 200) / 100f
        val density = key.resources.displayMetrics.density
        val location = IntArray(2).also(key::getLocationOnScreen)
        adjustPopupAnchorForTransform(key, location)
        val size = Point(key.resources.displayMetrics.widthPixels, key.resources.displayMetrics.heightPixels)
        @Suppress("DEPRECATION")
        key.display?.getRealSize(size)
        val insets = key.rootWindowInsets?.let {
            WindowInsetsCompat.toWindowInsetsCompat(it, key).getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        }
        val viewport = Rect(insets?.left ?: 0, insets?.top ?: 0,
            size.x - (insets?.right ?: 0), size.y - (insets?.bottom ?: 0))
        val previewGeometry = if (choosing) null else KeyboardSkinRegistry.find(style.skinId)?.keyPreview(
            key.resources, key.width, key.height, location[0], size.x, key.top > 0)
        val width = if (choosing) (48 * density * scale * layer.columns).toInt() else
            previewGeometry?.width ?: (key.width * 2 * scale).toInt()
        val height = if (choosing) (56 * density * scale * layer.rowCount).toInt() else
            previewGeometry?.height ?: ((key.height * 2 + 64) * scale).toInt()
        val w = width.coerceIn(1, viewport.width().coerceAtLeast(1))
        val h = height.coerceIn(1, viewport.height().coerceAtLeast(1))
        val desiredX = location[0] + (previewGeometry?.xOffset ?: (key.width / 2 - w / 2))
        val desiredY = if (choosing) location[1] - h else
            location[1] + key.height + (previewGeometry?.yOffset ?: -h)
        val x = desiredX.coerceIn(viewport.left, viewport.right - w)
        val y = desiredY.coerceIn(viewport.top, viewport.bottom - h)
        val bounds = Rect(x, y, x + w, y + h)
        val suppliedAnchor = windowAnchorProvider() ?: generateSequence(key as View?) { it.parent as? View }
            .filterIsInstance<SkinPopupWindowHost>().firstOrNull()?.applicationWindowView ?: key
        var windowX = x
        var windowY = y
        if (Build.VERSION.SDK_INT < 29) {
            val screen = IntArray(2).also(suppliedAnchor::getLocationOnScreen)
            val local = IntArray(2).also(suppliedAnchor::getLocationInWindow)
            windowX -= screen[0] - local[0]
            windowY -= screen[1] - local[1]
        }
        runCatching {
            if (!window.isShowing) {
                window.width = w
                window.height = h
                window.showAtLocation(suppliedAnchor, Gravity.NO_GRAVITY, windowX, windowY)
            } else if (bounds != popupBounds) window.update(windowX, windowY, w, h)
            popupBounds = bounds
        }.onFailure { dismissPopup() }
    }

    private fun dismissPopup() {
        observedTree?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDraw)
        observedTree = null
        popup?.dismiss()
        popup = null
        alternatives = null
        popupBounds.setEmpty()
    }

    private fun endGesture() {
        anchor?.removeCallbacks(longPress)
        anchor?.isPressed = false
        dismissPopup()
        activeLayer = null
        gesture = null
        choosing = false
        canceled = true
    }

    fun cancel() {
        endGesture()
        anchor?.setOnTouchListener(null)
        anchor?.setOnClickListener(null)
        anchor = null
    }
}
