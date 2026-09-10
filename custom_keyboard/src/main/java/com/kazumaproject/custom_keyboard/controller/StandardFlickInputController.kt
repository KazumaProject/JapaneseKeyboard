package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.PopupWindow
import androidx.core.graphics.drawable.toDrawable
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.domain.flick.FixedGestureSessionConfigSource
import com.kazumaproject.core.domain.flick.FlickGestureMath
import com.kazumaproject.core.domain.flick.GestureSessionConfig
import com.kazumaproject.core.domain.flick.GestureSessionConfigSource
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.kazumaproject.custom_keyboard.layout.SegmentedBackgroundDrawable
import com.kazumaproject.custom_keyboard.view.StandardFlickPopupView

class StandardFlickInputController(
    context: Context,
    private val gestureConfigSource: GestureSessionConfigSource
) {

    constructor(context: Context) : this(
        context = context,
        gestureConfigSource = FixedGestureSessionConfigSource(
            GestureSessionConfig(
                settingsRevision = 0L,
                flickSensitivity = 100,
                flickThresholdPx = 65f,
                longPressTimeoutMillis =
                    ViewConfiguration.getLongPressTimeout().toLong().coerceIn(100L, 2_000L)
            )
        )
    )

    interface StandardFlickListener {
        fun onPress(character: String)
        fun onFlick(character: String)
        fun onSelectionChanged(character: String?, isFlick: Boolean) {}
        fun onCanceled() {}
    }

    var listener: StandardFlickListener? = null
    private var popupWindowAnchorProvider: (() -> View?)? = null
    private var characterMap: Map<FlickDirection, String> = emptyMap()
    private var anchorView: View? = null
    private var segmentedDrawable: SegmentedBackgroundDrawable? = null

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var activeGestureConfig: GestureSessionConfig? = null

    private val popupWindow: PopupWindow
    private val popupView = StandardFlickPopupView(context)

    private var popupBackgroundColor: Int = Color.WHITE
    private var popupTextColor: Int = Color.BLACK
    private var popupStrokeColor: Int = Color.LTGRAY
    private var popupStyle = PopupViewStyle(100, 19f)

    init {
        popupWindow = PopupWindow(
            popupView,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            isClippingEnabled = false
            elevation = 8f
            animationStyle = 0
            enterTransition = null
            exitTransition = null
        }
    }

    fun setPopupColors(theme: FlickPopupColorTheme) {
        this.popupBackgroundColor = theme.segmentHighlightGradientStartColor
        this.popupTextColor = theme.textColor
        this.popupStrokeColor = theme.separatorColor
    }

    fun applyPopupViewStyle(style: PopupViewStyle) {
        popupStyle = PopupViewStyle(
            sizeScalePercent = style.sizeScalePercent.coerceIn(50, 200),
            textSizeSp = style.textSizeSp.coerceIn(8f, 48f),
            backgroundColor = style.backgroundColor,
            textColor = style.textColor,
            skinId = style.skinId
        )
        popupView.applyPopupViewStyle(popupStyle)
    }

    fun setPopupWindowAnchorProvider(provider: (() -> View?)?) {
        popupWindowAnchorProvider = provider
    }

    fun setInputTextTransform(transform: (String) -> String) {
        popupView.setInputTextTransform(transform)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(
        button: View,
        map: Map<FlickDirection, String>,
        drawable: SegmentedBackgroundDrawable
    ) {
        val completeMap = mutableMapOf<FlickDirection, String>()
        completeMap[FlickDirection.TAP] = map[FlickDirection.TAP] ?: ""
        completeMap[FlickDirection.UP] = map[FlickDirection.UP] ?: ""
        completeMap[FlickDirection.DOWN] = map[FlickDirection.DOWN] ?: ""
        completeMap[FlickDirection.UP_LEFT_FAR] = map[FlickDirection.UP_LEFT_FAR]
            ?: map.entries.find { it.key.name.contains("LEFT") }?.value ?: ""

        completeMap[FlickDirection.UP_RIGHT_FAR] = map[FlickDirection.UP_RIGHT_FAR]
            ?: map.entries.find { it.key.name.contains("RIGHT") }?.value ?: ""

        this.characterMap = completeMap
        this.segmentedDrawable = drawable
        button.setOnTouchListener { v, event ->
            handleTouchEvent(v, event)
        }
    }

    private fun handleTouchEvent(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeGestureConfig = gestureConfigSource.snapshot()
                anchorView = view
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                listener?.onPress(characterMap[FlickDirection.TAP] ?: "")
                segmentedDrawable?.highlightDirection = FlickDirection.TAP
                showPopup(FlickDirection.TAP)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                val direction = calculateDirection(dx, dy)
                segmentedDrawable?.highlightDirection = direction
                showPopup(direction)
                listener?.onSelectionChanged(
                    characterMap[direction]?.takeIf(String::isNotEmpty),
                    direction != FlickDirection.TAP
                )
                return true
            }

            MotionEvent.ACTION_UP -> {
                segmentedDrawable?.highlightDirection = null
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                val finalDirection = calculateDirection(dx, dy)
                characterMap[finalDirection]?.let {
                    if (it.isNotEmpty()) {
                        listener?.onFlick(it)
                    }
                }
                dismissPopup()
                activeGestureConfig = null
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                segmentedDrawable?.highlightDirection = null
                dismissPopup()
                anchorView = null
                activeGestureConfig = null
                listener?.onCanceled()
                return true
            }
        }
        return false
    }

    private fun showPopup(direction: FlickDirection) {
        val keyAnchor = anchorView ?: return
        val windowAnchor = popupWindowAnchorProvider?.invoke() ?: keyAnchor
        if (!isAnchorReady(keyAnchor, windowAnchor)) {
            if (popupWindow.isShowing) {
                popupWindow.dismiss()
            }
            return
        }

        popupView.setFlickDirection(direction)
        popupView.setColors(popupBackgroundColor, popupTextColor, popupStrokeColor)
        popupView.applyPopupViewStyle(popupStyle)

        if (direction == FlickDirection.TAP) {
            popupView.updateMultiCharText(characterMap)
        } else {
            val text = characterMap[direction]
            popupView.updateText(text)
        }

        com.kazumaproject.core.ui.skin.KeyboardSkinRegistry.find(popupStyle.skinId)?.let { skin ->
            val w = keyAnchor.width
            val h = keyAnchor.height
            val location = getLocationRelativeToWindowAnchor(keyAnchor, windowAnchor)
            val center = direction == FlickDirection.TAP
            val left = direction == FlickDirection.UP_LEFT || direction == FlickDirection.UP_LEFT_FAR
            val right = direction == FlickDirection.UP_RIGHT || direction == FlickDirection.UP_RIGHT_FAR
            val horizontal = left || right
            val popupWidth = if (center) w * 3 else if (horizontal) w * 3 / 2 else w
            val popupHeight = if (center) h * 3 else if (horizontal) h else h * 3 / 2
            popupView.width = popupWidth
            popupView.height = popupHeight
            popupView.setPadding(if (right) w / 2 else 0, if (direction == FlickDirection.DOWN) h / 2 else 0,
                if (left) w / 2 else 0, if (direction == FlickDirection.UP) h / 2 else 0)
            val x = location[0] + when { center || left -> -w; right -> w / 2; else -> 0 }
            val y = location[1] + when { center || direction == FlickDirection.UP -> -h
                direction == FlickDirection.DOWN -> h / 2; else -> 0 }
            popupWindow.elevation = 0f
            skin.showPopup(popupView)
            if (popupWindow.isShowing) popupWindow.update(x, y, popupWidth, popupHeight)
            else {
                popupWindow.width = popupWidth
                popupWindow.height = popupHeight
                popupWindow.showAtLocation(windowAnchor, Gravity.NO_GRAVITY, x, y)
            }
            return
        }
        popupView.setPadding(0, 0, 0, 0)
        popupWindow.elevation = 8f
        popupWindow.width = WindowManager.LayoutParams.WRAP_CONTENT
        popupWindow.height = WindowManager.LayoutParams.WRAP_CONTENT

        val baseOffsetY = 10
        val flickUpAdditionalOffset = 80

        val location = getLocationRelativeToWindowAnchor(keyAnchor, windowAnchor)
        val x = location[0] + (keyAnchor.width / 2) - (popupView.viewSize / 2)
        var y = location[1] - popupView.viewSize - baseOffsetY

        if (direction == FlickDirection.UP) {
            y -= flickUpAdditionalOffset
        }

        if (popupWindow.isShowing) {
            runCatching {
                popupWindow.update(x, y, -1, -1)
            }
        } else {
            runCatching {
                popupWindow.showAtLocation(windowAnchor, Gravity.NO_GRAVITY, x, y)
            }
        }
    }

    private fun dismissPopup() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    private fun calculateDirection(dx: Float, dy: Float): FlickDirection {
        val config = activeGestureConfig ?: gestureConfigSource.snapshot()
        if (
            !FlickGestureMath.isThresholdCrossed(
                deltaX = dx,
                deltaY = dy,
                thresholdPx = config.flickThresholdPx,
                thresholdShape = config.flickThresholdShape
            )
        ) {
            return FlickDirection.TAP
        }

        val angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble()))

        return when {
            angle > -45 && angle <= 45 -> FlickDirection.UP_RIGHT_FAR
            angle > 45 && angle <= 135 -> FlickDirection.DOWN
            angle < -45 && angle >= -135 -> FlickDirection.UP
            else -> FlickDirection.UP_LEFT_FAR
        }
    }

    fun cancel() {
        listener?.onCanceled()
        activeGestureConfig = null
        dismissPopup()
    }

    private fun isAnchorReady(keyAnchor: View, windowAnchor: View?): Boolean {
        if (!keyAnchor.isAttachedToWindow) return false
        if (windowAnchor == null) return false
        if (!windowAnchor.isAttachedToWindow) return false
        return windowAnchor.windowToken != null
    }
}
