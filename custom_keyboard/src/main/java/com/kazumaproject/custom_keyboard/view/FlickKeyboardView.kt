package com.kazumaproject.custom_keyboard.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import androidx.annotation.AttrRes
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.google.android.material.R
import com.kazumaproject.core.domain.extensions.isDarkThemeOn
import com.kazumaproject.custom_keyboard.controller.CrossFlickInputController
import com.kazumaproject.custom_keyboard.controller.FlickInputController
import com.kazumaproject.custom_keyboard.controller.PetalFlickInputController
import com.kazumaproject.custom_keyboard.controller.PopupPosition
import com.kazumaproject.custom_keyboard.controller.StandardFlickInputController
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.layout.SegmentedBackgroundDrawable

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
    private val standardFlickControllers = mutableListOf<StandardFlickInputController>()

    // ▼▼▼ 1. ADD LIST FOR THE NEW CONTROLLER ▼▼▼
    private val petalFlickControllers = mutableListOf<PetalFlickInputController>()

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
        standardFlickControllers.forEach { it.cancel() }
        standardFlickControllers.clear()
        // ▼▼▼ 2. CLEAR THE NEW CONTROLLER LIST ▼▼▼
        petalFlickControllers.forEach { it.cancel() }
        petalFlickControllers.clear()

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
                    if (keyData.label.contains("\n")) {
                        val parts = keyData.label.split("\n", limit = 2)
                        val primaryText = parts[0]
                        val secondaryText = parts.getOrNull(1) ?: ""
                        val spannable = SpannableString(keyData.label)
                        spannable.setSpan(
                            AbsoluteSizeSpan(spToPx(16f)),
                            0,
                            primaryText.length,
                            Spannable.SPAN_INCLUSIVE_INCLUSIVE
                        )
                        if (secondaryText.isNotEmpty()) {
                            spannable.setSpan(
                                AbsoluteSizeSpan(spToPx(10f)),
                                primaryText.length + 1,
                                keyData.label.length,
                                Spannable.SPAN_INCLUSIVE_INCLUSIVE
                            )
                        }
                        this.maxLines = 2
                        this.setLineSpacing(0f, 0.9f)
                        this.setPadding(0, dpToPx(4), 0, dpToPx(4))
                        this.gravity = Gravity.CENTER
                        this.text = spannable
                    } else {
                        text = keyData.label
                        gravity = Gravity.CENTER
                        val englishOnlyRegex = Regex("^[a-zA-Z@#/_'\"().,?! ]+$")
                        val symbolRegex = Regex("^[()\\[\\],./ -]+$")
                        if (englishOnlyRegex.matches(keyData.label)) {
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        } else if (symbolRegex.matches(keyData.label)) {
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        } else {
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                        }
                    }
                    if (keyData.isSpecialKey) {
                        setBackgroundResource(if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_side_bg_material else com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
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

            when (keyData.keyType) {
                KeyType.CIRCULAR_FLICK -> {
                    val flickKeyMapsList = layout.flickKeyMaps[keyData.label]
                    if (!flickKeyMapsList.isNullOrEmpty()) {
                        val controller = FlickInputController(context).apply {
                            val secondaryColor =
                                context.getColorFromAttr(R.attr.colorSecondaryContainer)
                            val surfaceContainerLow =
                                context.getColorFromAttr(R.attr.colorSurfaceContainerLow)
                            val surfaceContainerHighest =
                                context.getColorFromAttr(R.attr.colorSurfaceContainerHighest)
                            val textColor =
                                context.getColor(com.kazumaproject.core.R.color.keyboard_icon_color)
                            val dynamicColorTheme = FlickPopupColorTheme(
                                segmentColor = surfaceContainerLow,
                                segmentHighlightGradientStartColor = secondaryColor,
                                segmentHighlightGradientEndColor = secondaryColor,
                                centerGradientStartColor = surfaceContainerHighest,
                                centerGradientEndColor = surfaceContainerLow,
                                centerHighlightGradientStartColor = secondaryColor,
                                centerHighlightGradientEndColor = secondaryColor,
                                separatorColor = textColor,
                                textColor = textColor
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
                            setPopupPosition(PopupPosition.CENTER)
                            val stringMaps = flickKeyMapsList.map { actionMap ->
                                actionMap.mapValues { (_, flickAction) ->
                                    (flickAction as? FlickAction.Input)?.char ?: ""
                                }
                            }
                            attach(keyView, stringMaps)
                            val scaleFactor = 1.3f
                            val newCenter = 64f * scaleFactor
                            val newOrbit = 170f * scaleFactor
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
                            this.listener = object : CrossFlickInputController.CrossFlickListener {
                                override fun onFlick(flickAction: FlickAction) {
                                    when (flickAction) {
                                        is FlickAction.Input -> this@FlickKeyboardView.listener?.onKey(
                                            flickAction.char
                                        )

                                        is FlickAction.Action -> this@FlickKeyboardView.listener?.onAction(
                                            flickAction.action
                                        )
                                    }
                                }

                                override fun onFlickLongPress(flickAction: FlickAction) {
                                    when (flickAction) {
                                        is FlickAction.Action -> this@FlickKeyboardView.listener?.onFlickActionLongPress(
                                            flickAction.action
                                        )

                                        is FlickAction.Input -> {}
                                    }
                                }

                                override fun onFlickUpAfterLongPress(flickAction: FlickAction) {
                                    when (flickAction) {
                                        is FlickAction.Action -> this@FlickKeyboardView.listener?.onFlickActionUpAfterLongPress(
                                            flickAction.action
                                        )

                                        is FlickAction.Input -> {}
                                    }
                                }
                            }
                            attach(keyView, flickActionMap)
                        }
                        crossFlickControllers.add(controller)
                    }
                }

                KeyType.STANDARD_FLICK -> {
                    val flickActionMap = layout.flickKeyMaps[keyData.label]?.firstOrNull()
                    if (flickActionMap != null && keyView is Button) {
                        val label = keyData.label.split("\n").firstOrNull() ?: ""
                        val baseColor =
                            if (isDarkTheme) "#424242".toColorInt() else "#FFFFFF".toColorInt()
                        val highlightColor =
                            if (isDarkTheme) "#616161".toColorInt() else "#E0E0E0".toColorInt()
                        val textColor = if (isDarkTheme) Color.WHITE else Color.BLACK
                        val segmentedDrawable = SegmentedBackgroundDrawable(
                            label = label,
                            baseColor = baseColor,
                            highlightColor = highlightColor,
                            textColor = textColor,
                            cornerRadius = 20f
                        )
                        keyView.background = segmentedDrawable
                        keyView.setTextColor(Color.TRANSPARENT)
                        val controller = StandardFlickInputController(context).apply {
                            this.listener =
                                object : StandardFlickInputController.StandardFlickListener {
                                    override fun onFlick(character: String) {
                                        this@FlickKeyboardView.listener?.onKey(character)
                                    }
                                }
                            val stringMap = flickActionMap.mapValues { (_, flickAction) ->
                                (flickAction as? FlickAction.Input)?.char ?: ""
                            }
                            attach(keyView, stringMap, segmentedDrawable)
                        }
                        standardFlickControllers.add(controller)
                    }
                }

                KeyType.PETAL_FLICK -> {
                    val flickActionMap = layout.flickKeyMaps[keyData.label]?.firstOrNull()
                    if (flickActionMap != null) {
                        val controller = PetalFlickInputController(context).apply {
                            // ▼▼▼ FIX: Dynamic Colorsを取得してコントローラーに設定 ▼▼▼
                            val secondaryColor =
                                context.getColorFromAttr(R.attr.colorSecondaryContainer)
                            val surfaceContainerLow =
                                context.getColorFromAttr(R.attr.colorSurfaceContainerLow)
                            val surfaceContainerHighest =
                                context.getColorFromAttr(R.attr.colorSurfaceContainerHighest)
                            val textColor = if (isDarkTheme) Color.WHITE else Color.BLACK

                            val dynamicColorTheme = FlickPopupColorTheme(
                                segmentColor = surfaceContainerHighest,
                                segmentHighlightGradientStartColor = secondaryColor,
                                segmentHighlightGradientEndColor = secondaryColor,
                                centerGradientStartColor = surfaceContainerHighest, // Not used, but set
                                centerGradientEndColor = surfaceContainerLow,       // Not used, but set
                                centerHighlightGradientStartColor = secondaryColor, // Not used, but set
                                centerHighlightGradientEndColor = secondaryColor,   // Not used, but set
                                separatorColor = textColor, // Not used, but set
                                textColor = textColor
                            )
                            setPopupColors(dynamicColorTheme)

                            this.listener = object : PetalFlickInputController.PetalFlickListener {
                                override fun onFlick(character: String) {
                                    this@FlickKeyboardView.listener?.onKey(character)
                                }
                            }
                            val stringMap = flickActionMap.mapValues { (_, flickAction) ->
                                (flickAction as? FlickAction.Input)?.char ?: ""
                            }
                            attach(keyView, stringMap)
                        }
                        petalFlickControllers.add(controller)
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
                            isLongPressTriggered =
                                true; this@FlickKeyboardView.listener?.onActionLongPress(action); true
                        }
                        keyView.setOnTouchListener { _, event ->
                            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                                if (isLongPressTriggered) {
                                    this@FlickKeyboardView.listener?.onActionUpAfterLongPress(action); isLongPressTriggered =
                                        false
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
        standardFlickControllers.forEach { it.cancel() }
        // ▼▼▼ 3. CANCEL THE NEW CONTROLLERS ON DETACH ▼▼▼
        petalFlickControllers.forEach { it.cancel() }
    }

    private fun Context.getColorFromAttr(@AttrRes attrRes: Int): Int {
        val typedValue = TypedValue(); theme.resolveAttribute(
            attrRes,
            typedValue,
            true
        ); return ContextCompat.getColor(this, typedValue.resourceId)
    }

    private fun spToPx(sp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
            .toInt()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
