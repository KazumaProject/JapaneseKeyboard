package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import androidx.core.graphics.drawable.toDrawable
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.data.popup.TfbiFlickStartPositionMode
import com.kazumaproject.core.data.popup.TfbiPopupPresentationMode
import com.kazumaproject.core.domain.flick.FlickGestureMath
import com.kazumaproject.core.domain.flick.GestureSessionConfig
import com.kazumaproject.core.domain.flick.GestureSessionConfigSource
import com.kazumaproject.core.domain.flick.TfbiDiagonalRecognitionMode
import com.kazumaproject.custom_keyboard.data.BuiltInTwoStepCharacters
import com.kazumaproject.custom_keyboard.data.BuiltInTwoStepKey
import com.kazumaproject.custom_keyboard.data.TfbiFlickRotation
import com.kazumaproject.custom_keyboard.data.TfbiGuideFingerPosition
import com.kazumaproject.custom_keyboard.data.TfbiGuideGrid
import com.kazumaproject.custom_keyboard.data.TfbiGuidePopupState
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.*
import com.kazumaproject.custom_keyboard.view.TfbiFlickPopupView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/** Only compiled-in kana rows can produce input. Custom output maps have no entry point. */
internal class BuiltInTwoStepController(
    private val context: Context,
    private val settings: GestureSessionConfigSource,
    private val key: BuiltInTwoStepKey,
    private val rotation: TfbiFlickRotation
) {
    interface Listener {
        fun onPress(character: Char)
        fun onSelection(character: Char, isFlick: Boolean)
        fun onCommit(character: Char, isFlick: Boolean)
        fun onCanceled()
    }

    private data class Point(val x: Float, val y: Float)
    private data class Gesture(
        val start: Point,
        val config: GestureSessionConfig,
        val startMode: TfbiFlickStartPositionMode,
        var anchor: Point = start,
        var first: TfbiFlickDirection? = null,
        var selected: TfbiFlickDirection = TAP,
        var finger: TfbiGuideFingerPosition? = null,
        var expanded: Boolean = false
    )

    var listener: Listener? = null
    private var view: View? = null
    private var gesture: Gesture? = null
    private var anchorProvider: (() -> View?)? = null
    private var presentation = TfbiPopupPresentationMode.LEGACY_GRID
    private var startMode = TfbiFlickStartPositionMode.TOUCH_POINT
    private var style = PopupViewStyle(100, 20f)
    private var colors: Triple<Int, Int, Int>? = null
    private var displayTransform: (String) -> String = { it }
    private var popupView: TfbiFlickPopupView? = null
    private var popupWindow: PopupWindow? = null
    private val guidePopupHost by lazy { TfbiGuidePopupHost(context) { anchorProvider?.invoke() } }
    private var guideVisible = false

    // Holding changes the guide only. It cannot select another vocabulary or action map.
    private val expandGuide = Runnable {
        gesture?.takeIf { it.first == null }?.let {
            it.expanded = true
            render(it)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(target: View) {
        cancel()
        view = target
        target.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    endGesture()
                    val point = Point(event.x, event.y)
                    val next = Gesture(point, settings.snapshot(), startMode)
                    gesture = next
                    updateFinger(next, event)
                    listener?.onPress(tap())
                    render(next)
                    target.postDelayed(expandGuide, next.config.longPressTimeoutMillis)
                }
                MotionEvent.ACTION_MOVE -> gesture?.let { update(it, event, releasing = false) }
                MotionEvent.ACTION_UP -> gesture?.let {
                    update(it, event, releasing = true)
                    val character = selected(it)
                    val isFlick = it.first != null
                    endGesture()
                    listener?.onCommit(character, isFlick)
                }
                MotionEvent.ACTION_CANCEL -> {
                    endGesture()
                    listener?.onCanceled()
                }
            }
            true
        }
    }

    fun cancel() {
        endGesture()
        view?.setOnTouchListener(null)
        view = null
        listener?.onCanceled()
    }

    fun setPopupWindowAnchorProvider(provider: (() -> View?)?) { anchorProvider = provider }
    fun setTfbiFlickStartPositionMode(mode: TfbiFlickStartPositionMode) { startMode = mode }
    fun setPopupPresentationMode(mode: TfbiPopupPresentationMode) {
        if (presentation != mode) {
            endGesture()
            listener?.onCanceled()
            presentation = mode
        }
    }
    fun setInputTextTransform(transform: (String) -> String) {
        displayTransform = transform
        popupView?.setInputTextTransform(transform)
        guidePopupHost.setInputTextTransform(transform)
    }
    fun setPopupColors(backgroundColor: Int, highlightedColor: Int, textColor: Int) {
        colors = Triple(backgroundColor, highlightedColor, textColor)
        popupView?.setColors(backgroundColor, highlightedColor, textColor)
        guidePopupHost.setColors(backgroundColor, highlightedColor, textColor)
    }
    fun applyPopupViewStyle(value: PopupViewStyle) {
        style = value.copy(sizeScalePercent = value.sizeScalePercent.coerceIn(50, 200),
            textSizeSp = value.textSizeSp.coerceIn(8f, 48f))
        popupView?.applyPopupViewStyle(style)
        guidePopupHost.applyPopupViewStyle(style)
    }

    private fun tap(): Char = BuiltInTwoStepCharacters.output(key, TAP, TAP, rotation)!!
    private fun selected(g: Gesture): Char = g.first?.let {
        BuiltInTwoStepCharacters.output(key, it, g.selected, rotation)
            ?: BuiltInTwoStepCharacters.output(key, it, it, rotation)
    } ?: tap()

    private fun update(g: Gesture, event: MotionEvent, releasing: Boolean) {
        updateFinger(g, event)
        if (g.first == null) {
            val direction = direction(g, event.x - g.start.x, event.y - g.start.y,
                BuiltInTwoStepCharacters.firstDirections(key))
            if (direction != TAP) {
                g.first = direction
                g.selected = direction
                g.anchor = Point(event.x, event.y)
                view?.removeCallbacks(expandGuide)
            }
        } else {
            val atCenter = if (presentation == TfbiPopupPresentationMode.GUIDE_ABOVE_KEY) {
                g.finger?.let(::isTfbiGuideTapCell) == true
            } else hypot(event.x - g.start.x, event.y - g.start.y) < 70f
            if (atCenter) {
                g.first = null
                g.selected = TAP
                g.expanded = false
            } else {
                val entry = g.first!!
                val dx = event.x - g.anchor.x
                val dy = event.y - g.anchor.y
                var next = direction(g, dx, dy,
                    BuiltInTwoStepCharacters.secondDirections(key, entry, rotation))
                if (g.config.tfbiDiagonalRecognitionMode == TfbiDiagonalRecognitionMode.STABLE &&
                    next != entry && next in setOf(UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT)) {
                    val turn = if (entry == LEFT || entry == RIGHT) abs(dy) else abs(dx)
                    if (turn < g.config.flickThresholdPx) next = entry
                }
                g.selected = if (next == TAP) {
                    if (releasing) g.selected else entry
                } else next
            }
        }
        listener?.onSelection(selected(g), g.first != null)
        if (!releasing) render(g)
    }

    private fun direction(g: Gesture, dx: Float, dy: Float,
                          enabled: Set<TfbiFlickDirection>): TfbiFlickDirection {
        if (!FlickGestureMath.isThresholdCrossed(dx, dy, g.config.flickThresholdPx,
                g.config.flickThresholdShape)) return TAP
        if (presentation == TfbiPopupPresentationMode.GUIDE_ABOVE_KEY) {
            return g.finger?.let { resolveTfbiGuideGridDirection(it, enabled) } ?: TAP
        }
        val distance = hypot(dx, dy)
        fun score(direction: TfbiFlickDirection): Float {
            val cell = TfbiGuideGrid.cellOf(direction)
            val x = (cell.first - 1).toFloat()
            val y = (cell.second - 1).toFloat()
            return (dx * x + dy * y) / (distance * hypot(x, y))
        }
        val best = TfbiFlickDirection.entries.filter { it != TAP && it in enabled }
            .maxByOrNull(::score) ?: return TAP
        return if (score(best) >= cos(Math.toRadians(70.0))) best else TAP
    }

    private fun updateFinger(g: Gesture, event: MotionEvent) {
        view?.let { g.finger = resolveTfbiGuideFingerPosition(it, event, g.startMode,
            g.start.x, g.start.y) }
    }

    private fun render(g: Gesture) {
        if (g.expanded) {
            if (!g.config.showLongPressPopup) return
        } else if (!g.config.showFlickPopup) return
        val target = view ?: return
        val first = g.first
        val choices = if (first != null) {
            BuiltInTwoStepCharacters.secondDirections(key, first, rotation).associateWith {
                BuiltInTwoStepCharacters.output(key, first, it, rotation)!!.toString()
            }
        } else if (g.expanded) {
            BuiltInTwoStepCharacters.firstDirections(key).associateWith {
                BuiltInTwoStepCharacters.output(key, it, it, rotation)!!.toString()
            }
        } else emptyMap()
        if (presentation == TfbiPopupPresentationMode.GUIDE_ABOVE_KEY) {
            val state = TfbiGuidePopupState(selected(g).toString(), first ?: TAP, choices,
                first?.let { g.selected }, g.finger)
            if (guideVisible) guidePopupHost.update(state, first?.let { g.selected }) else {
                guidePopupHost.show(target, state, first?.let { g.selected }, style, displayTransform)
                guideVisible = true
            }
            return
        }
        val center = BuiltInTwoStepCharacters.output(key, first ?: TAP, TAP, rotation)!!.toString()
        popupView?.let {
            it.setCharacters(center, choices)
            it.highlightDirection(g.selected)
            return
        }
        val anchor = anchorProvider?.invoke() ?: target
        if (!target.isAttachedToWindow || !anchor.isAttachedToWindow || anchor.windowToken == null) return
        val content = TfbiFlickPopupView(context).apply {
            setInputTextTransform(displayTransform)
            applyPopupViewStyle(style)
            colors?.let { setColors(it.first, it.second, it.third) }
            setCharacters(center, choices)
            highlightDirection(g.selected)
        }
        val width = (target.width * 3 * style.sizeScalePercent / 100f).toInt().coerceAtLeast(1)
        val height = (target.height * 3 * style.sizeScalePercent / 100f).toInt().coerceAtLeast(1)
        val window = PopupWindow(content, width, height, false).apply {
            isTouchable = false
            isFocusable = false
            isClippingEnabled = false
            setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
        }
        val location = getLocationRelativeToWindowAnchor(target, anchor)
        runCatching { window.showAtLocation(anchor, Gravity.NO_GRAVITY,
            location[0] + (target.width - width) / 2, location[1] + (target.height - height) / 2) }
            .onSuccess { popupView = content; popupWindow = window }
    }

    private fun endGesture() {
        view?.removeCallbacks(expandGuide)
        gesture = null
        popupWindow?.dismiss()
        popupWindow = null
        popupView = null
        guidePopupHost.dismiss()
        guideVisible = false
    }
}
