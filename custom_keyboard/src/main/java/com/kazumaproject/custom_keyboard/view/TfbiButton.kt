package com.kazumaproject.custom_keyboard.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
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
        private const val FIRST_FLICK_THRESHOLD = 70f
        private const val SECOND_FLICK_THRESHOLD = 70f

        // The maximum allowed angle difference (if greater, it's considered a TAP)
        private const val MAX_ANGLE_DIFFERENCE = 70.0

        // ★ MODIFICATION ★: Added a threshold to detect when the finger returns to the center to cancel a flick.
        private const val CANCEL_THRESHOLD = 60f
    }

    private enum class FlickState { NEUTRAL, FIRST_FLICK_DETERMINED }

    private var flickState: FlickState = FlickState.NEUTRAL
    private var firstFlickDirection: TfbiFlickDirection = TfbiFlickDirection.TAP
    private var currentSecondFlickDirection: TfbiFlickDirection = TfbiFlickDirection.TAP

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var intermediateTouchX = 0f
    private var intermediateTouchY = 0f

    private val highlightPopupColor =
        ContextCompat.getColor(context, com.kazumaproject.core.R.color.popup_bg_active)
    private val defaultPopupColor =
        ContextCompat.getColor(context, com.kazumaproject.core.R.color.popup_bg)

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
            // ★ MODIFICATION ★: Check for cancellation before processing the second flick.
            val distanceFromInitial = hypot(
                (event.x - initialTouchX).toDouble(),
                (event.y - initialTouchY).toDouble()
            ).toFloat()

            if (distanceFromInitial < CANCEL_THRESHOLD) {
                // The finger has returned to the center. Reset the state to neutral.
                resetState()
                // Re-show the initial tap popup since resetState() clears all popups.
                createAndShowTapPopup(TfbiFlickDirection.TAP)
                // Stop further processing for this move event.
                return
            }

            // If not cancelled, continue with the original logic for the second flick.
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

    @SuppressLint("InflateParams")
    private fun createAndShowTapPopup(baseDirection: TfbiFlickDirection) {
        dismissTapPopup()
        val tapCharacter = characterMapProvider?.invoke(baseDirection, TfbiFlickDirection.TAP)
        if (tapCharacter.isNullOrEmpty()) return

        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.popup_flick, null)
        val popupTextView = popupView.findViewById<TextView>(R.id.popupTextView)
        popupTextView.text = tapCharacter
        popupTextView.gravity = Gravity.CENTER

        tapPopup = PopupWindow(
            popupView,
            width,
            height,
            false
        ).apply {
            isTouchable = false
            isFocusable = false
            (contentView.background as? GradientDrawable)?.let {
                val background = it.mutate() as GradientDrawable
                background.setColor(defaultPopupColor)
                background.setStroke(
                    2,
                    ContextCompat.getColor(
                        context,
                        com.kazumaproject.core.R.color.keyboard_icon_color
                    )
                )
                contentView.background = background
            }
        }

        if (!isAttachedToWindow) return
        val location = IntArray(2).also { getLocationInWindow(it) }
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
            popupTextView.gravity = Gravity.CENTER

            val popup = PopupWindow(
                popupView,
                width,
                height,
                false
            ).apply {
                isTouchable = false
                isFocusable = false
                contentView.setBackgroundResource(R.drawable.popup_background)
                (contentView.background.mutate() as? GradientDrawable)?.let { background ->
                    background.setColor(defaultPopupColor)
                    background.setStroke(
                        2,
                        ContextCompat.getColor(
                            context,
                            com.kazumaproject.core.R.color.keyboard_icon_color
                        )
                    )
                }
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

            val popupWidth = width
            val popupHeight = height

            val (x, y) = when (direction) {
                TfbiFlickDirection.UP -> Pair(anchorX, anchorY - popupHeight)
                TfbiFlickDirection.DOWN -> Pair(anchorX, anchorY + height)
                TfbiFlickDirection.LEFT -> Pair(anchorX - popupWidth, anchorY)
                TfbiFlickDirection.RIGHT -> Pair(anchorX + width, anchorY)
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
