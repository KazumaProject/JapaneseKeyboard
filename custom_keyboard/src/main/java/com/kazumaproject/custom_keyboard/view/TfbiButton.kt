package com.kazumaproject.custom_keyboard.view

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
import com.kazumaproject.custom_keyboard.R
import kotlin.math.atan2
import kotlin.math.hypot

enum class FlickDirection {
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
        fun onFlick(first: FlickDirection, second: FlickDirection)
    }

    companion object {
        private const val FIRST_FLICK_THRESHOLD = 65f
        private const val SECOND_FLICK_THRESHOLD = 40f
    }

    private enum class FlickState { NEUTRAL, FIRST_FLICK_DETERMINED }

    private var flickState: FlickState = FlickState.NEUTRAL
    private var firstFlickDirection: FlickDirection = FlickDirection.TAP
    private var currentSecondFlickDirection: FlickDirection = FlickDirection.TAP

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var intermediateTouchX = 0f
    private var intermediateTouchY = 0f

    private val highlightPopupColor = Color.parseColor("#FF6200EE")
    private val defaultPopupColor = Color.parseColor("#8037474F")

    private var onTwoStepFlickListener: OnTwoStepFlickListener? = null
    private var characterMapProvider: ((FlickDirection, FlickDirection) -> String)? = null
    private val petalPopups = mutableMapOf<FlickDirection, PopupWindow>()
    private var arePopupsVisible: Boolean = false

    fun setOnTwoStepFlickListener(
        listener: OnTwoStepFlickListener,
        provider: (FlickDirection, FlickDirection) -> String
    ) {
        this.onTwoStepFlickListener = listener
        this.characterMapProvider = provider
    }

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
        firstFlickDirection = FlickDirection.TAP
        initialTouchX = event.x
        initialTouchY = event.y
        // 1段階目のフリック候補（TAPを終点とする文字）からポップアップを生成
        createPetalPopups(getEnabledFirstFlickDirections())
    }

    private fun handleTouchMove(event: MotionEvent) {
        if (flickState == FlickState.NEUTRAL) {
            val dx = event.x - initialTouchX
            val dy = event.y - initialTouchY
            val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

            if (distance >= FIRST_FLICK_THRESHOLD) {
                // 【FIXED】1段階目として有効な方向のみを取得
                val enabledFirstDirections = getEnabledFirstFlickDirections()
                val determinedDirection =
                    calculateDirection(dx, dy, FIRST_FLICK_THRESHOLD, enabledFirstDirections)

                if (determinedDirection == FlickDirection.TAP) return

                firstFlickDirection = determinedDirection
                intermediateTouchX = event.x
                intermediateTouchY = event.y
                flickState = FlickState.FIRST_FLICK_DETERMINED

                // 2段階目のポップアップに更新
                updatePetalCharacters(firstFlickDirection)
            }
        } else {
            val dx = event.x - intermediateTouchX
            val dy = event.y - intermediateTouchY

            // 2段階目として有効な方向を取得
            val enabledSecondDirections = getEnabledSecondFlickDirections(firstFlickDirection)
            val secondDirection =
                calculateDirection(dx, dy, SECOND_FLICK_THRESHOLD, enabledSecondDirections)

            if (!arePopupsVisible) {
                showPetalPopups()
            }
            highlightPetalFor(secondDirection)
        }
    }

    private fun handleTouchUp(event: MotionEvent) {
        val finalSecondDirection: FlickDirection
        if (flickState == FlickState.FIRST_FLICK_DETERMINED) {
            val dx = event.x - intermediateTouchX
            val dy = event.y - intermediateTouchY
            val enabledSecondDirections = getEnabledSecondFlickDirections(firstFlickDirection)
            finalSecondDirection =
                calculateDirection(dx, dy, SECOND_FLICK_THRESHOLD, enabledSecondDirections)
        } else {
            val dx = event.x - initialTouchX
            val dy = event.y - initialTouchY
            // 【FIXED】1段階目として有効な方向のみを取得
            val enabledFirstDirections = getEnabledFirstFlickDirections()
            firstFlickDirection =
                calculateDirection(dx, dy, FIRST_FLICK_THRESHOLD, enabledFirstDirections)
            finalSecondDirection = FlickDirection.TAP
        }
        onTwoStepFlickListener?.onFlick(firstFlickDirection, finalSecondDirection)
        resetState()
    }

    /**
     * 【NEW】1段階目のフリックとして有効な方向（= その方向を起点とし、TAPを終点とする文字が存在する）のセットを取得
     */
    private fun getEnabledFirstFlickDirections(): Set<FlickDirection> {
        val provider = characterMapProvider ?: return emptySet()
        return FlickDirection.values().filter {
            it != FlickDirection.TAP && provider(it, FlickDirection.TAP).isNotEmpty()
        }.toSet()
    }

    /**
     * 【NEW】2段階目のフリックとして有効な方向のセットを取得
     */
    private fun getEnabledSecondFlickDirections(baseDirection: FlickDirection): Set<FlickDirection> {
        val provider = characterMapProvider ?: return emptySet()
        return FlickDirection.values().filter {
            it != FlickDirection.TAP && provider(baseDirection, it).isNotEmpty()
        }.toSet()
    }

    private fun calculateDirection(
        dx: Float,
        dy: Float,
        threshold: Float,
        enabledDirections: Set<FlickDirection>
    ): FlickDirection {
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (distance < threshold) return FlickDirection.TAP
        if (enabledDirections.isEmpty()) return FlickDirection.TAP

        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))

        val ranges = mutableMapOf(
            FlickDirection.RIGHT to (-22.5..22.5),
            FlickDirection.DOWN_RIGHT to (22.5..67.5),
            FlickDirection.DOWN to (67.5..112.5),
            FlickDirection.DOWN_LEFT to (112.5..157.5),
            FlickDirection.LEFT to (157.5..180.0),
            FlickDirection.UP_LEFT to (-157.5..-112.5),
            FlickDirection.UP to (-112.5..-67.5),
            FlickDirection.UP_RIGHT to (-67.5..-22.5)
        )
        val leftRange2 = -180.0..-157.5

        if (!enabledDirections.contains(FlickDirection.UP_RIGHT)) {
            ranges[FlickDirection.UP] = ranges.getValue(FlickDirection.UP).start..-45.0
            ranges[FlickDirection.RIGHT] = -45.0..ranges.getValue(FlickDirection.RIGHT).endInclusive
        }
        if (!enabledDirections.contains(FlickDirection.DOWN_RIGHT)) {
            ranges[FlickDirection.RIGHT] = ranges.getValue(FlickDirection.RIGHT).start..45.0
            ranges[FlickDirection.DOWN] = 45.0..ranges.getValue(FlickDirection.DOWN).endInclusive
        }
        if (!enabledDirections.contains(FlickDirection.DOWN_LEFT)) {
            ranges[FlickDirection.DOWN] = ranges.getValue(FlickDirection.DOWN).start..135.0
            ranges[FlickDirection.LEFT] = 135.0..ranges.getValue(FlickDirection.LEFT).endInclusive
        }
        if (!enabledDirections.contains(FlickDirection.UP_LEFT)) {
            ranges[FlickDirection.UP] = -135.0..ranges.getValue(FlickDirection.UP).endInclusive
            // leftRange2 の開始点を変更
            // ranges[FlickDirection.LEFT] は 157.5..180.0 のまま
        }


        for (direction in FlickDirection.values()) {
            if (direction == FlickDirection.TAP || !enabledDirections.contains(direction)) continue

            if (direction == FlickDirection.LEFT) {
                // UP_LEFTが無効な場合、LEFTの判定範囲を [-180, -135] と [135, 180] に広げる
                val currentLeftRange2 =
                    if (!enabledDirections.contains(FlickDirection.UP_LEFT)) -180.0..-135.0 else leftRange2
                if (angle in ranges.getValue(FlickDirection.LEFT) || angle in currentLeftRange2) {
                    return FlickDirection.LEFT
                }
            } else {
                if (angle in ranges.getValue(direction)) {
                    return direction
                }
            }
        }
        return FlickDirection.TAP
    }

    /**
     * 【MODIFIED】ポップアップ生成ロジックを汎用化
     */
    private fun createPetalPopups(
        enabledDirections: Set<FlickDirection>,
        baseDirectionForChar: FlickDirection = FlickDirection.TAP
    ) {
        petalPopups.values.forEach { it.dismiss() }
        petalPopups.clear()

        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        for (direction in enabledDirections) {
            val character = characterMapProvider?.invoke(
                if (baseDirectionForChar == FlickDirection.TAP) direction else baseDirectionForChar, // 1段階目と2段階目で文字取得方法を切り替え
                if (baseDirectionForChar == FlickDirection.TAP) FlickDirection.TAP else direction
            ) ?: ""

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
                val background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(defaultPopupColor)
                    setStroke(2, Color.WHITE)
                }
                contentView.background = background
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

            // ポップアップ位置の微調整
            val offset = 20
            val (x, y) = when (direction) {
                FlickDirection.UP -> Pair(
                    anchorX + width / 2 - popupWidth / 2,
                    anchorY - popupHeight - offset
                )

                FlickDirection.DOWN -> Pair(
                    anchorX + width / 2 - popupWidth / 2,
                    anchorY + height + offset
                )

                FlickDirection.LEFT -> Pair(
                    anchorX - popupWidth - offset,
                    anchorY + height / 2 - popupHeight / 2
                )

                FlickDirection.RIGHT -> Pair(
                    anchorX + width + offset,
                    anchorY + height / 2 - popupHeight / 2
                )

                FlickDirection.UP_RIGHT -> Pair(anchorX + width, anchorY - popupHeight)
                FlickDirection.DOWN_RIGHT -> Pair(anchorX + width, anchorY + height)
                FlickDirection.DOWN_LEFT -> Pair(anchorX - popupWidth, anchorY + height)
                FlickDirection.UP_LEFT -> Pair(anchorX - popupWidth, anchorY - popupHeight)
                else -> Pair(0, 0)
            }
            popup.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
        }
        arePopupsVisible = true
    }

    private fun highlightPetalFor(direction: FlickDirection) {
        if (direction == currentSecondFlickDirection) return
        currentSecondFlickDirection = direction
        petalPopups.forEach { (dir, popup) ->
            val background = popup.contentView.background as? GradientDrawable
            background?.setColor(if (dir == direction) highlightPopupColor else defaultPopupColor)
        }
    }

    /**
     * 【MODIFIED】2段階目の文字でポップアップを更新
     */
    private fun updatePetalCharacters(firstDirection: FlickDirection) {
        val enabledSecondDirections = getEnabledSecondFlickDirections(firstDirection)
        createPetalPopups(enabledSecondDirections, baseDirectionForChar = firstDirection)
    }

    private fun resetState() {
        petalPopups.values.forEach { it.dismiss() }
        arePopupsVisible = false
        flickState = FlickState.NEUTRAL
        firstFlickDirection = FlickDirection.TAP
        currentSecondFlickDirection = FlickDirection.TAP
    }
}
