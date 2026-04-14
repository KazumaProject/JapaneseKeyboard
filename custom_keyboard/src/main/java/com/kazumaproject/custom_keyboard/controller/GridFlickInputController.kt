package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.PopupWindow
import androidx.core.graphics.drawable.toDrawable
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
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

class GridFlickInputController(
    private val context: Context, private val flickSensitivity: Int
) {

    interface GridFlickListener {
        fun onPress(action: FlickAction)
        fun onFlick(action: FlickAction, isFlick: Boolean)
        fun onLongPress(action: FlickAction)
        fun onUpAfterLongPress(action: FlickAction, isFlick: Boolean)
    }

    var listener: GridFlickListener? = null
    private var characterMap: Map<FlickDirection, FlickAction> = emptyMap()
    private val controllerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var longPressJob: Job? = null
    private var isLongPressModeActive = false
    private var anchorView: View? = null
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var originalKeyText: CharSequence? = null
    private var longPressTimeout: Long = ViewConfiguration.getLongPressTimeout().toLong()
    private var isLongPressTriggered = false
    private var longPressAction: FlickAction? = null

    // 各方向に対応するPopupWindowを保持するMap
    private val popupMap: MutableMap<FlickDirection, PopupWindow> = mutableMapOf()
    private var currentVisiblePopup: PopupWindow? = null

    // 現在表示しているフリック方向を保持する変数（ちらつき防止用）
    private var currentFlickDirection: FlickDirection? = null

    private val gridPopup: PopupWindow = PopupWindow(
        FlickGridPopupView(context),
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        false
    ).apply {
        setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    }

    private var colorTheme: FlickPopupColorTheme? = null

    init {
        // gridPopupの初期設定
        gridPopup.isClippingEnabled = false
        gridPopup.elevation = 8f
        gridPopup.animationStyle = 0
        gridPopup.enterTransition = null
        gridPopup.exitTransition = null
    }

    fun setPopupColors(theme: FlickPopupColorTheme) {
        this.colorTheme = theme
    }

    fun setLongPressTimeout(timeoutMillis: Long) {
        longPressTimeout = timeoutMillis.coerceIn(100L, 2000L)
    }

    private fun createPopups() {
        popupMap.values.forEach { if (it.isShowing) it.dismiss() }
        popupMap.clear()

        val directions = listOf(
            FlickDirection.TAP,
            FlickDirection.UP,
            FlickDirection.DOWN,
            FlickDirection.UP_LEFT_FAR,
            FlickDirection.UP_RIGHT_FAR
        )

        val currentAnchor = anchorView ?: return

        directions.forEach { direction ->
            val action = characterMap[direction]
            val hasContent = when (action) {
                is FlickAction.Input -> action.char.isNotEmpty()
                is FlickAction.Action -> true
                null -> false
            }
            if (hasContent && action != null) {
                val popupView = DirectionalKeyPopupView(context).apply {
                    setAction(action)
                    colorTheme?.let { setColors(it) }
                    setFlickDirection(direction)
                }

                val popupHeight = when (direction) {
                    FlickDirection.UP, FlickDirection.DOWN -> {
                        currentAnchor.height + (currentAnchor.height / 4)
                    }

                    FlickDirection.TAP -> {
                        currentAnchor.height
                    }

                    else -> {
                        currentAnchor.height
                    }
                }

                val popupWidth = when (direction) {
                    FlickDirection.UP, FlickDirection.DOWN -> {
                        currentAnchor.width - (currentAnchor.height / 4)
                    }

                    FlickDirection.TAP -> {
                        currentAnchor.width
                    }

                    else -> {
                        currentAnchor.width + (currentAnchor.width / 2 - currentAnchor.width / 4)
                    }
                }

                val popup = PopupWindow(
                    popupView, popupWidth, popupHeight, false
                ).apply {
                    isClippingEnabled = false
                    elevation = 8f
                    animationStyle = 0
                    enterTransition = null
                    exitTransition = null
                }
                popupMap[direction] = popup
            }
        }
    }

    private fun showPopupForDirection(direction: FlickDirection) {
        if (direction == currentFlickDirection) {
            return
        }

        currentVisiblePopup?.dismiss()
        if (isLongPressModeActive) return

        val popupToShow = popupMap[direction]
        val currentAnchor = anchorView

        if (popupToShow != null && currentAnchor != null && currentAnchor.isAttachedToWindow) {
            val location = IntArray(2)
            currentAnchor.getLocationInWindow(location)
            val anchorX = location[0]
            val anchorY = location[1]
            val keyWidth = currentAnchor.width
            val keyHeight = currentAnchor.height
            val anchorCenterX = anchorX + keyWidth / 2
            val anchorCenterY = anchorY + keyHeight / 2

            val popupWidth = popupToShow.width
            val popupHeight = popupToShow.height

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

                FlickDirection.UP_LEFT_FAR -> {
                    x = anchorCenterX - popupWidth
                    y = anchorCenterY - popupHeight / 2
                }

                FlickDirection.UP_RIGHT_FAR -> {
                    x = anchorCenterX
                    y = anchorCenterY - popupHeight / 2
                }

                else -> {
                    x = anchorCenterX - popupWidth / 2
                    y = anchorCenterY - popupHeight / 2
                }
            }

            popupToShow.showAtLocation(currentAnchor, Gravity.NO_GRAVITY, x, y)
            currentVisiblePopup = popupToShow
            currentFlickDirection = direction
        }
    }

    private fun showGridPopup() {
        val currentAnchor = anchorView ?: return

        // FIX: Add a check to ensure the anchor view is attached to a window
        if (!currentAnchor.isAttachedToWindow) {
            return
        }

        val popupView = gridPopup.contentView as FlickGridPopupView
        colorTheme?.let { popupView.setColors(it) }

        popupView.setActions(characterMap, currentAnchor.width, currentAnchor.height)
        popupView.highlightKey(FlickDirection.TAP)

        val location = IntArray(2)
        currentAnchor.getLocationInWindow(location)
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)

        val x = location[0] + currentAnchor.width / 2 - popupView.measuredWidth / 2
        val y = location[1] + currentAnchor.height / 2 - popupView.measuredHeight / 2

        gridPopup.width = WindowManager.LayoutParams.WRAP_CONTENT
        gridPopup.height = WindowManager.LayoutParams.WRAP_CONTENT

        if (!gridPopup.isShowing) {
            gridPopup.showAtLocation(currentAnchor, Gravity.NO_GRAVITY, x, y)
            currentVisiblePopup = gridPopup
        } else {
            gridPopup.update(x, y, -1, -1)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(button: View, map: Map<FlickDirection, FlickAction>) {
        this.characterMap = map
        button.setOnTouchListener { v, event -> handleTouchEvent(v, event) }
    }

    private fun handleTouchEvent(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                anchorView = view
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isLongPressModeActive = false
                isLongPressTriggered = false
                longPressAction = null

                (anchorView as? Button)?.let { button ->
                    originalKeyText = button.text
                    button.text = ""
                }

                createPopups()
                characterMap[FlickDirection.TAP]?.let { listener?.onPress(it) }

                // 方向の状態を初期化
                currentFlickDirection = null

                showPopupForDirection(FlickDirection.TAP)

                longPressJob?.cancel()
                longPressJob = controllerScope.launch {
                    delay(longPressTimeout)
                    isLongPressModeActive = true
                    val tapAction = characterMap[FlickDirection.TAP] as? FlickAction.Action
                    if (tapAction != null) {
                        isLongPressTriggered = true
                        longPressAction = tapAction
                    }
                    dismissAllPopups() // 方向ポップアップを消す
                    showGridPopup()
                    if (tapAction != null) {
                        listener?.onLongPress(tapAction)
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                val distance = sqrt(dx * dx + dy * dy)

                if (distance > flickSensitivity * 0.5f) {
                    longPressJob?.cancel()
                }

                val direction = calculateDirection(dx, dy)
                if (isLongPressModeActive) {
                    // 長押しモード → グリッドポップアップで方向ハイライト
                    currentVisiblePopup?.let {
                        if (it !== gridPopup) {
                            it.dismiss()
                            currentVisiblePopup = null
                            currentFlickDirection = null
                        }
                    }
                    showGridPopup()
                    (gridPopup.contentView as? FlickGridPopupView)?.highlightKey(direction)
                } else if (distance >= flickSensitivity) {
                    // 通常フリック → 方向ごとのポップアップ
                    if (gridPopup.isShowing) gridPopup.dismiss()
                    showPopupForDirection(direction)
                } else {
                    // 閾値未満（TAP 状態）→ TAP ポップアップのみ
                    if (direction == FlickDirection.TAP) {
                        showPopupForDirection(FlickDirection.TAP)
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressJob?.cancel()
                longPressJob = null
                val finalDirection = if (event.action == MotionEvent.ACTION_UP) {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    calculateDirection(dx, dy)
                } else {
                    FlickDirection.TAP
                }

                if (isLongPressTriggered) {
                    val finalAction = characterMap[finalDirection] as? FlickAction.Action
                        ?: longPressAction
                    finalAction?.let {
                        listener?.onUpAfterLongPress(
                            it,
                            isFlick = finalDirection != FlickDirection.TAP
                        )
                    }
                } else if (event.action == MotionEvent.ACTION_UP) {
                    characterMap[finalDirection]?.let {
                        listener?.onFlick(
                            it, isFlick = finalDirection != FlickDirection.TAP
                        )
                    }
                }
                (anchorView as? Button)?.let { button ->
                    button.text = originalKeyText
                }
                originalKeyText = null
                isLongPressTriggered = false
                longPressAction = null
                dismissAllPopups()
                return true
            }
        }
        return false
    }

    fun dismissAllPopups() {
        currentVisiblePopup?.dismiss()
        currentVisiblePopup = null
        if (gridPopup.isShowing) gridPopup.dismiss()
        popupMap.values.forEach { if (it.isShowing) it.dismiss() }
        // 状態をリセット
        currentFlickDirection = null
    }

    private fun calculateDirection(dx: Float, dy: Float): FlickDirection {
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < flickSensitivity) {
            return FlickDirection.TAP
        }

        return if (abs(dx) > abs(dy)) {
            if (dx > 0) FlickDirection.UP_RIGHT_FAR else FlickDirection.UP_LEFT_FAR
        } else {
            if (dy > 0) FlickDirection.DOWN else FlickDirection.UP
        }
    }

    fun cancel() {
        dismissAllPopups()
        controllerScope.cancel()
    }
}
