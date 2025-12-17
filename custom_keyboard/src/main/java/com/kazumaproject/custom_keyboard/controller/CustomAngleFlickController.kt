package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.PopupWindow
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.kazumaproject.custom_keyboard.data.ShapeType
import com.kazumaproject.custom_keyboard.view.CustomAngleFlickPopupView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.sqrt

// 変更: flickSensitivity をコンストラクタで受け取る
class CustomAngleFlickController(
    context: Context,
    private val flickSensitivity: Int
) {

    interface FlickListener {
        fun onFlick(direction: FlickDirection, character: String)
        fun onStateChanged(view: View, newMap: Map<FlickDirection, String>)
        fun onFlickDirectionChanged(newDirection: FlickDirection)
    }

    var listener: FlickListener? = null

    private val popupView = CustomAngleFlickPopupView(context)
    private val popupWindow = PopupWindow(
        popupView,
        popupView.preferredWidth,
        popupView.preferredHeight,
        false
    ).apply {
        isOutsideTouchable = false
        isTouchable = false
    }

    private var anchorView: View? = null
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    // 以前の flickThreshold 変数は削除し、コンストラクタの flickSensitivity を使用します

    private var keyMaps: List<Map<FlickDirection, String>> = emptyList()
    private var currentMapIndex = 0

    private var previousDirection = FlickDirection.TAP

    private val controllerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var longPressJob: Job? = null
    private var isLongPressModeActive = false

    init {
        // 初期化時に感度（中心円の半径）をViewに伝える
        popupView.setFlickSensitivity(flickSensitivity.toFloat())
    }

    // --- Configuration ---

    fun setFlickRanges(ranges: Map<FlickDirection, Pair<Float, Float>>) {
        popupView.setCustomRanges(ranges)
    }

    fun setShapeType(shape: ShapeType) {
        popupView.setShapeType(shape)
    }

    fun setPopupColors(theme: FlickPopupColorTheme) {
        popupView.setColors(theme)
    }

    // 変更: center サイズは flickSensitivity で決まるため、引数から削除、あるいは無視するように変更しても良いですが、
    // ここでは orbit と textSize だけ更新するように修正します。
    fun setPopupViewSize(orbit: Float, textSize: Float) {
        // center は flickSensitivity を使用するため、ここでは orbit と text だけ更新
        popupView.setUiSize(orbit, textSize)
    }

    fun cancel() {
        controllerScope.cancel()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(button: View, maps: List<Map<FlickDirection, String>>) {
        if (maps.isEmpty()) return
        this.keyMaps = maps
        button.setOnTouchListener { v, event ->
            handleTouchEvent(v, event)
        }
    }

    private fun handleTouchEvent(view: View, event: MotionEvent): Boolean {
        val currentDirection = if (event.action == MotionEvent.ACTION_DOWN) {
            FlickDirection.TAP
        } else {
            calculateDirection(event.rawX, event.rawY)
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                anchorView = view
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                previousDirection = FlickDirection.TAP
                isLongPressModeActive = false
                currentMapIndex = 0

                popupView.setFullUIMode(false)

                if (keyMaps.isNotEmpty()) {
                    popupView.setCharacterMap(keyMaps[currentMapIndex])
                }
                popupView.updateFlickDirection(FlickDirection.TAP)

                showPopup()

                longPressJob?.cancel()
                longPressJob = controllerScope.launch {
                    delay(ViewConfiguration.getLongPressTimeout().toLong())
                    isLongPressModeActive = true
                    popupView.setFullUIMode(true)
                    popupView.invalidate()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (currentDirection != previousDirection) {
                    listener?.onFlickDirectionChanged(currentDirection)

                    if (currentDirection != FlickDirection.TAP) {
                        longPressJob?.cancel()
                    }

                    popupView.updateFlickDirection(currentDirection)
                    previousDirection = currentDirection
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressJob?.cancel()

                val finalDirection = calculateDirection(event.rawX, event.rawY)

                if (keyMaps.isNotEmpty()) {
                    val currentMap = keyMaps[currentMapIndex]
                    val character = currentMap[finalDirection] ?: ""

                    if (character.isNotEmpty()) {
                        listener?.onFlick(finalDirection, character)
                    } else if (finalDirection == FlickDirection.TAP) {
                        val tapChar = currentMap[FlickDirection.TAP] ?: ""
                        if (tapChar.isNotEmpty()) {
                            listener?.onFlick(FlickDirection.TAP, tapChar)
                        }
                    }
                }

                hidePopup()
                return true
            }
        }
        return false
    }

    private fun showPopup() {
        val currentAnchor = anchorView ?: return
        // PopupViewのサイズが更新されている可能性があるため再取得
        popupWindow.width = popupView.preferredWidth
        popupWindow.height = popupView.preferredHeight

        val location = IntArray(2)
        currentAnchor.getLocationInWindow(location)

        val x = location[0] + (currentAnchor.width / 2) - (popupWindow.width / 2)
        val y = location[1] + (currentAnchor.height / 2) - (popupWindow.height / 2)

        if (!popupWindow.isShowing) {
            popupWindow.showAtLocation(currentAnchor, Gravity.NO_GRAVITY, x, y)
        } else {
            popupWindow.update(x, y, popupWindow.width, popupWindow.height)
        }
    }

    private fun hidePopup() {
        if (popupWindow.isShowing) popupWindow.dismiss()
        anchorView = null
    }

    private fun calculateDirection(currentX: Float, currentY: Float): FlickDirection {
        val dx = currentX - initialTouchX
        val dy = currentY - initialTouchY
        val distance = sqrt(dx * dx + dy * dy)

        // 変更: コンストラクタで受け取った flickSensitivity を使用
        if (distance < flickSensitivity) return FlickDirection.TAP

        val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360) % 360
        return popupView.getDirectionForAngle(angle)
    }
}
