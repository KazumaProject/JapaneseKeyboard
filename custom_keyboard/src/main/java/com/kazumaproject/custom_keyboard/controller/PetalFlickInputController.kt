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

class PetalFlickInputController(
    private val context: Context, private val flickSensitivity: Int
) {

    interface PetalFlickListener {
        fun onFlick(character: String, isFlick: Boolean)
    }

    var listener: PetalFlickListener? = null
    private var characterMap: Map<FlickDirection, String> = emptyMap()
    private val controllerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var longPressJob: Job? = null
    private var isLongPressModeActive = false
    private var anchorView: View? = null
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var originalKeyText: CharSequence? = null

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
            val text = characterMap[direction] ?: ""
            if (text.isNotEmpty()) {
                val popupView = DirectionalKeyPopupView(context).apply {
                    this.text = text
                    colorTheme?.let { setColors(it) }
                    setFlickDirection(direction)
                }

                val popupHeight = when (direction) {
                    FlickDirection.UP, FlickDirection.DOWN -> {
                        currentAnchor.height + (currentAnchor.height / 2 - currentAnchor.height / 4)
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
                        currentAnchor.width
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

        popupView.setCharacters(characterMap, currentAnchor.width, currentAnchor.height)
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
    fun attach(button: View, map: Map<FlickDirection, String>) {
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

                (anchorView as? Button)?.let { button ->
                    originalKeyText = button.text
                    button.text = ""
                }

                createPopups()

                // 方向の状態を初期化
                currentFlickDirection = null

                showPopupForDirection(FlickDirection.TAP)

                longPressJob = controllerScope.launch {
                    delay(ViewConfiguration.getLongPressTimeout().toLong())
                    isLongPressModeActive = true
                    dismissAllPopups() // 方向ポップアップを消す
                    showGridPopup()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY

                if (sqrt(dx * dx + dy * dy) > flickSensitivity * 0.5f) {
                    longPressJob?.cancel()
                }

                val direction = calculateDirection(dx, dy)
                if (isLongPressModeActive) {
                    (gridPopup.contentView as? FlickGridPopupView)?.highlightKey(direction)
                } else {
                    showPopupForDirection(direction)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressJob?.cancel()
                if (event.action == MotionEvent.ACTION_UP) {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    val finalDirection = calculateDirection(dx, dy)
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
                dismissAllPopups()
                return true
            }
        }
        return false
    }

    private fun dismissAllPopups() {
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
