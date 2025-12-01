package com.kazumaproject.qwerty_keyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.util.Log
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.util.isNotEmpty
import androidx.core.util.size
import androidx.core.view.isVisible
import com.google.android.material.color.DynamicColors
import com.kazumaproject.core.data.qwerty.CapsLockState
import com.kazumaproject.core.data.qwerty.QWERTYKeys
import com.kazumaproject.core.data.qwerty.VariationInfo
import com.kazumaproject.core.domain.extensions.dpToPx
import com.kazumaproject.core.domain.extensions.isDarkThemeOn
import com.kazumaproject.core.domain.extensions.setMarginEnd
import com.kazumaproject.core.domain.extensions.setMarginStart
import com.kazumaproject.core.domain.extensions.toZenkaku
import com.kazumaproject.core.domain.listener.QWERTYKeyListener
import com.kazumaproject.core.domain.qwerty.QWERTYKey
import com.kazumaproject.core.domain.qwerty.QWERTYKeyInfo
import com.kazumaproject.core.domain.qwerty.QWERTYKeyMap
import com.kazumaproject.core.domain.state.QWERTYMode
import com.kazumaproject.qwerty_keyboard.R
import com.kazumaproject.qwerty_keyboard.databinding.QwertyLayoutBinding
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

/**
 * A custom keyboard view that:
 *  - Detects touches on multiple key types (QWERTYButton, AppCompatButton, AppCompatImageButton).
 *  - Shows a PopupWindow key‐preview above the pressed key.
 *  - Notifies a QWERTYKeyListener of key‐tap and key‐long‐press events.
 *  - Recognizes a double‐tap on the Shift key and suppresses that single‐tap.
 *  - Uses Kotlin Coroutines for long‐press detection.
 */
class QWERTYKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: QwertyLayoutBinding

    /** Map each active pointer ID → the View (key) it’s currently “pressing” (or null). */
    private val pointerButtonMap = SparseArray<View?>()

    /** For each pointer, store a coroutine Job to detect long‐press. */
    private val longPressJobs = SparseArray<Job>()

    /** CoroutineScope on main dispatcher. */
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** If a second finger cancels the first, we suppress that first pointer until it lifts. */
    private var suppressedPointerId: Int? = null

    private var keyPreviewPopup: PopupWindow? = null
    private val hitRect = Rect()

    private var qwertyKeyListener: QWERTYKeyListener? = null
    private var qwertyKeyMap: QWERTYKeyMap

    // ① Track the last time Shift was tapped (to detect double‐tap)
    private var lastShiftTapTime = 0L
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

    // ② After detecting a double‐tap, suppress the next single‐tap for Shift.
    private var shiftDoubleTapped = false

    // Long‐press timeout (system default)
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

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

    // ★ ポインターをロックするための変数を追加
    private var lockedPointerId: Int? = null

    private var isCursorMode: Boolean = false

    // カーソルモード中のタッチの初期位置を記録する変数
    private var cursorInitialX = 0f
    private var cursorInitialY = 0f

    private var isNumberKeysShow: Boolean = false
    private var isSymbolKeymapShow: Boolean = false

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
     * true の場合、QWERTYButton での上フリックが検知され、
     * その間のキースライドが無効になります。
     */
    private var enableFlickUpDetection = false

    /**
     * 下フリック検知を有効にするかどうかのフラグ
     * true の場合、QWERTYButton での下フリックが検知され、
     * その間のキースライドが無効になります。
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
     * (タッチスロップの1.5倍を基準に設定)
     */
    private val flickThreshold by lazy { ViewConfiguration.get(context).scaledTouchSlop.toFloat() * 1.5f }

    /**
     * Delte キーの左フリックを有効にするフラグ
     */
    private var enableDeleteLeftFlick = false

    /**
     * Delete キーの左フリック検知時のリスナー
     */
    private var onDeleteLeftFlickListener: (() -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        isDynamicColorsEnable = DynamicColors.isDynamicColorAvailable()

        val inflater = LayoutInflater.from(context)
        binding = QwertyLayoutBinding.inflate(inflater, this)

        qwertyKeyMap = QWERTYKeyMap()

        isTablet = resources.getBoolean(com.kazumaproject.core.R.bool.isTablet)
        val isDarkMode = this.context.isDarkThemeOn()
        setMaterialYouTheme(isDarkMode, isDynamicColorsEnable)

        scope.launch {
            launch {
                capsLockState.collectLatest { state ->
                    when {
                        state.shiftOn && state.capsLockOn -> {
                            qwertyButtonMap.keys.forEach { button ->
                                if (button is AppCompatButton) {
                                    button.isAllCaps = true
                                }
                                if (button is AppCompatImageButton) {
                                    if (button.id == binding.keyShift.id) {
                                        button.setImageResource(
                                            com.kazumaproject.core.R.drawable.caps_lock
                                        )
                                    }
                                }
                            }
                        }

                        !state.shiftOn && state.capsLockOn -> {
                            qwertyButtonMap.keys.forEach { button ->
                                if (button is AppCompatButton) {
                                    button.isAllCaps = true
                                }
                                if (button is AppCompatImageButton) {
                                    if (button.id == binding.keyShift.id) {
                                        button.setImageResource(
                                            com.kazumaproject.core.R.drawable.caps_lock
                                        )
                                    }
                                }
                            }
                        }

                        state.shiftOn && !state.capsLockOn -> {
                            qwertyButtonMap.keys.forEach { button ->
                                if (button is AppCompatButton) {
                                    button.isAllCaps = true
                                }
                                if (button is AppCompatImageButton) {
                                    if (button.id == binding.keyShift.id) {
                                        button.setImageResource(
                                            com.kazumaproject.core.R.drawable.shift_fill_24px
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            qwertyButtonMap.keys.forEach { button ->
                                if (button is AppCompatButton) {
                                    button.isAllCaps = false
                                }
                                if (button is AppCompatImageButton) {
                                    if (button.id == binding.keyShift.id) {
                                        button.setImageResource(
                                            com.kazumaproject.core.R.drawable.shift_24px
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            launch {
                qwertyMode.collectLatest { state ->
                    Log.d("qwertyMode", "$state")
                    when (state) {
                        QWERTYMode.Default -> {
                            binding.apply {
                                if (!romajiModeState.value) {
                                    keyAtMark.isVisible = false
                                    keyA.setMarginStart(
                                        23f
                                    )
                                    keyL.setMarginEnd(
                                        23f
                                    )
                                    defaultQWERTYButtonsRoman.forEach {
                                        it.topRightChar = null
                                    }
                                    if (isSymbolKeymapShow) {
                                        if (isNumberKeysShow) {
                                            defaultQWERTYButtons.forEach {
                                                when (it.id) {
                                                    R.id.key_a -> {
                                                        it.topRightChar = '@'
                                                    }

                                                    R.id.key_b -> {
                                                        it.topRightChar = ';'
                                                    }

                                                    R.id.key_c -> {
                                                        it.topRightChar = '\''
                                                    }

                                                    R.id.key_d -> {
                                                        it.topRightChar = '$'
                                                    }

                                                    R.id.key_e -> {
                                                        it.topRightChar = '|'
                                                    }

                                                    R.id.key_f -> {
                                                        it.topRightChar = '_'
                                                    }

                                                    R.id.key_g -> {
                                                        it.topRightChar = '&'
                                                    }

                                                    R.id.key_h -> {
                                                        it.topRightChar = '-'
                                                    }

                                                    R.id.key_i -> {
                                                        it.topRightChar = '>'
                                                    }

                                                    R.id.key_j -> {
                                                        it.topRightChar = '+'
                                                    }

                                                    R.id.key_k -> {
                                                        it.topRightChar = '('
                                                    }

                                                    R.id.key_l -> {
                                                        it.topRightChar = ')'
                                                    }

                                                    R.id.key_m -> {
                                                        it.topRightChar = '?'
                                                    }

                                                    R.id.key_n -> {
                                                        it.topRightChar = '!'
                                                    }

                                                    R.id.key_o -> {
                                                        it.topRightChar = '{'
                                                    }

                                                    R.id.key_p -> {
                                                        it.topRightChar = '}'
                                                    }

                                                    R.id.key_q -> {
                                                        it.topRightChar = '%'
                                                    }

                                                    R.id.key_r -> {
                                                        it.topRightChar = '='
                                                    }

                                                    R.id.key_s -> {
                                                        it.topRightChar = '#'
                                                    }

                                                    R.id.key_t -> {
                                                        it.topRightChar = '['
                                                    }

                                                    R.id.key_u -> {
                                                        it.topRightChar = '<'
                                                    }

                                                    R.id.key_v -> {
                                                        it.topRightChar = ':'
                                                    }

                                                    R.id.key_w -> {
                                                        it.topRightChar = '\\'
                                                    }

                                                    R.id.key_x -> {
                                                        it.topRightChar = '"'
                                                    }

                                                    R.id.key_y -> {
                                                        it.topRightChar = ']'
                                                    }

                                                    R.id.key_z -> {
                                                        it.topRightChar = '*'
                                                    }
                                                }
                                            }
                                        } else {
                                            defaultQWERTYButtonsRoman.forEach {
                                                when (it.id) {
                                                    R.id.key_a -> {
                                                        it.topRightChar = '@'
                                                    }

                                                    R.id.key_b -> {
                                                        it.topRightChar = ';'
                                                    }

                                                    R.id.key_c -> {
                                                        it.topRightChar = '\''
                                                    }

                                                    R.id.key_d -> {
                                                        it.topRightChar = '$'
                                                    }

                                                    R.id.key_e -> {
                                                        it.topRightChar = '3'
                                                    }

                                                    R.id.key_f -> {
                                                        it.topRightChar = '_'
                                                    }

                                                    R.id.key_g -> {
                                                        it.topRightChar = '&'
                                                    }

                                                    R.id.key_h -> {
                                                        it.topRightChar = '-'
                                                    }

                                                    R.id.key_i -> {
                                                        it.topRightChar = '8'
                                                    }

                                                    R.id.key_j -> {
                                                        it.topRightChar = '+'
                                                    }

                                                    R.id.key_k -> {
                                                        it.topRightChar = '('
                                                    }

                                                    R.id.key_at_mark -> {
                                                        it.topRightChar = null
                                                    }

                                                    R.id.key_l -> {
                                                        it.topRightChar = ')'
                                                    }

                                                    R.id.key_m -> {
                                                        it.topRightChar = '?'
                                                    }

                                                    R.id.key_n -> {
                                                        it.topRightChar = '!'
                                                    }

                                                    R.id.key_o -> {
                                                        it.topRightChar = '9'
                                                    }

                                                    R.id.key_p -> {
                                                        it.topRightChar = '0'
                                                    }

                                                    R.id.key_q -> {
                                                        it.topRightChar = '1'
                                                    }

                                                    R.id.key_r -> {
                                                        it.topRightChar = '4'
                                                    }

                                                    R.id.key_s -> {
                                                        it.topRightChar = '#'
                                                    }

                                                    R.id.key_t -> {
                                                        it.topRightChar = '5'
                                                    }

                                                    R.id.key_u -> {
                                                        it.topRightChar = '7'
                                                    }

                                                    R.id.key_v -> {
                                                        it.topRightChar = ':'
                                                    }

                                                    R.id.key_w -> {
                                                        it.topRightChar = '2'
                                                    }

                                                    R.id.key_x -> {
                                                        it.topRightChar = '"'
                                                    }

                                                    R.id.key_y -> {
                                                        it.topRightChar = '6'
                                                    }

                                                    R.id.key_z -> {
                                                        it.topRightChar = '*'
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    if (isSymbolKeymapShow) {
                                        if (isNumberKeysShow) {
                                            defaultQWERTYButtonsRoman.forEach {
                                                when (it.id) {
                                                    R.id.key_a -> {
                                                        it.topRightChar = '@'
                                                    }

                                                    R.id.key_b -> {
                                                        it.topRightChar = ';'
                                                    }

                                                    R.id.key_c -> {
                                                        it.topRightChar = '\''
                                                    }

                                                    R.id.key_d -> {
                                                        it.topRightChar = '￥'
                                                    }

                                                    R.id.key_e -> {
                                                        it.topRightChar = '|'
                                                    }

                                                    R.id.key_f -> {
                                                        it.topRightChar = '_'
                                                    }

                                                    R.id.key_g -> {
                                                        it.topRightChar = '&'
                                                    }

                                                    R.id.key_h -> {
                                                        it.topRightChar = '-'
                                                    }

                                                    R.id.key_i -> {
                                                        it.topRightChar = '>'
                                                    }

                                                    R.id.key_j -> {
                                                        it.topRightChar = '+'
                                                    }

                                                    R.id.key_k -> {
                                                        it.topRightChar = '('
                                                    }

                                                    R.id.key_at_mark -> {
                                                        it.topRightChar = ')'
                                                    }

                                                    R.id.key_l -> {
                                                        it.topRightChar = '/'
                                                    }

                                                    R.id.key_m -> {
                                                        it.topRightChar = '?'
                                                    }

                                                    R.id.key_n -> {
                                                        it.topRightChar = '!'
                                                    }

                                                    R.id.key_o -> {
                                                        it.topRightChar = '{'
                                                    }

                                                    R.id.key_p -> {
                                                        it.topRightChar = '}'
                                                    }

                                                    R.id.key_q -> {
                                                        it.topRightChar = '%'
                                                    }

                                                    R.id.key_r -> {
                                                        it.topRightChar = '='
                                                    }

                                                    R.id.key_s -> {
                                                        it.topRightChar = '#'
                                                    }

                                                    R.id.key_t -> {
                                                        it.topRightChar = '['
                                                    }

                                                    R.id.key_u -> {
                                                        it.topRightChar = '<'
                                                    }

                                                    R.id.key_v -> {
                                                        it.topRightChar = ':'
                                                    }

                                                    R.id.key_w -> {
                                                        it.topRightChar = '\\'
                                                    }

                                                    R.id.key_x -> {
                                                        it.topRightChar = '"'
                                                    }

                                                    R.id.key_y -> {
                                                        it.topRightChar = ']'
                                                    }

                                                    R.id.key_z -> {
                                                        it.topRightChar = '*'
                                                    }
                                                }
                                            }
                                        } else {
                                            defaultQWERTYButtonsRoman.forEach {
                                                when (it.id) {
                                                    R.id.key_a -> {
                                                        it.topRightChar = '@'
                                                    }

                                                    R.id.key_b -> {
                                                        it.topRightChar = ';'
                                                    }

                                                    R.id.key_c -> {
                                                        it.topRightChar = '\''
                                                    }

                                                    R.id.key_d -> {
                                                        it.topRightChar = '￥'
                                                    }

                                                    R.id.key_e -> {
                                                        it.topRightChar = '3'
                                                    }

                                                    R.id.key_f -> {
                                                        it.topRightChar = '_'
                                                    }

                                                    R.id.key_g -> {
                                                        it.topRightChar = '&'
                                                    }

                                                    R.id.key_h -> {
                                                        it.topRightChar = '-'
                                                    }

                                                    R.id.key_i -> {
                                                        it.topRightChar = '8'
                                                    }

                                                    R.id.key_j -> {
                                                        it.topRightChar = '+'
                                                    }

                                                    R.id.key_k -> {
                                                        it.topRightChar = '('
                                                    }

                                                    R.id.key_at_mark -> {
                                                        it.topRightChar = ')'
                                                    }

                                                    R.id.key_l -> {
                                                        it.topRightChar = '/'
                                                    }

                                                    R.id.key_m -> {
                                                        it.topRightChar = '?'
                                                    }

                                                    R.id.key_n -> {
                                                        it.topRightChar = '!'
                                                    }

                                                    R.id.key_o -> {
                                                        it.topRightChar = '9'
                                                    }

                                                    R.id.key_p -> {
                                                        it.topRightChar = '0'
                                                    }

                                                    R.id.key_q -> {
                                                        it.topRightChar = '1'
                                                    }

                                                    R.id.key_r -> {
                                                        it.topRightChar = '4'
                                                    }

                                                    R.id.key_s -> {
                                                        it.topRightChar = '#'
                                                    }

                                                    R.id.key_t -> {
                                                        it.topRightChar = '5'
                                                    }

                                                    R.id.key_u -> {
                                                        it.topRightChar = '7'
                                                    }

                                                    R.id.key_v -> {
                                                        it.topRightChar = ':'
                                                    }

                                                    R.id.key_w -> {
                                                        it.topRightChar = '2'
                                                    }

                                                    R.id.key_x -> {
                                                        it.topRightChar = '"'
                                                    }

                                                    R.id.key_y -> {
                                                        it.topRightChar = '6'
                                                    }

                                                    R.id.key_z -> {
                                                        it.topRightChar = '*'
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                keyV.isVisible = true
                                keyB.isVisible = true
                                keyShift.setMarginStart(4f)
                                keyDelete.setMarginEnd(4f)
                                keyShift.setImageResource(
                                    com.kazumaproject.core.R.drawable.shift_24px
                                )
                                key123.text =
                                    resources.getString(com.kazumaproject.core.R.string.string_123)
                            }
                            attachDefaultKeyLabels()
                            displayOrHideNumberKeys(true)
                        }

                        QWERTYMode.Number -> {
                            binding.apply {
                                keyAtMark.isVisible = true
                                keyV.isVisible = false
                                keyB.isVisible = false
                                keyA.setMarginStart(
                                    9f
                                )
                                keyL.setMarginEnd(
                                    9f
                                )
                                keyShift.setMarginStart(4f)
                                keyDelete.setMarginEnd(4f)
                                keyShift.setImageResource(
                                    com.kazumaproject.core.R.drawable.qwerty_symbol
                                )
                                key123.text = if (romajiModeState.value) {
                                    resources.getString(com.kazumaproject.core.R.string.string_abc_japanese)
                                } else {
                                    resources.getString(com.kazumaproject.core.R.string.string_abc)
                                }
                            }
                            attachNumberKeyLabels(false)
                            displayOrHideNumberKeys(false)
                            defaultQWERTYButtonsRoman.forEach {
                                it.topRightChar = null
                            }
                        }

                        QWERTYMode.Symbol -> {
                            binding.apply {
                                keyAtMark.isVisible = true
                                keyV.isVisible = false
                                keyB.isVisible = false
                                keyA.setMarginStart(
                                    9f
                                )
                                keyL.setMarginEnd(
                                    9f
                                )
                                keyShift.setMarginStart(4f)
                                keyDelete.setMarginEnd(4f)
                                keyShift.setImageResource(
                                    com.kazumaproject.core.R.drawable.qwerty_number
                                )
                                key123.text = if (romajiModeState.value) {
                                    resources.getString(com.kazumaproject.core.R.string.string_abc_japanese)
                                } else {
                                    resources.getString(com.kazumaproject.core.R.string.string_abc)
                                }
                            }
                            attachNumberKeyLabels(true)
                            displayOrHideNumberKeys(false)
                            defaultQWERTYButtonsRoman.forEach {
                                it.topRightChar = null
                            }
                        }
                    }
                }
            }
            launch {
                romajiModeState.collectLatest { romajiMode ->
                    if (romajiMode) {
                        binding.apply {
                            keySpace.text =
                                resources.getString(com.kazumaproject.core.R.string.space_japanese)
                            keyA.setMarginStart(9f)
                            keyL.setMarginEnd(9f)
                            keyShift.setMarginStart(4f)
                            keyDelete.setMarginEnd(4f)
                            keyAtMark.apply {
                                isVisible = true
                                text = "l"
                            }
                            when (qwertyMode.value) {
                                QWERTYMode.Number -> {
                                    keyF.text = "@"
                                    keyJ.text = "「"
                                    keyK.text = "」"
                                    keyAtMark.text = "￥"
                                    keyL.text = "&"
                                    keyZ.text = "。"
                                    keyX.text = "、"
                                }

                                QWERTYMode.Symbol -> {
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
                                }

                                else -> {
                                    keyL.text = "ー"
                                }
                            }
                            key123.text =
                                resources.getString(com.kazumaproject.core.R.string.string_123)
                            keyKuten.text = "。"
                            keyTouten.text = "、"
                        }
                        if (isSymbolKeymapShow && qwertyMode.value == QWERTYMode.Default) {
                            if (isNumberKeysShow) {
                                defaultQWERTYButtonsRoman.forEach {
                                    when (it.id) {
                                        R.id.key_a -> {
                                            it.topRightChar = '@'
                                        }

                                        R.id.key_b -> {
                                            it.topRightChar = ';'
                                        }

                                        R.id.key_c -> {
                                            it.topRightChar = '\''
                                        }

                                        R.id.key_d -> {
                                            it.topRightChar = '￥'
                                        }

                                        R.id.key_e -> {
                                            it.topRightChar = '|'
                                        }

                                        R.id.key_f -> {
                                            it.topRightChar = '_'
                                        }

                                        R.id.key_g -> {
                                            it.topRightChar = '&'
                                        }

                                        R.id.key_h -> {
                                            it.topRightChar = '-'
                                        }

                                        R.id.key_i -> {
                                            it.topRightChar = '>'
                                        }

                                        R.id.key_j -> {
                                            it.topRightChar = '+'
                                        }

                                        R.id.key_k -> {
                                            it.topRightChar = '('
                                        }

                                        R.id.key_at_mark -> {
                                            it.topRightChar = ')'
                                        }

                                        R.id.key_l -> {
                                            it.topRightChar = '/'
                                        }

                                        R.id.key_m -> {
                                            it.topRightChar = '?'
                                        }

                                        R.id.key_n -> {
                                            it.topRightChar = '!'
                                        }

                                        R.id.key_o -> {
                                            it.topRightChar = '{'
                                        }

                                        R.id.key_p -> {
                                            it.topRightChar = '}'
                                        }

                                        R.id.key_q -> {
                                            it.topRightChar = '%'
                                        }

                                        R.id.key_r -> {
                                            it.topRightChar = '='
                                        }

                                        R.id.key_s -> {
                                            it.topRightChar = '#'
                                        }

                                        R.id.key_t -> {
                                            it.topRightChar = '['
                                        }

                                        R.id.key_u -> {
                                            it.topRightChar = '<'
                                        }

                                        R.id.key_v -> {
                                            it.topRightChar = ':'
                                        }

                                        R.id.key_w -> {
                                            it.topRightChar = '\\'
                                        }

                                        R.id.key_x -> {
                                            it.topRightChar = '"'
                                        }

                                        R.id.key_y -> {
                                            it.topRightChar = ']'
                                        }

                                        R.id.key_z -> {
                                            it.topRightChar = '*'
                                        }
                                    }
                                }
                            } else {
                                defaultQWERTYButtonsRoman.forEach {
                                    when (it.id) {
                                        R.id.key_a -> {
                                            it.topRightChar = '@'
                                        }

                                        R.id.key_b -> {
                                            it.topRightChar = ';'
                                        }

                                        R.id.key_c -> {
                                            it.topRightChar = '\''
                                        }

                                        R.id.key_d -> {
                                            it.topRightChar = '￥'
                                        }

                                        R.id.key_e -> {
                                            it.topRightChar = '3'
                                        }

                                        R.id.key_f -> {
                                            it.topRightChar = '_'
                                        }

                                        R.id.key_g -> {
                                            it.topRightChar = '&'
                                        }

                                        R.id.key_h -> {
                                            it.topRightChar = '-'
                                        }

                                        R.id.key_i -> {
                                            it.topRightChar = '8'
                                        }

                                        R.id.key_j -> {
                                            it.topRightChar = '+'
                                        }

                                        R.id.key_k -> {
                                            it.topRightChar = '('
                                        }

                                        R.id.key_at_mark -> {
                                            it.topRightChar = ')'
                                        }

                                        R.id.key_l -> {
                                            it.topRightChar = '/'
                                        }

                                        R.id.key_m -> {
                                            it.topRightChar = '?'
                                        }

                                        R.id.key_n -> {
                                            it.topRightChar = '!'
                                        }

                                        R.id.key_o -> {
                                            it.topRightChar = '9'
                                        }

                                        R.id.key_p -> {
                                            it.topRightChar = '0'
                                        }

                                        R.id.key_q -> {
                                            it.topRightChar = '1'
                                        }

                                        R.id.key_r -> {
                                            it.topRightChar = '4'
                                        }

                                        R.id.key_s -> {
                                            it.topRightChar = '#'
                                        }

                                        R.id.key_t -> {
                                            it.topRightChar = '5'
                                        }

                                        R.id.key_u -> {
                                            it.topRightChar = '7'
                                        }

                                        R.id.key_v -> {
                                            it.topRightChar = ':'
                                        }

                                        R.id.key_w -> {
                                            it.topRightChar = '2'
                                        }

                                        R.id.key_x -> {
                                            it.topRightChar = '"'
                                        }

                                        R.id.key_y -> {
                                            it.topRightChar = '6'
                                        }

                                        R.id.key_z -> {
                                            it.topRightChar = '*'
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        binding.apply {
                            keyAtMark.apply {
                                isVisible = false
                                text = "@"
                            }
                            keySpace.text =
                                resources.getString(com.kazumaproject.core.R.string.space_english)
                            keyA.setMarginStart(23f)
                            keyL.apply {
                                setMarginEnd(23f)
                                text = "l"
                            }
                            keyShift.setMarginStart(4f)
                            keyDelete.setMarginEnd(4f)
                            when (qwertyMode.value) {
                                QWERTYMode.Default -> key123.text =
                                    resources.getString(com.kazumaproject.core.R.string.string_123)

                                QWERTYMode.Number -> {
                                    key123.text =
                                        resources.getString(com.kazumaproject.core.R.string.string_abc_japanese)
                                    keyF.text = ";"
                                    keyJ.text = "￥"
                                    keyK.text = "&"
                                    keyL.text = "\""
                                    keyZ.text = "."
                                    keyX.text = ","
                                }

                                QWERTYMode.Symbol -> {
                                    key123.text =
                                        resources.getString(com.kazumaproject.core.R.string.string_abc_japanese)
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
                            keyKuten.text = "."
                            keyTouten.text = ","
                        }
                        defaultQWERTYButtonsRoman.forEach {
                            it.topRightChar = null
                        }
                        if (isSymbolKeymapShow && qwertyMode.value == QWERTYMode.Default) {
                            if (isNumberKeysShow) {
                                defaultQWERTYButtons.forEach {
                                    when (it.id) {
                                        R.id.key_a -> {
                                            it.topRightChar = '@'
                                        }

                                        R.id.key_b -> {
                                            it.topRightChar = ';'
                                        }

                                        R.id.key_c -> {
                                            it.topRightChar = '\''
                                        }

                                        R.id.key_d -> {
                                            it.topRightChar = '$'
                                        }

                                        R.id.key_e -> {
                                            it.topRightChar = '|'
                                        }

                                        R.id.key_f -> {
                                            it.topRightChar = '_'
                                        }

                                        R.id.key_g -> {
                                            it.topRightChar = '&'
                                        }

                                        R.id.key_h -> {
                                            it.topRightChar = '-'
                                        }

                                        R.id.key_i -> {
                                            it.topRightChar = '>'
                                        }

                                        R.id.key_j -> {
                                            it.topRightChar = '+'
                                        }

                                        R.id.key_k -> {
                                            it.topRightChar = '('
                                        }

                                        R.id.key_l -> {
                                            it.topRightChar = ')'
                                        }

                                        R.id.key_m -> {
                                            it.topRightChar = '?'
                                        }

                                        R.id.key_n -> {
                                            it.topRightChar = '!'
                                        }

                                        R.id.key_o -> {
                                            it.topRightChar = '{'
                                        }

                                        R.id.key_p -> {
                                            it.topRightChar = '}'
                                        }

                                        R.id.key_q -> {
                                            it.topRightChar = '%'
                                        }

                                        R.id.key_r -> {
                                            it.topRightChar = '='
                                        }

                                        R.id.key_s -> {
                                            it.topRightChar = '#'
                                        }

                                        R.id.key_t -> {
                                            it.topRightChar = '['
                                        }

                                        R.id.key_u -> {
                                            it.topRightChar = '<'
                                        }

                                        R.id.key_v -> {
                                            it.topRightChar = ':'
                                        }

                                        R.id.key_w -> {
                                            it.topRightChar = '\\'
                                        }

                                        R.id.key_x -> {
                                            it.topRightChar = '"'
                                        }

                                        R.id.key_y -> {
                                            it.topRightChar = ']'
                                        }

                                        R.id.key_z -> {
                                            it.topRightChar = '*'
                                        }
                                    }
                                }
                            } else {
                                defaultQWERTYButtonsRoman.forEach {
                                    when (it.id) {
                                        R.id.key_a -> {
                                            it.topRightChar = '@'
                                        }

                                        R.id.key_b -> {
                                            it.topRightChar = ';'
                                        }

                                        R.id.key_c -> {
                                            it.topRightChar = '\''
                                        }

                                        R.id.key_d -> {
                                            it.topRightChar = '$'
                                        }

                                        R.id.key_e -> {
                                            it.topRightChar = '3'
                                        }

                                        R.id.key_f -> {
                                            it.topRightChar = '_'
                                        }

                                        R.id.key_g -> {
                                            it.topRightChar = '&'
                                        }

                                        R.id.key_h -> {
                                            it.topRightChar = '-'
                                        }

                                        R.id.key_i -> {
                                            it.topRightChar = '8'
                                        }

                                        R.id.key_j -> {
                                            it.topRightChar = '+'
                                        }

                                        R.id.key_k -> {
                                            it.topRightChar = '('
                                        }

                                        R.id.key_at_mark -> {
                                            it.topRightChar = null
                                        }

                                        R.id.key_l -> {
                                            it.topRightChar = ')'
                                        }

                                        R.id.key_m -> {
                                            it.topRightChar = '?'
                                        }

                                        R.id.key_n -> {
                                            it.topRightChar = '!'
                                        }

                                        R.id.key_o -> {
                                            it.topRightChar = '9'
                                        }

                                        R.id.key_p -> {
                                            it.topRightChar = '0'
                                        }

                                        R.id.key_q -> {
                                            it.topRightChar = '1'
                                        }

                                        R.id.key_r -> {
                                            it.topRightChar = '4'
                                        }

                                        R.id.key_s -> {
                                            it.topRightChar = '#'
                                        }

                                        R.id.key_t -> {
                                            it.topRightChar = '5'
                                        }

                                        R.id.key_u -> {
                                            it.topRightChar = '7'
                                        }

                                        R.id.key_v -> {
                                            it.topRightChar = ':'
                                        }

                                        R.id.key_w -> {
                                            it.topRightChar = '2'
                                        }

                                        R.id.key_x -> {
                                            it.topRightChar = '"'
                                        }

                                        R.id.key_y -> {
                                            it.topRightChar = '6'
                                        }

                                        R.id.key_z -> {
                                            it.topRightChar = '*'
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun setPopUpViewState(state: Boolean) {
        this.showPopupView = state
    }

    /**
     * 上フリックによる個別のアクションを有効にするか設定します。
     * @param enabled trueにすると、QWERTYButtonでの上フリックが検知され、
     * その際のキー間スライドが無効になります。
     */
    fun setFlickUpDetectionEnabled(enabled: Boolean) {
        this.enableFlickUpDetection = enabled
    }

    /**
     * 下フリックによる個別のアクションを有効にするか設定します。
     * @param enabled trueにすると、QWERTYButtonでの下フリックが検知され、
     * その際のキー間スライドが無効になります。
     */
    fun setFlickDownDetectionEnabled(enabled: Boolean) {
        this.enableFlickDownDetection = enabled
    }

    /**
     * Delete キーの左フリック検知を有効にするか設定します。
     */
    fun setDeleteLeftFlickEnabled(enabled: Boolean) {
        this.enableDeleteLeftFlick = enabled
    }

    /**
     * Delete キーが左フリックされた際に実行する処理を登録します。
     */
    fun setOnDeleteLeftFlickListener(listener: () -> Unit) {
        this.onDeleteLeftFlickListener = listener
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
            key1.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            key2.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            key3.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            key4.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            key5.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            key6.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            key7.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            key8.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            key9.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            key0.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))

            keyKuten.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyTouten.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))

            keyQ.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyW.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyE.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyR.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyT.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyY.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyU.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyI.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyO.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyP.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))

            keyA.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyS.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyD.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyF.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyG.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyH.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyJ.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyK.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyAtMark.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyL.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))

            keyZ.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyX.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyC.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyV.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyB.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyN.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))
            keyM.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))

            keySpace.setBackgroundDrawable(ContextCompat.getDrawable(context, bgRes))

            keyShift.setBackgroundDrawable(ContextCompat.getDrawable(context, bgSideRes))
            keyDelete.setBackgroundDrawable(ContextCompat.getDrawable(context, bgSideRes))
            keySwitchDefault.setBackgroundDrawable(ContextCompat.getDrawable(context, bgSideRes))
            keyReturn.setBackgroundDrawable(ContextCompat.getDrawable(context, bgSideRes))
            key123.setBackgroundDrawable(ContextCompat.getDrawable(context, bgSideRes))
            switchNumberLayout.setBackgroundDrawable(ContextCompat.getDrawable(context, bgSideRes))

            cursorLeft.setBackgroundDrawable(ContextCompat.getDrawable(context, bgSideRes))
            cursorRight.setBackgroundDrawable(ContextCompat.getDrawable(context, bgSideRes))
            switchRomajiEnglish.setBackgroundDrawable(ContextCompat.getDrawable(context, bgSideRes))
        }
    }

    fun setSpaceKeyText(text: String) {
        binding.keySpace.text = text
    }

    fun setReturnKeyText(text: String) {
        binding.keyReturn.text = text
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
            binding.keyShift to QWERTYKey.QWERTYKeyShift,      // AppCompatImageButton
            binding.keyDelete to QWERTYKey.QWERTYKeyDelete,    // AppCompatImageButton
            binding.keySwitchDefault to QWERTYKey.QWERTYKeySwitchDefaultLayout,
            binding.key123 to QWERTYKey.QWERTYKeySwitchMode,   // AppCompatButton
            binding.keySpace to QWERTYKey.QWERTYKeySpace,      // QWERTYButton
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
            // Top row (QWERTY)
            binding.keyQ,
            binding.keyW,
            binding.keyE,
            binding.keyR,
            binding.keyT,
            binding.keyY,
            binding.keyU,
            binding.keyI,
            binding.keyO,
            binding.keyP,

            // Middle row (ASDFGHJKL)
            binding.keyA,
            binding.keyS,
            binding.keyD,
            binding.keyF,
            binding.keyG,
            binding.keyH,
            binding.keyJ,
            binding.keyK,
            binding.keyL,

            // Bottom row (ZXCVBNM)
            binding.keyZ,
            binding.keyX,
            binding.keyC,
            binding.keyV,
            binding.keyB,
            binding.keyN,
            binding.keyM
        )
    }

    private val defaultQWERTYButtonsRoman: Array<QWERTYButton> by lazy {
        arrayOf(
            // Top row (QWERTY)
            binding.keyQ,
            binding.keyW,
            binding.keyE,
            binding.keyR,
            binding.keyT,
            binding.keyY,
            binding.keyU,
            binding.keyI,
            binding.keyO,
            binding.keyP,

            // Middle row (ASDFGHJKL)
            binding.keyA,
            binding.keyS,
            binding.keyD,
            binding.keyF,
            binding.keyG,
            binding.keyH,
            binding.keyJ,
            binding.keyK,
            binding.keyAtMark,
            binding.keyL,

            // Bottom row (ZXCVBNM)
            binding.keyZ,
            binding.keyX,
            binding.keyC,
            binding.keyV,
            binding.keyB,
            binding.keyN,
            binding.keyM
        )
    }

    private val numberQWERTYButtons: Array<QWERTYButton> by lazy {
        arrayOf(
            // Top row (QWERTY)
            binding.keyQ,
            binding.keyW,
            binding.keyE,
            binding.keyR,
            binding.keyT,
            binding.keyY,
            binding.keyU,
            binding.keyI,
            binding.keyO,
            binding.keyP,

            // Middle row (ASDFGHJKL)
            binding.keyA,
            binding.keyS,
            binding.keyD,
            binding.keyF,
            binding.keyG,
            binding.keyH,
            binding.keyJ,
            binding.keyK,
            binding.keyAtMark,
            binding.keyL,

            // Bottom row (ZXCVBNM)
            binding.keyZ,
            binding.keyX,
            binding.keyC,
            binding.keyN,
            binding.keyM
        )
    }

    private fun attachDefaultKeyLabels() {
        val chars = if (romajiModeState.value) {
            QWERTYKeys.DEFAULT_KEYS_JP
        } else {
            QWERTYKeys.DEFAULT_KEYS
        }
        val buttons = if (romajiModeState.value) {
            defaultQWERTYButtonsRoman
        } else {
            defaultQWERTYButtons
        }
        for (i in buttons.indices) {
            // One new length-1 String per button. No List, no boxed Characters, no lambdas.
            buttons[i].text = chars[i].toString()
        }
    }

    private fun attachNumberKeyLabels(isSymbol: Boolean) {
        val chars = if (isSymbol) {
            if (romajiModeState.value) {
                QWERTYKeys.SYMBOL_KEYS_JP
            } else {
                QWERTYKeys.SYMBOL_KEYS
            }
        } else {
            if (romajiModeState.value) {
                QWERTYKeys.NUMBER_KEYS_JP
            } else {
                QWERTYKeys.NUMBER_KEYS
            }
        }
        val buttons = numberQWERTYButtons
        for (i in buttons.indices) {
            // One new length-1 String per button. No List, no boxed Characters, no lambdas.
            buttons[i].text = chars[i].toString()
        }
    }

    /**
     * Set a listener that will receive:
     *  - onTouchQWERTYKey(...) on normal key‐up or tap, and
     *  - onLongPressQWERTYKey(...) on long‐press.
     */
    fun setOnQWERTYKeyListener(listener: QWERTYKeyListener) {
        this.qwertyKeyListener = listener
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // Intercept the initial DOWN so that onTouchEvent receives it
        return event.actionMasked == MotionEvent.ACTION_DOWN
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {

        if (isCursorMode) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val threshold = 20f // 移動を検知する閾値 (px)
                    val currentX = event.x
                    val currentY = event.y

                    val dx = currentX - cursorInitialX
                    val dy = currentY - cursorInitialY

                    // 水平方向の移動
                    if (abs(dx) > abs(dy) && abs(dx) > threshold) {
                        val direction =
                            if (dx < 0f) QWERTYKey.QWERTYKeyCursorLeft else QWERTYKey.QWERTYKeyCursorRight
                        qwertyKeyListener?.onReleasedQWERTYKey(direction, null, null)
                        cursorInitialX = currentX // 起点をリセット
                        cursorInitialY = currentY
                    }
                    // 垂直方向の移動
                    else if (abs(dy) > abs(dx) && abs(dy) > threshold) {
                        val direction =
                            if (dy < 0f) QWERTYKey.QWERTYKeyCursorUp else QWERTYKey.QWERTYKeyCursorDown
                        qwertyKeyListener?.onReleasedQWERTYKey(direction, null, null)
                        cursorInitialX = currentX // 起点をリセット
                        cursorInitialY = currentY
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    setCursorMode(false)
                    clearAllPressed()
                }
            }
            return true // イベントを消費
        }

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                // If multi-touch or leftover map entries exist, clear everything
                if (event.pointerCount > 1 || pointerButtonMap.isNotEmpty()) {
                    clearAllPressed()
                }
                suppressedPointerId = null
                handlePointerDown(event, pointerIndex = 0)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // If the variation popup is showing, ignore additional pointers.
                if (variationPopup?.isShowing == true) return true

                // Cancel the first tracked pointer if exactly one was active
                if (pointerButtonMap.size == 1) {
                    val firstPointerId = pointerButtonMap.keyAt(0)
                    val firstView = pointerButtonMap.valueAt(0)
                    firstView?.let { view ->
                        view.isPressed = false
                        dismissKeyPreview()
                        cancelLongPressForPointer(firstPointerId)

                        // ★フリック状態もクリーンアップ
                        pointerStartCoords.remove(firstPointerId)
                        flickLockedPointers.remove(firstPointerId)

                        val qwertyKey = qwertyButtonMap[view] ?: QWERTYKey.QWERTYKeyNotSelect
                        Log.d(
                            "QWERTYKeyboardView",
                            "ACTION_POINTER_DOWN: First finger (pid $firstPointerId) was on key: $qwertyKey"
                        )
                        if (firstPointerId == 0 && qwertyKey == QWERTYKey.QWERTYKeySwitchDefaultLayout) {
                            return true
                        }
                        logVariationIfNeeded(qwertyKey)
                    }
                    suppressedPointerId = firstPointerId
                    pointerButtonMap.remove(firstPointerId)
                }
                // Now track the new (second) finger
                val newIndex = event.actionIndex
                handlePointerDown(event, pointerIndex = newIndex)
            }

            MotionEvent.ACTION_MOVE -> {
                // If the variations popup is active, handle its selection
                if (variationPopup?.isShowing == true && longPressedPointerId != null) {
                    val index = event.findPointerIndex(longPressedPointerId!!)
                    if (index != -1) {
                        // Convert screen coordinates to the popup's local coordinates
                        val location = IntArray(2)
                        variationPopupView?.getLocationOnScreen(location)
                        val popupX = event.rawX - location[0]
                        variationPopupView?.updateSelection(popupX)
                    }
                } else {
                    // Otherwise, perform the normal move handling (★これが修正済みの handlePointerMove)
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

                // If it was the long-pressing pointer that lifted, treat it like a normal ACTION_UP.
                if (variationPopup?.isShowing == true && pointerId == longPressedPointerId) {
                    if (variationPopup?.isShowing == true) {
                        variationPopupView?.getSelectedChar()?.let { selectedChar ->
                            val qwertyKey =
                                qwertyButtonMap[pointerButtonMap[longPressedPointerId!!]]
                                    ?: QWERTYKey.QWERTYKeyNotSelect
                            qwertyKeyListener?.onReleasedQWERTYKey(
                                qwertyKey = qwertyKey,
                                tap = selectedChar,
                                variations = null
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


                // If it was a suppressed pointer, clear suppression
                if (suppressedPointerId == pointerId) {
                    suppressedPointerId = null
                }

                // ★ フリック開始座標をクリーンアップ
                pointerStartCoords.remove(pointerId)

                // ★ フリックロックされたポインターでなければ、通常のタップ処理を実行
                if (!flickLockedPointers.contains(pointerId)) {
                    val view = pointerButtonMap[pointerId]
                    view?.let {
                        it.isPressed = false
                        dismissKeyPreview()
                        cancelLongPressForPointer(pointerId)

                        val wasShift = (it.id == binding.keyShift.id)
                        // If Shift was double‐tapped, suppress this single‐tap event
                        if (wasShift && shiftDoubleTapped) {
                            // Consume without notifying
                            shiftDoubleTapped = false
                        } else {
                            val qwertyKey = qwertyButtonMap[it] ?: QWERTYKey.QWERTYKeyNotSelect
                            when (qwertyKey) {
                                QWERTYKey.QWERTYKeyCursorLeft, QWERTYKey.QWERTYKeyCursorRight, QWERTYKey.QWERTYKeySwitchRomajiEnglish, QWERTYKey.QWERTYKeySwitchNumberKey -> {
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
                // ★ フリックロックされたポインターの場合、何もしない（ACTION_MOVEで処理済み）

                pointerButtonMap.remove(pointerId)
            }

            MotionEvent.ACTION_UP -> {
                // ★ 最初に liftedId を取得
                val liftedId = event.getPointerId(event.actionIndex)

                // If a variation popup was active, finalize the selection
                if (variationPopup?.isShowing == true) {
                    variationPopupView?.getSelectedChar()?.let { selectedChar ->
                        val qwertyKey = qwertyButtonMap[pointerButtonMap[longPressedPointerId!!]]
                            ?: QWERTYKey.QWERTYKeyNotSelect
                        qwertyKeyListener?.onReleasedQWERTYKey(
                            qwertyKey = qwertyKey,
                            tap = selectedChar,
                            variations = null
                        )
                    }
                    disableShift()
                    variationPopup?.dismiss()
                    variationPopup = null
                    variationPopupView = null
                    longPressedPointerId = null

                    // ★ フリックロックされていないポインターの場合、通常のタップ処理
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
                            // If Shift was double‐tapped, suppress this single‐tap event
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
                                        QWERTYKey.QWERTYKeyCursorLeft, QWERTYKey.QWERTYKeyCursorRight, QWERTYKey.QWERTYKeySwitchRomajiEnglish, QWERTYKey.QWERTYKeySwitchNumberKey -> {
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
                // ★ フリックロックされたポインターの場合、何もしない（ACTION_MOVEで処理済み）

                // ★ すべてのUP/CANCEL処理の最後に clearAllPressed を呼ぶ
                clearAllPressed()
            }

            MotionEvent.ACTION_CANCEL -> {
                // On cancel, ensure the popup is dismissed and everything is reset
                variationPopup?.dismiss()
                variationPopup = null
                variationPopupView = null
                longPressedPointerId = null
                clearAllPressed()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
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

    fun resetQWERTYKeyboard(
    ) {
        clearShiftCaps()
        _qwertyMode.update { QWERTYMode.Default }
        _romajiModeState.update { false }
        binding.apply {
            keySpace.text = resources.getString(com.kazumaproject.core.R.string.space_english)
        }
    }


    fun resetQWERTYKeyboard(
        enterKyeText: String
    ) {
        clearShiftCaps()
        _qwertyMode.update { QWERTYMode.Default }
        _romajiModeState.update { false }
        binding.apply {
            keySpace.text = resources.getString(com.kazumaproject.core.R.string.space_english)
            keyReturn.text = enterKyeText
        }
    }

    fun setRomajiKeyboard(
        enterKeyText: String
    ) {
        clearShiftCaps()
        _qwertyMode.update { QWERTYMode.Default }
        _romajiModeState.update { true }
        binding.apply {
            keySpace.text = resources.getString(com.kazumaproject.core.R.string.space_japanese)
            keyReturn.text = enterKeyText
        }
    }

    /**
     * Handle a new pointer DOWN event (when it is not suppressed).
     */
    private fun handlePointerDown(event: MotionEvent, pointerIndex: Int) {
        val pid = event.getPointerId(pointerIndex)
        if (pid == suppressedPointerId) return

        // ★ Float型で座標を取得（IntへのキャストはfindButtonUnderの呼び出し時に行う）
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)

        // 1. フリック検知のため、タッチ開始座標を保存
        pointerStartCoords.put(pid, Pair(x, y))
        // 2. 既存のフリックロックを（念のため）解除
        flickLockedPointers.remove(pid)

        val view = findButtonUnder(x.toInt(), y.toInt()) // ★ .toInt() をここに追加
        view?.let {
            val qwertyKey = qwertyButtonMap[it]
            qwertyKey?.let { key ->
                qwertyKeyListener?.onPressedQWERTYKey(key)
            }
            it.isPressed = true
            pointerButtonMap.put(pid, it)

            // ⑤ If this is the Shift key, check for double‐tap
            if (it.id == binding.keyShift.id) {
                val now = SystemClock.uptimeMillis()
                if (now - lastShiftTapTime <= doubleTapTimeout) {
                    // Double‐tap detected
                    onShiftDoubleTapped()
                    // Prevent the next single‐tap
                    shiftDoubleTapped = true
                    // Reset so the next tap isn’t treated as “second” of a triple
                    lastShiftTapTime = 0L
                } else {
                    // Not a double‐tap (yet) – record this tap time
                    lastShiftTapTime = now
                }
            }

            // ⑥ Show preview for non‐edge, non‐icon keys
            if (it.id != binding.keySpace.id &&
                it.id != binding.keyDelete.id &&
                it.id != binding.keyShift.id &&
                it.id != binding.key123.id &&
                it.id != binding.keyReturn.id &&
                it.id != binding.keySwitchDefault.id &&
                it.id != binding.cursorLeft.id &&
                it.id != binding.cursorRight.id &&
                it.id != binding.switchRomajiEnglish.id &&
                it.id != binding.switchNumberLayout.id
            ) {
                showKeyPreview(it)
            }

            // ⑦ Schedule long‐press detection for this pointer + view
            scheduleLongPressForPointer(pid, it)
        }
    }

    private enum class FlickDirection {
        NONE, UP, DOWN, LEFT
    }

    private fun detectFlickDirection(
        x: Float, y: Float, startX: Float, startY: Float, threshold: Float
    ): FlickDirection {
        val dx = x - startX
        val dy = y - startY

        // 移動量の絶対値が大きい方を軸として判定する
        if (abs(dx) > abs(dy)) {
            // --- 水平方向 (Horizontal) ---
            // 閾値を超えており、かつ dx がマイナス（左方向）の場合
            if (abs(dx) > threshold && dx < 0) {
                return FlickDirection.LEFT
            }
        } else {
            // --- 垂直方向 (Vertical) ---
            // 閾値を超えている場合
            if (abs(dy) > threshold) {
                return if (dy < 0) FlickDirection.UP else FlickDirection.DOWN
            }
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
        Log.d("QWERTYKeyboardView", "Up-flick detected on key (during MOVE): $qwertyKey")
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
        if (qwertyKey == QWERTYKey.QWERTYKeySpace) return

        val baseChar = previousView.text.firstOrNull()?.uppercaseChar() ?: return
        val charToInsert = if (romajiModeState.value) {
            baseChar.toZenkaku()
        } else {
            baseChar
        }

        Log.d(
            "QWERTYKeyboardView",
            "Down-flick detected on key: $qwertyKey, inserting $charToInsert"
        )
        qwertyKeyListener?.onFlickDownQWERTYKey(
            qwertyKey = qwertyKey,
            character = charToInsert
        )
    }

    /**
     * Handle a MOVE event for a tracked pointer. If it slides off its original key, update pressed state.
     * フリック方向（上・下・左）を判定し、必要に応じて専用の処理を行う。
     */
    private fun handlePointerMove(event: MotionEvent, pointerIndex: Int, pointerId: Int) {
        if (pointerId == suppressedPointerId || pointerId == lockedPointerId) return

        // 既にフリック（または長押し専用）としてロックされているポインターは、それ以上処理しない
        if (flickLockedPointers.contains(pointerId)) {
            return
        }

        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)
        val previousView = pointerButtonMap[pointerId]

        // ------- フリック判定（全キー対象） -------
        pointerStartCoords[pointerId]?.let { (startX, startY) ->
            val flickDirection = detectFlickDirection(x, y, startX, startY, flickThreshold)

            when (flickDirection) {
                FlickDirection.UP -> {
                    // 上フリックは QWERTYButton のみ対象
                    if (enableFlickUpDetection && previousView is QWERTYButton) {
                        applyCommonFlickEffects(pointerId, previousView)
                        handleUpFlick(previousView)
                        return
                    }
                }

                FlickDirection.DOWN -> {
                    // 下フリックも QWERTYButton のみ対象
                    if (enableFlickDownDetection && previousView is QWERTYButton) {
                        applyCommonFlickEffects(pointerId, previousView)
                        handleDownFlick(previousView)
                        return
                    }
                }

                FlickDirection.LEFT -> {
                    // 左フリックは Delete キーかどうかだけ ID で判定する
                    Log.d(
                        "QWERTYKeyboardView",
                        "Delete Key Left-Flick called [$enableDeleteLeftFlick] [${previousView?.id == binding.keyDelete.id}]"
                    )
                    if (enableDeleteLeftFlick && previousView != null && previousView.id == binding.keyDelete.id) {
                        applyCommonFlickEffects(pointerId, previousView)

                        // リスナーを実行
                        onDeleteLeftFlickListener?.invoke()

                        Log.d("QWERTYKeyboardView", "Delete Key Left-Flick detected")
                        return
                    }
                }

                FlickDirection.NONE -> {
                    // フリックなし → 従来の MOVE 処理へ
                }
            }
        }
        // ------- フリック判定ここまで -------

        // 上記で return されていない場合のみ、通常のキースライド処理を実行
        val currentView = findButtonUnder(x.toInt(), y.toInt())

        if (currentView != previousView) {
            // 前のキーから指が離れた
            previousView?.let {
                it.isPressed = false
                dismissKeyPreview()
                cancelLongPressForPointer(pointerId)
            }

            currentView?.let {
                val qwertyKey = qwertyButtonMap[it]
                qwertyKey?.let { key ->
                    qwertyKeyListener?.onPressedQWERTYKey(key)
                }
                it.isPressed = true
                pointerButtonMap.put(pointerId, it)

                // 新しいキーに移動したので、フリックの開始点をリセットする
                pointerStartCoords.put(pointerId, Pair(x, y))

                if (it.id != binding.keySpace.id &&
                    it.id != binding.keyDelete.id &&
                    it.id != binding.keyShift.id &&
                    it.id != binding.key123.id &&
                    it.id != binding.keyReturn.id &&
                    it.id != binding.keySwitchDefault.id &&
                    it.id != binding.switchNumberLayout.id &&
                    it.id != binding.cursorRight.id &&
                    it.id != binding.cursorLeft.id
                ) {
                    showKeyPreview(it)
                }

                // 必要であればここで長押しを再スケジュール
                // scheduleLongPressForPointer(pointerId, it)
            } ?: run {
                // どのキー上でもなくなった場合
                pointerButtonMap.remove(pointerId)
                pointerStartCoords.remove(pointerId)
                cancelLongPressForPointer(pointerId)
            }
        }
    }

    /**
     * Clear pressed state for all tracked keys, dismiss the preview, cancel all long‐press Jobs, and reset suppression.
     */
    private fun clearAllPressed() {
        for (i in 0 until pointerButtonMap.size) {
            val pid = pointerButtonMap.keyAt(i)
            pointerButtonMap.valueAt(i)?.isPressed = false
            cancelLongPressForPointer(pid)
        }
        pointerButtonMap.clear()

        // --- ▼ここから追加▼ ---
        // ★ フリック追跡用のマップとセットもクリアする
        pointerStartCoords.clear()
        flickLockedPointers.clear()
        // --- ▲ここまで追加▲ ---

        dismissKeyPreview()
        suppressedPointerId = null
        variationPopup?.dismiss()
        variationPopup = null
        variationPopupView = null
        longPressedPointerId = null
        // dismissKeyPreview() // 元のコードで重複していた
        // suppressedPointerId = null // 元のコードで重複していた
        lockedPointerId = null
    }

    /**
     * Show a PopupWindow “preview” above the given key‐View.
     */
    private fun showKeyPreview(view: View) {
        if (isTablet) return
        if (!showPopupView) return
        dismissKeyPreview()
        val previewHeight = dpToPx(view.height)
        val layoutRes = R.layout.key_preview_large
        val popupView = LayoutInflater.from(context).inflate(layoutRes, this, false)
        val tv = popupView.findViewById<TextView>(R.id.preview_text)
        val iv = popupView.findViewById<ImageView>(R.id.preview_bubble_bg)
        val isLandMode =
            (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        // 1. Determine which key-IDs count as “left” vs. “right” bubbles
        val leftKeyIds: Set<Int> =
            if (qwertyMode.value == QWERTYMode.Default && !isLandMode && !romajiModeState.value) {
                setOf(binding.keyQ.id)
            } else {
                setOf(binding.keyQ.id, binding.keyA.id)
            }

        val rightKeyIds: Set<Int> =
            if (qwertyMode.value == QWERTYMode.Default && !isLandMode && !romajiModeState.value) {
                setOf(binding.keyP.id)
            } else {
                setOf(binding.keyP.id, binding.keyL.id)
            }

        val drawableResIdForImageView = when (view.id) {
            in leftKeyIds -> if (isDynamicColorsEnable) com.kazumaproject.core.R.drawable.key_preview_bubble_left_material else com.kazumaproject.core.R.drawable.key_preview_bubble_left
            in rightKeyIds -> if (isDynamicColorsEnable) com.kazumaproject.core.R.drawable.key_preview_bubble_right_material else com.kazumaproject.core.R.drawable.key_preview_bubble_right
            else -> if (isDynamicColorsEnable) com.kazumaproject.core.R.drawable.key_preview_bubble_material else com.kazumaproject.core.R.drawable.key_preview_bubble
        }
        iv.setBackgroundResource(drawableResIdForImageView)
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

        val popup = PopupWindow(
            popupView, view.width * 2, view.height * 2 + 64, false
        ).apply {
            isTouchable = false
            isFocusable = false
            elevation = 6f
        }


        val xOffset = -(view.width / 2)
        val yOffset = -(view.height * 2 + 64)

        popup.showAsDropDown(view, xOffset, yOffset)
        keyPreviewPopup = popup
    }

    /** Dismiss any visible key preview. */
    private fun dismissKeyPreview() {
        keyPreviewPopup?.dismiss()
        keyPreviewPopup = null
    }

    /**
     * Hit‐test: (1) return the key directly under (x,y), else (2) return the nearest key.
     */
    private fun findButtonUnder(x: Int, y: Int): View? {
        var nearestView: View? = null
        var minDistSquared = Int.MAX_VALUE

        // 1) Check direct hit first
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (!child.isVisible || !qwertyButtonMap.containsKey(child)) continue

            child.getHitRect(hitRect)
            if (hitRect.contains(x, y)) {
                return child
            }
        }

        // 2) Otherwise, find the nearest center
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

    /**
     * If the key supports variations, log details and notify listener for normal tap.
     */
    private fun logVariationIfNeeded(
        key: QWERTYKey
    ) {
        if (key == QWERTYKey.QWERTYKeySwitchMode) {
            when (qwertyMode.value) {
                QWERTYMode.Default -> {
                    clearShiftCaps()
                    _qwertyMode.update { QWERTYMode.Number }
                }

                QWERTYMode.Number, QWERTYMode.Symbol -> _qwertyMode.update { QWERTYMode.Default }
            }
            Log.d(
                "KEY_VARIATION", "KEY: $key, ${qwertyMode.value}"
            )
            return
        }
        val info = getVariationInfo(key)
        info?.apply {
            Log.d(
                "KEY_VARIATION",
                "KEY: $key, tap: ${tap}, cap: ${cap}, " + "variations: ${variations}, capVariations: $capVariations"
            )
            val outChar =
                if (capsLockState.value.capsLockOn || capsLockState.value.shiftOn) cap else tap
            qwertyKeyListener?.onReleasedQWERTYKey(
                qwertyKey = key, tap = outChar, variations = variations
            )
        }
    }

    /**
     * If `key` supports variations, return its tap/cap/variations/capVariations.
     * Otherwise return null.
     */
    private fun getVariationInfo(key: QWERTYKey): VariationInfo? {
        val info: QWERTYKeyInfo = when (qwertyMode.value) {
            QWERTYMode.Default -> if (romajiModeState.value) {
                if (isNumberKeysShow) {
                    qwertyKeyMap.getKeyInfoDefaultJPWithNumberRow(key)
                } else {
                    qwertyKeyMap.getKeyInfoDefaultJP(key)
                }
            } else {
                if (isNumberKeysShow) {
                    qwertyKeyMap.getKeyInfoDefaultWithNumberRow(key)
                } else {
                    qwertyKeyMap.getKeyInfoDefault(key)
                }
            }

            QWERTYMode.Number -> {
                if (romajiModeState.value) {
                    qwertyKeyMap.getKeyInfoNumberJP(key)
                } else {
                    qwertyKeyMap.getKeyInfoNumber(key)
                }
            }

            QWERTYMode.Symbol -> {
                if (romajiModeState.value) {
                    qwertyKeyMap.getKeyInfoSymbolJP(key)
                } else {
                    qwertyKeyMap.getKeyInfoSymbol(key)
                }
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

    /**
     * Called when the Shift key is double‐tapped.
     */
    private fun onShiftDoubleTapped() {
        Log.d("QWERTYKEY", "Shift was double‐tapped!")
        if (qwertyMode.value == QWERTYMode.Default) {
            enableCapsLock()
        }
    }

    // ─────────────────────────────────────────────
    // Long‐press scheduling / cancellation (with coroutines)
    // ─────────────────────────────────────────────

    /**
     * Schedule a “long‐press” Job for the given pointer + view.
     */
    private fun scheduleLongPressForPointer(pointerId: Int, view: View) {
        cancelLongPressForPointer(pointerId)

        val job = scope.launch {
            delay(longPressTimeout)
            val currentView = pointerButtonMap[pointerId]
            if (currentView == view) {
                val qwertyKey = qwertyButtonMap[view] ?: QWERTYKey.QWERTYKeyNotSelect

                val info = getVariationInfo(qwertyKey)

                // ★ 条件を分かりやすく変数に格納
                val hasVariations = info != null && !info.variations.isNullOrEmpty()
                val isSpecialLongPressKey = qwertyKey in longPressEnabledKeys

                // ★ 条件1: 派生文字を持つキーの場合
                if (hasVariations) {
                    // リスナーを呼び出し、ポップアップを表示する（元のロジック）
                    qwertyKeyListener?.onLongPressQWERTYKey(qwertyKey)
                    info?.variations?.let {
                        showVariationPopup(view, it)
                    }
                    longPressedPointerId = pointerId
                    dismissKeyPreview()
                }
                // ★ 条件2: 派生文字はないが、特別に長押しを有効にするキーの場合
                else if (isSpecialLongPressKey) {
                    // リスナーのみを呼び出す
                    qwertyKeyListener?.onLongPressQWERTYKey(qwertyKey)
                    lockedPointerId = pointerId
                }
            }
        }
        longPressJobs.put(pointerId, job)
    }

    // 新しいメソッド：派生文字ポップアップを表示する
    private fun showVariationPopup(anchorView: View, variations: List<Char>) {
        // 既存のポップアップがあれば閉じる
        variationPopup?.dismiss()

        val context = this.context
        variationPopupView = VariationsPopupView(context).apply {
            setChars(variations)
        }

        // ポップアップのサイズを計算
        val charWidth = 80 // 1文字あたりの幅 (dpToPxなどを使って調整)
        val popupWidth = charWidth * variations.size
        val popupHeight = 150 // ポップアップの高さ (同様に調整)

        val popup = PopupWindow(variationPopupView, popupWidth, popupHeight, false).apply {
            isTouchable = false // ポップアップ自体はタッチを受けない
        }

        // 表示位置を計算 (キーの上中央)
        val xOffset = if (variations.isNotEmpty() && variations.size == 1) {
            (-(popupWidth / 2) + (anchorView.width / 2))
        } else {
            (anchorView.width / 2)
        }
        val yOffset = -anchorView.height - popupHeight

        popup.showAsDropDown(anchorView, xOffset, yOffset)
        this.variationPopup = popup
    }

    /**
     * Cancel any pending “long‐press” Job for this pointer.
     */
    private fun cancelLongPressForPointer(pointerId: Int) {
        Log.d("QWERTYKEY", "Long‐press cancel")
        longPressJobs[pointerId]?.let { job ->
            job.cancel()
            longPressJobs.remove(pointerId)
        }
    }

    private fun toggleShift() {
        _capsLockState.update {
            it.copy(
                shiftOn = !it.shiftOn, capsLockOn = it.capsLockOn,
            )
        }
    }

    private fun disableShift() {
        _capsLockState.update {
            it.copy(
                shiftOn = false, capsLockOn = it.capsLockOn,
            )
        }
    }


    private fun enableCapsLock() {
        _capsLockState.update {
            it.copy(
                capsLockOn = true, shiftOn = false,
            )
        }
    }

    private fun clearShiftCaps() {
        _capsLockState.value = CapsLockState()
    }

    fun getRomajiMode(): Boolean {
        return romajiModeState.value
    }

    fun setRomajiMode(state: Boolean) {
        _romajiModeState.update { state }
    }

    fun setCursorMode(enabled: Boolean) {
        isCursorMode = enabled
        if (enabled) {
            setKeysForCursorMoveMode()
        } else {
            when (_qwertyMode.value) {
                QWERTYMode.Default -> {
                    attachDefaultKeyLabels()
                }

                QWERTYMode.Number -> {
                    attachNumberKeyLabels(isSymbol = false)
                }

                QWERTYMode.Symbol -> {
                    attachNumberKeyLabels(isSymbol = true)
                }
            }

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
        // 例：キーのテキストを消去する
        binding.apply {
            val characterKeys = listOf(
                keyQ,
                keyW,
                keyE,
                keyR,
                keyT,
                keyY,
                keyU,
                keyI,
                keyO,
                keyP,
                keyA,
                keyS,
                keyD,
                keyF,
                keyG,
                keyH,
                keyJ,
                keyK,
                keyL,
                keyZ,
                keyX,
                keyC,
                keyV,
                keyB,
                keyN,
                keyM,
                keyAtMark,
            )
            characterKeys.forEach { it.text = "" }
            keySpace.text = ""
        }
    }

    /**
     * 特定のキーの表示・非表示を切り替えます。
     * @param showCursors trueの場合、左右のカーソルキーを表示します。
     * @param showSwitchKey trueの場合、レイアウト切り替えキーを表示します。
     */
    fun setSpecialKeyVisibility(
        showCursors: Boolean,
        showSwitchKey: Boolean,
        showKutouten: Boolean
    ) {
        binding.cursorLeft.isVisible = showCursors
        binding.cursorRight.isVisible = showCursors
        binding.keySwitchDefault.isVisible = showSwitchKey
        binding.keyKuten.isVisible = showKutouten
        binding.keyTouten.isVisible = showKutouten
    }

    fun setRomajiEnglishSwitchKeyVisibility(
        showRomajiEnglishKey: Boolean
    ) {
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
     * Sets the style for the text in the number switch key ("あa1").
     *
     * This function makes the 'a' character bold and slightly larger,
     * while keeping the other characters in a normal style.
     */
    fun setNumberSwitchKeyTextStyle() {
        // The text to be displayed on the button.
        val text = "あa1"
        val spannableString = SpannableString(text)

        // Apply NORMAL style to the first character "あ" (index 0).
        spannableString.setSpan(
            StyleSpan(Typeface.NORMAL),
            0, // Start index (inclusive)
            1, // End index (exclusive)
            Spannable.SPAN_INCLUSIVE_EXCLUSIVE
        )

        // Apply BOLD style to the second character "a" (index 1).
        spannableString.setSpan(
            StyleSpan(Typeface.BOLD),
            1, // Start index (inclusive)
            2, // End index (exclusive)
            Spannable.SPAN_INCLUSIVE_EXCLUSIVE
        )

        // You could also increase the size of 'a' like in your other function.
        spannableString.setSpan(
            RelativeSizeSpan(1.5f), // Makes 'a' 20% larger
            1, // Start index
            2, // End index
            Spannable.SPAN_INCLUSIVE_EXCLUSIVE
        )

        // Apply NORMAL style to the third character "1" (index 2).
        spannableString.setSpan(
            StyleSpan(Typeface.NORMAL),
            2, // Start index (inclusive)
            3, // End index (exclusive)
            Spannable.SPAN_INCLUSIVE_EXCLUSIVE
        )
        binding.switchNumberLayout.text = spannableString
    }

    fun updateNumberKeyState(state: Boolean) {
        this.isNumberKeysShow = state
        displayOrHideNumberKeys(state)
    }

    fun updateSwitchRomajiEnglishState(state: Boolean) {
        binding.switchRomajiEnglish.isVisible = state
    }

    fun updateSymbolKeymapState(state: Boolean) {
        this.isSymbolKeymapShow = state
        if (state) {
            if (romajiModeState.value) {
                if (isNumberKeysShow) {
                    defaultQWERTYButtonsRoman.forEach {
                        when (it.id) {
                            R.id.key_a -> {
                                it.topRightChar = '@'
                            }

                            R.id.key_b -> {
                                it.topRightChar = ';'
                            }

                            R.id.key_c -> {
                                it.topRightChar = '\''
                            }

                            R.id.key_d -> {
                                it.topRightChar = '￥'
                            }

                            R.id.key_e -> {
                                it.topRightChar = '|'
                            }

                            R.id.key_f -> {
                                it.topRightChar = '_'
                            }

                            R.id.key_g -> {
                                it.topRightChar = '&'
                            }

                            R.id.key_h -> {
                                it.topRightChar = '-'
                            }

                            R.id.key_i -> {
                                it.topRightChar = '>'
                            }

                            R.id.key_j -> {
                                it.topRightChar = '+'
                            }

                            R.id.key_k -> {
                                it.topRightChar = '('
                            }

                            R.id.key_at_mark -> {
                                it.topRightChar = ')'
                            }

                            R.id.key_l -> {
                                it.topRightChar = '/'
                            }

                            R.id.key_m -> {
                                it.topRightChar = '?'
                            }

                            R.id.key_n -> {
                                it.topRightChar = '!'
                            }

                            R.id.key_o -> {
                                it.topRightChar = '{'
                            }

                            R.id.key_p -> {
                                it.topRightChar = '}'
                            }

                            R.id.key_q -> {
                                it.topRightChar = '%'
                            }

                            R.id.key_r -> {
                                it.topRightChar = '='
                            }

                            R.id.key_s -> {
                                it.topRightChar = '#'
                            }

                            R.id.key_t -> {
                                it.topRightChar = '['
                            }

                            R.id.key_u -> {
                                it.topRightChar = '<'
                            }

                            R.id.key_v -> {
                                it.topRightChar = ':'
                            }

                            R.id.key_w -> {
                                it.topRightChar = '\\'
                            }

                            R.id.key_x -> {
                                it.topRightChar = '"'
                            }

                            R.id.key_y -> {
                                it.topRightChar = ']'
                            }

                            R.id.key_z -> {
                                it.topRightChar = '*'
                            }
                        }
                    }
                } else {
                    defaultQWERTYButtonsRoman.forEach {
                        when (it.id) {
                            R.id.key_a -> {
                                it.topRightChar = '@'
                            }

                            R.id.key_b -> {
                                it.topRightChar = ';'
                            }

                            R.id.key_c -> {
                                it.topRightChar = '\''
                            }

                            R.id.key_d -> {
                                it.topRightChar = '￥'
                            }

                            R.id.key_e -> {
                                it.topRightChar = '3'
                            }

                            R.id.key_f -> {
                                it.topRightChar = '_'
                            }

                            R.id.key_g -> {
                                it.topRightChar = '&'
                            }

                            R.id.key_h -> {
                                it.topRightChar = '-'
                            }

                            R.id.key_i -> {
                                it.topRightChar = '8'
                            }

                            R.id.key_j -> {
                                it.topRightChar = '+'
                            }

                            R.id.key_k -> {
                                it.topRightChar = '('
                            }

                            R.id.key_at_mark -> {
                                it.topRightChar = ')'
                            }

                            R.id.key_l -> {
                                it.topRightChar = '/'
                            }

                            R.id.key_m -> {
                                it.topRightChar = '?'
                            }

                            R.id.key_n -> {
                                it.topRightChar = '!'
                            }

                            R.id.key_o -> {
                                it.topRightChar = '9'
                            }

                            R.id.key_p -> {
                                it.topRightChar = '0'
                            }

                            R.id.key_q -> {
                                it.topRightChar = '1'
                            }

                            R.id.key_r -> {
                                it.topRightChar = '4'
                            }

                            R.id.key_s -> {
                                it.topRightChar = '#'
                            }

                            R.id.key_t -> {
                                it.topRightChar = '5'
                            }

                            R.id.key_u -> {
                                it.topRightChar = '7'
                            }

                            R.id.key_v -> {
                                it.topRightChar = ':'
                            }

                            R.id.key_w -> {
                                it.topRightChar = '2'
                            }

                            R.id.key_x -> {
                                it.topRightChar = '"'
                            }

                            R.id.key_y -> {
                                it.topRightChar = '6'
                            }

                            R.id.key_z -> {
                                it.topRightChar = '*'
                            }
                        }
                    }
                }
            } else {
                if (isNumberKeysShow) {
                    defaultQWERTYButtons.forEach {
                        when (it.id) {
                            R.id.key_a -> {
                                it.topRightChar = '@'
                            }

                            R.id.key_b -> {
                                it.topRightChar = '#'
                            }

                            R.id.key_c -> {
                                it.topRightChar = '\''
                            }

                            R.id.key_d -> {
                                it.topRightChar = '$'
                            }

                            R.id.key_e -> {
                                it.topRightChar = '|'
                            }

                            R.id.key_f -> {
                                it.topRightChar = '_'
                            }

                            R.id.key_g -> {
                                it.topRightChar = '&'
                            }

                            R.id.key_h -> {
                                it.topRightChar = '-'
                            }

                            R.id.key_i -> {
                                it.topRightChar = '>'
                            }

                            R.id.key_j -> {
                                it.topRightChar = '+'
                            }

                            R.id.key_k -> {
                                it.topRightChar = '('
                            }

                            R.id.key_l -> {
                                it.topRightChar = ')'
                            }

                            R.id.key_m -> {
                                it.topRightChar = '?'
                            }

                            R.id.key_n -> {
                                it.topRightChar = '!'
                            }

                            R.id.key_o -> {
                                it.topRightChar = '{'
                            }

                            R.id.key_p -> {
                                it.topRightChar = '}'
                            }

                            R.id.key_q -> {
                                it.topRightChar = '%'
                            }

                            R.id.key_r -> {
                                it.topRightChar = '='
                            }

                            R.id.key_s -> {
                                it.topRightChar = '#'
                            }

                            R.id.key_t -> {
                                it.topRightChar = '['
                            }

                            R.id.key_u -> {
                                it.topRightChar = '<'
                            }

                            R.id.key_v -> {
                                it.topRightChar = ':'
                            }

                            R.id.key_w -> {
                                it.topRightChar = '\\'
                            }

                            R.id.key_x -> {
                                it.topRightChar = '"'
                            }

                            R.id.key_y -> {
                                it.topRightChar = ']'
                            }

                            R.id.key_z -> {
                                it.topRightChar = '*'
                            }
                        }
                    }
                } else {
                    defaultQWERTYButtonsRoman.forEach {
                        when (it.id) {
                            R.id.key_a -> {
                                it.topRightChar = '@'
                            }

                            R.id.key_b -> {
                                it.topRightChar = ';'
                            }

                            R.id.key_c -> {
                                it.topRightChar = '\''
                            }

                            R.id.key_d -> {
                                it.topRightChar = '$'
                            }

                            R.id.key_e -> {
                                it.topRightChar = '3'
                            }

                            R.id.key_f -> {
                                it.topRightChar = '_'
                            }

                            R.id.key_g -> {
                                it.topRightChar = '&'
                            }

                            R.id.key_h -> {
                                it.topRightChar = '-'
                            }

                            R.id.key_i -> {
                                it.topRightChar = '8'
                            }

                            R.id.key_j -> {
                                it.topRightChar = '+'
                            }

                            R.id.key_k -> {
                                it.topRightChar = '('
                            }

                            R.id.key_at_mark -> {
                                it.topRightChar = null
                            }

                            R.id.key_l -> {
                                it.topRightChar = ')'
                            }

                            R.id.key_m -> {
                                it.topRightChar = '?'
                            }

                            R.id.key_n -> {
                                it.topRightChar = '!'
                            }

                            R.id.key_o -> {
                                it.topRightChar = '9'
                            }

                            R.id.key_p -> {
                                it.topRightChar = '0'
                            }

                            R.id.key_q -> {
                                it.topRightChar = '1'
                            }

                            R.id.key_r -> {
                                it.topRightChar = '4'
                            }

                            R.id.key_s -> {
                                it.topRightChar = '#'
                            }

                            R.id.key_t -> {
                                it.topRightChar = '5'
                            }

                            R.id.key_u -> {
                                it.topRightChar = '7'
                            }

                            R.id.key_v -> {
                                it.topRightChar = ':'
                            }

                            R.id.key_w -> {
                                it.topRightChar = '2'
                            }

                            R.id.key_x -> {
                                it.topRightChar = '"'
                            }

                            R.id.key_y -> {
                                it.topRightChar = '6'
                            }

                            R.id.key_z -> {
                                it.topRightChar = '*'
                            }
                        }
                    }
                }
            }
        } else {
            defaultQWERTYButtonsRoman.forEach { it.topRightChar = null }
        }
    }

    private fun displayOrHideNumberKeys(state: Boolean) {
        val constraintSet = androidx.constraintlayout.widget.ConstraintSet()
        constraintSet.clone(this)

        val qRowKeys = listOf(
            binding.keyQ, binding.keyW, binding.keyE, binding.keyR, binding.keyT,
            binding.keyY, binding.keyU, binding.keyI, binding.keyO, binding.keyP
        )

        // Show 5 rows (with numbers)
        if (state && isNumberKeysShow) {
            // Set visibility for number keys
            binding.key1.isVisible = true
            binding.key2.isVisible = true
            binding.key3.isVisible = true
            binding.key4.isVisible = true
            binding.key5.isVisible = true
            binding.key6.isVisible = true
            binding.key7.isVisible = true
            binding.key8.isVisible = true
            binding.key9.isVisible = true
            binding.key0.isVisible = true

            // Set guideline percentages for a 5-row layout
            constraintSet.setGuidelinePercent(R.id.guideline_number_row, 0.20f)
            constraintSet.setGuidelinePercent(R.id.guideline_q_row, 0.40f)
            constraintSet.setGuidelinePercent(R.id.guideline_a_row, 0.60f)
            constraintSet.setGuidelinePercent(R.id.guideline_z_row, 0.80f)

            // Connect the Q-row's top to the bottom of the number row guideline
            qRowKeys.forEach { key ->
                constraintSet.connect(
                    key.id,
                    LayoutParams.TOP,
                    R.id.guideline_number_row,
                    ConstraintLayout.LayoutParams.BOTTOM
                )
            }
        }
        // Hide numbers and show 4 rows
        else {
            // Set visibility for number keys
            binding.key1.isVisible = false
            binding.key2.isVisible = false
            binding.key3.isVisible = false
            binding.key4.isVisible = false
            binding.key5.isVisible = false
            binding.key6.isVisible = false
            binding.key7.isVisible = false
            binding.key8.isVisible = false
            binding.key9.isVisible = false
            binding.key0.isVisible = false

            // Set guideline percentages for a 4-row layout
            // Move the first guideline to the top (0%) to effectively hide it
            constraintSet.setGuidelinePercent(R.id.guideline_number_row, 0.0f)
            constraintSet.setGuidelinePercent(R.id.guideline_q_row, 0.25f)
            constraintSet.setGuidelinePercent(R.id.guideline_a_row, 0.50f)
            constraintSet.setGuidelinePercent(R.id.guideline_z_row, 0.75f)

            // Connect the Q-row's top directly to the parent's top
            qRowKeys.forEach { key ->
                constraintSet.connect(
                    key.id,
                    LayoutParams.TOP,
                    LayoutParams.PARENT_ID,
                    LayoutParams.TOP
                )
            }
        }

        // Apply the new constraints to the layout
        constraintSet.applyTo(this)
    }

    fun setSwitchNumberLayoutKeyVisibility(state: Boolean) {
        binding.switchNumberLayout.isVisible = state
    }

}
