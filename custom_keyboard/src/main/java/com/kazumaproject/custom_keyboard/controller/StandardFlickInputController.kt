package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.PopupWindow
import androidx.core.graphics.drawable.toDrawable
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.kazumaproject.custom_keyboard.layout.SegmentedBackgroundDrawable
import com.kazumaproject.custom_keyboard.view.StandardFlickPopupView
import kotlin.math.sqrt

class StandardFlickInputController(context: Context) {

    interface StandardFlickListener {
        fun onFlick(character: String)
    }

    var listener: StandardFlickListener? = null
    private var characterMap: Map<FlickDirection, String> = emptyMap()
    private var anchorView: View? = null
    private var segmentedDrawable: SegmentedBackgroundDrawable? = null

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private val flickThreshold = 65f

    private val popupWindow: PopupWindow
    private val popupView = StandardFlickPopupView(context)

    private var popupBackgroundColor: Int = Color.WHITE
    private var popupTextColor: Int = Color.BLACK
    private var popupStrokeColor: Int = Color.LTGRAY

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

    /**
     * ▼▼▼ FIX: Method signature changed to accept FlickPopupColorTheme for consistency ▼▼▼
     */
    fun setPopupColors(theme: FlickPopupColorTheme) {
        // The standard popup is simple, so we map theme colors to its components.
        // Using highlight color for the popup background seems appropriate.
        this.popupBackgroundColor = theme.segmentHighlightGradientStartColor
        this.popupTextColor = theme.textColor
        // Using separator color for the popup's stroke.
        this.popupStrokeColor = theme.separatorColor
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
                cancel()
                anchorView = view
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                segmentedDrawable?.highlightDirection = FlickDirection.TAP
                showPopup(FlickDirection.TAP)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                cancel()
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                val direction = calculateDirection(dx, dy)
                segmentedDrawable?.highlightDirection = direction
                showPopup(direction)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancel()
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
                return true
            }
        }
        return false
    }

    /**
     * ▼▼▼ 変更点 ▼▼▼
     * ポップアップの位置をフリック方向に応じて動的に調整するよう修正。
     * 特に、上フリック（FlickDirection.UP）時に、通常より高い位置に表示します。
     */
    private fun showPopup(direction: FlickDirection) {
        val currentAnchor = anchorView ?: return
        val text = characterMap[direction]

        // ポップアップの表示内容と色を更新
        popupView.setColors(popupBackgroundColor, popupTextColor, popupStrokeColor)
        popupView.updateText(text)

        // ポップアップのY座標を決めるためのオフセット値
        val baseOffsetY = 10 // キーの上部に表示する際の基本的なマージン
        val flickUpAdditionalOffset = 80 // 上フリック時、さらに上へ移動させるための追加オフセット

        // ポップアップの表示位置を計算
        val location = IntArray(2)
        currentAnchor.getLocationInWindow(location)
        val x = location[0] + (currentAnchor.width / 2) - (popupView.viewSize / 2)
        var y = location[1] - popupView.viewSize - baseOffsetY // デフォルトのY座標

        // 方向が「上」の場合、Y座標をさらに上（値を小さく）へ調整
        if (direction == FlickDirection.UP) {
            y -= flickUpAdditionalOffset
        }

        if (popupWindow.isShowing) {
            // 既に表示されている場合は、位置を更新
            popupWindow.update(x, y, -1, -1) // 幅と高さは変更しないため-1を指定
        } else {
            // 表示されていない場合は、指定した位置に表示
            popupWindow.showAtLocation(currentAnchor, Gravity.NO_GRAVITY, x, y)
        }
    }

    private fun dismissPopup() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    private fun calculateDirection(dx: Float, dy: Float): FlickDirection {
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < flickThreshold) {
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
        dismissPopup()
    }
}
