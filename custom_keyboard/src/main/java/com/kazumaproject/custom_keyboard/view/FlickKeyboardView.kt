package com.kazumaproject.custom_keyboard.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
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
import android.widget.Button
import android.widget.GridLayout
import androidx.annotation.AttrRes
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
import com.kazumaproject.custom_keyboard.controller.GridFlickInputController
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
import kotlin.math.sqrt

class FlickKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : GridLayout(context, attrs, defStyleAttr) {

    interface OnKeyboardActionListener {
        fun onKey(text: String, isFlick: Boolean)
        fun onAction(action: KeyAction, view: View, isFlick: Boolean)
        fun onActionLongPress(action: KeyAction)
        fun onActionUpAfterLongPress(action: KeyAction)
        fun onFlickDirectionChanged(direction: FlickDirection)
        fun onFlickActionLongPress(action: KeyAction)
        fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean)
    }

    private var listener: OnKeyboardActionListener? = null
    private val flickControllers = mutableListOf<CustomAngleFlickController>()
    private val crossFlickControllers = mutableListOf<CrossFlickInputController>()
    private val standardFlickControllers = mutableListOf<StandardFlickInputController>()
    private val petalFlickControllers = mutableListOf<GridFlickInputController>()
    private val tfbiControllers = mutableListOf<TfbiInputController>()
    private val stickyTfbiControllers = mutableListOf<TfbiStickyFlickController>()
    private val hierarchicalTfbiControllers = mutableListOf<TfbiHierarchicalFlickController>()

    private val hitRect = Rect()
    private var flickSensitivity: Int = 100
    private var defaultTextSize = 14f
    private var isCursorMode: Boolean = false
    private var cursorInitialX = 0f
    private var cursorInitialY = 0f

    private var liquidGlassEnable: Boolean = false

    /**
     * 動的キー（keyIdを持つキー）の情報を保持するためのマップ
     * keyId: String -> KeyInfo
     */
    private val dynamicKeyMap = mutableMapOf<String, KeyInfo>()

    /**
     * flickKeyMaps などにアクセスするために、現在設定されているレイアウトを保持
     */
    private var currentLayout: KeyboardLayout? = null

    /**
     * 動的キーのViewと最新のKeyData、コントローラー、インデックスを保持する
     */
    private data class KeyInfo(
        var view: View,
        var keyData: KeyData,
        var controller: Any? = null,
        val index: Int
    )

    // Theme Variables (Initialized with defaults)
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

    fun setOnKeyboardActionListener(listener: OnKeyboardActionListener) {
        this.listener = listener
    }

    fun setFlickSensitivityValue(sensitivity: Int) {
        flickSensitivity = sensitivity
    }

    fun setDefaultTextSize(textSize: Float) {
        this.defaultTextSize = textSize
    }

    fun setCursorMode(enabled: Boolean) {
        isCursorMode = enabled
    }

    fun setAngleAndRange(
        range: Map<FlickDirection,
                Pair<Float, Float>>,
        circularPopViewScale: Float
    ) {
        this.customAngleAndRange = range
        this.circularViewScale = circularPopViewScale
    }

    /**
     * テーマ設定を一括で適用するメイン関数
     * メンバ変数に値を保存してからテーマを適用します。
     * @param currentNightMode res.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK の値
     */
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
        // メンバ変数に代入
        this.themeMode = themeMode

        // Int型の currentNightMode から Boolean型の isNightMode を判定
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

    /**
     * 色の明るさを調整するヘルパー関数 (QWERTYKeyboardViewと統一)
     * @param color 元の色
     * @param factor 1.0より大＝明るく、1.0より小＝暗く
     */
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

        // 1. 既存のリソースをすべてクリア
        this.removeAllViews()
        flickControllers.forEach { it.cancel() }
        flickControllers.clear()
        crossFlickControllers.forEach { it.cancel() }
        crossFlickControllers.clear()
        standardFlickControllers.forEach { it.cancel() }
        standardFlickControllers.clear()
        petalFlickControllers.forEach { it.cancel() }
        petalFlickControllers.clear()
        tfbiControllers.forEach { it.cancel() }
        tfbiControllers.clear()
        stickyTfbiControllers.forEach { it.cancel() }
        stickyTfbiControllers.clear()
        hierarchicalTfbiControllers.forEach { it.cancel() }
        hierarchicalTfbiControllers.clear()

        dynamicKeyMap.clear()
        currentLayout = layout

        this.columnCount = layout.columnCount
        this.rowCount = layout.rowCount
        this.isFocusable = false

        // 2. キーを順に生成してアタッチ
        layout.keys.forEach { keyData ->
            val index = this.childCount // addViewする前の現在のView数をインデックスとして使用

            // 3. ヘルパー関数でViewを生成
            val keyView = createKeyView(keyData)

            // 4. ヘルパー関数でビヘイビア（リスナーやコントローラー）をアタッチ
            val controller = attachKeyBehavior(keyView, keyData)

            // 5. 動的キーならマップに保存
            keyData.keyId?.let { id ->
                dynamicKeyMap[id] = KeyInfo(keyView, keyData, controller, index)
            }

            this.addView(keyView)
        }
    }

    /**
     * 指定されたkeyIdを持つキーの表示と動作を、新しいstateIndexに基づいて更新します。
     * このメソッドは、必要に応じてViewの再生成とコントローラーの再アタッチを行います。
     *
     * @param keyId 更新するキーのID (e.g., "enter_key")
     * @param stateIndex 適用する新しい状態のインデックス
     */
    fun updateDynamicKey(keyId: String, stateIndex: Int) {
        // 1. 更新対象のキー情報をマップから取得
        val info = dynamicKeyMap[keyId] ?: return
        val states = info.keyData.dynamicStates ?: return
        val newState = states.getOrNull(stateIndex) ?: states.firstOrNull() ?: return

        // 2. 新しいKeyDataをメモリ上で作成
        val newKeyData = info.keyData.copy(
            label = newState.label ?: "",
            action = newState.action,
            drawableResId = newState.drawableResId
        )

        // 3. Viewタイプの変更チェック
        val oldView = info.view
        val newViewIsIcon = newKeyData.isSpecialKey && newKeyData.drawableResId != null
        val newViewIsText = !newViewIsIcon

        val oldViewIsIcon = oldView is AppCompatImageButton
        val oldViewIsText = !oldViewIsIcon

        val needsNewView = (oldViewIsIcon && newViewIsText) || (oldViewIsText && newViewIsIcon)

        // 4. 古いビヘイビアをデタッチ
        detachKeyBehavior(info.controller)

        val newView: View
        if (needsNewView) {
            // Viewタイプが異なる場合：Viewを再生成して差し替える
            Log.d("FlickKeyboardView", "updateDynamicKey: Replacing View for $keyId")
            newView = createKeyView(newKeyData) // 新しいViewを生成
            newView.layoutParams = oldView.layoutParams // レイアウトパラメータは引き継ぐ

            this.removeViewAt(info.index) // 古いViewをGridから削除
            this.addView(newView, info.index) // 新しいViewを同じ位置に追加
        } else {
            // Viewタイプが同じ場合：Viewの表示内容だけ更新
            Log.d("FlickKeyboardView", "updateDynamicKey: Updating View for $keyId")
            newView = oldView
            updateKeyVisuals(newView, newKeyData) // 表示だけ更新
        }

        // 5. 新しいビヘイビアをアタッチ
        val newController = attachKeyBehavior(newView, newKeyData)

        // 6. 管理マップの情報を更新
        info.view = newView
        info.keyData = newKeyData
        info.controller = newController
    }

    /** keyDataに基づいてViewを生成し、基本的な設定（背景、テキスト、パディング等）を行います */
    private fun createKeyView(keyData: KeyData): View {
        val (leftInset, topInset, rightInset, bottomInset) = if (keyData.isSpecialKey) {
            listOf(6, 12, 6, 6)
        } else {
            listOf(6, 9, 6, 9)
        }

        val isDarkTheme = context.isDarkThemeOn()
        // 角丸サイズを統一
        val commonCornerRadius = dpToPx(8).toFloat()

        val keyView: View = if (keyData.isSpecialKey && keyData.drawableResId != null) {
            // ■■■ 1. 画像ボタン (AppCompatImageButton) ■■■
            AppCompatImageButton(context).apply {
                isFocusable = false
                elevation = 0f
                setImageResource(keyData.drawableResId)
                contentDescription = keyData.label
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE

                if (themeMode == "custom") {
                    // 影の分だけ中身を小さく見せる必要があるためパディングを設定
                    setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                }

                val originalBg = ContextCompat.getDrawable(
                    context,
                    if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_side_bg_material else com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light
                )
                val insetBg = android.graphics.drawable.InsetDrawable(
                    originalBg, leftInset, topInset, rightInset, bottomInset
                )
                background = insetBg

                if (keyData.isHiLighted) {
                    isPressed = true
                }

                // ★ テーマ適用
                when (themeMode) {
                    "custom" -> {
                        if (customBorderEnable) {
                            setDrawableSolidColor(customSpecialKeyColor)
                            setColorFilter(customSpecialKeyTextColor)
                            setBorder(customBorderColor, borderWidth)
                        } else {
                            // 1. ベース（ニューモーフィズム）- QWERTYと同じロジック
                            val neumorphDrawable = getDynamicNeumorphDrawable(
                                baseColor = customSpecialKeyColor,
                                radius = commonCornerRadius
                            )

                            // 2. 上層（透明なSegmentedDrawable）
                            // STANDARD_FLICKと見た目を合わせるため、アイコンキーにもダミーのSegmentedDrawableを重ねる
                            val segmentedDrawable = SegmentedBackgroundDrawable(
                                label = "",
                                baseColor = Color.TRANSPARENT,
                                highlightColor = customSpecialKeyColor,
                                textColor = customSpecialKeyTextColor,
                                cornerRadius = commonCornerRadius
                            )

                            // 3. レイヤー化とインセット設定
                            val layerDrawable =
                                LayerDrawable(arrayOf(neumorphDrawable, segmentedDrawable))
                            val inset = dpToPx(2) // QWERTYに合わせるため小さく
                            layerDrawable.setLayerInset(1, inset, inset, inset, inset)

                            background = layerDrawable
                            setColorFilter(customSpecialKeyTextColor)
                        }
                    }
                }

                if (liquidGlassEnable) {
                    this.setDrawableAlpha(liquidGlassKeyAlphaEnable)
                }
            }
        } else {
            // ■■■ 2. テキストボタン (AutoSizeButton) ■■■
            AutoSizeButton(context).apply {
                isFocusable = false
                isAllCaps = false
                elevation = 0f

                if (!keyData.isSpecialKey) {
                    setDefaultTextSize(defaultTextSize)
                }

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
                }

                val originalBg: Drawable? =
                    if (keyData.isSpecialKey) {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        ContextCompat.getDrawable(
                            context,
                            if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_side_bg_material else com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light
                        )
                    } else if (keyData.keyType != KeyType.STANDARD_FLICK) {
                        ContextCompat.getDrawable(
                            context,
                            if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_center_bg_material else com.kazumaproject.core.R.drawable.ten_keys_center_bg_material_light
                        )
                    } else {
                        null
                    }

                originalBg?.let {
                    val insetBg = android.graphics.drawable.InsetDrawable(
                        it, leftInset, topInset, rightInset, bottomInset
                    )
                    background = insetBg
                }

                // ★ テーマ適用
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
                            val targetHighlightColor = if (keyData.isSpecialKey) manipulateColor(
                                customSpecialKeyColor,
                                1.2f
                            ) else customSpecialKeyColor

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
                            val inset = dpToPx(2) // QWERTYに合わせる
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

        // LayoutParamsの設定
        val params = LayoutParams().apply {
            rowSpec = spec(keyData.row, keyData.rowSpan, FILL, 1f)
            columnSpec = spec(keyData.column, keyData.colSpan, FILL, 1f)
            width = 0
            height = 0

            if (themeMode == "custom" && !customBorderEnable) {
                setMargins(3, 6, 3, 6)
            } else {
                if (keyData.keyType == KeyType.STANDARD_FLICK) {
                    setMargins(6, 9, 6, 9)
                }
            }
        }
        keyView.layoutParams = params
        return keyView
    }

    /**
     * 指定された色(baseColor)を元に、ニューモーフィズムのDrawableを動的に生成する
     * QWERTYKeyboardViewと同じロジックを使用
     */
    private fun getDynamicNeumorphDrawable(baseColor: Int, radius: Float): Drawable {
        // 1. 色の計算 (manipulateColorを使用)
        val highlightColor = manipulateColor(baseColor, 1.2f)
        val shadowColor = manipulateColor(baseColor, 0.8f)

        // 2. ピクセル単位のオフセット量
        val offset = dpToPx(4)
        val padding = dpToPx(2)

        // --- A. 通常状態 (Idle) ---
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

        // Shadow: 左と上を空けて右下にずらす
        idleLayer.setLayerInset(0, offset, offset, 0, 0)
        // Highlight: 右と下を空けて左上にずらす
        idleLayer.setLayerInset(1, 0, 0, offset, offset)
        // Surface: 四方を少し空けて中央に配置
        idleLayer.setLayerInset(2, padding, padding, padding, padding)


        // --- B. 押下状態 (Pressed) ---
        val pressedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            // ベース色より少し暗くすることで「押し込まれた」感を出す
            setColor(manipulateColor(baseColor, 0.95f))
        }

        val pressedLayer = LayerDrawable(arrayOf(pressedDrawable))
        // サイズを変えないため、IdleのSurfaceと同じ位置に合わせるためのInset
        pressedLayer.setLayerInset(0, padding, padding, padding, padding)


        // --- C. StateListDrawable ---
        val stateList = android.graphics.drawable.StateListDrawable()
        stateList.addState(intArrayOf(android.R.attr.state_pressed), pressedLayer)
        stateList.addState(intArrayOf(), idleLayer)

        return stateList
    }

    /** Viewにリスナーやフリックコントローラーをアタッチします */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachKeyBehavior(keyView: View, keyData: KeyData): Any? {
        val layout = currentLayout ?: return null // currentLayoutが必須

        when (keyData.keyType) {
            KeyType.CIRCULAR_FLICK -> {
                val flickKeyMapsList = layout.flickKeyMaps[keyData.label]
                Log.d(
                    "FlickKeyboardView KeyType.CIRCULAR_FLICK",
                    "$flickKeyMapsList"
                )
                if (!flickKeyMapsList.isNullOrEmpty()) {
                    val controller = CustomAngleFlickController(context, flickSensitivity).apply {
                        // ( ... Controllerの各種設定 ... )
                        val secondaryColor =
                            context.getColorFromAttr(R.attr.colorSecondaryContainer)
                        val surfaceContainerLow =
                            context.getColorFromAttr(R.attr.colorSurfaceContainerLow)
                        val surfaceContainerHighest =
                            context.getColorFromAttr(R.attr.colorSurfaceContainerHighest)
                        val textColor =
                            context.getColor(com.kazumaproject.core.R.color.keyboard_icon_color)
                        val dynamicColorTheme = when (themeMode) {
                            "default" -> {
                                FlickPopupColorTheme(
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

                            "custom" -> {
                                FlickPopupColorTheme(
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
                            }

                            else -> {
                                FlickPopupColorTheme(
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
                        }
                        setPopupColors(dynamicColorTheme)
                        this.listener = object : CustomAngleFlickController.FlickListener {
                            override fun onFlick(direction: FlickDirection, character: String) {
                                if (character.isNotEmpty()) {
                                    this@FlickKeyboardView.listener?.onKey(
                                        text = character,
                                        isFlick = direction != FlickDirection.TAP
                                    )
                                }
                            }

                            override fun onStateChanged(
                                view: View,
                                newMap: Map<FlickDirection, String>
                            ) {

                            }

                            override fun onFlickDirectionChanged(newDirection: FlickDirection) {
                                this@FlickKeyboardView.listener?.onFlickDirectionChanged(
                                    newDirection
                                )
                            }
                        }
                        val stringMaps = flickKeyMapsList.map { actionMap ->
                            actionMap.mapValues { (_, flickAction) ->
                                (flickAction as? FlickAction.Input)?.char ?: ""
                            }
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
                            // UP (上): 270度を中心に ±45度
                            // 開始: 225度, 範囲: 90度 (225° 〜 315°)
                            FlickDirection.UP to Pair(225f, 90f),

                            // UP_RIGHT_FAR (右): 0度(360度)を中心に ±45度
                            // 開始: 315度, 範囲: 90度 (315° 〜 45°) ※0度をまたぐ設定
                            FlickDirection.UP_RIGHT_FAR to Pair(315f, 90f),

                            // DOWN (下): 90度を中心に ±45度
                            // 開始: 45度, 範囲: 90度 (45° 〜 135°)
                            FlickDirection.DOWN to Pair(45f, 90f),

                            // UP_LEFT_FAR (左): 180度を中心に ±45度
                            // 開始: 135度, 範囲: 90度 (135° 〜 225°)
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
                Log.d(
                    "FlickKeyboardView KeyType.CROSS_FLICK",
                    "$flickActionMap"
                )
                if (flickActionMap != null) {
                    val controller = CrossFlickInputController(context).apply {
                        this.listener = object : CrossFlickInputController.CrossFlickListener {
                            override fun onFlick(flickAction: FlickAction, isFlick: Boolean) {
                                when (flickAction) {
                                    is FlickAction.Input -> this@FlickKeyboardView.listener?.onKey(
                                        flickAction.char, isFlick = true
                                    )

                                    is FlickAction.Action -> this@FlickKeyboardView.listener?.onAction(
                                        flickAction.action, view = keyView, isFlick = isFlick
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

                            override fun onFlickUpAfterLongPress(
                                flickAction: FlickAction,
                                isFlick: Boolean
                            ) {
                                when (flickAction) {
                                    is FlickAction.Action -> this@FlickKeyboardView.listener?.onFlickActionUpAfterLongPress(
                                        flickAction.action, isFlick = isFlick
                                    )

                                    is FlickAction.Input -> {}
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
                Log.d(
                    "FlickKeyboardView KeyType.STANDARD_FLICK",
                    "$flickActionMap"
                )
                if (flickActionMap != null && keyView is Button) {
                    // ( ... Controllerの各種設定 ... )
                    val label = keyData.label
                    val isDarkTheme = context.isDarkThemeOn()
                    val segmentedDrawable: SegmentedBackgroundDrawable
                    if (themeMode == "custom") {
                        // --- A. ニューモーフィズムモード ---

                        // ベースとなるニューモーフィズムDrawableを作成
                        val neumorphDrawable = getDynamicNeumorphDrawable(
                            baseColor = customKeyColor,
                            radius = dpToPx(8).toFloat()
                        )

                        // ガイド描画用Drawable (背景は透明にして、下のニューモーフィズムを見せる)
                        segmentedDrawable = SegmentedBackgroundDrawable(
                            label = label,
                            baseColor = Color.TRANSPARENT, // ★重要: 透明にする
                            highlightColor = manipulateColor(customKeyColor, 1.2f), // ハイライト時の色
                            textColor = customKeyTextColor,
                            cornerRadius = 20f
                        )

                        // LayerDrawableで重ねる (下:ニューモーフィズム, 上:ガイド)
                        val layerDrawable =
                            LayerDrawable(arrayOf(neumorphDrawable, segmentedDrawable))

                        // ガイドが影に重ならないように少しインセットを入れる (任意調整)
                        val inset = dpToPx(2) // QWERTYに合わせる
                        layerDrawable.setLayerInset(1, inset, inset, inset, inset)

                        keyView.background = layerDrawable

                    } else {
                        // --- B. デフォルトモード (既存の処理) ---

                        val keyBaseColor =
                            if (isDarkTheme) context.getColorFromAttr(R.attr.colorSurfaceContainerHighest)
                            else context.getColorFromAttr(R.attr.colorSurface)
                        val keyHighlightColor =
                            context.getColorFromAttr(R.attr.colorSecondaryContainer)
                        val keyTextColor = context.getColorFromAttr(R.attr.colorOnSurface)

                        segmentedDrawable = SegmentedBackgroundDrawable(
                            label = label,
                            baseColor = keyBaseColor,
                            highlightColor = keyHighlightColor,
                            textColor = keyTextColor,
                            cornerRadius = 20f
                        )
                        keyView.background = segmentedDrawable
                    }

                    keyView.setTextColor(Color.TRANSPARENT)

                    val controller = StandardFlickInputController(context).apply {
                        this.listener =
                            object : StandardFlickInputController.StandardFlickListener {
                                override fun onFlick(character: String) {
                                    this@FlickKeyboardView.listener?.onKey(
                                        character, isFlick = true
                                    )
                                }
                            }

                        val popupBackgroundColor =
                            if (isDarkTheme) context.getColorFromAttr(R.attr.colorSurfaceContainerHighest) else context.getColorFromAttr(
                                R.attr.colorSurface
                            )
                        val popupTextColor = context.getColorFromAttr(R.attr.colorOnSurface)
                        val popupStrokeColor = context.getColorFromAttr(R.attr.colorOutline)

                        val dynamicColorTheme = when (themeMode) {
                            "default" -> {
                                FlickPopupColorTheme(
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
                            }

                            "custom" -> {
                                FlickPopupColorTheme(
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
                                    centerHighlightGradientStartColor = customSpecialKeyColor,
                                    centerHighlightGradientEndColor = customSpecialKeyColor,
                                    separatorColor = customSpecialKeyTextColor,
                                    textColor = customSpecialKeyTextColor
                                )
                            }

                            else -> {
                                FlickPopupColorTheme(
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
                            }
                        }
                        setPopupColors(dynamicColorTheme)

                        val stringMap = flickActionMap.mapValues { (_, flickAction) ->
                            (flickAction as? FlickAction.Input)?.char ?: ""
                        }
                        attach(keyView, stringMap, segmentedDrawable)
                    }
                    standardFlickControllers.add(controller)
                    return controller
                }
            }

            KeyType.PETAL_FLICK -> {
                val flickActionMap = layout.flickKeyMaps[keyData.label]?.firstOrNull()
                Log.d(
                    "FlickKeyboardView KeyType.PETAL_FLICK",
                    "$flickActionMap"
                )
                if (flickActionMap != null) {
                    val controller = GridFlickInputController(
                        context, flickSensitivity
                    ).apply {
                        // ( ... Controllerの各種設定 ... )
                        val isDarkTheme = context.isDarkThemeOn()
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

                        val dynamicColorTheme = when (themeMode) {
                            "default" -> {
                                FlickPopupColorTheme(
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

                            "custom" -> {
                                FlickPopupColorTheme(
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
                            }

                            else -> {
                                FlickPopupColorTheme(
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
                        }
                        setPopupColors(dynamicColorTheme)
                        elevation = 1f
                        this.listener = object : GridFlickInputController.GridFlickListener {
                            override fun onFlick(character: String, isFlick: Boolean) {
                                this@FlickKeyboardView.listener?.onKey(
                                    character, isFlick = isFlick
                                )
                            }
                        }
                        val stringMap = flickActionMap.mapValues { (_, flickAction) ->
                            (flickAction as? FlickAction.Input)?.char ?: ""
                        }
                        attach(keyView, stringMap)
                    }
                    petalFlickControllers.add(controller)
                    return controller
                }
            }

            KeyType.NORMAL -> {
                // ▼▼▼ 修正: newKeyData.action を参照する ▼▼▼
                keyData.action?.let { action ->
                    Log.d(
                        "FlickKeyboardView KeyType.NORMAL",
                        "key data: $keyData"
                    )
                    var isLongPressTriggered = false
                    keyView.setOnClickListener {
                        // ▼▼▼ 修正: info.keyData.action を参照して最新のアクションを実行する ▼▼▼
                        val currentAction = dynamicKeyMap[keyData.keyId]?.keyData?.action ?: action
                        Log.d(
                            "FlickKeyboardView KeyType.NORMAL",
                            "currentAction: $currentAction"
                        )
                        this@FlickKeyboardView.listener?.onAction(
                            currentAction, view = keyView, isFlick = false
                        )
                    }
                    keyView.setOnLongClickListener {
                        val currentAction = dynamicKeyMap[keyData.keyId]?.keyData?.action ?: action
                        isLongPressTriggered =
                            true; this@FlickKeyboardView.listener?.onActionLongPress(currentAction); true
                    }
                    keyView.setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                            if (isLongPressTriggered) {
                                val currentAction =
                                    dynamicKeyMap[keyData.keyId]?.keyData?.action ?: action
                                this@FlickKeyboardView.listener?.onActionUpAfterLongPress(
                                    currentAction
                                ); isLongPressTriggered =
                                    false
                            }
                        }
                        false
                    }
                }
                return null // コントローラーなし
            }

            KeyType.TWO_STEP_FLICK -> {
                val twoStepMap = layout.twoStepFlickKeyMaps[keyData.label]
                if (twoStepMap != null) {
                    val controller = TfbiInputController(
                        context,
                        flickSensitivity = flickSensitivity.toFloat()
                    ).apply {
                        this.listener = object : TfbiInputController.TfbiListener {
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
                                    this@FlickKeyboardView.listener?.onKey(
                                        text = character,
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
                                    this@FlickKeyboardView.listener?.onKey(
                                        text = character,
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
                            override fun onFlick(character: String) {
                                Log.d(
                                    "FlickKeyboardView KeyType.HIERARCHICAL_FLICK",
                                    "Char: $character"
                                )
                                if (character.isNotEmpty()) {
                                    this@FlickKeyboardView.listener?.onKey(
                                        text = character,
                                        isFlick = true // 階層フリックは常true
                                    )
                                }
                            }

                            override fun onModeChanged(newLabel: String) {
                                Log.d(
                                    "FlickKeyboardView",
                                    "onModeChanged: keyId=${keyData.keyId}, newLabel=$newLabel"
                                )

                                // 1. dynamicKeyMap のキャッシュを更新 (存在する場合)
                                keyData.keyId?.let { id ->
                                    dynamicKeyMap[id]?.let { info ->
                                        info.keyData = info.keyData.copy(label = newLabel)
                                    }
                                }

                                // 2. 実際のViewの表示を更新
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

    /** アタッチされたビヘイビア（コントローラー）を解除します */
    private fun detachKeyBehavior(controller: Any?) {
        // コントローラーを解除
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

            is GridFlickInputController -> {
                controller.cancel()
                petalFlickControllers.remove(controller)
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

    /** 既存Viewのビジュアル（テキスト/アイコン）のみを更新します */
    private fun updateKeyVisuals(view: View, keyData: KeyData) {
        when (view) {
            is AppCompatImageButton -> {
                keyData.drawableResId?.let { view.setImageResource(it) }
                view.contentDescription = keyData.label
                view.isPressed = keyData.isHiLighted
            }

            is AutoSizeButton -> {
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
                    view.text = spannable
                } else {
                    view.text = keyData.label
                }
                view.isPressed = keyData.isHiLighted
            }
        }
    }

    private val motionTargets = mutableMapOf<Int, View>()
    private val pointerDownTime = mutableMapOf<Int, Long>()
    private val TAG = "FlickKeyboardViewTouch"

    private fun findTargetView(x: Float, y: Float): View? {
        // まず、キーの矩形内に直接ヒットしたかチェック
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.getHitRect(hitRect)
            if (hitRect.contains(x.toInt(), y.toInt())) {
                return child
            }
        }

        // 直接ヒットしなかった場合（マージンなどをタッチした場合）、最も近いキーを探す
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

        // 最初の指が触れた瞬間に true を返すことで、
        // この後のすべてのタッチイベント(MOVE, UP, POINTER_DOWNなど)を
        // このビューの onTouchEvent で処理することを決定する。
        if (action == MotionEvent.ACTION_DOWN) {
            Log.d(TAG, "-> Intercepting gesture from ACTION_DOWN. Returning true.")
            return true
        }

        // すでにインターセプトしている場合は、子には渡さない
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
                    // Store the initial touch position for cursor movement
                    cursorInitialX = event.x
                    cursorInitialY = event.y
                    return true // Consume the event
                }

                MotionEvent.ACTION_MOVE -> {
                    val threshold = 30f // Movement detection threshold in pixels
                    val currentX = event.x
                    val currentY = event.y

                    val dx = currentX - cursorInitialX
                    val dy = currentY - cursorInitialY

                    // Horizontal movement
                    if (abs(dx) > abs(dy) && abs(dx) > threshold) {
                        val action2 =
                            if (dx < 0f) KeyAction.MoveCursorLeft else KeyAction.MoveCursorRight
                        listener?.onAction(action2, this, false)
                        cursorInitialX = currentX // Reset the origin for continuous swiping
                        cursorInitialY = currentY
                    }
                    // Vertical movement
                    else if (abs(dy) > abs(dx) && abs(dy) > threshold) {
                        // Assuming you have CURSOR_UP and CURSOR_DOWN in your KeyAction enum
                        val action2 =
                            if (dy < 0f) KeyAction.MoveCursorUp else KeyAction.MoveCursorDown
                        listener?.onAction(action2, this, false)
                        cursorInitialX = currentX // Reset the origin
                        cursorInitialY = currentY
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Exit cursor mode when the finger is lifted
                    setCursorMode(false)
                    crossFlickControllers.forEach { it.dismissAllPopups() }
                    return true
                }
            }
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                // 最初の指が触れた。すべての状態をクリアして開始。
                motionTargets.clear()
                pointerDownTime.clear()

                // この指の情報を保存
                pointerDownTime[pointerId] = event.downTime
                val x = event.x
                val y = event.y
                val targetView = findTargetView(x, y)

                targetView?.let {
                    motionTargets[pointerId] = it

                    // ★★★ 修正箇所 ★★★
                    // システムのイベントをコピーするのではなく、2本指目と同様に
                    // クリーンなACTION_DOWNイベントを自作する。
                    val newEvent = MotionEvent.obtain(
                        event.downTime,    // downTime
                        event.eventTime,   // eventTime
                        MotionEvent.ACTION_DOWN, // action
                        x,                 // x
                        y,                 // y
                        event.metaState    // metaState
                    )
                    // ★★★ ここまで ★★★

                    Log.d("FlickKeyboardView MotionEvent.ACTION_DOWN", "$newEvent")

                    newEvent.offsetLocation(-it.left.toFloat(), -it.top.toFloat())
                    it.dispatchTouchEvent(newEvent)
                    newEvent.recycle()
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {

                if (this.visibility != View.VISIBLE) {
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
                        // 1本目の指の現在の座標を取得
                        val existingPointerIndex = event.findPointerIndex(existingPointerId)
                        if (existingPointerIndex != -1) {
                            val x = event.getX(existingPointerIndex)
                            val y = event.getY(existingPointerIndex)

                            // 1本目の指に対して「ACTION_UP」イベントを自作して送る
                            val upEvent = MotionEvent.obtain(
                                downTime,
                                event.eventTime,
                                MotionEvent.ACTION_UP, // ジェスチャー終了としてUPイベントを偽装
                                x,
                                y,
                                event.metaState
                            )
                            upEvent.offsetLocation(-target.left.toFloat(), -target.top.toFloat())
                            target.dispatchTouchEvent(upEvent) // ターゲットにUPイベントをディスパッチ
                            upEvent.recycle()
                        }
                    }

                    val matchingEntry = dynamicKeyMap.entries.find { it.value.view == target }
                    if (matchingEntry != null) {
                        val keyId = matchingEntry.key
                        val keyInfo = matchingEntry.value
                        Log.d(
                            TAG,
                            "ACTION_POINTER_DOWN: First finger (ID: $existingPointerId) is on a dynamic key. KeyId: $keyId, KeyInfo: $keyInfo"
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

                // 既存のポインター情報をすべてクリア
                motionTargets.clear()
                pointerDownTime.clear()

                // 2. 新しい指（2本目）のジェスチャーを新しく開始する
                val newPointerId = event.getPointerId(pointerIndex)
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)

                // 新しい指の情報を保存
                pointerDownTime[newPointerId] = event.eventTime
                val targetView = findTargetView(x, y)


                targetView?.let {
                    motionTargets[newPointerId] = it
                    // この指専用の「ACTION_DOWN」イベントを自作する
                    val newEvent = MotionEvent.obtain(
                        event.eventTime, // 新しいジェスチャーなのでdownTimeは現在のeventTime
                        event.eventTime,
                        MotionEvent.ACTION_DOWN,
                        x,
                        y,
                        event.metaState
                    )
                    // 自作したきれいなDOWNイベントをターゲットにディスパッチ

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
                // 指が動いた。追跡中のすべての指に対して、それぞれ専用のMOVEイベントを作成する
                for (i in 0 until event.pointerCount) {
                    val pId = event.getPointerId(i)
                    val target = motionTargets[pId]
                    val downTime = pointerDownTime[pId]


                    if (target != null && downTime != null) {
                        val x = event.getX(i)
                        val y = event.getY(i)

                        // この指専用の「ACTION_MOVE」イベントを自作
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
                if (this.visibility != View.VISIBLE) {
                    return false
                }
                // 離された指の情報を取得
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)

                // ▼▼▼ ログ追加 ▼▼▼
                Log.d(
                    "FlickKeyboardView",
                    "ACTION_POINTER_UP: pointerId=$pointerId, index=$pointerIndex"
                )
                // ▲▲▲ ログ追加 ▲▲▲

                motionTargets[pointerId]?.let { target ->
                    val downTime = pointerDownTime[pointerId]!!

                    // ▼▼▼ ログ追加 ▼▼▼
                    Log.d(
                        "FlickKeyboardView",
                        "ACTION_POINTER_UP: Found target! $target"
                    )
                    // ▲▲▲ ログ追加 ▲▲▲

                    // この指専用の「ACTION_UP」イベントを自作
                    val newEvent = MotionEvent.obtain(
                        downTime, event.eventTime, MotionEvent.ACTION_UP, // ジェスチャーの終了として偽装
                        x, y, event.metaState
                    )

                    // ▼▼▼ ログ追加 ▼▼▼
                    Log.d(
                        "FlickKeyboardView",
                        "ACTION_POINTER_UP: Dispatching fake ACTION_UP to target. Event: $newEvent"
                    )
                    // ▲▲▲ ログ追加 ▲▲▲

                    newEvent.offsetLocation(-target.left.toFloat(), -target.top.toFloat())
                    target.dispatchTouchEvent(newEvent)
                    newEvent.recycle()

                } ?: run {
                    // ▼▼▼ ログ追加（ターゲットが見つからなかった場合）▼▼▼
                    Log.e(
                        "FlickKeyboardView",
                        "ACTION_POINTER_UP: No target found for pointerId=$pointerId"
                    )
                    // ▲▲▲ ログ追加 ▲▲▲
                }

                // 離された指の情報を削除
                motionTargets.remove(pointerId)
                pointerDownTime.remove(pointerId)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 最後の指が離された、またはジェスチャーがキャンセルされた
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                val actionToDispatch =
                    if (action == MotionEvent.ACTION_UP) MotionEvent.ACTION_UP else MotionEvent.ACTION_CANCEL


                motionTargets[pointerId]?.let { target ->
                    val downTime = pointerDownTime[pointerId]!!
                    // この指専用のUP/CANCELイベントを自作
                    val newEvent = MotionEvent.obtain(
                        downTime, event.eventTime, actionToDispatch, x, y, event.metaState
                    )

                    Log.d("FlickKeyboardView MotionEvent.ACTION_UP", "$downTime $newEvent")
                    newEvent.offsetLocation(-target.left.toFloat(), -target.top.toFloat())
                    target.dispatchTouchEvent(newEvent)
                    newEvent.recycle()
                }

                // すべての状態をクリア
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
        petalFlickControllers.forEach { it.cancel() }
        tfbiControllers.forEach { it.cancel() }
        stickyTfbiControllers.forEach { it.cancel() }
        hierarchicalTfbiControllers.forEach { it.cancel() }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
    }

    private fun Context.getColorFromAttr(@AttrRes attrRes: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(
            attrRes, typedValue, true
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
