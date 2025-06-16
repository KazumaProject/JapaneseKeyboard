package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.layout.SegmentedBackgroundDrawable
import com.kazumaproject.custom_keyboard.view.StandardFlickPopupView
import kotlin.math.abs
import kotlin.math.sqrt

class StandardFlickInputController(context: Context) {

    interface StandardFlickListener {
        fun onFlick(character: String)
    }

    var listener: StandardFlickListener? = null

    private val popupView = StandardFlickPopupView(context)
    private val popupWindow =
        PopupWindow(popupView, popupView.viewSize, popupView.viewSize, false).apply {
            isOutsideTouchable = false
            elevation = 8f
            isClippingEnabled = false
        }
    private var segmentedDrawable: SegmentedBackgroundDrawable? = null

    private var anchorView: View? = null
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private val flickThreshold = 65f
    private var characterMap: Map<FlickDirection, String> = emptyMap()
    private var lastDirection: FlickDirection? = null

    fun cancel() {
        hidePopup()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(
        button: View,
        map: Map<FlickDirection, String>,
        drawable: SegmentedBackgroundDrawable
    ) {
        if (map.isEmpty()) return
        this.characterMap = map
        this.segmentedDrawable = drawable
        // The listener passes both the view 'v' and the 'event'
        button.setOnTouchListener { v, event ->
            handleTouchEvent(v, event)
        }
    }

    // ▼▼▼ FIX: Method signature now correctly accepts the View ▼▼▼
    private fun handleTouchEvent(view: View, event: MotionEvent): Boolean {
        // ▼▼▼ FIX: Assign anchorView from the method parameter, not event ▼▼▼
        anchorView = view

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                lastDirection = null

                segmentedDrawable?.highlightedDirection = FlickDirection.TAP

                val initialContent = characterMap[FlickDirection.TAP]
                popupView.updateText(initialContent)
                showPopup()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                val direction = calculateDirection(dx, dy)

                if (direction != lastDirection) {
                    segmentedDrawable?.highlightedDirection = direction
                    val content = characterMap[direction]
                    popupView.updateText(content)
                    lastDirection = direction
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.action == MotionEvent.ACTION_UP) {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    val finalDirection = calculateDirection(dx, dy)
                    characterMap[finalDirection]?.let { content ->
                        val primaryChar = content.split('\n').firstOrNull() ?: ""
                        if (primaryChar.isNotEmpty()) {
                            listener?.onFlick(primaryChar)
                        }
                    }
                }
                cleanup()
                return true
            }
        }
        return false
    }

    private fun cleanup() {
        hidePopup()
        // Reset the highlight on the drawable itself
        segmentedDrawable?.highlightedDirection = null
        anchorView = null
    }

    private fun showPopup() {
        val currentAnchor = anchorView ?: return
        val location = IntArray(2)
        currentAnchor.getLocationInWindow(location)
        val margin = (currentAnchor.height * 0.2).toInt()
        val x = location[0] + (currentAnchor.width / 2) - (popupWindow.width / 2)
        val y = location[1] - popupWindow.height - margin
        if (!popupWindow.isShowing) {
            popupWindow.showAtLocation(currentAnchor, Gravity.NO_GRAVITY, x, y)
        }
    }

    private fun hidePopup() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    private fun calculateDirection(dx: Float, dy: Float): FlickDirection {
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < flickThreshold) {
            return FlickDirection.TAP
        }
        return when {
            abs(dx) > abs(dy) -> if (dx > 0) FlickDirection.UP_RIGHT_FAR else FlickDirection.UP_LEFT_FAR
            else -> if (dy > 0) FlickDirection.DOWN else FlickDirection.UP
        }
    }
}
