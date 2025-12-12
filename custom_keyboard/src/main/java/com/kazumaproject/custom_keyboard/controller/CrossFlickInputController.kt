package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.PopupWindow
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.view.CrossFlickPopupView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class CrossFlickInputController(private val context: Context) {

    interface CrossFlickListener {
        fun onFlick(flickAction: FlickAction, isFlick: Boolean)
        fun onFlickLongPress(flickAction: FlickAction)
        fun onFlickUpAfterLongPress(flickAction: FlickAction, isFlick: Boolean)
    }

    var listener: CrossFlickListener? = null

    private enum class CrossDirection {
        TAP, UP, DOWN, LEFT, RIGHT
    }

    private val directionMapping = mapOf(
        CrossDirection.UP to FlickDirection.UP,
        CrossDirection.DOWN to FlickDirection.DOWN,
        CrossDirection.LEFT to FlickDirection.UP_LEFT,
        CrossDirection.RIGHT to FlickDirection.UP_RIGHT
    )

    private var anchorView: View? = null
    private var initialTouchPoint = PointF(0f, 0f)
    private var flickActionMap: Map<FlickDirection, FlickAction> = emptyMap()

    private val popupWindows = mutableMapOf<CrossDirection, PopupWindow>()
    private val popupViews = mutableMapOf<CrossDirection, CrossFlickPopupView>()

    private var currentDirection = CrossDirection.TAP
    private val flickThreshold = 80f

    private val controllerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var longPressJob: Job? = null
    private var isLongPressMode = false
    private var isLongPressTriggered = false

    // 色設定保持用の変数
    private var popupBackgroundColor: Int? = null
    private var popupHighlightedColor: Int? = null
    private var popupTextColor: Int? = null

    /**
     * FlickKeyboardView から呼び出してテーマカラーをセットする
     */
    fun setPopupColors(backgroundColor: Int, highlightedColor: Int, textColor: Int) {
        this.popupBackgroundColor = backgroundColor
        this.popupHighlightedColor = highlightedColor
        this.popupTextColor = textColor
    }

    fun cancel() {
        controllerScope.cancel()
        dismissAllPopups()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(view: View, map: Map<FlickDirection, FlickAction>) {
        this.flickActionMap = map
        view.setOnTouchListener { v, event ->
            handleTouchEvent(v, event)
        }
    }

    private fun handleTouchEvent(view: View, event: MotionEvent): Boolean {
        // Log.d("handleTouchEvent CrossFlick", "${event.action}")
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // ▼▼▼ 修正: 押下状態を目視させる ▼▼▼
                view.isPressed = true
                view.drawableHotspotChanged(event.x, event.y)
                // ▲▲▲ 修正終了 ▲▲▲

                isLongPressMode = false
                isLongPressTriggered = false
                anchorView = view
                initialTouchPoint.set(event.rawX, event.rawY)
                currentDirection = CrossDirection.TAP

                longPressJob?.cancel()
                longPressJob = controllerScope.launch {
                    delay(ViewConfiguration.getLongPressTimeout().toLong())
                    isLongPressTriggered = true
                    isLongPressMode = true

                    val directionToCommit = if (currentDirection != CrossDirection.TAP) {
                        directionMapping[currentDirection]
                    } else {
                        FlickDirection.TAP
                    }
                    val longPressAction = flickActionMap[directionToCommit]

                    longPressAction?.let { listener?.onFlickLongPress(it) }

                    showAllPopups()
                    highlightPopup(currentDirection)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // ▼▼▼ 修正: ホットスポットの追従 ▼▼▼
                view.drawableHotspotChanged(event.x, event.y)
                // ▲▲▲ 修正終了 ▲▲▲

                val dx = event.rawX - initialTouchPoint.x
                val dy = event.rawY - initialTouchPoint.y

                val newDirection = calculateDirection(dx, dy)
                if (newDirection != currentDirection) {
                    currentDirection = newDirection
                    if (!isLongPressMode) {
                        dismissAllPopups()
                        showPopup(currentDirection)
                    } else {
                        // ロングプレスモード中のフリック先変更
                        highlightPopup(currentDirection)

                        val directionToCommit = if (currentDirection != CrossDirection.TAP) {
                            directionMapping[currentDirection]
                        } else {
                            FlickDirection.TAP
                        }
                        val longPressAction = flickActionMap[directionToCommit]

                        longPressAction?.let { listener?.onFlickLongPress(it) }
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // ▼▼▼ 修正: 押下状態解除 ▼▼▼
                view.isPressed = false
                // ▲▲▲ 修正終了 ▲▲▲

                longPressJob?.cancel()

                val flickActionToCommit = if (currentDirection != CrossDirection.TAP) {
                    flickActionMap[directionMapping[currentDirection]]
                } else {
                    flickActionMap[FlickDirection.TAP]
                }

                val isFlick = currentDirection != CrossDirection.TAP

                // Log.d("CrossFlick Up", "$flickActionToCommit $isLongPressTriggered $isFlick")

                if (isLongPressTriggered) {
                    if (flickActionToCommit == null) {
                        listener?.onFlickUpAfterLongPress(
                            FlickAction.Action(KeyAction.Cancel),
                            isFlick
                        )
                    } else {
                        flickActionToCommit.let { listener?.onFlickUpAfterLongPress(it, isFlick) }
                    }
                } else {
                    flickActionToCommit?.let { listener?.onFlick(it, isFlick) }
                }

                dismissAllPopups()
                return true
            }
        }
        return false
    }

    private fun calculateDirection(dx: Float, dy: Float): CrossDirection {
        val absDx = abs(dx)
        val absDy = abs(dy)

        if (absDx < flickThreshold && absDy < flickThreshold) {
            return CrossDirection.TAP
        }

        return if (absDx > absDy) {
            if (dx > 0) CrossDirection.RIGHT else CrossDirection.LEFT
        } else {
            if (dy > 0) CrossDirection.DOWN else CrossDirection.UP
        }
    }

    private fun showPopup(direction: CrossDirection) {
        if (direction == CrossDirection.TAP) return

        val flickDir = directionMapping[direction] ?: return
        val flickAction = flickActionMap[flickDir] ?: return
        val anchor = anchorView ?: return

        if (anchor.windowToken == null) {
            return
        }

        val popupView = CrossFlickPopupView(context).apply {
            setContent(flickAction)

            // 色設定があれば適用
            if (popupBackgroundColor != null && popupHighlightedColor != null && popupTextColor != null) {
                setColors(popupBackgroundColor!!, popupHighlightedColor!!, popupTextColor!!)
            }

            setHighlight(true)
        }

        val popupWindow = PopupWindow(popupView, anchor.width, anchor.height, false)
            .apply { isClippingEnabled = false }

        val location = IntArray(2)
        anchor.getLocationInWindow(location)
        val x = location[0]
        val y = location[1]

        val popupX = when (direction) {
            CrossDirection.LEFT -> x - anchor.width
            CrossDirection.RIGHT -> x + anchor.width
            else -> x
        }
        val popupY = when (direction) {
            CrossDirection.UP -> y - anchor.height
            CrossDirection.DOWN -> y + anchor.height
            else -> y
        }

        popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, popupX, popupY)
        popupWindows[direction] = popupWindow
    }

    private fun showAllPopups() {
        dismissAllPopups()
        val anchor = anchorView ?: return
        if (anchor.windowToken == null) {
            return
        }
        directionMapping.forEach { (crossDir, flickDir) ->
            flickActionMap[flickDir]?.let { flickAction ->
                val popupView = CrossFlickPopupView(context).apply {
                    setContent(flickAction)

                    // 色設定があれば適用
                    if (popupBackgroundColor != null && popupHighlightedColor != null && popupTextColor != null) {
                        setColors(popupBackgroundColor!!, popupHighlightedColor!!, popupTextColor!!)
                    }
                }

                val popupWindow = PopupWindow(popupView, anchor.width, anchor.height, false)
                    .apply { isClippingEnabled = false }

                val location = IntArray(2)
                anchor.getLocationInWindow(location)
                val x = location[0]
                val y = location[1]

                val popupX = when (crossDir) {
                    CrossDirection.LEFT -> x - anchor.width
                    CrossDirection.RIGHT -> x + anchor.width
                    else -> x
                }
                val popupY = when (crossDir) {
                    CrossDirection.UP -> y - anchor.height
                    CrossDirection.DOWN -> y + anchor.height
                    else -> y
                }

                popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, popupX, popupY)
                popupWindows[crossDir] = popupWindow
                popupViews[crossDir] = popupView
            }
        }
    }

    private fun highlightPopup(direction: CrossDirection) {
        popupViews.forEach { (dir, view) ->
            view.setHighlight(dir == direction)
        }
    }

    fun dismissAllPopups() {
        popupWindows.values.forEach { if (it.isShowing) it.dismiss() }
        popupWindows.clear()
        popupViews.clear()
    }
}
