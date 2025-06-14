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

    // OnKeyboardActionListenerインターフェースをKeyActionベースに修正
    interface OnKeyboardActionListener {
        fun onKey(text: String)
        fun onAction(action: KeyAction)
        fun onActionLongPress(action: KeyAction)
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
            // isSpecialKeyの代わりにactionの有無で判定
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
                setMargins(if (isSpecialKey) 16 else 8)
            }
            keyView.layoutParams = params

            if (keyData.isFlickable) {
                // フリック可能なキーの処理
                val flickKeyMapsList = layout.flickKeyMaps[keyData.label]
                if (!flickKeyMapsList.isNullOrEmpty()) {
                    val controller = FlickInputController(context).apply {

                        // --- ここからが省略されていた部分です ---
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

                        // 3. コントローラーにカラーテーマを設定
                        setPopupColors(dynamicColorTheme)
                        // --- ここまでが省略されていた部分です ---

                        this.listener = object : FlickInputController.FlickListener {
                            override fun onStateChanged(
                                view: View,
                                newMap: Map<FlickDirection, String>
                            ) {
                                // 必要であれば実装
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

                        // --- ここからが省略されていた部分です ---
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
                        // --- ここまでが省略されていた部分です ---
                    }
                    flickControllers.add(controller)
                }
            } else {
                // フリック不可キーのクリック/長押し処理
                keyData.action?.let { action ->
                    keyView.setOnClickListener {
                        this@FlickKeyboardView.listener?.onAction(action)
                    }
                    keyView.setOnLongClickListener {
                        this@FlickKeyboardView.listener?.onActionLongPress(action)
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
        return ContextCompat.getColor(this, typedValue.resourceId)
    }
}
