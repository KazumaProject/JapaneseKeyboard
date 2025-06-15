package com.kazumaproject.custom_keyboard.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import androidx.annotation.AttrRes
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import com.kazumaproject.core.domain.extensions.isDarkThemeOn
import com.kazumaproject.custom_keyboard.controller.CrossFlickInputController
import com.kazumaproject.custom_keyboard.controller.FlickInputController
import com.kazumaproject.custom_keyboard.controller.PopupPosition
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.math.atan2
import kotlin.math.sqrt

class FlickKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : GridLayout(context, attrs, defStyleAttr) {

    interface OnKeyboardActionListener {
        fun onKey(text: String)
        fun onAction(action: KeyAction)
        fun onActionLongPress(action: KeyAction)
        fun onActionUpAfterLongPress(action: KeyAction)
        fun onFlickDirectionChanged(direction: FlickDirection)
        fun onFlickActionLongPress(action: KeyAction)
        fun onFlickActionUpAfterLongPress(action: KeyAction)
    }

    private var listener: OnKeyboardActionListener? = null
    private val flickControllers = mutableListOf<FlickInputController>()
    private val crossFlickControllers = mutableListOf<CrossFlickInputController>()

    // ▼▼▼ 新設 ▼▼▼ ハイブリッドキーのCoroutinesスコープ
    private val hybridKeyScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun setOnKeyboardActionListener(listener: OnKeyboardActionListener) {
        this.listener = listener
    }

    @SuppressLint("ClickableViewAccessibility", "Recycle")
    fun setKeyboard(layout: KeyboardLayout) {
        this.removeAllViews()
        flickControllers.forEach { it.cancel() }
        flickControllers.clear()
        crossFlickControllers.forEach { it.cancel() }
        crossFlickControllers.clear()
        // ▼▼▼ 新設 ▼▼▼ スコープをキャンセル
        hybridKeyScope.coroutineContext[Job]?.cancel()


        this.columnCount = layout.columnCount
        this.rowCount = layout.rowCount
        this.isFocusable = false

        layout.keys.forEach { keyData ->
            val isDarkTheme = context.isDarkThemeOn()

            val keyView: View = if (keyData.isSpecialKey && keyData.drawableResId != null) {
                AppCompatImageButton(context).apply {
                    isFocusable = false
                    setImageResource(keyData.drawableResId)
                    contentDescription = keyData.label
                    setBackgroundResource(if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_side_bg_material else com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light)
                }
            } else {
                Button(context).apply {
                    isFocusable = false
                    isAllCaps = false
                    text = keyData.label
                    if (keyData.isSpecialKey) {
                        setBackgroundResource(if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_side_bg_material else com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light)
                    } else {
                        setBackgroundResource(if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_center_bg_material else com.kazumaproject.core.R.drawable.ten_keys_center_bg_material_light)
                    }
                }
            }

            val params = LayoutParams().apply {
                rowSpec = spec(keyData.row, keyData.rowSpan, FILL, 1f)
                columnSpec = spec(keyData.column, keyData.colSpan, FILL, 1f)
                width = 0
                height = 0
                if (keyData.isSpecialKey) {
                    setMargins(6, 12, 6, 6)
                } else {
                    setMargins(6, 9, 6, 9)
                }
            }
            keyView.layoutParams = params

            // ▼▼▼ 修正 ▼▼▼ キーのタイプに応じてイベントハンドラを割り振る
            when (keyData.keyType) {
                KeyType.CIRCULAR_FLICK -> {
                    val flickKeyMapsList = layout.flickKeyMaps[keyData.label]
                    if (!flickKeyMapsList.isNullOrEmpty()) {
                        val controller = FlickInputController(context).apply {
                            val primaryColor =
                                context.getColorFromAttr(com.google.android.material.R.attr.colorPrimary)
                            val secondaryColor =
                                context.getColorFromAttr(com.google.android.material.R.attr.colorSecondary)
                            val tertiaryColor =
                                context.getColorFromAttr(com.google.android.material.R.attr.colorTertiary)
                            val surfaceContainerLow =
                                context.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerLow)
                            val surfaceContainerHighest =
                                context.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerHighest)
                            val outline =
                                context.getColorFromAttr(com.google.android.material.R.attr.colorOutline)
                            val dynamicColorTheme = FlickPopupColorTheme(
                                segmentColor = surfaceContainerLow,
                                segmentHighlightGradientStartColor = primaryColor,
                                segmentHighlightGradientEndColor = secondaryColor,
                                centerGradientStartColor = surfaceContainerHighest,
                                centerGradientEndColor = surfaceContainerLow,
                                centerHighlightGradientStartColor = tertiaryColor,
                                centerHighlightGradientEndColor = primaryColor,
                                separatorColor = outline,
                                textColor = outline
                            )
                            setPopupColors(dynamicColorTheme)
                            this.listener = object : FlickInputController.FlickListener {
                                override fun onStateChanged(
                                    view: View,
                                    newMap: Map<FlickDirection, String>
                                ) {
                                }

                                override fun onFlick(direction: FlickDirection, character: String) {
                                    if (character.isNotEmpty()) {
                                        this@FlickKeyboardView.listener?.onKey(character)
                                    }
                                }

                                override fun onFlickDirectionChanged(newDirection: FlickDirection) {
                                    this@FlickKeyboardView.listener?.onFlickDirectionChanged(
                                        newDirection
                                    )
                                }
                            }
                            setPopupPosition(PopupPosition.TOP)
                            val stringMaps = flickKeyMapsList.map { actionMap ->
                                actionMap.mapValues { (_, flickAction) ->
                                    (flickAction as? FlickAction.Input)?.char ?: ""
                                }
                            }
                            attach(keyView, stringMaps)
                            val scaleFactor = 1.3f
                            val newCenter = 80f * scaleFactor
                            val newOrbit = 180f * scaleFactor
                            val newTextSize = 55f * scaleFactor
                            setPopupViewSize(
                                center = newCenter,
                                target = newOrbit,
                                orbit = newOrbit,
                                textSize = newTextSize
                            )
                        }
                        flickControllers.add(controller)
                    }
                }

                KeyType.CROSS_FLICK -> {
                    // ▼▼▼ 修正 ▼▼▼ keyIdを優先してflickMapを検索する
                    val mapKey = keyData.keyId ?: keyData.label
                    val flickActionMap = layout.flickKeyMaps[mapKey]?.firstOrNull()
                    if (flickActionMap != null) {
                        val controller = CrossFlickInputController(context).apply {
                            this.listener = object : CrossFlickInputController.CrossFlickListener {
                                override fun onFlick(flickAction: FlickAction) {
                                    when (flickAction) {
                                        is FlickAction.Input -> {
                                            this@FlickKeyboardView.listener?.onKey(flickAction.char)
                                        }

                                        is FlickAction.Action -> {
                                            this@FlickKeyboardView.listener?.onAction(flickAction.action)
                                        }
                                    }
                                }

                                override fun onFlickLongPress(flickAction: FlickAction) {
                                    if (flickAction is FlickAction.Action) {
                                        this@FlickKeyboardView.listener?.onFlickActionLongPress(
                                            flickAction.action
                                        )
                                    }
                                }

                                override fun onFlickUpAfterLongPress(flickAction: FlickAction) {
                                    if (flickAction is FlickAction.Action) {
                                        this@FlickKeyboardView.listener?.onFlickActionUpAfterLongPress(
                                            flickAction.action
                                        )
                                    }
                                }
                            }
                            attach(keyView, flickActionMap)
                        }
                        crossFlickControllers.add(controller)
                    }
                }

                // ▼▼▼ 新設 ▼▼▼ DYNAMIC_FLICKキーの処理
                KeyType.DYNAMIC_FLICK -> {
                    val mapKey = keyData.keyId ?: keyData.label
                    val flickMap = layout.flickKeyMaps[mapKey]?.firstOrNull() ?: emptyMap()

                    var isFlick = false
                    val initialTouchPoint = PointF(0f, 0f)
                    val flickThreshold = 80f

                    keyView.setOnTouchListener { _, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                isFlick = false
                                initialTouchPoint.set(event.rawX, event.rawY)
                                // ここでポップアップ表示を開始しても良い
                                true
                            }

                            MotionEvent.ACTION_MOVE -> {
                                val dx = event.rawX - initialTouchPoint.x
                                val dy = event.rawY - initialTouchPoint.y
                                if (!isFlick && sqrt(dx * dx + dy * dy) > flickThreshold) {
                                    isFlick = true
                                    // フリックが検出されたことを示す（例：振動、ポップアップ表示）
                                }
                                true
                            }

                            MotionEvent.ACTION_UP -> {
                                if (isFlick) {
                                    // フリック操作の場合
                                    val dx = event.rawX - initialTouchPoint.x
                                    val dy = event.rawY - initialTouchPoint.y
                                    val angle = atan2(dy.toDouble(), dx.toDouble()) * 180 / Math.PI
                                    val direction = when {
                                        angle > -135 && angle <= -45 -> FlickDirection.UP
                                        angle > -45 && angle <= 45 -> FlickDirection.UP_RIGHT
                                        angle > 45 && angle <= 135 -> FlickDirection.DOWN
                                        else -> FlickDirection.UP_LEFT
                                    }

                                    // 暫定的な方向マッピング。より詳細なフリック方向が必要な場合は要調整
                                    val targetDirection = when (direction) {
                                        FlickDirection.UP_LEFT -> FlickDirection.UP_LEFT
                                        FlickDirection.UP_RIGHT -> FlickDirection.UP_RIGHT
                                        else -> direction
                                    }

                                    val action = flickMap[targetDirection]
                                    if (action is FlickAction.Action) {
                                        listener?.onAction(action.action)
                                    }
                                } else {
                                    // タップ操作の場合：keyDataの現在のactionを使用
                                    keyData.action?.let { listener?.onAction(it) }
                                }
                                true
                            }

                            else -> false
                        }
                    }
                }

                KeyType.NORMAL -> {
                    keyData.action?.let { action ->
                        var isLongPressTriggered = false
                        keyView.setOnClickListener {
                            if (!isLongPressTriggered) {
                                this@FlickKeyboardView.listener?.onAction(action)
                            }
                            isLongPressTriggered = false // Reset after click
                        }
                        keyView.setOnLongClickListener {
                            isLongPressTriggered = true
                            this@FlickKeyboardView.listener?.onActionLongPress(action)
                            true
                        }
                        keyView.setOnTouchListener { v, event ->
                            if (event.action == MotionEvent.ACTION_UP && isLongPressTriggered) {
                                this@FlickKeyboardView.listener?.onActionUpAfterLongPress(action)
                                isLongPressTriggered = false
                            }
                            // Return false to allow OnClickListener to be called
                            v.onTouchEvent(event)
                            false
                        }
                    }
                }
            }
            this.addView(keyView)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        flickControllers.forEach { it.cancel() }
        crossFlickControllers.forEach { it.cancel() }
        hybridKeyScope.coroutineContext[Job]?.cancel()
    }

    private fun Context.getColorFromAttr(@AttrRes attrRes: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attrRes, typedValue, true)
        return ContextCompat.getColor(this, typedValue.resourceId)
    }
}
