package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.PopupWindow
import androidx.core.graphics.drawable.toDrawable
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.custom_keyboard.view.TfbiFlickPopupView
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

@SuppressLint("ClickableViewAccessibility")
class FlickLongPressInputController(
    private val context: Context,
    private val flickSensitivity: Float
) {

    interface Listener {
        fun onPress(character: String)
        fun onCommit(character: String, isFlick: Boolean)
    }

    companion object {
        private const val MAX_ANGLE_DIFFERENCE = 70.0
    }

    var listener: Listener? = null

    private var attachedView: View? = null
    private var normalMap: Map<TfbiFlickDirection, String> = emptyMap()
    private var longPressMap: Map<TfbiFlickDirection, String> = emptyMap()
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var currentDirection: TfbiFlickDirection? = null
    private var longPressDirection: TfbiFlickDirection? = null
    private var isTouchActive = false
    private var isLongPressActive = false

    private var popupView: TfbiFlickPopupView? = null
    private var popupWindow: PopupWindow? = null
    private var popupWindowAnchorProvider: (() -> View?)? = null
    private var longPressTimeout: Long = ViewConfiguration.getLongPressTimeout().toLong()

    private var popupBackgroundColor: Int? = null
    private var popupHighlightedColor: Int? = null
    private var popupTextColor: Int? = null
    private var popupStyle = PopupViewStyle(100, 20f)

    private val longPressRunnable = Runnable {
        val direction = longPressDirection ?: return@Runnable
        if (!isTouchActive || currentDirection != direction || isLongPressActive) return@Runnable
        if (longPressMap[direction].orEmpty().isEmpty()) return@Runnable

        isLongPressActive = true
        updatePopupCharacters()
        popupView?.highlightDirection(direction)
    }

    fun setPopupColors(backgroundColor: Int, highlightedColor: Int, textColor: Int) {
        popupBackgroundColor = backgroundColor
        popupHighlightedColor = highlightedColor
        popupTextColor = textColor
    }

    fun applyPopupViewStyle(style: PopupViewStyle) {
        popupStyle = PopupViewStyle(
            sizeScalePercent = style.sizeScalePercent.coerceIn(50, 200),
            textSizeSp = style.textSizeSp.coerceIn(8f, 48f),
            backgroundColor = style.backgroundColor,
            textColor = style.textColor
        )
        popupView?.applyPopupViewStyle(popupStyle)
    }

    fun setLongPressTimeout(timeoutMillis: Long) {
        longPressTimeout = timeoutMillis.coerceIn(100L, 2000L)
    }

    fun setPopupWindowAnchorProvider(provider: (() -> View?)?) {
        popupWindowAnchorProvider = provider
    }

    fun attach(
        view: View,
        normalMap: Map<TfbiFlickDirection, String>,
        longPressMap: Map<TfbiFlickDirection, String>
    ) {
        attachedView = view
        this.normalMap = normalMap
        this.longPressMap = longPressMap
        view.setOnTouchListener { _, event -> handleTouchEvent(event) }
    }

    fun cancel() {
        clearLongPressCallback()
        resetState()
        attachedView?.setOnTouchListener(null)
        attachedView = null
        normalMap = emptyMap()
        longPressMap = emptyMap()
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        val view = attachedView ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleTouchDown(event, view)
            MotionEvent.ACTION_MOVE -> handleTouchMove(event)
            MotionEvent.ACTION_UP -> handleTouchUp()
            MotionEvent.ACTION_CANCEL -> resetState()
        }
        return true
    }

    private fun handleTouchDown(event: MotionEvent, view: View) {
        resetState()
        isTouchActive = true
        initialTouchX = event.x
        initialTouchY = event.y
        currentDirection = TfbiFlickDirection.TAP.takeIf { isConfigured(it) }
        listener?.onPress(normalMap[TfbiFlickDirection.TAP].orEmpty())

        if (hasAnyConfiguredOutput()) {
            showPopup(view)
            popupView?.highlightDirection(TfbiFlickDirection.TAP)
        }
        scheduleLongPressIfNeeded(currentDirection)
    }

    private fun handleTouchMove(event: MotionEvent) {
        val dx = event.x - initialTouchX
        val dy = event.y - initialTouchY
        val nextDirection = resolveDirection(dx, dy)

        if (nextDirection == currentDirection) return

        currentDirection = nextDirection
        isLongPressActive = false
        scheduleLongPressIfNeeded(nextDirection)
        updatePopupCharacters()
        popupView?.highlightDirection(nextDirection ?: TfbiFlickDirection.TAP)
    }

    private fun handleTouchUp() {
        val direction = currentDirection
        val output = if (direction == null) {
            ""
        } else if (isLongPressActive) {
            longPressMap[direction].orEmpty().ifEmpty { normalMap[direction].orEmpty() }
        } else {
            normalMap[direction].orEmpty()
        }

        if (direction != null && output.isNotEmpty()) {
            listener?.onCommit(output, direction != TfbiFlickDirection.TAP)
        }
        resetState()
    }

    private fun scheduleLongPressIfNeeded(direction: TfbiFlickDirection?) {
        clearLongPressCallback()
        longPressDirection = direction
        if (direction == null || longPressMap[direction].orEmpty().isEmpty()) return
        attachedView?.postDelayed(longPressRunnable, longPressTimeout)
    }

    private fun resolveDirection(dx: Float, dy: Float): TfbiFlickDirection? {
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (distance < flickSensitivity) {
            return TfbiFlickDirection.TAP.takeIf { isConfigured(it) }
        }

        val direction = calculateRawDirection(dx, dy) ?: return null
        return direction.takeIf { isConfigured(it) }
    }

    private fun calculateRawDirection(dx: Float, dy: Float): TfbiFlickDirection? {
        val centerAngles = mapOf(
            TfbiFlickDirection.RIGHT to 0.0,
            TfbiFlickDirection.DOWN_RIGHT to 35.0,
            TfbiFlickDirection.DOWN to 90.0,
            TfbiFlickDirection.DOWN_LEFT to 125.0,
            TfbiFlickDirection.LEFT to 180.0,
            TfbiFlickDirection.UP_LEFT to -125.0,
            TfbiFlickDirection.UP to -90.0,
            TfbiFlickDirection.UP_RIGHT to -35.0
        )

        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
        val closest = centerAngles.map { (direction, centerAngle) ->
            val targetAngle = if (direction == TfbiFlickDirection.LEFT && angle < 0) {
                -180.0
            } else {
                centerAngle
            }

            var diff = abs(angle - targetAngle)
            if (diff > 180) diff = 360 - diff
            direction to diff
        }.minByOrNull { it.second }

        if (closest == null || closest.second > MAX_ANGLE_DIFFERENCE) return null
        return closest.first
    }

    private fun isConfigured(direction: TfbiFlickDirection): Boolean =
        normalMap[direction].orEmpty().isNotEmpty() ||
                longPressMap[direction].orEmpty().isNotEmpty()

    private fun hasAnyConfiguredOutput(): Boolean =
        TfbiFlickDirection.entries.any { isConfigured(it) }

    private fun displayCharacter(direction: TfbiFlickDirection): String {
        val longPress = longPressMap[direction].orEmpty()
        if (isLongPressActive && currentDirection == direction && longPress.isNotEmpty()) {
            return longPress
        }
        return normalMap[direction].orEmpty()
    }

    private fun updatePopupCharacters() {
        popupView?.setCharacters(
            displayCharacter(TfbiFlickDirection.TAP),
            TfbiFlickDirection.entries
                .filter { it != TfbiFlickDirection.TAP && isConfigured(it) }
                .associateWith { displayCharacter(it) }
        )
    }

    private fun showPopup(anchorView: View) {
        popupWindow?.dismiss()
        popupView = TfbiFlickPopupView(context).apply {
            if (popupBackgroundColor != null && popupHighlightedColor != null && popupTextColor != null) {
                setColors(popupBackgroundColor!!, popupHighlightedColor!!, popupTextColor!!)
            }
            applyPopupViewStyle(popupStyle)
        }
        updatePopupCharacters()

        val scale = popupStyle.sizeScalePercent.coerceIn(50, 200) / 100f
        val popupWidth = (anchorView.width * 3 * scale).toInt().coerceAtLeast(1)
        val popupHeight = (anchorView.height * 3 * scale).toInt().coerceAtLeast(1)
        popupWindow = PopupWindow(popupView, popupWidth, popupHeight, false).apply {
            isTouchable = false
            isFocusable = false
            setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
            isClippingEnabled = false
        }

        val windowAnchor = popupWindowAnchorProvider?.invoke() ?: anchorView
        if (!isAnchorReady(anchorView, windowAnchor)) return
        val location = getLocationRelativeToWindowAnchor(anchorView, windowAnchor)
        val offsetX = location[0] + anchorView.width / 2 - popupWidth / 2
        val offsetY = location[1] + anchorView.height / 2 - popupHeight / 2
        runCatching {
            popupWindow?.showAtLocation(windowAnchor, Gravity.NO_GRAVITY, offsetX, offsetY)
        }
    }

    private fun resetState() {
        clearLongPressCallback()
        popupWindow?.dismiss()
        popupWindow = null
        popupView = null
        isTouchActive = false
        isLongPressActive = false
        currentDirection = null
        longPressDirection = null
    }

    private fun clearLongPressCallback() {
        attachedView?.removeCallbacks(longPressRunnable)
    }

    private fun isAnchorReady(keyAnchor: View, windowAnchor: View?): Boolean {
        if (!keyAnchor.isAttachedToWindow) return false
        if (windowAnchor == null) return false
        if (!windowAnchor.isAttachedToWindow) return false
        return windowAnchor.windowToken != null
    }
}
