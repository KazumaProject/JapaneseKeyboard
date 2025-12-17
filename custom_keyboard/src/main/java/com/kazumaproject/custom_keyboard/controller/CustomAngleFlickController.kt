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

// flickSensitivity はあくまで「距離の閾値」として保持
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

    private var keyMaps: List<Map<FlickDirection, String>> = emptyList()
    private var currentMapIndex = 0

    private var previousDirection = FlickDirection.TAP

    private val controllerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var longPressJob: Job? = null
    private var isLongPressModeActive = false

    init {
        // デフォルトの見た目サイズを設定（必要に応じて setPopupViewSize で上書きしてください）
        popupView.setUiSize(160f, 60f, 40f)
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

    // 見た目用に centerRadius (px) を受け取れるようにする
    // これにより、flickSensitivity(判定) と centerRadius(見た目) を別々に管理可能
    fun setPopupViewSize(orbit: Float, centerRadius: Float, textSize: Float) {
        popupView.setUiSize(orbit, centerRadius, textSize)
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
        // ACTION_DOWNの時はTAP扱い、それ以外は座標から計算
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
                currentMapIndex = 0 // タッチ開始時は常に最初のマップにリセット

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
                }

                // ★変更: UP_RIGHT方向フリック検知時のマップ切り替えロジック
                if (currentDirection == FlickDirection.UP_RIGHT) {
                    // フルUIモードにして次の候補を見やすくする
                    popupView.setFullUIMode(true)

                    // 前回がUP_RIGHTでなく、今回UP_RIGHTになった瞬間のみ切り替える
                    if (previousDirection != FlickDirection.UP_RIGHT) {
                        if (keyMaps.isNotEmpty()) {
                            currentMapIndex = (currentMapIndex + 1) % keyMaps.size
                            val newMap = keyMaps[currentMapIndex]
                            popupView.setCharacterMap(newMap)
                            listener?.onStateChanged(view, newMap)
                        }
                    }
                }

                if (currentDirection != previousDirection) {
                    popupView.updateFlickDirection(currentDirection)
                    previousDirection = currentDirection
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressJob?.cancel()

                val finalDirection = calculateDirection(event.rawX, event.rawY)

                // ★変更: UP_RIGHTフリックはマップ切り替え動作のため、文字入力を行わない
                if (finalDirection != FlickDirection.UP_RIGHT) {
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
                }

                hidePopup()
                return true
            }
        }
        return false
    }

    private fun showPopup() {
        val currentAnchor = anchorView ?: return
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

        // 判定はコンストラクタで渡された flickSensitivity を使用
        if (distance < flickSensitivity) return FlickDirection.TAP

        val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360) % 360
        return popupView.getDirectionForAngle(angle)
    }
}
