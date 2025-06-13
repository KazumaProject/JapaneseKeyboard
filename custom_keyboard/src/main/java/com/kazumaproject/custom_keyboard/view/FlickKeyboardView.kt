package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import androidx.annotation.AttrRes
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.view.setMargins
import com.kazumaproject.custom_keyboard.controller.FlickInputController
import com.kazumaproject.custom_keyboard.controller.PopupPosition
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.kazumaproject.custom_keyboard.data.KeyboardLayout

class FlickKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : GridLayout(context, attrs, defStyleAttr) {

    interface OnKeyboardActionListener {
        fun onKey(text: String)
        fun onSpecialKey(action: String)
        fun onSpecialKeyLongPress(action: String)
    }

    private var listener: OnKeyboardActionListener? = null
    private val flickControllers = mutableListOf<FlickInputController>()

    fun setOnKeyboardActionListener(listener: OnKeyboardActionListener) {
        this.listener = listener
    }

    fun setKeyboard(layout: KeyboardLayout) {
        this.removeAllViews()
        flickControllers.forEach { it.cancel() }
        flickControllers.clear()

        this.columnCount = layout.columnCount
        this.rowCount = layout.rowCount

        layout.keys.forEach { keyData ->
            val keyView: View = if (keyData.isSpecialKey && keyData.drawableResId != null) {
                AppCompatImageButton(context).apply {
                    setImageResource(keyData.drawableResId)
                    contentDescription = keyData.label
                    setBackgroundResource(com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light)
                }
            } else {
                Button(context).apply {
                    text = keyData.label
                    if (keyData.isSpecialKey) {
                        setBackgroundResource(com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light)
                    } else {
                        setBackgroundResource(com.kazumaproject.core.R.drawable.ten_keys_center_bg_material_light)
                    }
                }
            }

            val params = LayoutParams().apply {
                rowSpec = spec(keyData.row, keyData.rowSpan, FILL, 1f)
                columnSpec = spec(keyData.column, keyData.colSpan, FILL, 1f)
                width = 0
                height = 0
                setMargins(if (keyData.isSpecialKey) 16 else 10)
            }
            keyView.layoutParams = params

            if (keyData.isFlickable) {
                val flickKeyMapsList = layout.flickKeyMaps[keyData.label]

                if (!flickKeyMapsList.isNullOrEmpty()) {
                    val controller = FlickInputController(context).apply {

                        // 1. Dynamic Colorsの役割（属性）を定義
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

                        val onPrimaryContainer =
                            context.getColorFromAttr(com.google.android.material.R.attr.colorOnPrimaryContainer)
                        val outline =
                            context.getColorFromAttr(com.google.android.material.R.attr.colorOutline)

                        val dynamicColorTheme = FlickPopupColorTheme(
                            // 通常のセグメント: 低い階層のコンテナ色
                            segmentColor = surfaceContainerLow,

                            // ハイライトセグメント: プライマリーカラーでグラデーション
                            segmentHighlightGradientStartColor = primaryColor,
                            segmentHighlightGradientEndColor = secondaryColor, // プライマリとセカンダリで変化をつける

                            // 通常の中央ボタン: 高い階層のコンテナ色
                            centerGradientStartColor = surfaceContainerHighest,
                            centerGradientEndColor = surfaceContainerLow,

                            // ハイライト時の中央ボタン: ターシャリーカラー（第3色）で目立たせる
                            centerHighlightGradientStartColor = tertiaryColor,
                            centerHighlightGradientEndColor = primaryColor,

                            // 区切り線: アウトラインの色
                            separatorColor = outline,

                            // 文字の色: コンテナ上の文字色
                            textColor = outline
                        )

                        // 3. コントローラーにカラーテーマを設定
                        setPopupColors(dynamicColorTheme)

                        this.listener = object : FlickInputController.FlickListener {
                            // This is called when the controller's state changes (e.g., dakuten)
                            override fun onStateChanged(
                                view: View,
                                newMap: Map<FlickDirection, String>
                            ) {
                                //(view as? Button)?.text = newMap[FlickDirection.TAP]
                            }

                            // This is called when a character is actually inputted
                            override fun onFlick(direction: FlickDirection, character: String) {
                                if (character.isNotEmpty()) {
                                    this@FlickKeyboardView.listener?.onKey(character)
                                }
                                // Reset the button's text back to its default state after input
                                (keyView as? Button)?.text =
                                    flickKeyMapsList.firstOrNull()?.get(FlickDirection.TAP)
                            }
                        }

                        setPopupPosition(PopupPosition.TOP)
                        // ▼▼▼ FIX ▼▼▼ The attach call now has the correct number of arguments
                        attach(keyView, flickKeyMapsList)

                        val scaleFactor = 1.618f

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
                keyView.setOnClickListener {
                    this@FlickKeyboardView.listener?.onSpecialKey(keyData.label)
                }

                if (keyData.isSpecialKey) {
                    keyView.setOnLongClickListener {
                        listener?.onSpecialKeyLongPress(keyData.label)
                        true
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
        // XMLで定義されたテーマ属性から実際の色リソースIDを取得し、その色を返す
        return ContextCompat.getColor(this, typedValue.resourceId)
    }
}
