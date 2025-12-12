package com.kazumaproject.custom_keyboard.view

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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
        fun onFlick(first: TfbiFlickDirection, second: TfbiFlickDirection)
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

    var listener: TfbiListener? = null
    private var characterMapProvider: ((TfbiFlickDirection, TfbiFlickDirection) -> String)? = null
    private var attachedView: View? = null

    private var popupView: TfbiFlickPopupView? = null
    private var popupWindow: PopupWindow? = null

    private lateinit var gestureDetector: GestureDetector

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

    fun attach(
        view: View,
        provider: (TfbiFlickDirection, TfbiFlickDirection) -> String
    ) {
        this.attachedView = view
        this.characterMapProvider = provider

        gestureDetector =
            GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    if (flickState == FlickState.NEUTRAL) {
                        popupWindow?.dismiss()
                        showPopup(view, TfbiFlickDirection.TAP, true)
                    }
                }
            })

        view.setOnTouchListener { _, event -> handleTouchEvent(event) }
    }

    fun cancel() {
        resetState()
        attachedView?.setOnTouchListener(null)
        attachedView = null
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
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
        initialTouchX = event.x
        initialTouchY = event.y

        showPopup(view, TfbiFlickDirection.TAP, false)
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
            finalSecondDirection = TfbiFlickDirection.TAP
        }

        listener?.onFlick(firstFlickDirection, finalSecondDirection)
        resetState()
    }

    private fun showPopup(
        anchorView: View,
        baseDirection: TfbiFlickDirection,
        showPetals: Boolean
    ) {
        if (popupWindow?.isShowing == true && !showPetals) return

        val tapCharacter = characterMapProvider?.invoke(baseDirection, TfbiFlickDirection.TAP) ?: ""

        val petalChars = if (showPetals) {
            val enabledDirections = getEnabledFirstFlickDirections()
            enabledDirections.associateWith { direction ->
                characterMapProvider?.invoke(direction, direction) ?: ""
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
        if (!anchorView.isAttachedToWindow) return
        val location = IntArray(2).also { anchorView.getLocationInWindow(it) }
        val offsetX = location[0] - anchorView.width
        val offsetY = location[1] - anchorView.height
        popupWindow?.showAtLocation(anchorView, Gravity.NO_GRAVITY, offsetX, offsetY)
    }

    private fun setupSecondStageUI(firstDirection: TfbiFlickDirection) {
        val tapCharacter =
            characterMapProvider?.invoke(firstDirection, TfbiFlickDirection.TAP) ?: ""
        val enabledDirections = getEnabledSecondFlickDirections(firstDirection)
        val petalChars = enabledDirections.associateWith {
            characterMapProvider?.invoke(firstDirection, it) ?: ""
        }
        popupView?.setCharacters(tapCharacter, petalChars)
    }


    private fun resetState() {
        popupWindow?.dismiss()
        popupWindow = null
        popupView = null
        flickState = FlickState.NEUTRAL
        firstFlickDirection = TfbiFlickDirection.TAP
        currentSecondFlickDirection = TfbiFlickDirection.TAP
    }

    private fun getEnabledFirstFlickDirections(): Set<TfbiFlickDirection> {
        val provider = characterMapProvider ?: return emptySet()
        return TfbiFlickDirection.entries.filter {
            it != TfbiFlickDirection.TAP && provider(it, TfbiFlickDirection.TAP).isNotEmpty()
        }.toSet()
    }

    private fun getEnabledSecondFlickDirections(baseDirection: TfbiFlickDirection): Set<TfbiFlickDirection> {
        val provider = characterMapProvider ?: return emptySet()
        return TfbiFlickDirection.entries.filter {
            it != TfbiFlickDirection.TAP && provider(baseDirection, it).isNotEmpty()
        }.toSet()
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
}
