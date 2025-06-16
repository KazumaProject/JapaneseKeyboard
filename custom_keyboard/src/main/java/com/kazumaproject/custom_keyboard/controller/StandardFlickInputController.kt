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

class StandardFlickInputController(private val context: Context) {

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

    /**
     * ▼▼▼ FIX: Method signature changed to accept FlickPopupColorTheme for consistency ▼▼▼
     */
    fun setPopupColors(theme: FlickPopupColorTheme) {
        // The standard popup is simple, so we map theme colors to its components.
        // Using highlight color for the popup background seems appropriate.
        this.popupBackgroundColor = theme.segmentHighlightGradientStartColor
        this.popupTextColor = theme.textColor
        // Using separator color for the popup's stroke.
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

    private fun showPopup(direction: FlickDirection) {
        val currentAnchor = anchorView ?: return
        val text = characterMap[direction]

        popupView.setColors(popupBackgroundColor, popupTextColor, popupStrokeColor)
        popupView.updateText(text)

        if (!popupWindow.isShowing) {
            val location = IntArray(2)
            currentAnchor.getLocationInWindow(location)
            val x = location[0] + (currentAnchor.width / 2) - (popupView.viewSize / 2)
            val y = location[1] - popupView.viewSize - 10 // Position above the key
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
