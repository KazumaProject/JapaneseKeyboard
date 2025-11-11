package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import androidx.core.graphics.drawable.toDrawable
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.custom_keyboard.view.TfbiFlickPopupView
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

class TfbiStickyFlickController(
    private val context: Context,
    private val flickSensitivity: Float
) {
    /**
     * このコントローラー専用のリスナーインターフェース
     */
    interface TfbiListener {
        fun onFlick(first: TfbiFlickDirection, second: TfbiFlickDirection)
    }

    private enum class FlickState { NEUTRAL, FIRST_FLICK_DETERMINED }

    companion object {
        private const val MAX_ANGLE_DIFFERENCE = 70.0
        // CANCEL_THRESHOLD はこのクラスでは使用しない
        // private const val CANCEL_THRESHOLD = 70f
    }

    private var flickState: FlickState = FlickState.NEUTRAL
    private var firstFlickDirection: TfbiFlickDirection = TfbiFlickDirection.TAP
    private var currentSecondFlickDirection: TfbiFlickDirection = TfbiFlickDirection.TAP
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var intermediateTouchX = 0f
    private var intermediateTouchY = 0f

    var listener: TfbiListener? = null
    private var characterMapProvider: ((TfbiFlickDirection, TfbiFlickDirection) -> String)? = null
    private var attachedView: View? = null

    private var popupView: TfbiFlickPopupView? = null
    private var popupWindow: PopupWindow? = null

    private lateinit var gestureDetector: GestureDetector

    @SuppressLint("ClickableViewAccessibility")
    fun attach(
        view: View,
        provider: (TfbiFlickDirection, TfbiFlickDirection) -> String
    ) {
        this.attachedView = view
        this.characterMapProvider = provider

        // GestureDetectorを初期化
        gestureDetector =
            GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    // 長押しが検知されたら、フリックが開始前であることを確認して
                    // 花びら付きのポップアップを表示する
                    if (flickState == FlickState.NEUTRAL) {
                        popupWindow?.dismiss()
                        showPopup(view, TfbiFlickDirection.TAP, true)
                    }
                }
            })

        view.setOnTouchListener { _, event -> handleTouchEvent(event) }
    }

    fun cancel() {
        resetState()
        attachedView?.setOnTouchListener(null)
        attachedView = null
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        // タッチイベントをまずGestureDetectorに渡す
        gestureDetector.onTouchEvent(event)

        val view = attachedView ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> handleTouchDown(event, view)
            MotionEvent.ACTION_MOVE -> handleTouchMove(event, view)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleTouchUp(event)
        }
        return true
    }

    private fun handleTouchDown(event: MotionEvent, view: View) {
        // 状態リセットはここで行う
        resetState()
        flickState = FlickState.NEUTRAL
        initialTouchX = event.x
        initialTouchY = event.y

        // 最初は必ず花びらなしのポップアップを表示する
        showPopup(view, TfbiFlickDirection.TAP, false)
    }

    private fun handleTouchMove(event: MotionEvent, view: View) {
        if (flickState == FlickState.NEUTRAL) {
            val dx = event.x - initialTouchX
            val dy = event.y - initialTouchY
            val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

            if (distance >= flickSensitivity) {
                val enabledFirstDirections = getEnabledFirstFlickDirections()
                val determinedDirection =
                    calculateDirection(dx, dy, flickSensitivity, enabledFirstDirections)
                if (determinedDirection == TfbiFlickDirection.TAP) return

                firstFlickDirection = determinedDirection
                intermediateTouchX = event.x
                intermediateTouchY = event.y
                flickState = FlickState.FIRST_FLICK_DETERMINED

                setupSecondStageUI(firstFlickDirection)
                popupView?.highlightDirection(determinedDirection)
                currentSecondFlickDirection = determinedDirection
            }
        } else {
            // ===== ★ 変更点 1 =====
            // 中央に戻った時のキャンセルしきい値チェックを削除
            /*
            val distanceFromInitial = hypot(
                (event.x - initialTouchX).toDouble(),
                (event.y - initialTouchY).toDouble()
            ).toFloat()
            if (distanceFromInitial < CANCEL_THRESHOLD) {
                resetState()
                showPopup(view, TfbiFlickDirection.TAP, false)
                return
            }
            */
            // ===== ★ 変更点 1 終了 =====

            val dx = event.x - intermediateTouchX
            val dy = event.y - intermediateTouchY
            val enabledSecondDirections = getEnabledSecondFlickDirections(firstFlickDirection)

            var highlightTargetDirection =
                calculateDirection(dx, dy, flickSensitivity, enabledSecondDirections)

            if (highlightTargetDirection == TfbiFlickDirection.TAP) {
                // ===== ★ 変更点 2 =====
                // TAP領域に戻った場合、最初のフリック方向(firstFlickDirection)ではなく、
                // 「最後にハイライトしていた方向(currentSecondFlickDirection)」を維持する
                highlightTargetDirection = currentSecondFlickDirection
                // ===== ★ 変更点 2 終了 =====
            }

            if (highlightTargetDirection != currentSecondFlickDirection) {
                popupView?.highlightDirection(highlightTargetDirection)
                currentSecondFlickDirection = highlightTargetDirection
            }
        }
    }

    private fun handleTouchUp(event: MotionEvent) {
        // (この関数内のロジックは元の TfbiInputController と同じで、変更不要です)
        // ログのタグだけ "TfbStickyInput" に変更しています。

        Log.d(
            "TfbStickyInput", // ★ ログタグ
            "handleTouchUp: START. Current state = $flickState"
        )

        var finalSecondDirection: TfbiFlickDirection
        if (flickState == FlickState.FIRST_FLICK_DETERMINED) {
            val dx = event.x - intermediateTouchX
            val dy = event.y - intermediateTouchY
            val enabledSecondDirections = getEnabledSecondFlickDirections(firstFlickDirection)
            finalSecondDirection =
                calculateDirection(dx, dy, flickSensitivity, enabledSecondDirections)

            Log.d(
                "TfbStickyInput", // ★ ログタグ
                "handleTouchUp: State=FIRST_FLICK_DETERMINED. dx=$dx, dy=$dy, calculatedDir=$finalSecondDirection"
            )

            // このロジックが「TAP領域で指を離しても、直前にハイライトしていた方向を採用する」
            // という動作を担っているため、変更不要
            if (finalSecondDirection == TfbiFlickDirection.TAP && currentSecondFlickDirection != TfbiFlickDirection.TAP) {
                finalSecondDirection = currentSecondFlickDirection
                Log.d(
                    "TfbStickyInput", // ★ ログタグ
                    "handleTouchUp: Using currentSecondFlickDirection. finalDir=$finalSecondDirection"
                )
            }
        } else {
            val dx = event.x - initialTouchX
            val dy = event.y - initialTouchY
            val enabledFirstDirections = getEnabledFirstFlickDirections()
            firstFlickDirection =
                calculateDirection(dx, dy, flickSensitivity, enabledFirstDirections)
            finalSecondDirection = TfbiFlickDirection.TAP

            Log.d(
                "TfbStickyInput", // ★ ログタグ
                "handleTouchUp: State=NEUTRAL. dx=$dx, dy=$dy. firstDir=$firstFlickDirection, finalDir=$finalSecondDirection"
            )
        }

        if (listener == null) {
            Log.w("TfbStickyInput", "handleTouchUp: Listener is NULL!") // ★ ログタグ
        }
        Log.d(
            "TfbStickyInput", // ★ ログタグ
            "handleTouchUp: Calling onFlick(first=$firstFlickDirection, second=$finalSecondDirection)"
        )

        listener?.onFlick(firstFlickDirection, finalSecondDirection)
        resetState()
    }

    // ===== 以下のヘルパーメソッドは TfbiInputController と同一です =====

    private fun showPopup(
        anchorView: View,
        baseDirection: TfbiFlickDirection,
        showPetals: Boolean
    ) {
        if (popupWindow?.isShowing == true && !showPetals) return

        val tapCharacter = characterMapProvider?.invoke(baseDirection, TfbiFlickDirection.TAP) ?: ""

        val petalChars = if (showPetals) {
            val enabledDirections = getEnabledFirstFlickDirections()
            enabledDirections.associateWith { direction ->
                characterMapProvider?.invoke(direction, direction) ?: ""
            }
        } else {
            emptyMap()
        }

        popupView = TfbiFlickPopupView(context).apply {
            setCharacters(tapCharacter, petalChars)
            highlightDirection(TfbiFlickDirection.TAP)
        }
        val popupWidth = anchorView.width * 3
        val popupHeight = anchorView.height * 3
        popupWindow = PopupWindow(popupView, popupWidth, popupHeight, false).apply {
            isTouchable = false
            isFocusable = false
            setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
            isClippingEnabled = false
        }
        if (!anchorView.isAttachedToWindow) return
        val location = IntArray(2).also { anchorView.getLocationInWindow(it) }
        val offsetX = location[0] - anchorView.width
        val offsetY = location[1] - anchorView.height
        popupWindow?.showAtLocation(anchorView, Gravity.NO_GRAVITY, offsetX, offsetY)
    }

    private fun setupSecondStageUI(firstDirection: TfbiFlickDirection) {
        val tapCharacter =
            characterMapProvider?.invoke(firstDirection, TfbiFlickDirection.TAP) ?: ""
        val enabledDirections = getEnabledSecondFlickDirections(firstDirection)
        val petalChars = enabledDirections.associateWith {
            characterMapProvider?.invoke(firstDirection, it) ?: ""
        }
        popupView?.setCharacters(tapCharacter, petalChars)
    }


    private fun resetState() {
        popupWindow?.dismiss()
        popupWindow = null
        popupView = null
        flickState = FlickState.NEUTRAL
        firstFlickDirection = TfbiFlickDirection.TAP
        currentSecondFlickDirection = TfbiFlickDirection.TAP
    }

    private fun getEnabledFirstFlickDirections(): Set<TfbiFlickDirection> {
        val provider = characterMapProvider ?: return emptySet()
        return TfbiFlickDirection.entries.filter {
            it != TfbiFlickDirection.TAP && provider(it, TfbiFlickDirection.TAP).isNotEmpty()
        }.toSet()
    }

    private fun getEnabledSecondFlickDirections(baseDirection: TfbiFlickDirection): Set<TfbiFlickDirection> {
        val provider = characterMapProvider ?: return emptySet()
        return TfbiFlickDirection.entries.filter {
            it != TfbiFlickDirection.TAP && provider(baseDirection, it).isNotEmpty()
        }.toSet()
    }

    private fun calculateDirection(
        dx: Float,
        dy: Float,
        threshold: Float,
        enabledDirections: Set<TfbiFlickDirection>
    ): TfbiFlickDirection {
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (distance < threshold) {
            return TfbiFlickDirection.TAP
        }
        if (enabledDirections.isEmpty()) {
            return TfbiFlickDirection.TAP
        }

        val centerAngles = mapOf(
            TfbiFlickDirection.RIGHT to 0.0,
            TfbiFlickDirection.DOWN_RIGHT to 35.0,
            TfbiFlickDirection.DOWN to 90.0,
            TfbiFlickDirection.DOWN_LEFT to 125.0,
            TfbiFlickDirection.LEFT to 180.0,
            TfbiFlickDirection.UP_LEFT to -125.0,
            TfbiFlickDirection.UP to -90.0,
            TfbiFlickDirection.UP_RIGHT to -35.0
        )

        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))

        val closestDirectionData = enabledDirections.map { direction ->
            val targetAngle =
                if (direction == TfbiFlickDirection.LEFT && angle < 0) -180.0 else centerAngles[direction]!!

            var diff = abs(angle - targetAngle)
            if (diff > 180) {
                diff = 360 - diff
            }
            Pair(direction, diff)
        }.minByOrNull { it.second }

        if (closestDirectionData == null || closestDirectionData.second > MAX_ANGLE_DIFFERENCE) {
            return TfbiFlickDirection.TAP
        }

        return closestDirectionData.first
    }
}
