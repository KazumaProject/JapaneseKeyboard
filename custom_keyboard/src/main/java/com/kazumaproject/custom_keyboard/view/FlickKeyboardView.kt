package com.kazumaproject.custom_keyboard.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import androidx.annotation.AttrRes
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.view.setMargins
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

class FlickKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : GridLayout(context, attrs, defStyleAttr) {

    /**
     * ▼▼▼ 修正 ▼▼▼
     * 十字フリックのロングプレス後の指離しイベントを処理するメソッドを追加
     */
    interface OnKeyboardActionListener {
        fun onKey(text: String)
        fun onAction(action: KeyAction)
        fun onActionLongPress(action: KeyAction)
        fun onActionUpAfterLongPress(action: KeyAction)
        fun onFlickDirectionChanged(direction: FlickDirection)
        fun onFlickActionLongPress(action: KeyAction)
        fun onFlickActionUpAfterLongPress(action: KeyAction)
    }
    // ▲▲▲ 修正 ▲▲▲

    private var listener: OnKeyboardActionListener? = null
    private val flickControllers = mutableListOf<FlickInputController>()
    private val crossFlickControllers = mutableListOf<CrossFlickInputController>()

    fun setOnKeyboardActionListener(listener: OnKeyboardActionListener) {
        this.listener = listener
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setKeyboard(layout: KeyboardLayout) {
        this.removeAllViews()
        flickControllers.forEach { it.cancel() }
        flickControllers.clear()
        crossFlickControllers.forEach { it.cancel() }
        crossFlickControllers.clear()

        this.columnCount = layout.columnCount
        this.rowCount = layout.rowCount

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
                    setMargins(6,7,6,5)
                } else {
                    setMargins(7)
                }
            }
            keyView.layoutParams = params

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
                                    val firstMap = flickKeyMapsList.firstOrNull()
                                    val tapAction = firstMap?.get(FlickDirection.TAP)
                                    if (tapAction is FlickAction.Input) {
                                        (keyView as? Button)?.text = tapAction.char
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
                    val flickActionMap = layout.flickKeyMaps[keyData.label]?.firstOrNull()
                    if (flickActionMap != null) {
                        val controller = CrossFlickInputController(context).apply {
                            /**
                             * ▼▼▼ 修正 ▼▼▼
                             * 新しい onFlickUpAfterLongPress メソッドを実装
                             */
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
                                    when (flickAction) {
                                        is FlickAction.Action -> {
                                            this@FlickKeyboardView.listener?.onFlickActionLongPress(
                                                flickAction.action
                                            )
                                        }

                                        is FlickAction.Input -> {
                                            // 必要であれば、文字入力のロングプレスに対する処理をここに記述
                                        }
                                    }
                                }

                                override fun onFlickUpAfterLongPress(flickAction: FlickAction) {
                                    when (flickAction) {
                                        is FlickAction.Action -> {
                                            this@FlickKeyboardView.listener?.onFlickActionUpAfterLongPress(
                                                flickAction.action
                                            )
                                        }

                                        is FlickAction.Input -> {
                                            // 必要であれば、文字入力のロングプレス後の指離しに対する処理をここに記述
                                        }
                                    }
                                }
                            }
                            // ▲▲▲ 修正 ▲▲▲
                            attach(keyView, flickActionMap)
                        }
                        crossFlickControllers.add(controller)
                    }
                }

                KeyType.NORMAL -> {
                    keyData.action?.let { action ->
                        var isLongPressTriggered = false
                        keyView.setOnClickListener {
                            this@FlickKeyboardView.listener?.onAction(
                                action
                            )
                        }
                        keyView.setOnLongClickListener {
                            isLongPressTriggered = true
                            this@FlickKeyboardView.listener?.onActionLongPress(action)
                            true
                        }
                        keyView.setOnTouchListener { _, event ->
                            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                                if (isLongPressTriggered) {
                                    this@FlickKeyboardView.listener?.onActionUpAfterLongPress(action)
                                    isLongPressTriggered = false
                                }
                            }
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
    }

    private fun Context.getColorFromAttr(@AttrRes attrRes: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attrRes, typedValue, true)
        return ContextCompat.getColor(this, typedValue.resourceId)
    }
}
