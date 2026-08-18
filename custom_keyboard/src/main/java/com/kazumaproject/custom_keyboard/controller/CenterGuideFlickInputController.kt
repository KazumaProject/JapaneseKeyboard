package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.data.keyboard.KeyboardSkinRef
import com.kazumaproject.core.domain.flick.FlickGestureMath
import com.kazumaproject.core.domain.flick.FixedGestureSessionConfigSource
import com.kazumaproject.core.domain.flick.GestureSessionConfig
import com.kazumaproject.core.domain.flick.GestureSessionConfigSource
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.TfbiGuideFingerPosition
import com.kazumaproject.custom_keyboard.data.TfbiGuidePopupState
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection

/**
 * One-stage Sumire flick input with a guide card and a cursor marker.
 *
 * The marker uses a virtual coordinate system: every gesture begins at the center of the key,
 * then follows the displacement from the real ACTION_DOWN point. Direction classification still
 * uses the shared gesture threshold, so this controller behaves consistently with other flick
 * styles while making the guide's origin stable for edge touches.
 */
@SuppressLint("ClickableViewAccessibility")
class CenterGuideFlickInputController(
    private val context: Context,
    private val gestureConfigSource: GestureSessionConfigSource
) {

    constructor(
        context: Context,
        flickSensitivity: Float
    ) : this(
        context = context,
        gestureConfigSource = FixedGestureSessionConfigSource(
            GestureSessionConfig(
                settingsRevision = 0L,
                flickSensitivity = 100,
                flickThresholdPx = flickSensitivity.coerceAtLeast(1f),
                longPressTimeoutMillis =
                    ViewConfiguration.getLongPressTimeout().toLong().coerceIn(100L, 2_000L)
            )
        )
    )

    interface Listener {
        fun onPress(character: String)
        fun onCommit(character: String, isFlick: Boolean)
        fun onSelectionChanged(character: String?, isFlick: Boolean) {}
        fun onCanceled() {}
    }

    var listener: Listener? = null

    private var attachedView: View? = null
    private var popupWindowAnchorProvider: (() -> View?)? = null
    private var textMap: Map<FlickDirection, String> = emptyMap()
    private var inputTextTransform: (String) -> String = { it }
    private var popupStyle = PopupViewStyle(100, 20f)
    private var keyboardSkinId: KeyboardSkinRef = KeyboardSkinRef.DEFAULT

    private var activeGestureConfig: GestureSessionConfig? = null
    private var isTouchActive = false
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var currentDirection = TfbiFlickDirection.TAP

    private val popupHost = TfbiGuidePopupHost(context) {
        popupWindowAnchorProvider?.invoke()
    }

    fun setPopupWindowAnchorProvider(provider: (() -> View?)?) {
        popupWindowAnchorProvider = provider
    }

    fun setInputTextTransform(transform: (String) -> String) {
        inputTextTransform = transform
        popupHost.setInputTextTransform(transform)
    }

    fun applyPopupViewStyle(style: PopupViewStyle) {
        popupStyle = PopupViewStyle(
            sizeScalePercent = style.sizeScalePercent.coerceIn(50, 200),
            textSizeSp = style.textSizeSp.coerceIn(8f, 48f),
            backgroundColor = style.backgroundColor,
            textColor = style.textColor
        )
        popupHost.applyPopupViewStyle(popupStyle)
    }

    fun setPopupColors(
        backgroundColor: Int,
        highlightedColor: Int,
        textColor: Int
    ) {
        popupHost.setColors(backgroundColor, highlightedColor, textColor)
    }

    fun setKeyboardSkin(skinId: KeyboardSkinRef) {
        keyboardSkinId = skinId
        popupHost.setKeyboardSkin(skinId)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(
        view: View,
        map: Map<FlickDirection, String>
    ) {
        attachedView?.takeUnless { it === view }?.setOnTouchListener(null)
        attachedView = view
        textMap = map
        view.setOnTouchListener { touchedView, event ->
            handleTouchEvent(touchedView, event)
        }
    }

    fun cancel() {
        listener?.onCanceled()
        activeGestureConfig = null
        isTouchActive = false
        popupHost.dismiss()
        attachedView?.setOnTouchListener(null)
        attachedView = null
        textMap = emptyMap()
    }

    private fun handleTouchEvent(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeGestureConfig = gestureConfigSource.snapshot()
                isTouchActive = true
                initialTouchX = event.x
                initialTouchY = event.y
                currentDirection = TfbiFlickDirection.TAP
                view.isPressed = true
                view.drawableHotspotChanged(event.x, event.y)

                val tapText = resolveText(TfbiFlickDirection.TAP)
                listener?.onPress(tapText)
                showGuide(view, deltaX = 0f, deltaY = 0f)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isTouchActive) return true
                view.drawableHotspotChanged(event.x, event.y)

                val deltaX = event.x - initialTouchX
                val deltaY = event.y - initialTouchY
                val newDirection = resolveCenterGuideDirection(
                    deltaX = deltaX,
                    deltaY = deltaY,
                    config = currentGestureConfig()
                )
                val directionChanged = newDirection != currentDirection
                currentDirection = newDirection

                updateGuide(view, deltaX, deltaY)
                if (directionChanged) {
                    listener?.onSelectionChanged(
                        resolveText(newDirection).takeIf(String::isNotEmpty),
                        newDirection != TfbiFlickDirection.TAP
                    )
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!isTouchActive) return true
                view.isPressed = false
                val character = resolveText(currentDirection)
                if (character.isNotEmpty()) {
                    listener?.onCommit(
                        character = character,
                        isFlick = currentDirection != TfbiFlickDirection.TAP
                    )
                }
                finishGesture()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (!isTouchActive) return true
                view.isPressed = false
                listener?.onCanceled()
                finishGesture()
                return true
            }
        }
        return false
    }

    private fun showGuide(view: View, deltaX: Float, deltaY: Float) {
        val position = resolveCenterGuideFingerPosition(
            keyWidth = view.width,
            keyHeight = view.height,
            deltaX = deltaX,
            deltaY = deltaY
        )
        popupHost.show(
            anchor = view,
            state = createPopupState(position),
            direction = null,
            style = popupStyle,
            inputTextTransform = inputTextTransform
        )
    }

    private fun updateGuide(view: View, deltaX: Float, deltaY: Float) {
        val position = resolveCenterGuideFingerPosition(
            keyWidth = view.width,
            keyHeight = view.height,
            deltaX = deltaX,
            deltaY = deltaY
        )
        popupHost.update(
            state = createPopupState(position),
            direction = null
        )
    }

    private fun createPopupState(position: TfbiGuideFingerPosition?): TfbiGuidePopupState {
        val optionLabels = linkedMapOf<TfbiFlickDirection, String>()
        listOf(
            TfbiFlickDirection.TAP,
            TfbiFlickDirection.UP,
            TfbiFlickDirection.DOWN,
            TfbiFlickDirection.LEFT,
            TfbiFlickDirection.RIGHT
        ).forEach { direction ->
            optionLabels[direction] = resolveText(direction)
        }
        return TfbiGuidePopupState(
            currentText = resolveText(currentDirection),
            currentSlot = currentDirection,
            optionLabels = optionLabels,
            fingerPosition = position
        )
    }

    private fun resolveText(direction: TfbiFlickDirection): String {
        val legacyDirection = direction.toLegacyFlickDirection() ?: return ""
        return legacyDirection.directionCandidates()
            .asSequence()
            .mapNotNull { textMap[it] }
            .firstOrNull(String::isNotEmpty)
            .orEmpty()
    }

    private fun finishGesture() {
        isTouchActive = false
        activeGestureConfig = null
        popupHost.dismiss()
        currentDirection = TfbiFlickDirection.TAP
    }

    private fun currentGestureConfig(): GestureSessionConfig =
        activeGestureConfig ?: gestureConfigSource.snapshot()
}

private fun TfbiFlickDirection.toLegacyFlickDirection(): FlickDirection? {
    return when (this) {
        TfbiFlickDirection.TAP -> FlickDirection.TAP
        TfbiFlickDirection.UP -> FlickDirection.UP
        TfbiFlickDirection.DOWN -> FlickDirection.DOWN
        TfbiFlickDirection.LEFT -> FlickDirection.UP_LEFT_FAR
        TfbiFlickDirection.RIGHT -> FlickDirection.UP_RIGHT_FAR
        TfbiFlickDirection.UP_RIGHT,
        TfbiFlickDirection.DOWN_RIGHT,
        TfbiFlickDirection.DOWN_LEFT,
        TfbiFlickDirection.UP_LEFT -> null
    }
}

internal fun resolveCenterGuideDirection(
    deltaX: Float,
    deltaY: Float,
    config: GestureSessionConfig
): TfbiFlickDirection {
    return when (
        FlickGestureMath.cardinalDirection(
            deltaX = deltaX,
            deltaY = deltaY,
            thresholdPx = config.flickThresholdPx,
            thresholdShape = config.flickThresholdShape
        )
    ) {
        com.kazumaproject.core.domain.flick.FlickDirection.Tap -> TfbiFlickDirection.TAP
        com.kazumaproject.core.domain.flick.FlickDirection.Left -> TfbiFlickDirection.LEFT
        com.kazumaproject.core.domain.flick.FlickDirection.Top -> TfbiFlickDirection.UP
        com.kazumaproject.core.domain.flick.FlickDirection.Right -> TfbiFlickDirection.RIGHT
        com.kazumaproject.core.domain.flick.FlickDirection.Bottom -> TfbiFlickDirection.DOWN
    }
}

internal fun resolveCenterGuideFingerPosition(
    keyWidth: Int,
    keyHeight: Int,
    deltaX: Float,
    deltaY: Float
): TfbiGuideFingerPosition? {
    if (keyWidth <= 0 || keyHeight <= 0) return null

    val x = keyWidth / 2f + deltaX
    val y = keyHeight / 2f + deltaY
    return TfbiGuideFingerPosition(
        x = (x / keyWidth.toFloat()).coerceIn(0f, 1f),
        y = (y / keyHeight.toFloat()).coerceIn(0f, 1f)
    )
}
