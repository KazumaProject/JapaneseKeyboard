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
import android.os.Build
import android.os.SystemClock
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
import android.widget.Space
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.R
import com.kazumaproject.core.data.popup.TfbiFlickStartPositionMode
import com.kazumaproject.core.data.popup.FlickPopupViewStyleSet
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.data.popup.TfbiPopupPresentationMode
import com.kazumaproject.core.domain.extensions.isDarkThemeOn
import com.kazumaproject.core.domain.extensions.setBorder
import com.kazumaproject.core.domain.extensions.setDrawableAlpha
import com.kazumaproject.core.domain.extensions.setDrawableSolidColor
import com.kazumaproject.core.domain.flick.DelegatingRuntimeGestureSettingsSource
import com.kazumaproject.core.domain.flick.FlickGestureMath
import com.kazumaproject.core.domain.flick.FlickThresholdShape
import com.kazumaproject.core.domain.flick.GestureSessionConfig
import com.kazumaproject.core.domain.flick.GestureSessionConfigSource
import com.kazumaproject.core.domain.flick.MutableRuntimeGestureSettingsSource
import com.kazumaproject.core.domain.flick.RuntimeGestureSettings
import com.kazumaproject.core.domain.flick.RuntimeGestureSettingsSource
import com.kazumaproject.custom_keyboard.controller.CenterGuideFlickInputController
import com.kazumaproject.custom_keyboard.controller.CrossFlickInputController
import com.kazumaproject.custom_keyboard.controller.CustomAngleFlickController
import com.kazumaproject.custom_keyboard.controller.CancellableTask
import com.kazumaproject.custom_keyboard.controller.DoubleTapActionDispatcher
import com.kazumaproject.custom_keyboard.controller.FlickLongPressInputController
import com.kazumaproject.custom_keyboard.controller.StandardFlickInputController
import com.kazumaproject.custom_keyboard.controller.TapTaskScheduler
import com.kazumaproject.custom_keyboard.controller.TapLongPressInputController
import com.kazumaproject.custom_keyboard.controller.TfbiHierarchicalFlickController
import com.kazumaproject.custom_keyboard.controller.TfbiStickyFlickController
import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyCharacterCase
import com.kazumaproject.custom_keyboard.data.KeyIconResolver
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyVisualStyleResolver
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.ResolvedSumireSpecialKeyAction
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection
import com.kazumaproject.custom_keyboard.data.TfbiFlickNode
import com.kazumaproject.custom_keyboard.data.applyTapOverrideDisplayForDynamicSumireSpecialKey
import com.kazumaproject.custom_keyboard.data.buildSumireSpecialKeyDisplayActionMap
import com.kazumaproject.custom_keyboard.data.buildEvenCircularRanges
import com.kazumaproject.custom_keyboard.data.dispatchResolvedSumireSpecialKeyAction
import com.kazumaproject.custom_keyboard.data.dispatchSumireSpecialKeyRuntimeAction
import com.kazumaproject.custom_keyboard.data.effectiveDoubleTapBinding
import com.kazumaproject.custom_keyboard.data.refreshSumireSpecialKeyTap
import com.kazumaproject.custom_keyboard.data.toCircularFlickKeyMaps
import com.kazumaproject.custom_keyboard.data.toLegacyFlickDirection
import com.kazumaproject.custom_keyboard.data.toSumireSpecialKeyDirectionOrNull
import com.kazumaproject.custom_keyboard.layout.SegmentedBackgroundDrawable
import com.kazumaproject.core.domain.flick.FlickTextPreviewEmitter
import com.kazumaproject.core.domain.flick.FlickTextPreviewListener
import com.kazumaproject.core.domain.flick.FlickTextSelection
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

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
        fun onLongPressActionCanceled(action: KeyAction) {}
    }

    private companion object {
        private const val SPECIAL_KEY_BASE_TEXT_SIZE_SP = 16f
        private const val SPECIAL_ICON_TO_TEXT_RATIO = 1.6f
        private const val INPUT_MODE_SWITCH_ICON_SIZE_MULTIPLIER = 1.65f
        private const val DOUBLE_TAP_MIN_INTERVAL_MILLIS = 40L
    }

    private var listener: OnKeyboardActionListener? = null
    private val flickTextPreviewEmitter = FlickTextPreviewEmitter()
    private var previewKeyData: KeyData? = null
    private val flickControllers = mutableListOf<CustomAngleFlickController>()
    private val crossFlickControllers = mutableListOf<CrossFlickInputController>()
    private val centerGuideFlickControllers = mutableListOf<CenterGuideFlickInputController>()
    private val standardFlickControllers = mutableListOf<StandardFlickInputController>()
    private val tfbiControllers = mutableListOf<TfbiInputController>()
    private val flickLongPressControllers = mutableListOf<FlickLongPressInputController>()
    private val stickyTfbiControllers = mutableListOf<TfbiStickyFlickController>()
    private val hierarchicalTfbiControllers = mutableListOf<TfbiHierarchicalFlickController>()
    private val tapLongPressControllers = mutableListOf<TapLongPressInputController>()
    private val doubleTapActionDispatcher = DoubleTapActionDispatcher(
        timeoutMillis = ViewConfiguration.getDoubleTapTimeout().toLong(),
        minimumIntervalMillis = DOUBLE_TAP_MIN_INTERVAL_MILLIS,
        clockMillis = SystemClock::uptimeMillis,
        scheduler = TapTaskScheduler { delayMillis, task ->
            val runnable = Runnable(task)
            postDelayed(runnable, delayMillis)
            CancellableTask { removeCallbacks(runnable) }
        },
        dispatch = { action -> listener?.onAction(action, false) }
    )

    private var popupWindowAnchorProvider: (() -> View?)? = null
    private var tfbiPopupPresentationMode = TfbiPopupPresentationMode.LEGACY_GRID
    private var tfbiFlickStartPositionMode = TfbiFlickStartPositionMode.TOUCH_POINT

    private val hitRect = Rect()
    private var flickSensitivity: Int = 100
    private var longPressTimeout: Long = ViewConfiguration.getLongPressTimeout().toLong()
    private val localRuntimeGestureSettings = MutableRuntimeGestureSettingsSource(
        RuntimeGestureSettings(
            flickSensitivity = flickSensitivity,
            longPressTimeoutMillis = longPressTimeout
        )
    )
    private val runtimeGestureSettings =
        DelegatingRuntimeGestureSettingsSource(localRuntimeGestureSettings)
    private val gestureSessionConfigSource = GestureSessionConfigSource {
        val settings = runtimeGestureSettings.snapshot()
        GestureSessionConfig(
            settingsRevision = settings.revision,
            flickSensitivity = settings.flickSensitivity,
            flickThresholdPx = resolvedFlickThresholdPx(settings.flickSensitivity),
            longPressTimeoutMillis = settings.longPressTimeoutMillis,
            flickThresholdShape = settings.flickThresholdShape
        )
    }
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

    private val keyInfos = mutableListOf<KeyInfo>()
    private val dynamicKeyMap = mutableMapOf<String, KeyInfo>()
    private val canonicalGuideLabels =
        IdentityHashMap<AutoSizeButton, AutoSizeButton.FlickGuideLabels>()
    private var currentLayout: KeyboardLayout? = null
    private var controllerRebindPending = false
    private var keyboardRenderRevision: Int = 0
    private var renderedKeyboardRenderRevision: Int = -1
    private var keyCharacterCase: KeyCharacterCase = KeyCharacterCase.AS_DEFINED
    private var sumireSpecialKeyActionResolver:
            ((String, String, KeyData, SumireSpecialKeyDirection) -> ResolvedSumireSpecialKeyAction)? =
        null
    private var sumireSpecialKeyLayoutType: String? = null
    private var sumireSpecialKeyInputMode: String? = null

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
    private var customAngleAndRange: Map<CircularFlickDirection, Pair<Float, Float>> = emptyMap()
    private var circularViewScale: Float = 1.0f
    private var circularFlickDirectionCount: Int = 4
    private var hierarchicalFlickModeSwitchAngleMargin: Double = 20.0
    private var visibleKeyLabels: Set<String>? = null
    private var borderWidth: Int = 1
    private var flickGuideEnabled: Boolean = false
    private var flickGuideAllowsMultiCharacterLabels: Boolean = false
    private var flickGuideTextSizeSp: Float = 9f
    private var flickGuideMaxCodePoints: Int = 1
    private var popupViewStyleSet = FlickPopupViewStyleSet(
        directional = PopupViewStyle(100, 28f),
        cross = PopupViewStyle(100, 18f),
        standard = PopupViewStyle(100, 19f),
        tfbi = PopupViewStyle(100, 20f)
    )

    private data class KeyVisualPalette(
        val usesSpecialSurface: Boolean,
        val baseColor: Int,
        val textColor: Int,
        val highlightColor: Int
    )

    init {
        setPadding(0, 0, 0, 0)
        clipToPadding = false
        clipChildren = false
    }

    fun setOnKeyboardActionListener(listener: OnKeyboardActionListener) {
        this.listener = listener
    }

    fun setOnFlickTextPreviewListener(listener: FlickTextPreviewListener?) {
        flickTextPreviewEmitter.listener = listener
    }

    /**
     * Updates text-producing key labels without rebuilding the keyboard or changing canonical
     * [KeyData]. This preserves active gesture recognizers, including the second Shift tap.
     */
    fun setKeyCharacterCase(characterCase: KeyCharacterCase) {
        if (keyCharacterCase == characterCase) return
        keyCharacterCase = characterCase
        keyInfos.forEach(::refreshKeyTextPresentation)
        refreshPopupTextTransformers()
    }

    fun setSumireSpecialKeyActionResolver(
        resolver: ((String, String, KeyData, SumireSpecialKeyDirection) -> ResolvedSumireSpecialKeyAction)?,
        layoutType: String?,
        inputMode: String?
    ) {
        sumireSpecialKeyActionResolver = resolver
        sumireSpecialKeyLayoutType = layoutType
        sumireSpecialKeyInputMode = inputMode
    }

    fun clearSumireSpecialKeyActionResolver() {
        sumireSpecialKeyActionResolver = null
        sumireSpecialKeyLayoutType = null
        sumireSpecialKeyInputMode = null
    }

    fun setPopupWindowAnchorProvider(provider: (() -> View?)?) {
        popupWindowAnchorProvider = provider
        flickControllers.forEach { it.setPopupWindowAnchorProvider(provider) }
        crossFlickControllers.forEach { it.setPopupOverlayHostProvider(provider) }
        centerGuideFlickControllers.forEach { it.setPopupWindowAnchorProvider(provider) }
        standardFlickControllers.forEach { it.setPopupWindowAnchorProvider(provider) }
        tfbiControllers.forEach { it.setPopupWindowAnchorProvider(provider) }
        flickLongPressControllers.forEach { it.setPopupWindowAnchorProvider(provider) }
        stickyTfbiControllers.forEach { it.setPopupWindowAnchorProvider(provider) }
        hierarchicalTfbiControllers.forEach { it.setPopupWindowAnchorProvider(provider) }
    }

    fun setTfbiPopupPresentationMode(mode: TfbiPopupPresentationMode) {
        tfbiPopupPresentationMode = mode
        tfbiControllers.forEach { it.setPopupPresentationMode(mode) }
        stickyTfbiControllers.forEach { it.setPopupPresentationMode(mode) }
        hierarchicalTfbiControllers.forEach { it.setPopupPresentationMode(mode) }
    }

    fun setTfbiFlickStartPositionMode(mode: TfbiFlickStartPositionMode) {
        tfbiFlickStartPositionMode = mode
        tfbiControllers.forEach { it.setTfbiFlickStartPositionMode(mode) }
        stickyTfbiControllers.forEach { it.setTfbiFlickStartPositionMode(mode) }
        hierarchicalTfbiControllers.forEach { it.setTfbiFlickStartPositionMode(mode) }
    }

    fun applyPopupViewStyleSet(styleSet: FlickPopupViewStyleSet) {
        popupViewStyleSet = FlickPopupViewStyleSet(
            directional = clampPopupStyle(styleSet.directional),
            cross = clampPopupStyle(styleSet.cross),
            standard = clampPopupStyle(styleSet.standard),
            tfbi = clampPopupStyle(styleSet.tfbi)
        )
        crossFlickControllers.forEach {
            it.applyPopupViewStyleSet(popupViewStyleSet.directional, popupViewStyleSet.cross)
        }
        centerGuideFlickControllers.forEach { it.applyPopupViewStyle(popupViewStyleSet.tfbi) }
        standardFlickControllers.forEach { it.applyPopupViewStyle(popupViewStyleSet.standard) }
        tfbiControllers.forEach { it.applyPopupViewStyle(popupViewStyleSet.tfbi) }
        flickLongPressControllers.forEach { it.applyPopupViewStyle(popupViewStyleSet.tfbi) }
        stickyTfbiControllers.forEach { it.applyPopupViewStyle(popupViewStyleSet.tfbi) }
        hierarchicalTfbiControllers.forEach { it.applyPopupViewStyle(popupViewStyleSet.tfbi) }
    }

    private fun clampPopupStyle(style: PopupViewStyle): PopupViewStyle {
        return PopupViewStyle(
            sizeScalePercent = style.sizeScalePercent.coerceIn(50, 200),
            textSizeSp = style.textSizeSp.coerceIn(8f, 48f),
            backgroundColor = style.backgroundColor,
            textColor = style.textColor
        )
    }

    fun setFlickSensitivityValue(sensitivity: Int) {
        val normalized = sensitivity.coerceIn(1, 200)
        if (flickSensitivity == normalized) return
        flickSensitivity = normalized
        localRuntimeGestureSettings.update(flickSensitivity = normalized)
    }

    fun setFlickThresholdShape(shape: FlickThresholdShape) {
        localRuntimeGestureSettings.update(flickThresholdShape = shape)
    }

    /**
     * Binds this surface to the IME-wide runtime source. Existing controllers retain this View's
     * stable [gestureSessionConfigSource], so binding never recreates keys or controllers.
     */
    fun bindRuntimeGestureSettings(source: RuntimeGestureSettingsSource?) {
        runtimeGestureSettings.bind(source)
    }

    private fun resolvedFlickThresholdPx(sensitivity: Int): Float {
        return FlickGestureMath.thresholdPxForSensitivity(
            sensitivity = sensitivity,
            scaledTouchSlopPx = ViewConfiguration.get(context).scaledTouchSlop,
            sensitiveMultiplier = 1.5f,
            normalMultiplier = 3.5f,
            stableMultiplier = 4.25f
        )
    }

    fun setLongPressTimeout(timeoutMillis: Long) {
        val normalized = timeoutMillis.coerceIn(100L, 2000L)
        if (longPressTimeout == normalized) return
        longPressTimeout = normalized
        localRuntimeGestureSettings.update(longPressTimeoutMillis = normalized)
    }

    fun setDefaultTextSize(textSize: Float) {
        this.defaultTextSize = textSize
    }

    fun setFlickGuideEnabled(enabled: Boolean) {
        setFlickGuideEnabled(enabled, allowMultiCharacterLabels = false)
    }

    fun setFlickGuideEnabled(
        enabled: Boolean,
        allowMultiCharacterLabels: Boolean
    ) {
        if (
            flickGuideEnabled == enabled &&
            flickGuideAllowsMultiCharacterLabels == allowMultiCharacterLabels
        ) {
            return
        }
        flickGuideEnabled = enabled
        flickGuideAllowsMultiCharacterLabels = allowMultiCharacterLabels
        rebuildCurrentKeyboard()
    }

    fun setFlickGuideTextSizeSp(sizeSp: Float) {
        val coerced = sizeSp.coerceIn(6f, 16f)
        if (flickGuideTextSizeSp == coerced) return
        flickGuideTextSizeSp = coerced
        rebuildCurrentKeyboard()
    }

    fun setFlickGuideMaxCodePoints(maxCodePoints: Int) {
        val coerced = maxCodePoints.coerceIn(1, 4)
        if (flickGuideMaxCodePoints == coerced) return
        flickGuideMaxCodePoints = coerced
        rebuildCurrentKeyboard()
    }

    fun applyKeySizing(
        keyWidthScalePercent: Int,
        keyHeightScalePercent: Int,
        iconScalePercent: Int,
        textSizeSp: Float,
        specialKeyTextSizeSp: Float
    ) {
        val normalizedWidthScale = keyWidthScalePercent.coerceIn(0, 200)
        val normalizedHeightScale = keyHeightScalePercent.coerceIn(0, 200)
        val normalizedIconScale = iconScalePercent.coerceIn(40, 200)
        val normalizedTextSize = textSizeSp.coerceIn(8f, 32f)
        val normalizedSpecialTextSize = specialKeyTextSizeSp.coerceIn(8f, 32f)
        if (
            this.keyWidthScalePercent == normalizedWidthScale &&
            this.keyHeightScalePercent == normalizedHeightScale &&
            this.iconScalePercent == normalizedIconScale &&
            this.defaultTextSize == normalizedTextSize &&
            this.specialKeyTextSizeSp == normalizedSpecialTextSize
        ) {
            return
        }
        this.keyWidthScalePercent = normalizedWidthScale
        this.keyHeightScalePercent = normalizedHeightScale
        this.iconScalePercent = normalizedIconScale
        this.defaultTextSize = normalizedTextSize
        this.specialKeyTextSizeSp = normalizedSpecialTextSize

        rebuildCurrentKeyboard()
    }

    fun setCursorMode(enabled: Boolean) {
        isCursorMode = enabled
    }

    fun setAngleAndRange(
        range: Map<CircularFlickDirection, Pair<Float, Float>>,
        circularPopViewScale: Float
    ) {
        this.customAngleAndRange = range
        this.circularViewScale = circularPopViewScale
    }

    fun setCircularFlickOptions(directionCount: Int) {
        circularFlickDirectionCount = directionCount.coerceIn(4, 7)
    }

    fun setHierarchicalFlickModeSwitchAngleMargin(margin: Double) {
        hierarchicalFlickModeSwitchAngleMargin = margin.coerceIn(0.0, 34.0)
        hierarchicalTfbiControllers.forEach {
            it.setModeSwitchAngleMargin(hierarchicalFlickModeSwitchAngleMargin)
        }
    }

    fun setVisibleKeyLabels(labels: Set<String>?) {
        visibleKeyLabels = labels
        dynamicKeyMap.values.forEach { info ->
            applyVisibleKeyFilter(info.view, info.keyData)
        }
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
        val renderConfigurationChanged =
            this.themeMode != themeMode ||
                this.isNightMode !=
                (currentNightMode == Configuration.UI_MODE_NIGHT_YES) ||
                this.isDynamicColorEnabled != isDynamicColorEnabled ||
                this.customBgColor != customBgColor ||
                this.customKeyColor != customKeyColor ||
                this.customSpecialKeyColor != customSpecialKeyColor ||
                this.customKeyTextColor != customKeyTextColor ||
                this.customSpecialKeyTextColor != customSpecialKeyTextColor ||
                this.liquidGlassEnable != liquidGlassEnable ||
                this.customBorderEnable != customBorderEnable ||
                this.customBorderColor != customBorderColor ||
                this.liquidGlassKeyAlphaEnable != liquidGlassKeyAlphaEnable ||
                this.borderWidth != borderWidth
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
        if (renderConfigurationChanged) {
            keyboardRenderRevision += 1
        }

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

    private fun resolveKeyVisualPalette(keyData: KeyData): KeyVisualPalette {
        val usesSpecialSurface = KeyVisualStyleResolver.usesSpecialSurface(keyData)
        return if (usesSpecialSurface) {
            KeyVisualPalette(
                usesSpecialSurface = true,
                baseColor = customSpecialKeyColor,
                textColor = customSpecialKeyTextColor,
                highlightColor = manipulateColor(customSpecialKeyColor, 1.2f)
            )
        } else {
            KeyVisualPalette(
                usesSpecialSurface = false,
                baseColor = customKeyColor,
                textColor = customKeyTextColor,
                highlightColor = customSpecialKeyColor
            )
        }
    }

    private fun defaultKeyBackgroundDrawable(keyData: KeyData, isDarkTheme: Boolean): Drawable? {
        val drawableResId = when {
            resolveKeyVisualPalette(keyData).usesSpecialSurface -> {
                if (isDarkTheme) {
                    com.kazumaproject.core.R.drawable.ten_keys_side_bg_material
                } else {
                    com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light
                }
            }

            keyData.keyType != KeyType.STANDARD_FLICK -> {
                if (isDarkTheme) {
                    com.kazumaproject.core.R.drawable.ten_keys_center_bg_material
                } else {
                    com.kazumaproject.core.R.drawable.ten_keys_center_bg_material_light
                }
            }

            else -> return null
        }
        return ContextCompat.getDrawable(context, drawableResId)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setKeyboard(layout: KeyboardLayout) {
        setKeyboard(layout, forceRebuild = false)
    }

    private fun rebuildCurrentKeyboard() {
        currentLayout?.let { setKeyboard(it, forceRebuild = true) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setKeyboard(layout: KeyboardLayout, forceRebuild: Boolean) {
        val expectedChildCount = if (layout.items.isNotEmpty()) {
            layout.items.size
        } else {
            layout.keys.size
        }
        if (
            !forceRebuild &&
            currentLayout == layout &&
            renderedKeyboardRenderRevision == keyboardRenderRevision &&
            childCount == expectedChildCount
        ) {
            Log.d("FlickKeyboardView", "setKeyboard (Reuse Existing Views)")
            cancelTrackedTouchState()
            return
        }
        Log.d("FlickKeyboardView", "setKeyboard (Full Rebuild)")

        doubleTapActionDispatcher.cancel()
        cancelTrackedTouchState()
        listener?.onLongPressActionCanceled(KeyAction.Cancel)

        removeAllViews()

        flickControllers.forEach { it.cancel() }
        flickControllers.clear()

        crossFlickControllers.forEach { it.cancel() }
        crossFlickControllers.clear()

        centerGuideFlickControllers.forEach { it.cancel() }
        centerGuideFlickControllers.clear()

        standardFlickControllers.forEach { it.cancel() }
        standardFlickControllers.clear()

        tfbiControllers.forEach { it.cancel() }
        tfbiControllers.clear()

        flickLongPressControllers.forEach { it.cancel() }
        flickLongPressControllers.clear()

        stickyTfbiControllers.forEach { it.cancel() }
        stickyTfbiControllers.clear()

        hierarchicalTfbiControllers.forEach { it.cancel() }
        hierarchicalTfbiControllers.clear()

        tapLongPressControllers.forEach { it.cancel() }
        tapLongPressControllers.clear()

        keyInfos.clear()
        dynamicKeyMap.clear()
        canonicalGuideLabels.clear()
        currentLayout = layout

        columnCount = if (layout.items.isNotEmpty()) layout.columnUnitCount else layout.columnCount
        rowCount = if (layout.items.isNotEmpty()) layout.rowUnitCount else layout.rowCount
        isFocusable = false

        if (layout.items.isNotEmpty()) {
            layout.items.forEach { item ->
                when (item) {
                    is KeyItem -> addKeyItem(item)
                    is SpacerItem -> addSpacerItem(item)
                }
            }
        } else {
            layout.keys.forEach { keyData ->
                addKeyItem(
                    KeyItem(
                        id = keyData.keyId
                            ?: "legacy_${keyData.row}_${keyData.column}_${keyData.label}",
                        keyData = keyData,
                        placement = GridPlacement(
                            rowUnits = keyData.row,
                            columnUnits = keyData.column,
                            rowSpanUnits = keyData.rowSpan,
                            columnSpanUnits = keyData.colSpan
                        )
                    )
                )
            }
        }
        renderedKeyboardRenderRevision = keyboardRenderRevision
    }

    private fun addKeyItem(item: KeyItem) {
        val keyData = item.keyData
        val index = childCount
        val keyView = createKeyView(keyData)
        keyView.layoutParams = createLayoutParams(item.placement, keyData)
        val controller = attachKeyBehavior(keyView, keyData)
        applyVisibleKeyFilter(keyView, keyData)

        val info = KeyInfo(keyView, keyData, controller, index)
        keyInfos += info
        keyData.keyId?.let { id ->
            dynamicKeyMap[id] = info
        }

        addView(keyView)
    }

    private fun applyVisibleKeyFilter(keyView: View, keyData: KeyData) {
        val labels = visibleKeyLabels ?: run {
            keyView.visibility = View.VISIBLE
            keyView.isEnabled = true
            return
        }
        val isVisible = keyData.label in labels
        keyView.visibility = if (isVisible) View.VISIBLE else View.INVISIBLE
        keyView.isEnabled = isVisible
    }

    private fun addSpacerItem(item: SpacerItem) {
        val spacer = Space(context).apply {
            isClickable = false
            isFocusable = false
            isEnabled = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        spacer.layoutParams = createLayoutParams(item.placement)
        addView(spacer)
    }

    fun updateDynamicKey(keyId: String, stateIndex: Int) {
        val info = dynamicKeyMap[keyId] ?: return
        val states = info.keyData.dynamicStates ?: return
        val newState = states.getOrNull(stateIndex) ?: states.firstOrNull() ?: return

        val dynamicStateKeyData = info.keyData.copy(
            label = newState.label ?: "",
            action = newState.action,
            drawableResId = newState.drawableResId
        )
        val newKeyData = dynamicStateKeyData.applyTapOverrideDisplayForDynamicSumireSpecialKey(
            displayActions = KeyActionMapper.getDisplayActions(context),
            resolve = ::resolveSumireSpecialKeyOverride
        )
        if (info.keyData == newKeyData) return

        val oldView = info.view
        val newViewIsIcon = KeyIconResolver.hasIcon(newKeyData)
        val newViewIsText = !newViewIsIcon

        val oldViewIsIcon = oldView is AppCompatImageButton
        val oldViewIsText = !oldViewIsIcon

        val needsNewView = (oldViewIsIcon && newViewIsText) || (oldViewIsText && newViewIsIcon)

        detachKeyBehavior(info.controller)
        (oldView as? AutoSizeButton)?.let(canonicalGuideLabels::remove)

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
            .filter { !KeyIconResolver.hasIconOverride(it.keyData) }
            .forEach { info ->
                if (info.view is AppCompatImageButton) {
                    (info.view as AppCompatImageButton).apply {
                        setImageResource(drawableResId)
                        applyImageButtonTint(this, info.keyData.copy(drawableResId = drawableResId))
                    }
                }
            }
    }

    private fun refreshKeyTextPresentation(info: KeyInfo) {
        val button = info.view as? AutoSizeButton ?: return
        val guideLabels = canonicalGuideLabels[button]
        updateKeyVisuals(button, info.keyData)
        if (guideLabels != null) {
            applyDisplayedGuideLabels(button, info.keyData, guideLabels)
        }
    }

    private fun refreshPopupTextTransformers() {
        val transform = ::transformInputTextForDisplay
        flickControllers.forEach { it.setInputTextTransform(transform) }
        crossFlickControllers.forEach { it.setInputTextTransform(transform) }
        centerGuideFlickControllers.forEach { it.setInputTextTransform(transform) }
        standardFlickControllers.forEach { it.setInputTextTransform(transform) }
        tfbiControllers.forEach { it.setInputTextTransform(transform) }
        flickLongPressControllers.forEach { it.setInputTextTransform(transform) }
        stickyTfbiControllers.forEach { it.setInputTextTransform(transform) }
        hierarchicalTfbiControllers.forEach { it.setInputTextTransform(transform) }
    }

    private fun transformInputTextForDisplay(text: String): String =
        keyCharacterCase.transformAsciiLetters(text)

    private fun shouldTransformMainLabel(keyData: KeyData): Boolean {
        if (keyData.action is KeyAction.Text) return true
        return !keyData.isSpecialKey && keyData.keyType != KeyType.NORMAL
    }

    private fun keyLabelForDisplay(keyData: KeyData): String {
        val canonicalLabel = KeyIconResolver.resolvedLabelForRendering(keyData)
        return if (shouldTransformMainLabel(keyData)) {
            transformInputTextForDisplay(canonicalLabel)
        } else {
            canonicalLabel
        }
    }

    private fun transformGuideLabels(
        labels: AutoSizeButton.FlickGuideLabels
    ): AutoSizeButton.FlickGuideLabels = labels.copy(
        tap = transformInputTextForDisplay(labels.tap),
        up = transformInputTextForDisplay(labels.up),
        upRight = transformInputTextForDisplay(labels.upRight),
        right = transformInputTextForDisplay(labels.right),
        downRight = transformInputTextForDisplay(labels.downRight),
        down = transformInputTextForDisplay(labels.down),
        downLeft = transformInputTextForDisplay(labels.downLeft),
        left = transformInputTextForDisplay(labels.left),
        upLeft = transformInputTextForDisplay(labels.upLeft)
    )

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

    private fun createLayoutParams(
        placement: GridPlacement,
        keyData: KeyData? = null
    ): LayoutParams {
        return LayoutParams().apply {
            rowSpec = spec(
                placement.rowUnits,
                placement.rowSpanUnits,
                FILL,
                placement.rowSpanUnits.toFloat()
            )
            columnSpec = spec(
                placement.columnUnits,
                placement.columnSpanUnits,
                FILL,
                placement.columnSpanUnits.toFloat()
            )
            width = 0
            height = 0

            if (keyData != null) {
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

                setMargins(
                    getScaledHorizontalMarginPx(baseHorizontalMarginDp),
                    getScaledVerticalMarginPx(baseVerticalMarginDp),
                    getScaledHorizontalMarginPx(baseHorizontalMarginDp),
                    getScaledVerticalMarginPx(baseVerticalMarginDp)
                )
            }
        }
    }

    private fun getSpecialKeyTextSizeSp(): Float {
        return specialKeyTextSizeSp.coerceIn(8f, 32f)
    }

    private fun getKeyTextSizeSp(keyData: KeyData): Float {
        return if (keyData.isSpecialKey) {
            getSpecialKeyTextSizeSp()
        } else {
            defaultTextSize
        }
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

    private fun applyImageButtonTint(button: AppCompatImageButton, keyData: KeyData) {
        if (
            themeMode == "custom" &&
            keyData.isSpecialKey &&
            KeyIconResolver.shouldTintIcon(keyData)
        ) {
            button.setColorFilter(resolveKeyVisualPalette(keyData).textColor)
        } else {
            button.clearColorFilter()
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
        val targetTextSizeSp = getKeyTextSizeSp(keyData)
        val label = keyLabelForDisplay(keyData)

        button.setDefaultTextSize(targetTextSizeSp)
        button.setFlickGuideTextSizeSp(flickGuideTextSizeSp)
        button.setFlickGuideLabels(null)
        button.contentDescription = label

        if (label.contains("\n")) {
            button.maxLines = 2
            button.setLineSpacing(0f, 0.9f)
            button.setPadding(0, dpToPx(4), 0, dpToPx(4))
            button.gravity = Gravity.CENTER
            button.text = buildKeyLabelSpannable(label, targetTextSizeSp)
        } else {
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, targetTextSizeSp)
            button.text = label
            button.gravity = Gravity.CENTER
        }

        button.refreshTextSize()
    }

    private fun extractInputMap(actionMap: Map<FlickDirection, FlickAction>): Map<FlickDirection, String> {
        return actionMap.mapValues { (_, flickAction) ->
            (flickAction as? FlickAction.Input)?.char ?: ""
        }
    }

    private fun extractFlickLongPressMap(
        actionMap: Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>>?
    ): Map<TfbiFlickDirection, String> {
        return actionMap.orEmpty().mapNotNull { (direction, innerMap) ->
            innerMap[direction]
                ?.takeIf { it.isNotEmpty() }
                ?.let { output -> direction to output }
        }.toMap()
    }

    private fun textOutputFromAction(action: KeyAction?): String {
        return when (action) {
            is KeyAction.Text -> action.text
            is KeyAction.InputText -> action.text
            else -> ""
        }
    }

    private fun circularActionLabel(action: FlickAction?): String {
        return when (action) {
            is FlickAction.Input -> action.label ?: action.char
            is FlickAction.Action -> action.label ?: when (action.action) {
                KeyAction.ShowEmojiKeyboard -> "絵"
                KeyAction.SwitchToNextIme -> "IME"
                KeyAction.SwitchToKanaLayout -> "かな"
                KeyAction.SwitchToEnglishLayout -> "英"
                KeyAction.SwitchToNumberLayout -> "数"
                else -> ""
            }

            null -> ""
        }
    }

    private fun extractCircularLabelMap(
        actionMap: Map<CircularFlickDirection, FlickAction>
    ): Map<CircularFlickDirection, String> {
        return actionMap.mapValues { (_, flickAction) -> circularActionLabel(flickAction) }
    }

    private fun isCircularMapSwitchAction(action: FlickAction?): Boolean {
        return action is FlickAction.Action &&
                action.action == KeyAction.MoveCustomKeyboardTab &&
                action.label == "⇄"
    }

    private fun getGuideLabels(stringMap: Map<FlickDirection, String>): AutoSizeButton.FlickGuideLabels {
        val tap = sanitizeGuideText(stringMap[FlickDirection.TAP] ?: "") ?: ""
        val left = sanitizeGuideText(
            stringMap[FlickDirection.UP_LEFT_FAR]
                ?: stringMap[FlickDirection.UP_LEFT]
                ?: stringMap.entries.firstOrNull { it.key.name.contains("LEFT") }?.value
                ?: ""
        ) ?: ""
        val right = sanitizeGuideText(
            stringMap[FlickDirection.UP_RIGHT_FAR]
                ?: stringMap[FlickDirection.UP_RIGHT]
                ?: stringMap.entries.firstOrNull { it.key.name.contains("RIGHT") }?.value
                ?: ""
        ) ?: ""
        val down = sanitizeGuideText(
            stringMap[FlickDirection.DOWN]
                ?: stringMap.entries.firstOrNull { it.key.name.contains("DOWN") }?.value
                ?: ""
        ) ?: ""
        val up = sanitizeGuideText(stringMap[FlickDirection.UP] ?: "") ?: ""

        return AutoSizeButton.FlickGuideLabels(
            tap = tap,
            up = up,
            right = right,
            down = down,
            left = left
        )
    }

    private fun getCircularGuideLabels(
        stringMap: Map<CircularFlickDirection, String>
    ): AutoSizeButton.FlickGuideLabels {
        val tap = sanitizeGuideText(stringMap[CircularFlickDirection.TAP] ?: "") ?: ""
        val up = sanitizeGuideText(stringMap[CircularFlickDirection.SLOT_0] ?: "") ?: ""
        val right = sanitizeGuideText(stringMap[CircularFlickDirection.SLOT_1] ?: "") ?: ""
        val down = sanitizeGuideText(stringMap[CircularFlickDirection.SLOT_2] ?: "") ?: ""
        val left = sanitizeGuideText(stringMap[CircularFlickDirection.SLOT_3] ?: "") ?: ""
        return AutoSizeButton.FlickGuideLabels(
            tap = tap,
            up = up,
            right = right,
            down = down,
            left = left
        )
    }

    private fun sanitizeGuideText(value: String): String? {
        return FlickGuideLabelMapper.sanitizeGuideText(value, flickGuideMaxCodePoints)
    }

    private fun getGuideTextColor(keyData: KeyData): Int {
        return when (themeMode) {
            "custom" -> resolveKeyVisualPalette(keyData).textColor

            else -> context.getColorFromAttr(R.attr.colorOnSurface)
        }
    }

    private fun applyGuideLabels(
        button: AutoSizeButton,
        keyData: KeyData,
        stringMap: Map<FlickDirection, String>
    ) {
        applyResolvedGuideLabels(button, keyData, getGuideLabels(stringMap))
    }

    private fun applyCircularGuideLabels(
        button: AutoSizeButton,
        keyData: KeyData,
        stringMap: Map<CircularFlickDirection, String>
    ) {
        applyResolvedGuideLabels(button, keyData, getCircularGuideLabels(stringMap))
    }

    private fun applyTwoStepGuideLabels(
        button: AutoSizeButton,
        keyData: KeyData,
        twoStepMap: Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>>
    ) {
        applyResolvedGuideLabels(
            button,
            keyData,
            FlickGuideLabelMapper.buildTwoStepRootGuideLabels(
                twoStepMap,
                flickGuideMaxCodePoints
            )
        )
    }

    private fun applyHierarchicalGuideLabels(
        button: AutoSizeButton,
        keyData: KeyData,
        rootMap: Map<TfbiFlickDirection, TfbiFlickNode>
    ) {
        applyResolvedGuideLabels(
            button,
            keyData,
            FlickGuideLabelMapper.buildHierarchicalGuideLabels(
                rootMap,
                flickGuideMaxCodePoints
            )
        )
    }

    private fun applyResolvedGuideLabels(
        button: AutoSizeButton,
        keyData: KeyData,
        labels: AutoSizeButton.FlickGuideLabels
    ) {
        val singleCharacterLabel = isSingleGuideCharacter(keyData.label)
        val eligibleMultiCharacterLabel =
            flickGuideAllowsMultiCharacterLabels && !keyData.isSpecialKey
        if (
            !flickGuideEnabled ||
            (!singleCharacterLabel && !eligibleMultiCharacterLabel) ||
            !labels.hasVisibleGuides()
        ) {
            canonicalGuideLabels.remove(button)
            button.setFlickGuideLabels(null)
            return
        }

        canonicalGuideLabels[button] = labels
        applyDisplayedGuideLabels(button, keyData, labels)
    }

    private fun applyDisplayedGuideLabels(
        button: AutoSizeButton,
        keyData: KeyData,
        canonicalLabels: AutoSizeButton.FlickGuideLabels
    ) {
        val labels = transformGuideLabels(canonicalLabels)
        val singleCharacterLabel = isSingleGuideCharacter(keyData.label)
        val eligibleMultiCharacterLabel =
            flickGuideAllowsMultiCharacterLabels && !keyData.isSpecialKey
        if (!singleCharacterLabel && eligibleMultiCharacterLabel && labels.tap.isNotEmpty()) {
            button.maxLines = 1
            button.setLineSpacing(0f, 1f)
            button.text = labels.tap
            button.refreshTextSize()
        }
        button.setFlickGuideLabels(labels, getGuideTextColor(keyData))
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
        val visualPalette = resolveKeyVisualPalette(keyData)

        val keyView: View = if (KeyIconResolver.hasIcon(keyData)) {
            AppCompatImageButton(context).apply {
                isFocusable = false
                elevation = 0f
                KeyIconResolver.setImage(this, keyData)
                contentDescription = keyData.label
                scaleType = android.widget.ImageView.ScaleType.MATRIX

                applyImageButtonSizing(this, keyData)

                val originalBg = defaultKeyBackgroundDrawable(keyData, isDarkTheme)

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
                            setDrawableSolidColor(visualPalette.baseColor)
                            setBorder(customBorderColor, borderWidth)
                        } else {
                            val neumorphDrawable = getDynamicNeumorphDrawable(
                                baseColor = visualPalette.baseColor,
                                radius = commonCornerRadius
                            )

                            val segmentedDrawable = SegmentedBackgroundDrawable(
                                label = "",
                                baseColor = Color.TRANSPARENT,
                                highlightColor = visualPalette.highlightColor,
                                textColor = visualPalette.textColor,
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
                        }
                    }
                }
                applyImageButtonTint(this, keyData)

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

                val originalBg: Drawable? = defaultKeyBackgroundDrawable(keyData, isDarkTheme)

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
                            setDrawableSolidColor(visualPalette.baseColor)
                            setTextColor(visualPalette.textColor)
                            setBorder(customBorderColor, borderWidth)
                        } else {
                            val neumorphDrawable = getDynamicNeumorphDrawable(
                                baseColor = visualPalette.baseColor,
                                radius = commonCornerRadius
                            )

                            val segmentedDrawable = SegmentedBackgroundDrawable(
                                label = "",
                                baseColor = Color.TRANSPARENT,
                                highlightColor = visualPalette.highlightColor,
                                textColor = visualPalette.textColor,
                                cornerRadius = commonCornerRadius
                            )

                            val layerDrawable =
                                LayerDrawable(arrayOf(neumorphDrawable, segmentedDrawable))
                            val inset = dpToPx(2)
                            layerDrawable.setLayerInset(1, inset, inset, inset, inset)

                            background = layerDrawable
                            setTextColor(visualPalette.textColor)
                        }
                    }
                }

                if (liquidGlassEnable) {
                    setDrawableAlpha(liquidGlassKeyAlphaEnable)
                }
            }
        }

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
                val circularKeyMapsList =
                    keyData.keyId?.let { layout.circularFlickKeyMaps[it] }
                        ?: layout.circularFlickKeyMaps[keyData.label]
                        ?: (
                                keyData.keyId?.let { layout.flickKeyMaps[it] }
                                    ?: layout.flickKeyMaps[keyData.label]
                                )?.let { mapOf(keyData.label to it).toCircularFlickKeyMaps()[keyData.label] }
                Log.d("FlickKeyboardView KeyType.CIRCULAR_FLICK", "$circularKeyMapsList")
                if (!circularKeyMapsList.isNullOrEmpty()) {
                    val controller = CustomAngleFlickController(
                        context = context,
                        gestureConfigSource = gestureSessionConfigSource
                    ).apply {
                        setPopupWindowAnchorProvider(popupWindowAnchorProvider)
                        setInputTextTransform(::transformInputTextForDisplay)
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
                            override fun onPress(action: FlickAction?) {
                                when (action) {
                                    is FlickAction.Input -> notifyTextPress(keyData, action.char)
                                    is FlickAction.Action -> this@FlickKeyboardView.listener?.onPress(
                                        action.action
                                    )

                                    null -> Unit
                                }
                            }

                            override fun onFlick(
                                direction: CircularFlickDirection,
                                action: FlickAction
                            ) {
                                when (action) {
                                    is FlickAction.Input -> {
                                        if (action.char.isNotEmpty()) {
                                            dispatchCommittedKeyAction(
                                                keyData,
                                                KeyAction.Text(action.char),
                                                isFlick = direction != CircularFlickDirection.TAP
                                            )
                                        }
                                    }

                                    is FlickAction.Action -> {
                                        dispatchCommittedKeyAction(
                                            keyData,
                                            action.action,
                                            isFlick = direction != CircularFlickDirection.TAP
                                        )
                                    }
                                }
                            }

                            override fun onStateChanged(
                                view: View,
                                newMap: Map<CircularFlickDirection, FlickAction>
                            ) {
                                if (view is AutoSizeButton) {
                                    applyCircularGuideLabels(
                                        view,
                                        keyData,
                                        extractCircularLabelMap(newMap)
                                    )
                                }
                            }

                            override fun onFlickDirectionChanged(
                                newDirection: CircularFlickDirection
                            ) {
                                this@FlickKeyboardView.listener?.onFlickDirectionChanged(
                                    newDirection.toLegacyFlickDirection()
                                )
                            }

                            override fun onSelectionChanged(
                                action: FlickAction?,
                                isFlick: Boolean
                            ) {
                                updateTextPreview(textOutputFromFlickAction(action), isFlick)
                            }

                            override fun onCanceled() = cancelTextPreview()
                        }

                        val mapSwitchLabels = List(circularKeyMapsList.size) { null }
                        val stringMaps = circularKeyMapsList
                        val guideMaps = stringMaps.map { map ->
                            extractCircularLabelMap(map)
                        }

                        if (keyView is AutoSizeButton) {
                            guideMaps.firstOrNull()?.let { firstMap ->
                                applyCircularGuideLabels(keyView, keyData, firstMap)
                            } ?: keyView.setFlickGuideLabels(null)
                        }

                        attach(keyView, stringMaps, mapSwitchLabels)

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
                        buildEvenCircularRanges(circularFlickDirectionCount)
                    }.filterKeys {
                        CircularFlickDirection.slots(circularFlickDirectionCount).contains(it)
                    }

                    controller.setFlickRanges(ranges)
                    flickControllers.add(controller)
                    return controller
                }
            }

            KeyType.CROSS_FLICK -> {
                val rawFlickActionMap =
                    keyData.keyId?.let { layout.flickKeyMaps[it] }?.firstOrNull()
                        ?: layout.flickKeyMaps[keyData.label]?.firstOrNull()
                // Sumire 特殊キーで keyId alias を引いた場合、layout 構築時の static な
                // TAP entry が残っている可能性がある。dynamicStates の影響で
                // keyData.action / label / drawableResId は updateDynamicKey で更新されるので、
                // attach 時に必ず現在の keyData.action を TAP に反映させる。
                val flickActionMap = rawFlickActionMap?.refreshSumireSpecialKeyTap(keyData)
                Log.d("FlickKeyboardView KeyType.CROSS_FLICK", "$flickActionMap")
                if (flickActionMap != null) {
                    val displayFlickActionMap =
                        buildSumireSpecialKeyDisplayActionMap(keyData, flickActionMap) { data, direction ->
                            resolveSumireSpecialKeyOverride(data, direction)
                        }
                    val controller = CrossFlickInputController(
                        context = context,
                        gestureConfigSource = gestureSessionConfigSource
                    ).apply {
                        setPopupOverlayHostProvider(popupWindowAnchorProvider)
                        setInputTextTransform(::transformInputTextForDisplay)
                        applyPopupViewStyleSet(
                            popupViewStyleSet.directional,
                            popupViewStyleSet.cross
                        )
                        this.listener = object : CrossFlickInputController.CrossFlickListener {
                            override fun onPress(action: KeyAction) {
                                onPress(action, FlickDirection.TAP)
                            }

                            override fun onPress(action: KeyAction, direction: FlickDirection) {
                                when (
                                    val resolved = resolveSumireSpecialKeyOverride(
                                        keyData,
                                        direction.toSumireSpecialKeyDirectionOrNull()
                                            ?: SumireSpecialKeyDirection.TAP
                                    )
                                ) {
                                    ResolvedSumireSpecialKeyAction.Default ->
                                        this@FlickKeyboardView.listener?.onPress(action)

                                    ResolvedSumireSpecialKeyAction.None -> Unit
                                    is ResolvedSumireSpecialKeyAction.Action ->
                                        this@FlickKeyboardView.listener?.onPress(resolved.action)

                                    is ResolvedSumireSpecialKeyAction.InputText ->
                                        this@FlickKeyboardView.listener?.onPress(
                                            KeyAction.Text(resolved.text)
                                        )
                                }
                            }

                            override fun onFlick(action: KeyAction, isFlick: Boolean) {
                                onFlickCommitted(
                                    fallbackAction = action,
                                    isFlick = isFlick,
                                    direction = if (isFlick) FlickDirection.UP else FlickDirection.TAP
                                )
                            }

                            override fun onFlick(
                                action: KeyAction,
                                isFlick: Boolean,
                                direction: FlickDirection
                            ) {
                                onFlickCommitted(action, isFlick, direction)
                            }

                            override fun onFlickCommitted(
                                fallbackAction: KeyAction?,
                                isFlick: Boolean,
                                direction: FlickDirection
                            ) {
                                dispatchSumireSpecialKeyRuntimeAction(
                                    keyData = keyData,
                                    flickDirection = direction,
                                    fallbackAction = fallbackAction,
                                    isFlick = isFlick,
                                    resolve = ::resolveSumireSpecialKeyOverride
                                ) { dispatchedAction, actionIsFlick ->
                                    dispatchCommittedKeyAction(
                                        keyData,
                                        dispatchedAction,
                                        actionIsFlick
                                    )
                                }
                            }

                            override fun onFlickLongPress(action: KeyAction) {
                                if (action !is KeyAction.Text) {
                                    doubleTapActionDispatcher.interrupt()
                                    this@FlickKeyboardView.listener?.onFlickActionLongPress(action)
                                }
                            }

                            override fun onFlickUpAfterLongPress(
                                action: KeyAction,
                                isFlick: Boolean
                            ) {
                                if (action !is KeyAction.Text) {
                                    doubleTapActionDispatcher.interrupt()
                                    this@FlickKeyboardView.listener?.onFlickActionUpAfterLongPress(
                                        action, isFlick = isFlick
                                    )
                                }
                            }

                            override fun onFlickLongPressCanceled(
                                action: KeyAction,
                                direction: FlickDirection
                            ) {
                                if (action !is KeyAction.Text) {
                                    this@FlickKeyboardView.listener?.onLongPressActionCanceled(
                                        action
                                    )
                                }
                            }
                        }

                        attach(keyView, displayFlickActionMap)
                    }

                    when (themeMode) {
                        "custom" -> {
                            controller.setPopupColors(
                                FlickPopupColorTheme(
                                    segmentColor = customSpecialKeyColor,
                                    segmentHighlightGradientStartColor = manipulateColor(
                                        customSpecialKeyColor,
                                        1.2f
                                    ),
                                    segmentHighlightGradientEndColor = manipulateColor(
                                        customSpecialKeyColor,
                                        1.2f
                                    ),
                                    centerGradientStartColor = customSpecialKeyColor,
                                    centerGradientEndColor = customSpecialKeyColor,
                                    centerHighlightGradientStartColor = manipulateColor(
                                        customSpecialKeyColor,
                                        1.2f
                                    ),
                                    centerHighlightGradientEndColor = manipulateColor(
                                        customSpecialKeyColor,
                                        1.2f
                                    ),
                                    separatorColor = customSpecialKeyTextColor,
                                    textColor = customSpecialKeyTextColor
                                )
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
                    val targetTextSizeSp = getKeyTextSizeSp(keyData)
                    val primaryTextSizePx = spToPx(targetTextSizeSp).toFloat()
                    val secondaryTextSizePx =
                        spToPx((targetTextSizeSp * 0.625f).coerceAtLeast(8f)).toFloat()

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
                                cornerRadius = baseCorner,
                                primaryTextSizePx = primaryTextSizePx,
                                secondaryTextSizePx = secondaryTextSizePx
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
                                cornerRadius = dpToPx(8).toFloat(),
                                primaryTextSizePx = primaryTextSizePx,
                                secondaryTextSizePx = secondaryTextSizePx
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
                            cornerRadius = 20f,
                            primaryTextSizePx = primaryTextSizePx,
                            secondaryTextSizePx = secondaryTextSizePx
                        )

                        keyView.background = segmentedDrawable
                        keyView.setTextColor(Color.TRANSPARENT)
                    }

                    if (liquidGlassEnable) {
                        keyView.setDrawableAlpha(liquidGlassKeyAlphaEnable)
                    }

                    val controller = StandardFlickInputController(
                        context = context,
                        gestureConfigSource = gestureSessionConfigSource
                    ).apply {
                        setPopupWindowAnchorProvider(popupWindowAnchorProvider)
                        setInputTextTransform(::transformInputTextForDisplay)
                        applyPopupViewStyle(popupViewStyleSet.standard)
                        this.listener =
                            object : StandardFlickInputController.StandardFlickListener {
                                override fun onPress(character: String) {
                                    notifyTextPress(keyData, character)
                                }

                                override fun onFlick(character: String) {
                                    dispatchCommittedKeyAction(
                                        keyData,
                                        KeyAction.Text(character),
                                        isFlick = true
                                    )
                                }

                                override fun onSelectionChanged(
                                    character: String?,
                                    isFlick: Boolean
                                ) = updateTextPreview(character, isFlick)

                                override fun onCanceled() = cancelTextPreview()
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
                    val controller = CrossFlickInputController(
                        context = context,
                        gestureConfigSource = gestureSessionConfigSource
                    ).apply {
                        setPopupOverlayHostProvider(popupWindowAnchorProvider)
                        setInputTextTransform(::transformInputTextForDisplay)
                        applyPopupViewStyleSet(
                            popupViewStyleSet.directional,
                            popupViewStyleSet.cross
                        )
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
                                    is KeyAction.Text -> notifyTextPress(keyData, action.text)
                                    else -> this@FlickKeyboardView.listener?.onPress(action)
                                }
                            }

                            override fun onFlick(action: KeyAction, isFlick: Boolean) {
                                dispatchCommittedKeyAction(keyData, action, isFlick)
                            }

                            override fun onTextSelectionChanged(
                                text: String?,
                                isFlick: Boolean
                            ) = updateTextPreview(text, isFlick)

                            override fun onCanceled() = cancelTextPreview()

                            override fun onFlickLongPress(action: KeyAction) {
                                if (action !is KeyAction.Text) {
                                    doubleTapActionDispatcher.interrupt()
                                    this@FlickKeyboardView.listener?.onFlickActionLongPress(action)
                                }
                            }

                            override fun onFlickUpAfterLongPress(
                                action: KeyAction,
                                isFlick: Boolean
                            ) {
                                if (action !is KeyAction.Text) {
                                    doubleTapActionDispatcher.interrupt()
                                    this@FlickKeyboardView.listener?.onFlickActionUpAfterLongPress(
                                        action,
                                        isFlick
                                    )
                                }
                            }

                            override fun onFlickLongPressCanceled(
                                action: KeyAction,
                                direction: FlickDirection
                            ) {
                                if (action !is KeyAction.Text) {
                                    this@FlickKeyboardView.listener?.onLongPressActionCanceled(
                                        action
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

            KeyType.CENTER_GUIDE_FLICK -> {
                val flickActionMap = layout.flickKeyMaps[keyData.keyId]?.firstOrNull()
                    ?: layout.flickKeyMaps[keyData.label]?.firstOrNull()
                if (flickActionMap != null) {
                    val stringMap = extractInputMap(flickActionMap)
                    if (keyView is AutoSizeButton) {
                        applyGuideLabels(keyView, keyData, stringMap)
                    }

                    val controller = CenterGuideFlickInputController(
                        context = context,
                        gestureConfigSource = gestureSessionConfigSource
                    ).apply {
                        setPopupWindowAnchorProvider(popupWindowAnchorProvider)
                        setInputTextTransform(::transformInputTextForDisplay)
                        applyPopupViewStyle(popupViewStyleSet.tfbi)
                        listener = object : CenterGuideFlickInputController.Listener {
                            override fun onPress(character: String) {
                                notifyTextPress(keyData, character)
                            }

                            override fun onCommit(character: String, isFlick: Boolean) {
                                dispatchCommittedKeyAction(
                                    keyData,
                                    KeyAction.Text(character),
                                    isFlick = isFlick
                                )
                            }

                            override fun onSelectionChanged(
                                character: String?,
                                isFlick: Boolean
                            ) = updateTextPreview(character, isFlick)

                            override fun onCanceled() = cancelTextPreview()
                        }

                        attach(keyView, stringMap)
                    }

                    if (themeMode == "custom") {
                        controller.setPopupColors(
                            backgroundColor = customSpecialKeyColor,
                            highlightedColor = manipulateColor(customSpecialKeyColor, 1.2f),
                            textColor = customSpecialKeyTextColor
                        )
                    }

                    centerGuideFlickControllers.add(controller)
                    return controller
                }
            }

            KeyType.NORMAL -> {
                keyData.action?.let { action ->
                    Log.d("FlickKeyboardView KeyType.NORMAL", "key data: $keyData")
                    fun currentAction(): KeyAction {
                        return keyData.keyId
                            ?.let { dynamicKeyMap[it]?.keyData?.action }
                            ?: action
                    }

                    val controller = TapLongPressInputController(
                        gestureConfigSource = gestureSessionConfigSource
                    ).apply {
                        attach(
                            keyView,
                            object : TapLongPressInputController.Listener {
                                override fun onPress() {
                                    when (
                                        val resolved = resolveSumireSpecialKeyOverride(
                                            keyData,
                                            SumireSpecialKeyDirection.TAP
                                        )
                                    ) {
                                        ResolvedSumireSpecialKeyAction.Default ->
                                            this@FlickKeyboardView.listener?.onPress(
                                                currentAction()
                                            )

                                        ResolvedSumireSpecialKeyAction.None -> Unit
                                        is ResolvedSumireSpecialKeyAction.Action ->
                                            this@FlickKeyboardView.listener?.onPress(
                                                resolved.action
                                            )

                                        is ResolvedSumireSpecialKeyAction.InputText ->
                                            this@FlickKeyboardView.listener?.onPress(
                                                KeyAction.Text(resolved.text)
                                            )
                                    }
                                }

                                override fun onTap() {
                                    if (
                                        dispatchResolvedSumireSpecialKeyAction(
                                            keyData,
                                            resolveSumireSpecialKeyOverride(
                                                keyData,
                                                SumireSpecialKeyDirection.TAP
                                            ),
                                            isFlick = false
                                        )
                                    ) {
                                        return
                                    }
                                    val currentAction = currentAction()
                                    Log.d(
                                        "FlickKeyboardView KeyType.NORMAL",
                                        "currentAction: $currentAction"
                                    )
                                    dispatchCommittedKeyAction(
                                        keyData,
                                        currentAction,
                                        isFlick = false
                                    )
                                }

                                override fun onLongPress() {
                                    doubleTapActionDispatcher.interrupt()
                                    this@FlickKeyboardView.listener?.onActionLongPress(
                                        currentAction()
                                    )
                                }

                                override fun onUpAfterLongPress() {
                                    doubleTapActionDispatcher.interrupt()
                                    this@FlickKeyboardView.listener?.onActionUpAfterLongPress(
                                        currentAction()
                                    )
                                }

                                override fun onLongPressCanceled() {
                                    this@FlickKeyboardView.listener?.onLongPressActionCanceled(
                                        currentAction()
                                    )
                                }
                            }
                        )
                    }
                    tapLongPressControllers.add(controller)
                    return controller
                }
                return null
            }

            KeyType.TWO_STEP_FLICK -> {
                val twoStepMap = layout.twoStepFlickKeyMaps[keyData.keyId]
                    ?: layout.twoStepFlickKeyMaps[keyData.label]
                val twoStepLongPressMap = layout.twoStepLongPressKeyMaps[keyData.keyId]
                    ?: layout.twoStepLongPressKeyMaps[keyData.label]

                if (twoStepMap != null) {
                    if (keyView is AutoSizeButton) {
                        applyTwoStepGuideLabels(keyView, keyData, twoStepMap)
                    }

                    val controller = TfbiInputController(
                        context = context,
                        gestureConfigSource = gestureSessionConfigSource
                    ).apply {
                        setPopupWindowAnchorProvider(popupWindowAnchorProvider)
                        setPopupPresentationMode(tfbiPopupPresentationMode)
                        setTfbiFlickStartPositionMode(tfbiFlickStartPositionMode)
                        setInputTextTransform(::transformInputTextForDisplay)
                        applyPopupViewStyle(popupViewStyleSet.tfbi)
                        this.listener = object : TfbiInputController.TfbiListener {
                            override fun onPress(
                                first: TfbiFlickDirection,
                                second: TfbiFlickDirection
                            ) {
                                notifyTextPress(keyData, twoStepMap[first]?.get(second) ?: "")
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
                                    dispatchCommittedKeyAction(
                                        keyData,
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

                                dispatchCommittedKeyAction(
                                    keyData = keyData,
                                    action = KeyAction.Text(output),
                                    isFlick = !(first == TfbiFlickDirection.TAP &&
                                        second == TfbiFlickDirection.TAP)
                                )
                                return true
                            }

                            override fun onSelectionChanged(
                                first: TfbiFlickDirection,
                                second: TfbiFlickDirection,
                                isLongPress: Boolean
                            ) {
                                val longPressText = if (isLongPress) {
                                    twoStepLongPressMap?.get(first)?.get(second)
                                } else {
                                    null
                                }
                                updateTextPreview(
                                    longPressText?.takeIf(String::isNotEmpty)
                                        ?: twoStepMap[first]?.get(second),
                                    first != TfbiFlickDirection.TAP ||
                                        second != TfbiFlickDirection.TAP
                                )
                            }

                            override fun onCanceled() = cancelTextPreview()
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

            KeyType.FLICK_LONG_PRESS -> {
                val flickLongPressMap = layout.twoStepFlickKeyMaps[keyData.keyId]
                    ?: layout.twoStepFlickKeyMaps[keyData.label]
                    ?: emptyMap()
                val flickLongPressHoldMap = layout.twoStepLongPressKeyMaps[keyData.keyId]
                    ?: layout.twoStepLongPressKeyMaps[keyData.label]
                    ?: emptyMap()

                val normalMap = extractFlickLongPressMap(flickLongPressMap).toMutableMap()
                if (normalMap[TfbiFlickDirection.TAP].orEmpty().isEmpty()) {
                    textOutputFromAction(keyData.action)
                        .takeIf { it.isNotEmpty() }
                        ?.let { normalMap[TfbiFlickDirection.TAP] = it }
                }
                val holdMap = extractFlickLongPressMap(flickLongPressHoldMap)

                if (normalMap.isNotEmpty() || holdMap.isNotEmpty()) {
                    if (keyView is AutoSizeButton) {
                        applyTwoStepGuideLabels(keyView, keyData, flickLongPressMap)
                    }

                    val controller = FlickLongPressInputController(
                        context = context,
                        gestureConfigSource = gestureSessionConfigSource
                    ).apply {
                        setPopupWindowAnchorProvider(popupWindowAnchorProvider)
                        setInputTextTransform(::transformInputTextForDisplay)
                        applyPopupViewStyle(popupViewStyleSet.tfbi)
                        this.listener = object : FlickLongPressInputController.Listener {
                            override fun onPress(character: String) {
                                notifyTextPress(keyData, character)
                            }

                            override fun onCommit(character: String, isFlick: Boolean) {
                                dispatchCommittedKeyAction(
                                    keyData,
                                    KeyAction.Text(character),
                                    isFlick = isFlick
                                )
                            }
                        }

                        attach(
                            view = keyView,
                            normalMap = normalMap,
                            longPressMap = holdMap
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

                    flickLongPressControllers.add(controller)
                    return controller
                }
            }

            KeyType.STICKY_TWO_STEP_FLICK -> {
                val twoStepMap = layout.twoStepFlickKeyMaps[keyData.keyId]
                    ?: layout.twoStepFlickKeyMaps[keyData.label]
                if (twoStepMap != null) {
                    if (keyView is AutoSizeButton) {
                        applyTwoStepGuideLabels(keyView, keyData, twoStepMap)
                    }

                    val controller = TfbiStickyFlickController(
                        context = context,
                        gestureConfigSource = gestureSessionConfigSource
                    ).apply {
                        setPopupWindowAnchorProvider(popupWindowAnchorProvider)
                        setPopupPresentationMode(tfbiPopupPresentationMode)
                        setTfbiFlickStartPositionMode(tfbiFlickStartPositionMode)
                        setInputTextTransform(::transformInputTextForDisplay)
                        applyPopupViewStyle(popupViewStyleSet.tfbi)
                        this.listener = object : TfbiStickyFlickController.TfbiListener {
                            override fun onPress(
                                first: TfbiFlickDirection,
                                second: TfbiFlickDirection
                            ) {
                                notifyTextPress(keyData, twoStepMap[first]?.get(second) ?: "")
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
                                    dispatchCommittedKeyAction(
                                        keyData,
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

                    when (themeMode) {
                        "custom" -> {
                            controller.setPopupColors(
                                backgroundColor = customSpecialKeyColor,
                                highlightedColor = manipulateColor(customSpecialKeyColor, 1.2f),
                                textColor = customSpecialKeyTextColor
                            )
                        }
                    }

                    stickyTfbiControllers.add(controller)
                    return controller
                }
            }

            KeyType.HIERARCHICAL_FLICK -> {
                val statefulNode = layout.hierarchicalFlickMaps[keyData.label]

                if (statefulNode != null) {
                    if (keyView is AutoSizeButton) {
                        applyHierarchicalGuideLabels(keyView, keyData, statefulNode.normalMap)
                    }

                    Log.d(
                        "AttachBehavior",
                        "-> Attaching TfbiHierarchicalFlickController for ${keyData.label}"
                    )

                    val controller = TfbiHierarchicalFlickController(
                        context = context,
                        gestureConfigSource = gestureSessionConfigSource
                    ).apply {
                        setPopupWindowAnchorProvider(popupWindowAnchorProvider)
                        setPopupPresentationMode(tfbiPopupPresentationMode)
                        setTfbiFlickStartPositionMode(tfbiFlickStartPositionMode)
                        setInputTextTransform(::transformInputTextForDisplay)
                        setModeSwitchAngleMargin(hierarchicalFlickModeSwitchAngleMargin)
                        applyPopupViewStyle(popupViewStyleSet.tfbi)
                        this.listener = object : TfbiHierarchicalFlickController.TfbiListener {
                            override fun onPress(character: String) {
                                notifyTextPress(keyData, character)
                            }

                            override fun onFlick(character: String) {
                                Log.d(
                                    "FlickKeyboardView KeyType.HIERARCHICAL_FLICK",
                                    "Char: $character"
                                )
                                if (character.isNotEmpty()) {
                                    dispatchCommittedKeyAction(
                                        keyData,
                                        KeyAction.Text(character),
                                        isFlick = true
                                    )
                                }
                            }

                            override fun onSelectionChanged(
                                character: String?,
                                isFlick: Boolean
                            ) = updateTextPreview(character, isFlick)

                            override fun onCanceled() = cancelTextPreview()

                            override fun onModeChanged(
                                newLabel: String,
                                activeRootMap: Map<TfbiFlickDirection, TfbiFlickNode>
                            ) {
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
                                if (keyView is AutoSizeButton) {
                                    applyHierarchicalGuideLabels(
                                        keyView,
                                        newVisualKeyData,
                                        activeRootMap
                                    )
                                }
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

    private fun resolveSumireSpecialKeyOverride(
        keyData: KeyData,
        direction: SumireSpecialKeyDirection
    ): ResolvedSumireSpecialKeyAction {
        if (!keyData.isSpecialKey) return ResolvedSumireSpecialKeyAction.Default
        if (keyData.keyId.isNullOrBlank()) return ResolvedSumireSpecialKeyAction.Default

        val resolver = sumireSpecialKeyActionResolver
            ?: return ResolvedSumireSpecialKeyAction.Default
        val layoutType = sumireSpecialKeyLayoutType
            ?: return ResolvedSumireSpecialKeyAction.Default
        val inputMode = sumireSpecialKeyInputMode
            ?: return ResolvedSumireSpecialKeyAction.Default

        return resolver(layoutType, inputMode, keyData, direction)
    }

    private fun dispatchCommittedKeyAction(
        keyData: KeyData,
        action: KeyAction,
        isFlick: Boolean
    ) {
        val dispatch = {
            if (isFlick) {
                dispatchNonTapActionWithoutPreviewCancel(action, isFlick = true)
            } else {
                doubleTapActionDispatcher.onCommittedTap(
                    keyIdentity = keyData.keyId
                        ?: "legacy:${keyData.row}:${keyData.column}:${keyData.keyType}",
                    normalAction = action,
                    binding = keyData.effectiveDoubleTapBinding(action)
                )
            }
        }
        val textAction = action as? KeyAction.Text
        if (textAction != null && canPreviewText(keyData, textAction)) {
            flickTextPreviewEmitter.commit(
                FlickTextSelection(textAction.text, isFlick),
                dispatch
            )
            previewKeyData = null
        } else {
            cancelTextPreview()
            dispatch()
        }
    }

    private fun dispatchNonTapAction(action: KeyAction, isFlick: Boolean) {
        cancelTextPreview()
        dispatchNonTapActionWithoutPreviewCancel(action, isFlick)
    }

    private fun dispatchNonTapActionWithoutPreviewCancel(action: KeyAction, isFlick: Boolean) {
        doubleTapActionDispatcher.interrupt()
        listener?.onAction(action, isFlick)
    }

    private fun dispatchResolvedSumireSpecialKeyAction(
        keyData: KeyData,
        resolved: ResolvedSumireSpecialKeyAction,
        isFlick: Boolean
    ): Boolean {
        return dispatchResolvedSumireSpecialKeyAction(resolved, isFlick) { action, actionIsFlick ->
            dispatchCommittedKeyAction(keyData, action, actionIsFlick)
        }
    }

    private fun notifyTextPress(keyData: KeyData, character: String) {
        val action = KeyAction.Text(character)
        if (character.isNotEmpty()) listener?.onPress(action)
        if (character.isNotEmpty() && canPreviewText(keyData, action)) {
            previewKeyData = keyData
            flickTextPreviewEmitter.begin(FlickTextSelection(character, false))
        } else {
            cancelTextPreview()
        }
    }

    private fun updateTextPreview(character: String?, isFlick: Boolean) {
        if (previewKeyData == null) return
        flickTextPreviewEmitter.update(FlickTextSelection(character, isFlick))
    }

    private fun cancelTextPreview() {
        flickTextPreviewEmitter.cancel()
        previewKeyData = null
    }

    private fun canPreviewText(keyData: KeyData, action: KeyAction.Text): Boolean {
        return !keyData.isSpecialKey && keyData.effectiveDoubleTapBinding(action) == null
    }

    private fun textOutputFromFlickAction(action: FlickAction?): String? {
        return when (action) {
            is FlickAction.Input -> action.char
            is FlickAction.Action -> (action.action as? KeyAction.Text)?.text
            null -> null
        }?.takeIf(String::isNotEmpty)
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

            is CenterGuideFlickInputController -> {
                controller.cancel()
                centerGuideFlickControllers.remove(controller)
            }

            is StandardFlickInputController -> {
                controller.cancel()
                standardFlickControllers.remove(controller)
            }

            is TfbiInputController -> {
                controller.cancel()
                tfbiControllers.remove(controller)
            }

            is FlickLongPressInputController -> {
                controller.cancel()
                flickLongPressControllers.remove(controller)
            }

            is TfbiStickyFlickController -> {
                controller.cancel()
                stickyTfbiControllers.remove(controller)
            }

            is TfbiHierarchicalFlickController -> {
                controller.cancel()
                hierarchicalTfbiControllers.remove(controller)
            }

            is TapLongPressInputController -> {
                controller.cancel()
                tapLongPressControllers.remove(controller)
            }
        }
    }

    private fun updateKeyVisuals(view: View, keyData: KeyData) {
        when (view) {
            is AppCompatImageButton -> {
                KeyIconResolver.setImage(view, keyData)
                applyImageButtonTint(view, keyData)
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

    /**
     * A gesture must not depend on the IME window-local origin after ACTION_DOWN.
     *
     * Candidate-surface relayout can move the IME window origin between two input events even
     * though the keyboard remains at the same physical position. Keeping both the target and its
     * display-space origin makes the complete gesture independent from that transient relayout.
     */
    private data class MotionTarget(
        val view: View,
        val displayOriginX: Float,
        val displayOriginY: Float
    )

    private val motionTargets = mutableMapOf<Int, MotionTarget>()
    private val pointerDownTime = mutableMapOf<Int, Long>()
    private val TAG = "FlickKeyboardViewTouch"

    private fun cancelTrackedTouchState() {
        cancelTextPreview()
        if (motionTargets.isEmpty() && pointerDownTime.isEmpty()) return

        val eventTime = SystemClock.uptimeMillis()
        motionTargets.toList().forEach { (trackedPointerId, target) ->
            var cancelEvent: MotionEvent? = null
            try {
                cancelEvent = MotionEvent.obtain(
                    pointerDownTime[trackedPointerId] ?: eventTime,
                    eventTime,
                    MotionEvent.ACTION_CANCEL,
                    target.view.width / 2f,
                    target.view.height / 2f,
                    0
                )
                target.view.dispatchTouchEvent(cancelEvent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to dispatch ACTION_CANCEL while clearing touch state", e)
            } finally {
                cancelEvent?.recycle()
            }

            try {
                target.view.cancelPendingInputEvents()
                target.view.isPressed = false
                target.view.isSelected = false
                target.view.refreshDrawableState()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to reset tracked touch target state", e)
            }
        }

        motionTargets.clear()
        pointerDownTime.clear()
    }

    fun resetTouchStateForFastInputTest() {
        doubleTapActionDispatcher.cancel()
        cancelTextPreview()
        cancelTrackedTouchState()
        listener?.onLongPressActionCanceled(KeyAction.Cancel)
        flickControllers.forEach { it.cancel() }
        crossFlickControllers.forEach { it.cancel() }
        centerGuideFlickControllers.forEach { it.cancel() }
        standardFlickControllers.forEach { it.cancel() }
        tfbiControllers.forEach { it.cancel() }
        flickLongPressControllers.forEach { it.cancel() }
        stickyTfbiControllers.forEach { it.cancel() }
        hierarchicalTfbiControllers.forEach { it.cancel() }
        tapLongPressControllers.forEach { it.resetGestureStateForFastInputTest() }
        setCursorMode(false)
    }

    private fun findTargetView(displayX: Float, displayY: Float): MotionTarget? {
        val location = IntArray(2)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != View.VISIBLE || !child.isEnabled) continue
            child.getLocationOnScreen(location)
            hitRect.set(
                location[0],
                location[1],
                location[0] + child.width,
                location[1] + child.height
            )
            if (hitRect.contains(displayX.toInt(), displayY.toInt())) {
                return MotionTarget(
                    view = child,
                    displayOriginX = location[0].toFloat(),
                    displayOriginY = location[1].toFloat()
                )
            }
        }

        return null
    }

    private fun MotionEvent.displayX(pointerIndex: Int): Float {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getRawX(pointerIndex)
        } else {
            getX(pointerIndex) + rawX - x
        }
    }

    private fun MotionEvent.displayY(pointerIndex: Int): Float {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getRawY(pointerIndex)
        } else {
            getY(pointerIndex) + rawY - y
        }
    }

    private fun dispatchPointerEvent(
        source: MotionEvent,
        pointerIndex: Int,
        target: MotionTarget,
        action: Int,
        downTime: Long,
        eventTime: Long = source.eventTime
    ) {
        val displayX = source.displayX(pointerIndex)
        val displayY = source.displayY(pointerIndex)
        val childEvent = MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            displayX,
            displayY,
            source.metaState
        )
        childEvent.offsetLocation(
            -target.displayOriginX,
            -target.displayOriginY
        )
        target.view.dispatchTouchEvent(childEvent)
        childEvent.recycle()
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
                        dispatchNonTapAction(action2, false)
                        cursorInitialX = currentX
                        cursorInitialY = currentY
                    } else if (abs(dy) > abs(dx) && abs(dy) > threshold) {
                        val action2 =
                            if (dy < 0f) KeyAction.MoveCursorUp else KeyAction.MoveCursorDown
                        dispatchNonTapAction(action2, false)
                        cursorInitialX = currentX
                        cursorInitialY = currentY
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val actionToDispatch =
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            MotionEvent.ACTION_UP
                        } else {
                            MotionEvent.ACTION_CANCEL
                        }

                    dispatchEndEventToTrackedTargets(event, actionToDispatch)

                    if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        listener?.onLongPressActionCanceled(KeyAction.Cancel)
                    }

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
                val targetView = findTargetView(
                    displayX = event.displayX(pointerIndex),
                    displayY = event.displayY(pointerIndex)
                )

                targetView?.let { target ->
                    motionTargets[pointerId] = target
                    dispatchPointerEvent(
                        source = event,
                        pointerIndex = pointerIndex,
                        target = target,
                        action = MotionEvent.ACTION_DOWN,
                        downTime = event.downTime
                    )
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
                            // A second finger starts a new key gesture; the first finger should be
                            // committed at its current position, not canceled. The current
                            // position can be the last movement sample carried by this
                            // ACTION_POINTER_DOWN event. Deliver it before ACTION_UP so the child
                            // controller can resolve a fast flick instead of committing TAP.
                            dispatchPointerEvent(
                                source = event,
                                pointerIndex = existingPointerIndex,
                                target = target,
                                action = MotionEvent.ACTION_MOVE,
                                downTime = downTime
                            )
                            dispatchPointerEvent(
                                source = event,
                                pointerIndex = existingPointerIndex,
                                target = target,
                                action = MotionEvent.ACTION_UP,
                                downTime = downTime
                            )
                        }
                    }

                    val matchingEntry =
                        dynamicKeyMap.entries.find { it.value.view == target?.view }
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

                pointerDownTime[newPointerId] = event.eventTime
                val targetView = findTargetView(
                    displayX = event.displayX(pointerIndex),
                    displayY = event.displayY(pointerIndex)
                )

                targetView?.let { target ->
                    motionTargets[newPointerId] = target
                    dispatchPointerEvent(
                        source = event,
                        pointerIndex = pointerIndex,
                        target = target,
                        action = MotionEvent.ACTION_DOWN,
                        downTime = event.eventTime
                    )
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pId = event.getPointerId(i)
                    val target = motionTargets[pId]
                    val downTime = pointerDownTime[pId]

                    if (target != null && downTime != null) {
                        dispatchPointerEvent(
                            source = event,
                            pointerIndex = i,
                            target = target,
                            action = MotionEvent.ACTION_MOVE,
                            downTime = downTime
                        )
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (visibility != View.VISIBLE) {
                    return false
                }

                Log.d(
                    "FlickKeyboardView",
                    "ACTION_POINTER_UP: pointerId=$pointerId, index=$pointerIndex"
                )

                motionTargets[pointerId]?.let { target ->
                    val downTime = pointerDownTime[pointerId]!!

                    Log.d("FlickKeyboardView", "ACTION_POINTER_UP: Found target! $target")

                    dispatchPointerEvent(
                        source = event,
                        pointerIndex = pointerIndex,
                        target = target,
                        action = MotionEvent.ACTION_UP,
                        downTime = downTime
                    )
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
                val actionToDispatch =
                    if (action == MotionEvent.ACTION_UP) MotionEvent.ACTION_UP else MotionEvent.ACTION_CANCEL

                motionTargets[pointerId]?.let { target ->
                    val downTime = pointerDownTime[pointerId]!!
                    dispatchPointerEvent(
                        source = event,
                        pointerIndex = pointerIndex,
                        target = target,
                        action = actionToDispatch,
                        downTime = downTime
                    )
                }

                if (action == MotionEvent.ACTION_CANCEL) {
                    listener?.onLongPressActionCanceled(KeyAction.Cancel)
                }

                motionTargets.clear()
                pointerDownTime.clear()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!controllerRebindPending) return

        controllerRebindPending = false
        post {
            if (isAttachedToWindow) {
                Log.d(
                    "FlickKeyboardView",
                    "Rebuilding input controllers after window reattach"
                )
                rebuildCurrentKeyboard()
            } else {
                controllerRebindPending = true
            }
        }
    }

    override fun onDetachedFromWindow() {
        controllerRebindPending = true
        doubleTapActionDispatcher.cancel()
        cancelTextPreview()
        cancelTrackedTouchState()
        super.onDetachedFromWindow()
        listener?.onLongPressActionCanceled(KeyAction.Cancel)
        flickControllers.forEach { it.cancel() }
        crossFlickControllers.forEach { it.cancel() }
        centerGuideFlickControllers.forEach { it.cancel() }
        standardFlickControllers.forEach { it.cancel() }
        tfbiControllers.forEach { it.cancel() }
        flickLongPressControllers.forEach { it.cancel() }
        stickyTfbiControllers.forEach { it.cancel() }
        hierarchicalTfbiControllers.forEach { it.cancel() }
        tapLongPressControllers.forEach { it.cancel() }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView == this && visibility != View.VISIBLE) {
            doubleTapActionDispatcher.cancel()
            cancelTextPreview()
            cancelTrackedTouchState()
            listener?.onLongPressActionCanceled(KeyAction.Cancel)
        }
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

    private fun dispatchEndEventToTrackedTargets(
        sourceEvent: MotionEvent,
        actionToDispatch: Int
    ) {
        motionTargets.forEach { (trackedPointerId, target) ->
            val trackedPointerIndex = sourceEvent.findPointerIndex(trackedPointerId)
            if (trackedPointerIndex == -1) {
                target.view.isPressed = false
                target.view.isSelected = false
                target.view.refreshDrawableState()
                return@forEach
            }

            val downTime = pointerDownTime[trackedPointerId] ?: sourceEvent.downTime
            dispatchPointerEvent(
                source = sourceEvent,
                pointerIndex = trackedPointerIndex,
                target = target,
                action = actionToDispatch,
                downTime = downTime
            )
        }
    }

    private fun clearSpaceKeyPressedState() {
        dynamicKeyMap.values
            .filter { keyInfo ->
                keyInfo.keyData.action == KeyAction.Space
            }
            .forEach { keyInfo ->
                keyInfo.view.isPressed = false
                keyInfo.view.isSelected = false
                keyInfo.view.refreshDrawableState()
            }
    }

}
