package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.PopupWindow
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.view.DirectionalKeyPopupView
import com.kazumaproject.custom_keyboard.view.FlickGridPopupView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

class PetalFlickInputController(private val context: Context) {

    // ... (Listener interface and properties are unchanged) ...
    interface PetalFlickListener {
        fun onFlick(character: String)
    }

    var listener: PetalFlickListener? = null
    private var characterMap: Map<FlickDirection, String> = emptyMap()
    private val controllerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var longPressJob: Job? = null
    private var isLongPressModeActive = false
    private var anchorView: View? = null
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private val flickThreshold = 65f

    private val directionalPopup: PopupWindow
    private val gridPopup: PopupWindow

    init {
        directionalPopup = PopupWindow(
            DirectionalKeyPopupView(context),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            false
        )
        gridPopup = PopupWindow(
            FlickGridPopupView(context),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        listOf(directionalPopup, gridPopup).forEach {
            it.isClippingEnabled = false
            it.elevation = 8f
        }
    }

    // ... (attach and handleTouchEvent methods are unchanged) ...
    @SuppressLint("ClickableViewAccessibility")
    fun attach(button: View, map: Map<FlickDirection, String>) {
        this.characterMap = map; button.setOnTouchListener { v, event ->
            handleTouchEvent(
                v,
                event
            )
        }
    }

    private fun handleTouchEvent(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                anchorView = view; initialTouchX = event.rawX; initialTouchY =
                    event.rawY; isLongPressModeActive = false
                showDirectionalPopup(FlickDirection.TAP)
                longPressJob = controllerScope.launch {
                    delay(
                        ViewConfiguration.getLongPressTimeout().toLong()
                    ); isLongPressModeActive = true; directionalPopup.dismiss(); showGridPopup()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX;
                val dy = event.rawY - initialTouchY
                if (sqrt(dx * dx + dy * dy) > flickThreshold * 0.5f) {
                    longPressJob?.cancel()
                }
                val direction = calculateDirection(dx, dy)
                if (isLongPressModeActive) {
                    (gridPopup.contentView as? FlickGridPopupView)?.highlightKey(direction)
                } else {
                    showDirectionalPopup(direction)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressJob?.cancel()
                if (event.action == MotionEvent.ACTION_UP) {
                    val dx = event.rawX - initialTouchX;
                    val dy = event.rawY - initialTouchY
                    val finalDirection = calculateDirection(dx, dy)
                    characterMap[finalDirection]?.let { listener?.onFlick(it) }
                }
                dismissAllPopups()
                return true
            }
        }
        return false
    }

    private fun showDirectionalPopup(direction: FlickDirection) {
        val popupView = directionalPopup.contentView as DirectionalKeyPopupView
        popupView.setFlickDirection(direction)
        popupView.text = characterMap[direction] ?: ""

        val currentAnchor = anchorView ?: return
        val location = IntArray(2); currentAnchor.getLocationInWindow(location)
        val anchorX = location[0];
        val anchorY = location[1]
        val keyWidth = currentAnchor.width;
        val keyHeight = currentAnchor.height
        val anchorCenterX = anchorX + keyWidth / 2
        val anchorCenterY = anchorY + keyHeight / 2

        // ▼▼▼ FIX: 動的にポップアップのサイズを計算 ▼▼▼
        val lengthExtension = 1.4f // フリック時の延長率
        val popupWidth: Int
        val popupHeight: Int

        when (direction) {
            FlickDirection.TAP -> {
                popupWidth = keyWidth
                popupHeight = keyHeight
            }

            FlickDirection.UP, FlickDirection.DOWN -> {
                popupWidth = keyWidth
                popupHeight = (keyHeight * lengthExtension).toInt()
            }

            FlickDirection.UP_LEFT_FAR, FlickDirection.UP_RIGHT_FAR -> {
                popupWidth = (keyWidth * lengthExtension).toInt()
                popupHeight = keyHeight
            }

            else -> {
                popupWidth = keyWidth
                popupHeight = keyHeight
            }
        }

        val x: Int
        val y: Int

        when (direction) {
            FlickDirection.TAP -> {
                x = anchorCenterX - popupWidth / 2
                y = anchorCenterY - popupHeight / 2
            }

            FlickDirection.UP -> {
                x = anchorCenterX - popupWidth / 2
                y = anchorCenterY - popupHeight
            }

            FlickDirection.DOWN -> {
                x = anchorCenterX - popupWidth / 2
                y = anchorCenterY
            }

            FlickDirection.UP_LEFT_FAR -> { // Left
                x = anchorCenterX - popupWidth
                y = anchorCenterY - popupHeight / 2
            }

            FlickDirection.UP_RIGHT_FAR -> { // Right
                x = anchorCenterX
                y = anchorCenterY - popupHeight / 2
            }

            else -> {
                x = anchorCenterX - popupWidth / 2
                y = anchorCenterY - popupHeight / 2
            }
        }

        if (!directionalPopup.isShowing) {
            directionalPopup.width = popupWidth
            directionalPopup.height = popupHeight
            directionalPopup.showAtLocation(currentAnchor, Gravity.NO_GRAVITY, x, y)
        } else {
            // ▼▼▼ FIX: 座標とサイズの両方を更新 ▼▼▼
            directionalPopup.update(x, y, popupWidth, popupHeight)
        }
    }

    // ... (The rest of the file is unchanged) ...
    private fun showGridPopup() {
        val popupView = gridPopup.contentView as FlickGridPopupView
        popupView.setCharacters(characterMap); popupView.highlightKey(FlickDirection.TAP)
        val currentAnchor = anchorView ?: return
        val location = IntArray(2); currentAnchor.getLocationInWindow(location)
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val x = location[0] + currentAnchor.width / 2 - popupView.measuredWidth / 2
        val y = location[1] + currentAnchor.height / 2 - popupView.measuredHeight / 2
        gridPopup.showAtLocation(currentAnchor, Gravity.NO_GRAVITY, x, y)
    }

    private fun dismissAllPopups() {
        if (directionalPopup.isShowing) directionalPopup.dismiss(); if (gridPopup.isShowing) gridPopup.dismiss()
    }

    private fun calculateDirection(dx: Float, dy: Float): FlickDirection {
        val distance =
            sqrt(dx * dx + dy * dy); if (distance < flickThreshold) return FlickDirection.TAP
        return when {
            abs(dx) > abs(dy) * 1.5 -> if (dx > 0) FlickDirection.UP_RIGHT_FAR else FlickDirection.UP_LEFT_FAR
            abs(dy) > abs(dx) * 1.5 -> if (dy > 0) FlickDirection.DOWN else FlickDirection.UP
            else -> FlickDirection.TAP
        }
    }

    fun cancel() {
        dismissAllPopups(); controllerScope.cancel()
    }
}
