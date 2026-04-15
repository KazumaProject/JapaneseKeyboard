package com.kazumaproject.custom_keyboard.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.GridLayout
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.R
import com.kazumaproject.core.domain.extensions.isDarkThemeOn
import com.kazumaproject.core.domain.extensions.setBorder
import com.kazumaproject.core.domain.extensions.setDrawableAlpha
import com.kazumaproject.core.domain.extensions.setDrawableSolidColor
import com.kazumaproject.custom_keyboard.controller.CrossFlickInputController
import com.kazumaproject.custom_keyboard.controller.CustomAngleFlickController
import com.kazumaproject.custom_keyboard.controller.StandardFlickInputController
import com.kazumaproject.custom_keyboard.controller.TfbiHierarchicalFlickController
import com.kazumaproject.custom_keyboard.controller.TfbiStickyFlickController
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.layout.SegmentedBackgroundDrawable
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class FlickKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : GridLayout(context, attrs, defStyleAttr) {

    interface OnKeyboardActionListener {
        fun onPress(action: KeyAction)
        fun onAction(action: KeyAction, isFlick: Boolean)
        fun onActionLongPress(action: KeyAction)
        fun onActionUpAfterLongPress(action: KeyAction)
        fun onFlickDirectionChanged(direction: FlickDirection)
        fun onFlickActionLongPress(action: KeyAction)
        fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean)
    }

    private companion object {
        private const val SPECIAL_KEY_BASE_TEXT_SIZE_SP = 16f
        private const val SPECIAL_ICON_TO_TEXT_RATIO = 1.6f
        private const val INPUT_MODE_SWITCH_ICON_SIZE_MULTIPLIER = 1.65f
    }

    private var listener: OnKeyboardActionListener? = null
    private val flickControllers = mutableListOf<CustomAngleFlickController>()
    private val crossFlickControllers = mutableListOf<CrossFlickInputController>()
    private val standardFlickControllers = mutableListOf<StandardFlickInputController>()
    private val tfbiControllers = mutableListOf<TfbiInputController>()
    private val stickyTfbiControllers = mutableListOf<TfbiStickyFlickController>()
    private val hierarchicalTfbiControllers = mutableListOf<TfbiHierarchicalFlickController>()

    private val hitRect = Rect()
    private var flickSensitivity: Int = 100
    private var longPressTimeout: Long = ViewConfiguration.getLongPressTimeout().toLong()
    private var defaultTextSize = 14f
    private var specialKeyTextSizeSp = SPECIAL_KEY_BASE_TEXT_SIZE_SP

    /**
     * 100 = デフォルト
     * 200 = margin 0 に近い最大サイズ
     * 0 に近づくほど margin が増えて小さく見える
     */
    private var keyWidthScalePercent: Int = 100
    private var keyHeightScalePercent: Int = 100

    private var iconScalePercent: Int = 100
    private var isCursorMode: Boolean = false
    private var cursorInitialX = 0f
    private var cursorInitialY = 0f

    private var liquidGlassEnable: Boolean = false

    private val dynamicKeyMap = mutableMapOf<String, KeyInfo>()
    private var currentLayout: KeyboardLayout? = null

    private data class KeyInfo(
        var view: View,
        var keyData: KeyData,
        var controller: Any? = null,
        val index: Int
    )

    private var themeMode: String = "default"
    private var isNightMode: Boolean = false
    private var isDynamicColorEnabled: Boolean = false
    private var customBgColor: Int = Color.WHITE
    private var customKeyColor: Int = Color.LTGRAY
    private var customSpecialKeyColor: Int = Color.GRAY
    private var customKeyTextColor: Int = Color.BLACK
    private var customSpecialKeyTextColor: Int = Color.BLACK

    private var liquidGlassKeyAlphaEnable: Int = 255
    private var customBorderEnable: Boolean = false
    private var customBorderColor: Int = Color.BLACK
    private var customAngleAndRange: Map<FlickDirection, Pair<Float, Float>> = emptyMap()
    private var circularViewScale: Float = 1.0f
    private var borderWidth: Int = 1
    private var flickGuideEnabled: Boolean = false

    init {
        setPadding(0, 0, 0, 0)
        clipToPadding = false
        clipChildren = false
    }

    fun setOnKeyboardActionListener(listener: OnKeyboardActionListener) {
        this.listener = listener
    }

    fun setFlickSensitivityValue(sensitivity: Int) {
        flickSensitivity = sensitivity
    }

    fun setLongPressTimeout(timeoutMillis: Long) {
        val normalized = timeoutMillis.coerceIn(100L, 2000L)
        if (longPressTimeout == normalized) return
        longPressTimeout = normalized
        currentLayout?.let { setKeyboard(it) }
    }

    fun setDefaultTextSize(textSize: Float) {
        this.defaultTextSize = textSize
    }

    fun setFlickGuideEnabled(enabled: Boolean) {
        if (flickGuideEnabled == enabled) return
        flickGuideEnabled = enabled
        currentLayout?.let { setKeyboard(it) }
    }

    fun applyKeySizing(
        keyWidthScalePercent: Int,
        keyHeightScalePercent: Int,
        iconScalePercent: Int,
        textSizeSp: Float,
        specialKeyTextSizeSp: Float
    ) {
        this.keyWidthScalePercent = keyWidthScalePercent.coerceIn(0, 200)
        this.keyHeightScalePercent = keyHeightScalePercent.coerceIn(0, 200)
        this.iconScalePercent = iconScalePercent.coerceIn(40, 200)
        this.defaultTextSize = textSizeSp.coerceIn(8f, 32f)
        this.specialKeyTextSizeSp = specialKeyTextSizeSp.coerceIn(8f, 32f)

        currentLayout?.let { setKeyboard(it) }
    }

    fun setCursorMode(enabled: Boolean) {
        isCursorMode = enabled
    }

    fun setAngleAndRange(
        range: Map<FlickDirection, Pair<Float, Float>>,
        circularPopViewScale: Float
    ) {
        this.customAngleAndRange = range
        this.circularViewScale = circularPopViewScale
    }

    fun applyKeyboardTheme(
        themeMode: String,
        currentNightMode: Int,
        isDynamicColorEnabled: Boolean,
        customBgColor: Int,
        customKeyColor: Int,
        customSpecialKeyColor: Int,
        customKeyTextColor: Int,
        customSpecialKeyTextColor: Int,
        liquidGlassEnable: Boolean,
        customBorderEnable: Boolean,
        customBorderColor: Int,
        liquidGlassKeyAlphaEnable: Int,
        borderWidth: Int
    ) {
        this.themeMode = themeMode
        this.isNightMode = (currentNightMode == Configuration.UI_MODE_NIGHT_YES)
        this.isDynamicColorEnabled = isDynamicColorEnabled
        this.customBgColor = customBgColor
        this.customKeyColor = customKeyColor
        this.customSpecialKeyColor = customSpecialKeyColor
        this.customKeyTextColor = customKeyTextColor
        this.customSpecialKeyTextColor = customSpecialKeyTextColor
        this.liquidGlassEnable = liquidGlassEnable
        this.customBorderEnable = customBorderEnable
        this.customBorderColor = customBorderColor
        this.liquidGlassKeyAlphaEnable = liquidGlassKeyAlphaEnable
        this.borderWidth = borderWidth

        if (liquidGlassEnable) {
            this.setBackgroundColor(ColorUtils.setAlphaComponent(customBgColor, 0))
        }
    }

    private fun manipulateColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setKeyboard(layout: KeyboardLayout) {
        Log.d("FlickKeyboardView", "setKeyboard (Full Rebuild)")

        removeAllViews()

        flickControllers.forEach { it.cancel() }
        flickControllers.clear()

        crossFlickControllers.forEach { it.cancel() }
        crossFlickControllers.clear()

        standardFlickControllers.forEach { it.cancel() }
        standardFlickControllers.clear()

        tfbiControllers.forEach { it.cancel() }
        tfbiControllers.clear()

        stickyTfbiControllers.forEach { it.cancel() }
        stickyTfbiControllers.clear()

        hierarchicalTfbiControllers.forEach { it.cancel() }
        hierarchicalTfbiControllers.clear()

        dynamicKeyMap.clear()
        currentLayout = layout

        columnCount = layout.columnCount
        rowCount = layout.rowCount
        isFocusable = false

        layout.keys.forEach { keyData ->
            val index = childCount
            val keyView = createKeyView(keyData)
            val controller = attachKeyBehavior(keyView, keyData)

            keyData.keyId?.let { id ->
                dynamicKeyMap[id] = KeyInfo(keyView, keyData, controller, index)
            }

            addView(keyView)
        }
    }

    fun updateDynamicKey(keyId: String, stateIndex: Int) {
        val info = dynamicKeyMap[keyId] ?: return
        val states = info.keyData.dynamicStates ?: return
        val newState = states.getOrNull(stateIndex) ?: states.firstOrNull() ?: return

        val newKeyData = info.keyData.copy(
            label = newState.label ?: "",
            action = newState.action,
            drawableResId = newState.drawableResId
        )

        val oldView = info.view
        val newViewIsIcon = newKeyData.isSpecialKey && newKeyData.drawableResId != null
        val newViewIsText = !newViewIsIcon

        val oldViewIsIcon = oldView is AppCompatImageButton
        val oldViewIsText = !oldViewIsIcon

        val needsNewView = (oldViewIsIcon && newViewIsText) || (oldViewIsText && newViewIsIcon)

        detachKeyBehavior(info.controller)

        val newView: View
        if (needsNewView) {
            newView = createKeyView(newKeyData)
            newView.layoutParams = oldView.layoutParams
            removeViewAt(info.index)
            addView(newView, info.index)
        } else {
            Log.d("FlickKeyboardView", "updateDynamicKey: Updating View for $keyId")
            newView = oldView
            updateKeyVisuals(newView, newKeyData)
        }

        val newController = attachKeyBehavior(newView, newKeyData)

        info.view = newView
        info.keyData = newKeyData
        info.controller = newController
    }

    fun updateKeyIconByAction(action: KeyAction, @DrawableRes drawableResId: Int) {
        dynamicKeyMap.values
            .filter { it.keyData.action == action }
            .forEach { info ->
                if (info.view is AppCompatImageButton) {
                    (info.view as AppCompatImageButton).setImageResource(drawableResId)
                }
            }
    }

    /**
     * 100 = デフォルト margin
     * 200 = margin 0
     * 0   = margin 2倍
     */
    private fun getScaledHorizontalMarginPx(baseMarginDp: Int): Int {
        val percent = keyWidthScalePercent.coerceIn(0, 200)
        val marginFactor = ((200f - percent) / 100f).coerceIn(0f, 2f)
        val marginDp = baseMarginDp * marginFactor
        return dpToPx(marginDp.roundToInt())
    }

    /**
     * 100 = デフォルト margin
     * 200 = margin 0
     * 0   = margin 2倍
     */
    private fun getScaledVerticalMarginPx(baseMarginDp: Int): Int {
        val percent = keyHeightScalePercent.coerceIn(0, 200)
        val marginFactor = ((200f - percent) / 100f).coerceIn(0f, 2f)
        val marginDp = baseMarginDp * marginFactor
        return dpToPx(marginDp.roundToInt())
    }

    private fun getSpecialKeyTextSizeSp(): Float {
        return specialKeyTextSizeSp.coerceIn(8f, 32f)
    }

    private fun getSpecialIconTargetSizePx(keyData: KeyData): Float {
        val baseTextSizePx = spToPx(SPECIAL_KEY_BASE_TEXT_SIZE_SP).toFloat()
        val iconScale = iconScalePercent / 100f
        val extraScale = if (shouldUseLargeImageButtonIcon(keyData)) {
            INPUT_MODE_SWITCH_ICON_SIZE_MULTIPLIER
        } else {
            1f
        }
        return baseTextSizePx * SPECIAL_ICON_TO_TEXT_RATIO * iconScale * extraScale
    }

    private fun shouldUseLargeImageButtonIcon(keyData: KeyData): Boolean {
        return when (keyData.action) {
            KeyAction.SwitchToNumberLayout,
            KeyAction.SwitchToEnglishLayout,
            KeyAction.SwitchToKanaLayout -> true

            else -> keyData.label in setOf("SwitchToNumber", "SwitchToEnglish", "SwitchToKana")
        }
    }

    private fun applyImageButtonSizing(button: AppCompatImageButton, keyData: KeyData) {
        button.scaleType = android.widget.ImageView.ScaleType.MATRIX
        button.imageMatrix = Matrix()
        button.setPadding(0, 0, 0, 0)

        button.post {
            updateImageButtonMatrix(button, keyData)
        }
    }

    private fun updateImageButtonMatrix(button: AppCompatImageButton, keyData: KeyData) {
        val drawable = button.drawable ?: return

        val drawableWidth = drawable.intrinsicWidth.toFloat()
        val drawableHeight = drawable.intrinsicHeight.toFloat()

        if (drawableWidth <= 0f || drawableHeight <= 0f) return

        val availableWidth = (button.width - button.paddingLeft - button.paddingRight).toFloat()
        val availableHeight = (button.height - button.paddingTop - button.paddingBottom).toFloat()

        if (availableWidth <= 0f || availableHeight <= 0f) return

        val targetContentSizePx = getSpecialIconTargetSizePx(keyData)

        val baseScale = minOf(
            targetContentSizePx / drawableWidth,
            targetContentSizePx / drawableHeight
        )

        val maxFitScale = minOf(
            availableWidth / drawableWidth,
            availableHeight / drawableHeight
        )

        val finalScale = minOf(baseScale, maxFitScale)

        val dx = (availableWidth - drawableWidth * finalScale) / 2f + button.paddingLeft
        val dy = (availableHeight - drawableHeight * finalScale) / 2f + button.paddingTop

        val matrix = Matrix().apply {
            postScale(finalScale, finalScale)
            postTranslate(dx, dy)
        }

        button.imageMatrix = matrix
        button.invalidate()
    }

    private fun buildKeyLabelSpannable(label: String, textSizeSp: Float): SpannableString {
        val parts = label.split("\n", limit = 2)
        val primaryText = parts[0]
        val secondaryText = parts.getOrNull(1) ?: ""
        val spannable = SpannableString(label)
        val primarySizePx = spToPx(textSizeSp)
        val secondarySizePx = spToPx((textSizeSp * 0.625f).coerceAtLeast(8f))

        spannable.setSpan(
            AbsoluteSizeSpan(primarySizePx),
            0,
            primaryText.length,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )

        if (secondaryText.isNotEmpty()) {
            spannable.setSpan(
                AbsoluteSizeSpan(secondarySizePx),
                primaryText.length + 1,
                label.length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
        }

        return spannable
    }

    private fun applyButtonText(button: AutoSizeButton, keyData: KeyData) {
        val targetTextSizeSp = if (keyData.isSpecialKey) {
            getSpecialKeyTextSizeSp()
        } else {
            defaultTextSize
        }

        button.setDefaultTextSize(targetTextSizeSp)
        button.setFlickGuideLabels(null)

        if (keyData.label.contains("\n")) {
            button.maxLines = 2
            button.setLineSpacing(0f, 0.9f)
            button.setPadding(0, dpToPx(4), 0, dpToPx(4))
            button.gravity = Gravity.CENTER
            button.text = buildKeyLabelSpannable(keyData.label, targetTextSizeSp)
        } else {
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, targetTextSizeSp)
            button.text = keyData.label
            button.gravity = Gravity.CENTER
        }

        button.refreshTextSize()
    }

    private fun extractInputMap(actionMap: Map<FlickDirection, FlickAction>): Map<FlickDirection, String> {
        return actionMap.mapValues { (_, flickAction) ->
            (flickAction as? FlickAction.Input)?.char ?: ""
        }
    }

    private fun getGuideLabels(stringMap: Map<FlickDirection, String>): AutoSizeButton.FlickGuideLabels {
        val tap = sanitizeGuideCharacter(stringMap[FlickDirection.TAP] ?: "") ?: ""
        val left = sanitizeGuideCharacter(
            stringMap[FlickDirection.UP_LEFT_FAR]
                ?: stringMap[FlickDirection.UP_LEFT]
                ?: stringMap.entries.firstOrNull { it.key.name.contains("LEFT") }?.value
                ?: ""
        ) ?: ""
        val right = sanitizeGuideCharacter(
            stringMap[FlickDirection.UP_RIGHT_FAR]
                ?: stringMap[FlickDirection.UP_RIGHT]
                ?: stringMap.entries.firstOrNull { it.key.name.contains("RIGHT") }?.value
                ?: ""
        ) ?: ""
        val down = sanitizeGuideCharacter(
            stringMap[FlickDirection.DOWN]
                ?: stringMap.entries.firstOrNull { it.key.name.contains("DOWN") }?.value
                ?: ""
        ) ?: ""
        val up = sanitizeGuideCharacter(stringMap[FlickDirection.UP] ?: "") ?: ""

        return AutoSizeButton.FlickGuideLabels(
            tap = tap,
            up = up,
            right = right,
            down = down,
            left = left
        )
    }

    private fun sanitizeGuideCharacter(value: String): String? {
        if (value.isEmpty()) return null
        val endIndex = value.offsetByCodePoints(0, 1)
        return value.substring(0, endIndex)
    }

    private fun getGuideTextColor(keyData: KeyData): Int {
        return when (themeMode) {
            "custom" -> if (keyData.isSpecialKey) {
                customSpecialKeyTextColor
            } else {
                customKeyTextColor
            }

            else -> context.getColorFromAttr(R.attr.colorOnSurface)
        }
    }

    private fun applyGuideLabels(
        button: AutoSizeButton,
        keyData: KeyData,
        stringMap: Map<FlickDirection, String>
    ) {
        if (!flickGuideEnabled) {
            button.setFlickGuideLabels(null)
            return
        }

        if (!isSingleGuideCharacter(keyData.label)) {
            button.setFlickGuideLabels(null)
            return
        }

        button.setFlickGuideLabels(getGuideLabels(stringMap), getGuideTextColor(keyData))
    }

    private fun isSingleGuideCharacter(value: String): Boolean {
        return value.isNotEmpty() && value.codePointCount(0, value.length) == 1
    }

    private fun getScaledHorizontalInsetDp(baseInsetDp: Int): Int {
        val percent = keyWidthScalePercent.coerceIn(0, 200)
        val insetFactor = ((200f - percent) / 100f).coerceIn(0f, 2f)
        return (baseInsetDp * insetFactor).roundToInt()
    }

    private fun getScaledVerticalInsetDp(baseInsetDp: Int): Int {
        val percent = keyHeightScalePercent.coerceIn(0, 200)
        val insetFactor = ((200f - percent) / 100f).coerceIn(0f, 2f)
        return (baseInsetDp * insetFactor).roundToInt()
    }

    private fun createKeyView(keyData: KeyData): View {
        val baseInsets = if (keyData.isSpecialKey) {
            listOf(6, 12, 6, 6)
        } else {
            listOf(6, 9, 6, 9)
        }

        val leftInset = getScaledHorizontalInsetDp(baseInsets[0])
        val topInset = getScaledVerticalInsetDp(baseInsets[1])
        val rightInset = getScaledHorizontalInsetDp(baseInsets[2])
        val bottomInset = getScaledVerticalInsetDp(baseInsets[3])

        val isDarkTheme = context.isDarkThemeOn()
        val commonCornerRadius = dpToPx(8).toFloat()

        val keyView: View = if (keyData.isSpecialKey && keyData.drawableResId != null) {
            AppCompatImageButton(context).apply {
                isFocusable = false
                elevation = 0f
                setImageResource(keyData.drawableResId)
                contentDescription = keyData.label
                scaleType = android.widget.ImageView.ScaleType.MATRIX

                applyImageButtonSizing(this, keyData)

                val originalBg = ContextCompat.getDrawable(
                    context,
                    if (isDarkTheme) {
                        com.kazumaproject.core.R.drawable.ten_keys_side_bg_material
                    } else {
                        com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light
                    }
                )

                val insetBg = android.graphics.drawable.InsetDrawable(
                    originalBg,
                    leftInset,
                    topInset,
                    rightInset,
                    bottomInset
                )
                background = insetBg

                addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    updateImageButtonMatrix(this, keyData)
                }

                if (keyData.isHiLighted) {
                    isPressed = true
                }

                when (themeMode) {
                    "custom" -> {
                        if (customBorderEnable) {
                            setDrawableSolidColor(customSpecialKeyColor)
                            setColorFilter(customSpecialKeyTextColor)
                            setBorder(customBorderColor, borderWidth)
                        } else {
                            val neumorphDrawable = getDynamicNeumorphDrawable(
                                baseColor = customSpecialKeyColor,
                                radius = commonCornerRadius
                            )

                            val segmentedDrawable = SegmentedBackgroundDrawable(
                                label = "",
                                baseColor = Color.TRANSPARENT,
                                highlightColor = customSpecialKeyColor,
                                textColor = customSpecialKeyTextColor,
                                cornerRadius = commonCornerRadius
                            )

                            val layerDrawable =
                                LayerDrawable(arrayOf(neumorphDrawable, segmentedDrawable))
                            val innerInsetHorizontal = dpToPx(getScaledHorizontalInsetDp(2))
                            val innerInsetVertical = dpToPx(getScaledVerticalInsetDp(2))
                            layerDrawable.setLayerInset(
                                1,
                                innerInsetHorizontal,
                                innerInsetVertical,
                                innerInsetHorizontal,
                                innerInsetVertical
                            )
                            background = layerDrawable
                            setColorFilter(customSpecialKeyTextColor)
                        }
                    }
                }

                if (liquidGlassEnable) {
                    setDrawableAlpha(liquidGlassKeyAlphaEnable)
                }
            }
        } else {
            AutoSizeButton(context).apply {
                isFocusable = false
                isAllCaps = false
                elevation = 0f

                applyButtonText(this, keyData)

                val originalBg: Drawable? =
                    if (keyData.isSpecialKey) {
                        ContextCompat.getDrawable(
                            context,
                            if (isDarkTheme) {
                                com.kazumaproject.core.R.drawable.ten_keys_side_bg_material
                            } else {
                                com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light
                            }
                        )
                    } else if (keyData.keyType != KeyType.STANDARD_FLICK) {
                        ContextCompat.getDrawable(
                            context,
                            if (isDarkTheme) {
                                com.kazumaproject.core.R.drawable.ten_keys_center_bg_material
                            } else {
                                com.kazumaproject.core.R.drawable.ten_keys_center_bg_material_light
                            }
                        )
                    } else {
                        null
                    }

                originalBg?.let {
                    val insetBg = android.graphics.drawable.InsetDrawable(
                        it,
                        leftInset,
                        topInset,
                        rightInset,
                        bottomInset
                    )
                    background = insetBg
                }

                when (themeMode) {
                    "custom" -> {
                        if (customBorderEnable) {
                            setDrawableSolidColor(customKeyColor)
                            setTextColor(customKeyTextColor)
                            setBorder(customBorderColor, borderWidth)
                        } else {
                            val targetBaseColor =
                                if (keyData.isSpecialKey) customSpecialKeyColor else customKeyColor
                            val targetTextColor =
                                if (keyData.isSpecialKey) customSpecialKeyTextColor else customKeyTextColor
                            val targetHighlightColor =
                                if (keyData.isSpecialKey) {
                                    manipulateColor(customSpecialKeyColor, 1.2f)
                                } else {
                                    customSpecialKeyColor
                                }

                            val neumorphDrawable = getDynamicNeumorphDrawable(
                                baseColor = targetBaseColor,
                                radius = commonCornerRadius
                            )

                            val segmentedDrawable = SegmentedBackgroundDrawable(
                                label = "",
                                baseColor = Color.TRANSPARENT,
                                highlightColor = targetHighlightColor,
                                textColor = targetTextColor,
                                cornerRadius = commonCornerRadius
                            )

                            val layerDrawable =
                                LayerDrawable(arrayOf(neumorphDrawable, segmentedDrawable))
                            val inset = dpToPx(2)
                            layerDrawable.setLayerInset(1, inset, inset, inset, inset)

                            background = layerDrawable
                            setTextColor(targetTextColor)
                        }
                    }
                }

                if (liquidGlassEnable) {
                    setDrawableAlpha(liquidGlassKeyAlphaEnable)
                }
            }
        }

        val baseHorizontalMarginDp: Int
        val baseVerticalMarginDp: Int

        if (keyData.keyType == KeyType.STANDARD_FLICK) {
            baseHorizontalMarginDp = 6
            baseVerticalMarginDp = 9
        } else if (keyData.isSpecialKey) {
            baseHorizontalMarginDp = 3
            baseVerticalMarginDp = 6
        } else {
            baseHorizontalMarginDp = 4
            baseVerticalMarginDp = 6
        }

        val params = LayoutParams().apply {
            rowSpec = spec(keyData.row, keyData.rowSpan, FILL, 1f)
            columnSpec = spec(keyData.column, keyData.colSpan, FILL, 1f)
            width = 0
            height = 0

            setMargins(
                getScaledHorizontalMarginPx(baseHorizontalMarginDp),
                getScaledVerticalMarginPx(baseVerticalMarginDp),
                getScaledHorizontalMarginPx(baseHorizontalMarginDp),
                getScaledVerticalMarginPx(baseVerticalMarginDp)
            )
        }

        keyView.layoutParams = params
        return keyView
    }

    private fun getDynamicNeumorphDrawable(baseColor: Int, radius: Float): Drawable {
        val highlightColor = manipulateColor(baseColor, 1.2f)
        val shadowColor = manipulateColor(baseColor, 0.8f)

        val offset = dpToPx(4)
        val padding = dpToPx(2)

        val shadowDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(shadowColor)
        }

        val highlightDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(highlightColor)
        }

        val surfaceDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(baseColor)
        }

        val idleLayer = LayerDrawable(arrayOf(shadowDrawable, highlightDrawable, surfaceDrawable))
        idleLayer.setLayerInset(0, offset, offset, 0, 0)
        idleLayer.setLayerInset(1, 0, 0, offset, offset)
        idleLayer.setLayerInset(2, padding, padding, padding, padding)

        val pressedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(manipulateColor(baseColor, 0.95f))
        }

        val pressedLayer = LayerDrawable(arrayOf(pressedDrawable))
        pressedLayer.setLayerInset(0, padding, padding, padding, padding)

        val stateList = android.graphics.drawable.StateListDrawable()
        stateList.addState(intArrayOf(android.R.attr.state_pressed), pressedLayer)
        stateList.addState(intArrayOf(), idleLayer)

        return stateList
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachKeyBehavior(keyView: View, keyData: KeyData): Any? {
        val layout = currentLayout ?: return null

        when (keyData.keyType) {
            KeyType.CIRCULAR_FLICK -> {
                val flickKeyMapsList = layout.flickKeyMaps[keyData.label]
                Log.d("FlickKeyboardView KeyType.CIRCULAR_FLICK", "$flickKeyMapsList")
                if (!flickKeyMapsList.isNullOrEmpty()) {
                    val controller = CustomAngleFlickController(context, flickSensitivity).apply {
                        setLongPressTimeout(longPressTimeout)
                        val secondaryColor =
                            context.getColorFromAttr(R.attr.colorSecondaryContainer)
                        val surfaceContainerLow =
                            context.getColorFromAttr(R.attr.colorSurfaceContainerLow)
                        val surfaceContainerHighest =
                            context.getColorFromAttr(R.attr.colorSurfaceContainerHighest)
                        val textColor =
                            context.getColor(com.kazumaproject.core.R.color.keyboard_icon_color)

                        val dynamicColorTheme = when (themeMode) {
                            "default" -> FlickPopupColorTheme(
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

                            "custom" -> FlickPopupColorTheme(
                                segmentColor = customSpecialKeyColor,
                                segmentHighlightGradientStartColor = customSpecialKeyColor,
                                segmentHighlightGradientEndColor = customSpecialKeyColor,
                                centerGradientStartColor = manipulateColor(
                                    customSpecialKeyColor,
                                    1.2f
                                ),
                                centerGradientEndColor = manipulateColor(
                                    customSpecialKeyColor,
                                    0.8f
                                ),
                                centerHighlightGradientStartColor = manipulateColor(
                                    customSpecialKeyColor,
                                    1.2f
                                ),
                                centerHighlightGradientEndColor = manipulateColor(
                                    customSpecialKeyColor,
                                    0.8f
                                ),
                                separatorColor = customSpecialKeyTextColor,
                                textColor = customSpecialKeyTextColor
                            )

                            else -> FlickPopupColorTheme(
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
                        }

                        setPopupColors(dynamicColorTheme)

                        this.listener = object : CustomAngleFlickController.FlickListener {
                            override fun onPress(character: String) {
                                notifyTextPress(character)
                            }

                            override fun onFlick(direction: FlickDirection, character: String) {
                                if (character.isNotEmpty()) {
                                    this@FlickKeyboardView.listener?.onAction(
                                        KeyAction.Text(character),
                                        isFlick = direction != FlickDirection.TAP
                                    )
                                }
                            }

                            override fun onStateChanged(
                                view: View,
                                newMap: Map<FlickDirection, String>
                            ) {
                                if (view is AutoSizeButton) {
                                    applyGuideLabels(view, keyData, newMap)
                                }
                            }

                            override fun onFlickDirectionChanged(newDirection: FlickDirection) {
                                this@FlickKeyboardView.listener?.onFlickDirectionChanged(
                                    newDirection
                                )
                            }
                        }

                        val stringMaps = flickKeyMapsList.map(::extractInputMap)

                        if (keyView is AutoSizeButton) {
                            stringMaps.firstOrNull()?.let { firstMap ->
                                applyGuideLabels(keyView, keyData, firstMap)
                            } ?: keyView.setFlickGuideLabels(null)
                        }

                        attach(keyView, stringMaps)

                        val newCenter = 64f * circularViewScale
                        val newOrbit = 170f * circularViewScale
                        val newTextSize = 55f * circularViewScale
                        setPopupViewSize(
                            orbit = newOrbit,
                            centerRadius = newCenter,
                            textSize = newTextSize
                        )
                    }

                    val ranges = customAngleAndRange.ifEmpty {
                        mapOf(
                            FlickDirection.UP to Pair(225f, 90f),
                            FlickDirection.UP_RIGHT_FAR to Pair(315f, 90f),
                            FlickDirection.DOWN to Pair(45f, 90f),
                            FlickDirection.UP_LEFT_FAR to Pair(135f, 90f)
                        )
                    }

                    controller.setFlickRanges(ranges)
                    flickControllers.add(controller)
                    return controller
                }
            }

            KeyType.CROSS_FLICK -> {
                val flickActionMap = layout.flickKeyMaps[keyData.label]?.firstOrNull()
                Log.d("FlickKeyboardView KeyType.CROSS_FLICK", "$flickActionMap")
                if (flickActionMap != null) {
                    val controller = CrossFlickInputController(context).apply {
                        setLongPressTimeout(longPressTimeout)
                        this.listener = object : CrossFlickInputController.CrossFlickListener {
                            override fun onPress(action: KeyAction) {
                                this@FlickKeyboardView.listener?.onPress(action)
                            }

                            override fun onFlick(action: KeyAction, isFlick: Boolean) {
                                this@FlickKeyboardView.listener?.onAction(action, isFlick)
                            }

                            override fun onFlickLongPress(action: KeyAction) {
                                if (action !is KeyAction.Text) {
                                    this@FlickKeyboardView.listener?.onFlickActionLongPress(action)
                                }
                            }

                            override fun onFlickUpAfterLongPress(action: KeyAction, isFlick: Boolean) {
                                if (action !is KeyAction.Text) {
                                    this@FlickKeyboardView.listener?.onFlickActionUpAfterLongPress(
                                        action, isFlick = isFlick
                                    )
                                }
                            }
                        }

                        attach(keyView, flickActionMap)
                    }

                    when (themeMode) {
                        "custom" -> {
                            controller.setPopupColors(
                                backgroundColor = customSpecialKeyColor,
                                highlightedColor = manipulateColor(customSpecialKeyColor, 1.2f),
                                textColor = customSpecialKeyTextColor
                            )
                        }
                    }

                    crossFlickControllers.add(controller)
                    return controller
                }
            }

            KeyType.STANDARD_FLICK -> {
                val flickActionMap = layout.flickKeyMaps[keyData.label]?.firstOrNull()
                if (flickActionMap != null && keyView is Button) {
                    val label = keyData.label
                    val isDarkTheme = context.isDarkThemeOn()

                    val segmentedDrawable: SegmentedBackgroundDrawable

                    if (themeMode == "custom") {
                        if (customBorderEnable) {
                            keyView.backgroundTintList = null

                            val baseCorner = dpToPx(8).toFloat()
                            val baseWithBorder = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = baseCorner
                                setColor(customKeyColor)
                                setStroke(borderWidth, customBorderColor)
                            }

                            segmentedDrawable = SegmentedBackgroundDrawable(
                                label = label,
                                baseColor = Color.TRANSPARENT,
                                highlightColor = manipulateColor(customKeyColor, 1.2f),
                                textColor = customKeyTextColor,
                                cornerRadius = baseCorner
                            )

                            val layer = LayerDrawable(arrayOf(baseWithBorder, segmentedDrawable))
                            val inset = dpToPx(2)
                            layer.setLayerInset(1, inset, inset, inset, inset)

                            keyView.background = layer
                            keyView.setTextColor(Color.TRANSPARENT)
                        } else {
                            val neumorphDrawable = getDynamicNeumorphDrawable(
                                baseColor = customKeyColor,
                                radius = dpToPx(8).toFloat()
                            )

                            segmentedDrawable = SegmentedBackgroundDrawable(
                                label = label,
                                baseColor = Color.TRANSPARENT,
                                highlightColor = manipulateColor(customKeyColor, 1.2f),
                                textColor = customKeyTextColor,
                                cornerRadius = dpToPx(8).toFloat()
                            )

                            val layerDrawable =
                                LayerDrawable(arrayOf(neumorphDrawable, segmentedDrawable))
                            val inset = dpToPx(2)
                            layerDrawable.setLayerInset(1, inset, inset, inset, inset)
                            keyView.background = layerDrawable
                            keyView.setTextColor(Color.TRANSPARENT)
                        }
                    } else {
                        val keyBaseColor =
                            if (isDarkTheme) {
                                context.getColorFromAttr(R.attr.colorSurfaceContainerHighest)
                            } else {
                                context.getColorFromAttr(R.attr.colorSurface)
                            }

                        val keyHighlightColor =
                            context.getColorFromAttr(R.attr.colorSecondaryContainer)
                        val keyTextColor =
                            context.getColorFromAttr(R.attr.colorOnSurface)

                        segmentedDrawable = SegmentedBackgroundDrawable(
                            label = label,
                            baseColor = keyBaseColor,
                            highlightColor = keyHighlightColor,
                            textColor = keyTextColor,
                            cornerRadius = 20f
                        )

                        keyView.background = segmentedDrawable
                        keyView.setTextColor(Color.TRANSPARENT)
                    }

                    if (liquidGlassEnable) {
                        keyView.setDrawableAlpha(liquidGlassKeyAlphaEnable)
                    }

                    val controller = StandardFlickInputController(context).apply {
                        this.listener =
                            object : StandardFlickInputController.StandardFlickListener {
                                override fun onPress(character: String) {
                                    notifyTextPress(character)
                                }

                                override fun onFlick(character: String) {
                                    this@FlickKeyboardView.listener?.onAction(
                                        KeyAction.Text(character),
                                        isFlick = true
                                    )
                                }
                            }

                        val stringMap = extractInputMap(flickActionMap)

                        if (keyView is AutoSizeButton) {
                            applyGuideLabels(keyView, keyData, stringMap)
                        }

                        val secondaryColor =
                            context.getColorFromAttr(R.attr.colorSecondaryContainer)
                        val surfaceContainerLow =
                            context.getColorFromAttr(R.attr.colorSurfaceContainerLow)
                        val surfaceContainerHighest =
                            if (isDarkTheme) {
                                context.getColorFromAttr(R.attr.colorSurfaceContainerHighest)
                            } else {
                                context.getColorFromAttr(R.attr.colorSurface)
                            }
                        val textColor =
                            context.getColor(com.kazumaproject.core.R.color.keyboard_icon_color)

                        val dynamicColorTheme = when (themeMode) {
                            "default" -> FlickPopupColorTheme(
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

                            "custom" -> FlickPopupColorTheme(
                                segmentColor = customSpecialKeyColor,
                                segmentHighlightGradientStartColor = customSpecialKeyColor,
                                segmentHighlightGradientEndColor = customSpecialKeyColor,
                                centerGradientStartColor = manipulateColor(
                                    customSpecialKeyColor,
                                    1.2f
                                ),
                                centerGradientEndColor = manipulateColor(
                                    customSpecialKeyColor,
                                    0.8f
                                ),
                                centerHighlightGradientStartColor = manipulateColor(
                                    customSpecialKeyColor,
                                    1.2f
                                ),
                                centerHighlightGradientEndColor = manipulateColor(
                                    customSpecialKeyColor,
                                    0.8f
                                ),
                                separatorColor = customSpecialKeyTextColor,
                                textColor = customSpecialKeyTextColor
                            )

                            else -> FlickPopupColorTheme(
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
                        }

                        setPopupColors(dynamicColorTheme)
                        attach(keyView, stringMap, segmentedDrawable)
                    }

                    standardFlickControllers.add(controller)
                    return controller
                }
            }

            KeyType.PETAL_FLICK -> {
                val flickActionMap = layout.flickKeyMaps[keyData.keyId]?.firstOrNull()
                    ?: layout.flickKeyMaps[keyData.label]?.firstOrNull()
                Log.d("FlickKeyboardView KeyType.PETAL_FLICK", "$flickActionMap")
                if (flickActionMap != null) {
                    val controller = CrossFlickInputController(context, flickSensitivity).apply {
                        setLongPressTimeout(longPressTimeout)
                        val isDarkTheme = context.isDarkThemeOn()
                        val secondaryColor =
                            context.getColorFromAttr(R.attr.colorSecondaryContainer)
                        val surfaceContainerLow =
                            context.getColorFromAttr(R.attr.colorSurfaceContainerLow)
                        val surfaceContainerHighest =
                            if (isDarkTheme) {
                                context.getColorFromAttr(R.attr.colorSurfaceContainerHighest)
                            } else {
                                context.getColorFromAttr(R.attr.colorSurface)
                            }
                        val textColor =
                            context.getColor(com.kazumaproject.core.R.color.keyboard_icon_color)

                        val dynamicColorTheme = when (themeMode) {
                            "default" -> FlickPopupColorTheme(
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

                            "custom" -> FlickPopupColorTheme(
                                segmentColor = customSpecialKeyColor,
                                segmentHighlightGradientStartColor = customSpecialKeyColor,
                                segmentHighlightGradientEndColor = customSpecialKeyColor,
                                centerGradientStartColor = manipulateColor(
                                    customSpecialKeyColor,
                                    1.2f
                                ),
                                centerGradientEndColor = manipulateColor(
                                    customSpecialKeyColor,
                                    0.8f
                                ),
                                centerHighlightGradientStartColor = manipulateColor(
                                    customSpecialKeyColor,
                                    1.2f
                                ),
                                centerHighlightGradientEndColor = manipulateColor(
                                    customSpecialKeyColor,
                                    0.8f
                                ),
                                separatorColor = customSpecialKeyTextColor,
                                textColor = customSpecialKeyTextColor
                            )

                            else -> FlickPopupColorTheme(
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
                        }

                        setPopupColors(dynamicColorTheme)
                        this.listener = object : CrossFlickInputController.CrossFlickListener {
                            override fun onPress(action: KeyAction) {
                                when (action) {
                                    is KeyAction.Text -> notifyTextPress(action.text)
                                    else -> this@FlickKeyboardView.listener?.onPress(action)
                                }
                            }

                            override fun onFlick(action: KeyAction, isFlick: Boolean) {
                                this@FlickKeyboardView.listener?.onAction(action, isFlick)
                            }

                            override fun onFlickLongPress(action: KeyAction) {
                                if (action !is KeyAction.Text) {
                                    this@FlickKeyboardView.listener?.onFlickActionLongPress(action)
                                }
                            }

                            override fun onFlickUpAfterLongPress(action: KeyAction, isFlick: Boolean) {
                                if (action !is KeyAction.Text) {
                                    this@FlickKeyboardView.listener?.onFlickActionUpAfterLongPress(
                                        action,
                                        isFlick
                                    )
                                }
                            }
                        }

                        val stringMap = extractInputMap(flickActionMap)
                        val longPressStringMap = layout.longPressFlickKeyMaps[keyData.keyId]
                            ?: layout.longPressFlickKeyMaps[keyData.label]
                            ?: emptyMap()

                        if (keyView is AutoSizeButton) {
                            applyGuideLabels(keyView, keyData, stringMap)
                        }

                        attachText(keyView, stringMap, longPressStringMap)
                    }

                    crossFlickControllers.add(controller)
                    return controller
                }
            }

            KeyType.NORMAL -> {
                keyData.action?.let { action ->
                    Log.d("FlickKeyboardView KeyType.NORMAL", "key data: $keyData")

                    var isLongPressTriggered = false

                    keyView.setOnClickListener {
                        val currentAction =
                            dynamicKeyMap[keyData.keyId]?.keyData?.action ?: action
                        Log.d("FlickKeyboardView KeyType.NORMAL", "currentAction: $currentAction")
                        this@FlickKeyboardView.listener?.onAction(
                            currentAction,
                            isFlick = false
                        )
                    }

                    keyView.setOnLongClickListener {
                        val currentAction =
                            dynamicKeyMap[keyData.keyId]?.keyData?.action ?: action
                        isLongPressTriggered = true
                        this@FlickKeyboardView.listener?.onActionLongPress(currentAction)
                        true
                    }

                    keyView.setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            val currentAction =
                                dynamicKeyMap[keyData.keyId]?.keyData?.action ?: action
                            this@FlickKeyboardView.listener?.onPress(currentAction)
                        }
                        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                            if (isLongPressTriggered) {
                                val currentAction =
                                    dynamicKeyMap[keyData.keyId]?.keyData?.action ?: action
                                this@FlickKeyboardView.listener?.onActionUpAfterLongPress(
                                    currentAction
                                )
                                isLongPressTriggered = false
                            }
                        }
                        false
                    }
                }
                return null
            }

            KeyType.TWO_STEP_FLICK -> {
                val twoStepMap = layout.twoStepFlickKeyMaps[keyData.keyId]
                    ?: layout.twoStepFlickKeyMaps[keyData.label]
                val twoStepLongPressMap = layout.twoStepLongPressKeyMaps[keyData.keyId]
                    ?: layout.twoStepLongPressKeyMaps[keyData.label]

                if (twoStepMap != null) {
                    val controller = TfbiInputController(
                        context,
                        flickSensitivity = flickSensitivity.toFloat()
                    ).apply {
                        setLongPressTimeout(longPressTimeout)
                        this.listener = object : TfbiInputController.TfbiListener {
                            override fun onPress(
                                first: TfbiFlickDirection,
                                second: TfbiFlickDirection
                            ) {
                                notifyTextPress(twoStepMap[first]?.get(second) ?: "")
                            }

                            override fun onFlick(
                                first: TfbiFlickDirection,
                                second: TfbiFlickDirection
                            ) {
                                val character = twoStepMap[first]?.get(second) ?: ""
                                Log.d(
                                    "FlickKeyboardView KeyType.TWO_STEP_FLICK",
                                    "$character $first $second"
                                )
                                if (character.isNotEmpty()) {
                                    this@FlickKeyboardView.listener?.onAction(
                                        KeyAction.Text(character),
                                        isFlick = !(first == TfbiFlickDirection.TAP && second == TfbiFlickDirection.TAP)
                                    )
                                }
                            }

                            override fun onLongPressFlick(
                                first: TfbiFlickDirection,
                                second: TfbiFlickDirection
                            ): Boolean {
                                val output = twoStepLongPressMap?.get(first)?.get(second).orEmpty()
                                if (output.isEmpty()) return false

                                this@FlickKeyboardView.listener?.onAction(
                                    KeyAction.Text(output),
                                    isFlick = !(first == TfbiFlickDirection.TAP &&
                                            second == TfbiFlickDirection.TAP)
                                )
                                return true
                            }
                        }

                        attach(
                            view = keyView,
                            provider = { first, second ->
                                twoStepMap[first]?.get(second) ?: ""
                            },
                            longPressProvider = { first, second ->
                                twoStepLongPressMap?.get(first)?.get(second).orEmpty()
                            }
                        )
                    }

                    when (themeMode) {
                        "custom" -> {
                            controller.setPopupColors(
                                backgroundColor = customSpecialKeyColor,
                                highlightedColor = manipulateColor(customSpecialKeyColor, 1.2f),
                                textColor = customSpecialKeyTextColor
                            )
                        }
                    }

                    tfbiControllers.add(controller)
                    return controller
                }
            }

            KeyType.STICKY_TWO_STEP_FLICK -> {
                val twoStepMap = layout.twoStepFlickKeyMaps[keyData.label]
                if (twoStepMap != null) {
                    val controller = TfbiStickyFlickController(
                        context,
                        flickSensitivity = flickSensitivity.toFloat()
                    ).apply {
                        this.listener = object : TfbiStickyFlickController.TfbiListener {
                            override fun onPress(
                                first: TfbiFlickDirection,
                                second: TfbiFlickDirection
                            ) {
                                notifyTextPress(twoStepMap[first]?.get(second) ?: "")
                            }

                            override fun onFlick(
                                first: TfbiFlickDirection,
                                second: TfbiFlickDirection
                            ) {
                                val character = twoStepMap[first]?.get(second) ?: ""
                                Log.d(
                                    "FlickKeyboardView KeyType.STICKY_TWO_STEP_FLICK",
                                    "$character $first $second"
                                )
                                if (character.isNotEmpty()) {
                                    this@FlickKeyboardView.listener?.onAction(
                                        KeyAction.Text(character),
                                        isFlick = !(first == TfbiFlickDirection.TAP && second == TfbiFlickDirection.TAP)
                                    )
                                }
                            }
                        }

                        attach(
                            view = keyView,
                            provider = { first, second ->
                                twoStepMap[first]?.get(second) ?: ""
                            }
                        )
                    }

                    stickyTfbiControllers.add(controller)
                    return controller
                }
            }

            KeyType.HIERARCHICAL_FLICK -> {
                val statefulNode = layout.hierarchicalFlickMaps[keyData.label]

                if (statefulNode != null) {
                    Log.d(
                        "AttachBehavior",
                        "-> Attaching TfbiHierarchicalFlickController for ${keyData.label}"
                    )

                    val controller = TfbiHierarchicalFlickController(
                        context,
                        flickSensitivity = flickSensitivity.toFloat()
                    ).apply {
                        this.listener = object : TfbiHierarchicalFlickController.TfbiListener {
                            override fun onPress(character: String) {
                                notifyTextPress(character)
                            }

                            override fun onFlick(character: String) {
                                Log.d(
                                    "FlickKeyboardView KeyType.HIERARCHICAL_FLICK",
                                    "Char: $character"
                                )
                                if (character.isNotEmpty()) {
                                    this@FlickKeyboardView.listener?.onAction(
                                        KeyAction.Text(character),
                                        isFlick = true
                                    )
                                }
                            }

                            override fun onModeChanged(newLabel: String) {
                                Log.d(
                                    "FlickKeyboardView",
                                    "onModeChanged: keyId=${keyData.keyId}, newLabel=$newLabel"
                                )

                                keyData.keyId?.let { id ->
                                    dynamicKeyMap[id]?.let { info ->
                                        info.keyData = info.keyData.copy(label = newLabel)
                                    }
                                }

                                val newVisualKeyData = keyData.copy(label = newLabel)
                                updateKeyVisuals(keyView, newVisualKeyData)
                            }
                        }

                        attach(keyView, statefulNode)
                    }

                    when (themeMode) {
                        "custom" -> {
                            controller.setPopupColors(
                                backgroundColor = customSpecialKeyColor,
                                highlightedColor = manipulateColor(customSpecialKeyColor, 1.2f),
                                textColor = customSpecialKeyTextColor
                            )
                        }
                    }

                    hierarchicalTfbiControllers.add(controller)
                    return controller
                } else {
                    Log.e(
                        "AttachBehavior",
                        "-> FAILED HIERARCHICAL_FLICK: statefulNode is NULL for key '${keyData.label}'"
                    )
                }
            }
        }

        return null
    }

    private fun notifyTextPress(character: String) {
        if (character.isNotEmpty()) {
            listener?.onPress(KeyAction.Text(character))
        }
    }

    private fun detachKeyBehavior(controller: Any?) {
        when (controller) {
            is CustomAngleFlickController -> {
                controller.cancel()
                flickControllers.remove(controller)
            }

            is CrossFlickInputController -> {
                controller.cancel()
                crossFlickControllers.remove(controller)
            }

            is StandardFlickInputController -> {
                controller.cancel()
                standardFlickControllers.remove(controller)
            }

            is TfbiInputController -> {
                controller.cancel()
                tfbiControllers.remove(controller)
            }

            is TfbiStickyFlickController -> {
                controller.cancel()
                stickyTfbiControllers.remove(controller)
            }

            is TfbiHierarchicalFlickController -> {
                controller.cancel()
                hierarchicalTfbiControllers.remove(controller)
            }
        }
    }

    private fun updateKeyVisuals(view: View, keyData: KeyData) {
        when (view) {
            is AppCompatImageButton -> {
                keyData.drawableResId?.let { view.setImageResource(it) }
                applyImageButtonSizing(view, keyData)
                view.contentDescription = keyData.label
                view.isPressed = keyData.isHiLighted
            }

            is AutoSizeButton -> {
                applyButtonText(view, keyData)
                view.isPressed = keyData.isHiLighted
            }
        }
    }

    private val motionTargets = mutableMapOf<Int, View>()
    private val pointerDownTime = mutableMapOf<Int, Long>()
    private val TAG = "FlickKeyboardViewTouch"

    private fun findTargetView(x: Float, y: Float): View? {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.getHitRect(hitRect)
            if (hitRect.contains(x.toInt(), y.toInt())) {
                return child
            }
        }

        var nearestChild: View? = null
        var minDistance = Double.MAX_VALUE

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val childCenterX = child.left + child.width / 2f
            val childCenterY = child.top + child.height / 2f
            val distance = sqrt((x - childCenterX).pow(2) + (y - childCenterY).pow(2))

            if (distance < minDistance) {
                minDistance = distance.toDouble()
                nearestChild = child
            }
        }

        return nearestChild
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        Log.d(TAG, "onInterceptTouchEvent: ${MotionEvent.actionToString(action)} $")

        if (action == MotionEvent.ACTION_DOWN) {
            Log.d(TAG, "-> Intercepting gesture from ACTION_DOWN. Returning true.")
            return true
        }

        if (motionTargets.isNotEmpty()) {
            return true
        }

        return super.onInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)

        if (isCursorMode) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cursorInitialX = event.x
                    cursorInitialY = event.y
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val threshold = 30f
                    val currentX = event.x
                    val currentY = event.y

                    val dx = currentX - cursorInitialX
                    val dy = currentY - cursorInitialY

                    if (abs(dx) > abs(dy) && abs(dx) > threshold) {
                        val action2 =
                            if (dx < 0f) KeyAction.MoveCursorLeft else KeyAction.MoveCursorRight
                        listener?.onAction(action2, false)
                        cursorInitialX = currentX
                        cursorInitialY = currentY
                    } else if (abs(dy) > abs(dx) && abs(dy) > threshold) {
                        val action2 =
                            if (dy < 0f) KeyAction.MoveCursorUp else KeyAction.MoveCursorDown
                        listener?.onAction(action2, false)
                        cursorInitialX = currentX
                        cursorInitialY = currentY
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    setCursorMode(false)
                    crossFlickControllers.forEach { it.dismissAllPopups() }
                    clearSpaceKeyPressedState()
                    motionTargets.clear()
                    pointerDownTime.clear()
                    return true
                }
            }
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                motionTargets.clear()
                pointerDownTime.clear()

                pointerDownTime[pointerId] = event.downTime
                val x = event.x
                val y = event.y
                val targetView = findTargetView(x, y)

                targetView?.let {
                    motionTargets[pointerId] = it

                    val newEvent = MotionEvent.obtain(
                        event.downTime,
                        event.eventTime,
                        MotionEvent.ACTION_DOWN,
                        x,
                        y,
                        event.metaState
                    )

                    Log.d("FlickKeyboardView MotionEvent.ACTION_DOWN", "$newEvent")

                    newEvent.offsetLocation(-it.left.toFloat(), -it.top.toFloat())
                    it.dispatchTouchEvent(newEvent)
                    newEvent.recycle()
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (visibility != View.VISIBLE) {
                    return false
                }

                motionTargets.keys.toList().forEach { existingPointerId ->
                    val target = motionTargets[existingPointerId]
                    val downTime = pointerDownTime[existingPointerId]

                    Log.d(
                        "FlickKeyboardView",
                        "MotionEvent.ACTION_POINTER_DOWN called ${event.metaState} $target $downTime"
                    )

                    if (target != null && downTime != null) {
                        val existingPointerIndex = event.findPointerIndex(existingPointerId)
                        if (existingPointerIndex != -1) {
                            val x = event.getX(existingPointerIndex)
                            val y = event.getY(existingPointerIndex)

                            val upEvent = MotionEvent.obtain(
                                downTime,
                                event.eventTime,
                                MotionEvent.ACTION_UP,
                                x,
                                y,
                                event.metaState
                            )
                            upEvent.offsetLocation(-target.left.toFloat(), -target.top.toFloat())
                            target.dispatchTouchEvent(upEvent)
                            upEvent.recycle()
                        }
                    }

                    val matchingEntry = dynamicKeyMap.entries.find { it.value.view == target }
                    if (matchingEntry != null) {
                        val keyInfo = matchingEntry.value
                        Log.d(
                            TAG,
                            "ACTION_POINTER_DOWN: First finger (ID: $existingPointerId) is on a dynamic key. KeyInfo: $keyInfo"
                        )
                        if (keyInfo.keyData.action == KeyAction.InputText(text = "^_^") ||
                            keyInfo.keyData.keyId == "switch_next_ime"
                        ) {
                            return true
                        }
                    } else {
                        Log.d(
                            TAG,
                            "ACTION_POINTER_DOWN: First finger (ID: $existingPointerId) is on a non-dynamic key."
                        )
                    }
                }

                motionTargets.clear()
                pointerDownTime.clear()

                val newPointerId = event.getPointerId(pointerIndex)
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)

                pointerDownTime[newPointerId] = event.eventTime
                val targetView = findTargetView(x, y)

                targetView?.let {
                    motionTargets[newPointerId] = it
                    val newEvent = MotionEvent.obtain(
                        event.eventTime,
                        event.eventTime,
                        MotionEvent.ACTION_DOWN,
                        x,
                        y,
                        event.metaState
                    )

                    Log.d(
                        "FlickKeyboardView",
                        "MotionEvent.ACTION_POINTER_DOWN called new $newPointerId $newEvent"
                    )

                    newEvent.offsetLocation(-it.left.toFloat(), -it.top.toFloat())
                    it.dispatchTouchEvent(newEvent)
                    newEvent.recycle()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pId = event.getPointerId(i)
                    val target = motionTargets[pId]
                    val downTime = pointerDownTime[pId]

                    if (target != null && downTime != null) {
                        val x = event.getX(i)
                        val y = event.getY(i)

                        val newEvent = MotionEvent.obtain(
                            downTime,
                            event.eventTime,
                            MotionEvent.ACTION_MOVE,
                            x,
                            y,
                            event.metaState
                        )
                        newEvent.offsetLocation(-target.left.toFloat(), -target.top.toFloat())
                        target.dispatchTouchEvent(newEvent)
                        newEvent.recycle()
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (visibility != View.VISIBLE) {
                    return false
                }

                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)

                Log.d(
                    "FlickKeyboardView",
                    "ACTION_POINTER_UP: pointerId=$pointerId, index=$pointerIndex"
                )

                motionTargets[pointerId]?.let { target ->
                    val downTime = pointerDownTime[pointerId]!!

                    Log.d("FlickKeyboardView", "ACTION_POINTER_UP: Found target! $target")

                    val newEvent = MotionEvent.obtain(
                        downTime,
                        event.eventTime,
                        MotionEvent.ACTION_UP,
                        x,
                        y,
                        event.metaState
                    )

                    Log.d(
                        "FlickKeyboardView",
                        "ACTION_POINTER_UP: Dispatching fake ACTION_UP to target. Event: $newEvent"
                    )

                    newEvent.offsetLocation(-target.left.toFloat(), -target.top.toFloat())
                    target.dispatchTouchEvent(newEvent)
                    newEvent.recycle()
                } ?: run {
                    Log.e(
                        "FlickKeyboardView",
                        "ACTION_POINTER_UP: No target found for pointerId=$pointerId"
                    )
                }

                motionTargets.remove(pointerId)
                pointerDownTime.remove(pointerId)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                val actionToDispatch =
                    if (action == MotionEvent.ACTION_UP) MotionEvent.ACTION_UP else MotionEvent.ACTION_CANCEL

                motionTargets[pointerId]?.let { target ->
                    val downTime = pointerDownTime[pointerId]!!
                    val newEvent = MotionEvent.obtain(
                        downTime,
                        event.eventTime,
                        actionToDispatch,
                        x,
                        y,
                        event.metaState
                    )

                    Log.d("FlickKeyboardView MotionEvent.ACTION_UP", "$downTime $newEvent")
                    newEvent.offsetLocation(-target.left.toFloat(), -target.top.toFloat())
                    target.dispatchTouchEvent(newEvent)
                    newEvent.recycle()
                }

                motionTargets.clear()
                pointerDownTime.clear()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        flickControllers.forEach { it.cancel() }
        crossFlickControllers.forEach { it.cancel() }
        standardFlickControllers.forEach { it.cancel() }
        tfbiControllers.forEach { it.cancel() }
        stickyTfbiControllers.forEach { it.cancel() }
        hierarchicalTfbiControllers.forEach { it.cancel() }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
    }

    private fun Context.getColorFromAttr(@AttrRes attrRes: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attrRes, typedValue, true)
        return ContextCompat.getColor(this, typedValue.resourceId)
    }

    private fun spToPx(sp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            resources.displayMetrics
        ).toInt()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun clearSpaceKeyPressedState() {
        for (i in 0 until childCount) {
            val child = getChildAt(i)

            val isKuhakuKey = when (child) {
                is AutoSizeButton -> child.text?.toString() == "空白"
                is AppCompatImageButton -> child.contentDescription?.toString() == "空白"
                else -> false
            }

            if (isKuhakuKey) {
                child.isPressed = false
                child.isSelected = false
                child.refreshDrawableState()
            }
        }
    }
}
