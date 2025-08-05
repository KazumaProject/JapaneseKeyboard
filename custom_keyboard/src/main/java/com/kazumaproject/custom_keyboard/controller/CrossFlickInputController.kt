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

    /**
     * ▼▼▼ 修正 ▼▼▼
     * ロングプレス後の指離しイベント用のリスナーメソッドを追加
     */
    interface CrossFlickListener {
        fun onFlick(flickAction: FlickAction, isFlick: Boolean)
        fun onFlickLongPress(flickAction: FlickAction)
        fun onFlickUpAfterLongPress(flickAction: FlickAction, isFlick: Boolean)
    }
    // ▲▲▲ 修正 ▲▲▲

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
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
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
                        // 1. 新しいフリック先をハイライトする
                        highlightPopup(currentDirection)

                        /**
                         * ▼▼▼ 修正 ▼▼▼
                         * ロングプレスモード中にフリック先が変更された場合、
                         * その新しいフリック先のアクションで onFlickLongPress を呼び出す。
                         */
                        val directionToCommit = if (currentDirection != CrossDirection.TAP) {
                            directionMapping[currentDirection]
                        } else {
                            FlickDirection.TAP
                        }
                        val longPressAction = flickActionMap[directionToCommit]

                        longPressAction?.let { listener?.onFlickLongPress(it) }
                        // ▲▲▲ 修正 ▲▲▲
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressJob?.cancel()

                /**
                 * ▼▼▼ 修正 ▼▼▼
                 * ロングプレスが発火したか否かで、呼び出すリスナーを分岐させる
                 */
                // 指を離した時点での最終的なアクションを決定
                val flickActionToCommit = if (currentDirection != CrossDirection.TAP) {
                    flickActionMap[directionMapping[currentDirection]]
                } else {
                    flickActionMap[FlickDirection.TAP]
                }

                val isFlick = currentDirection != CrossDirection.TAP

                if (isLongPressTriggered) {
                    // ロングプレスが発火済みの場合は、UpAfterLongPress を呼び出す
                    flickActionToCommit?.let { listener?.onFlickUpAfterLongPress(it, isFlick) }
                } else {
                    // ロングプレスが発火していない場合は、通常の onFlick を呼び出す
                    flickActionToCommit?.let { listener?.onFlick(it, isFlick) }
                }
                // ▲▲▲ 修正 ▲▲▲

                dismissAllPopups()
                return true
            }
        }
        return false
    }

    // ... (calculateDirection, showPopup, showAllPopups, highlightPopup, dismissAllPopups は変更なし)
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

        val popupView = CrossFlickPopupView(context).apply {
            setContent(flickAction)
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

        directionMapping.forEach { (crossDir, flickDir) ->
            flickActionMap[flickDir]?.let { flickAction ->
                val popupView = CrossFlickPopupView(context).apply { setContent(flickAction) }
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

    private fun dismissAllPopups() {
        popupWindows.values.forEach { if (it.isShowing) it.dismiss() }
        popupWindows.clear()
        popupViews.clear()
    }
}
