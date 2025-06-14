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
import com.kazumaproject.custom_keyboard.controller.FlickInputController
import com.kazumaproject.custom_keyboard.controller.PopupPosition
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyboardLayout

class FlickKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : GridLayout(context, attrs, defStyleAttr) {

    // ▼▼▼ 変更 ▼▼▼ 新しいメソッドを追加
    interface OnKeyboardActionListener {
        fun onKey(text: String)
        fun onAction(action: KeyAction)
        fun onActionLongPress(action: KeyAction)
        fun onActionUpAfterLongPress(action: KeyAction)
    }

    private var listener: OnKeyboardActionListener? = null
    private val flickControllers = mutableListOf<FlickInputController>()

    fun setOnKeyboardActionListener(listener: OnKeyboardActionListener) {
        this.listener = listener
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setKeyboard(layout: KeyboardLayout) {
        this.removeAllViews()
        flickControllers.forEach { it.cancel() }
        flickControllers.clear()

        this.columnCount = layout.columnCount
        this.rowCount = layout.rowCount

        layout.keys.forEach { keyData ->
            val isSpecialKey = keyData.action != null
            val isDarkTheme = context.isDarkThemeOn()

            val keyView: View = if (isSpecialKey && keyData.drawableResId != null) {
                AppCompatImageButton(context).apply {
                    setImageResource(keyData.drawableResId)
                    contentDescription = keyData.label
                    setBackgroundResource(if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_side_bg_material else com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light)
                }
            } else {
                Button(context).apply {
                    text = keyData.label
                    if (isSpecialKey) {
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
                setMargins(if (isSpecialKey) 8 else 8)
            }
            keyView.layoutParams = params

            if (keyData.isFlickable) {
                // (フリック可能なキーの処理は変更なし)
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
                                (keyView as? Button)?.text =
                                    flickKeyMapsList.firstOrNull()?.get(FlickDirection.TAP)
                            }
                        }
                        setPopupPosition(PopupPosition.TOP)
                        attach(keyView, flickKeyMapsList)
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
            } else {
                // ▼▼▼ 変更箇所 ▼▼▼
                // フリック不可キーのクリック/長押し/指離し処理
                keyData.action?.let { action ->
                    // 長押しがトリガーされたかを追跡するフラグ
                    var isLongPressTriggered = false

                    // 通常のクリックイベント
                    keyView.setOnClickListener {
                        // isLongPressTriggeredがfalseの場合のみ実行される（LongClickがtrueを返すため）
                        this@FlickKeyboardView.listener?.onAction(action)
                    }

                    // 長押し開始イベント
                    keyView.setOnLongClickListener {
                        isLongPressTriggered = true
                        this@FlickKeyboardView.listener?.onActionLongPress(action)
                        true // trueを返し、クリックイベントの発生を防ぐ
                    }

                    // 指の動きを監視するタッチイベント
                    keyView.setOnTouchListener { _, event ->
                        // 指が離れた (UP) またはジェスチャーがキャンセルされた場合
                        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                            // もし長押しがトリガーされていたなら
                            if (isLongPressTriggered) {
                                // 「長押し後の指離し」イベントを通知
                                this@FlickKeyboardView.listener?.onActionUpAfterLongPress(action)
                                // フラグをリセットして次の操作に備える
                                isLongPressTriggered = false
                            }
                        }
                        // falseを返すことで、他のリスナー(onClick, onLongClick)の邪魔をしない
                        false
                    }
                }
            }
            this.addView(keyView)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        flickControllers.forEach { it.cancel() }
    }

    private fun Context.getColorFromAttr(@AttrRes attrRes: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attrRes, typedValue, true)
        return ContextCompat.getColor(this, typedValue.resourceId)
    }
}
