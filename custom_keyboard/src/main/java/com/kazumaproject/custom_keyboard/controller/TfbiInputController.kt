package com.kazumaproject.custom_keyboard.view

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.PopupWindow
import androidx.core.graphics.drawable.toDrawable
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

enum class TfbiFlickDirection {
    UP, DOWN, LEFT, RIGHT,
    UP_RIGHT, DOWN_RIGHT, DOWN_LEFT, UP_LEFT,
    TAP
}

@SuppressLint("ClickableViewAccessibility")
class TfbiInputController(
    private val context: Context,
    private val flickSensitivity: Float
) {

    interface TfbiListener {
        fun onPress(first: TfbiFlickDirection, second: TfbiFlickDirection)
        fun onFlick(first: TfbiFlickDirection, second: TfbiFlickDirection)
        fun onLongPressFlick(first: TfbiFlickDirection, second: TfbiFlickDirection): Boolean = false
    }

    private enum class FlickState { NEUTRAL, FIRST_FLICK_DETERMINED }

    companion object {
        private const val MAX_ANGLE_DIFFERENCE = 70.0
        private const val CANCEL_THRESHOLD = 70f
    }

    private var flickState: FlickState = FlickState.NEUTRAL
    private var firstFlickDirection: TfbiFlickDirection = TfbiFlickDirection.TAP
    private var currentSecondFlickDirection: TfbiFlickDirection = TfbiFlickDirection.TAP
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var intermediateTouchX = 0f
    private var intermediateTouchY = 0f
    private var isLongPressModeActive = false

    var listener: TfbiListener? = null
    private var characterMapProvider: ((TfbiFlickDirection, TfbiFlickDirection) -> String)? = null
    private var longPressCharacterMapProvider: ((TfbiFlickDirection, TfbiFlickDirection) -> String)? = null
    private var attachedView: View? = null

    private var popupView: TfbiFlickPopupView? = null
    private var popupWindow: PopupWindow? = null

    private var popupWindowAnchorProvider: (() -> View?)? = null

    private var longPressTimeout: Long = ViewConfiguration.getLongPressTimeout().toLong()
    private var isTouchActive = false
    private val longPressRunnable = Runnable {
        val view = attachedView ?: return@Runnable
        if (!isTouchActive || flickState != FlickState.NEUTRAL || isLongPressModeActive) {
            return@Runnable
        }

        isLongPressModeActive = true
        popupWindow?.dismiss()
        showPopup(view, TfbiFlickDirection.TAP, true)
    }

    // ▼▼▼ 追加: 色設定保持用の変数 ▼▼▼
    private var popupBackgroundColor: Int? = null
    private var popupHighlightedColor: Int? = null
    private var popupTextColor: Int? = null

    // ▼▼▼ 追加: 色を設定するメソッド ▼▼▼
    fun setPopupColors(backgroundColor: Int, highlightedColor: Int, textColor: Int) {
        this.popupBackgroundColor = backgroundColor
        this.popupHighlightedColor = highlightedColor
        this.popupTextColor = textColor
    }

    fun setLongPressTimeout(timeoutMillis: Long) {
        longPressTimeout = timeoutMillis.coerceIn(100L, 2000L)
    }

    fun setPopupWindowAnchorProvider(provider: (() -> View?)?) {
        popupWindowAnchorProvider = provider
    }

    fun attach(
        view: View,
        provider: (TfbiFlickDirection, TfbiFlickDirection) -> String,
        longPressProvider: (TfbiFlickDirection, TfbiFlickDirection) -> String = { _, _ -> "" }
    ) {
        this.attachedView = view
        this.characterMapProvider = provider
        this.longPressCharacterMapProvider = longPressProvider

        view.setOnTouchListener { _, event -> handleTouchEvent(event) }
    }

    fun cancel() {
        clearLongPressCallback(attachedView)
        isTouchActive = false
        resetState()
        attachedView?.setOnTouchListener(null)
        attachedView = null
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        // Log.d("TfbInput", "handleTouchEvent: ${MotionEvent.actionToString(event.action)}")

        val view = attachedView ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                handleTouchDown(event, view)
            }

            MotionEvent.ACTION_MOVE -> {
                handleTouchMove(event, view)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handleTouchUp(event)
            }
        }
        return true
    }

    private fun handleTouchDown(event: MotionEvent, view: View) {
        resetState()
        flickState = FlickState.NEUTRAL
        isLongPressModeActive = false
        isTouchActive = true
        initialTouchX = event.x
        initialTouchY = event.y
        listener?.onPress(TfbiFlickDirection.TAP, TfbiFlickDirection.TAP)

        showPopup(view, TfbiFlickDirection.TAP, false)
        view.removeCallbacks(longPressRunnable)
        view.postDelayed(longPressRunnable, longPressTimeout)
    }

    private fun handleTouchMove(event: MotionEvent, view: View) {
        if (flickState == FlickState.NEUTRAL) {
            val dx = event.x - initialTouchX
            val dy = event.y - initialTouchY
            val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

            if (distance >= flickSensitivity) {
                val enabledFirstDirections = getEnabledFirstFlickDirections()
                val determinedDirection =
                    calculateDirection(dx, dy, flickSensitivity, enabledFirstDirections)
                if (determinedDirection == TfbiFlickDirection.TAP) return

                firstFlickDirection = determinedDirection
                intermediateTouchX = event.x
                intermediateTouchY = event.y
                flickState = FlickState.FIRST_FLICK_DETERMINED
                clearLongPressCallback(view)

                setupSecondStageUI(firstFlickDirection)
                popupView?.highlightDirection(determinedDirection)
                currentSecondFlickDirection = determinedDirection
            }
        } else {
            val distanceFromInitial = hypot(
                (event.x - initialTouchX).toDouble(),
                (event.y - initialTouchY).toDouble()
            ).toFloat()
            if (distanceFromInitial < CANCEL_THRESHOLD) {
                if (isLongPressModeActive) {
                    return
                }
                resetState()
                showPopup(view, TfbiFlickDirection.TAP, false)
                return
            }
            val dx = event.x - intermediateTouchX
            val dy = event.y - intermediateTouchY
            val enabledSecondDirections = getEnabledSecondFlickDirections(firstFlickDirection)

            var highlightTargetDirection =
                calculateDirection(dx, dy, flickSensitivity, enabledSecondDirections)

            if (highlightTargetDirection == TfbiFlickDirection.TAP) {
                highlightTargetDirection = firstFlickDirection
            }

            if (highlightTargetDirection != currentSecondFlickDirection) {
                popupView?.highlightDirection(highlightTargetDirection)
                currentSecondFlickDirection = highlightTargetDirection
            }
        }
    }

    private fun handleTouchUp(event: MotionEvent) {
        isTouchActive = false
        clearLongPressCallback(attachedView)

        var finalSecondDirection: TfbiFlickDirection
        if (flickState == FlickState.FIRST_FLICK_DETERMINED) {
            val dx = event.x - intermediateTouchX
            val dy = event.y - intermediateTouchY
            val enabledSecondDirections = getEnabledSecondFlickDirections(firstFlickDirection)
            finalSecondDirection =
                calculateDirection(dx, dy, flickSensitivity, enabledSecondDirections)

            if (finalSecondDirection == TfbiFlickDirection.TAP && currentSecondFlickDirection != TfbiFlickDirection.TAP) {
                finalSecondDirection = currentSecondFlickDirection
            }
        } else {
            val dx = event.x - initialTouchX
            val dy = event.y - initialTouchY
            val enabledFirstDirections = getEnabledFirstFlickDirections()
            firstFlickDirection =
                calculateDirection(dx, dy, flickSensitivity, enabledFirstDirections)
            finalSecondDirection = if (firstFlickDirection == TfbiFlickDirection.TAP) {
                TfbiFlickDirection.TAP
            } else {
                firstFlickDirection
            }
        }

        if (isLongPressModeActive) {
            val consumed = listener?.onLongPressFlick(firstFlickDirection, finalSecondDirection) == true
            if (!consumed) {
                listener?.onFlick(firstFlickDirection, finalSecondDirection)
            }
        } else {
            listener?.onFlick(firstFlickDirection, finalSecondDirection)
        }
        resetState()
    }

    private fun showPopup(
        anchorView: View,
        baseDirection: TfbiFlickDirection,
        showPetals: Boolean
    ) {
        if (popupWindow?.isShowing == true && !showPetals) return

        val tapCharacter = characterFor(baseDirection, TfbiFlickDirection.TAP)

        val petalChars = if (showPetals) {
            val enabledDirections = getEnabledFirstFlickDirections()
            enabledDirections.associateWith { direction ->
                characterFor(direction, direction)
            }
        } else {
            emptyMap()
        }

        popupView = TfbiFlickPopupView(context).apply {
            // ▼▼▼ 修正: 色設定があれば適用 ▼▼▼
            if (popupBackgroundColor != null && popupHighlightedColor != null && popupTextColor != null) {
                setColors(popupBackgroundColor!!, popupHighlightedColor!!, popupTextColor!!)
            }

            setCharacters(tapCharacter, petalChars)
            highlightDirection(TfbiFlickDirection.TAP)
        }
        val popupWidth = anchorView.width * 3
        val popupHeight = anchorView.height * 3
        popupWindow = PopupWindow(popupView, popupWidth, popupHeight, false).apply {
            isTouchable = false
            isFocusable = false
            setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
            isClippingEnabled = false
        }
        val windowAnchor = popupWindowAnchorProvider?.invoke() ?: anchorView
        if (!isAnchorReady(anchorView, windowAnchor)) return
        val location = IntArray(2).also { anchorView.getLocationInWindow(it) }
        val offsetX = location[0] - anchorView.width
        val offsetY = location[1] - anchorView.height
        runCatching {
            popupWindow?.showAtLocation(windowAnchor, Gravity.NO_GRAVITY, offsetX, offsetY)
        }
    }

    private fun setupSecondStageUI(firstDirection: TfbiFlickDirection) {
        val tapCharacter = characterFor(firstDirection, TfbiFlickDirection.TAP)
        val enabledDirections = getEnabledSecondFlickDirections(firstDirection)
        val petalChars = enabledDirections.associateWith {
            characterFor(firstDirection, it)
        }
        popupView?.setCharacters(tapCharacter, petalChars)
    }

    private fun characterFor(first: TfbiFlickDirection, second: TfbiFlickDirection): String {
        val normal = characterMapProvider?.invoke(first, second).orEmpty()
        val longPress = longPressCharacterMapProvider?.invoke(first, second).orEmpty()
        return if (isLongPressModeActive && longPress.isNotEmpty()) longPress else normal
    }

    private fun resetState() {
        clearLongPressCallback(attachedView)
        popupWindow?.dismiss()
        popupWindow = null
        popupView = null
        flickState = FlickState.NEUTRAL
        firstFlickDirection = TfbiFlickDirection.TAP
        currentSecondFlickDirection = TfbiFlickDirection.TAP
        isLongPressModeActive = false
    }

    private fun clearLongPressCallback(view: View?) {
        view?.removeCallbacks(longPressRunnable)
    }

    private fun getEnabledFirstFlickDirections(): Set<TfbiFlickDirection> {
        val provider = characterMapProvider ?: return emptySet()
        return TfbiFlickDirection.entries.filter {
            it != TfbiFlickDirection.TAP && (
                    provider(it, TfbiFlickDirection.TAP).isNotEmpty() ||
                            (isLongPressModeActive && hasLongPressOutputForFirstDirection(it))
                    )
        }.toSet()
    }

    private fun getEnabledSecondFlickDirections(baseDirection: TfbiFlickDirection): Set<TfbiFlickDirection> {
        val provider = characterMapProvider ?: return emptySet()
        return TfbiFlickDirection.entries.filter {
            it != TfbiFlickDirection.TAP && (
                    provider(baseDirection, it).isNotEmpty() ||
                            (isLongPressModeActive && longPressCharacterMapProvider?.invoke(baseDirection, it).orEmpty().isNotEmpty())
                    )
        }.toSet()
    }

    private fun hasLongPressOutputForFirstDirection(firstDirection: TfbiFlickDirection): Boolean {
        val provider = longPressCharacterMapProvider ?: return false
        return TfbiFlickDirection.entries.any { secondDirection ->
            provider(firstDirection, secondDirection).isNotEmpty()
        }
    }

    private fun calculateDirection(
        dx: Float,
        dy: Float,
        threshold: Float,
        enabledDirections: Set<TfbiFlickDirection>
    ): TfbiFlickDirection {
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (distance < threshold) {
            return TfbiFlickDirection.TAP
        }
        if (enabledDirections.isEmpty()) {
            return TfbiFlickDirection.TAP
        }

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

        val closestDirectionData = enabledDirections.map { direction ->
            val targetAngle =
                if (direction == TfbiFlickDirection.LEFT && angle < 0) -180.0 else centerAngles[direction]!!

            var diff = abs(angle - targetAngle)
            if (diff > 180) {
                diff = 360 - diff
            }
            Pair(direction, diff)
        }.minByOrNull { it.second }

        if (closestDirectionData == null || closestDirectionData.second > MAX_ANGLE_DIFFERENCE) {
            return TfbiFlickDirection.TAP
        }

        return closestDirectionData.first
    }

    private fun isAnchorReady(keyAnchor: View, windowAnchor: View?): Boolean {
        if (!keyAnchor.isAttachedToWindow) return false
        if (windowAnchor == null) return false
        if (!windowAnchor.isAttachedToWindow) return false
        return windowAnchor.windowToken != null
    }
}
