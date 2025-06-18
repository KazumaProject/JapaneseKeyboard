package com.kazumaproject.custom_keyboard.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
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
import kotlin.math.pow
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
    private val standardFlickControllers = mutableListOf<StandardFlickInputController>()
    private val petalFlickControllers = mutableListOf<PetalFlickInputController>()

    // START: New properties for handling touches in margins
    private var motionTarget: View? = null
    private val hitRect = Rect()
    // END: New properties

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
                    elevation = 2f
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
                        // This padding was removed in the original code, re-add if needed for text positioning
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
                        elevation = 2f
                        setBackgroundResource(if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_side_bg_material else com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    } else if (keyData.keyType != KeyType.STANDARD_FLICK) {
                        setBackgroundResource(if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_center_bg_material else com.kazumaproject.core.R.drawable.ten_keys_center_bg_material_light)
                    }
                }
            }
            // REVERTED CHANGE: We are using margins again to create the visual gaps
            val params = LayoutParams().apply {
                rowSpec = spec(keyData.row, keyData.rowSpan, FILL, 1f)
                columnSpec = spec(keyData.column, keyData.colSpan, FILL, 1f)
                width = 0
                height = 0
                elevation = 2f
                if (keyData.isSpecialKey) {
                    elevation = 2f
                    setMargins(6, 12, 6, 6)
                } else {
                    setMargins(6, 9, 6, 9)
                }
            }
            keyView.layoutParams = params

            // The rest of this method (the `when` block) remains unchanged
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
                            val scaleFactor = 1.4f
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
                        val label = keyData.label

                        val keyBaseColor =
                            if (isDarkTheme) context.getColorFromAttr(R.attr.colorSurfaceContainerHighest) else context.getColorFromAttr(
                                R.attr.colorSurface
                            )
                        val keyHighlightColor =
                            context.getColorFromAttr(R.attr.colorSecondaryContainer)
                        val keyTextColor = context.getColorFromAttr(R.attr.colorOnSurface)

                        val segmentedDrawable = SegmentedBackgroundDrawable(
                            label = label,
                            baseColor = keyBaseColor,
                            highlightColor = keyHighlightColor,
                            textColor = keyTextColor,
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

                            val popupBackgroundColor =
                                if (isDarkTheme) context.getColorFromAttr(R.attr.colorSurfaceContainerHighest) else context.getColorFromAttr(
                                    R.attr.colorSurface
                                )
                            val popupTextColor = context.getColorFromAttr(R.attr.colorOnSurface)
                            val popupStrokeColor = context.getColorFromAttr(R.attr.colorOutline)

                            val dynamicColorTheme = FlickPopupColorTheme(
                                segmentHighlightGradientStartColor = popupBackgroundColor,
                                textColor = popupTextColor,
                                separatorColor = popupStrokeColor,
                                segmentColor = 0,
                                segmentHighlightGradientEndColor = 0,
                                centerGradientStartColor = 0,
                                centerGradientEndColor = 0,
                                centerHighlightGradientStartColor = 0,
                                centerHighlightGradientEndColor = 0
                            )
                            setPopupColors(dynamicColorTheme)

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
                            val secondaryColor =
                                context.getColorFromAttr(R.attr.colorSecondaryContainer)
                            val surfaceContainerLow =
                                context.getColorFromAttr(R.attr.colorSurfaceContainerLow)
                            val surfaceContainerHighest =
                                if (isDarkTheme) context.getColorFromAttr(R.attr.colorSurfaceContainerHighest) else context.getColorFromAttr(
                                    R.attr.colorSurface
                                )
                            val textColor =
                                context.getColor(com.kazumaproject.core.R.color.keyboard_icon_color)

                            val dynamicColorTheme = FlickPopupColorTheme(
                                segmentColor = surfaceContainerHighest,
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

                            elevation = 1f

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

    // START: New method to handle touches in margins
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // First, check if the touch is on any child view.
                for (i in 0 until childCount) {
                    val child = getChildAt(i)
                    child.getHitRect(hitRect)
                    if (hitRect.contains(event.x.toInt(), event.y.toInt())) {
                        // If it is, let the system handle it normally.
                        return super.onTouchEvent(event)
                    }
                }

                // If not on any child, it's in a margin. Find the nearest child.
                var nearestChild: View? = null
                var minDistance = Double.MAX_VALUE

                for (i in 0 until childCount) {
                    val child = getChildAt(i)
                    val childCenterX = child.left + child.width / 2f
                    val childCenterY = child.top + child.height / 2f

                    val distance = sqrt(
                        (event.x - childCenterX).pow(2) + (event.y - childCenterY).pow(2)
                    )

                    if (distance < minDistance) {
                        minDistance = distance.toDouble()
                        nearestChild = child
                    }
                }

                // We found the nearest child, make it the target for this touch gesture.
                motionTarget = nearestChild
                motionTarget?.let {
                    // Create a new event with coordinates translated to the child's coordinate system
                    val newEvent = MotionEvent.obtain(event)
                    newEvent.offsetLocation(-it.left.toFloat(), -it.top.toFloat())
                    // Dispatch the event to the child
                    it.dispatchTouchEvent(newEvent)
                    newEvent.recycle()
                }
                // Return true to indicate we are handling this touch gesture.
                return true
            }

            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // If we have a target from a previous ACTION_DOWN...
                motionTarget?.let {
                    // ...continue forwarding events to it.
                    val newEvent = MotionEvent.obtain(event)
                    newEvent.offsetLocation(-it.left.toFloat(), -it.top.toFloat())
                    it.dispatchTouchEvent(newEvent)
                    newEvent.recycle()

                    // If the gesture is over, clear the target.
                    if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                        motionTarget = null
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
    // END: New method

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        flickControllers.forEach { it.cancel() }
        crossFlickControllers.forEach { it.cancel() }
        standardFlickControllers.forEach { it.cancel() }
        petalFlickControllers.forEach { it.cancel() }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
    }

    private fun Context.getColorFromAttr(@AttrRes attrRes: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(
            attrRes,
            typedValue,
            true
        )
        return ContextCompat.getColor(this, typedValue.resourceId)
    }

    private fun spToPx(sp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
            .toInt()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
