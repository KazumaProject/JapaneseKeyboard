package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.PopupWindow
import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.kazumaproject.custom_keyboard.data.KeyAction
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
        fun onPress(action: FlickAction?)
        fun onFlick(direction: CircularFlickDirection, action: FlickAction)
        fun onStateChanged(view: View, newMap: Map<CircularFlickDirection, FlickAction>)
        fun onFlickDirectionChanged(newDirection: CircularFlickDirection)
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

    private var keyMaps: List<Map<CircularFlickDirection, FlickAction>> = emptyList()
    private var currentMapIndex = 0

    private var previousDirection = CircularFlickDirection.TAP
    private var mapSwitchDirection: CircularFlickDirection? = CircularFlickDirection.SLOT_4
    private var enabledSlots: Set<CircularFlickDirection> =
        CircularFlickDirection.slots(4).toSet()
    private var mapSwitchLabels: List<String?> = emptyList()

    private val controllerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var longPressJob: Job? = null
    private var isLongPressModeActive = false
    private var longPressTimeout: Long = ViewConfiguration.getLongPressTimeout().toLong()

    init {
        // デフォルトの見た目サイズを設定（必要に応じて setPopupViewSize で上書きしてください）
        popupView.setUiSize(160f, 60f, 40f)
    }

    // --- Configuration ---

    fun setFlickRanges(ranges: Map<CircularFlickDirection, Pair<Float, Float>>) {
        popupView.setCustomRanges(ranges)
        enabledSlots = ranges.keys.filter { it != CircularFlickDirection.TAP }.toSet()
    }

    fun setMapSwitchDirection(direction: CircularFlickDirection?) {
        mapSwitchDirection = direction?.takeIf { it != CircularFlickDirection.TAP }
        popupView.setMapSwitchDirection(mapSwitchDirection)
        updateMapSwitchLabelState()
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

    fun setLongPressTimeout(timeoutMillis: Long) {
        longPressTimeout = timeoutMillis.coerceIn(100L, 2000L)
    }

    fun cancel() {
        controllerScope.cancel()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(
        button: View,
        maps: List<Map<CircularFlickDirection, FlickAction>>,
        switchLabels: List<String?>
    ) {
        if (maps.isEmpty()) return
        this.keyMaps = maps
        this.mapSwitchLabels = switchLabels
        updateMapSwitchLabelState()
        button.setOnTouchListener { v, event ->
            handleTouchEvent(v, event)
        }
    }

    private fun updateMapSwitchLabelState() {
        val enabled = keyMaps.size >= 2 && getActiveMapSwitchDirection() != null
        popupView.setMapSwitchLabelEnabled(enabled)
        if (enabled) {
            popupView.setMapSwitchLabel(mapSwitchLabels.getOrNull(currentMapIndex))
        } else {
            popupView.setMapSwitchLabel(null)
        }
    }

    private fun isMapSwitchAction(action: FlickAction?): Boolean {
        return action is FlickAction.Action &&
            action.action == KeyAction.MoveCustomKeyboardTab &&
            action.label == "⇄"
    }

    private fun isExplicitSlotAction(action: FlickAction?): Boolean {
        return when (action) {
            is FlickAction.Input -> action.label != null
            is FlickAction.Action -> action.label != null
            null -> false
        }
    }

    private fun getActiveMapSwitchDirection(): CircularFlickDirection? {
        if (keyMaps.isEmpty()) return null
        val currentMap = keyMaps[currentMapIndex]
        return currentMap.entries.firstOrNull { (direction, action) ->
            direction != CircularFlickDirection.TAP &&
                enabledSlots.contains(direction) &&
                isMapSwitchAction(action)
        }?.key ?: mapSwitchDirection?.takeIf { direction ->
            enabledSlots.contains(direction) && !isExplicitSlotAction(currentMap[direction])
        }
    }

    private fun handleTouchEvent(view: View, event: MotionEvent): Boolean {
        // ACTION_DOWNの時はTAP扱い、それ以外は座標から計算
        val currentDirection = if (event.action == MotionEvent.ACTION_DOWN) {
            CircularFlickDirection.TAP
        } else {
            calculateDirection(event.rawX, event.rawY)
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                anchorView = view
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                previousDirection = CircularFlickDirection.TAP
                isLongPressModeActive = false
                currentMapIndex = 0 // タッチ開始時は常に最初のマップにリセット

                popupView.setFullUIMode(false)

                if (keyMaps.isNotEmpty()) {
                    popupView.setCharacterMap(keyMaps[currentMapIndex])
                    listener?.onPress(keyMaps[currentMapIndex][CircularFlickDirection.TAP])
                }
                updateMapSwitchLabelState()
                popupView.updateFlickDirection(CircularFlickDirection.TAP)

                showPopup()

                longPressJob?.cancel()
                longPressJob = controllerScope.launch {
                    delay(longPressTimeout)
                    isLongPressModeActive = true
                    popupView.setFullUIMode(true)
                    popupView.invalidate()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (currentDirection != previousDirection) {
                    listener?.onFlickDirectionChanged(currentDirection)

                    if (currentDirection != CircularFlickDirection.TAP) {
                        longPressJob?.cancel()
                    }
                }

                val switchDirection = getActiveMapSwitchDirection()
                if (
                    switchDirection != null &&
                    keyMaps.size > 1 &&
                    enabledSlots.contains(switchDirection) &&
                    currentDirection == switchDirection
                ) {
                    // フルUIモードにして次の候補を見やすくする
                    popupView.setFullUIMode(true)

                    if (previousDirection != switchDirection) {
                        currentMapIndex = (currentMapIndex + 1) % keyMaps.size
                        val newMap = keyMaps[currentMapIndex]
                        popupView.setCharacterMap(newMap)
                        updateMapSwitchLabelState()
                        listener?.onStateChanged(view, newMap)
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

                val switchDirection = getActiveMapSwitchDirection()
                val isSwitchDirection =
                    switchDirection != null &&
                        keyMaps.size > 1 &&
                        enabledSlots.contains(switchDirection) &&
                        finalDirection == switchDirection

                if (!isSwitchDirection) {
                    if (keyMaps.isNotEmpty()) {
                        val currentMap = keyMaps[currentMapIndex]
                        val action = currentMap[finalDirection]

                        if (action != null) {
                            listener?.onFlick(finalDirection, action)
                        } else if (finalDirection == CircularFlickDirection.TAP) {
                            val tapAction = currentMap[CircularFlickDirection.TAP]
                            if (tapAction != null) {
                                listener?.onFlick(CircularFlickDirection.TAP, tapAction)
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

    private fun calculateDirection(currentX: Float, currentY: Float): CircularFlickDirection {
        val dx = currentX - initialTouchX
        val dy = currentY - initialTouchY
        val distance = sqrt(dx * dx + dy * dy)

        // 判定はコンストラクタで渡された flickSensitivity を使用
        if (distance < flickSensitivity) return CircularFlickDirection.TAP

        val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360) % 360
        return popupView.getDirectionForAngle(angle)
    }
}
