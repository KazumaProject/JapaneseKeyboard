package com.kazumaproject.tabletkey

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.PopupWindow
import androidx.appcompat.widget.AppCompatButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.color.DynamicColors
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.core.data.tablet.TabletCapsLockState
import com.kazumaproject.core.domain.extensions.hide
import com.kazumaproject.core.domain.extensions.layoutXPosition
import com.kazumaproject.core.domain.extensions.layoutYPosition
import com.kazumaproject.core.domain.extensions.setBottomToTopOf
import com.kazumaproject.core.domain.extensions.setEndToStartOf
import com.kazumaproject.core.domain.extensions.setHorizontalWeight
import com.kazumaproject.core.domain.extensions.setLargeUnicodeIcon
import com.kazumaproject.core.domain.extensions.setLargeUnicodeIconScaleX
import com.kazumaproject.core.domain.extensions.setMarginEnd
import com.kazumaproject.core.domain.extensions.setStartToEndOf
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.key.KeyInfo
import com.kazumaproject.core.domain.key.KeyInfo.KeyEEnglish.getOutputChar
import com.kazumaproject.core.domain.key.KeyMap
import com.kazumaproject.core.domain.key.KeyRect
import com.kazumaproject.core.domain.listener.FlickListener
import com.kazumaproject.core.domain.listener.LongPressListener
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.InputMode.ModeEnglish.next
import com.kazumaproject.core.domain.state.PressedKey
import com.kazumaproject.core.ui.effect.Blur
import com.kazumaproject.core.ui.key_window.KeyWindowLayout
import com.kazumaproject.tabletkey.databinding.TabletLayoutBinding
import com.kazumaproject.tabletkey.extenstions.setPopUpWindowBottom
import com.kazumaproject.tabletkey.extenstions.setPopUpWindowCenter
import com.kazumaproject.tabletkey.extenstions.setPopUpWindowFlickBottom
import com.kazumaproject.tabletkey.extenstions.setPopUpWindowFlickLeft
import com.kazumaproject.tabletkey.extenstions.setPopUpWindowFlickRight
import com.kazumaproject.tabletkey.extenstions.setPopUpWindowFlickTap
import com.kazumaproject.tabletkey.extenstions.setPopUpWindowFlickTop
import com.kazumaproject.tabletkey.extenstions.setPopUpWindowLeft
import com.kazumaproject.tabletkey.extenstions.setPopUpWindowRight
import com.kazumaproject.tabletkey.extenstions.setPopUpWindowTop
import com.kazumaproject.tabletkey.extenstions.setTabletKeyTextEnglish
import com.kazumaproject.tabletkey.extenstions.setTabletKeyTextEnglishCaps
import com.kazumaproject.tabletkey.extenstions.setTabletKeyTextJapanese
import com.kazumaproject.tabletkey.extenstions.setTabletKeyTextNumber
import com.kazumaproject.tabletkey.extenstions.setTabletTextDefaultEnglish
import com.kazumaproject.tabletkey.extenstions.setTabletTextFlickBottomJapanese
import com.kazumaproject.tabletkey.extenstions.setTabletTextFlickLeftJapanese
import com.kazumaproject.tabletkey.extenstions.setTabletTextFlickRightJapanese
import com.kazumaproject.tabletkey.extenstions.setTabletTextFlickTopJapanese
import com.kazumaproject.tabletkey.extenstions.setTabletTextTapJapanese
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
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * A custom view that wraps the tablet keyboard layout and provides easy access
 * to all of its key views via binding.
 */
@SuppressLint("ClickableViewAccessibility")
class TabletKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), View.OnTouchListener {

    private val binding: TabletLayoutBinding =
        TabletLayoutBinding.inflate(LayoutInflater.from(context), this)

    val currentInputMode = AtomicReference<InputMode>(InputMode.ModeJapanese)
    private lateinit var pressedKey: PressedKey

    private var flickSensitivity: Int = 100

    // All AppCompatButton keys (all the character keys)
    private val allButtonKeys = listOf(
        binding.key1,
        binding.key2,
        binding.key3,
        binding.key4,
        binding.key5,
        binding.key6,
        binding.key7,
        binding.key8,
        binding.key9,
        binding.key10,
        binding.key11,
        binding.key12,
        binding.key13,
        binding.key14,
        binding.key15,
        binding.key16,
        binding.key17,
        binding.key18,
        binding.key19,
        binding.key20,
        binding.key21,
        binding.key22,
        binding.key23,
        binding.key24,
        binding.key25,
        binding.key26,
        binding.key27,
        binding.key28,
        binding.key29,
        binding.key30,
        binding.key31,
        binding.key32,
        binding.key33,
        binding.key34,
        binding.key35,
        binding.key36,
        binding.key37,
        binding.key38,
        binding.key39,
        binding.key40,
        binding.key41,
        binding.key42,
        binding.key43,
        binding.key44,
        binding.key45,
        binding.key46,
        binding.key47,
        binding.key48,
        binding.key49,
        binding.key50,
        binding.key51,
        binding.key52,
        binding.key53,
        binding.key54,
        binding.key55
    )

    // All AppCompatImageButton keys (side and utility keys)
    private val allImageButtonKeys = listOf(
        binding.keyKigou,
        binding.keyPrevious,
        binding.keySwitchKeyMode,
        binding.keyLeftCursor,
        binding.keyRightCursor,
        binding.keyDelete,
        binding.keySpace,
        binding.keyEnter
    )

    private val listKeys: Map<Key, Any> = mapOf(
        // あ row
        Key.KeyA to binding.key51,
        Key.KeyI to binding.key52,
        Key.KeyU to binding.key53,
        Key.KeyE to binding.key54,
        Key.KeyO to binding.key55,

        // か row
        Key.KeyKA to binding.key46,
        Key.KeyKI to binding.key47,
        Key.KeyKU to binding.key48,
        Key.KeyKE to binding.key49,
        Key.KeyKO to binding.key50,

        // さ row
        Key.KeySA to binding.key41,
        Key.KeySHI to binding.key42,
        Key.KeySU to binding.key43,
        Key.KeySE to binding.key44,
        Key.KeySO to binding.key45,

        // た row
        Key.KeyTA to binding.key36,
        Key.KeyCHI to binding.key37,
        Key.KeyTSU to binding.key38,
        Key.KeyTE to binding.key39,
        Key.KeyTO to binding.key40,

        // な row
        Key.KeyNA to binding.key31,
        Key.KeyNI to binding.key32,
        Key.KeyNU to binding.key33,
        Key.KeyNE to binding.key34,
        Key.KeyNO to binding.key35,

        // は row
        Key.KeyHA to binding.key26,
        Key.KeyHI to binding.key27,
        Key.KeyFU to binding.key28,
        Key.KeyHE to binding.key29,
        Key.KeyHO to binding.key30,

        // ま row
        Key.KeyMA to binding.key21,
        Key.KeyMI to binding.key22,
        Key.KeyMU to binding.key23,
        Key.KeyME to binding.key24,
        Key.KeyMO to binding.key25,

        // や row
        Key.KeyYA to binding.key16,
        Key.KeySPACE1 to binding.key17,
        Key.KeyYU to binding.key18,
        Key.KeySPACE2 to binding.key19,
        Key.KeyYO to binding.key20,

        // ら row
        Key.KeyRA to binding.key11,
        Key.KeyRI to binding.key12,
        Key.KeyRU to binding.key13,
        Key.KeyRE to binding.key14,
        Key.KeyRO to binding.key15,

        // わ row
        Key.KeyWA to binding.key6,
        Key.KeyWO to binding.key7,
        Key.KeyN to binding.key8,
        Key.KeyMinus to binding.key9,

        // symbols & modifiers
        Key.KeyDakutenSmall to binding.key10,
        Key.KeyKagikakko to binding.key1,
        Key.KeyQuestion to binding.key2,
        Key.KeyCaution to binding.key3,
        Key.KeyTouten to binding.key4,
        Key.KeyKuten to binding.key5,

        // side keys
        Key.SideKeySymbol to binding.keyKigou,
        Key.SideKeyPreviousChar to binding.keyPrevious,
        Key.SideKeyInputMode to binding.keySwitchKeyMode,
        Key.SideKeyCursorLeft to binding.keyLeftCursor,
        Key.SideKeyCursorRight to binding.keyRightCursor,
        Key.SideKeyDelete to binding.keyDelete,
        Key.SideKeySpace to binding.keySpace,
        Key.SideKeyEnter to binding.keyEnter,
    )

    private var keyMap: KeyMap
    private var flickListener: FlickListener? = null
    private var longPressListener: LongPressListener? = null

    private var longPressJob: Job? = null
    private var isLongPressed = false

    private lateinit var popupWindowActive: PopupWindow
    private lateinit var bubbleViewActive: KeyWindowLayout
    private lateinit var popTextActive: MaterialTextView
    private lateinit var popupWindowLeft: PopupWindow
    private lateinit var bubbleViewLeft: KeyWindowLayout
    private lateinit var popTextLeft: MaterialTextView
    private lateinit var popupWindowTop: PopupWindow
    private lateinit var bubbleViewTop: KeyWindowLayout
    private lateinit var popTextTop: MaterialTextView
    private lateinit var popupWindowRight: PopupWindow
    private lateinit var bubbleViewRight: KeyWindowLayout
    private lateinit var popTextRight: MaterialTextView
    private lateinit var popupWindowBottom: PopupWindow
    private lateinit var bubbleViewBottom: KeyWindowLayout
    private lateinit var popTextBottom: MaterialTextView
    private lateinit var popupWindowCenter: PopupWindow
    private lateinit var bubbleViewCenter: KeyWindowLayout
    private lateinit var popTextCenter: MaterialTextView

    private val _tabletCapsLockState = MutableStateFlow(TabletCapsLockState())
    private val tabletCapsLockState: StateFlow<TabletCapsLockState> =
        _tabletCapsLockState.asStateFlow()
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var isDynamicColorsEnable = false

    init {
        (allButtonKeys + allImageButtonKeys).forEach { it.setOnTouchListener(this) }
        keyMap = KeyMap()
        declarePopupWindows()
        handleCurrentInputModeSwitch(inputMode = currentInputMode.get())

        setMaterialYouTheme()

        uiScope.launch {
            tabletCapsLockState.collectLatest { state ->
                Log.d("tabletCapsLockState", "$state")
                when (currentInputMode.get()) {
                    InputMode.ModeJapanese -> {

                    }

                    InputMode.ModeEnglish -> {
                        binding.apply {
                            updateKeyStylesEnglish(state)
                        }
                    }

                    InputMode.ModeNumber -> {
                        binding.apply {
                            setKeysInNumberText(state.zenkakuOn)
                        }
                    }
                }
            }
        }
    }

    private fun setMaterialYouTheme() {
        isDynamicColorsEnable = DynamicColors.isDynamicColorAvailable()
        if (isDynamicColorsEnable) {
            allButtonKeys.forEach {
                it.setBackgroundDrawable(
                    ContextCompat.getDrawable(
                        this.context,
                        com.kazumaproject.core.R.drawable.tablet_keyboard_center_bg_material
                    )
                )
            }
            allImageButtonKeys.forEach {
                it.setBackgroundDrawable(
                    ContextCompat.getDrawable(
                        this.context,
                        com.kazumaproject.core.R.drawable.ten_keys_side_bg_material
                    )
                )
            }
            return
        }
    }

    private fun updateKeyStylesEnglish(state: TabletCapsLockState) {
        if (state.zenkakuOn) {
            when {
                state.capsLockOn && state.shiftOn -> {
                    setKeysInEnglishCapsOnText(true)
                }

                !state.capsLockOn && state.shiftOn -> {
                    setKeysInEnglishShiftOnText(true)
                }

                state.capsLockOn && !state.shiftOn -> {
                    setKeysInEnglishCapsOnText(true)
                }

                else -> {
                    setKeysInEnglishText(true)
                }
            }
        } else {
            when {
                state.capsLockOn -> setKeysInEnglishCapsOnText(false)
                state.shiftOn -> setKeysInEnglishShiftOnText(false)
                else -> setKeysInEnglishText(false)
            }
        }
    }

    private fun updateKeyStylesNumber(state: TabletCapsLockState) {
        setKeysInNumberText(state.zenkakuOn)
    }

    private fun toggleShift() {
        _tabletCapsLockState.update {
            it.copy(
                shiftOn = !it.shiftOn, capsLockOn = it.capsLockOn, zenkakuOn = it.zenkakuOn
            )
        }
    }


    private fun enableCapsLock() {
        _tabletCapsLockState.update {
            it.copy(
                capsLockOn = true, shiftOn = false, zenkakuOn = it.zenkakuOn
            )
        }
    }

    private fun toggleZenkaku() {
        _tabletCapsLockState.update {
            it.copy(
                shiftOn = it.shiftOn, capsLockOn = it.capsLockOn, zenkakuOn = !it.zenkakuOn
            )
        }
    }


    private fun clearShiftCaps() {
        _tabletCapsLockState.value = TabletCapsLockState()
    }

    private fun clearShiftCapsWithoutZenkaku() {
        _tabletCapsLockState.update {
            it.copy(
                shiftOn = false, capsLockOn = false, zenkakuOn = it.zenkakuOn
            )
        }
    }

    private fun clearShiftCapsOnlyZenkaku() {
        _tabletCapsLockState.update {
            it.copy(
                shiftOn = it.shiftOn, capsLockOn = it.capsLockOn, zenkakuOn = false
            )
        }
    }

    private var skipNextTouches = false

    private val gestureDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                return if (currentInputMode.get() == InputMode.ModeEnglish) {
                    val key = pressedKeyByMotionEvent(e, 0)
                    if (key == Key.KeyKuten) {
                        hideAllPopWindow()
                        enableCapsLock()
                        skipNextTouches = true
                    }
                    true
                } else {
                    false
                }
            }
        })

    @SuppressLint("InflateParams")
    private fun declarePopupWindows() {
        isDynamicColorsEnable = DynamicColors.isDynamicColorAvailable()
        val mPopWindowActive = PopupWindow(context)
        val popupViewActive =
            if (isDynamicColorsEnable) LayoutInflater.from(context)
                .inflate(R.layout.popup_layout_active_material, null) else LayoutInflater.from(
                context
            ).inflate(R.layout.popup_layout_active, null)
        mPopWindowActive.contentView = popupViewActive

        val mPopWindowLeft = PopupWindow(context)
        val mPopWindowTop = PopupWindow(context)
        val mPopWindowRight = PopupWindow(context)
        val mPopWindowBottom = PopupWindow(context)
        val mPopWindowCenter = PopupWindow(context)

        val popupViewLeft = LayoutInflater.from(context).inflate(R.layout.popup_layout, null)
        val popupViewTop = LayoutInflater.from(context).inflate(R.layout.popup_layout, null)
        val popupViewRight = LayoutInflater.from(context).inflate(R.layout.popup_layout, null)
        val popupViewBottom = LayoutInflater.from(context).inflate(R.layout.popup_layout, null)
        val popupViewCenter = LayoutInflater.from(context).inflate(R.layout.popup_layout, null)

        mPopWindowLeft.contentView = popupViewLeft
        mPopWindowTop.contentView = popupViewTop
        mPopWindowRight.contentView = popupViewRight
        mPopWindowBottom.contentView = popupViewBottom
        mPopWindowCenter.contentView = popupViewCenter

        popupWindowActive = mPopWindowActive
        popupWindowLeft = mPopWindowLeft
        popupWindowTop = mPopWindowTop
        popupWindowRight = mPopWindowRight
        popupWindowBottom = mPopWindowBottom
        popupWindowCenter = mPopWindowCenter

        bubbleViewActive = mPopWindowActive.contentView.findViewById(R.id.bubble_layout_active)
        popTextActive = mPopWindowActive.contentView.findViewById(R.id.popup_text_active)
        bubbleViewLeft = mPopWindowLeft.contentView.findViewById(R.id.bubble_layout)
        popTextLeft = mPopWindowLeft.contentView.findViewById(R.id.popup_text)
        bubbleViewTop = mPopWindowTop.contentView.findViewById(R.id.bubble_layout)
        popTextTop = mPopWindowTop.contentView.findViewById(R.id.popup_text)
        bubbleViewRight = mPopWindowRight.contentView.findViewById(R.id.bubble_layout)
        popTextRight = mPopWindowRight.contentView.findViewById(R.id.popup_text)
        bubbleViewBottom = mPopWindowBottom.contentView.findViewById(R.id.bubble_layout)
        popTextBottom = mPopWindowBottom.contentView.findViewById(R.id.popup_text)
        bubbleViewCenter = mPopWindowCenter.contentView.findViewById(R.id.bubble_layout)
        popTextCenter = mPopWindowCenter.contentView.findViewById(R.id.popup_text)
    }

    fun setOnFlickListener(flickListener: FlickListener) {
        this.flickListener = flickListener
    }

    fun setOnLongPressListener(longPressListener: LongPressListener) {
        this.longPressListener = longPressListener
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (v != null && event != null) {
            if (this.visibility != View.VISIBLE) {
                return false
            }
            if (skipNextTouches) {
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
                    skipNextTouches = false
                }
                return true
            }
            if (currentInputMode.get() == InputMode.ModeEnglish) {
                gestureDetector.onTouchEvent(event)
            }
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    val key = pressedKeyByMotionEvent(event, 0)
                    flickListener?.onFlick(
                        gestureType = GestureType.Down, key = key, char = null
                    )
                    pressedKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        PressedKey(
                            key = key,
                            pointer = 0,
                            initialX = event.getRawX(event.actionIndex),
                            initialY = event.getRawY(event.actionIndex),
                        )
                    } else {
                        PressedKey(
                            key = key,
                            pointer = 0,
                            initialX = event.getX(event.actionIndex),
                            initialY = event.getY(event.actionIndex),
                        )
                    }
                    setKeyPressed()
                    if (currentInputMode.get() == InputMode.ModeEnglish &&
                        key == Key.KeyKuten
                    ) {
                        toggleShift()
                    } else if (currentInputMode.get() == InputMode.ModeEnglish &&
                        pressedKey.key == Key.KeyKuten && tabletCapsLockState.value.capsLockOn
                    ) {
                        clearShiftCaps()
                    } else if (currentInputMode.get() == InputMode.ModeEnglish &&
                        pressedKey.key == Key.KeyO
                    ) {
                        toggleZenkaku()
                    } else if (currentInputMode.get() == InputMode.ModeEnglish &&
                        pressedKey.key == Key.KeyKO
                    ) {
                        toggleZenkaku()
                    }
                    if (currentInputMode.get() == InputMode.ModeEnglish &&
                        key != Key.SideKeyDelete &&
                        key != Key.SideKeyCursorRight &&
                        key != Key.SideKeyCursorLeft
                    ) {
                        return false
                    }

                    if (currentInputMode.get() == InputMode.ModeNumber &&
                        key == Key.KeyO
                    ) {
                        toggleZenkaku()
                    } else if (
                        currentInputMode.get() == InputMode.ModeNumber &&
                        key == Key.KeyKO
                    ) {
                        toggleZenkaku()
                    }

                    if (currentInputMode.get() == InputMode.ModeNumber &&
                        key != Key.SideKeyDelete &&
                        key != Key.SideKeyCursorRight &&
                        key != Key.SideKeyCursorLeft
                    ) {
                        return false
                    }
                    Log.d("ACTION_DOWN: ", "${tabletCapsLockState.value}")
                    longPressJob = CoroutineScope(Dispatchers.Main).launch {
                        delay(ViewConfiguration.getLongPressTimeout().toLong())
                        if (pressedKey.key != Key.NotSelected) {
                            longPressListener?.onLongPress(pressedKey.key)
                            isLongPressed = true
                            onLongPressed()
                        }
                    }
                    return false
                }

                MotionEvent.ACTION_UP -> {
                    resetLongPressAction()
                    if (pressedKey.pointer == event.getPointerId(event.actionIndex)) {
                        val gestureType = getGestureType(event)
                        val keyInfo = currentInputMode.get().next(
                            keyMap = keyMap, key = pressedKey.key, isTablet = true
                        )
                        if (keyInfo == KeyInfo.Null) {
                            flickListener?.onFlick(
                                gestureType = gestureType, key = pressedKey.key, char = null
                            )
                            if (pressedKey.key == Key.SideKeyInputMode) {
                                handleClickInputModeSwitch()
                            }
                        } else if (keyInfo is KeyInfo.KeyTapFlickInfo) {
                            when (gestureType) {
                                GestureType.Null -> {}
                                GestureType.Down -> {}
                                GestureType.Tap -> {
                                    when (currentInputMode.get()) {
                                        InputMode.ModeJapanese -> {
                                            flickListener?.onFlick(
                                                gestureType = gestureType,
                                                key = pressedKey.key,
                                                char = keyInfo.tap,
                                            )
                                        }

                                        InputMode.ModeEnglish -> {
                                            val capState = tabletCapsLockState.value
                                            val isZenkaku = capState.zenkakuOn
                                            val outputChar = keyInfo.getOutputChar(capState)

                                            if (capState.shiftOn && pressedKey.key !in setOf(
                                                    Key.KeyKuten, Key.KeyO, Key.KeyKO
                                                )
                                            ) {
                                                toggleShift()
                                            }

                                            if (pressedKey.key == Key.KeyKuten && capState.capsLockOn) {
                                                clearShiftCapsWithoutZenkaku()
                                            }

                                            if (pressedKey.key == Key.KeyKO && isZenkaku) {
                                                clearShiftCapsOnlyZenkaku()
                                            }
                                            Log.d("capState", "after: $capState")
                                            flickListener?.onFlick(
                                                gestureType = gestureType,
                                                key = pressedKey.key,
                                                char = outputChar
                                            )
                                        }

                                        InputMode.ModeNumber -> {
                                            val capState = tabletCapsLockState.value
                                            val outputChar = keyInfo.getOutputChar(capState)
                                            if (pressedKey.key == Key.KeyKuten) {
                                                flickListener?.onFlick(
                                                    gestureType = gestureType,
                                                    key = Key.KeyDakutenSmall,
                                                    char = outputChar,
                                                )
                                            } else {
                                                flickListener?.onFlick(
                                                    gestureType = gestureType,
                                                    key = pressedKey.key,
                                                    char = outputChar,
                                                )
                                            }
                                        }
                                    }

                                }

                                GestureType.FlickLeft -> {
                                    if (currentInputMode.get() == InputMode.ModeEnglish) return false
                                    flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.flickLeft,
                                    )
                                }

                                GestureType.FlickTop -> {
                                    if (currentInputMode.get() == InputMode.ModeEnglish) return false
                                    flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.flickTop,
                                    )
                                }

                                GestureType.FlickRight -> {
                                    if (currentInputMode.get() == InputMode.ModeEnglish) return false
                                    flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.flickRight,
                                    )
                                }

                                GestureType.FlickBottom -> {
                                    if (currentInputMode.get() == InputMode.ModeEnglish) return false
                                    flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.flickBottom,
                                    )
                                }
                            }
                        }
                    }
                    resetAllKeys()
                    popupWindowActive.hide()
                    val button = getButtonFromKey(pressedKey.key)
                    button?.let {
                        if (it is AppCompatButton) {
                            when (currentInputMode.get()) {
                                InputMode.ModeJapanese -> {
                                    it.setTabletKeyTextJapanese(it.id)
                                }

                                InputMode.ModeEnglish -> {
                                    if (tabletCapsLockState.value.capsLockOn) {
                                        it.setTabletKeyTextEnglishCaps(it.id)
                                    } else {
                                        it.setTabletKeyTextEnglish(it.id)
                                    }
                                }

                                InputMode.ModeNumber -> {
                                    it.setTabletKeyTextNumber(it.id)
                                }
                            }
                        }
                    }
                    return false
                }

                MotionEvent.ACTION_MOVE -> {
                    val gestureType =
                        if (event.pointerCount == 1) getGestureType(event, 0) else getGestureType(
                            event, pressedKey.pointer
                        )
                    when (gestureType) {
                        GestureType.Null -> {}
                        GestureType.Down -> {}
                        GestureType.Tap -> {
                            setTapInActionMove()
                        }

                        GestureType.FlickLeft, GestureType.FlickTop, GestureType.FlickRight, GestureType.FlickBottom -> {
                            setFlickInActionMove(gestureType)
                        }
                    }
                    return false
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (isLongPressed) {
                        hideAllPopWindow()
                        Blur.removeBlurEffect(this)
                    }
                    popupWindowActive.hide()
                    longPressJob?.cancel()
                    if (event.pointerCount == 2) {
                        isLongPressed = false
                        val pointer = event.getPointerId(event.actionIndex)
                        val key = pressedKeyByMotionEvent(event, pointer)
                        val gestureType2 = getGestureType(event, if (pointer == 0) 1 else 0)
                        val keyInfo = currentInputMode.get()
                            .next(keyMap = keyMap, key = pressedKey.key, isTablet = true)
                        if (keyInfo == KeyInfo.Null) {
                            flickListener?.onFlick(
                                gestureType = gestureType2, key = pressedKey.key, char = null
                            )
                        } else if (keyInfo is KeyInfo.KeyTapFlickInfo) {
                            when (gestureType2) {
                                GestureType.Null -> {}
                                GestureType.Down -> {}
                                GestureType.Tap -> {
                                    val capState = tabletCapsLockState.value
                                    val outputChar = keyInfo.getOutputChar(capState)
                                    flickListener?.onFlick(
                                        gestureType = gestureType2,
                                        key = pressedKey.key,
                                        char = outputChar,
                                    )
                                    val button = getButtonFromKey(pressedKey.key)
                                    if (currentInputMode.get() == InputMode.ModeEnglish &&
                                        tabletCapsLockState.value.capsLockOn
                                    ) {
                                        button?.let {
                                            if (it is AppCompatButton) {
                                                it.setTabletKeyTextEnglishCaps(it.id)
                                            }
                                        }
                                    } else if (currentInputMode.get() == InputMode.ModeEnglish &&
                                        tabletCapsLockState.value.shiftOn
                                    ) {
                                        toggleShift()
                                    } else {
                                        button?.let {
                                            if (it is AppCompatButton) {
                                                if (it == binding.key10) return false
                                                when (currentInputMode.get()) {
                                                    InputMode.ModeJapanese -> {
                                                        it.setTabletKeyTextJapanese(it.id)
                                                    }

                                                    InputMode.ModeEnglish -> {
                                                        it.setTabletKeyTextEnglish(it.id)
                                                    }

                                                    InputMode.ModeNumber -> {
                                                        it.setTabletKeyTextNumber(it.id)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                GestureType.FlickLeft, GestureType.FlickTop, GestureType.FlickRight, GestureType.FlickBottom -> {
                                    setFlickActionPointerDown(keyInfo, gestureType2)
                                }
                            }
                        }
                        pressedKey = pressedKey.copy(
                            key = key,
                            pointer = pointer,
                            initialX = if (pointer == 0) if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                event.getRawX(0)
                            } else {
                                event.getX(0)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                event.getRawX(1)
                            } else {
                                event.getX(1)
                            },
                            initialY = if (pointer == 0) if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                event.getRawY(0)
                            } else {
                                event.getY(0)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                event.getRawY(1)
                            } else {
                                event.getY(1)
                            },
                        )
                        setKeyPressed()
                        longPressJob = CoroutineScope(Dispatchers.Main).launch {
                            delay(ViewConfiguration.getLongPressTimeout().toLong())
                            if (pressedKey.key != Key.NotSelected) {
                                longPressListener?.onLongPress(pressedKey.key)
                                isLongPressed = true
                                onLongPressed()
                            }
                        }
                    }
                    return false
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.pointerCount == 2) {
                        if (pressedKey.pointer == event.getPointerId(event.actionIndex)) {
                            resetLongPressAction()
                            val gestureType =
                                getGestureType(event, event.getPointerId(event.actionIndex))
                            val keyInfo = currentInputMode.get()
                                .next(keyMap = keyMap, key = pressedKey.key, isTablet = true)
                            if (keyInfo == KeyInfo.Null) {
                                flickListener?.onFlick(
                                    gestureType = gestureType, key = pressedKey.key, char = null
                                )
                                if (pressedKey.key == Key.SideKeyInputMode) {
                                    handleClickInputModeSwitch()
                                }
                            } else if (keyInfo is KeyInfo.KeyTapFlickInfo) {
                                val capState = tabletCapsLockState.value
                                val outputChar = keyInfo.getOutputChar(capState)
                                when (gestureType) {
                                    GestureType.Null -> {}
                                    GestureType.Down -> {}
                                    GestureType.Tap, GestureType.FlickLeft, GestureType.FlickTop, GestureType.FlickRight, GestureType.FlickBottom -> flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = outputChar,
                                    )
                                }
                            }
                            val button = getButtonFromKey(pressedKey.key)
                            if (currentInputMode.get() == InputMode.ModeEnglish && tabletCapsLockState.value.capsLockOn) {
                                button?.let {
                                    if (it is AppCompatButton) {
                                        it.setTabletKeyTextEnglishCaps(it.id)
                                    }
                                }
                            } else {
                                button?.let {
                                    if (it is AppCompatButton) {
                                        if (it == binding.key10) return false
                                        it.isPressed = false
                                        when (currentInputMode.get()) {
                                            InputMode.ModeJapanese -> {
                                                it.setTabletKeyTextJapanese(it.id)
                                            }

                                            InputMode.ModeEnglish -> {
                                                it.setTabletKeyTextEnglish(it.id)
                                            }

                                            InputMode.ModeNumber -> {
                                                it.setTabletKeyTextNumber(it.id)
                                            }
                                        }
                                    }
                                }
                            }
                            pressedKey = pressedKey.copy(
                                key = Key.NotSelected,
                            )
                            popupWindowActive.hide()
                        }
                        return false

                    }
                    return false
                }

                else -> {
                    return false
                }
            }
        }
        return false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
        uiScope.cancel()
    }

    private fun release() {
        flickListener = null
        longPressListener = null
        longPressJob?.cancel()
        longPressJob = null
    }

    private fun getGestureType(event: MotionEvent, pointer: Int = 0): GestureType {
        val finalX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            event.getRawX(pointer)
        } else {
            event.getX(pointer)
        }
        val finalY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            event.getRawY(pointer)
        } else {
            event.getY(pointer)
        }
        val distanceX = finalX - pressedKey.initialX
        val distanceY = finalY - pressedKey.initialY
        return when {
            abs(distanceX) < flickSensitivity && abs(distanceY) < flickSensitivity -> GestureType.Tap
            abs(distanceX) > abs(distanceY) && pressedKey.initialX >= finalX -> GestureType.FlickLeft
            abs(distanceX) <= abs(distanceY) && pressedKey.initialY >= finalY -> GestureType.FlickTop
            abs(distanceX) > abs(distanceY) && pressedKey.initialX < finalX -> GestureType.FlickRight
            abs(distanceX) <= abs(distanceY) && pressedKey.initialY < finalY -> GestureType.FlickBottom
            else -> GestureType.Null
        }
    }

    private fun setKeyPressed() {
        when (pressedKey.key) {
            // --- あ row ---
            Key.KeyA -> {
                resetAllKeys()
                binding.key51.isPressed = true
            }

            Key.KeyI -> {
                resetAllKeys()
                binding.key52.isPressed = true
            }

            Key.KeyU -> {
                resetAllKeys()
                binding.key53.isPressed = true
            }

            Key.KeyE -> {
                resetAllKeys()
                binding.key54.isPressed = true
            }

            Key.KeyO -> {
                resetAllKeys()
                binding.key55.isPressed = true
            }

            // --- か row ---
            Key.KeyKA -> {
                resetAllKeys()
                binding.key46.isPressed = true
            }

            Key.KeyKI -> {
                resetAllKeys()
                binding.key47.isPressed = true
            }

            Key.KeyKU -> {
                resetAllKeys()
                binding.key48.isPressed = true
            }

            Key.KeyKE -> {
                resetAllKeys()
                binding.key49.isPressed = true
            }

            Key.KeyKO -> {
                resetAllKeys()
                binding.key50.isPressed = true
            }

            // --- さ row ---
            Key.KeySA -> {
                resetAllKeys()
                binding.key41.isPressed = true
            }

            Key.KeySHI -> {
                resetAllKeys()
                binding.key42.isPressed = true
            }

            Key.KeySU -> {
                resetAllKeys()
                binding.key43.isPressed = true
            }

            Key.KeySE -> {
                resetAllKeys()
                binding.key44.isPressed = true
            }

            Key.KeySO -> {
                resetAllKeys()
                binding.key45.isPressed = true
            }

            // --- た row ---
            Key.KeyTA -> {
                resetAllKeys()
                binding.key36.isPressed = true
            }

            Key.KeyCHI -> {
                resetAllKeys()
                binding.key37.isPressed = true
            }

            Key.KeyTSU -> {
                resetAllKeys()
                binding.key38.isPressed = true
            }

            Key.KeyTE -> {
                resetAllKeys()
                binding.key39.isPressed = true
            }

            Key.KeyTO -> {
                resetAllKeys()
                binding.key40.isPressed = true
            }

            // --- な row ---
            Key.KeyNA -> {
                resetAllKeys()
                binding.key31.isPressed = true
            }

            Key.KeyNI -> {
                resetAllKeys()
                binding.key32.isPressed = true
            }

            Key.KeyNU -> {
                resetAllKeys()
                binding.key33.isPressed = true
            }

            Key.KeyNE -> {
                resetAllKeys()
                binding.key34.isPressed = true
            }

            Key.KeyNO -> {
                resetAllKeys()
                binding.key35.isPressed = true
            }

            // --- は row ---
            Key.KeyHA -> {
                resetAllKeys()
                binding.key26.isPressed = true
            }

            Key.KeyHI -> {
                resetAllKeys()
                binding.key27.isPressed = true
            }

            Key.KeyFU -> {
                resetAllKeys()
                binding.key28.isPressed = true
            }

            Key.KeyHE -> {
                resetAllKeys()
                binding.key29.isPressed = true
            }

            Key.KeyHO -> {
                resetAllKeys()
                binding.key30.isPressed = true
            }

            // --- ま row ---
            Key.KeyMA -> {
                resetAllKeys()
                binding.key21.isPressed = true
            }

            Key.KeyMI -> {
                resetAllKeys()
                binding.key22.isPressed = true
            }

            Key.KeyMU -> {
                resetAllKeys()
                binding.key23.isPressed = true
            }

            Key.KeyME -> {
                resetAllKeys()
                binding.key24.isPressed = true
            }

            Key.KeyMO -> {
                resetAllKeys()
                binding.key25.isPressed = true
            }

            // --- や row ---
            Key.KeyYA -> {
                resetAllKeys()
                binding.key16.isPressed = true
            }

            Key.KeyYU -> {
                resetAllKeys()
                binding.key18.isPressed = true
            }

            Key.KeyYO -> {
                resetAllKeys()
                binding.key20.isPressed = true
            }

            Key.KeySPACE1 -> {
                resetAllKeys()
                binding.key17.isPressed = true
            }

            Key.KeySPACE2 -> {
                resetAllKeys()
                binding.key19.isPressed = true
            }

            // --- ら row ---
            Key.KeyRA -> {
                resetAllKeys()
                binding.key11.isPressed = true
            }

            Key.KeyRI -> {
                resetAllKeys()
                binding.key12.isPressed = true
            }

            Key.KeyRU -> {
                resetAllKeys()
                binding.key13.isPressed = true
            }

            Key.KeyRE -> {
                resetAllKeys()
                binding.key14.isPressed = true
            }

            Key.KeyRO -> {
                resetAllKeys()
                binding.key15.isPressed = true
            }

            // --- わ row + ん + minus ---
            Key.KeyWA -> {
                resetAllKeys()
                binding.key6.isPressed = true
            }

            Key.KeyWO -> {
                resetAllKeys()
                binding.key7.isPressed = true
            }

            Key.KeyN -> {
                resetAllKeys()
                binding.key8.isPressed = true
            }

            Key.KeyMinus -> {
                resetAllKeys()
                binding.key9.isPressed = true
            }

            // --- Modifiers & punctuation ---
            Key.KeyDakutenSmall -> {
                resetAllKeys()
                binding.key10.isPressed = true
            }

            Key.KeyKagikakko -> {
                resetAllKeys()
                binding.key1.isPressed = true
            }

            Key.KeyQuestion -> {
                resetAllKeys()
                binding.key2.isPressed = true
            }

            Key.KeyCaution -> {
                resetAllKeys()
                binding.key3.isPressed = true
            }

            Key.KeyTouten -> {
                resetAllKeys()
                binding.key4.isPressed = true
            }

            Key.KeyKuten -> {
                resetAllKeys()
                binding.key5.isPressed = true
            }

            // --- Side-row keys ---
            Key.SideKeySymbol -> {
                resetAllKeys()
                binding.keyKigou.isPressed = true
            }

            Key.SideKeyPreviousChar -> {
                resetAllKeys()
                binding.keyPrevious.isPressed = true
            }

            Key.SideKeyInputMode -> {
                resetAllKeys()
                binding.keySwitchKeyMode.isPressed = true
            }

            Key.SideKeyCursorLeft -> {
                resetAllKeys()
                binding.keyLeftCursor.isPressed = true
            }

            Key.SideKeyCursorRight -> {
                resetAllKeys()
                binding.keyRightCursor.isPressed = true
            }

            Key.SideKeyDelete -> {
                resetAllKeys()
                binding.keyDelete.isPressed = true
            }

            Key.SideKeySpace -> {
                resetAllKeys()
                binding.keySpace.isPressed = true
            }

            Key.SideKeyEnter -> {
                resetAllKeys()
                binding.keyEnter.isPressed = true
            }

            Key.NotSelected -> {
                // no key pressed
            }

            Key.KeyDakutenSmall -> {}
            Key.KeyKutouten -> {}
            Key.SideKeyInputMode -> {}
            Key.SideKeyPreviousChar -> {}
        }
    }

    private fun resetAllKeys() {
        // あ row
        binding.key51.isPressed = false
        binding.key52.isPressed = false
        binding.key53.isPressed = false
        binding.key54.isPressed = false
        binding.key55.isPressed = false

        // か row
        binding.key46.isPressed = false
        binding.key47.isPressed = false
        binding.key48.isPressed = false
        binding.key49.isPressed = false
        binding.key50.isPressed = false

        // さ row
        binding.key41.isPressed = false
        binding.key42.isPressed = false
        binding.key43.isPressed = false
        binding.key44.isPressed = false
        binding.key45.isPressed = false

        // た row
        binding.key36.isPressed = false
        binding.key37.isPressed = false
        binding.key38.isPressed = false
        binding.key39.isPressed = false
        binding.key40.isPressed = false

        // な row
        binding.key31.isPressed = false
        binding.key32.isPressed = false
        binding.key33.isPressed = false
        binding.key34.isPressed = false
        binding.key35.isPressed = false

        // は row
        binding.key26.isPressed = false
        binding.key27.isPressed = false
        binding.key28.isPressed = false
        binding.key29.isPressed = false
        binding.key30.isPressed = false

        // ま row
        binding.key21.isPressed = false
        binding.key22.isPressed = false
        binding.key23.isPressed = false
        binding.key24.isPressed = false
        binding.key25.isPressed = false

        // や row
        binding.key16.isPressed = false
        binding.key18.isPressed = false
        binding.key20.isPressed = false

        // ら row
        binding.key11.isPressed = false
        binding.key12.isPressed = false
        binding.key13.isPressed = false
        binding.key14.isPressed = false
        binding.key15.isPressed = false

        // わ row + ん + minus
        binding.key6.isPressed = false
        binding.key7.isPressed = false
        binding.key8.isPressed = false
        binding.key9.isPressed = false

        // Modifiers & punctuation
        binding.key10.isPressed = false
        binding.key1.isPressed = false
        binding.key2.isPressed = false
        binding.key3.isPressed = false
        binding.key4.isPressed = false
        binding.key5.isPressed = false

        // Side-row keys
        binding.keyKigou.isPressed = false
        binding.keyPrevious.isPressed = false
        binding.keySwitchKeyMode.isPressed = false
        binding.keyLeftCursor.isPressed = false
        binding.keyRightCursor.isPressed = false
        binding.keyDelete.isPressed = false
        binding.keySpace.isPressed = false
        binding.keyEnter.isPressed = false
    }

    private fun buildKeyRects() = listOf(
        // ---- Side Keys ----
        KeyRect(
            Key.SideKeySymbol,
            binding.keyKigou.layoutXPosition(),
            binding.keyKigou.layoutYPosition(),
            binding.keyKigou.layoutXPosition() + binding.keyKigou.width,
            binding.keyKigou.layoutYPosition() + binding.keyKigou.height
        ),
        KeyRect(
            Key.SideKeyPreviousChar,
            binding.keyPrevious.layoutXPosition(),
            binding.keyPrevious.layoutYPosition(),
            binding.keyPrevious.layoutXPosition() + binding.keyPrevious.width,
            binding.keyPrevious.layoutYPosition() + binding.keyPrevious.height
        ),
        KeyRect(
            Key.SideKeyInputMode,
            binding.keySwitchKeyMode.layoutXPosition(),
            binding.keySwitchKeyMode.layoutYPosition(),
            binding.keySwitchKeyMode.layoutXPosition() + binding.keySwitchKeyMode.width,
            binding.keySwitchKeyMode.layoutYPosition() + binding.keySwitchKeyMode.height
        ),
        KeyRect(
            Key.SideKeyCursorLeft,
            binding.keyLeftCursor.layoutXPosition(),
            binding.keyLeftCursor.layoutYPosition(),
            binding.keyLeftCursor.layoutXPosition() + binding.keyLeftCursor.width,
            binding.keyLeftCursor.layoutYPosition() + binding.keyLeftCursor.height
        ),
        KeyRect(
            Key.SideKeyCursorRight,
            binding.keyRightCursor.layoutXPosition(),
            binding.keyRightCursor.layoutYPosition(),
            binding.keyRightCursor.layoutXPosition() + binding.keyRightCursor.width,
            binding.keyRightCursor.layoutYPosition() + binding.keyRightCursor.height
        ),
        KeyRect(
            Key.SideKeyDelete,
            binding.keyDelete.layoutXPosition(),
            binding.keyDelete.layoutYPosition(),
            binding.keyDelete.layoutXPosition() + binding.keyDelete.width,
            binding.keyDelete.layoutYPosition() + binding.keyDelete.height
        ),
        KeyRect(
            Key.SideKeySpace,
            binding.keySpace.layoutXPosition(),
            binding.keySpace.layoutYPosition(),
            binding.keySpace.layoutXPosition() + binding.keySpace.width,
            binding.keySpace.layoutYPosition() + binding.keySpace.height
        ),
        KeyRect(
            Key.SideKeyEnter,
            binding.keyEnter.layoutXPosition(),
            binding.keyEnter.layoutYPosition(),
            binding.keyEnter.layoutXPosition() + binding.keyEnter.width,
            binding.keyEnter.layoutYPosition() + binding.keyEnter.height
        ),

        // ---- Character Keys ----
        KeyRect(
            Key.KeyKagikakko,
            binding.key1.layoutXPosition(),
            binding.key1.layoutYPosition(),
            binding.key1.layoutXPosition() + binding.key1.width,
            binding.key1.layoutYPosition() + binding.key1.height
        ),
        KeyRect(
            Key.KeyQuestion,
            binding.key2.layoutXPosition(),
            binding.key2.layoutYPosition(),
            binding.key2.layoutXPosition() + binding.key2.width,
            binding.key2.layoutYPosition() + binding.key2.height
        ),
        KeyRect(
            Key.KeyCaution,
            binding.key3.layoutXPosition(),
            binding.key3.layoutYPosition(),
            binding.key3.layoutXPosition() + binding.key3.width,
            binding.key3.layoutYPosition() + binding.key3.height
        ),
        KeyRect(
            Key.KeyTouten,
            binding.key4.layoutXPosition(),
            binding.key4.layoutYPosition(),
            binding.key4.layoutXPosition() + binding.key4.width,
            binding.key4.layoutYPosition() + binding.key4.height
        ),
        KeyRect(
            Key.KeyKuten,
            binding.key5.layoutXPosition(),
            binding.key5.layoutYPosition(),
            binding.key5.layoutXPosition() + binding.key5.width,
            binding.key5.layoutYPosition() + binding.key5.height
        ),

        // わ row and punctuation
        KeyRect(
            Key.KeyWA,
            binding.key6.layoutXPosition(),
            binding.key6.layoutYPosition(),
            binding.key6.layoutXPosition() + binding.key6.width,
            binding.key6.layoutYPosition() + binding.key6.height
        ),
        KeyRect(
            Key.KeyWO,
            binding.key7.layoutXPosition(),
            binding.key7.layoutYPosition(),
            binding.key7.layoutXPosition() + binding.key7.width,
            binding.key7.layoutYPosition() + binding.key7.height
        ),
        KeyRect(
            Key.KeyN,
            binding.key8.layoutXPosition(),
            binding.key8.layoutYPosition(),
            binding.key8.layoutXPosition() + binding.key8.width,
            binding.key8.layoutYPosition() + binding.key8.height
        ),
        KeyRect(
            Key.KeyMinus,
            binding.key9.layoutXPosition(),
            binding.key9.layoutYPosition(),
            binding.key9.layoutXPosition() + binding.key9.width,
            binding.key9.layoutYPosition() + binding.key9.height
        ),
        KeyRect(
            Key.KeyDakutenSmall,
            binding.key10.layoutXPosition(),
            binding.key10.layoutYPosition(),
            binding.key10.layoutXPosition() + binding.key10.width,
            binding.key10.layoutYPosition() + binding.key10.height
        ),

        // ら row
        KeyRect(
            Key.KeyRA,
            binding.key11.layoutXPosition(),
            binding.key11.layoutYPosition(),
            binding.key11.layoutXPosition() + binding.key11.width,
            binding.key11.layoutYPosition() + binding.key11.height
        ),
        KeyRect(
            Key.KeyRI,
            binding.key12.layoutXPosition(),
            binding.key12.layoutYPosition(),
            binding.key12.layoutXPosition() + binding.key12.width,
            binding.key12.layoutYPosition() + binding.key12.height
        ),
        KeyRect(
            Key.KeyRU,
            binding.key13.layoutXPosition(),
            binding.key13.layoutYPosition(),
            binding.key13.layoutXPosition() + binding.key13.width,
            binding.key13.layoutYPosition() + binding.key13.height
        ),
        KeyRect(
            Key.KeyRE,
            binding.key14.layoutXPosition(),
            binding.key14.layoutYPosition(),
            binding.key14.layoutXPosition() + binding.key14.width,
            binding.key14.layoutYPosition() + binding.key14.height
        ),
        KeyRect(
            Key.KeyRO,
            binding.key15.layoutXPosition(),
            binding.key15.layoutYPosition(),
            binding.key15.layoutXPosition() + binding.key15.width,
            binding.key15.layoutYPosition() + binding.key15.height
        ),

        // や row
        KeyRect(
            Key.KeyYA,
            binding.key16.layoutXPosition(),
            binding.key16.layoutYPosition(),
            binding.key16.layoutXPosition() + binding.key16.width,
            binding.key16.layoutYPosition() + binding.key16.height
        ),
        // key17 = (empty)
        KeyRect(
            Key.KeySPACE1,
            binding.key17.layoutXPosition(),
            binding.key17.layoutYPosition(),
            binding.key17.layoutXPosition() + binding.key17.width,
            binding.key17.layoutYPosition() + binding.key17.height
        ),
        KeyRect(
            Key.KeyYU,
            binding.key18.layoutXPosition(),
            binding.key18.layoutYPosition(),
            binding.key18.layoutXPosition() + binding.key18.width,
            binding.key18.layoutYPosition() + binding.key18.height
        ),
        // key19 = (empty)
        KeyRect(
            Key.KeySPACE2,
            binding.key19.layoutXPosition(),
            binding.key19.layoutYPosition(),
            binding.key19.layoutXPosition() + binding.key19.width,
            binding.key19.layoutYPosition() + binding.key19.height
        ),
        KeyRect(
            Key.KeyYO,
            binding.key20.layoutXPosition(),
            binding.key20.layoutYPosition(),
            binding.key20.layoutXPosition() + binding.key20.width,
            binding.key20.layoutYPosition() + binding.key20.height
        ),

        // ま row
        KeyRect(
            Key.KeyMA,
            binding.key21.layoutXPosition(),
            binding.key21.layoutYPosition(),
            binding.key21.layoutXPosition() + binding.key21.width,
            binding.key21.layoutYPosition() + binding.key21.height
        ),
        KeyRect(
            Key.KeyMI,
            binding.key22.layoutXPosition(),
            binding.key22.layoutYPosition(),
            binding.key22.layoutXPosition() + binding.key22.width,
            binding.key22.layoutYPosition() + binding.key22.height
        ),
        KeyRect(
            Key.KeyMU,
            binding.key23.layoutXPosition(),
            binding.key23.layoutYPosition(),
            binding.key23.layoutXPosition() + binding.key23.width,
            binding.key23.layoutYPosition() + binding.key23.height
        ),
        KeyRect(
            Key.KeyME,
            binding.key24.layoutXPosition(),
            binding.key24.layoutYPosition(),
            binding.key24.layoutXPosition() + binding.key24.width,
            binding.key24.layoutYPosition() + binding.key24.height
        ),
        KeyRect(
            Key.KeyMO,
            binding.key25.layoutXPosition(),
            binding.key25.layoutYPosition(),
            binding.key25.layoutXPosition() + binding.key25.width,
            binding.key25.layoutYPosition() + binding.key25.height
        ),

        // は row
        KeyRect(
            Key.KeyHA,
            binding.key26.layoutXPosition(),
            binding.key26.layoutYPosition(),
            binding.key26.layoutXPosition() + binding.key26.width,
            binding.key26.layoutYPosition() + binding.key26.height
        ),
        KeyRect(
            Key.KeyHI,
            binding.key27.layoutXPosition(),
            binding.key27.layoutYPosition(),
            binding.key27.layoutXPosition() + binding.key27.width,
            binding.key27.layoutYPosition() + binding.key27.height
        ),
        KeyRect(
            Key.KeyFU,
            binding.key28.layoutXPosition(),
            binding.key28.layoutYPosition(),
            binding.key28.layoutXPosition() + binding.key28.width,
            binding.key28.layoutYPosition() + binding.key28.height
        ),
        KeyRect(
            Key.KeyHE,
            binding.key29.layoutXPosition(),
            binding.key29.layoutYPosition(),
            binding.key29.layoutXPosition() + binding.key29.width,
            binding.key29.layoutYPosition() + binding.key29.height
        ),
        KeyRect(
            Key.KeyHO,
            binding.key30.layoutXPosition(),
            binding.key30.layoutYPosition(),
            binding.key30.layoutXPosition() + binding.key30.width,
            binding.key30.layoutYPosition() + binding.key30.height
        ),

        // な row
        KeyRect(
            Key.KeyNA,
            binding.key31.layoutXPosition(),
            binding.key31.layoutYPosition(),
            binding.key31.layoutXPosition() + binding.key31.width,
            binding.key31.layoutYPosition() + binding.key31.height
        ),
        KeyRect(
            Key.KeyNI,
            binding.key32.layoutXPosition(),
            binding.key32.layoutYPosition(),
            binding.key32.layoutXPosition() + binding.key32.width,
            binding.key32.layoutYPosition() + binding.key32.height
        ),
        KeyRect(
            Key.KeyNU,
            binding.key33.layoutXPosition(),
            binding.key33.layoutYPosition(),
            binding.key33.layoutXPosition() + binding.key33.width,
            binding.key33.layoutYPosition() + binding.key33.height
        ),
        KeyRect(
            Key.KeyNE,
            binding.key34.layoutXPosition(),
            binding.key34.layoutYPosition(),
            binding.key34.layoutXPosition() + binding.key34.width,
            binding.key34.layoutYPosition() + binding.key34.height
        ),
        KeyRect(
            Key.KeyNO,
            binding.key35.layoutXPosition(),
            binding.key35.layoutYPosition(),
            binding.key35.layoutXPosition() + binding.key35.width,
            binding.key35.layoutYPosition() + binding.key35.height
        ),

        // た row
        KeyRect(
            Key.KeyTA,
            binding.key36.layoutXPosition(),
            binding.key36.layoutYPosition(),
            binding.key36.layoutXPosition() + binding.key36.width,
            binding.key36.layoutYPosition() + binding.key36.height
        ),
        KeyRect(
            Key.KeyCHI,
            binding.key37.layoutXPosition(),
            binding.key37.layoutYPosition(),
            binding.key37.layoutXPosition() + binding.key37.width,
            binding.key37.layoutYPosition() + binding.key37.height
        ),
        KeyRect(
            Key.KeyTSU,
            binding.key38.layoutXPosition(),
            binding.key38.layoutYPosition(),
            binding.key38.layoutXPosition() + binding.key38.width,
            binding.key38.layoutYPosition() + binding.key38.height
        ),
        KeyRect(
            Key.KeyTE,
            binding.key39.layoutXPosition(),
            binding.key39.layoutYPosition(),
            binding.key39.layoutXPosition() + binding.key39.width,
            binding.key39.layoutYPosition() + binding.key39.height
        ),
        KeyRect(
            Key.KeyTO,
            binding.key40.layoutXPosition(),
            binding.key40.layoutYPosition(),
            binding.key40.layoutXPosition() + binding.key40.width,
            binding.key40.layoutYPosition() + binding.key40.height
        ),

        // さ row
        KeyRect(
            Key.KeySA,
            binding.key41.layoutXPosition(),
            binding.key41.layoutYPosition(),
            binding.key41.layoutXPosition() + binding.key41.width,
            binding.key41.layoutYPosition() + binding.key41.height
        ),
        KeyRect(
            Key.KeySHI,
            binding.key42.layoutXPosition(),
            binding.key42.layoutYPosition(),
            binding.key42.layoutXPosition() + binding.key42.width,
            binding.key42.layoutYPosition() + binding.key42.height
        ),
        KeyRect(
            Key.KeySU,
            binding.key43.layoutXPosition(),
            binding.key43.layoutYPosition(),
            binding.key43.layoutXPosition() + binding.key43.width,
            binding.key43.layoutYPosition() + binding.key43.height
        ),
        KeyRect(
            Key.KeySE,
            binding.key44.layoutXPosition(),
            binding.key44.layoutYPosition(),
            binding.key44.layoutXPosition() + binding.key44.width,
            binding.key44.layoutYPosition() + binding.key44.height
        ),
        KeyRect(
            Key.KeySO,
            binding.key45.layoutXPosition(),
            binding.key45.layoutYPosition(),
            binding.key45.layoutXPosition() + binding.key45.width,
            binding.key45.layoutYPosition() + binding.key45.height
        ),

        // か row
        KeyRect(
            Key.KeyKA,
            binding.key46.layoutXPosition(),
            binding.key46.layoutYPosition(),
            binding.key46.layoutXPosition() + binding.key46.width,
            binding.key46.layoutYPosition() + binding.key46.height
        ),
        KeyRect(
            Key.KeyKI,
            binding.key47.layoutXPosition(),
            binding.key47.layoutYPosition(),
            binding.key47.layoutXPosition() + binding.key47.width,
            binding.key47.layoutYPosition() + binding.key47.height
        ),
        KeyRect(
            Key.KeyKU,
            binding.key48.layoutXPosition(),
            binding.key48.layoutYPosition(),
            binding.key48.layoutXPosition() + binding.key48.width,
            binding.key48.layoutYPosition() + binding.key48.height
        ),
        KeyRect(
            Key.KeyKE,
            binding.key49.layoutXPosition(),
            binding.key49.layoutYPosition(),
            binding.key49.layoutXPosition() + binding.key49.width,
            binding.key49.layoutYPosition() + binding.key49.height
        ),
        KeyRect(
            Key.KeyKO,
            binding.key50.layoutXPosition(),
            binding.key50.layoutYPosition(),
            binding.key50.layoutXPosition() + binding.key50.width,
            binding.key50.layoutYPosition() + binding.key50.height
        ),

        // あ row
        KeyRect(
            Key.KeyA,
            binding.key51.layoutXPosition(),
            binding.key51.layoutYPosition(),
            binding.key51.layoutXPosition() + binding.key51.width,
            binding.key51.layoutYPosition() + binding.key51.height
        ),
        KeyRect(
            Key.KeyI,
            binding.key52.layoutXPosition(),
            binding.key52.layoutYPosition(),
            binding.key52.layoutXPosition() + binding.key52.width,
            binding.key52.layoutYPosition() + binding.key52.height
        ),
        KeyRect(
            Key.KeyU,
            binding.key53.layoutXPosition(),
            binding.key53.layoutYPosition(),
            binding.key53.layoutXPosition() + binding.key53.width,
            binding.key53.layoutYPosition() + binding.key53.height
        ),
        KeyRect(
            Key.KeyE,
            binding.key54.layoutXPosition(),
            binding.key54.layoutYPosition(),
            binding.key54.layoutXPosition() + binding.key54.width,
            binding.key54.layoutYPosition() + binding.key54.height
        ),
        KeyRect(
            Key.KeyO,
            binding.key55.layoutXPosition(),
            binding.key55.layoutYPosition(),
            binding.key55.layoutXPosition() + binding.key55.width,
            binding.key55.layoutYPosition() + binding.key55.height
        ),
    )

    private fun buildKeyRectsEnglish() = listOf(
        // ---- Side Keys ----
        KeyRect(
            Key.SideKeySymbol,
            binding.keyKigou.layoutXPosition(),
            binding.keyKigou.layoutYPosition(),
            binding.keyKigou.layoutXPosition() + binding.keyKigou.width,
            binding.keyKigou.layoutYPosition() + binding.keyKigou.height
        ),
        KeyRect(
            Key.SideKeyPreviousChar,
            binding.keyPrevious.layoutXPosition(),
            binding.keyPrevious.layoutYPosition(),
            binding.keyPrevious.layoutXPosition() + binding.keyPrevious.width,
            binding.keyPrevious.layoutYPosition() + binding.keyPrevious.height
        ),
        KeyRect(
            Key.SideKeyInputMode,
            binding.keySwitchKeyMode.layoutXPosition(),
            binding.keySwitchKeyMode.layoutYPosition(),
            binding.keySwitchKeyMode.layoutXPosition() + binding.keySwitchKeyMode.width,
            binding.keySwitchKeyMode.layoutYPosition() + binding.keySwitchKeyMode.height
        ),
        KeyRect(
            Key.SideKeyCursorLeft,
            binding.keyLeftCursor.layoutXPosition(),
            binding.keyLeftCursor.layoutYPosition(),
            binding.keyLeftCursor.layoutXPosition() + binding.keyLeftCursor.width,
            binding.keyLeftCursor.layoutYPosition() + binding.keyLeftCursor.height
        ),
        KeyRect(
            Key.SideKeyCursorRight,
            binding.keyRightCursor.layoutXPosition(),
            binding.keyRightCursor.layoutYPosition(),
            binding.keyRightCursor.layoutXPosition() + binding.keyRightCursor.width,
            binding.keyRightCursor.layoutYPosition() + binding.keyRightCursor.height
        ),
        KeyRect(
            Key.SideKeyDelete,
            binding.keyDelete.layoutXPosition(),
            binding.keyDelete.layoutYPosition(),
            binding.keyDelete.layoutXPosition() + binding.keyDelete.width,
            binding.keyDelete.layoutYPosition() + binding.keyDelete.height
        ),
        KeyRect(
            Key.SideKeySpace,
            binding.keySpace.layoutXPosition(),
            binding.keySpace.layoutYPosition(),
            binding.keySpace.layoutXPosition() + binding.keySpace.width,
            binding.keySpace.layoutYPosition() + binding.keySpace.height
        ),
        KeyRect(
            Key.SideKeyEnter,
            binding.keyEnter.layoutXPosition(),
            binding.keyEnter.layoutYPosition(),
            binding.keyEnter.layoutXPosition() + binding.keyEnter.width,
            binding.keyEnter.layoutYPosition() + binding.keyEnter.height
        ),

        // ---- Character Keys ----
        KeyRect(
            Key.KeyKagikakko,
            binding.key1.layoutXPosition(),
            binding.key1.layoutYPosition(),
            binding.key1.layoutXPosition() + binding.key1.width,
            binding.key1.layoutYPosition() + binding.key1.height
        ),
        KeyRect(
            Key.KeyQuestion,
            binding.key2.layoutXPosition(),
            binding.key2.layoutYPosition(),
            binding.key2.layoutXPosition() + binding.key2.width,
            binding.key2.layoutYPosition() + binding.key2.height
        ),
        KeyRect(
            Key.KeyCaution,
            binding.key3.layoutXPosition(),
            binding.key3.layoutYPosition(),
            binding.key3.layoutXPosition() + binding.key3.width,
            binding.key3.layoutYPosition() + binding.key3.height
        ),
        KeyRect(
            Key.KeyTouten,
            binding.key4.layoutXPosition(),
            binding.key4.layoutYPosition(),
            binding.key4.layoutXPosition() + binding.key4.width,
            binding.key4.layoutYPosition() + binding.key4.height
        ),
        KeyRect(
            Key.KeyKuten,
            binding.key5.layoutXPosition(),
            binding.key5.layoutYPosition(),
            binding.key5.layoutXPosition() + binding.key5.width,
            binding.key5.layoutYPosition() + binding.key5.height
        ),

        // わ row and punctuation
        KeyRect(
            Key.KeyWA,
            binding.key6.layoutXPosition(),
            binding.key6.layoutYPosition(),
            binding.key6.layoutXPosition() + binding.key6.width,
            binding.key6.layoutYPosition() + binding.key6.height
        ),
        KeyRect(
            Key.KeyWO,
            binding.key7.layoutXPosition(),
            binding.key7.layoutYPosition(),
            binding.key7.layoutXPosition() + binding.key7.width,
            binding.key7.layoutYPosition() + binding.key7.height
        ),
        KeyRect(
            Key.KeyN,
            binding.key8.layoutXPosition(),
            binding.key8.layoutYPosition(),
            binding.key8.layoutXPosition() + binding.key8.width,
            binding.key8.layoutYPosition() + binding.key8.height
        ),
        KeyRect(
            Key.KeyMinus,
            binding.key9.layoutXPosition(),
            binding.key9.layoutYPosition(),
            binding.key9.layoutXPosition() + binding.key9.width,
            binding.key9.layoutYPosition() + binding.key9.height
        ),
        KeyRect(
            Key.KeyDakutenSmall,
            binding.key10.layoutXPosition(),
            binding.key10.layoutYPosition(),
            binding.key10.layoutXPosition() + binding.key10.width,
            binding.key10.layoutYPosition() + binding.key10.height
        ),

        // ら row
        KeyRect(
            Key.KeyRA,
            binding.key11.layoutXPosition(),
            binding.key11.layoutYPosition(),
            binding.key11.layoutXPosition() + binding.key11.width,
            binding.key11.layoutYPosition() + binding.key11.height
        ),
        KeyRect(
            Key.KeyRI,
            binding.key12.layoutXPosition(),
            binding.key12.layoutYPosition(),
            binding.key12.layoutXPosition() + binding.key12.width,
            binding.key12.layoutYPosition() + binding.key12.height
        ),
        KeyRect(
            Key.KeyRU,
            binding.key13.layoutXPosition(),
            binding.key13.layoutYPosition(),
            binding.key13.layoutXPosition() + binding.key13.width,
            binding.key13.layoutYPosition() + binding.key13.height
        ),
        KeyRect(
            Key.KeyRE,
            binding.key14.layoutXPosition(),
            binding.key14.layoutYPosition(),
            binding.key14.layoutXPosition() + binding.key14.width,
            binding.key14.layoutYPosition() + binding.key14.height
        ),
        KeyRect(
            Key.KeyDakutenSmall,
            binding.key15.layoutXPosition(),
            binding.key15.layoutYPosition(),
            binding.key15.layoutXPosition() + binding.key15.width,
            binding.key15.layoutYPosition() + binding.key15.height
        ),

        // や row
        KeyRect(
            Key.KeyYA,
            binding.key16.layoutXPosition(),
            binding.key16.layoutYPosition(),
            binding.key16.layoutXPosition() + binding.key16.width,
            binding.key16.layoutYPosition() + binding.key16.height
        ),
        // key17 = (empty)
        KeyRect(
            Key.KeySPACE1,
            binding.key17.layoutXPosition(),
            binding.key17.layoutYPosition(),
            binding.key17.layoutXPosition() + binding.key17.width,
            binding.key17.layoutYPosition() + binding.key17.height
        ),
        KeyRect(
            Key.KeyYU,
            binding.key18.layoutXPosition(),
            binding.key18.layoutYPosition(),
            binding.key18.layoutXPosition() + binding.key18.width,
            binding.key18.layoutYPosition() + binding.key18.height
        ),
        // key19 = (empty)
        KeyRect(
            Key.KeySPACE2,
            binding.key19.layoutXPosition(),
            binding.key19.layoutYPosition(),
            binding.key19.layoutXPosition() + binding.key19.width,
            binding.key19.layoutYPosition() + binding.key19.height
        ),
        KeyRect(
            Key.KeyDakutenSmall,
            binding.key20.layoutXPosition(),
            binding.key20.layoutYPosition(),
            binding.key20.layoutXPosition() + binding.key20.width,
            binding.key20.layoutYPosition() + binding.key20.height
        ),

        // ま row
        KeyRect(
            Key.KeyMA,
            binding.key21.layoutXPosition(),
            binding.key21.layoutYPosition(),
            binding.key21.layoutXPosition() + binding.key21.width,
            binding.key21.layoutYPosition() + binding.key21.height
        ),
        KeyRect(
            Key.KeyMI,
            binding.key22.layoutXPosition(),
            binding.key22.layoutYPosition(),
            binding.key22.layoutXPosition() + binding.key22.width,
            binding.key22.layoutYPosition() + binding.key22.height
        ),
        KeyRect(
            Key.KeyMU,
            binding.key23.layoutXPosition(),
            binding.key23.layoutYPosition(),
            binding.key23.layoutXPosition() + binding.key23.width,
            binding.key23.layoutYPosition() + binding.key23.height
        ),
        KeyRect(
            Key.KeyME,
            binding.key24.layoutXPosition(),
            binding.key24.layoutYPosition(),
            binding.key24.layoutXPosition() + binding.key24.width,
            binding.key24.layoutYPosition() + binding.key24.height
        ),
        KeyRect(
            Key.KeyMO,
            binding.key25.layoutXPosition(),
            binding.key25.layoutYPosition(),
            binding.key25.layoutXPosition() + binding.key25.width,
            binding.key25.layoutYPosition() + binding.key25.height
        ),

        // は row
        KeyRect(
            Key.KeyHA,
            binding.key26.layoutXPosition(),
            binding.key26.layoutYPosition(),
            binding.key26.layoutXPosition() + binding.key26.width,
            binding.key26.layoutYPosition() + binding.key26.height
        ),
        KeyRect(
            Key.KeyHI,
            binding.key27.layoutXPosition(),
            binding.key27.layoutYPosition(),
            binding.key27.layoutXPosition() + binding.key27.width,
            binding.key27.layoutYPosition() + binding.key27.height
        ),
        KeyRect(
            Key.KeyFU,
            binding.key28.layoutXPosition(),
            binding.key28.layoutYPosition(),
            binding.key28.layoutXPosition() + binding.key28.width,
            binding.key28.layoutYPosition() + binding.key28.height
        ),
        KeyRect(
            Key.KeyHE,
            binding.key29.layoutXPosition(),
            binding.key29.layoutYPosition(),
            binding.key29.layoutXPosition() + binding.key29.width,
            binding.key29.layoutYPosition() + binding.key29.height
        ),
        KeyRect(
            Key.KeyHO,
            binding.key30.layoutXPosition(),
            binding.key30.layoutYPosition(),
            binding.key30.layoutXPosition() + binding.key30.width,
            binding.key30.layoutYPosition() + binding.key30.height
        ),

        // な row
        KeyRect(
            Key.KeyNA,
            binding.key31.layoutXPosition(),
            binding.key31.layoutYPosition(),
            binding.key31.layoutXPosition() + binding.key31.width,
            binding.key31.layoutYPosition() + binding.key31.height
        ),
        KeyRect(
            Key.KeyNI,
            binding.key32.layoutXPosition(),
            binding.key32.layoutYPosition(),
            binding.key32.layoutXPosition() + binding.key32.width,
            binding.key32.layoutYPosition() + binding.key32.height
        ),
        KeyRect(
            Key.KeyNU,
            binding.key33.layoutXPosition(),
            binding.key33.layoutYPosition(),
            binding.key33.layoutXPosition() + binding.key33.width,
            binding.key33.layoutYPosition() + binding.key33.height
        ),
        KeyRect(
            Key.KeyNE,
            binding.key34.layoutXPosition(),
            binding.key34.layoutYPosition(),
            binding.key34.layoutXPosition() + binding.key34.width,
            binding.key34.layoutYPosition() + binding.key34.height
        ),
        KeyRect(
            Key.KeyNO,
            binding.key35.layoutXPosition(),
            binding.key35.layoutYPosition(),
            binding.key35.layoutXPosition() + binding.key35.width,
            binding.key35.layoutYPosition() + binding.key35.height
        ),

        // た row
        KeyRect(
            Key.KeyTA,
            binding.key36.layoutXPosition(),
            binding.key36.layoutYPosition(),
            binding.key36.layoutXPosition() + binding.key36.width,
            binding.key36.layoutYPosition() + binding.key36.height
        ),
        KeyRect(
            Key.KeyCHI,
            binding.key37.layoutXPosition(),
            binding.key37.layoutYPosition(),
            binding.key37.layoutXPosition() + binding.key37.width,
            binding.key37.layoutYPosition() + binding.key37.height
        ),
        KeyRect(
            Key.KeyTSU,
            binding.key38.layoutXPosition(),
            binding.key38.layoutYPosition(),
            binding.key38.layoutXPosition() + binding.key38.width,
            binding.key38.layoutYPosition() + binding.key38.height
        ),
        KeyRect(
            Key.KeyTE,
            binding.key39.layoutXPosition(),
            binding.key39.layoutYPosition(),
            binding.key39.layoutXPosition() + binding.key39.width,
            binding.key39.layoutYPosition() + binding.key39.height
        ),
        KeyRect(
            Key.KeyTO,
            binding.key40.layoutXPosition(),
            binding.key40.layoutYPosition(),
            binding.key40.layoutXPosition() + binding.key40.width,
            binding.key40.layoutYPosition() + binding.key40.height
        ),

        // さ row
        KeyRect(
            Key.KeySA,
            binding.key41.layoutXPosition(),
            binding.key41.layoutYPosition(),
            binding.key41.layoutXPosition() + binding.key41.width,
            binding.key41.layoutYPosition() + binding.key41.height
        ),
        KeyRect(
            Key.KeySHI,
            binding.key42.layoutXPosition(),
            binding.key42.layoutYPosition(),
            binding.key42.layoutXPosition() + binding.key42.width,
            binding.key42.layoutYPosition() + binding.key42.height
        ),
        KeyRect(
            Key.KeySU,
            binding.key43.layoutXPosition(),
            binding.key43.layoutYPosition(),
            binding.key43.layoutXPosition() + binding.key43.width,
            binding.key43.layoutYPosition() + binding.key43.height
        ),
        KeyRect(
            Key.KeySE,
            binding.key44.layoutXPosition(),
            binding.key44.layoutYPosition(),
            binding.key44.layoutXPosition() + binding.key44.width,
            binding.key44.layoutYPosition() + binding.key44.height
        ),
        KeyRect(
            Key.KeySO,
            binding.key45.layoutXPosition(),
            binding.key45.layoutYPosition(),
            binding.key45.layoutXPosition() + binding.key45.width,
            binding.key45.layoutYPosition() + binding.key45.height
        ),

        // か row
        KeyRect(
            Key.KeyKA,
            binding.key46.layoutXPosition(),
            binding.key46.layoutYPosition(),
            binding.key46.layoutXPosition() + binding.key46.width,
            binding.key46.layoutYPosition() + binding.key46.height
        ),
        KeyRect(
            Key.KeyKI,
            binding.key47.layoutXPosition(),
            binding.key47.layoutYPosition(),
            binding.key47.layoutXPosition() + binding.key47.width,
            binding.key47.layoutYPosition() + binding.key47.height
        ),
        KeyRect(
            Key.KeyKU,
            binding.key48.layoutXPosition(),
            binding.key48.layoutYPosition(),
            binding.key48.layoutXPosition() + binding.key48.width,
            binding.key48.layoutYPosition() + binding.key48.height
        ),
        KeyRect(
            Key.KeyKE,
            binding.key49.layoutXPosition(),
            binding.key49.layoutYPosition(),
            binding.key49.layoutXPosition() + binding.key49.width,
            binding.key49.layoutYPosition() + binding.key49.height
        ),
        KeyRect(
            Key.KeyKO,
            binding.key50.layoutXPosition(),
            binding.key50.layoutYPosition(),
            binding.key50.layoutXPosition() + binding.key50.width,
            binding.key50.layoutYPosition() + binding.key50.height
        ),

        // あ row
        KeyRect(
            Key.KeyA,
            binding.key51.layoutXPosition(),
            binding.key51.layoutYPosition(),
            binding.key51.layoutXPosition() + binding.key51.width,
            binding.key51.layoutYPosition() + binding.key51.height
        ),
        KeyRect(
            Key.KeyI,
            binding.key52.layoutXPosition(),
            binding.key52.layoutYPosition(),
            binding.key52.layoutXPosition() + binding.key52.width,
            binding.key52.layoutYPosition() + binding.key52.height
        ),
        KeyRect(
            Key.KeyU,
            binding.key53.layoutXPosition(),
            binding.key53.layoutYPosition(),
            binding.key53.layoutXPosition() + binding.key53.width,
            binding.key53.layoutYPosition() + binding.key53.height
        ),
        KeyRect(
            Key.KeyE,
            binding.key54.layoutXPosition(),
            binding.key54.layoutYPosition(),
            binding.key54.layoutXPosition() + binding.key54.width,
            binding.key54.layoutYPosition() + binding.key54.height
        ),
        KeyRect(
            Key.KeyO,
            binding.key55.layoutXPosition(),
            binding.key55.layoutYPosition(),
            binding.key55.layoutXPosition() + binding.key55.width,
            binding.key55.layoutYPosition() + binding.key55.height
        ),
    )

    private fun buildKeyRectsNumber() = listOf(
        // ---- Side Keys ----
        KeyRect(
            Key.SideKeySymbol,
            binding.keyKigou.layoutXPosition(),
            binding.keyKigou.layoutYPosition(),
            binding.keyKigou.layoutXPosition() + binding.keyKigou.width,
            binding.keyKigou.layoutYPosition() + binding.keyKigou.height
        ),
        KeyRect(
            Key.SideKeyPreviousChar,
            binding.keyPrevious.layoutXPosition(),
            binding.keyPrevious.layoutYPosition(),
            binding.keyPrevious.layoutXPosition() + binding.keyPrevious.width,
            binding.keyPrevious.layoutYPosition() + binding.keyPrevious.height
        ),
        KeyRect(
            Key.SideKeyInputMode,
            binding.keySwitchKeyMode.layoutXPosition(),
            binding.keySwitchKeyMode.layoutYPosition(),
            binding.keySwitchKeyMode.layoutXPosition() + binding.keySwitchKeyMode.width,
            binding.keySwitchKeyMode.layoutYPosition() + binding.keySwitchKeyMode.height
        ),
        KeyRect(
            Key.SideKeyCursorLeft,
            binding.keyLeftCursor.layoutXPosition(),
            binding.keyLeftCursor.layoutYPosition(),
            binding.keyLeftCursor.layoutXPosition() + binding.keyLeftCursor.width,
            binding.keyLeftCursor.layoutYPosition() + binding.keyLeftCursor.height
        ),
        KeyRect(
            Key.SideKeyCursorRight,
            binding.keyRightCursor.layoutXPosition(),
            binding.keyRightCursor.layoutYPosition(),
            binding.keyRightCursor.layoutXPosition() + binding.keyRightCursor.width,
            binding.keyRightCursor.layoutYPosition() + binding.keyRightCursor.height
        ),
        KeyRect(
            Key.SideKeyDelete,
            binding.keyDelete.layoutXPosition(),
            binding.keyDelete.layoutYPosition(),
            binding.keyDelete.layoutXPosition() + binding.keyDelete.width,
            binding.keyDelete.layoutYPosition() + binding.keyDelete.height
        ),
        KeyRect(
            Key.SideKeySpace,
            binding.keySpace.layoutXPosition(),
            binding.keySpace.layoutYPosition(),
            binding.keySpace.layoutXPosition() + binding.keySpace.width,
            binding.keySpace.layoutYPosition() + binding.keySpace.height
        ),
        KeyRect(
            Key.SideKeyEnter,
            binding.keyEnter.layoutXPosition(),
            binding.keyEnter.layoutYPosition(),
            binding.keyEnter.layoutXPosition() + binding.keyEnter.width,
            binding.keyEnter.layoutYPosition() + binding.keyEnter.height
        ),

        // ---- Character Keys ----
        KeyRect(
            Key.KeyKagikakko,
            binding.key1.layoutXPosition(),
            binding.key1.layoutYPosition(),
            binding.key1.layoutXPosition() + binding.key1.width,
            binding.key1.layoutYPosition() + binding.key1.height
        ),
        KeyRect(
            Key.KeyQuestion,
            binding.key2.layoutXPosition(),
            binding.key2.layoutYPosition(),
            binding.key2.layoutXPosition() + binding.key2.width,
            binding.key2.layoutYPosition() + binding.key2.height
        ),
        KeyRect(
            Key.KeyCaution,
            binding.key3.layoutXPosition(),
            binding.key3.layoutYPosition(),
            binding.key3.layoutXPosition() + binding.key3.width,
            binding.key3.layoutYPosition() + binding.key3.height
        ),
        KeyRect(
            Key.KeyTouten,
            binding.key4.layoutXPosition(),
            binding.key4.layoutYPosition(),
            binding.key4.layoutXPosition() + binding.key4.width,
            binding.key4.layoutYPosition() + binding.key4.height
        ),
        KeyRect(
            Key.KeyKuten,
            binding.key5.layoutXPosition(),
            binding.key5.layoutYPosition(),
            binding.key5.layoutXPosition() + binding.key5.width,
            binding.key5.layoutYPosition() + binding.key5.height
        ),
        // ら row
        KeyRect(
            Key.KeyRA,
            binding.key11.layoutXPosition(),
            binding.key11.layoutYPosition(),
            binding.key11.layoutXPosition() + binding.key11.width,
            binding.key11.layoutYPosition() + binding.key11.height
        ),
        KeyRect(
            Key.KeyRI,
            binding.key12.layoutXPosition(),
            binding.key12.layoutYPosition(),
            binding.key12.layoutXPosition() + binding.key12.width,
            binding.key12.layoutYPosition() + binding.key12.height
        ),
        KeyRect(
            Key.KeyRU,
            binding.key13.layoutXPosition(),
            binding.key13.layoutYPosition(),
            binding.key13.layoutXPosition() + binding.key13.width,
            binding.key13.layoutYPosition() + binding.key13.height
        ),
        KeyRect(
            Key.KeyRE,
            binding.key14.layoutXPosition(),
            binding.key14.layoutYPosition(),
            binding.key14.layoutXPosition() + binding.key14.width,
            binding.key14.layoutYPosition() + binding.key14.height
        ),
        KeyRect(
            Key.KeyDakutenSmall,
            binding.key15.layoutXPosition(),
            binding.key15.layoutYPosition(),
            binding.key15.layoutXPosition() + binding.key15.width,
            binding.key15.layoutYPosition() + binding.key15.height
        ),
        // は row
        KeyRect(
            Key.KeyHA,
            binding.key26.layoutXPosition(),
            binding.key26.layoutYPosition(),
            binding.key26.layoutXPosition() + binding.key26.width,
            binding.key26.layoutYPosition() + binding.key26.height
        ),
        KeyRect(
            Key.KeyHI,
            binding.key27.layoutXPosition(),
            binding.key27.layoutYPosition(),
            binding.key27.layoutXPosition() + binding.key27.width,
            binding.key27.layoutYPosition() + binding.key27.height
        ),
        KeyRect(
            Key.KeyFU,
            binding.key28.layoutXPosition(),
            binding.key28.layoutYPosition(),
            binding.key28.layoutXPosition() + binding.key28.width,
            binding.key28.layoutYPosition() + binding.key28.height
        ),
        KeyRect(
            Key.KeyHE,
            binding.key29.layoutXPosition(),
            binding.key29.layoutYPosition(),
            binding.key29.layoutXPosition() + binding.key29.width,
            binding.key29.layoutYPosition() + binding.key29.height
        ),
        KeyRect(
            Key.KeyHO,
            binding.key30.layoutXPosition(),
            binding.key30.layoutYPosition(),
            binding.key30.layoutXPosition() + binding.key30.width,
            binding.key30.layoutYPosition() + binding.key30.height
        ),

        // な row
        KeyRect(
            Key.KeyNA,
            binding.key31.layoutXPosition(),
            binding.key31.layoutYPosition(),
            binding.key31.layoutXPosition() + binding.key31.width,
            binding.key31.layoutYPosition() + binding.key31.height
        ),
        KeyRect(
            Key.KeyNI,
            binding.key32.layoutXPosition(),
            binding.key32.layoutYPosition(),
            binding.key32.layoutXPosition() + binding.key32.width,
            binding.key32.layoutYPosition() + binding.key32.height
        ),
        KeyRect(
            Key.KeyNU,
            binding.key33.layoutXPosition(),
            binding.key33.layoutYPosition(),
            binding.key33.layoutXPosition() + binding.key33.width,
            binding.key33.layoutYPosition() + binding.key33.height
        ),
        KeyRect(
            Key.KeyNE,
            binding.key34.layoutXPosition(),
            binding.key34.layoutYPosition(),
            binding.key34.layoutXPosition() + binding.key34.width,
            binding.key34.layoutYPosition() + binding.key34.height
        ),
        KeyRect(
            Key.KeyNO,
            binding.key35.layoutXPosition(),
            binding.key35.layoutYPosition(),
            binding.key35.layoutXPosition() + binding.key35.width,
            binding.key35.layoutYPosition() + binding.key35.height
        ),

        // た row
        KeyRect(
            Key.KeyTA,
            binding.key36.layoutXPosition(),
            binding.key36.layoutYPosition(),
            binding.key36.layoutXPosition() + binding.key36.width,
            binding.key36.layoutYPosition() + binding.key36.height
        ),
        KeyRect(
            Key.KeyCHI,
            binding.key37.layoutXPosition(),
            binding.key37.layoutYPosition(),
            binding.key37.layoutXPosition() + binding.key37.width,
            binding.key37.layoutYPosition() + binding.key37.height
        ),
        KeyRect(
            Key.KeyTSU,
            binding.key38.layoutXPosition(),
            binding.key38.layoutYPosition(),
            binding.key38.layoutXPosition() + binding.key38.width,
            binding.key38.layoutYPosition() + binding.key38.height
        ),
        KeyRect(
            Key.KeyTE,
            binding.key39.layoutXPosition(),
            binding.key39.layoutYPosition(),
            binding.key39.layoutXPosition() + binding.key39.width,
            binding.key39.layoutYPosition() + binding.key39.height
        ),
        KeyRect(
            Key.KeyTO,
            binding.key40.layoutXPosition(),
            binding.key40.layoutYPosition(),
            binding.key40.layoutXPosition() + binding.key40.width,
            binding.key40.layoutYPosition() + binding.key40.height
        ),

        // さ row
        KeyRect(
            Key.KeySA,
            binding.key41.layoutXPosition(),
            binding.key41.layoutYPosition(),
            binding.key41.layoutXPosition() + binding.key41.width,
            binding.key41.layoutYPosition() + binding.key41.height
        ),
        KeyRect(
            Key.KeySHI,
            binding.key42.layoutXPosition(),
            binding.key42.layoutYPosition(),
            binding.key42.layoutXPosition() + binding.key42.width,
            binding.key42.layoutYPosition() + binding.key42.height
        ),
        KeyRect(
            Key.KeySU,
            binding.key43.layoutXPosition(),
            binding.key43.layoutYPosition(),
            binding.key43.layoutXPosition() + binding.key43.width,
            binding.key43.layoutYPosition() + binding.key43.height
        ),
        KeyRect(
            Key.KeySE,
            binding.key44.layoutXPosition(),
            binding.key44.layoutYPosition(),
            binding.key44.layoutXPosition() + binding.key44.width,
            binding.key44.layoutYPosition() + binding.key44.height
        ),
        KeyRect(
            Key.KeySO,
            binding.key45.layoutXPosition(),
            binding.key45.layoutYPosition(),
            binding.key45.layoutXPosition() + binding.key45.width,
            binding.key45.layoutYPosition() + binding.key45.height
        ),

        // か row
        KeyRect(
            Key.KeyKA,
            binding.key46.layoutXPosition(),
            binding.key46.layoutYPosition(),
            binding.key46.layoutXPosition() + binding.key46.width,
            binding.key46.layoutYPosition() + binding.key46.height
        ),
        KeyRect(
            Key.KeyKI,
            binding.key47.layoutXPosition(),
            binding.key47.layoutYPosition(),
            binding.key47.layoutXPosition() + binding.key47.width,
            binding.key47.layoutYPosition() + binding.key47.height
        ),
        KeyRect(
            Key.KeyKU,
            binding.key48.layoutXPosition(),
            binding.key48.layoutYPosition(),
            binding.key48.layoutXPosition() + binding.key48.width,
            binding.key48.layoutYPosition() + binding.key48.height
        ),
        KeyRect(
            Key.KeyKE,
            binding.key49.layoutXPosition(),
            binding.key49.layoutYPosition(),
            binding.key49.layoutXPosition() + binding.key49.width,
            binding.key49.layoutYPosition() + binding.key49.height
        ),
        KeyRect(
            Key.KeyKO,
            binding.key50.layoutXPosition(),
            binding.key50.layoutYPosition(),
            binding.key50.layoutXPosition() + binding.key50.width,
            binding.key50.layoutYPosition() + binding.key50.height
        ),

        // あ row
        KeyRect(
            Key.KeyA,
            binding.key51.layoutXPosition(),
            binding.key51.layoutYPosition(),
            binding.key51.layoutXPosition() + binding.key51.width,
            binding.key51.layoutYPosition() + binding.key51.height
        ),
        KeyRect(
            Key.KeyI,
            binding.key52.layoutXPosition(),
            binding.key52.layoutYPosition(),
            binding.key52.layoutXPosition() + binding.key52.width,
            binding.key52.layoutYPosition() + binding.key52.height
        ),
        KeyRect(
            Key.KeyU,
            binding.key53.layoutXPosition(),
            binding.key53.layoutYPosition(),
            binding.key53.layoutXPosition() + binding.key53.width,
            binding.key53.layoutYPosition() + binding.key53.height
        ),
        KeyRect(
            Key.KeyE,
            binding.key54.layoutXPosition(),
            binding.key54.layoutYPosition(),
            binding.key54.layoutXPosition() + binding.key54.width,
            binding.key54.layoutYPosition() + binding.key54.height
        ),
        KeyRect(
            Key.KeyO,
            binding.key55.layoutXPosition(),
            binding.key55.layoutYPosition(),
            binding.key55.layoutXPosition() + binding.key55.width,
            binding.key55.layoutYPosition() + binding.key55.height
        ),
    )

    private fun pressedKeyByMotionEvent(event: MotionEvent, pointer: Int): Key {
        val (x, y) = getRawCoordinates(event, pointer)

        val keyRects = when (currentInputMode.get()) {
            InputMode.ModeEnglish -> buildKeyRectsEnglish()
            InputMode.ModeJapanese -> buildKeyRects()
            InputMode.ModeNumber -> buildKeyRectsNumber()
        }

        keyRects.forEach { rect ->
            if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
                return rect.key
            }
        }

        val nearest = keyRects.minByOrNull { rect ->
            val centerX = (rect.left + rect.right) / 2
            val centerY = (rect.top + rect.bottom) / 2
            val dx = x - centerX
            val dy = y - centerY
            dx * dx + dy * dy
        }
        return nearest?.key ?: Key.NotSelected
    }

    // --- Utility to get consistent absolute coordinates ---
    private fun getRawCoordinates(event: MotionEvent, pointer: Int): Pair<Float, Float> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            event.getRawX(pointer) to event.getRawY(pointer)
        } else {
            val location = IntArray(2)
            this.getLocationOnScreen(location)
            (event.getX(pointer) + location[0]) to (event.getY(pointer) + location[1])
        }
    }

    private fun resetLongPressAction() {
        if (isLongPressed) {
            hideAllPopWindow()
            Blur.removeBlurEffect(this)
        }
        longPressJob?.cancel()
        isLongPressed = false
    }

    private fun getButtonFromKey(key: Key): Any? {
        return listKeys.getOrDefault(key, null)
    }

    private fun onLongPressed() {
        val button = getButtonFromKey(pressedKey.key)
        button?.let {
            if (it is AppCompatButton) {
                if (it.id == binding.key10.id || it.id == binding.key17.id || it.id == binding.key19.id) return
                when (currentInputMode.get()) {
                    InputMode.ModeJapanese -> {
                        popTextTop.setTabletTextFlickTopJapanese(it.id)
                        popTextLeft.setTabletTextFlickLeftJapanese(it.id)
                        popTextBottom.setTabletTextFlickBottomJapanese(it.id)
                        popTextRight.setTabletTextFlickRightJapanese(it.id)
                        popTextActive.setTabletTextTapJapanese(it.id)
                    }

                    InputMode.ModeEnglish -> {
                        return
                    }

                    InputMode.ModeNumber -> {
                        return
                    }
                }
                if (popTextTop.text.isNotEmpty()) {
                    popupWindowTop.setPopUpWindowFlickTop(context, bubbleViewTop, it)
                }
                if (popTextLeft.text.isNotEmpty()) {
                    popupWindowLeft.setPopUpWindowFlickLeft(context, bubbleViewLeft, it)
                }
                if (popTextBottom.text.isNotEmpty()) {
                    popupWindowBottom.setPopUpWindowFlickBottom(
                        context, bubbleViewBottom, it
                    )
                }
                if (popTextRight.text.isNotEmpty()) {
                    popupWindowRight.setPopUpWindowFlickRight(
                        context, bubbleViewRight, it
                    )
                }
                popupWindowActive.setPopUpWindowFlickTap(
                    context, bubbleViewActive, it
                )
                Blur.applyBlurEffect(this, 8f)
            }
        }
    }

    private fun hideAllPopWindow() {
        popupWindowActive.hide()
        popupWindowLeft.hide()
        popupWindowTop.hide()
        popupWindowRight.hide()
        popupWindowBottom.hide()
        popupWindowCenter.hide()
    }

    private fun setTapInActionMove() {
        if (!isLongPressed) popupWindowActive.hide()
        val button = getButtonFromKey(pressedKey.key)
        if (currentInputMode.get() == InputMode.ModeEnglish &&
            (tabletCapsLockState.value.capsLockOn || tabletCapsLockState.value.shiftOn)
        ) {
            button?.let {
                if (it is AppCompatButton) {
                    it.setTabletKeyTextEnglishCaps(it.id)
                }
            }
            return
        }
        button?.let {
            if (it is AppCompatButton) {
                when (currentInputMode.get()) {
                    InputMode.ModeJapanese -> {
                        it.setTabletKeyTextJapanese(it.id)
                        if (isLongPressed) popTextActive.setTabletTextTapJapanese(it.id)
                    }

                    InputMode.ModeEnglish -> {
                        it.setTabletKeyTextEnglish(it.id)
                        if (isLongPressed) popTextActive.setTabletTextDefaultEnglish(it.id)
                    }

                    InputMode.ModeNumber -> {
                        it.setTabletKeyTextNumber(it.id)
                    }
                }
                it.isPressed = true
                if (isLongPressed) {
                    popupWindowActive.setPopUpWindowCenter(
                        context, bubbleViewActive, it
                    )
                }
            }
        }
    }

    private fun setFlickInActionMove(gestureType: GestureType) {
        longPressJob?.cancel()
        val button = getButtonFromKey(pressedKey.key)
        if (currentInputMode.get() == InputMode.ModeEnglish &&
            (tabletCapsLockState.value.capsLockOn || tabletCapsLockState.value.shiftOn)
        ) {
            button?.let {
                if (it is AppCompatButton) {
                    it.setTabletKeyTextEnglishCaps(it.id)
                }
            }
            return
        } else if (currentInputMode.get() == InputMode.ModeEnglish) {
            return
        } else if (currentInputMode.get() == InputMode.ModeNumber) {
            return
        }
        button?.let {
            if (it is AppCompatButton) {
                if (!isLongPressed) it.text = ""
                when (gestureType) {
                    GestureType.FlickLeft -> {
                        when (currentInputMode.get()) {
                            InputMode.ModeJapanese -> {
                                popTextActive.setTabletTextFlickLeftJapanese(it.id)
                                if (isLongPressed) popTextCenter.setTabletTextTapJapanese(it.id)
                            }

                            InputMode.ModeEnglish -> {
                                return
                            }

                            InputMode.ModeNumber -> {
                                return
                            }
                        }
                        if (isLongPressed) {
                            if (popTextActive.text.isNotEmpty()) {
                                popupWindowActive.setPopUpWindowLeft(
                                    context, bubbleViewActive, it
                                )
                                popupWindowCenter.setPopUpWindowCenter(
                                    context, bubbleViewCenter, it
                                )
                            }
                        } else {
                            if (popTextActive.text.isNotEmpty()) {
                                popupWindowActive.setPopUpWindowFlickLeft(
                                    context, bubbleViewActive, it
                                )
                            }
                        }
                    }

                    GestureType.FlickTop -> {
                        when (currentInputMode.get()) {
                            InputMode.ModeJapanese -> {
                                popTextActive.setTabletTextFlickTopJapanese(it.id)
                                if (isLongPressed) popTextCenter.setTabletTextTapJapanese(it.id)
                            }

                            InputMode.ModeEnglish -> {
                                return
                            }

                            InputMode.ModeNumber -> {
                                return
                            }
                        }
                        if (isLongPressed) {
                            if (popTextActive.text.isNotEmpty()) {
                                popupWindowActive.setPopUpWindowTop(
                                    context, bubbleViewActive, it
                                )
                                popupWindowCenter.setPopUpWindowCenter(
                                    context, bubbleViewCenter, it
                                )
                            }
                        } else {
                            if (popTextActive.text.isNotEmpty()) {
                                popupWindowActive.setPopUpWindowFlickTop(
                                    context, bubbleViewActive, it
                                )
                            }
                        }
                    }

                    GestureType.FlickRight -> {
                        when (currentInputMode.get()) {
                            InputMode.ModeJapanese -> {
                                popTextActive.setTabletTextFlickRightJapanese(it.id)
                                if (isLongPressed) popTextCenter.setTabletTextTapJapanese(it.id)
                            }

                            InputMode.ModeEnglish -> {
                                return
                            }

                            InputMode.ModeNumber -> {
                                return
                            }
                        }
                        if (isLongPressed) {
                            if (popTextActive.text.isNotEmpty()) {
                                popupWindowActive.setPopUpWindowRight(
                                    context, bubbleViewActive, it
                                )
                                popupWindowCenter.setPopUpWindowCenter(
                                    context, bubbleViewCenter, it
                                )
                            }
                        } else {
                            if (popTextActive.text.isNotEmpty()) {
                                popupWindowActive.setPopUpWindowFlickRight(
                                    context, bubbleViewActive, it
                                )
                            }
                        }
                    }

                    GestureType.FlickBottom -> {
                        when (currentInputMode.get()) {
                            InputMode.ModeJapanese -> {
                                popTextActive.setTabletTextFlickBottomJapanese(it.id)
                                if (isLongPressed) popTextCenter.setTabletTextTapJapanese(it.id)
                            }

                            InputMode.ModeEnglish -> {
                                return
                            }

                            InputMode.ModeNumber -> {
                                return
                            }
                        }
                        if (isLongPressed) {
                            if (popTextActive.text.isNotEmpty()) {
                                popupWindowActive.setPopUpWindowBottom(
                                    context, bubbleViewActive, it
                                )
                                popupWindowCenter.setPopUpWindowCenter(
                                    context, bubbleViewCenter, it
                                )
                            }
                        } else {
                            if (popTextActive.text.isNotEmpty()) {
                                popupWindowActive.setPopUpWindowFlickBottom(
                                    context, bubbleViewActive, it
                                )
                            }
                        }
                    }

                    else -> {

                    }
                }
                it.isPressed = false
            }
        }
    }

    private fun setFlickActionPointerDown(keyInfo: KeyInfo, gestureType: GestureType) {
        if (keyInfo is KeyInfo.KeyTapFlickInfo) {
            val charToSend = if (currentInputMode.get() == InputMode.ModeEnglish) {
                val capState = tabletCapsLockState.value
                keyInfo.getOutputChar(capState)
            } else {
                when (gestureType) {
                    GestureType.Tap -> keyInfo.tap
                    GestureType.FlickLeft -> keyInfo.flickLeft
                    GestureType.FlickTop -> keyInfo.flickTop
                    GestureType.FlickRight -> keyInfo.flickRight
                    GestureType.FlickBottom -> keyInfo.flickBottom
                    GestureType.Down -> null
                    GestureType.Null -> null
                }
            }
            flickListener?.onFlick(
                gestureType = gestureType,
                key = pressedKey.key,
                char = charToSend,
            )
            val button = getButtonFromKey(pressedKey.key)
            if (currentInputMode.get() == InputMode.ModeEnglish && tabletCapsLockState.value.capsLockOn) {
                button?.let {
                    if (it is AppCompatButton) {
                        it.setTabletKeyTextEnglishCaps(it.id)
                    }
                }
            } else {
                button?.let {
                    if (it is AppCompatButton) {
                        when (currentInputMode.get()) {
                            InputMode.ModeJapanese -> {
                                it.setTabletKeyTextJapanese(it.id)
                            }

                            InputMode.ModeEnglish -> {
                                it.setTabletKeyTextEnglish(it.id)
                            }

                            InputMode.ModeNumber -> {
                                it.setTabletKeyTextNumber(it.id)
                            }
                        }
                    }
                }
            }
        }
    }

    fun setSideKeyEnterDrawable(drawable: Drawable?) {
        binding.keyEnter.setImageDrawable(drawable)
    }

    fun setSideKeySpaceDrawable(drawable: Drawable?) {
        binding.keySpace.setImageDrawable(drawable)
    }

    fun setSideKeyPreviousState(state: Boolean) {
        binding.keyPrevious.isEnabled = state
    }

    fun setInputModeSwitchState() {
        val inputMode = currentInputMode.get()
        binding.keySwitchKeyMode.setInputMode(inputMode, true)
        handleCurrentInputModeSwitch(inputMode)
    }

    fun setFlickSensitivityValue(sensitivity: Int) {
        flickSensitivity = sensitivity
    }

    private fun handleCurrentInputModeSwitch(inputMode: InputMode) {
        when (inputMode) {
            InputMode.ModeJapanese -> {
                setKeysInJapaneseText()
            }

            InputMode.ModeEnglish -> {
                setKeysInEnglishText(false)
            }

            InputMode.ModeNumber -> {
                setKeysInNumberText(false)
            }
        }
        clearShiftCaps()
    }

    private fun handleClickInputModeSwitch() {
        val newInputMode = when (currentInputMode.get()) {
            InputMode.ModeJapanese -> {
                setKeysInEnglishText(false)
                binding.apply {
                    /** は行の margin を削除 **/
                    key26.setMarginEnd(0f)
                    key27.setMarginEnd(0f)
                    key28.setMarginEnd(0f)
                    key29.setMarginEnd(0f)
                    key30.setMarginEnd(0f)

                    /** 最後の行の margin を削除 **/
                    key1.setMarginEnd(0f)
                    key2.setMarginEnd(0f)
                    key3.setMarginEnd(0f)
                    key4.setMarginEnd(0f)
                    key5.setMarginEnd(0f)

                    /** わ行を削除 **/
                    key6.isVisible = false
                    key7.isVisible = false
                    key8.isVisible = false
                    key9.isVisible = false
                    key10.isVisible = false

                    /** 句点ボタンの width を 2 にする **/
                    key5.setHorizontalWeight(2f)
                    /** ろボタンを隠す **/
                    key15.isVisible = false
                    /** れボタンを句点ボタンの上に配置 **/
                    key14.setBottomToTopOf(key5)
                    /** 句点ボタンをよボタンの左に配置 **/
                    key5.setEndToStartOf(key20)

                    /** よボタンの width を 2 にする **/
                    key20.setHorizontalWeight(2f)
                    /** もボタンを隠す **/
                    key25.isVisible = false
                    /** めボタンをよボタンの上部に配置 **/
                    key24.setBottomToTopOf(key20)
                    /** よボタンをほボタンの左に配置 **/
                    key20.setEndToStartOf(key30)
                    /** ほボタンをよボタンの右に配置 **/
                    key30.setStartToEndOf(key20)

                    /** おボタンの width を 2 にする **/
                    key55.setHorizontalWeight(2f)
                    key50.isVisible = false
                    key49.setBottomToTopOf(key55)
                    key55.setStartToEndOf(key45)
                    key45.setEndToStartOf(key55)

                }
                clearShiftCaps()
                InputMode.ModeEnglish
            }

            InputMode.ModeEnglish -> {
                setKeysInNumberText(false)
                binding.apply {
                    /** や行を削除 **/
                    key16.isVisible = false
                    key17.isVisible = false
                    key18.isVisible = false
                    key19.isVisible = false
                    key20.isVisible = false
                    /** ま行を削除 **/
                    key21.isVisible = false
                    key22.isVisible = false
                    key23.isVisible = false
                    key24.isVisible = false

                    key36.setMarginEnd(2f)
                    key37.setMarginEnd(2f)
                    key38.setMarginEnd(2f)
                    key39.setMarginEnd(2f)
                    key40.setMarginEnd(2f)

                    if (tabletCapsLockState.value.capsLockOn || tabletCapsLockState.value.shiftOn) {
                        if (isDynamicColorsEnable) {
                            binding.key5.background = ContextCompat.getDrawable(
                                this@TabletKeyboardView.context,
                                com.kazumaproject.core.R.drawable.selector_corner_bottom_left_material
                            )
                        } else {
                            binding.key5.background = ContextCompat.getDrawable(
                                this@TabletKeyboardView.context,
                                com.kazumaproject.core.R.drawable.selector_corner_bottom_left
                            )
                        }
                    }

                    if (tabletCapsLockState.value.zenkakuOn) {
                        if (isDynamicColorsEnable) {
                            binding.key55.background = ContextCompat.getDrawable(
                                this@TabletKeyboardView.context,
                                com.kazumaproject.core.R.drawable.selector_corner_bottom_right_material
                            )
                        } else {
                            binding.key55.background = ContextCompat.getDrawable(
                                this@TabletKeyboardView.context,
                                com.kazumaproject.core.R.drawable.selector_corner_bottom_right
                            )
                        }
                    }
                }
                clearShiftCaps()
                InputMode.ModeNumber
            }

            InputMode.ModeNumber -> {
                setKeysInJapaneseText()
                binding.apply {
                    /** は行に margin を追加 **/
                    key26.setMarginEnd(2f)
                    key27.setMarginEnd(2f)
                    key28.setMarginEnd(2f)
                    key29.setMarginEnd(2f)
                    key30.setMarginEnd(2f)

                    /** 最後の行に margin を追加 **/
                    key1.setMarginEnd(2f)
                    key2.setMarginEnd(2f)
                    key3.setMarginEnd(2f)
                    key4.setMarginEnd(2f)
                    key5.setMarginEnd(2f)

                    /** わ行を追加 **/
                    key6.isVisible = true
                    key7.isVisible = true
                    key8.isVisible = true
                    key9.isVisible = true
                    key10.isVisible = true

                    /** 句点ボタンの width を 1 にする **/
                    key5.setHorizontalWeight(1f)
                    /** ろボタンを表示 **/
                    key15.isVisible = true
                    key14.setBottomToTopOf(key15)
                    key5.setEndToStartOf(key10)
                    key10.setStartToEndOf(key5)
                    /** もボタンを表示 **/
                    key25.isVisible = true
                    /** めボタンをよボタンの上部に配置 **/
                    key24.setBottomToTopOf(key25)
                    key20.setEndToStartOf(key25)
                    key30.setStartToEndOf(key25)

                    /** おボタンの width を 1 にする **/
                    key55.setHorizontalWeight(1f)
                    key50.isVisible = true
                    key49.setBottomToTopOf(key50)
                    key55.setStartToEndOf(key50)
                    key45.setEndToStartOf(key50)

                    /** や行を追加 **/
                    key16.isVisible = true
                    key17.isVisible = true
                    key18.isVisible = true
                    key19.isVisible = true
                    key20.isVisible = true
                    /** ま行を追加 **/
                    key21.isVisible = true
                    key22.isVisible = true
                    key23.isVisible = true
                    key24.isVisible = true

                    key36.setMarginEnd(0f)
                    key37.setMarginEnd(0f)
                    key38.setMarginEnd(0f)
                    key39.setMarginEnd(0f)
                    key40.setMarginEnd(0f)

                    if (isDynamicColorsEnable) {
                        key55.background = ContextCompat.getDrawable(
                            this@TabletKeyboardView.context,
                            com.kazumaproject.core.R.drawable.selector_corner_bottom_right_material
                        )
                    }
                }
                clearShiftCaps()
                InputMode.ModeJapanese
            }
        }
        currentInputMode.set(newInputMode)
        binding.keySwitchKeyMode.setInputMode(newInputMode, isTablet = true)
    }

    fun resetLayout() {
        Log.d("resetLayout", "called: ${tabletCapsLockState.value} ${currentInputMode.get()}")
        when (currentInputMode.get()) {
            InputMode.ModeJapanese -> {
                binding.apply {
                    /** は行に margin を追加 **/
                    key26.setMarginEnd(2f)
                    key27.setMarginEnd(2f)
                    key28.setMarginEnd(2f)
                    key29.setMarginEnd(2f)
                    key30.setMarginEnd(2f)

                    /** 最後の行に margin を追加 **/
                    key1.setMarginEnd(2f)
                    key2.setMarginEnd(2f)
                    key3.setMarginEnd(2f)
                    key4.setMarginEnd(2f)
                    key5.setMarginEnd(2f)

                    /** わ行を追加 **/
                    key6.isVisible = true
                    key7.isVisible = true
                    key8.isVisible = true
                    key9.isVisible = true
                    key10.isVisible = true

                    /** 句点ボタンの width を 1 にする **/
                    key5.setHorizontalWeight(1f)
                    /** ろボタンを表示 **/
                    key15.isVisible = true
                    key14.setBottomToTopOf(key15)
                    key5.setEndToStartOf(key10)
                    key10.setStartToEndOf(key5)
                    /** もボタンを表示 **/
                    key25.isVisible = true
                    /** めボタンをよボタンの上部に配置 **/
                    key24.setBottomToTopOf(key25)
                    key20.setEndToStartOf(key25)
                    key30.setStartToEndOf(key25)

                    /** おボタンの width を 1 にする **/
                    key55.setHorizontalWeight(1f)
                    key50.isVisible = true
                    key49.setBottomToTopOf(key50)
                    key55.setStartToEndOf(key50)
                    key45.setEndToStartOf(key50)

                    /** や行を追加 **/
                    key16.isVisible = true
                    key17.isVisible = true
                    key18.isVisible = true
                    key19.isVisible = true
                    key20.isVisible = true
                    /** ま行を追加 **/
                    key21.isVisible = true
                    key22.isVisible = true
                    key23.isVisible = true
                    key24.isVisible = true

                    key36.setMarginEnd(0f)
                    key37.setMarginEnd(0f)
                    key38.setMarginEnd(0f)
                    key39.setMarginEnd(0f)
                    key40.setMarginEnd(0f)
                    if (isDynamicColorsEnable) {
                        key5.background = ContextCompat.getDrawable(
                            this@TabletKeyboardView.context,
                            com.kazumaproject.core.R.drawable.selector_corner_bottom_left_material
                        )
                        key55.background = ContextCompat.getDrawable(
                            this@TabletKeyboardView.context,
                            com.kazumaproject.core.R.drawable.selector_corner_bottom_right_material
                        )
                    }
                }
                clearShiftCaps()
            }

            InputMode.ModeEnglish -> {
                binding.apply {
                    /** は行の margin を削除 **/
                    key26.setMarginEnd(0f)
                    key27.setMarginEnd(0f)
                    key28.setMarginEnd(0f)
                    key29.setMarginEnd(0f)
                    key30.setMarginEnd(0f)

                    /** 最後の行の margin を削除 **/
                    key1.setMarginEnd(0f)
                    key2.setMarginEnd(0f)
                    key3.setMarginEnd(0f)
                    key4.setMarginEnd(0f)
                    key5.setMarginEnd(0f)

                    /** わ行を削除 **/
                    key6.isVisible = false
                    key7.isVisible = false
                    key8.isVisible = false
                    key9.isVisible = false
                    key10.isVisible = false

                    /** 句点ボタンの width を 2 にする **/
                    key5.setHorizontalWeight(2f)
                    /** ろボタンを隠す **/
                    key15.isVisible = false
                    /** れボタンを句点ボタンの上に配置 **/
                    key14.setBottomToTopOf(key5)
                    /** 句点ボタンをよボタンの左に配置 **/
                    key5.setEndToStartOf(key20)

                    /** よボタンの width を 2 にする **/
                    key20.setHorizontalWeight(2f)
                    /** もボタンを隠す **/
                    key25.isVisible = false
                    /** めボタンをよボタンの上部に配置 **/
                    key24.setBottomToTopOf(key20)
                    /** よボタンをほボタンの左に配置 **/
                    key20.setEndToStartOf(key30)
                    /** ほボタンをよボタンの右に配置 **/
                    key30.setStartToEndOf(key20)

                    /** おボタンの width を 2 にする **/
                    key55.setHorizontalWeight(2f)
                    key50.isVisible = false
                    key49.setBottomToTopOf(key55)
                    key55.setStartToEndOf(key45)
                    key45.setEndToStartOf(key55)

                    if (isDynamicColorsEnable) {
                        key5.background = ContextCompat.getDrawable(
                            this@TabletKeyboardView.context,
                            com.kazumaproject.core.R.drawable.selector_corner_bottom_left_material
                        )
                        key55.background = ContextCompat.getDrawable(
                            this@TabletKeyboardView.context,
                            com.kazumaproject.core.R.drawable.selector_corner_bottom_right_material
                        )
                    }
                }
                clearShiftCaps()
            }

            InputMode.ModeNumber -> {
                binding.apply {
                    /** や行を削除 **/
                    key16.isVisible = false
                    key17.isVisible = false
                    key18.isVisible = false
                    key19.isVisible = false
                    key20.isVisible = false
                    /** ま行を削除 **/
                    key21.isVisible = false
                    key22.isVisible = false
                    key23.isVisible = false
                    key24.isVisible = false

                    key36.setMarginEnd(2f)
                    key37.setMarginEnd(2f)
                    key38.setMarginEnd(2f)
                    key39.setMarginEnd(2f)
                    key40.setMarginEnd(2f)

                    if (isDynamicColorsEnable) {
                        key5.background = ContextCompat.getDrawable(
                            this@TabletKeyboardView.context,
                            com.kazumaproject.core.R.drawable.selector_corner_bottom_left_material
                        )

                        key55.background = ContextCompat.getDrawable(
                            this@TabletKeyboardView.context,
                            com.kazumaproject.core.R.drawable.selector_corner_bottom_right_material
                        )
                    }
                }
                clearShiftCaps()
            }
        }
    }

    private fun setKeysInJapaneseText() {
        allButtonKeys.forEach {
            it.setTabletKeyTextJapanese(keyId = it.id)
        }
        binding.apply {
            key5.setLargeUnicodeIconScaleX(
                icon = resources.getString(com.kazumaproject.core.R.string.string_kuten),
                scaleX = 1f
            )
            key20.setLargeUnicodeIconScaleX(
                icon = resources.getString(com.kazumaproject.core.R.string.string_よ), scaleX = 1f
            )
        }
    }

    private fun setKeysInEnglishText(isZenkaku: Boolean) = binding.run {
        allButtonKeys.filterNot { it == key5 }.forEach { it.setTabletKeyTextEnglish(it.id) }

        val shiftOn = tabletCapsLockState.value.capsLockOn
        val ctx = root.context
        key5.apply {
            setLargeUnicodeIconScaleX(
                icon = ctx.getString(
                    if (shiftOn) com.kazumaproject.core.R.string.caps_lock_icon
                    else com.kazumaproject.core.R.string.shift_symbol
                ), scaleX = 1.618f
            )
            if (isDynamicColorsEnable) {
                background = ContextCompat.getDrawable(
                    ctx, com.kazumaproject.core.R.drawable.selector_corner_bottom_left_material
                )
            }
        }
        key20.setLargeUnicodeIcon(
            icon = ctx.getString(com.kazumaproject.core.R.string.undo_symbol)
        )
        val zenkakuBg = if (isZenkaku) com.kazumaproject.core.R.drawable.zenkaku_pressed_bg
        else {
            if (isDynamicColorsEnable) {
                com.kazumaproject.core.R.drawable.selector_corner_bottom_right_material
            } else {
                com.kazumaproject.core.R.drawable.selector_corner_bottom_right
            }
        }

        key55.background = ContextCompat.getDrawable(ctx, zenkakuBg)
    }

    private fun setKeysInEnglishCapsOnText(isZenkaku: Boolean) = binding.run {
        allButtonKeys.filterNot { it == key5 }.forEach { it.setTabletKeyTextEnglishCaps(it.id) }

        val ctx = root.context
        key5.apply {
            setLargeUnicodeIconScaleX(
                icon = ctx.getString(com.kazumaproject.core.R.string.caps_lock_icon),
                scaleX = 1.618f,
                iconSizeSp = 30
            )
            background = ContextCompat.getDrawable(
                this@TabletKeyboardView.context, com.kazumaproject.core.R.drawable.caps_lock_on_bg
            )
        }
        key20.setLargeUnicodeIcon(
            icon = ctx.getString(com.kazumaproject.core.R.string.undo_symbol)
        )
        val bgRes = if (isZenkaku) com.kazumaproject.core.R.drawable.zenkaku_pressed_bg
        else {
            if (isDynamicColorsEnable) {
                com.kazumaproject.core.R.drawable.selector_corner_bottom_right_material
            } else {
                com.kazumaproject.core.R.drawable.selector_corner_bottom_right
            }
        }
        key55.background = ContextCompat.getDrawable(ctx, bgRes)
    }

    private fun setKeysInEnglishShiftOnText(isZenkaku: Boolean) = binding.run {
        allButtonKeys.filterNot { it == key5 }.forEach { it.setTabletKeyTextEnglishCaps(it.id) }

        val ctx = root.context

        key5.apply {
            setLargeUnicodeIconScaleX(
                icon = ctx.getString(com.kazumaproject.core.R.string.shift_symbol), scaleX = 1.618f
            )
            background = ContextCompat.getDrawable(
                ctx, com.kazumaproject.core.R.drawable.caps_lock_on_bg
            )
        }

        key20.setLargeUnicodeIcon(
            icon = ctx.getString(com.kazumaproject.core.R.string.undo_symbol)
        )

        val bgRes = if (isZenkaku) com.kazumaproject.core.R.drawable.zenkaku_pressed_bg
        else {
            if (isDynamicColorsEnable) {
                com.kazumaproject.core.R.drawable.selector_corner_bottom_right_material
            } else {
                com.kazumaproject.core.R.drawable.selector_corner_bottom_right
            }
        }
        key55.background = ContextCompat.getDrawable(ctx, bgRes)
    }

    private fun setKeysInNumberText(isZenkaku: Boolean) {
        allButtonKeys.forEach { button ->
            if (button != binding.key5) button.setTabletKeyTextNumber(keyId = button.id)
        }
        binding.apply {
            val ctx = root.context
            key5.setLargeUnicodeIconScaleX(
                icon = resources.getString(com.kazumaproject.core.R.string.tablet_number_command),
            )
            val zenkakuBg = if (isZenkaku) com.kazumaproject.core.R.drawable.zenkaku_pressed_bg
            else {
                if (isDynamicColorsEnable) {
                    com.kazumaproject.core.R.drawable.selector_corner_bottom_right_material
                } else {
                    com.kazumaproject.core.R.drawable.selector_corner_bottom_right
                }
            }

            key55.background = ContextCompat.getDrawable(ctx, zenkakuBg)
        }
    }

}
