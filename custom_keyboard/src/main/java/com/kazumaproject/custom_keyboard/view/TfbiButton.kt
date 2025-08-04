package com.kazumaproject.custom_keyboard.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.graphics.toColorInt
import com.kazumaproject.custom_keyboard.R
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

enum class TfbiFlickDirection {
    UP, DOWN, LEFT, RIGHT,
    UP_RIGHT, DOWN_RIGHT, DOWN_LEFT, UP_LEFT,
    TAP
}

class TfbiButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.buttonStyle
) : AppCompatButton(context, attrs, defStyleAttr) {

    interface OnTwoStepFlickListener {
        fun onFlick(first: TfbiFlickDirection, second: TfbiFlickDirection)
    }

    companion object {
        private const val FIRST_FLICK_THRESHOLD = 65f
        private const val SECOND_FLICK_THRESHOLD = 100f

        //許容する最大角度差 (これより大きい場合はTAPとみなす)
        private const val MAX_ANGLE_DIFFERENCE = 40.0
    }

    private enum class FlickState { NEUTRAL, FIRST_FLICK_DETERMINED }

    private var flickState: FlickState = FlickState.NEUTRAL
    private var firstFlickDirection: TfbiFlickDirection = TfbiFlickDirection.TAP
    private var currentSecondFlickDirection: TfbiFlickDirection = TfbiFlickDirection.TAP

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var intermediateTouchX = 0f
    private var intermediateTouchY = 0f

    private val highlightPopupColor = "#FF6200EE".toColorInt()
    private val defaultPopupColor = "#8037474F".toColorInt()

    private var onTwoStepFlickListener: OnTwoStepFlickListener? = null

    private var characterMapProvider: ((TfbiFlickDirection, TfbiFlickDirection) -> String)? = null
    private val petalPopups = mutableMapOf<TfbiFlickDirection, PopupWindow>()
    private var arePopupsVisible: Boolean = false
    private var tapPopup: PopupWindow? = null

    fun setOnTwoStepFlickListener(
        listener: OnTwoStepFlickListener,
        provider: (TfbiFlickDirection, TfbiFlickDirection) -> String
    ) {
        this.onTwoStepFlickListener = listener
        this.characterMapProvider = provider
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> handleTouchDown(event)
            MotionEvent.ACTION_MOVE -> handleTouchMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleTouchUp(event)
        }
        return true
    }

    private fun handleTouchDown(event: MotionEvent) {
        flickState = FlickState.NEUTRAL
        firstFlickDirection = TfbiFlickDirection.TAP
        initialTouchX = event.x
        initialTouchY = event.y

        createAndShowTapPopup(TfbiFlickDirection.TAP)
    }

    private fun handleTouchMove(event: MotionEvent) {
        if (flickState == FlickState.NEUTRAL) {
            val dx = event.x - initialTouchX
            val dy = event.y - initialTouchY
            val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

            if (distance >= FIRST_FLICK_THRESHOLD) {
                val enabledFirstDirections = getEnabledFirstFlickDirections()
                val determinedDirection =
                    calculateDirection(dx, dy, FIRST_FLICK_THRESHOLD, enabledFirstDirections)

                if (determinedDirection == TfbiFlickDirection.TAP) return

                firstFlickDirection = determinedDirection
                intermediateTouchX = event.x
                intermediateTouchY = event.y
                flickState = FlickState.FIRST_FLICK_DETERMINED

                setupSecondStageUI(firstFlickDirection)
                highlightForDirection(TfbiFlickDirection.TAP)
            }
        } else { // flickState == FlickState.FIRST_FLICK_DETERMINED
            val dx = event.x - intermediateTouchX
            val dy = event.y - intermediateTouchY

            val enabledSecondDirections = getEnabledSecondFlickDirections(firstFlickDirection)
            val secondDirection =
                calculateDirection(dx, dy, SECOND_FLICK_THRESHOLD, enabledSecondDirections)

            highlightForDirection(secondDirection)
        }
    }

    private fun handleTouchUp(event: MotionEvent) {
        val finalSecondDirection: TfbiFlickDirection
        if (flickState == FlickState.FIRST_FLICK_DETERMINED) {
            val dx = event.x - intermediateTouchX
            val dy = event.y - intermediateTouchY
            val enabledSecondDirections = getEnabledSecondFlickDirections(firstFlickDirection)
            finalSecondDirection =
                calculateDirection(dx, dy, SECOND_FLICK_THRESHOLD, enabledSecondDirections)
        } else {
            val dx = event.x - initialTouchX
            val dy = event.y - initialTouchY
            val enabledFirstDirections = getEnabledFirstFlickDirections()
            firstFlickDirection =
                calculateDirection(dx, dy, FIRST_FLICK_THRESHOLD, enabledFirstDirections)
            finalSecondDirection = TfbiFlickDirection.TAP
        }
        onTwoStepFlickListener?.onFlick(firstFlickDirection, finalSecondDirection)
        resetState()
    }

    private fun setupSecondStageUI(firstDirection: TfbiFlickDirection) {
        resetPopups()
        createAndShowTapPopup(firstDirection)
        val enabledSecondDirections = getEnabledSecondFlickDirections(firstDirection)
        createPetalPopups(enabledSecondDirections, baseDirectionForChar = firstDirection)
        showPetalPopups()
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
            TfbiFlickDirection.DOWN_RIGHT to 45.0,
            TfbiFlickDirection.DOWN to 90.0,
            TfbiFlickDirection.DOWN_LEFT to 135.0,
            TfbiFlickDirection.LEFT to 180.0,
            TfbiFlickDirection.UP_LEFT to -135.0,
            TfbiFlickDirection.UP to -90.0,
            TfbiFlickDirection.UP_RIGHT to -45.0
        )

        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))

        // 各有効方向と現在のフリック角度との差を計算
        val closestDirectionData = enabledDirections.map { direction ->
            val targetAngle =
                // ±180度の境界をまたぐ場合に対応するための特殊処理
                if (direction == TfbiFlickDirection.LEFT && angle < 0) -180.0 else centerAngles[direction]!!

            var diff = abs(angle - targetAngle)
            // 角度の差が180度を超える場合は、逆方向から計算する (例: 350度と10度は10度差)
            if (diff > 180) {
                diff = 360 - diff
            }
            // 方向と角度差をペアで保持
            Pair(direction, diff)
        }.minByOrNull { it.second } // 角度差(second)が最も小さいものを選ぶ

        // 最も近い方向が見つからない、またはその角度差がしきい値より大きい場合はTAPとする
        if (closestDirectionData == null || closestDirectionData.second > MAX_ANGLE_DIFFERENCE) {
            return TfbiFlickDirection.TAP
        }

        // しきい値以内であれば、その方向を返す
        return closestDirectionData.first
    }

    @SuppressLint("InflateParams")
    private fun createAndShowTapPopup(baseDirection: TfbiFlickDirection) {
        dismissTapPopup()
        val tapCharacter = characterMapProvider?.invoke(baseDirection, TfbiFlickDirection.TAP)
        if (tapCharacter.isNullOrEmpty()) return

        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.popup_flick, null)
        val popupTextView = popupView.findViewById<TextView>(R.id.popupTextView)
        popupTextView.text = tapCharacter
        // Optional but recommended: Center the text inside the now larger popup view.
        popupTextView.gravity = Gravity.CENTER

        tapPopup = PopupWindow(
            popupView,
            width, // 1. Use the button's width
            height, // 2. Use the button's height
            false
        ).apply {
            isTouchable = false
            isFocusable = false
            (contentView.background as? GradientDrawable)?.let {
                val background = it.mutate() as GradientDrawable
                background.setColor(defaultPopupColor)
                background.setStroke(2, Color.WHITE)
                contentView.background = background
            }
        }

        if (!isAttachedToWindow) return
        val location = IntArray(2).also { getLocationInWindow(it) }

        // 3. Simplify positioning logic.
        // The popup is the same size as the button, so it can be shown at the button's exact location.
        val offsetX = location[0]
        val offsetY = location[1]

        tapPopup?.showAtLocation(this, Gravity.NO_GRAVITY, offsetX, offsetY)
    }

    private fun dismissTapPopup() {
        tapPopup?.dismiss()
        tapPopup = null
    }

    @SuppressLint("InflateParams")
    private fun createPetalPopups(
        enabledDirections: Set<TfbiFlickDirection>,
        baseDirectionForChar: TfbiFlickDirection
    ) {
        petalPopups.values.forEach { it.dismiss() }
        petalPopups.clear()
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        for (direction in enabledDirections) {
            val character = characterMapProvider?.invoke(baseDirectionForChar, direction) ?: ""
            if (character.isEmpty()) continue
            val popupView = inflater.inflate(R.layout.popup_flick, null)
            val popupTextView = popupView.findViewById<TextView>(R.id.popupTextView)
            popupTextView.text = character
            val popup = PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false
            ).apply {
                isTouchable = false
                isFocusable = false
                contentView.setBackgroundResource(R.drawable.popup_background)
                val background = contentView.background.mutate() as? GradientDrawable
                background?.setColor(defaultPopupColor)
            }
            petalPopups[direction] = popup
        }
    }

    private fun showPetalPopups() {
        if (!isAttachedToWindow) return
        val location = IntArray(2).also { getLocationInWindow(it) }
        val anchorX = location[0]
        val anchorY = location[1]
        petalPopups.forEach { (direction, popup) ->
            if (popup.isShowing) return@forEach
            popup.contentView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            val popupWidth = popup.contentView.measuredWidth
            val popupHeight = popup.contentView.measuredHeight
            val offset = 20
            val (x, y) = when (direction) {
                TfbiFlickDirection.UP -> Pair(
                    anchorX + width / 2 - popupWidth / 2,
                    anchorY - popupHeight - offset
                )

                TfbiFlickDirection.DOWN -> Pair(
                    anchorX + width / 2 - popupWidth / 2,
                    anchorY + height + offset
                )

                TfbiFlickDirection.LEFT -> Pair(
                    anchorX - popupWidth - offset,
                    anchorY + height / 2 - popupHeight / 2
                )

                TfbiFlickDirection.RIGHT -> Pair(
                    anchorX + width + offset,
                    anchorY + height / 2 - popupHeight / 2
                )

                TfbiFlickDirection.UP_RIGHT -> Pair(anchorX + width, anchorY - popupHeight)
                TfbiFlickDirection.DOWN_RIGHT -> Pair(anchorX + width, anchorY + height)
                TfbiFlickDirection.DOWN_LEFT -> Pair(anchorX - popupWidth, anchorY + height)
                TfbiFlickDirection.UP_LEFT -> Pair(anchorX - popupWidth, anchorY - popupHeight)
                else -> Pair(0, 0)
            }
            popup.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
        }
        arePopupsVisible = true
    }

    private fun highlightForDirection(direction: TfbiFlickDirection) {
        if (direction == currentSecondFlickDirection) return
        currentSecondFlickDirection = direction

        tapPopup?.let { popup ->
            val background = popup.contentView.background as? GradientDrawable
            val color =
                if (direction == TfbiFlickDirection.TAP) highlightPopupColor else defaultPopupColor
            background?.setColor(color)
        }

        petalPopups.forEach { (dir, popup) ->
            val background = popup.contentView.background as? GradientDrawable
            val color = if (dir == direction) highlightPopupColor else defaultPopupColor
            background?.setColor(color)
        }
    }

    private fun updatePetalCharacters(firstDirection: TfbiFlickDirection) {
        val enabledSecondDirections = getEnabledSecondFlickDirections(firstDirection)
        createPetalPopups(enabledSecondDirections, baseDirectionForChar = firstDirection)
    }

    private fun resetPopups() {
        dismissTapPopup()
        petalPopups.values.forEach { it.dismiss() }
        petalPopups.clear()
        arePopupsVisible = false
    }

    private fun resetState() {
        resetPopups()
        flickState = FlickState.NEUTRAL
        firstFlickDirection = TfbiFlickDirection.TAP
        currentSecondFlickDirection = TfbiFlickDirection.TAP
    }
}
