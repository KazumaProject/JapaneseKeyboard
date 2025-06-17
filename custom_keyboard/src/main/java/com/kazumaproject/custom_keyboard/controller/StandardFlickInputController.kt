package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.PopupWindow
import androidx.core.graphics.drawable.toDrawable
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.kazumaproject.custom_keyboard.layout.SegmentedBackgroundDrawable
import com.kazumaproject.custom_keyboard.view.StandardFlickPopupView
import kotlin.math.sqrt

class StandardFlickInputController(context: Context) {

    interface StandardFlickListener {
        fun onFlick(character: String)
    }

    var listener: StandardFlickListener? = null
    private var characterMap: Map<FlickDirection, String> = emptyMap()
    private var anchorView: View? = null
    private var segmentedDrawable: SegmentedBackgroundDrawable? = null

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private val flickThreshold = 65f

    private val popupWindow: PopupWindow
    private val popupView = StandardFlickPopupView(context)

    private var popupBackgroundColor: Int = Color.WHITE
    private var popupTextColor: Int = Color.BLACK
    private var popupStrokeColor: Int = Color.LTGRAY

    init {
        popupWindow = PopupWindow(
            popupView,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            isClippingEnabled = false
            elevation = 8f
            animationStyle = 0
            enterTransition = null
            exitTransition = null
        }
    }

    fun setPopupColors(theme: FlickPopupColorTheme) {
        this.popupBackgroundColor = theme.segmentHighlightGradientStartColor
        this.popupTextColor = theme.textColor
        this.popupStrokeColor = theme.separatorColor
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(
        button: View,
        map: Map<FlickDirection, String>,
        drawable: SegmentedBackgroundDrawable
    ) {
        val completeMap = mutableMapOf<FlickDirection, String>()
        completeMap[FlickDirection.TAP] = map[FlickDirection.TAP] ?: ""
        completeMap[FlickDirection.UP] = map[FlickDirection.UP] ?: ""
        completeMap[FlickDirection.DOWN] = map[FlickDirection.DOWN] ?: ""
        completeMap[FlickDirection.UP_LEFT_FAR] = map[FlickDirection.UP_LEFT_FAR]
            ?: map.entries.find { it.key.name.contains("LEFT") }?.value ?: ""

        // ▼▼▼ BUG FIX ▼▼▼
        // The original code had a copy-paste error here, looking for UP_LEFT_FAR again.
        // This now correctly looks for the character associated with the RIGHT direction.
        completeMap[FlickDirection.UP_RIGHT_FAR] = map[FlickDirection.UP_RIGHT_FAR]
            ?: map.entries.find { it.key.name.contains("RIGHT") }?.value ?: ""

        this.characterMap = completeMap
        this.segmentedDrawable = drawable
        button.setOnTouchListener { v, event ->
            handleTouchEvent(v, event)
        }
    }

    private fun handleTouchEvent(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                anchorView = view
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                segmentedDrawable?.highlightDirection = FlickDirection.TAP
                showPopup(FlickDirection.TAP)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                val direction = calculateDirection(dx, dy)
                segmentedDrawable?.highlightDirection = direction
                showPopup(direction)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                segmentedDrawable?.highlightDirection = null
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                val finalDirection = calculateDirection(dx, dy)
                characterMap[finalDirection]?.let {
                    if (it.isNotEmpty()) {
                        listener?.onFlick(it)
                    }
                }
                dismissPopup()
                return true
            }
        }
        return false
    }

    /**
     * ▼▼▼ MODIFIED METHOD ▼▼▼
     * This method now shows the multi-character popup for TAP
     * and the standard single-character popup for all other directions.
     */
    private fun showPopup(direction: FlickDirection) {
        val currentAnchor = anchorView ?: return

        // Update popup colors
        popupView.setColors(popupBackgroundColor, popupTextColor, popupStrokeColor)

        // ▼▼▼ NEW LOGIC HERE ▼▼▼
        if (direction == FlickDirection.TAP) {
            // For TAP, call the new method to show all flick characters
            popupView.updateMultiCharText(characterMap)
        } else {
            // For any other direction, call the original updateText method
            val text = characterMap[direction]
            popupView.updateText(text)
        }
        // ▲▲▲ END OF NEW LOGIC ▲▲▲


        // Y-coordinate offset values
        val baseOffsetY = 10 // Basic margin to show above the key
        val flickUpAdditionalOffset = 80 // Additional offset for UP flick

        // Calculate popup position
        val location = IntArray(2)
        currentAnchor.getLocationInWindow(location)
        val x = location[0] + (currentAnchor.width / 2) - (popupView.viewSize / 2)
        var y = location[1] - popupView.viewSize - baseOffsetY // Default Y position

        // If direction is UP, adjust Y higher (smaller value)
        if (direction == FlickDirection.UP) {
            y -= flickUpAdditionalOffset
        }

        if (popupWindow.isShowing) {
            // If already showing, update position
            popupWindow.update(x, y, -1, -1)
        } else {
            // If not showing, show at the specified location
            popupWindow.showAtLocation(currentAnchor, Gravity.NO_GRAVITY, x, y)
        }
    }

    private fun dismissPopup() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    private fun calculateDirection(dx: Float, dy: Float): FlickDirection {
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < flickThreshold) {
            return FlickDirection.TAP
        }

        val angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble()))

        return when {
            angle > -45 && angle <= 45 -> FlickDirection.UP_RIGHT_FAR
            angle > 45 && angle <= 135 -> FlickDirection.DOWN
            angle < -45 && angle >= -135 -> FlickDirection.UP
            else -> FlickDirection.UP_LEFT_FAR
        }
    }

    fun cancel() {
        dismissPopup()
    }
}
