package com.kazumaproject.qwerty_keyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.util.Log
import android.util.SparseArray
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.util.isNotEmpty
import androidx.core.util.size
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.data.popup.QwertyPopupViewStyleSet
import com.kazumaproject.core.data.qwerty.CapsLockState
import com.kazumaproject.core.data.qwerty.QWERTYKeys
import com.kazumaproject.core.data.qwerty.VariationInfo
import com.kazumaproject.core.domain.extensions.dpToPx
import com.kazumaproject.core.domain.extensions.setBorder
import com.kazumaproject.core.domain.extensions.setDrawableAlpha
import com.kazumaproject.core.domain.extensions.setDrawableSolidColor
import com.kazumaproject.core.domain.extensions.setMarginEnd
import com.kazumaproject.core.domain.extensions.setMarginStart
import com.kazumaproject.core.domain.extensions.toZenkaku
import com.kazumaproject.core.domain.listener.QWERTYKeyListener
import com.kazumaproject.core.domain.listener.KeyTouchCancelReason
import com.kazumaproject.core.domain.listener.QwertyKeyTouchCancelListener
import com.kazumaproject.core.domain.qwerty.QWERTYKey
import com.kazumaproject.core.domain.qwerty.QWERTYKeyInfo
import com.kazumaproject.core.domain.qwerty.QWERTYKeyMap
import com.kazumaproject.core.domain.state.QWERTYMode
import com.kazumaproject.qwerty_keyboard.R
import com.kazumaproject.qwerty_keyboard.databinding.QwertyLayoutBinding
import com.kazumaproject.qwerty_keyboard.glide.QwertyGlideGesturePolicy
import com.kazumaproject.qwerty_keyboard.glide.QwertyGlideInputListener
import com.kazumaproject.qwerty_keyboard.glide.QwertyGlideKeyClassifier
import com.kazumaproject.qwerty_keyboard.glide.QwertyInputPointerPoint
import com.kazumaproject.qwerty_keyboard.glide.QwertyInputPointers
import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyProximity
import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyboardProximityInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

/**
 * A custom keyboard view with dynamic margins.
 */
class QWERTYKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: QwertyLayoutBinding

    // --- Dynamic Margin Variables (in dp) ---
    private var keyVerticalMarginDp: Float = 5f
    private var keyHorizontalGapDp: Float = 2f
    private var keyIndentLargeDp: Float = 23f
    private var keyIndentSmallDp: Float = 9f
    private var keySideMarginDp: Float = 4f
    private var keyTextSizeSp: Float = 20f
    private var specialKeyTextSizeSp: Float = 12f
    private var specialKeyIconSizeDp: Float = 18f
    private var pendingSpecialIconSizeRefresh = false

    /** Map each active pointer ID → the View (key) it’s currently “pressing” (or null). */
    private val pointerButtonMap = SparseArray<View?>()

    /** For each pointer, store a coroutine Job to detect long‐press. */
    private val longPressJobs = SparseArray<Job>()

    /** StateFlow collectors are tied to the current View attach lifecycle. */
    private var renderScope: CoroutineScope? = null

    /** Touch jobs are also recreated after PopupWindow detach / reattach. */
    private var touchScope: CoroutineScope? = null

    /** If a second finger cancels the first, we suppress that first pointer until it lifts. */
    private var suppressedPointerId: Int? = null

    private var keyPreviewPopup: PopupWindow? = null
    private val hitRect = Rect()

    private var qwertyKeyListener: QWERTYKeyListener? = null
    private var qwertyKeyTouchCancelListener: QwertyKeyTouchCancelListener? = null
    private var qwertyKeyMap: QWERTYKeyMap

    // ① Track the last time Shift was tapped (to detect double‐tap)
    private var lastShiftTapTime = 0L
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

    // ② After detecting a double‐tap, suppress the next single‐tap for Shift.
    private var shiftDoubleTapped = false

    // Long‐press timeout (system default)
    private var longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private val _capsLockState = MutableStateFlow(CapsLockState())
    private val capsLockState: StateFlow<CapsLockState> = _capsLockState.asStateFlow()

    private val _romajiModeState = MutableStateFlow(false)
    private val romajiModeState = _romajiModeState.asStateFlow()

    private val _qwertyMode = MutableStateFlow<QWERTYMode>(QWERTYMode.Default)
    private val qwertyMode: StateFlow<QWERTYMode> = _qwertyMode.asStateFlow()

    private var isTablet = false
    private var showPopupView = true

    private var isDynamicColorsEnable = false

    private var variationPopup: PopupWindow? = null
    private var variationPopupView: VariationsPopupView? = null
    private var longPressedPointerId: Int? = null
    private var keyPreviewPopupStyle = PopupViewStyle(100, 28f)
    private var variationPopupStyle = PopupViewStyle(100, 28f)

    // ★ ポインターをロックするための変数を追加
    private var lockedPointerId: Int? = null

    private var isCursorMode: Boolean = false

    // カーソルモード中のタッチの初期位置を記録する変数
    private var cursorInitialX = 0f
    private var cursorInitialY = 0f

    private var isNumberKeysShow: Boolean = false
    private var isSymbolKeymapShow: Boolean = false
    private var numberKeyFlickUpChars: Map<String, String> = emptyMap()
    private var numberKeyFlickDownChars: Map<String, String> = emptyMap()

    private var liquidGlassEnable: Boolean = false

    private var qwertyGlideInputMode: Boolean = false
    private var qwertyGlideInputListener: QwertyGlideInputListener? = null
    private var glideCandidatePointerId: Int? = null
    private var glideStarted = false
    private var glideDownTime = 0L
    private var glideLastSampleX = 0f
    private var glideLastSampleY = 0f
    private var lastNonGlideKeyUpTime = 0L
    private val glideRawPoints = mutableListOf<QwertyInputPointerPoint>()
    private val glideTrailPoints = mutableListOf<Pair<Float, Float>>()
    private val glideTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 66, 133, 244)
        strokeWidth = context.resources.displayMetrics.density * 5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }
    private val glideTrailPath = Path()
    private val glideMinMoveDistance by lazy { ViewConfiguration.get(context).scaledTouchSlop * 2.4f }
    private val glideFastMoveDistance by lazy { ViewConfiguration.get(context).scaledTouchSlop * 3.0f }
    private val glideSamplingMinDistance by lazy { ViewConfiguration.get(context).scaledTouchSlop * 0.45f }
    private val pendingGlideLongPressSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }
    private val glideMinElapsedMillis = 45L
    private val glideFastTypingSuppressMillis = 55L

    // ★ ポップアップなしで長押しを有効にするキーのリストを追加
    private val longPressEnabledKeys = setOf(
        QWERTYKey.QWERTYKeyDelete,
        QWERTYKey.QWERTYKeySpace,
        QWERTYKey.QWERTYKeySwitchDefaultLayout,
        QWERTYKey.QWERTYKeyCursorLeft,
        QWERTYKey.QWERTYKeyCursorRight
    )

    /**
     * 上フリック検知を有効にするかどうかのフラグ
     */
    private var enableFlickUpDetection = false

    /**
     * 下フリック検知を有効にするかどうかのフラグ
     */
    private var enableFlickDownDetection = false

    /**
     * 各ポインターのタッチ開始座標 (X, Y) を保存するマップ
     * Key: pointerId, Value: Pair(startX, startY)
     */
    private val pointerStartCoords = SparseArray<Pair<Float, Float>>()

    /**
     * 上フリックジェスチャー中として「ロック」されたポインターIDのセット
     */
    private val flickLockedPointers = mutableSetOf<Int>()

    /**
     * フリックと判定するための最小Y軸移動距離 (px)
     */
    private val flickThreshold by lazy { ViewConfiguration.get(context).scaledTouchSlop.toFloat() * 1.5f }

    /**
     * Delte キーの左フリックを有効にするフラグ
     */
    private var enableDeleteLeftFlick = false

    /**
     * Delete キーの上フリックを有効にするフラグ
     */
    private var enableDeleteUpFlick = false

    /**
     * Delete キーの下フリックを有効にするフラグ
     */
    private var enableDeleteDownFlick = false

    /**
     * Delete キーの左フリック検知時のリスナー
     */
    private var onDeleteLeftFlickListener: (() -> Unit)? = null

    /**
     * Delete キーの上フリック検知時のリスナー
     */
    private var onDeleteUpFlickListener: (() -> Unit)? = null

    /**
     * Delete キーの下フリック検知時のリスナー
     */
    private var onDeleteDownFlickListener: (() -> Unit)? = null

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
    private var borderWidth: Int = 1

    init {
        isClickable = true
        isFocusable = true
        isDynamicColorsEnable = DynamicColors.isDynamicColorAvailable()

        val inflater = LayoutInflater.from(context)
        binding = QwertyLayoutBinding.inflate(inflater, this)

        qwertyKeyMap = QWERTYKeyMap()

        isTablet = resources.getBoolean(com.kazumaproject.core.R.bool.isTablet)

        touchScope = createViewScope()
    }

    private fun createViewScope(): CoroutineScope {
        return CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (touchScope == null) {
            touchScope = createViewScope()
        }
        val enterKeyText = binding.keyReturn.text
        val spaceKeyText = binding.keySpace.text
        startStateCollectors()
        renderCurrentStateImmediately(enterKeyText, spaceKeyText)
    }

    override fun onDetachedFromWindow() {
        notifyQwertyTouchCanceledForActivePointers(KeyTouchCancelReason.DetachedFromWindow)
        stopStateCollectors()
        stopTouchScope()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView == this && visibility != View.VISIBLE) {
            notifyQwertyTouchCanceledForActivePointers(KeyTouchCancelReason.ViewHidden)
            setCursorMode(false)
            clearAllPressed()
            cancelQwertyGlideCandidate(notify = true)
        }
    }

    private fun startStateCollectors() {
        if (renderScope != null) return

        val newScope = createViewScope()
        renderScope = newScope

        newScope.launch {
            capsLockState.collectLatest { state ->
                updateCapsLockUI(state)
                renderShiftKeyDrawable()
            }
        }
        newScope.launch {
            qwertyMode.collectLatest { state ->
                Log.d("qwertyMode", "$state")
                applyLayoutForMode(state)
                applyContentForMode(state)
                renderShiftKeyDrawable()
            }
        }
        newScope.launch {
            romajiModeState.collectLatest { romajiMode ->
                applyLayoutForMode(qwertyMode.value)
                applyContentForMode(qwertyMode.value)
                applyRomajiModeLabels(romajiMode)
                renderShiftKeyDrawable()
            }
        }
    }

    private fun stopStateCollectors() {
        renderScope?.cancel()
        renderScope = null
    }

    private fun stopTouchScope() {
        for (i in 0 until longPressJobs.size()) {
            longPressJobs.valueAt(i)?.cancel()
        }
        longPressJobs.clear()
        touchScope?.cancel()
        touchScope = null
    }

    private fun renderCurrentStateImmediately(
        enterKeyText: CharSequence? = binding.keyReturn.text,
        spaceKeyText: CharSequence? = binding.keySpace.text,
    ) {
        applyLayoutForMode(qwertyMode.value)
        applyContentForMode(qwertyMode.value)
        applyRomajiModeLabels(romajiModeState.value)
        updateCapsLockUI(capsLockState.value)
        renderShiftKeyDrawable()
        binding.keyReturn.text = enterKeyText
        binding.keySpace.text = spaceKeyText
        refreshSpecialKeyIconSizesWhenLaidOut()
    }

    private fun applyRomajiModeLabels(romajiMode: Boolean) {
        binding.apply {
            if (romajiMode) {
                keySpace.text = resources.getString(com.kazumaproject.core.R.string.space_japanese)
                keyKuten.text = "。"
                keyTouten.text = "、"
            } else {
                keySpace.text = resources.getString(com.kazumaproject.core.R.string.space_english)
                keyKuten.text = "."
                keyTouten.text = ","
            }
        }
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

        LayoutInflater.from(context)

        when (this.themeMode) {
            "default" -> {
                setBackgroundColor(Color.TRANSPARENT)
                setMaterialYouTheme(this.isNightMode, true)
            }

            "custom" -> {
                setFullCustomNeumorphismTheme(
                    backgroundColor = customBgColor,
                    normalKeyColor = customKeyColor,
                    specialKeyColor = customSpecialKeyColor,
                    normalKeyTextColor = customKeyTextColor,
                    specialKeyTextColor = customSpecialKeyTextColor,
                    borderWidth = borderWidth
                )
            }

            else -> {
                setBackgroundColor(Color.TRANSPARENT)
                setMaterialYouTheme(this.isNightMode, true)
            }
        }
    }

    /**
     * 詳細な色指定によるニューモーフィズムテーマの適用（拡張版）
     *
     * @param backgroundColor View全体の背景色
     * @param normalKeyColor 「通常キー」の背景色 (追加)
     * @param specialKeyColor 「特殊キー（Enter, Deleteなど）」の背景色
     * @param normalKeyTextColor 通常キーの文字・アイコン色
     * @param specialKeyTextColor 特殊キーの文字・アイコン色
     */
    fun setFullCustomNeumorphismTheme(
        backgroundColor: Int,
        normalKeyColor: Int, // 引数を追加
        specialKeyColor: Int,
        normalKeyTextColor: Int,
        specialKeyTextColor: Int,
        borderWidth: Int
    ) {
        val density = context.resources.displayMetrics.density
        val radius = 8f * density // 角丸の半径 (8dp)

        // 1. 全体の背景色を設定
        if (liquidGlassEnable) {
            this.setBackgroundColor(ColorUtils.setAlphaComponent(backgroundColor, 0))
        } else {
            this.setBackgroundColor(backgroundColor)
        }

        binding.apply {
            // --- キーの分類リスト定義 ---
            val normalKeys = listOf(
                key1, key2, key3, key4, key5, key6, key7, key8, key9, key0,
                keyKuten, keyTouten, keyQ, keyW, keyE, keyR, keyT, keyY, keyU, keyI, keyO, keyP,
                keyA, keyS, keyD, keyF, keyG, keyH, keyJ, keyK, keyAtMark, keyL,
                keyZ, keyX, keyC, keyV, keyB, keyN, keyM, keySpace
            )

            val specialKeys = listOf(
                keyShift, keyDelete, keySwitchDefault, keyEmoji, keyReturn, key123,
                switchNumberLayout, cursorLeft, cursorRight, switchRomajiEnglish
            )

            // --- 色の適用処理 ---

            // 2. 通常キーへの適用 (normalKeyColorを使用)
            val normalDrawableState =
                getDynamicNeumorphDrawable(normalKeyColor, radius).constantState

            val normalColorStateList = ColorStateList.valueOf(normalKeyTextColor)

            normalKeys.forEach { view ->
                if (customBorderEnable) {
                    view.setDrawableSolidColor(customKeyColor)
                    view.setBorder(customBorderColor, borderWidth)
                } else {
                    view.background = normalDrawableState?.newDrawable()?.mutate()
                }
                view.setTextColor(normalColorStateList)
                view.setDrawableAlpha(liquidGlassKeyAlphaEnable)
            }

            // 3. 特殊キーへの適用 (specialKeyColorを使用)
            val specialDrawableState =
                getDynamicNeumorphDrawable(specialKeyColor, radius).constantState

            val specialColorStateList = ColorStateList.valueOf(specialKeyTextColor)

            specialKeys.forEach { view ->
                if (customBorderEnable) {
                    view.setDrawableSolidColor(customSpecialKeyColor)
                    view.setBorder(customBorderColor, borderWidth)
                } else {
                    view.background = specialDrawableState?.newDrawable()?.mutate()
                }

                if (view is MaterialTextView) view.setTextColor(specialColorStateList)
                if (view is AppCompatButton) view.setTextColor(specialColorStateList)

                if (view is AppCompatImageButton) {
                    ImageViewCompat.setImageTintList(view, specialColorStateList)
                }
                view.setDrawableAlpha(liquidGlassKeyAlphaEnable)
            }
        }
    }

    /**
     * 指定された色(baseColor)を元に、ニューモーフィズムのDrawableを動的に生成する
     * @param baseColor キーのメインカラー
     * @param radius キーの角丸の半径 (px)
     */
    private fun getDynamicNeumorphDrawable(baseColor: Int, radius: Float): Drawable {
        // 1. 色の計算
        // ハイライト色: ベース色に白(#FFFFFF)を50%混ぜる（または明るくする）
        val highlightColor = manipulateColor(baseColor, 1.2f) // 輝度を上げる簡易版
        // シャドウ色: ベース色に黒(#000000)を混ぜて暗くする
        val shadowColor = manipulateColor(baseColor, 0.8f)    // 輝度を下げる簡易版

        // 2. ピクセル単位のオフセット量（4dpなどをpxに変換）
        val density = context.resources.displayMetrics.density
        val offset = (4 * density).toInt() // 影のずれ幅
        val padding = (2 * density).toInt() // メイン面の縮小幅

        // --- A. 通常状態 (Idle) の作成 ---

        // レイヤー0: 暗い影 (右下に配置)
        val shadowDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(shadowColor)
        }

        // レイヤー1: 明るいハイライト (左上に配置)
        val highlightDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(highlightColor)
        }

        // レイヤー2: メインの面
        val surfaceDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(baseColor)
        }

        // LayerDrawableで重ねる (下から順に描画される)
        val idleLayer = LayerDrawable(arrayOf(shadowDrawable, highlightDrawable, surfaceDrawable))

        // インセット（余白）を設定して位置をずらす
        // setLayerInset(index, left, top, right, bottom)

        // 影: 左と上を空けて、右下に表示させる
        idleLayer.setLayerInset(0, offset, offset, 0, 0)

        // ハイライト: 右と下を空けて、左上に表示させる
        idleLayer.setLayerInset(1, 0, 0, offset, offset)

        // メイン面: 全体に少し余白を入れて中央に配置（影が見えるようにする）
        idleLayer.setLayerInset(2, padding, padding, padding, padding)


        // --- B. 押下状態 (Pressed) の作成 ---

        // 押したときは凹む表現（影を消して少し暗くする、あるいは内側の影を擬似的に表現）
        val pressedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            // ベース色より少し暗くすることで「押し込まれた」感を出す
            setColor(manipulateColor(baseColor, 0.95f))
        }
        // Pressed状態はサイズを変えないため、IdleのSurfaceと同じ位置に合わせるためのInsetが必要ならLayerDrawableにする
        val pressedLayer = LayerDrawable(arrayOf(pressedDrawable))
        pressedLayer.setLayerInset(0, padding, padding, padding, padding)


        // --- C. StateListDrawable (Selector) にまとめる ---
        val stateListDrawable = android.graphics.drawable.StateListDrawable()

        // 押された時
        stateListDrawable.addState(
            intArrayOf(android.R.attr.state_pressed),
            pressedLayer
        )
        // 無効な時 (必要であれば)
        stateListDrawable.addState(
            intArrayOf(-android.R.attr.state_enabled),
            pressedLayer // 簡易的にPressedと同じ、あるいは透明度を下げるなど
        )
        // 通常時
        stateListDrawable.addState(
            intArrayOf(),
            idleLayer
        )

        return stateListDrawable
    }

    /**
     * 色の明るさを調整するヘルパー関数
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

    /**
     * ★ レイアウト（マージン、表示/非表示）を適用するメソッド
     */
    private fun applyLayoutForMode(mode: QWERTYMode) {
        // まず全キーの基本マージンをリセット
        updateGlobalMargins()

        when (mode) {
            QWERTYMode.Default -> {
                binding.apply {
                    if (!romajiModeState.value) {
                        keyAtMark.isVisible = false
                        keyA.setMarginStart(keyIndentLargeDp)
                        keyL.setMarginEnd(keyIndentLargeDp)
                        // Roman mode specific visibility
                        keyV.isVisible = true
                        keyB.isVisible = true
                    } else {
                        // Romaji mode
                        keyV.isVisible = true
                        keyB.isVisible = true
                        keyA.setMarginStart(keyIndentSmallDp)
                        keyL.setMarginEnd(keyIndentSmallDp)
                        keyAtMark.isVisible = true
                    }
                    keyShift.setMarginStart(keySideMarginDp)
                    keyDelete.setMarginEnd(keySideMarginDp)
                }
                displayOrHideNumberKeys(true)
            }

            QWERTYMode.Number -> {
                binding.apply {
                    keyAtMark.isVisible = true
                    keyV.isVisible = false
                    keyB.isVisible = false

                    keyA.setMarginStart(keyIndentSmallDp)
                    keyL.setMarginEnd(keyIndentSmallDp)
                    keyShift.setMarginStart(keySideMarginDp)
                    keyDelete.setMarginEnd(keySideMarginDp)
                }
                displayOrHideNumberKeys(false)
            }

            QWERTYMode.Symbol -> {
                binding.apply {
                    keyAtMark.isVisible = true
                    keyV.isVisible = false
                    keyB.isVisible = false

                    keyA.setMarginStart(keyIndentSmallDp)
                    keyL.setMarginEnd(keyIndentSmallDp)
                    keyShift.setMarginStart(keySideMarginDp)
                    keyDelete.setMarginEnd(keySideMarginDp)
                }
                displayOrHideNumberKeys(false)
            }
        }
        // マージン変更を反映するために再レイアウトを要求
        requestLayout()
    }

    /**
     * ★ すべてのキーの基本マージン（縦、および横の隙間）を適用する
     */
    private fun updateGlobalMargins() {
        val verticalMarginPx = dpToPx(keyVerticalMarginDp.toInt())
        val horizontalGapPx = dpToPx(keyHorizontalGapDp.toInt())

        qwertyButtonMap.keys.forEach { view ->
            // LayoutParamsを取得して更新
            val params = view.layoutParams as? ViewGroup.MarginLayoutParams
            params?.let {
                it.topMargin = verticalMarginPx
                it.bottomMargin = verticalMarginPx

                // 特定のインデントを持つキー以外には基本の横隙間を適用
                // （applyLayoutForModeでsetMarginStart/Endされるキーは後で上書きされる）
                it.marginStart = horizontalGapPx
                it.marginEnd = horizontalGapPx
                view.layoutParams = it
            }
        }
    }

    /**
     * ★ 外部からマージンを設定するメソッド
     */
    fun setKeyMargins(
        verticalDp: Float = keyVerticalMarginDp,
        horizontalGapDp: Float = keyHorizontalGapDp,
        indentLargeDp: Float = keyIndentLargeDp,
        indentSmallDp: Float = keyIndentSmallDp,
        sideMarginDp: Float = keySideMarginDp,
        textSizeSp: Float = keyTextSizeSp,
        specialTextSizeSp: Float = specialKeyTextSizeSp,
        specialIconSizeDp: Float = specialKeyIconSizeDp
    ) {
        this.keyVerticalMarginDp = verticalDp
        this.keyHorizontalGapDp = horizontalGapDp
        this.keyIndentLargeDp = indentLargeDp
        this.keyIndentSmallDp = indentSmallDp
        this.keySideMarginDp = sideMarginDp
        this.keyTextSizeSp = textSizeSp
        this.specialKeyTextSizeSp = specialTextSizeSp
        this.specialKeyIconSizeDp = specialIconSizeDp

        // 現在のモードでレイアウトを再適用
        applyLayoutForMode(qwertyMode.value)

        updateAllKeyTextSizes()
        updateSpecialKeyTextSizes()
        refreshSpecialKeyIconSizesWhenLaidOut()
    }

    private fun refreshSpecialKeyIconSizesWhenLaidOut() {
        if (isLaidOut) {
            pendingSpecialIconSizeRefresh = false
            updateSpecialKeyIconSizes()
            return
        }
        if (pendingSpecialIconSizeRefresh) return
        pendingSpecialIconSizeRefresh = true
        doOnLayout {
            pendingSpecialIconSizeRefresh = false
            updateSpecialKeyIconSizes()
        }
    }

    private fun updateAllKeyTextSizes() {
        qwertyButtonMap.keys.forEach { view ->
            if (specialTextButtons.contains(view)) return@forEach
            if (view is TextView) { // QWERTYButton, AppCompatButton は TextView を継承している
                view.textSize = keyTextSizeSp
            }
        }
    }

    private fun updateSpecialKeyTextSizes() {
        specialTextButtons.forEach { view ->
            view.textSize = specialKeyTextSizeSp
        }
    }

    private fun updateSpecialKeyIconSizes() {
        val iconSizePx = context.dpToPx(specialKeyIconSizeDp).coerceAtLeast(1)
        specialIconButtons.forEach { button ->
            button.scaleType = ImageView.ScaleType.FIT_CENTER
            applyIconPadding(button, iconSizePx)
            button.post {
                applyIconPadding(button, iconSizePx)
            }
        }
    }

    private fun applyIconPadding(button: AppCompatImageButton, iconSizePx: Int) {
        if (button.width == 0 || button.height == 0) return

        val horizontalPadding = ((button.width - iconSizePx) / 2).coerceAtLeast(0)
        val verticalPadding = ((button.height - iconSizePx) / 2).coerceAtLeast(0)
        button.setPadding(
            horizontalPadding,
            verticalPadding,
            horizontalPadding,
            verticalPadding
        )
    }

    /**
     * キーの文字内容（ラベル、右上の文字など）を適用するメソッド
     *
     * 注意: keyShift の drawable はここでは直接 setImageResource を呼ばず、
     * [renderShiftKeyDrawable] に集約して上書き競合を避ける。
     */
    private fun applyContentForMode(mode: QWERTYMode) {
        when (mode) {
            QWERTYMode.Default -> {
                binding.apply {
                    if (!romajiModeState.value) {
                        defaultQWERTYButtonsRoman.forEach { it.topRightChar = null }
                    }

                    key123.text = resources.getString(com.kazumaproject.core.R.string.string_123)

                    // 右上の文字の更新ロジック
                    updateTopRightCharsForDefaultMode()
                }
                attachDefaultKeyLabels()
            }

            QWERTYMode.Number -> {
                binding.apply {
                    key123.text = if (romajiModeState.value) {
                        resources.getString(com.kazumaproject.core.R.string.string_abc_japanese)
                    } else {
                        resources.getString(com.kazumaproject.core.R.string.string_abc)
                    }
                    // Number mode specific texts for JP
                    if (romajiModeState.value) {
                        keyF.text = "@"
                        keyJ.text = "「"
                        keyK.text = "」"
                        keyAtMark.text = "￥"
                        keyL.text = "&"
                        keyZ.text = "。"
                        keyX.text = "、"
                    } else {
                        keyF.text = ";"
                        keyJ.text = "￥"
                        keyK.text = "&"
                        keyL.text = "\""
                        keyZ.text = "."
                        keyX.text = ","
                    }
                }
                attachNumberKeyLabels(false)
                defaultQWERTYButtonsRoman.forEach { it.topRightChar = null }
            }

            QWERTYMode.Symbol -> {
                binding.apply {
                    key123.text = if (romajiModeState.value) {
                        resources.getString(com.kazumaproject.core.R.string.string_abc_japanese)
                    } else {
                        resources.getString(com.kazumaproject.core.R.string.string_abc)
                    }
                    // Symbol mode specific texts for JP
                    if (romajiModeState.value) {
                        keyA.text = "_"
                        keyS.text = "/"
                        keyD.text = ";"
                        keyF.text = "|"
                        keyH.text = ">"
                        keyJ.text = "\""
                        keyK.text = "\'"
                        keyAtMark.text = "$"
                        keyL.text = "€"
                        keyZ.text = "。"
                        keyX.text = "、"
                    } else {
                        keyD.text = "\\"
                        keyF.text = "~"
                        keyH.text = ">"
                        keyJ.text = "$"
                        keyH.text = ">"
                        keyK.text = "€"
                        keyL.text = "・"
                        keyZ.text = "."
                        keyX.text = ","
                    }
                }
                attachNumberKeyLabels(true)
                defaultQWERTYButtonsRoman.forEach { it.topRightChar = null }
            }
        }
        // キーラベルが切り替わった後に Shift drawable を再評価する。
        renderShiftKeyDrawable()
    }

    /**
     * Shift キーの drawable を一箇所で決定する関数。
     *
     * Number / Symbol mode の場合は Shift キーが Number/Symbol 切替キーとして使われるため、
     * CapsLockState よりも QWERTYMode の drawable を優先する。
     * Default mode の場合のみ CapsLock / Shift の状態に応じた drawable を選択する。
     */
    private fun renderShiftKeyDrawable() {
        val drawableRes = when (qwertyMode.value) {
            QWERTYMode.Number -> com.kazumaproject.core.R.drawable.qwerty_symbol
            QWERTYMode.Symbol -> com.kazumaproject.core.R.drawable.qwerty_number
            QWERTYMode.Default -> {
                val state = capsLockState.value
                when {
                    state.capsLockOn -> com.kazumaproject.core.R.drawable.caps_lock
                    state.shiftOn -> com.kazumaproject.core.R.drawable.shift_fill_24px
                    else -> com.kazumaproject.core.R.drawable.shift_24px
                }
            }
        }
        binding.keyShift.setImageResource(drawableRes)
    }

    // CapsLock UI update extraction
    private fun updateCapsLockUI(state: CapsLockState) {
        // 大文字表示の切り替え
        val allCaps = state.shiftOn || state.capsLockOn
        qwertyButtonMap.keys.forEach { button ->
            if (button is AppCompatButton) button.isAllCaps = allCaps
        }
        // Shift キーの drawable は renderShiftKeyDrawable() に集約。
        renderShiftKeyDrawable()
    }

    private fun updateTopRightCharsForDefaultMode() {
        if (!isSymbolKeymapShow || qwertyMode.value != QWERTYMode.Default) {
            defaultQWERTYButtonsRoman.forEach { it.topRightChar = null }
            return
        }

        val buttonsToUpdate =
            if (!romajiModeState.value && !isNumberKeysShow) defaultQWERTYButtonsRoman else if (!romajiModeState.value) defaultQWERTYButtons else defaultQWERTYButtonsRoman
        // Note: simplified logic here, assuming defaultQWERTYButtonsRoman covers most cases or using the original mapping logic.
        // Re-implementing the massive mapping from your original code:

        if (romajiModeState.value) {
            // JP Logic (Simplified for brevity, matches original flow)
            if (isNumberKeysShow) {
                // ... (JP with Number row mapping)
                applyTopRightCharsJP(true)
            } else {
                // ... (JP without Number row mapping)
                applyTopRightCharsJP(false)
            }
        } else {
            // EN Logic
            if (isNumberKeysShow) {
                applyTopRightCharsEN(true)
            } else {
                applyTopRightCharsEN(false)
            }
        }
    }

    // Helper to separate the massive char mapping
    private fun applyTopRightCharsJP(hasNumberRow: Boolean) {
        val buttons = defaultQWERTYButtonsRoman
        buttons.forEach {
            when (it.id) {
                R.id.key_a -> it.topRightChar = '@'
                R.id.key_b -> it.topRightChar = ';'
                R.id.key_c -> it.topRightChar = '\''
                R.id.key_d -> it.topRightChar = '￥'
                R.id.key_e -> it.topRightChar = if (hasNumberRow) '|' else '3'
                R.id.key_f -> it.topRightChar = '_'
                R.id.key_g -> it.topRightChar = '&'
                R.id.key_h -> it.topRightChar = '-'
                R.id.key_i -> it.topRightChar = if (hasNumberRow) '>' else '8'
                R.id.key_j -> it.topRightChar = '+'
                R.id.key_k -> it.topRightChar = '('
                R.id.key_at_mark -> it.topRightChar = ')'
                R.id.key_l -> it.topRightChar = '/'
                R.id.key_m -> it.topRightChar = '?'
                R.id.key_n -> it.topRightChar = '!'
                R.id.key_o -> it.topRightChar = if (hasNumberRow) '{' else '9'
                R.id.key_p -> it.topRightChar = if (hasNumberRow) '}' else '0'
                R.id.key_q -> it.topRightChar = if (hasNumberRow) '%' else '1'
                R.id.key_r -> it.topRightChar = if (hasNumberRow) '=' else '4'
                R.id.key_s -> it.topRightChar = '#'
                R.id.key_t -> it.topRightChar = if (hasNumberRow) '[' else '5'
                R.id.key_u -> it.topRightChar = if (hasNumberRow) '<' else '7'
                R.id.key_v -> it.topRightChar = ':'
                R.id.key_w -> it.topRightChar = if (hasNumberRow) '\\' else '2'
                R.id.key_x -> it.topRightChar = '"'
                R.id.key_y -> it.topRightChar = if (hasNumberRow) ']' else '6'
                R.id.key_z -> it.topRightChar = '*'
            }
        }
    }

    private fun applyTopRightCharsEN(hasNumberRow: Boolean) {
        // EN Logic
        val buttons = if (hasNumberRow) defaultQWERTYButtons else defaultQWERTYButtonsRoman
        buttons.forEach {
            when (it.id) {
                R.id.key_a -> it.topRightChar = '@'
                R.id.key_b -> it.topRightChar = ';'
                R.id.key_c -> it.topRightChar = '\''
                R.id.key_d -> it.topRightChar = '$'
                R.id.key_e -> it.topRightChar = if (hasNumberRow) '|' else '3'
                R.id.key_f -> it.topRightChar = '_'
                R.id.key_g -> it.topRightChar = '&'
                R.id.key_h -> it.topRightChar = '-'
                R.id.key_i -> it.topRightChar = if (hasNumberRow) '>' else '8'
                R.id.key_j -> it.topRightChar = '+'
                R.id.key_k -> it.topRightChar = '('
                R.id.key_l -> it.topRightChar = ')'
                R.id.key_m -> it.topRightChar = '?'
                R.id.key_n -> it.topRightChar = '!'
                R.id.key_o -> it.topRightChar = if (hasNumberRow) '{' else '9'
                R.id.key_p -> it.topRightChar = if (hasNumberRow) '}' else '0'
                R.id.key_q -> it.topRightChar = if (hasNumberRow) '%' else '1'
                R.id.key_r -> it.topRightChar = if (hasNumberRow) '=' else '4'
                R.id.key_s -> it.topRightChar = '#'
                R.id.key_t -> it.topRightChar = if (hasNumberRow) '[' else '5'
                R.id.key_u -> it.topRightChar = if (hasNumberRow) '<' else '7'
                R.id.key_v -> it.topRightChar = ':'
                R.id.key_w -> it.topRightChar = if (hasNumberRow) '\\' else '2'
                R.id.key_x -> it.topRightChar = '"'
                R.id.key_y -> it.topRightChar = if (hasNumberRow) ']' else '6'
                R.id.key_z -> it.topRightChar = '*'
            }
        }
    }

    fun setPopUpViewState(state: Boolean) {
        this.showPopupView = state
    }

    fun setFlickUpDetectionEnabled(enabled: Boolean) {
        this.enableFlickUpDetection = enabled
        updateNumberKeyFlickGuides()
    }

    fun setFlickDownDetectionEnabled(enabled: Boolean) {
        this.enableFlickDownDetection = enabled
        updateNumberKeyFlickGuides()
    }

    fun setNumberKeyFlickUpChars(map: Map<String, String>) {
        numberKeyFlickUpChars = map
        updateNumberKeyFlickGuides()
    }

    fun setNumberKeyFlickDownChars(map: Map<String, String>) {
        numberKeyFlickDownChars = map
        updateNumberKeyFlickGuides()
    }

    fun setDeleteLeftFlickEnabled(enabled: Boolean) {
        this.enableDeleteLeftFlick = enabled
    }

    fun setDeleteUpFlickEnabled(enabled: Boolean) {
        this.enableDeleteUpFlick = enabled
    }

    fun setDeleteDownFlickEnabled(enabled: Boolean) {
        this.enableDeleteDownFlick = enabled
    }

    fun setLongPressTimeout(timeoutMillis: Long) {
        longPressTimeout = timeoutMillis.coerceIn(100L, 2000L)
    }

    fun setOnDeleteLeftFlickListener(listener: () -> Unit) {
        this.onDeleteLeftFlickListener = listener
    }

    fun setOnDeleteUpFlickListener(listener: () -> Unit) {
        this.onDeleteUpFlickListener = listener
    }

    fun setOnDeleteDownFlickListener(listener: () -> Unit) {
        this.onDeleteDownFlickListener = listener
    }

    private fun setMaterialYouTheme(
        isDarkMode: Boolean, isDynamicColorEnable: Boolean
    ) {
        if (!isDynamicColorEnable) return
        val bgRes = if (isDarkMode) com.kazumaproject.core.R.drawable.ten_keys_center_bg_material
        else com.kazumaproject.core.R.drawable.ten_keys_center_bg_material_light

        val bgSideRes = if (isDarkMode) com.kazumaproject.core.R.drawable.ten_keys_side_bg_material
        else com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light

        binding.apply {
            listOf(
                key1, key2, key3, key4, key5, key6, key7, key8, key9, key0,
                keyKuten, keyTouten, keyQ, keyW, keyE, keyR, keyT, keyY, keyU, keyI, keyO, keyP,
                keyA, keyS, keyD, keyF, keyG, keyH, keyJ, keyK, keyAtMark, keyL,
                keyZ, keyX, keyC, keyV, keyB, keyN, keyM, keySpace
            ).forEach {
                it.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
                if (liquidGlassEnable) {
                    it.setDrawableAlpha(liquidGlassKeyAlphaEnable)
                }
            }

            listOf(
                keyShift, keyDelete, keySwitchDefault, keyEmoji, keyReturn, key123,
                switchNumberLayout, cursorLeft, cursorRight, switchRomajiEnglish
            ).forEach {
                it.setBackgroundDrawable(ContextCompat.getDrawable(context, bgSideRes))
                if (liquidGlassEnable) {
                    it.setDrawableAlpha(liquidGlassKeyAlphaEnable)
                }
            }
        }
    }

    fun setSpaceKeyText(text: String) {
        binding.keySpace.text = text
    }

    fun setReturnKeyText(text: String) {
        binding.keyReturn.text = text
    }

    private val specialIconButtons: List<AppCompatImageButton> by lazy {
        listOf(
            binding.keyShift,
            binding.keyDelete,
            binding.keyEmoji,
            binding.keySwitchDefault,
            binding.cursorLeft,
            binding.cursorRight
        )
    }

    private val specialTextButtons: List<TextView> by lazy {
        listOf(
            binding.keySpace,
            binding.key123,
            binding.keyKuten,
            binding.keyTouten,
            binding.switchRomajiEnglish,
            binding.switchNumberLayout,
            binding.keyReturn
        )
    }

    /** Map each key View → its corresponding QWERTYKey. */
    private val qwertyButtonMap: Map<View, QWERTYKey> by lazy {
        mapOf(
            binding.keyA to QWERTYKey.QWERTYKeyA,
            binding.keyB to QWERTYKey.QWERTYKeyB,
            binding.keyC to QWERTYKey.QWERTYKeyC,
            binding.keyD to QWERTYKey.QWERTYKeyD,
            binding.keyE to QWERTYKey.QWERTYKeyE,
            binding.keyF to QWERTYKey.QWERTYKeyF,
            binding.keyG to QWERTYKey.QWERTYKeyG,
            binding.keyH to QWERTYKey.QWERTYKeyH,
            binding.keyI to QWERTYKey.QWERTYKeyI,
            binding.keyJ to QWERTYKey.QWERTYKeyJ,
            binding.keyK to QWERTYKey.QWERTYKeyK,
            binding.keyL to QWERTYKey.QWERTYKeyL,
            binding.keyM to QWERTYKey.QWERTYKeyM,
            binding.keyN to QWERTYKey.QWERTYKeyN,
            binding.keyO to QWERTYKey.QWERTYKeyO,
            binding.keyP to QWERTYKey.QWERTYKeyP,
            binding.keyQ to QWERTYKey.QWERTYKeyQ,
            binding.keyR to QWERTYKey.QWERTYKeyR,
            binding.keyS to QWERTYKey.QWERTYKeyS,
            binding.keyT to QWERTYKey.QWERTYKeyT,
            binding.keyU to QWERTYKey.QWERTYKeyU,
            binding.keyV to QWERTYKey.QWERTYKeyV,
            binding.keyW to QWERTYKey.QWERTYKeyW,
            binding.keyX to QWERTYKey.QWERTYKeyX,
            binding.keyY to QWERTYKey.QWERTYKeyY,
            binding.keyZ to QWERTYKey.QWERTYKeyZ,
            binding.keyAtMark to QWERTYKey.QWERTYKeyAtMark,
            // Side and function keys
            binding.keyShift to QWERTYKey.QWERTYKeyShift,
            binding.keyDelete to QWERTYKey.QWERTYKeyDelete,
            binding.keySwitchDefault to QWERTYKey.QWERTYKeySwitchDefaultLayout,
            binding.keyEmoji to QWERTYKey.QWERTYKeyEmoji,
            binding.key123 to QWERTYKey.QWERTYKeySwitchMode,
            binding.keySpace to QWERTYKey.QWERTYKeySpace,
            binding.keyReturn to QWERTYKey.QWERTYKeyReturn,
            binding.cursorLeft to QWERTYKey.QWERTYKeyCursorLeft,
            binding.cursorRight to QWERTYKey.QWERTYKeyCursorRight,
            binding.keyTouten to QWERTYKey.QWERTYKeyTouten,
            binding.keyKuten to QWERTYKey.QWERTYKeyKuten,
            binding.switchRomajiEnglish to QWERTYKey.QWERTYKeySwitchRomajiEnglish,
            binding.switchNumberLayout to QWERTYKey.QWERTYKeySwitchNumberKey,

            binding.key1 to QWERTYKey.QWERTYKey1,
            binding.key2 to QWERTYKey.QWERTYKey2,
            binding.key3 to QWERTYKey.QWERTYKey3,
            binding.key4 to QWERTYKey.QWERTYKey4,
            binding.key5 to QWERTYKey.QWERTYKey5,
            binding.key6 to QWERTYKey.QWERTYKey6,
            binding.key7 to QWERTYKey.QWERTYKey7,
            binding.key8 to QWERTYKey.QWERTYKey8,
            binding.key9 to QWERTYKey.QWERTYKey9,
            binding.key0 to QWERTYKey.QWERTYKey0,
        )
    }

    private val defaultQWERTYButtons: Array<QWERTYButton> by lazy {
        arrayOf(
            // Top row
            binding.keyQ, binding.keyW, binding.keyE, binding.keyR, binding.keyT,
            binding.keyY, binding.keyU, binding.keyI, binding.keyO, binding.keyP,
            // Middle row
            binding.keyA, binding.keyS, binding.keyD, binding.keyF, binding.keyG,
            binding.keyH, binding.keyJ, binding.keyK, binding.keyL,
            // Bottom row
            binding.keyZ, binding.keyX, binding.keyC, binding.keyV, binding.keyB,
            binding.keyN, binding.keyM
        )
    }

    private val qRowLetterViews: List<QWERTYButton> by lazy {
        listOf(
            binding.keyQ, binding.keyW, binding.keyE, binding.keyR, binding.keyT,
            binding.keyY, binding.keyU, binding.keyI, binding.keyO, binding.keyP
        )
    }

    private val aRowLetterViews: List<QWERTYButton> by lazy {
        listOf(
            binding.keyA, binding.keyS, binding.keyD, binding.keyF, binding.keyG,
            binding.keyH, binding.keyJ, binding.keyK, binding.keyL
        )
    }

    private val zRowLetterViews: List<QWERTYButton> by lazy {
        listOf(
            binding.keyZ, binding.keyX, binding.keyC, binding.keyV, binding.keyB,
            binding.keyN, binding.keyM
        )
    }

    private val defaultQWERTYButtonsRoman: Array<QWERTYButton> by lazy {
        arrayOf(
            // Top row
            binding.keyQ, binding.keyW, binding.keyE, binding.keyR, binding.keyT,
            binding.keyY, binding.keyU, binding.keyI, binding.keyO, binding.keyP,
            // Middle row
            binding.keyA, binding.keyS, binding.keyD, binding.keyF, binding.keyG,
            binding.keyH, binding.keyJ, binding.keyK, binding.keyAtMark, binding.keyL,
            // Bottom row
            binding.keyZ, binding.keyX, binding.keyC, binding.keyV, binding.keyB,
            binding.keyN, binding.keyM
        )
    }

    private val numberQWERTYButtons: Array<QWERTYButton> by lazy {
        arrayOf(
            // Top row
            binding.keyQ, binding.keyW, binding.keyE, binding.keyR, binding.keyT,
            binding.keyY, binding.keyU, binding.keyI, binding.keyO, binding.keyP,
            // Middle row
            binding.keyA, binding.keyS, binding.keyD, binding.keyF, binding.keyG,
            binding.keyH, binding.keyJ, binding.keyK, binding.keyAtMark, binding.keyL,
            // Bottom row
            binding.keyZ, binding.keyX, binding.keyC, binding.keyN, binding.keyM
        )
    }

    private fun attachDefaultKeyLabels() {
        val chars =
            if (romajiModeState.value) QWERTYKeys.DEFAULT_KEYS_JP else QWERTYKeys.DEFAULT_KEYS
        val buttons = if (romajiModeState.value) defaultQWERTYButtonsRoman else defaultQWERTYButtons
        for (i in buttons.indices) {
            buttons[i].text = chars[i].toString()
        }
    }

    private fun attachNumberKeyLabels(isSymbol: Boolean) {
        val chars = if (isSymbol) {
            if (romajiModeState.value) QWERTYKeys.SYMBOL_KEYS_JP else QWERTYKeys.SYMBOL_KEYS
        } else {
            if (romajiModeState.value) QWERTYKeys.NUMBER_KEYS_JP else QWERTYKeys.NUMBER_KEYS
        }
        val buttons = numberQWERTYButtons
        for (i in buttons.indices) {
            buttons[i].text = chars[i].toString()
        }
    }

    fun setOnQWERTYKeyListener(listener: QWERTYKeyListener) {
        this.qwertyKeyListener = listener
    }

    fun setOnQwertyKeyTouchCancelListener(listener: QwertyKeyTouchCancelListener?) {
        qwertyKeyTouchCancelListener = listener
    }

    fun setQwertyGlideInputMode(enabled: Boolean) {
        if (qwertyGlideInputMode == enabled) return
        qwertyGlideInputMode = enabled
        if (!enabled) {
            cancelQwertyGlideCandidate(notify = false)
        }
    }

    fun setQwertyGlideInputListener(listener: QwertyGlideInputListener?) {
        qwertyGlideInputListener = listener
    }

    fun getQwertyKeyboardProximityInfo(): QwertyKeyboardProximityInfo {
        val letterViews = getVisibleQwertyLetterViews()
        val keysWithoutNeighbors = letterViews.mapIndexed { index, view ->
            val row = when (view) {
                in qRowLetterViews -> 0
                in aRowLetterViews -> 1
                else -> 2
            }
            val column = when (row) {
                0 -> qRowLetterViews.indexOf(view)
                1 -> aRowLetterViews.indexOf(view)
                else -> zRowLetterViews.indexOf(view)
            }
            val ch = view.text?.firstOrNull()?.lowercaseChar() ?: ('a' + index)
            QwertyKeyProximity(
                char = ch,
                centerX = view.left + view.width / 2f,
                centerY = view.top + view.height / 2f,
                width = view.width.toFloat(),
                height = view.height.toFloat(),
                rowIndex = row,
                columnIndex = column,
                neighborChars = emptyList()
            )
        }.filter { it.char in 'a'..'z' }

        val averageKeyWidth = keysWithoutNeighbors.map { it.width }.average().toFloatOrDefault()
        val averageKeyHeight = keysWithoutNeighbors.map { it.height }.average().toFloatOrDefault()
        val neighborRadius = hypot(averageKeyWidth, averageKeyHeight) * 1.35f
        val keys = keysWithoutNeighbors.map { key ->
            key.copy(
                neighborChars = keysWithoutNeighbors
                    .asSequence()
                    .filter { it.char != key.char }
                    .map { other ->
                        other.char to hypot(key.centerX - other.centerX, key.centerY - other.centerY)
                    }
                    .filter { (_, distance) -> distance <= neighborRadius }
                    .sortedBy { (_, distance) -> distance }
                    .map { (char, _) -> char }
                    .take(8)
                    .toList()
            )
        }

        return QwertyKeyboardProximityInfo(
            keys = keys,
            keyboardWidth = width,
            keyboardHeight = height,
            averageKeyWidth = averageKeyWidth,
            averageKeyHeight = averageKeyHeight
        )
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return event.actionMasked == MotionEvent.ACTION_DOWN
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {

        if (isCursorMode) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val threshold = 20f
                    val currentX = event.x
                    val currentY = event.y

                    val dx = currentX - cursorInitialX
                    val dy = currentY - cursorInitialY

                    if (abs(dx) > abs(dy) && abs(dx) > threshold) {
                        val direction =
                            if (dx < 0f) QWERTYKey.QWERTYKeyCursorLeft else QWERTYKey.QWERTYKeyCursorRight
                        qwertyKeyListener?.onReleasedQWERTYKey(direction, null, null)
                        cursorInitialX = currentX
                        cursorInitialY = currentY
                    } else if (abs(dy) > abs(dx) && abs(dy) > threshold) {
                        val direction =
                            if (dy < 0f) QWERTYKey.QWERTYKeyCursorUp else QWERTYKey.QWERTYKeyCursorDown
                        qwertyKeyListener?.onReleasedQWERTYKey(direction, null, null)
                        cursorInitialX = currentX
                        cursorInitialY = currentY
                    }
                }

                MotionEvent.ACTION_UP -> {
                    setCursorMode(false)
                    clearAllPressed()
                }

                MotionEvent.ACTION_CANCEL -> {
                    notifyQwertyTouchCanceledForActivePointers(KeyTouchCancelReason.ActionCancel)
                    setCursorMode(false)
                    clearAllPressed()
                }
            }
            return true
        }

        if (handleQwertyGlideTouchEvent(event)) {
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount > 1 || pointerButtonMap.isNotEmpty()) {
                    clearAllPressed()
                }
                suppressedPointerId = null
                handlePointerDown(event, pointerIndex = 0)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (variationPopup?.isShowing == true) return true
                if (pointerButtonMap.size == 1) {
                    val firstPointerId = pointerButtonMap.keyAt(0)
                    val firstView = pointerButtonMap.valueAt(0)
                    firstView?.let { view ->
                        view.isPressed = false
                        dismissKeyPreview()
                        notifyQwertyTouchCanceledForPointer(
                            firstPointerId,
                            KeyTouchCancelReason.PointerInterrupted
                        )
                        cancelLongPressForPointer(firstPointerId)
                        pointerStartCoords.remove(firstPointerId)
                        flickLockedPointers.remove(firstPointerId)

                        val qwertyKey = qwertyButtonMap[view] ?: QWERTYKey.QWERTYKeyNotSelect
                        if (firstPointerId == 0 && qwertyKey == QWERTYKey.QWERTYKeySwitchDefaultLayout) {
                            return true
                        }
                        logVariationIfNeeded(qwertyKey)
                    }
                    suppressedPointerId = firstPointerId
                    pointerButtonMap.remove(firstPointerId)
                }
                val newIndex = event.actionIndex
                handlePointerDown(event, pointerIndex = newIndex)
            }

            MotionEvent.ACTION_MOVE -> {
                if (variationPopup?.isShowing == true && longPressedPointerId != null) {
                    val index = event.findPointerIndex(longPressedPointerId!!)
                    if (index != -1) {
                        val location = IntArray(2)
                        variationPopupView?.getLocationOnScreen(location)
                        val touchX = event.rawX
                        val touchY = event.rawY
                        val popupX = touchX - location[0]
                        val popupY = touchY - location[1]
                        variationPopupView?.updateSelection(popupX, popupY)
                    }
                } else {
                    for (i in 0 until event.pointerCount) {
                        val pid = event.getPointerId(i)
                        if (pid == suppressedPointerId || pid == lockedPointerId) continue
                        handlePointerMove(event, pointerIndex = i, pointerId = pid)
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)

                if (variationPopup?.isShowing == true && pointerId == longPressedPointerId) {
                    if (variationPopup?.isShowing == true) {
                        variationPopupView?.getSelectedChar()?.let { selectedChar ->
                            val qwertyKey =
                                qwertyButtonMap[pointerButtonMap[longPressedPointerId!!]]
                                    ?: QWERTYKey.QWERTYKeyNotSelect
                            qwertyKeyListener?.onReleasedQWERTYKey(
                                qwertyKey = qwertyKey, tap = selectedChar, variations = null
                            )
                        }
                        disableShift()
                        variationPopup?.dismiss()
                        variationPopup = null
                        variationPopupView = null
                        longPressedPointerId = null
                    }
                    clearAllPressed()
                    return true
                }

                if (suppressedPointerId == pointerId) {
                    suppressedPointerId = null
                }
                pointerStartCoords.remove(pointerId)

                if (!flickLockedPointers.contains(pointerId)) {
                    val view = pointerButtonMap[pointerId]
                    view?.let {
                        it.isPressed = false
                        dismissKeyPreview()
                        cancelLongPressForPointer(pointerId)
                        val wasShift = (it.id == binding.keyShift.id)
                        if (wasShift && shiftDoubleTapped) {
                            shiftDoubleTapped = false
                        } else {
                            val qwertyKey = qwertyButtonMap[it] ?: QWERTYKey.QWERTYKeyNotSelect
                            when (qwertyKey) {
                                QWERTYKey.QWERTYKeyCursorLeft, QWERTYKey.QWERTYKeyCursorRight, QWERTYKey.QWERTYKeySwitchRomajiEnglish, QWERTYKey.QWERTYKeySwitchNumberKey, QWERTYKey.QWERTYKeyEmoji -> {
                                    qwertyKeyListener?.onReleasedQWERTYKey(qwertyKey, null, null)
                                }

                                else -> {
                                    logVariationIfNeeded(qwertyKey)
                                    setToggleShiftState(view)
                                }
                            }
                        }
                    }
                }
                pointerButtonMap.remove(pointerId)
            }

            MotionEvent.ACTION_UP -> {
                val liftedId = event.getPointerId(event.actionIndex)
                if (variationPopup?.isShowing == true) {
                    variationPopupView?.getSelectedChar()?.let { selectedChar ->
                        val qwertyKey = qwertyButtonMap[pointerButtonMap[longPressedPointerId!!]]
                            ?: QWERTYKey.QWERTYKeyNotSelect
                        qwertyKeyListener?.onReleasedQWERTYKey(
                            qwertyKey = qwertyKey, tap = selectedChar, variations = null
                        )
                    }
                    disableShift()
                    variationPopup?.dismiss()
                    variationPopup = null
                    variationPopupView = null
                    longPressedPointerId = null
                } else if (!flickLockedPointers.contains(liftedId)) {
                    val liftedId2 = event.getPointerId(event.actionIndex)
                    if (suppressedPointerId == liftedId2) {
                        suppressedPointerId = null
                    }
                    if (pointerButtonMap.size == 1) {
                        val view = pointerButtonMap.valueAt(0)
                        view?.let {
                            it.isPressed = false
                            dismissKeyPreview()
                            cancelLongPressForPointer(liftedId2)
                            val wasShift = (it.id == binding.keyShift.id)
                            if (wasShift && shiftDoubleTapped) {
                                shiftDoubleTapped = false
                            } else {
                                val qwertyMode = qwertyMode.value
                                if (qwertyMode != QWERTYMode.Default && wasShift) {
                                    if (qwertyMode == QWERTYMode.Number) {
                                        _qwertyMode.update { QWERTYMode.Symbol }
                                    } else {
                                        _qwertyMode.update { QWERTYMode.Number }
                                    }
                                } else {
                                    val qwertyKey =
                                        qwertyButtonMap[it] ?: QWERTYKey.QWERTYKeyNotSelect
                                    when (qwertyKey) {
                                        QWERTYKey.QWERTYKeyCursorLeft, QWERTYKey.QWERTYKeyCursorRight, QWERTYKey.QWERTYKeySwitchRomajiEnglish, QWERTYKey.QWERTYKeySwitchNumberKey, QWERTYKey.QWERTYKeyEmoji -> {
                                            qwertyKeyListener?.onReleasedQWERTYKey(
                                                qwertyKey,
                                                null,
                                                null
                                            )
                                        }

                                        else -> {
                                            logVariationIfNeeded(qwertyKey)
                                            setToggleShiftState(view)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                clearAllPressed()
                lastNonGlideKeyUpTime = SystemClock.uptimeMillis()
            }

            MotionEvent.ACTION_CANCEL -> {
                notifyQwertyTouchCanceledForActivePointers(KeyTouchCancelReason.ActionCancel)
                variationPopup?.dismiss()
                variationPopup = null
                variationPopupView = null
                longPressedPointerId = null
                clearAllPressed()
                cancelQwertyGlideCandidate(notify = true)
            }
        }
        return true
    }

    private fun handleQwertyGlideTouchEvent(event: MotionEvent): Boolean {
        if (!qwertyGlideInputMode || romajiModeState.value) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                beginQwertyGlideCandidateIfPossible(event, event.actionIndex)
                return false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (glideCandidatePointerId != null) {
                    cancelQwertyGlideCandidate(notify = glideStarted)
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerId = glideCandidatePointerId ?: return false
                val pointerIndex = event.findPointerIndex(pointerId)
                if (pointerIndex < 0) {
                    cancelQwertyGlideCandidate(notify = glideStarted)
                    return true
                }
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                val keyHit = classifyQwertyGlideKeyHit(x, y)
                val moveHandling = QwertyGlideKeyClassifier.decideMoveHandling(
                    glidePointerId = glideCandidatePointerId,
                    pointerId = pointerId,
                    keyHit = keyHit
                )

                if (moveHandling == QwertyGlideKeyClassifier.MoveHandling.ROUTE_TO_NORMAL_KEY_HANDLER) {
                    return false
                }

                if (!glideStarted) {
                    // Glide 確定前の微小 MOVE では通常の長押し予約を保持する。
                    if (moveHandling == QwertyGlideKeyClassifier.MoveHandling.APPEND_TO_GLIDE_PATH) {
                        appendHistoricalQwertyGlideLetterPoints(event, pointerIndex, pointerId)
                        appendQwertyGlidePoint(
                            x = x,
                            y = y,
                            eventTime = event.eventTime,
                            pointerId = pointerId
                        )
                        if (shouldStartQwertyGlide(event)) {
                            startQwertyGlide(pointerId)
                            qwertyGlideInputListener?.onQwertyGlideUpdated(
                                inputPointers = QwertyInputPointers(glideRawPoints.toList()),
                                proximityInfo = getQwertyKeyboardProximityInfo()
                            )
                            return true
                        }
                    }

                    return if (isPendingQwertyGlideMoveWithinLongPressBounds(pointerId, x, y)) {
                        false
                    } else {
                        cancelLongPressForPointer(pointerId)
                        false
                    }
                }

                // Glide candidate / active pointer の MOVE はここで所有する。
                // 通常 MOVE に落とすと pointerButtonMap が数字・句読点などへ
                // 書き換わり、UP で通常 tap として commit されてしまう。
                if (moveHandling == QwertyGlideKeyClassifier.MoveHandling.IGNORE_AND_CONSUME) {
                    releasePressedKeyForGlideMove(pointerId)
                    return true
                }

                releasePressedKeyForGlideMove(pointerId)
                appendHistoricalQwertyGlideLetterPoints(event, pointerIndex, pointerId)
                appendQwertyGlidePoint(
                    x = x,
                    y = y,
                    eventTime = event.eventTime,
                    pointerId = pointerId
                )
                if (!glideStarted && shouldStartQwertyGlide(event)) {
                    startQwertyGlide(pointerId)
                }
                if (glideStarted) {
                    qwertyGlideInputListener?.onQwertyGlideUpdated(
                        inputPointers = QwertyInputPointers(glideRawPoints.toList()),
                        proximityInfo = getQwertyKeyboardProximityInfo()
                    )
                    return true
                }
                return false
            }

            MotionEvent.ACTION_POINTER_UP -> {
                return handleQwertyGlidePointerUp(event)
            }

            MotionEvent.ACTION_UP -> {
                return handleQwertyGlidePointerUp(event)
            }

            MotionEvent.ACTION_CANCEL -> {
                val consumed = glideCandidatePointerId != null
                cancelQwertyGlideCandidate(notify = glideStarted)
                return consumed
            }
        }
        return false
    }

    private fun setToggleShiftState(view: View) {
        if (view.id == binding.keyShift.id) {
            if (capsLockState.value.capsLockOn || capsLockState.value.shiftOn) {
                clearShiftCaps()
            } else {
                toggleShift()
            }
        } else if (view.id == binding.keyDelete.id || view.id == binding.keySpace.id) {
            /** empty body **/
        } else {
            disableShift()
        }
    }

    private fun beginQwertyGlideCandidateIfPossible(event: MotionEvent, pointerIndex: Int) {
        val pointerId = event.getPointerId(pointerIndex)
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)
        if (findExactQwertyGlideLetterViewUnder(x, y) == null) return
        if (SystemClock.uptimeMillis() - lastNonGlideKeyUpTime < glideFastTypingSuppressMillis) return

        glideCandidatePointerId = pointerId
        glideStarted = false
        glideDownTime = event.eventTime
        glideRawPoints.clear()
        glideTrailPoints.clear()
        glideLastSampleX = x
        glideLastSampleY = y
        appendQwertyGlidePoint(x, y, event.eventTime, pointerId, force = true)
    }

    private fun startQwertyGlide(pointerId: Int) {
        val pressedView = pointerButtonMap[pointerId]
        pressedView?.isPressed = false
        dismissKeyPreview()
        notifyQwertyTouchCanceledForPointer(pointerId, KeyTouchCancelReason.PointerInterrupted)
        cancelLongPressForPointer(pointerId)
        variationPopup?.dismiss()
        variationPopup = null
        variationPopupView = null
        longPressedPointerId = null
        pointerButtonMap.remove(pointerId)
        pointerStartCoords.remove(pointerId)
        flickLockedPointers.add(pointerId)
        glideStarted = true
        qwertyGlideInputListener?.onQwertyGlideStarted()
    }

    private fun isPendingQwertyGlideMoveWithinLongPressBounds(
        pointerId: Int,
        x: Float,
        y: Float
    ): Boolean {
        val pressedView = pointerButtonMap[pointerId] ?: return false
        pressedView.getHitRect(glideHitRect)
        glideHitRect.inset(-pendingGlideLongPressSlop, -pendingGlideLongPressSlop)
        return glideHitRect.contains(x.toInt(), y.toInt())
    }

    private fun handleQwertyGlidePointerUp(event: MotionEvent): Boolean {
        val pointerId = glideCandidatePointerId ?: return false
        val liftedId = event.getPointerId(event.actionIndex)
        if (liftedId != pointerId) return false
        val upX = event.getX(event.actionIndex)
        val upY = event.getY(event.actionIndex)
        if (findExactQwertyGlideLetterViewUnder(upX, upY) != null) {
            appendQwertyGlidePoint(
                x = upX,
                y = upY,
                eventTime = event.eventTime,
                pointerId = pointerId
            )
        }
        return if (glideStarted) {
            qwertyGlideInputListener?.onQwertyGlideEnded(
                inputPointers = QwertyInputPointers(glideRawPoints.toList()),
                proximityInfo = getQwertyKeyboardProximityInfo()
            )
            clearQwertyGlideState(clearTrail = true)
            clearAllPressed()
            true
        } else {
            clearQwertyGlideState(clearTrail = true)
            false
        }
    }

    private fun releasePressedKeyForGlideMove(pointerId: Int) {
        pointerButtonMap[pointerId]?.isPressed = false
        dismissKeyPreview()
        notifyQwertyTouchCanceledForPointer(pointerId, KeyTouchCancelReason.PointerInterrupted)
        cancelLongPressForPointer(pointerId)
    }

    /**
     * Glide 開始後に history を取り込むための関数。
     *
     * history に含まれる各座標が「Glide 用 alphabet key 上にある」ものだけを
     * raw points / trail に採用する。Space / 数字キー / Return など非アルファベットキー上の座標は、
     * 一瞬通過しただけでも history に乗っているケースがあり、それらが
     * `glideRawPoints` や proximity decoder に混入して候補生成を歪める原因に
     * なるため除外する。
     */
    private fun appendHistoricalQwertyGlideLetterPoints(
        event: MotionEvent,
        pointerIndex: Int,
        pointerId: Int
    ) {
        for (historyIndex in 0 until event.historySize) {
            val hx = event.getHistoricalX(pointerIndex, historyIndex)
            val hy = event.getHistoricalY(pointerIndex, historyIndex)
            if (findExactQwertyGlideLetterViewUnder(hx, hy) == null) {
                continue
            }
            appendQwertyGlidePoint(
                x = hx,
                y = hy,
                eventTime = event.getHistoricalEventTime(historyIndex),
                pointerId = pointerId
            )
        }
    }

    private fun appendQwertyGlidePoint(
        x: Float,
        y: Float,
        eventTime: Long,
        pointerId: Int,
        force: Boolean = false
    ) {
        if (!force && hypot(x - glideLastSampleX, y - glideLastSampleY) < glideSamplingMinDistance) {
            return
        }
        glideLastSampleX = x
        glideLastSampleY = y
        val relativeTime = (eventTime - glideDownTime).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        glideRawPoints.add(
            QwertyInputPointerPoint(
                x = x.toInt(),
                y = y.toInt(),
                time = relativeTime,
                pointerId = pointerId
            )
        )
        glideTrailPoints.add(x to y)
        invalidate()
    }

    private fun shouldStartQwertyGlide(event: MotionEvent): Boolean {
        if (glideRawPoints.size < 3) return false
        val first = glideRawPoints.first()
        val last = glideRawPoints.last()
        val directDistance = hypot(
            (last.x - first.x).toFloat(),
            (last.y - first.y).toFloat()
        )
        val elapsed = event.eventTime - glideDownTime
        return QwertyGlideGesturePolicy.shouldStart(
            pointCount = glideRawPoints.size,
            directDistance = directDistance,
            elapsedMillis = elapsed,
            distinctLetterKeysNearTrail = countDistinctQwertyLetterKeysNearTrail(),
            minMoveDistance = glideMinMoveDistance,
            fastMoveDistance = glideFastMoveDistance,
            minElapsedMillis = glideMinElapsedMillis
        )
    }

    private fun countDistinctQwertyLetterKeysNearTrail(): Int {
        if (glideRawPoints.isEmpty()) return 0
        val proximityInfo = getQwertyKeyboardProximityInfo()
        val radius = (proximityInfo.averageKeyWidth.coerceAtLeast(1f) * 0.75f)
        return glideRawPoints.mapNotNull { point ->
            proximityInfo.keys
                .minByOrNull { key -> hypot(point.x - key.centerX, point.y - key.centerY) }
                ?.takeIf { key -> hypot(point.x - key.centerX, point.y - key.centerY) <= radius }
                ?.char
        }.distinct().size
    }

    private fun isInsideQwertyGlideGestureArea(x: Float, y: Float): Boolean {
        val keys = getVisibleQwertyLetterViews()
        if (keys.isEmpty()) return false
        val left = keys.minOf { it.left }.toFloat() - keySideMarginDp * resources.displayMetrics.density * 3f
        val right = keys.maxOf { it.right }.toFloat() + keySideMarginDp * resources.displayMetrics.density * 3f
        val top = keys.minOf { it.top }.toFloat() - keyVerticalMarginDp * resources.displayMetrics.density * 3f
        val bottom = keys.maxOf { it.bottom }.toFloat() + keyVerticalMarginDp * resources.displayMetrics.density * 3f
        return x in left..right && y in top..bottom
    }

    private fun cancelQwertyGlideCandidate(notify: Boolean) {
        if (notify) qwertyGlideInputListener?.onQwertyGlideCancelled()
        clearQwertyGlideState(clearTrail = true)
        clearAllPressed()
    }

    private fun clearQwertyGlideState(clearTrail: Boolean) {
        glideCandidatePointerId = null
        glideStarted = false
        glideDownTime = 0L
        glideRawPoints.clear()
        if (clearTrail) {
            glideTrailPoints.clear()
            invalidate()
        }
    }

    private fun clearPendingQwertyGlideCandidateForLongPress(pointerId: Int) {
        if (glideCandidatePointerId == pointerId && !glideStarted) {
            clearQwertyGlideState(clearTrail = true)
        }
    }

    fun resetQWERTYKeyboard() {
        cancelQwertyGlideCandidate(notify = glideStarted)
        clearShiftCaps()
        _qwertyMode.update { QWERTYMode.Default }
        _romajiModeState.update { false }
        binding.apply {
            keySpace.text = resources.getString(com.kazumaproject.core.R.string.space_english)
        }
        refreshSpecialKeyIconSizesWhenLaidOut()
    }

    fun resetQWERTYKeyboard(enterKyeText: String) {
        cancelQwertyGlideCandidate(notify = glideStarted)
        clearShiftCaps()
        _qwertyMode.update { QWERTYMode.Default }
        _romajiModeState.update { false }
        binding.apply {
            keySpace.text = resources.getString(com.kazumaproject.core.R.string.space_english)
            keyReturn.text = enterKyeText
        }
        refreshSpecialKeyIconSizesWhenLaidOut()
    }

    fun setNumberView() {
        cancelQwertyGlideCandidate(notify = glideStarted)
        clearShiftCaps()
        _qwertyMode.update { QWERTYMode.Number }
        _romajiModeState.update { false }
        binding.apply {
            keySpace.text = resources.getString(com.kazumaproject.core.R.string.space_english)
        }
        refreshSpecialKeyIconSizesWhenLaidOut()
    }

    fun setRomajiKeyboard(enterKeyText: String) {
        cancelQwertyGlideCandidate(notify = glideStarted)
        clearShiftCaps()
        _qwertyMode.update { QWERTYMode.Default }
        _romajiModeState.update { true }
        binding.apply {
            keySpace.text = resources.getString(com.kazumaproject.core.R.string.space_japanese)
            keyReturn.text = enterKeyText
        }
        refreshSpecialKeyIconSizesWhenLaidOut()
    }

    /**
     * 現在の QWERTY 表示状態のスナップショットを返す。
     *
     * Floating mode と通常モードのように 2 つの QWERTYKeyboardView インスタンス間で
     * 状態を非破壊的にコピーするために [renderUiState] と組み合わせて利用する。
     */
    fun snapshotUiState(): QwertyKeyboardUiState {
        return QwertyKeyboardUiState(
            qwertyMode = qwertyMode.value,
            capsLockState = capsLockState.value,
            romajiMode = romajiModeState.value,
            enterKeyText = binding.keyReturn.text?.toString().orEmpty(),
            spaceKeyText = binding.keySpace.text?.toString().orEmpty(),
            showRomajiEnglishSwitchKey = binding.switchRomajiEnglish.isVisible
        )
    }

    /**
     * 渡された [state] をそのまま反映する。
     *
     * [resetQWERTYKeyboard] のように内部状態を初期化する関数ではない。
     * - Shift / CapsLock を無条件にクリアしない
     * - qwertyMode を Default に戻さない
     *
     * Floating mode ON / OFF 切り替え時、または surface 再描画時に
     * もう一方の QWERTYKeyboardView へ現在状態を伝搬する用途で利用する。
     */
    fun renderUiState(state: QwertyKeyboardUiState) {
        cancelQwertyGlideCandidate(notify = glideStarted)
        // romaji を先に反映してから qwertyMode を反映することで、
        // applyContentForMode で参照される romajiMode の値が正しい状態で
        // 各キーラベルが描画されるようにする。
        _romajiModeState.value = state.romajiMode
        _qwertyMode.value = state.qwertyMode
        _capsLockState.value = state.capsLockState

        binding.apply {
            keyReturn.text = state.enterKeyText
            keySpace.text = state.spaceKeyText
            switchRomajiEnglish.isVisible = state.showRomajiEnglishSwitchKey
        }

        // Floating PopupWindow の detach 中は collector が止まっているため、
        // StateFlow の emit だけに依存せず、渡された状態をこの場で描画する。
        applyLayoutForMode(state.qwertyMode)
        applyContentForMode(state.qwertyMode)
        applyRomajiModeLabels(state.romajiMode)
        updateCapsLockUI(state.capsLockState)
        renderShiftKeyDrawable()
        binding.apply {
            keyReturn.text = state.enterKeyText
            keySpace.text = state.spaceKeyText
            switchRomajiEnglish.isVisible = state.showRomajiEnglishSwitchKey
        }
        refreshSpecialKeyIconSizesWhenLaidOut()
    }

    private fun handlePointerDown(event: MotionEvent, pointerIndex: Int) {
        val pid = event.getPointerId(pointerIndex)
        if (pid == suppressedPointerId) return

        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)

        pointerStartCoords.put(pid, Pair(x, y))
        flickLockedPointers.remove(pid)

        val view = findButtonUnder(x.toInt(), y.toInt())
        view?.let {
            val qwertyKey = qwertyButtonMap[it]
            qwertyKey?.let { key ->
                qwertyKeyListener?.onPressedQWERTYKey(key)
            }
            it.isPressed = true
            pointerButtonMap.put(pid, it)

            if (it.id == binding.keyShift.id) {
                val now = SystemClock.uptimeMillis()
                if (now - lastShiftTapTime <= doubleTapTimeout) {
                    onShiftDoubleTapped()
                    shiftDoubleTapped = true
                    lastShiftTapTime = 0L
                } else {
                    lastShiftTapTime = now
                }
            }

            if (it.id != binding.keySpace.id &&
                it.id != binding.keyDelete.id &&
                it.id != binding.keyShift.id &&
                it.id != binding.key123.id &&
                it.id != binding.keyReturn.id &&
                it.id != binding.keySwitchDefault.id &&
                it.id != binding.keyEmoji.id &&
                it.id != binding.cursorLeft.id &&
                it.id != binding.cursorRight.id &&
                it.id != binding.switchRomajiEnglish.id &&
                it.id != binding.switchNumberLayout.id
            ) {
                showKeyPreview(it)
            }
            scheduleLongPressForPointer(pid, it)
        }
    }

    private enum class FlickDirection { NONE, UP, DOWN, LEFT }

    private fun detectFlickDirection(
        x: Float, y: Float, startX: Float, startY: Float, threshold: Float
    ): FlickDirection {
        val dx = x - startX
        val dy = y - startY
        if (abs(dx) > abs(dy)) {
            if (abs(dx) > threshold && dx < 0) return FlickDirection.LEFT
        } else {
            if (abs(dy) > threshold) return if (dy < 0) FlickDirection.UP else FlickDirection.DOWN
        }
        return FlickDirection.NONE
    }

    private fun applyCommonFlickEffects(pointerId: Int, previousView: View) {
        flickLockedPointers.add(pointerId)
        previousView.isPressed = false
        dismissKeyPreview()
        cancelLongPressForPointer(pointerId)
    }

    private fun handleUpFlick(previousView: View) {
        val qwertyKey = qwertyButtonMap[previousView] ?: QWERTYKey.QWERTYKeyNotSelect
        getNumberKeyFlickUpChar(qwertyKey)?.let { charToInsert ->
            qwertyKeyListener?.onFlickUPQWERTYKey(
                qwertyKey = qwertyKey,
                tap = charToInsert,
                variations = listOf(charToInsert)
            )
            return
        }
        val variations = getVariationInfo(qwertyKey)
        variations?.let { variation ->
            qwertyKeyListener?.onFlickUPQWERTYKey(
                qwertyKey = qwertyKey,
                tap = variation.tap,
                variations = variations.variations
            )
        }
    }

    private fun handleDownFlick(previousView: View) {
        if (previousView !is QWERTYButton) return
        val qwertyKey = qwertyButtonMap[previousView] ?: QWERTYKey.QWERTYKeyNotSelect
        getNumberKeyFlickDownChar(qwertyKey)?.let { charToInsert ->
            qwertyKeyListener?.onFlickDownQWERTYKey(qwertyKey = qwertyKey, character = charToInsert)
            return
        }
        if (qwertyKey == QWERTYKey.QWERTYKeySpace) return
        val baseChar = previousView.text.firstOrNull()?.uppercaseChar() ?: return
        val charToInsert = if (romajiModeState.value) baseChar.toZenkaku() else baseChar
        qwertyKeyListener?.onFlickDownQWERTYKey(qwertyKey = qwertyKey, character = charToInsert)
    }

    private fun getNumberKeyFlickUpChar(qwertyKey: QWERTYKey): Char? {
        return QwertyNumberKeyFlickConfig.charForKeyWhenEnabled(
            key = qwertyKey,
            chars = numberKeyFlickUpChars,
            isNumberKeysShown = isNumberKeysShow,
            isFlickEnabled = enableFlickUpDetection
        )
    }

    private fun getNumberKeyFlickDownChar(qwertyKey: QWERTYKey): Char? {
        return QwertyNumberKeyFlickConfig.charForKeyWhenEnabled(
            key = qwertyKey,
            chars = numberKeyFlickDownChars,
            isNumberKeysShown = isNumberKeysShow,
            isFlickEnabled = enableFlickDownDetection
        )
    }

    private fun isQwertyUpFlickEnabledForCurrentGesture(): Boolean {
        return !qwertyGlideInputMode && enableFlickUpDetection
    }

    private fun isQwertyDownFlickEnabledForCurrentGesture(): Boolean {
        return !qwertyGlideInputMode && enableFlickDownDetection
    }

    private fun handlePointerMove(event: MotionEvent, pointerIndex: Int, pointerId: Int) {
        if (pointerId == suppressedPointerId || pointerId == lockedPointerId) return
        if (flickLockedPointers.contains(pointerId)) return

        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)
        val previousView = pointerButtonMap[pointerId]

        pointerStartCoords[pointerId]?.let { (startX, startY) ->
            val flickDirection = detectFlickDirection(x, y, startX, startY, flickThreshold)
            when (flickDirection) {
                FlickDirection.UP -> {
                    // Delete キーの上フリックを優先 (左フリックと同じ流儀)
                    if (enableDeleteUpFlick && previousView != null && previousView.id == binding.keyDelete.id) {
                        applyCommonFlickEffects(pointerId, previousView)
                        onDeleteUpFlickListener?.invoke()
                        return
                    }
                    if (isQwertyUpFlickEnabledForCurrentGesture() && previousView is QWERTYButton) {
                        applyCommonFlickEffects(pointerId, previousView)
                        handleUpFlick(previousView)
                        return
                    }
                }

                FlickDirection.DOWN -> {
                    // Delete キーの下フリックを優先 (左フリックと同じ流儀)
                    if (enableDeleteDownFlick && previousView != null && previousView.id == binding.keyDelete.id) {
                        applyCommonFlickEffects(pointerId, previousView)
                        onDeleteDownFlickListener?.invoke()
                        return
                    }
                    if (isQwertyDownFlickEnabledForCurrentGesture() && previousView is QWERTYButton) {
                        applyCommonFlickEffects(pointerId, previousView)
                        handleDownFlick(previousView)
                        return
                    }
                }

                FlickDirection.LEFT -> {
                    if (enableDeleteLeftFlick && previousView != null && previousView.id == binding.keyDelete.id) {
                        applyCommonFlickEffects(pointerId, previousView)
                        onDeleteLeftFlickListener?.invoke()
                        return
                    }
                }

                FlickDirection.NONE -> {}
            }
        }

        val currentView = findButtonUnder(x.toInt(), y.toInt())
        if (currentView != previousView) {
            previousView?.let {
                it.isPressed = false
                dismissKeyPreview()
                cancelLongPressForPointer(pointerId)
            }
            currentView?.let {
                it.isPressed = true
                pointerButtonMap.put(pointerId, it)
                pointerStartCoords.put(pointerId, Pair(x, y))

                if (it.id != binding.keySpace.id &&
                    it.id != binding.keyDelete.id &&
                    it.id != binding.keyShift.id &&
                    it.id != binding.key123.id &&
                    it.id != binding.keyReturn.id &&
                    it.id != binding.keySwitchDefault.id &&
                    it.id != binding.keyEmoji.id &&
                    it.id != binding.switchNumberLayout.id &&
                    it.id != binding.cursorRight.id &&
                    it.id != binding.cursorLeft.id
                ) {
                    showKeyPreview(it)
                }
            } ?: run {
                pointerButtonMap.remove(pointerId)
                pointerStartCoords.remove(pointerId)
                cancelLongPressForPointer(pointerId)
            }
        }
    }

    private fun clearAllPressed() {
        for (i in 0 until pointerButtonMap.size) {
            val pid = pointerButtonMap.keyAt(i)
            pointerButtonMap.valueAt(i)?.isPressed = false
            cancelLongPressForPointer(pid)
        }
        pointerButtonMap.clear()
        pointerStartCoords.clear()
        flickLockedPointers.clear()
        dismissKeyPreview()
        suppressedPointerId = null
        variationPopup?.dismiss()
        variationPopup = null
        variationPopupView = null
        longPressedPointerId = null
        lockedPointerId = null
    }

    private fun notifyQwertyTouchCanceledForActivePointers(
        reason: KeyTouchCancelReason
    ) {
        for (i in 0 until pointerButtonMap.size()) {
            val view = pointerButtonMap.valueAt(i) ?: continue
            val key = qwertyButtonMap[view] ?: continue
            qwertyKeyTouchCancelListener?.onQwertyKeyTouchCanceled(key, reason)
        }
    }

    private fun notifyQwertyTouchCanceledForPointer(
        pointerId: Int,
        reason: KeyTouchCancelReason
    ) {
        val view = pointerButtonMap[pointerId] ?: return
        val key = qwertyButtonMap[view] ?: return
        qwertyKeyTouchCancelListener?.onQwertyKeyTouchCanceled(key, reason)
    }

    private fun showKeyPreview(view: View) {
        if (isTablet) return
        if (!showPopupView) return
        dismissKeyPreview()
        val previewHeight = dpToPx(view.height)
        val layoutRes = R.layout.key_preview_large
        val popupView = LayoutInflater.from(context).inflate(layoutRes, this, false)
        val tv = popupView.findViewById<TextView>(R.id.preview_text)
        val iv = popupView.findViewById<ImageView>(R.id.preview_bubble_bg)
        tv.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            keyPreviewPopupStyle.textSizeSp.coerceIn(8f, 48f)
        )
        val isLandMode =
            (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)

        val leftKeyIds: Set<Int> =
            if (qwertyMode.value == QWERTYMode.Default && !isLandMode && !romajiModeState.value) {
                setOf(binding.keyQ.id, binding.keyA.id, binding.key1.id)
            } else {
                setOf(binding.keyQ.id, binding.keyA.id, binding.key1.id)
            }
        val rightKeyIds: Set<Int> =
            if (qwertyMode.value == QWERTYMode.Default && !isLandMode && !romajiModeState.value) {
                setOf(binding.keyP.id, binding.keyL.id, binding.key0.id)
            } else {
                setOf(binding.keyP.id, binding.keyL.id, binding.key0.id)
            }

        val drawableResIdForImageView = when (view.id) {
            in leftKeyIds -> if (isDynamicColorsEnable) com.kazumaproject.core.R.drawable.key_preview_bubble_left_material else com.kazumaproject.core.R.drawable.key_preview_bubble_left
            in rightKeyIds -> if (isDynamicColorsEnable) com.kazumaproject.core.R.drawable.key_preview_bubble_right_material else com.kazumaproject.core.R.drawable.key_preview_bubble_right
            else -> if (isDynamicColorsEnable) com.kazumaproject.core.R.drawable.key_preview_bubble_material else com.kazumaproject.core.R.drawable.key_preview_bubble
        }
        iv.setBackgroundResource(drawableResIdForImageView)
        when (themeMode) {
            "custom" -> {
                iv.setDrawableSolidColor(customSpecialKeyColor)
                tv.setTextColor(customSpecialKeyTextColor)
            }

            else -> {

            }
        }
        keyPreviewPopupStyle.backgroundColor?.let { backgroundColor ->
            iv.setDrawableSolidColor(backgroundColor)
        }
        keyPreviewPopupStyle.textColor?.let { textColor ->
            tv.setTextColor(textColor)
        }
        popupView.rootView.layoutParams.height = previewHeight

        when (view) {
            is QWERTYButton -> {
                if (capsLockState.value.capsLockOn || capsLockState.value.shiftOn) {
                    tv.text = view.text.toString().uppercase()
                } else {
                    tv.text = view.text
                }
            }

            is AppCompatButton -> tv.text = view.text
            is AppCompatImageButton -> tv.text = ""
            else -> tv.text = ""
        }

        val scale = keyPreviewPopupStyle.sizeScalePercent.coerceIn(50, 200) / 100f
        val popupWidth = (view.width * 2 * scale).toInt().coerceAtLeast(1)
        val popupHeight = ((view.height * 2 + 64) * scale).toInt().coerceAtLeast(1)

        val popup = PopupWindow(popupView, popupWidth, popupHeight, false).apply {
            isTouchable = false
            isFocusable = false
            elevation = 6f
        }

        val xOffset = -((popupWidth - view.width) / 2)
        val yOffset = -popupHeight
        popup.showAsDropDown(view, xOffset, yOffset)
        keyPreviewPopup = popup
    }

    private fun dismissKeyPreview() {
        keyPreviewPopup?.dismiss()
        keyPreviewPopup = null
    }

    private fun findButtonUnder(x: Int, y: Int): View? {
        var nearestView: View? = null
        var minDistSquared = Int.MAX_VALUE
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (!child.isVisible || !qwertyButtonMap.containsKey(child)) continue
            child.getHitRect(hitRect)
            if (hitRect.contains(x, y)) return child
        }
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (!child.isVisible || !qwertyButtonMap.containsKey(child)) continue
            val centerX = child.left + child.width / 2
            val centerY = child.top + child.height / 2
            val dx = x - centerX
            val dy = y - centerY
            val distSq = dx * dx + dy * dy
            if (distSq < minDistSquared) {
                minDistSquared = distSq
                nearestView = child
            }
        }
        return nearestView
    }

    private fun getVisibleQwertyLetterViews(): List<QWERTYButton> {
        if (qwertyMode.value != QWERTYMode.Default) return emptyList()
        return (qRowLetterViews + aRowLetterViews + zRowLetterViews)
            .filter { it.isVisible && it.text?.singleOrNull()?.lowercaseChar() in 'a'..'z' }
    }

    private fun isQwertyGlideLetterView(view: View?): Boolean {
        if (view !is QWERTYButton) return false
        return view in getVisibleQwertyLetterViews()
    }

    private fun View.toQwertyGlideKeyRect(): QwertyGlideKeyClassifier.KeyRect {
        getHitRect(glideHitRect)
        return QwertyGlideKeyClassifier.KeyRect(
            left = glideHitRect.left,
            top = glideHitRect.top,
            right = glideHitRect.right,
            bottom = glideHitRect.bottom
        )
    }

    private fun classifyQwertyGlideKeyHit(
        x: Float,
        y: Float
    ): QwertyGlideKeyClassifier.KeyHit {
        val letterViews = getVisibleQwertyLetterViews()
        val letterViewSet = letterViews.toSet()
        val letterRects = letterViews.map { it.toQwertyGlideKeyRect() }
        val nonLetterRects = qwertyButtonMap.keys
            .filter { it.isVisible && it !in letterViewSet }
            .map { it.toQwertyGlideKeyRect() }
        return QwertyGlideKeyClassifier.classify(
            letterRects = letterRects,
            nonLetterRects = nonLetterRects,
            x = x.toInt(),
            y = y.toInt()
        )
    }

    /**
     * 共有 [hitRect] を使い回すと、Glide 中の判定が他のタッチ処理と競合する恐れが
     * あるため、Glide 専用の Rect を別に確保する。
     */
    private val glideHitRect = Rect()

    /**
     * 指定座標が「Glide で実際に使う visible なアルファベットキー」の hitRect 内に
     * 入っているかを厳密に判定する。
     *
     * [findButtonUnder] のような nearest-neighbor フォールバックは行わない。
     * 数字キー / Space / Return などの上にある座標を、近接するアルファベットキーへ
     * 吸わせてしまうのを防ぐ目的で、矩形に *厳密に* 含まれているケースだけ true を
     * 返す。
     */
    private fun findExactQwertyGlideLetterViewUnder(x: Float, y: Float): QWERTYButton? {
        val xi = x.toInt()
        val yi = y.toInt()
        return getVisibleQwertyLetterViews().firstOrNull { key ->
            if (!key.isVisible) return@firstOrNull false
            key.getHitRect(glideHitRect)
            glideHitRect.contains(xi, yi)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (glideTrailPoints.size < 2) return
        glideTrailPath.reset()
        val first = glideTrailPoints.first()
        glideTrailPath.moveTo(first.first, first.second)
        for (i in 1 until glideTrailPoints.size) {
            val previous = glideTrailPoints[i - 1]
            val current = glideTrailPoints[i]
            val midX = (previous.first + current.first) / 2f
            val midY = (previous.second + current.second) / 2f
            glideTrailPath.quadTo(previous.first, previous.second, midX, midY)
        }
        val last = glideTrailPoints.last()
        glideTrailPath.lineTo(last.first, last.second)
        canvas.drawPath(glideTrailPath, glideTrailPaint)
    }

    private fun logVariationIfNeeded(key: QWERTYKey) {
        if (key == QWERTYKey.QWERTYKeySwitchMode) {
            when (qwertyMode.value) {
                QWERTYMode.Default -> {
                    clearShiftCaps()
                    _qwertyMode.update { QWERTYMode.Number }
                }

                QWERTYMode.Number, QWERTYMode.Symbol -> _qwertyMode.update { QWERTYMode.Default }
            }
            return
        }
        val info = getVariationInfo(key)
        info?.apply {
            val outChar =
                if (capsLockState.value.capsLockOn || capsLockState.value.shiftOn) cap else tap
            qwertyKeyListener?.onReleasedQWERTYKey(
                qwertyKey = key,
                tap = outChar,
                variations = variations
            )
        }
    }

    private fun getVariationInfo(key: QWERTYKey): VariationInfo? {
        val info: QWERTYKeyInfo = when (qwertyMode.value) {
            QWERTYMode.Default -> if (romajiModeState.value) {
                if (isNumberKeysShow) qwertyKeyMap.getKeyInfoDefaultJPWithNumberRow(key)
                else qwertyKeyMap.getKeyInfoDefaultJP(key)
            } else {
                if (isNumberKeysShow) qwertyKeyMap.getKeyInfoDefaultWithNumberRow(key)
                else qwertyKeyMap.getKeyInfoDefault(key)
            }

            QWERTYMode.Number -> {
                if (romajiModeState.value) qwertyKeyMap.getKeyInfoNumberJP(key)
                else qwertyKeyMap.getKeyInfoNumber(key)
            }

            QWERTYMode.Symbol -> {
                if (romajiModeState.value) qwertyKeyMap.getKeyInfoSymbolJP(key)
                else qwertyKeyMap.getKeyInfoSymbol(key)
            }
        }
        return if (info is QWERTYKeyInfo.QWERTYVariation) {
            VariationInfo(
                tap = info.tap,
                cap = info.capChar,
                variations = if (capsLockState.value.shiftOn || capsLockState.value.capsLockOn) info.capVariations else info.variations,
                capVariations = info.capVariations
            )
        } else {
            null
        }
    }

    private fun onShiftDoubleTapped() {
        if (qwertyMode.value == QWERTYMode.Default) {
            enableCapsLock()
        }
    }

    private fun scheduleLongPressForPointer(pointerId: Int, view: View) {
        cancelLongPressForPointer(pointerId)
        val activeTouchScope = touchScope ?: createViewScope().also { touchScope = it }
        val job = activeTouchScope.launch {
            delay(longPressTimeout)
            val currentView = pointerButtonMap[pointerId]
            if (currentView == view) {
                val qwertyKey = qwertyButtonMap[view] ?: QWERTYKey.QWERTYKeyNotSelect
                val info = getVariationInfo(qwertyKey)
                val hasVariations = info != null && !info.variations.isNullOrEmpty()
                val isSpecialLongPressKey = qwertyKey in longPressEnabledKeys
                if (hasVariations) {
                    clearPendingQwertyGlideCandidateForLongPress(pointerId)
                    qwertyKeyListener?.onLongPressQWERTYKey(qwertyKey)
                    info?.variations?.let { showVariationPopup(view, it) }
                    longPressedPointerId = pointerId
                    dismissKeyPreview()
                } else if (isSpecialLongPressKey) {
                    clearPendingQwertyGlideCandidateForLongPress(pointerId)
                    qwertyKeyListener?.onLongPressQWERTYKey(qwertyKey)
                    lockedPointerId = pointerId
                }
            }
        }
        longPressJobs.put(pointerId, job)
    }

    private fun showVariationPopup(anchorView: View, variations: List<Char>) {
        variationPopup?.dismiss()
        val context = this.context
        variationPopupView = VariationsPopupView(context).apply {
            applyPopupViewStyle(variationPopupStyle)
            setChars(variations)
        }
        when (themeMode) {
            "custom" -> {
                variationPopupView?.setNeumorphicColors(
                    bgColor = customSpecialKeyColor,
                    selectedColor = manipulateColor(customSpecialKeyColor, 1.2f),
                    textColor = customSpecialKeyTextColor
                )
            }

            else -> {

            }
        }
        val maxColumns = 3
        val scale = variationPopupStyle.sizeScalePercent.coerceIn(50, 200) / 100f
        val itemSize = (100 * scale).toInt().coerceAtLeast(1)
        val cols = if (variations.size < maxColumns) variations.size else maxColumns
        val rows = kotlin.math.ceil(variations.size.toFloat() / maxColumns).toInt()
        val popupWidth = itemSize * cols
        val popupHeight = ((150 * rows) * scale).toInt().coerceAtLeast(1)
        val popup = PopupWindow(variationPopupView, popupWidth, popupHeight, false).apply {
            isTouchable = false
        }
        val xOffset = if (cols == 1) {
            (-(popupWidth / 2) + (anchorView.width / 2))
        } else {
            (anchorView.width / 2) - (popupWidth / 2)
        }
        val yOffset = -anchorView.height - popupHeight
        popup.showAsDropDown(anchorView, xOffset, yOffset)
        this.variationPopup = popup
    }

    fun applyPopupViewStyleSet(styleSet: QwertyPopupViewStyleSet) {
        keyPreviewPopupStyle = PopupViewStyle(
            sizeScalePercent = styleSet.keyPreview.sizeScalePercent.coerceIn(50, 200),
            textSizeSp = styleSet.keyPreview.textSizeSp.coerceIn(8f, 48f),
            backgroundColor = styleSet.keyPreview.backgroundColor,
            textColor = styleSet.keyPreview.textColor
        )
        variationPopupStyle = PopupViewStyle(
            sizeScalePercent = styleSet.variation.sizeScalePercent.coerceIn(50, 200),
            textSizeSp = styleSet.variation.textSizeSp.coerceIn(8f, 48f),
            backgroundColor = styleSet.variation.backgroundColor,
            textColor = styleSet.variation.textColor
        )
        variationPopupView?.applyPopupViewStyle(variationPopupStyle)
    }

    private fun cancelLongPressForPointer(pointerId: Int) {
        longPressJobs[pointerId]?.let { job ->
            job.cancel()
            longPressJobs.remove(pointerId)
        }
    }

    private fun toggleShift() {
        _capsLockState.update { it.copy(shiftOn = !it.shiftOn, capsLockOn = it.capsLockOn) }
    }

    private fun disableShift() {
        _capsLockState.update { it.copy(shiftOn = false, capsLockOn = it.capsLockOn) }
    }

    private fun enableCapsLock() {
        _capsLockState.update { it.copy(capsLockOn = true, shiftOn = false) }
    }

    private fun clearShiftCaps() {
        _capsLockState.value = CapsLockState()
    }

    fun getRomajiMode(): Boolean {
        return romajiModeState.value
    }

    fun setRomajiMode(state: Boolean) {
        Log.d("QWERTY Keyboard Debug", "romaji: [$state]")
        if (state) cancelQwertyGlideCandidate(notify = glideStarted)
        _romajiModeState.update { state }
    }

    fun setCursorMode(enabled: Boolean) {
        isCursorMode = enabled
        if (enabled) {
            setKeysForCursorMoveMode()
        } else {
            applyContentForMode(qwertyMode.value)
            if (_romajiModeState.value) {
                binding.keySpace.text =
                    resources.getString(com.kazumaproject.core.R.string.space_japanese)
            } else {
                binding.keySpace.text =
                    resources.getString(com.kazumaproject.core.R.string.space_english)
            }
        }
    }

    private fun setKeysForCursorMoveMode() {
        binding.apply {
            listOf(
                keyQ, keyW, keyE, keyR, keyT, keyY, keyU, keyI, keyO, keyP,
                keyA, keyS, keyD, keyF, keyG, keyH, keyJ, keyK, keyL,
                keyZ, keyX, keyC, keyV, keyB, keyN, keyM, keyAtMark, keySpace
            ).forEach { it.text = "" }
        }
    }

    fun setSpecialKeyVisibility(
        showCursors: Boolean,
        showSwitchKey: Boolean,
        showKutouten: Boolean,
        showEmojiKey: Boolean = false
    ) {
        binding.cursorLeft.isVisible = showCursors
        binding.cursorRight.isVisible = showCursors
        binding.keySwitchDefault.isVisible = showSwitchKey
        binding.keyEmoji.isVisible = showEmojiKey
        binding.keyKuten.isVisible = showKutouten
        binding.keyTouten.isVisible = showKutouten
    }

    fun setRomajiEnglishSwitchKeyVisibility(showRomajiEnglishKey: Boolean) {
        binding.switchRomajiEnglish.isVisible = showRomajiEnglishKey
    }

    fun setRomajiEnglishSwitchKeyTextWithStyle(showRomajiEnglishKey: Boolean) {
        val text = "あa"
        val spannableString = SpannableString(text)
        if (showRomajiEnglishKey) {
            spannableString.setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                1,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
            spannableString.setSpan(
                StyleSpan(Typeface.NORMAL),
                1,
                2,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
        } else {
            spannableString.setSpan(
                StyleSpan(Typeface.NORMAL),
                0,
                1,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
            spannableString.setSpan(
                StyleSpan(Typeface.BOLD),
                1,
                2,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
            spannableString.setSpan(
                RelativeSizeSpan(1.7f),
                1,
                2,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
        }
        binding.switchRomajiEnglish.text = spannableString
    }

    /**
     * @param excludeNumber trueの場合 "あa"、falseの場合 "あa1" を表示
     */
    fun setNumberSwitchKeyTextStyle(excludeNumber: Boolean) {
        // booleanによってテキストを切り替え
        val text = if (excludeNumber) "あa" else "あa1"
        val spannableString = SpannableString(text)

        // 1文字目 "あ" (共通)
        spannableString.setSpan(
            StyleSpan(Typeface.NORMAL),
            0,
            1,
            Spannable.SPAN_INCLUSIVE_EXCLUSIVE
        )

        // 2文字目 "a" (共通)
        spannableString.setSpan(StyleSpan(Typeface.BOLD), 1, 2, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        spannableString.setSpan(RelativeSizeSpan(1.5f), 1, 2, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)

        // 3文字目 "1" (falseの場合のみ存在するので、範囲チェックを行う)
        if (!excludeNumber) {
            spannableString.setSpan(
                StyleSpan(Typeface.NORMAL),
                2,
                3,
                Spannable.SPAN_INCLUSIVE_EXCLUSIVE
            )
        }

        binding.switchNumberLayout.text = spannableString
    }

    fun updateNumberKeyState(state: Boolean) {
        this.isNumberKeysShow = state
        displayOrHideNumberKeys(state)
        // Ensure topRightChars update correctly when number row toggles
        updateTopRightCharsForDefaultMode()
        updateNumberKeyFlickGuides()
    }

    private fun updateNumberKeyFlickGuides() {
        qwertyButtonMap.forEach { (view, key) ->
            if (view !is QWERTYButton || !QwertyNumberKeyFlickConfig.isQwertyNumberKey(key)) {
                return@forEach
            }
            view.topRightChar = if (isNumberKeysShow && enableFlickUpDetection) {
                QwertyNumberKeyFlickConfig.charForKeyWhenEnabled(
                    key = key,
                    chars = numberKeyFlickUpChars,
                    isNumberKeysShown = isNumberKeysShow,
                    isFlickEnabled = enableFlickUpDetection
                )
            } else {
                null
            }
            view.bottomRightChar = if (isNumberKeysShow && enableFlickDownDetection) {
                QwertyNumberKeyFlickConfig.charForKeyWhenEnabled(
                    key = key,
                    chars = numberKeyFlickDownChars,
                    isNumberKeysShown = isNumberKeysShow,
                    isFlickEnabled = enableFlickDownDetection
                )
            } else {
                null
            }
        }
    }

    fun updateSwitchRomajiEnglishState(state: Boolean) {
        binding.switchRomajiEnglish.isVisible = state
    }

    fun updateSymbolKeymapState(state: Boolean) {
        this.isSymbolKeymapShow = state
        updateTopRightCharsForDefaultMode()
    }

    private fun displayOrHideNumberKeys(state: Boolean) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(this)
        val qRowKeys = listOf(
            binding.keyQ, binding.keyW, binding.keyE, binding.keyR, binding.keyT,
            binding.keyY, binding.keyU, binding.keyI, binding.keyO, binding.keyP
        )
        val numberKeys = listOf(
            binding.key1, binding.key2, binding.key3, binding.key4, binding.key5,
            binding.key6, binding.key7, binding.key8, binding.key9, binding.key0
        )

        if (state && isNumberKeysShow) {
            numberKeys.forEach { it.isVisible = true }
            constraintSet.setGuidelinePercent(R.id.guideline_number_row, 0.20f)
            constraintSet.setGuidelinePercent(R.id.guideline_q_row, 0.40f)
            constraintSet.setGuidelinePercent(R.id.guideline_a_row, 0.60f)
            constraintSet.setGuidelinePercent(R.id.guideline_z_row, 0.80f)
            qRowKeys.forEach { key ->
                constraintSet.connect(
                    key.id,
                    ConstraintLayout.LayoutParams.TOP,
                    R.id.guideline_number_row,
                    ConstraintLayout.LayoutParams.BOTTOM
                )
            }
        } else {
            numberKeys.forEach { it.isVisible = false }
            constraintSet.setGuidelinePercent(R.id.guideline_number_row, 0.0f)
            constraintSet.setGuidelinePercent(R.id.guideline_q_row, 0.25f)
            constraintSet.setGuidelinePercent(R.id.guideline_a_row, 0.50f)
            constraintSet.setGuidelinePercent(R.id.guideline_z_row, 0.75f)
            qRowKeys.forEach { key ->
                constraintSet.connect(
                    key.id,
                    ConstraintLayout.LayoutParams.TOP,
                    ConstraintLayout.LayoutParams.PARENT_ID,
                    ConstraintLayout.LayoutParams.TOP
                )
            }
        }
        constraintSet.applyTo(this)
    }

    fun setSwitchNumberLayoutKeyVisibility(state: Boolean) {
        binding.switchNumberLayout.isVisible = state
    }

    fun setDefaultView() {
        _qwertyMode.update { QWERTYMode.Default }
    }
}

private fun Double.toFloatOrDefault(defaultValue: Float = 0f): Float {
    return if (isNaN()) defaultValue else toFloat()
}
