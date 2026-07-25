package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import com.kazumaproject.core.domain.flick.FlickGestureMath
import com.kazumaproject.core.domain.flick.GestureSessionConfig
import com.kazumaproject.core.domain.flick.GestureSessionConfigSource

/**
 * Shared tap/long-press recognizer for non-flick keys.
 *
 * Android's View long-click timeout cannot follow the app preference. This controller uses the
 * same per-gesture configuration snapshot as every flick recognizer and keeps View rendering
 * independent from preference changes.
 */
class TapLongPressInputController(
    private val gestureConfigSource: GestureSessionConfigSource
) {

    interface Listener {
        fun onPress()
        fun onTap()
        fun onLongPress()
        fun onUpAfterLongPress()
        fun onLongPressCanceled()
    }

    private var attachedView: View? = null
    private var listener: Listener? = null
    private var activeGestureConfig: GestureSessionConfig? = null
    private var initialRawX = 0f
    private var initialRawY = 0f
    private var isTouchActive = false
    private var isLongPressTriggered = false
    private var isTapCanceled = false

    private val longPressRunnable = Runnable {
        if (!isTouchActive || isTapCanceled || isLongPressTriggered) return@Runnable
        isLongPressTriggered = true
        listener?.onLongPress()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(view: View, listener: Listener) {
        cancel()
        attachedView = view
        this.listener = listener
        view.setOnClickListener {
            this.listener?.onTap()
        }
        view.setOnTouchListener { _, event -> handleTouchEvent(event) }
    }

    fun cancel() {
        val view = attachedView
        view?.removeCallbacks(longPressRunnable)
        if (isLongPressTriggered) {
            listener?.onLongPressCanceled()
        }
        view?.isPressed = false
        view?.setOnTouchListener(null)
        view?.setOnClickListener(null)
        attachedView = null
        listener = null
        clearGesture()
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        val view = attachedView ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                clearGesture()
                val config = gestureConfigSource.snapshot()
                activeGestureConfig = config
                initialRawX = event.rawX
                initialRawY = event.rawY
                isTouchActive = true
                view.isPressed = true
                view.drawableHotspotChanged(event.x, event.y)
                listener?.onPress()
                view.postDelayed(longPressRunnable, config.longPressTimeoutMillis)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                view.drawableHotspotChanged(event.x, event.y)
                val config = activeGestureConfig ?: return true
                val dx = event.rawX - initialRawX
                val dy = event.rawY - initialRawY
                if (
                    FlickGestureMath.isThresholdCrossed(
                        deltaX = dx,
                        deltaY = dy,
                        thresholdPx = config.flickThresholdPx
                    )
                ) {
                    view.removeCallbacks(longPressRunnable)
                }
                if (
                    event.x < -config.flickThresholdPx ||
                    event.x > view.width + config.flickThresholdPx ||
                    event.y < -config.flickThresholdPx ||
                    event.y > view.height + config.flickThresholdPx
                ) {
                    isTapCanceled = true
                    view.removeCallbacks(longPressRunnable)
                    view.isPressed = false
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                view.removeCallbacks(longPressRunnable)
                view.isPressed = false
                isTouchActive = false
                when {
                    isLongPressTriggered -> listener?.onUpAfterLongPress()
                    !isTapCanceled -> view.performClick()
                }
                clearGesture()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                view.removeCallbacks(longPressRunnable)
                view.isPressed = false
                if (isLongPressTriggered) {
                    listener?.onLongPressCanceled()
                }
                clearGesture()
                return true
            }
        }
        return false
    }

    private fun clearGesture() {
        activeGestureConfig = null
        isTouchActive = false
        isLongPressTriggered = false
        isTapCanceled = false
    }
}
