package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.PopupWindow
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.kazumaproject.custom_keyboard.view.FlickCirclePopupView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.sqrt

// ▼▼▼【追加】ポップアップの表示位置を定義するenum ▼▼▼
enum class PopupPosition {
    CENTER,
    TOP
}

class FlickInputController(context: Context) {

    // ▼▼▼ CHANGE ▼▼▼ Added onStateChanged to the listener interface
    interface FlickListener {
        fun onFlick(direction: FlickDirection, character: String)
        fun onStateChanged(view: View, newMap: Map<FlickDirection, String>)
    }

    var listener: FlickListener? = null

    private val popupView = FlickCirclePopupView(context)
    private val popupWindow = PopupWindow(
        popupView,
        popupView.preferredWidth,
        popupView.preferredHeight,
        false
    ).apply {
        isOutsideTouchable = false
    }

    private var anchorView: View? = null
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var flickThreshold = 80f

    private var keyMaps: List<Map<FlickDirection, String>> = emptyList()
    private var currentMapIndex = 0
    private var previousDirection = FlickDirection.TAP
    private var lastValidFlickDirection = FlickDirection.TAP
    private var isDownModeActive = false

    private var isLongPressModeActive = false
    private val controllerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var longPressJob: Job? = null

    // ▼▼▼【追加】表示位置を保持する変数。デフォルトは中央。 ▼▼▼
    private var popupPosition: PopupPosition = PopupPosition.CENTER

    /**
     * ▼▼▼【追加】ポップアップの表示位置を設定する関数 ▼▼▼
     * @param position 表示したい位置 (CENTER or TOP)
     */
    fun setPopupPosition(position: PopupPosition) {
        this.popupPosition = position
    }

    fun setPopupColors(theme: FlickPopupColorTheme) {
        popupView.setColors(theme)
    }

    fun setPopupViewSize(center: Float, target: Float, orbit: Float, textSize: Float) {
        popupView.setUiSize(center, target, orbit, textSize)
        this.flickThreshold = center
    }

    fun setUpperOrbit(upperOrbit: Float) {
        popupView.setUpperOrbit(upperOrbit)
    }

    fun cancel() {
        controllerScope.cancel()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(button: View, maps: List<Map<FlickDirection, String>>) {
        if (maps.isEmpty()) {
            Log.e("FlickInputController", "Character maps cannot be empty.")
            return
        }
        this.keyMaps = maps
        button.setOnTouchListener { _, event ->
            handleTouchEvent(button, event)
        }
    }

    private fun handleTouchEvent(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                anchorView = view
                initialTouchX = event.rawX
                initialTouchY = event.rawY

                currentMapIndex = 0
                previousDirection = FlickDirection.TAP
                lastValidFlickDirection = FlickDirection.TAP
                isDownModeActive = false
                isLongPressModeActive = false
                popupView.setFullUIMode(false)

                popupView.setCharacterMap(keyMaps[currentMapIndex])
                popupView.updateFlickDirection(FlickDirection.TAP)

                showPopup()

                longPressJob?.cancel()
                longPressJob = controllerScope.launch {
                    delay(ViewConfiguration.getLongPressTimeout().toLong())
                    Log.d("FlickInputController", "Long press detected!")
                    isLongPressModeActive = true
                    popupView.setFullUIMode(true)
                    popupView.invalidate()
                }

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val currentCalculatedDirection = calculateDirection(event.rawX, event.rawY)

                if (currentCalculatedDirection != FlickDirection.TAP) {
                    longPressJob?.cancel()
                }

                if (currentCalculatedDirection != FlickDirection.TAP) {
                    lastValidFlickDirection = currentCalculatedDirection
                }

                if (!isDownModeActive && !isLongPressModeActive && currentCalculatedDirection != FlickDirection.TAP) {
                    isDownModeActive = true
                    popupView.setFullUIMode(true)
                }

                if (currentCalculatedDirection == FlickDirection.DOWN && previousDirection != FlickDirection.DOWN) {
                    currentMapIndex = (currentMapIndex + 1) % keyMaps.size
                    val newMap = keyMaps[currentMapIndex]
                    popupView.setCharacterMap(newMap)
                    listener?.onStateChanged(view, newMap)
                }

                popupView.updateFlickDirection(currentCalculatedDirection)

                previousDirection = currentCalculatedDirection
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressJob?.cancel()

                val finalDirectionToInput = if (isDownModeActive || isLongPressModeActive) {
                    calculateDirection(event.rawX, event.rawY)
                } else {
                    lastValidFlickDirection
                }

                if (finalDirectionToInput != FlickDirection.DOWN) {
                    val currentMap = keyMaps[currentMapIndex]
                    val character = currentMap[finalDirectionToInput] ?: ""
                    Log.d(
                        "FlickInputController",
                        "Final Direction: $finalDirectionToInput, Char: $character"
                    )
                    listener?.onFlick(finalDirectionToInput, character)
                }

                hidePopup()
                return true
            }
        }
        return false
    }

    // ▼▼▼【ロジック更新】表示位置設定に応じて座標を計算するように変更 ▼▼▼
    private fun showPopup() {
        val currentAnchor = anchorView ?: return
        popupWindow.width = popupView.preferredWidth
        popupWindow.height = popupView.preferredHeight

        val location = IntArray(2)
        currentAnchor.getLocationInWindow(location)

        val anchorX = location[0]
        val anchorY = location[1]

        // ポップアップの左上のX座標を計算 (これは常に中央)
        val x = anchorX + (currentAnchor.width / 2) - (popupWindow.width / 2)

        // ポップアップの左上のY座標を、設定に応じて計算
        val y = when (popupPosition) {
            // ボタンの中央にポップアップの中央を合わせる
            PopupPosition.CENTER -> anchorY + (currentAnchor.height / 2) - (popupWindow.height / 2)
            // ボタンの上辺にポップアップの中央を合わせる（結果、少し上部に表示される）
            PopupPosition.TOP -> anchorY - (popupWindow.height / 2)
        }

        if (!popupWindow.isShowing) {
            popupWindow.showAtLocation(currentAnchor, Gravity.NO_GRAVITY, x, y)
        }
    }

    private fun hidePopup() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
        anchorView = null
    }

    private fun calculateDirection(currentX: Float, currentY: Float): FlickDirection {
        val dx = currentX - initialTouchX
        val dy = currentY - initialTouchY
        val distance = sqrt(dx * dx + dy * dy)

        if (distance < flickThreshold) {
            return FlickDirection.TAP
        }

        // Angle is calculated from 0-360, starting East (3 o'clock) and going counter-clockwise
        val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360) % 360

        return when (angle) {
            // DOWN is now a 140° segment (20° to 160°)
            in 20.0..160.0 -> FlickDirection.DOWN
            // The other 5 directions get a 44° segment each (220° / 5)
            in 160.0..204.0 -> FlickDirection.UP_LEFT_FAR
            in 204.0..248.0 -> FlickDirection.UP_LEFT
            in 248.0..292.0 -> FlickDirection.UP
            in 292.0..336.0 -> FlickDirection.UP_RIGHT
            // else covers the remaining segment (336° to 360° and 0° to 20°)
            else -> FlickDirection.UP_RIGHT_FAR
        }
    }
}
