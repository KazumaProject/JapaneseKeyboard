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
                                } else {
                                    if (isSymbolKeymapShow) {
                                        defaultQWERTYButtonsRoman.forEach {
                                            when (it.id) {
                                                R.id.key_a -> {
                                                    it.topRightChar = '@'
                                                }

                                                R.id.key_b -> {
                                                    it.topRightChar = '!'
                                                }

                                                R.id.key_c -> {
                                                    it.topRightChar = '\"'
                                                }

                                                R.id.key_d -> {
                                                    it.topRightChar = '+'
                                                }

                                                R.id.key_e -> {
                                                    it.topRightChar = '3'
                                                }

                                                R.id.key_f -> {
                                                    it.topRightChar = '-'
                                                }

                                                R.id.key_g -> {
                                                    it.topRightChar = '='
                                                }

                                                R.id.key_h -> {
                                                    it.topRightChar = '/'
                                                }

                                                R.id.key_i -> {
                                                    it.topRightChar = '8'
                                                }

                                                R.id.key_j -> {
                                                    it.topRightChar = '#'
                                                }

                                                R.id.key_k -> {
                                                    it.topRightChar = '('
                                                }

                                                R.id.key_at_mark -> {
                                                    it.topRightChar = ')'
                                                }

                                                R.id.key_m -> {
                                                    it.topRightChar = '…'
                                                }

                                                R.id.key_n -> {
                                                    it.topRightChar = '~'
                                                }

                                                R.id.key_o -> {
                                                    it.topRightChar = '9'
                                                }

                                                R.id.key_p -> {
                                                    it.topRightChar = '1'
                                                }

                                                R.id.key_q -> {
                                                    it.topRightChar = '1'
                                                }

                                                R.id.key_r -> {
                                                    it.topRightChar = '4'
                                                }

                                                R.id.key_s -> {
                                                    it.topRightChar = '*'
                                                }

                                                R.id.key_t -> {
                                                    it.topRightChar = '5'
                                                }

                                                R.id.key_u -> {
                                                    it.topRightChar = '7'
                                                }

                                                R.id.key_v -> {
                                                    it.topRightChar = '?'
                                                }

                                                R.id.key_w -> {
                                                    it.topRightChar = '2'
                                                }

                                                R.id.key_x -> {
                                                    it.topRightChar = ':'
                                                }

                                                R.id.key_y -> {
                                                    it.topRightChar = '6'
                                                }

                                                R.id.key_z -> {
                                                    it.topRightChar = '\''
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
                                    keyH.text = "|"
                                    keyJ.text = "\""
                                    keyK.text = "\'"
                                    keyAtMark.text = "￥"
                                    keyL.text = "€"
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
                        if (isSymbolKeymapShow) {
                            defaultQWERTYButtonsRoman.forEach {
                                when (it.id) {
                                    R.id.key_a -> {
                                        it.topRightChar = '@'
                                    }

                                    R.id.key_b -> {
                                        it.topRightChar = '!'
                                    }

                                    R.id.key_c -> {
                                        it.topRightChar = '\"'
                                    }

                                    R.id.key_d -> {
                                        it.topRightChar = '+'
                                    }

                                    R.id.key_e -> {
                                        it.topRightChar = '3'
                                    }

                                    R.id.key_f -> {
                                        it.topRightChar = '-'
                                    }

                                    R.id.key_g -> {
                                        it.topRightChar = '='
                                    }

                                    R.id.key_h -> {
                                        it.topRightChar = '/'
                                    }

                                    R.id.key_i -> {
                                        it.topRightChar = '8'
                                    }

                                    R.id.key_j -> {
                                        it.topRightChar = '#'
                                    }

                                    R.id.key_k -> {
                                        it.topRightChar = '('
                                    }

                                    R.id.key_at_mark -> {
                                        it.topRightChar = ')'
                                    }

                                    R.id.key_m -> {
                                        it.topRightChar = '…'
                                    }

                                    R.id.key_n -> {
                                        it.topRightChar = '~'
                                    }

                                    R.id.key_o -> {
                                        it.topRightChar = '9'
                                    }

                                    R.id.key_p -> {
                                        it.topRightChar = '1'
                                    }

                                    R.id.key_q -> {
                                        it.topRightChar = '1'
                                    }

                                    R.id.key_r -> {
                                        it.topRightChar = '4'
                                    }

                                    R.id.key_s -> {
                                        it.topRightChar = '*'
                                    }

                                    R.id.key_t -> {
                                        it.topRightChar = '5'
                                    }

                                    R.id.key_u -> {
                                        it.topRightChar = '7'
                                    }

                                    R.id.key_v -> {
                                        it.topRightChar = '?'
                                    }

                                    R.id.key_w -> {
                                        it.topRightChar = '2'
                                    }

                                    R.id.key_x -> {
                                        it.topRightChar = ':'
                                    }

                                    R.id.key_y -> {
                                        it.topRightChar = '6'
                                    }

                                    R.id.key_z -> {
                                        it.topRightChar = '\''
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
                            key123.text =
                                resources.getString(com.kazumaproject.core.R.string.string_123)
                            keyKuten.text = "."
                            keyTouten.text = ","
                        }
                        defaultQWERTYButtonsRoman.forEach {
                            it.topRightChar = null
                        }
                    }
                }
            }
        }
    }

    fun setPopUpViewState(state: Boolean) {
        this.showPopupView = state
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

                        val qwertyKey = qwertyButtonMap[view] ?: QWERTYKey.QWERTYKeyNotSelect
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
                    // Otherwise, perform the normal move handling
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
                    // This will be handled by ACTION_UP logic, so we can delegate to it.
                    // To keep it simple, we'll just process the UP logic here.
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

                // If that pointer was tracked, fire its “key up” or cancel long‐press
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
                            QWERTYKey.QWERTYKeyCursorLeft, QWERTYKey.QWERTYKeyCursorRight, QWERTYKey.QWERTYKeySwitchRomajiEnglish -> {
                                qwertyKeyListener?.onReleasedQWERTYKey(qwertyKey, null, null)
                            }

                            else -> {
                                logVariationIfNeeded(qwertyKey)
                                setToggleShiftState(view)
                            }
                        }
                    }
                }
                pointerButtonMap.remove(pointerId)
            }

            MotionEvent.ACTION_UP -> {
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
                } else {
                    // Otherwise, perform the normal UP action
                    val liftedId = event.getPointerId(event.actionIndex)
                    if (suppressedPointerId == liftedId) {
                        suppressedPointerId = null
                    }
                    if (pointerButtonMap.size == 1) {
                        val view = pointerButtonMap.valueAt(0)
                        view?.let {
                            it.isPressed = false
                            dismissKeyPreview()
                            cancelLongPressForPointer(liftedId)

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
                                        QWERTYKey.QWERTYKeyCursorLeft, QWERTYKey.QWERTYKeyCursorRight, QWERTYKey.QWERTYKeySwitchRomajiEnglish -> {
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

        val x = event.getX(pointerIndex).toInt()
        val y = event.getY(pointerIndex).toInt()
        val view = findButtonUnder(x, y)
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
                it.id != binding.switchRomajiEnglish.id
            ) {
                showKeyPreview(it)
            }

            // ⑦ Schedule long‐press detection for this pointer + view
            scheduleLongPressForPointer(pid, it)
        }
    }

    /**
     * Handle a MOVE event for a tracked pointer. If it slides off its original key, update pressed state.
     */
    private fun handlePointerMove(event: MotionEvent, pointerIndex: Int, pointerId: Int) {
        if (pointerId == suppressedPointerId) return

        val x = event.getX(pointerIndex).toInt()
        val y = event.getY(pointerIndex).toInt()
        val previousView = pointerButtonMap[pointerId]
        val currentView = findButtonUnder(x, y)

        if (currentView != previousView) {
            // Finger moved off previous key
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
                if (it.id != binding.keySpace.id && it.id != binding.keyDelete.id && it.id != binding.keyShift.id && it.id != binding.key123.id && it.id != binding.keyReturn.id && it.id != binding.keySwitchDefault.id) {
                    showKeyPreview(it)
                }
                // Schedule a new long‐press for this new key
                //scheduleLongPressForPointer(pointerId, it)
            } ?: run {
                // Finger moved off any key entirely
                pointerButtonMap.remove(pointerId)
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
        dismissKeyPreview()
        suppressedPointerId = null
        variationPopup?.dismiss()
        variationPopup = null
        variationPopupView = null
        longPressedPointerId = null
        dismissKeyPreview()
        suppressedPointerId = null
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
                qwertyKeyMap.getKeyInfoDefaultJP(key)
            } else {
                qwertyKeyMap.getKeyInfoDefault(key)
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
        val xOffset = -(popupWidth / 2) + (anchorView.width / 2)
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
                keyAtMark
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

    fun updateNumberKeyState(state: Boolean) {
        this.isNumberKeysShow = state
        displayOrHideNumberKeys(state)
    }

    fun updateSymbolKeymapState(state: Boolean) {
        this.isSymbolKeymapShow = state
        if (state) {
            defaultQWERTYButtonsRoman.forEach {
                when (it.id) {
                    R.id.key_a -> {
                        it.topRightChar = '@'
                    }

                    R.id.key_b -> {
                        it.topRightChar = '!'
                    }

                    R.id.key_c -> {
                        it.topRightChar = '\"'
                    }

                    R.id.key_d -> {
                        it.topRightChar = '+'
                    }

                    R.id.key_e -> {
                        it.topRightChar = '3'
                    }

                    R.id.key_f -> {
                        it.topRightChar = '-'
                    }

                    R.id.key_g -> {
                        it.topRightChar = '='
                    }

                    R.id.key_h -> {
                        it.topRightChar = '/'
                    }

                    R.id.key_i -> {
                        it.topRightChar = '8'
                    }

                    R.id.key_j -> {
                        it.topRightChar = '#'
                    }

                    R.id.key_k -> {
                        it.topRightChar = '('
                    }

                    R.id.key_at_mark -> {
                        it.topRightChar = ')'
                    }

                    R.id.key_m -> {
                        it.topRightChar = '…'
                    }

                    R.id.key_n -> {
                        it.topRightChar = '~'
                    }

                    R.id.key_o -> {
                        it.topRightChar = '9'
                    }

                    R.id.key_p -> {
                        it.topRightChar = '1'
                    }

                    R.id.key_q -> {
                        it.topRightChar = '1'
                    }

                    R.id.key_r -> {
                        it.topRightChar = '4'
                    }

                    R.id.key_s -> {
                        it.topRightChar = '*'
                    }

                    R.id.key_t -> {
                        it.topRightChar = '5'
                    }

                    R.id.key_u -> {
                        it.topRightChar = '7'
                    }

                    R.id.key_v -> {
                        it.topRightChar = '?'
                    }

                    R.id.key_w -> {
                        it.topRightChar = '2'
                    }

                    R.id.key_x -> {
                        it.topRightChar = ':'
                    }

                    R.id.key_y -> {
                        it.topRightChar = '6'
                    }

                    R.id.key_z -> {
                        it.topRightChar = '\''
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
                    ConstraintLayout.LayoutParams.TOP,
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
                    ConstraintLayout.LayoutParams.TOP,
                    ConstraintLayout.LayoutParams.PARENT_ID,
                    ConstraintLayout.LayoutParams.TOP
                )
            }
        }

        // Apply the new constraints to the layout
        constraintSet.applyTo(this)
    }

}
