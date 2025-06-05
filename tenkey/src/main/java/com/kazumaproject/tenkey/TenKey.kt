package com.kazumaproject.tenkey

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.PopupWindow
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.core.Constants.DEFAULT_TAP_RANGE_SMART_PHONE
import com.kazumaproject.core.domain.extensions.hide
import com.kazumaproject.core.domain.extensions.layoutXPosition
import com.kazumaproject.core.domain.extensions.layoutYPosition
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.key.KeyInfo
import com.kazumaproject.core.domain.key.KeyMap
import com.kazumaproject.core.domain.key.KeyRect
import com.kazumaproject.core.domain.listener.FlickListener
import com.kazumaproject.core.domain.listener.LongPressListener
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.InputMode.ModeEnglish.next
import com.kazumaproject.core.domain.state.PressedKey
import com.kazumaproject.core.ui.effect.Blur
import com.kazumaproject.core.ui.input_mode_witch.InputModeSwitch
import com.kazumaproject.core.ui.key_window.KeyWindowLayout
import com.kazumaproject.tenkey.databinding.KeyboardLayoutBinding
import com.kazumaproject.tenkey.databinding.PopupLayoutActiveBinding
import com.kazumaproject.tenkey.databinding.PopupLayoutBinding
import com.kazumaproject.tenkey.extensions.setPopUpWindowBottom
import com.kazumaproject.tenkey.extensions.setPopUpWindowCenter
import com.kazumaproject.tenkey.extensions.setPopUpWindowFlickBottom
import com.kazumaproject.tenkey.extensions.setPopUpWindowFlickLeft
import com.kazumaproject.tenkey.extensions.setPopUpWindowFlickRight
import com.kazumaproject.tenkey.extensions.setPopUpWindowFlickTop
import com.kazumaproject.tenkey.extensions.setPopUpWindowLeft
import com.kazumaproject.tenkey.extensions.setPopUpWindowRight
import com.kazumaproject.tenkey.extensions.setPopUpWindowTop
import com.kazumaproject.tenkey.extensions.setTenKeyTextEnglish
import com.kazumaproject.tenkey.extensions.setTenKeyTextJapanese
import com.kazumaproject.tenkey.extensions.setTenKeyTextNumber
import com.kazumaproject.tenkey.extensions.setTenKeyTextWhenTapEnglish
import com.kazumaproject.tenkey.extensions.setTenKeyTextWhenTapJapanese
import com.kazumaproject.tenkey.extensions.setTenKeyTextWhenTapNumber
import com.kazumaproject.tenkey.extensions.setTextFlickBottomEnglish
import com.kazumaproject.tenkey.extensions.setTextFlickBottomJapanese
import com.kazumaproject.tenkey.extensions.setTextFlickBottomNumber
import com.kazumaproject.tenkey.extensions.setTextFlickLeftEnglish
import com.kazumaproject.tenkey.extensions.setTextFlickLeftJapanese
import com.kazumaproject.tenkey.extensions.setTextFlickLeftNumber
import com.kazumaproject.tenkey.extensions.setTextFlickRightEnglish
import com.kazumaproject.tenkey.extensions.setTextFlickRightJapanese
import com.kazumaproject.tenkey.extensions.setTextFlickRightNumber
import com.kazumaproject.tenkey.extensions.setTextFlickTopEnglish
import com.kazumaproject.tenkey.extensions.setTextFlickTopJapanese
import com.kazumaproject.tenkey.extensions.setTextFlickTopNumber
import com.kazumaproject.tenkey.extensions.setTextTapEnglish
import com.kazumaproject.tenkey.extensions.setTextTapJapanese
import com.kazumaproject.tenkey.extensions.setTextTapNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
class TenKey(context: Context, attributeSet: AttributeSet) :
    ConstraintLayout(context, attributeSet), View.OnTouchListener {

    // ViewBinding for the main keyboard layout
    private val binding: KeyboardLayoutBinding

    // KeyMap to decide which character to send on tap/flick
    private var keyMap: KeyMap

    // For handling long-press detection
    private var longPressJob: Job? = null
    private var isLongPressed = false

    // Track which key is currently pressed
    private lateinit var pressedKey: PressedKey

    // External listeners
    private var flickListener: FlickListener? = null
    private var longPressListener: LongPressListener? = null

    /** ← REPLACED AtomicReference with StateFlow **/
    private val _currentInputMode = MutableStateFlow<InputMode>(InputMode.ModeJapanese)
    val currentInputMode: StateFlow<InputMode> = _currentInputMode

    // Popups: active (center) and directional
    private val popupWindowActive: PopupWindow
    private val bubbleViewActive: KeyWindowLayout
    private val popTextActive: MaterialTextView

    private val popupWindowLeft: PopupWindow
    private val bubbleViewLeft: KeyWindowLayout
    private val popTextLeft: MaterialTextView

    private val popupWindowTop: PopupWindow
    private val bubbleViewTop: KeyWindowLayout
    private val popTextTop: MaterialTextView

    private val popupWindowRight: PopupWindow
    private val bubbleViewRight: KeyWindowLayout
    private val popTextRight: MaterialTextView

    private val popupWindowBottom: PopupWindow
    private val bubbleViewBottom: KeyWindowLayout
    private val popTextBottom: MaterialTextView

    private val popupWindowCenter: PopupWindow
    private val bubbleViewCenter: KeyWindowLayout
    private val popTextCenter: MaterialTextView

    // Map each Key enum to its corresponding View (Button/ImageButton/Switch)
    private var listKeys: Map<Key, Any>

    private var isSelectMode = false

    /** ← NEW: scope tied to this view; cancel it on detach **/
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        // Inflate the keyboard layout with ViewBinding (root is <merge>, so attachToParent = true)
        val inflater = LayoutInflater.from(context)
        binding = KeyboardLayoutBinding.inflate(inflater, this)

        // Initialize keyMap
        keyMap = KeyMap()

        // Prepare popups using their own bindings
        // --- Active popup (center) ---
        val activeBinding = PopupLayoutActiveBinding.inflate(inflater, null, false)
        popupWindowActive = PopupWindow(
            activeBinding.root, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, false
        )
        bubbleViewActive = activeBinding.bubbleLayoutActive
        popTextActive = activeBinding.popupTextActive

        // --- Left popup ---
        val leftBinding = PopupLayoutBinding.inflate(inflater, null, false)
        popupWindowLeft = PopupWindow(
            leftBinding.root, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, false
        )
        bubbleViewLeft = leftBinding.bubbleLayout
        popTextLeft = leftBinding.popupText

        // --- Top popup ---
        val topBinding = PopupLayoutBinding.inflate(inflater, null, false)
        popupWindowTop = PopupWindow(
            topBinding.root, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, false
        )
        bubbleViewTop = topBinding.bubbleLayout
        popTextTop = topBinding.popupText

        // --- Right popup ---
        val rightBinding = PopupLayoutBinding.inflate(inflater, null, false)
        popupWindowRight = PopupWindow(
            rightBinding.root, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, false
        )
        bubbleViewRight = rightBinding.bubbleLayout
        popTextRight = rightBinding.popupText

        // --- Bottom popup ---
        val bottomBinding = PopupLayoutBinding.inflate(inflater, null, false)
        popupWindowBottom = PopupWindow(
            bottomBinding.root, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, false
        )
        bubbleViewBottom = bottomBinding.bubbleLayout
        popTextBottom = bottomBinding.popupText

        // --- Center popup (for long‐press + flick previews) ---
        val centerBinding = PopupLayoutBinding.inflate(inflater, null, false)
        popupWindowCenter = PopupWindow(
            centerBinding.root, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, false
        )
        bubbleViewCenter = centerBinding.bubbleLayout
        popTextCenter = centerBinding.popupText

        // Build the map from Key enum to actual View references
        listKeys = mapOf(
            Key.KeyA to binding.key1,
            Key.KeyKA to binding.key2,
            Key.KeySA to binding.key3,
            Key.KeyTA to binding.key4,
            Key.KeyNA to binding.key5,
            Key.KeyHA to binding.key6,
            Key.KeyMA to binding.key7,
            Key.KeyYA to binding.key8,
            Key.KeyRA to binding.key9,
            Key.KeyWA to binding.key11,
            Key.KeyKutouten to binding.key12,
            Key.KeyDakutenSmall to binding.keySmallLetter,
            Key.SideKeyPreviousChar to binding.keyReturn,
            Key.SideKeyCursorLeft to binding.keySoftLeft,
            Key.SideKeyCursorRight to binding.keyMoveCursorRight,
            Key.SideKeySymbol to binding.sideKeySymbol,
            Key.SideKeyInputMode to binding.keySwitchKeyMode,
            Key.SideKeyDelete to binding.keyDelete,
            Key.SideKeySpace to binding.keySpace,
            Key.SideKeyEnter to binding.keyEnter
        )

        // Make all key views non-focusable so touches go directly to onTouch
        setViewsNotFocusable()

        // Initially display Japanese text on main keys
        binding.key12.setTenKeyTextJapanese(binding.key12.id)

        // Set default drawable for small/dakuten key
        setBackgroundSmallLetterKey()

        // Attach this view as its own touch listener
        this.setOnTouchListener(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.focusable = View.NOT_FOCUSABLE
        } else {
            this.isFocusable = false
        }

        // ← NEW: launch a coroutine to observe changes to currentInputMode
        scope.launch {
            currentInputMode.collect { inputMode ->
                // Whenever inputMode changes, update all keys and switch UI
                handleCurrentInputModeSwitch(inputMode)
                binding.keySwitchKeyMode.setInputMode(inputMode, false)
            }
        }
    }

    fun setCurrentMode(inputMode: InputMode) {
        _currentInputMode.update { inputMode }
    }

    /** Allow setting an external FlickListener **/
    fun setOnFlickListener(flickListener: FlickListener) {
        this.flickListener = flickListener
    }

    /** Allow setting an external LongPressListener **/
    fun setOnLongPressListener(longPressListener: LongPressListener) {
        this.longPressListener = longPressListener
    }

    /** Padding setters for side keys (symbol, cursors, delete, enter, previous char) **/
    fun setPaddingToSideKeySymbol(paddingSize: Int) {
        binding.sideKeySymbol.setPadding(paddingSize)
    }

    fun setTextToAllButtons(isSelecMode: Boolean) {
        if (isSelecMode) {
            setKeysInEmptyText()
        } else {
            handleCurrentInputModeSwitch(currentInputMode.value)
        }
        this.isSelectMode = isSelecMode
    }

    /** Clean up references when view is detached **/
    private fun release() {
        flickListener = null
        longPressListener = null
        longPressJob?.cancel()
        longPressJob = null

        isSelectMode = false
        // ← CANCEL the observing coroutine when the view is detached
        scope.coroutineContext.cancelChildren()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
    }

    /** Intercept all touch events so we can handle them manually in onTouch **/
    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View?, event: MotionEvent?): Boolean {
        if (view != null && event != null) {
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    val key = pressedKeyByMotionEvent(event, 0)
                    flickListener?.onFlick(GestureType.Down, key, null)

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
                    if (isSelectMode) {
                        // 1️⃣ Key→View のマッピングだけ行う
                        val viewToPress: View? = when (key) {
                            Key.KeyA -> binding.key1
                            Key.KeySA -> binding.key3
                            Key.KeyMA -> binding.key7
                            Key.KeyRA -> binding.key9
                            Key.SideKeyDelete -> binding.keyDelete
                            Key.SideKeyCursorRight -> binding.keyMoveCursorRight
                            Key.SideKeyCursorLeft -> binding.keySoftLeft
                            Key.SideKeySpace -> binding.keySpace
                            else -> null
                        }

                        // 2️⃣ 該当する View があれば isPressed を設定
                        viewToPress?.let { keyButton ->
                            keyButton.isPressed = true

                            // 3️⃣ 長押しをサポートするキーであれば、ジョブを立ち上げる
                            when (key) {
                                Key.SideKeyDelete,
                                Key.SideKeyCursorRight,
                                Key.SideKeyCursorLeft -> {
                                    longPressJob?.cancel()  // 必要に応じて previous job をキャンセル
                                    longPressJob = CoroutineScope(Dispatchers.Main).launch {
                                        delay(ViewConfiguration.getLongPressTimeout().toLong())
                                        if (pressedKey.key != Key.NotSelected) {
                                            longPressListener?.onLongPress(pressedKey.key)
                                            isLongPressed = true
                                            onLongPressed()
                                        }
                                    }
                                }

                                else -> {
                                    // 長押しなしのキーはここでは何もしない
                                }
                            }
                        }

                        return true
                    }
                    setKeyPressed()
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
                    if (isSelectMode) {
                        // Map each Key to its corresponding button/view
                        val viewToRelease: View? = when (pressedKey.key) {
                            Key.KeyA -> binding.key1
                            Key.KeySA -> binding.key3
                            Key.KeyMA -> binding.key7
                            Key.KeyRA -> binding.key9
                            Key.SideKeyDelete -> binding.keyDelete
                            Key.SideKeyCursorRight -> binding.keyMoveCursorRight
                            Key.SideKeyCursorLeft -> binding.keySoftLeft
                            Key.SideKeySpace -> binding.keySpace
                            else -> null
                        }

                        viewToRelease?.let { key ->
                            key.isPressed = false
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = pressedKey.key,
                                char = null
                            )
                        }

                        return false
                    }
                    if (pressedKey.pointer == event.getPointerId(event.actionIndex)) {
                        val gestureType = getGestureType(event)
                        // ← READING the state flow's current value:
                        val keyInfo = currentInputMode.value
                            .next(keyMap = keyMap, key = pressedKey.key, isTablet = false)

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
                                GestureType.Tap -> flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = pressedKey.key,
                                    char = keyInfo.tap
                                )

                                GestureType.FlickLeft -> flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = pressedKey.key,
                                    char = keyInfo.flickLeft
                                )

                                GestureType.FlickTop -> flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = pressedKey.key,
                                    char = keyInfo.flickTop
                                )

                                GestureType.FlickRight -> flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = pressedKey.key,
                                    char = keyInfo.flickRight
                                )

                                GestureType.FlickBottom -> flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = pressedKey.key,
                                    char = keyInfo.flickBottom
                                )
                            }
                        }
                    }
                    resetAllKeys()
                    popupWindowActive.hide()
                    val button = getButtonFromKey(pressedKey.key)
                    button?.let {
                        if (it is AppCompatButton) {
                            if (it == binding.sideKeySymbol) return false
                            // ← UPDATE: use state flow's value to set text after finger-up
                            when (currentInputMode.value) {
                                InputMode.ModeJapanese -> it.setTenKeyTextJapanese(it.id)
                                InputMode.ModeEnglish -> it.setTenKeyTextEnglish(it.id)
                                InputMode.ModeNumber -> it.setTenKeyTextNumber(it.id)
                            }
                        }
                        if (it is AppCompatImageButton && currentInputMode.value == InputMode.ModeNumber && it == binding.keySmallLetter) {
                            it.setImageDrawable(
                                ContextCompat.getDrawable(
                                    context, com.kazumaproject.core.R.drawable.number_small
                                )
                            )
                        }
                    }
                    return false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isSelectMode) return false
                    val gestureType = if (event.pointerCount == 1) {
                        getGestureType(event, 0)
                    } else {
                        getGestureType(event, pressedKey.pointer)
                    }
                    when (gestureType) {
                        GestureType.Null -> {}
                        GestureType.Down -> {}
                        GestureType.Tap -> setTapInActionMove()
                        GestureType.FlickLeft, GestureType.FlickTop, GestureType.FlickRight, GestureType.FlickBottom -> setFlickInActionMove(
                            gestureType
                        )
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
                    if (isSelectMode) return true
                    if (event.pointerCount == 2) {
                        isLongPressed = false
                        val pointer = event.getPointerId(event.actionIndex)
                        val key = pressedKeyByMotionEvent(event, pointer)
                        val gestureType2 = getGestureType(
                            event, if (pointer == 0) 1 else 0
                        )
                        if (pressedKey.key == Key.KeyDakutenSmall && currentInputMode.value == InputMode.ModeNumber) {
                            binding.keySmallLetter.setImageDrawable(
                                ContextCompat.getDrawable(
                                    context, com.kazumaproject.core.R.drawable.number_small
                                )
                            )
                        }
                        val keyInfo = currentInputMode.value
                            .next(keyMap = keyMap, key = pressedKey.key, isTablet = false)
                        if (keyInfo == KeyInfo.Null) {
                            flickListener?.onFlick(
                                gestureType = gestureType2, key = pressedKey.key, char = null
                            )
                        } else if (keyInfo is KeyInfo.KeyTapFlickInfo) {
                            when (gestureType2) {
                                GestureType.Null -> {}
                                GestureType.Down -> {}
                                GestureType.Tap -> {
                                    flickListener?.onFlick(
                                        gestureType = gestureType2,
                                        key = pressedKey.key,
                                        char = keyInfo.tap
                                    )
                                    val button = getButtonFromKey(pressedKey.key)
                                    button?.let {
                                        if (it is AppCompatButton) {
                                            if (it == binding.sideKeySymbol) return false
                                            when (currentInputMode.value) {
                                                InputMode.ModeJapanese -> it.setTenKeyTextJapanese(
                                                    it.id
                                                )

                                                InputMode.ModeEnglish -> it.setTenKeyTextEnglish(it.id)
                                                InputMode.ModeNumber -> it.setTenKeyTextNumber(it.id)
                                            }
                                        }
                                        if (it is AppCompatImageButton && currentInputMode.value == InputMode.ModeNumber && it == binding.keySmallLetter) {
                                            it.setImageDrawable(
                                                ContextCompat.getDrawable(
                                                    context,
                                                    com.kazumaproject.core.R.drawable.number_small
                                                )
                                            )
                                        }
                                    }
                                }

                                GestureType.FlickLeft, GestureType.FlickTop, GestureType.FlickRight, GestureType.FlickBottom -> {
                                    setFlickActionPointerDown(keyInfo, gestureType2)
                                }
                            }
                        }
                        pressedKey = pressedKey.copy(
                            key = key, pointer = pointer, initialX = if (pointer == 0) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    event.getRawX(0)
                                } else {
                                    event.getX(0)
                                }
                            } else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    event.getRawX(1)
                                } else {
                                    event.getX(1)
                                }
                            }, initialY = if (pointer == 0) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    event.getRawY(0)
                                } else {
                                    event.getY(0)
                                }
                            } else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    event.getRawY(1)
                                } else {
                                    event.getY(1)
                                }
                            }
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
                            if (isSelectMode) return true
                            val gestureType = getGestureType(
                                event, event.getPointerId(event.actionIndex)
                            )
                            val keyInfo = currentInputMode.value
                                .next(keyMap = keyMap, key = pressedKey.key, isTablet = false)
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
                                    GestureType.Tap -> flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.tap
                                    )

                                    GestureType.FlickLeft -> flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.flickLeft
                                    )

                                    GestureType.FlickTop -> flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.flickTop
                                    )

                                    GestureType.FlickRight -> flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.flickRight
                                    )

                                    GestureType.FlickBottom -> flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.flickBottom
                                    )
                                }
                            }
                            val button = getButtonFromKey(pressedKey.key)
                            button?.let {
                                if (it is AppCompatButton) {
                                    if (it == binding.sideKeySymbol) return false
                                    it.isPressed = false
                                    when (currentInputMode.value) {
                                        InputMode.ModeJapanese -> it.setTenKeyTextJapanese(it.id)
                                        InputMode.ModeEnglish -> it.setTenKeyTextEnglish(it.id)
                                        InputMode.ModeNumber -> it.setTenKeyTextNumber(it.id)
                                    }
                                }
                            }
                            pressedKey = pressedKey.copy(key = Key.NotSelected)
                            popupWindowActive.hide()
                        }
                        return false
                    }
                    return false
                }

                else -> return false
            }
        }
        return false
    }

    /** Handle orientation changes by re‐applying text on all keys **/
    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        newConfig?.apply {
            if (orientation == Configuration.ORIENTATION_PORTRAIT || orientation == Configuration.ORIENTATION_LANDSCAPE) {
                setTextToAllButtons()
            }
        }
    }

    private fun setTextToAllButtons() {
        binding.key1.setTenKeyTextJapanese(binding.key1.id)
        binding.key2.setTenKeyTextJapanese(binding.key2.id)
        binding.key3.setTenKeyTextJapanese(binding.key3.id)
        binding.key4.setTenKeyTextJapanese(binding.key4.id)
        binding.key5.setTenKeyTextJapanese(binding.key5.id)
        binding.key6.setTenKeyTextJapanese(binding.key6.id)
        binding.key7.setTenKeyTextJapanese(binding.key7.id)
        binding.key8.setTenKeyTextJapanese(binding.key8.id)
        binding.key9.setTenKeyTextJapanese(binding.key9.id)
        binding.key11.setTenKeyTextJapanese(binding.key11.id)
    }

    /** Determine which Key enum corresponds to the touch coordinates **/
    private fun pressedKeyByMotionEvent(event: MotionEvent, pointer: Int): Key {
        val (x, y) = getRawCoordinates(event, pointer)

        val keyRects = listOf(
            KeyRect(
                Key.SideKeyPreviousChar,
                binding.keyReturn.layoutXPosition(),
                binding.keyReturn.layoutYPosition(),
                binding.keyReturn.layoutXPosition() + binding.keyReturn.width,
                binding.keyReturn.layoutYPosition() + binding.keyReturn.height
            ), KeyRect(
                Key.KeyA,
                binding.key1.layoutXPosition(),
                binding.key1.layoutYPosition(),
                binding.key1.layoutXPosition() + binding.key1.width,
                binding.key1.layoutYPosition() + binding.key1.height
            ), KeyRect(
                Key.KeyKA,
                binding.key2.layoutXPosition(),
                binding.key2.layoutYPosition(),
                binding.key2.layoutXPosition() + binding.key2.width,
                binding.key2.layoutYPosition() + binding.key2.height
            ), KeyRect(
                Key.KeySA,
                binding.key3.layoutXPosition(),
                binding.key3.layoutYPosition(),
                binding.key3.layoutXPosition() + binding.key3.width,
                binding.key3.layoutYPosition() + binding.key3.height
            ), KeyRect(
                Key.SideKeyDelete,
                binding.keyDelete.layoutXPosition(),
                binding.keyDelete.layoutYPosition(),
                binding.keyDelete.layoutXPosition() + binding.keyDelete.width,
                binding.keyDelete.layoutYPosition() + binding.keyDelete.height
            ), KeyRect(
                Key.SideKeyCursorLeft,
                binding.keySoftLeft.layoutXPosition(),
                binding.keySoftLeft.layoutYPosition(),
                binding.keySoftLeft.layoutXPosition() + binding.keySoftLeft.width,
                binding.keySoftLeft.layoutYPosition() + binding.keySoftLeft.height
            ), KeyRect(
                Key.KeyTA,
                binding.key4.layoutXPosition(),
                binding.key4.layoutYPosition(),
                binding.key4.layoutXPosition() + binding.key4.width,
                binding.key4.layoutYPosition() + binding.key4.height
            ), KeyRect(
                Key.KeyNA,
                binding.key5.layoutXPosition(),
                binding.key5.layoutYPosition(),
                binding.key5.layoutXPosition() + binding.key5.width,
                binding.key5.layoutYPosition() + binding.key5.height
            ), KeyRect(
                Key.KeyHA,
                binding.key6.layoutXPosition(),
                binding.key6.layoutYPosition(),
                binding.key6.layoutXPosition() + binding.key6.width,
                binding.key6.layoutYPosition() + binding.key6.height
            ), KeyRect(
                Key.SideKeyCursorRight,
                binding.keyMoveCursorRight.layoutXPosition(),
                binding.keyMoveCursorRight.layoutYPosition(),
                binding.keyMoveCursorRight.layoutXPosition() + binding.keyMoveCursorRight.width,
                binding.keyMoveCursorRight.layoutYPosition() + binding.keyMoveCursorRight.height
            ), KeyRect(
                Key.SideKeySymbol,
                binding.sideKeySymbol.layoutXPosition(),
                binding.sideKeySymbol.layoutYPosition(),
                binding.sideKeySymbol.layoutXPosition() + binding.sideKeySymbol.width,
                binding.sideKeySymbol.layoutYPosition() + binding.sideKeySymbol.height
            ), KeyRect(
                Key.KeyMA,
                binding.key7.layoutXPosition(),
                binding.key7.layoutYPosition(),
                binding.key7.layoutXPosition() + binding.key7.width,
                binding.key7.layoutYPosition() + binding.key7.height
            ), KeyRect(
                Key.KeyYA,
                binding.key8.layoutXPosition(),
                binding.key8.layoutYPosition(),
                binding.key8.layoutXPosition() + binding.key8.width,
                binding.key8.layoutYPosition() + binding.key8.height
            ), KeyRect(
                Key.KeyRA,
                binding.key9.layoutXPosition(),
                binding.key9.layoutYPosition(),
                binding.key9.layoutXPosition() + binding.key9.width,
                binding.key9.layoutYPosition() + binding.key9.height
            ), KeyRect(
                Key.SideKeySpace,
                binding.keySpace.layoutXPosition(),
                binding.keySpace.layoutYPosition(),
                binding.keySpace.layoutXPosition() + binding.keySpace.width,
                binding.keySpace.layoutYPosition() + binding.keySpace.height
            ), KeyRect(
                Key.SideKeyInputMode,
                binding.keySwitchKeyMode.layoutXPosition(),
                binding.keySwitchKeyMode.layoutYPosition(),
                binding.keySwitchKeyMode.layoutXPosition() + binding.keySwitchKeyMode.width,
                binding.keySwitchKeyMode.layoutYPosition() + binding.keySwitchKeyMode.height
            ), KeyRect(
                Key.KeyDakutenSmall,
                binding.keySmallLetter.layoutXPosition(),
                binding.keySmallLetter.layoutYPosition(),
                binding.keySmallLetter.layoutXPosition() + binding.keySmallLetter.width,
                binding.keySmallLetter.layoutYPosition() + binding.keySmallLetter.height
            ), KeyRect(
                Key.KeyWA,
                binding.key11.layoutXPosition(),
                binding.key11.layoutYPosition(),
                binding.key11.layoutXPosition() + binding.key11.width,
                binding.key11.layoutYPosition() + binding.key11.height
            ), KeyRect(
                Key.KeyKutouten,
                binding.key12.layoutXPosition(),
                binding.key12.layoutYPosition(),
                binding.key12.layoutXPosition() + binding.key12.width,
                binding.key12.layoutYPosition() + binding.key12.height
            ), KeyRect(
                Key.SideKeyEnter,
                binding.keyEnter.layoutXPosition(),
                binding.keyEnter.layoutYPosition(),
                binding.keyEnter.layoutXPosition() + binding.keyEnter.width,
                binding.keyEnter.layoutYPosition() + binding.keyEnter.height
            )
        )

        // If the touch falls inside any key's rectangle, return that enum
        keyRects.forEach { rect ->
            if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
                return rect.key
            }
        }

        // Otherwise return the nearest key by Euclidean distance
        val nearest = keyRects.minByOrNull { rect ->
            val centerX = (rect.left + rect.right) / 2
            val centerY = (rect.top + rect.bottom) / 2
            val dx = x - centerX
            val dy = y - centerY
            dx * dx + dy * dy
        }
        return nearest?.key ?: Key.NotSelected
    }

    /** Get absolute coordinates for the given pointer **/
    private fun getRawCoordinates(event: MotionEvent, pointer: Int): Pair<Float, Float> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            event.getRawX(pointer) to event.getRawY(pointer)
        } else {
            val location = IntArray(2)
            this.getLocationOnScreen(location)
            (event.getX(pointer) + location[0]) to (event.getY(pointer) + location[1])
        }
    }

    /** Determine whether the movement is a tap or a flick in a direction **/
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
            abs(distanceX) < DEFAULT_TAP_RANGE_SMART_PHONE && abs(distanceY) < DEFAULT_TAP_RANGE_SMART_PHONE -> GestureType.Tap
            abs(distanceX) > abs(distanceY) && pressedKey.initialX >= finalX -> GestureType.FlickLeft
            abs(distanceX) <= abs(distanceY) && pressedKey.initialY >= finalY -> GestureType.FlickTop
            abs(distanceX) > abs(distanceY) && pressedKey.initialX < finalX -> GestureType.FlickRight
            abs(distanceX) <= abs(distanceY) && pressedKey.initialY < finalY -> GestureType.FlickBottom
            else -> GestureType.Null
        }
    }

    /** Visually indicate which key is pressed **/
    private fun setKeyPressed() {
        listKeys.forEach { (keyEnum, viewObj) ->
            when (viewObj) {
                is InputModeSwitch -> viewObj.isPressed = (keyEnum == pressedKey.key)
                is AppCompatButton -> viewObj.isPressed = (keyEnum == pressedKey.key)
                is AppCompatImageButton -> viewObj.isPressed = (keyEnum == pressedKey.key)
            }
        }
    }

    /** Cancel ongoing long‐press visuals and job **/
    private fun resetLongPressAction() {
        if (isLongPressed) {
            hideAllPopWindow()
            Blur.removeBlurEffect(this)
        }
        longPressJob?.cancel()
        isLongPressed = false
    }

    /** Un–highlight all keys **/
    private fun resetAllKeys() {
        listKeys.values.forEach { viewObj ->
            when (viewObj) {
                is InputModeSwitch -> viewObj.isPressed = false
                is AppCompatButton -> viewObj.isPressed = false
                is AppCompatImageButton -> viewObj.isPressed = false
            }
        }
    }

    /** Return the underlying view object (Button/ImageButton/Switch) for a given Key **/
    private fun getButtonFromKey(key: Key): Any? {
        return listKeys[key]
    }

    /** Called when a long‐press is detected; show all related popups **/
    private fun onLongPressed() {
        val button = getButtonFromKey(pressedKey.key)
        button?.let {
            if (it is AppCompatButton) {
                if (it == binding.sideKeySymbol) return

                when (currentInputMode.value) {
                    InputMode.ModeJapanese -> {
                        popTextTop.setTextFlickTopJapanese(it.id)
                        popTextLeft.setTextFlickLeftJapanese(it.id)
                        popTextBottom.setTextFlickBottomJapanese(it.id)
                        popTextRight.setTextFlickRightJapanese(it.id)
                        popTextActive.setTextTapJapanese(it.id)
                    }

                    InputMode.ModeEnglish -> {
                        popTextTop.setTextFlickTopEnglish(it.id)
                        popTextLeft.setTextFlickLeftEnglish(it.id)
                        popTextBottom.setTextFlickBottomEnglish(it.id)
                        popTextRight.setTextFlickRightEnglish(it.id)
                        popTextActive.setTextTapEnglish(it.id)
                    }

                    InputMode.ModeNumber -> {
                        popTextTop.setTextFlickTopNumber(it.id)
                        popTextLeft.setTextFlickLeftNumber(it.id)
                        popTextBottom.setTextFlickBottomNumber(it.id)
                        popTextRight.setTextFlickRightNumber(it.id)
                        popTextActive.setTextTapNumber(it.id)
                    }
                }
                popupWindowTop.setPopUpWindowTop(context, bubbleViewTop, it)
                popupWindowLeft.setPopUpWindowLeft(context, bubbleViewLeft, it)
                if (popTextBottom.text.isNotEmpty()) {
                    popupWindowBottom.setPopUpWindowBottom(context, bubbleViewBottom, it)
                }
                if (popTextRight.text.isNotEmpty()) {
                    popupWindowRight.setPopUpWindowRight(context, bubbleViewRight, it)
                }
                popupWindowActive.setPopUpWindowCenter(context, bubbleViewActive, it)
                Blur.applyBlurEffect(this, 8f)
            }

            if (it is AppCompatImageButton) {
                if (currentInputMode.value == InputMode.ModeNumber && it == binding.keySmallLetter) {
                    popTextTop.setTextFlickTopNumber(it.id)
                    popTextLeft.setTextFlickLeftNumber(it.id)
                    popTextBottom.setTextFlickBottomNumber(it.id)
                    popTextRight.setTextFlickRightNumber(it.id)
                    popupWindowTop.setPopUpWindowTop(context, bubbleViewTop, it)
                    popupWindowLeft.setPopUpWindowLeft(context, bubbleViewLeft, it)
                    popupWindowBottom.setPopUpWindowBottom(context, bubbleViewBottom, it)
                    popupWindowRight.setPopUpWindowRight(context, bubbleViewRight, it)
                    popupWindowActive.setPopUpWindowCenter(context, bubbleViewActive, it)
                    Blur.applyBlurEffect(this, 8f)
                }
            }
        }
    }

    /** Hide every popup bubble **/
    private fun hideAllPopWindow() {
        popupWindowActive.hide()
        popupWindowLeft.hide()
        popupWindowTop.hide()
        popupWindowRight.hide()
        popupWindowBottom.hide()
        popupWindowCenter.hide()
    }

    /** Called during a “tap” gesture in an ongoing move event **/
    private fun setTapInActionMove() {
        if (!isLongPressed) popupWindowActive.hide()
        val button = getButtonFromKey(pressedKey.key)
        button?.let {
            if (it is AppCompatButton) {
                if (it == binding.sideKeySymbol) return
                when (currentInputMode.value) {
                    InputMode.ModeJapanese -> {
                        it.setTenKeyTextWhenTapJapanese(it.id)
                        if (isLongPressed) popTextActive.setTextTapJapanese(it.id)
                    }

                    InputMode.ModeEnglish -> {
                        it.setTenKeyTextWhenTapEnglish(it.id)
                        if (isLongPressed) popTextActive.setTextTapEnglish(it.id)
                    }

                    InputMode.ModeNumber -> {
                        it.setTenKeyTextWhenTapNumber(it.id)
                        if (isLongPressed) popTextActive.setTextTapNumber(it.id)
                    }
                }
                it.isPressed = true
                it.setTextColor(Color.WHITE)
                if (isLongPressed) {
                    popupWindowActive.setPopUpWindowCenter(context, bubbleViewActive, it)
                }
            }
            if (it is AppCompatImageButton && currentInputMode.value == InputMode.ModeNumber && it == binding.keySmallLetter) {
                it.setImageDrawable(
                    ContextCompat.getDrawable(
                        context, com.kazumaproject.core.R.drawable.open_bracket
                    )
                )
                if (isLongPressed) popTextActive.setTextTapNumber(it.id)
                it.isPressed = true
                if (isLongPressed) {
                    popupWindowActive.setPopUpWindowCenter(context, bubbleViewActive, it)
                }
            }
        }
    }

    /** Called during a “flick” gesture in an ongoing move event **/
    private fun setFlickInActionMove(gestureType: GestureType) {
        longPressJob?.cancel()
        val button = getButtonFromKey(pressedKey.key)
        button?.let {
            if (it is AppCompatButton) {
                if (it == binding.sideKeySymbol) return
                if (!isLongPressed) it.text = ""
                when (gestureType) {
                    GestureType.FlickLeft -> {
                        when (currentInputMode.value) {
                            InputMode.ModeJapanese -> {
                                popTextActive.setTextFlickLeftJapanese(it.id)
                                if (isLongPressed) popTextCenter.setTextTapJapanese(it.id)
                            }

                            InputMode.ModeEnglish -> {
                                popTextActive.setTextFlickLeftEnglish(it.id)
                                if (isLongPressed) popTextCenter.setTextTapEnglish(it.id)
                            }

                            InputMode.ModeNumber -> {
                                popTextActive.setTextFlickLeftNumber(it.id)
                                if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                            }
                        }
                        if (isLongPressed) {
                            popupWindowCenter.setPopUpWindowCenter(context, bubbleViewCenter, it)
                            popupWindowActive.setPopUpWindowLeft(context, bubbleViewActive, it)
                        } else {
                            popupWindowActive.setPopUpWindowFlickLeft(context, bubbleViewActive, it)
                        }
                    }

                    GestureType.FlickTop -> {
                        when (currentInputMode.value) {
                            InputMode.ModeJapanese -> {
                                popTextActive.setTextFlickTopJapanese(it.id)
                                if (isLongPressed) popTextCenter.setTextTapJapanese(it.id)
                            }

                            InputMode.ModeEnglish -> {
                                popTextActive.setTextFlickTopEnglish(it.id)
                                if (isLongPressed) popTextCenter.setTextTapEnglish(it.id)
                            }

                            InputMode.ModeNumber -> {
                                popTextActive.setTextFlickTopNumber(it.id)
                                if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                            }
                        }
                        if (isLongPressed) {
                            popupWindowCenter.setPopUpWindowCenter(context, bubbleViewCenter, it)
                            popupWindowActive.setPopUpWindowTop(context, bubbleViewActive, it)
                        } else {
                            popupWindowActive.setPopUpWindowFlickTop(context, bubbleViewActive, it)
                        }
                    }

                    GestureType.FlickRight -> {
                        when (currentInputMode.value) {
                            InputMode.ModeJapanese -> {
                                popTextActive.setTextFlickRightJapanese(it.id)
                                if (isLongPressed) popTextCenter.setTextTapJapanese(it.id)
                            }

                            InputMode.ModeEnglish -> {
                                popTextActive.setTextFlickRightEnglish(it.id)
                                if (isLongPressed) popTextCenter.setTextTapEnglish(it.id)
                            }

                            InputMode.ModeNumber -> {
                                popTextActive.setTextFlickRightNumber(it.id)
                                if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                            }
                        }
                        if (isLongPressed) {
                            if (popTextActive.text.isNotEmpty()) {
                                popupWindowActive.setPopUpWindowRight(context, bubbleViewActive, it)
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
                        when (currentInputMode.value) {
                            InputMode.ModeJapanese -> {
                                popTextActive.setTextFlickBottomJapanese(it.id)
                                if (isLongPressed) popTextCenter.setTextTapJapanese(it.id)
                            }

                            InputMode.ModeEnglish -> {
                                popTextActive.setTextFlickBottomEnglish(it.id)
                                if (isLongPressed) popTextCenter.setTextTapEnglish(it.id)
                            }

                            InputMode.ModeNumber -> {
                                popTextActive.setTextFlickBottomNumber(it.id)
                                if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
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

                    else -> {}
                }
                it.isPressed = false
            }
            if (it is AppCompatImageButton && currentInputMode.value == InputMode.ModeNumber && it == binding.keySmallLetter) {
                if (!isLongPressed) it.setImageDrawable(null)
                when (gestureType) {
                    GestureType.FlickLeft -> {
                        popTextActive.setTextFlickLeftNumber(it.id)
                        if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                        if (isLongPressed) {
                            popupWindowCenter.setPopUpWindowCenter(context, bubbleViewCenter, it)
                            popupWindowActive.setPopUpWindowLeft(context, bubbleViewActive, it)
                        } else {
                            popupWindowActive.setPopUpWindowFlickLeft(context, bubbleViewActive, it)
                        }
                    }

                    GestureType.FlickTop -> {
                        popTextActive.setTextFlickTopNumber(it.id)
                        if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                        if (isLongPressed) {
                            popupWindowCenter.setPopUpWindowCenter(context, bubbleViewCenter, it)
                            popupWindowActive.setPopUpWindowTop(context, bubbleViewActive, it)
                        } else {
                            popupWindowActive.setPopUpWindowFlickTop(context, bubbleViewActive, it)
                        }
                    }

                    GestureType.FlickRight -> {
                        popTextActive.setTextFlickRightNumber(it.id)
                        if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                        if (isLongPressed) {
                            popupWindowCenter.setPopUpWindowCenter(context, bubbleViewCenter, it)
                            popupWindowActive.setPopUpWindowRight(context, bubbleViewActive, it)
                        } else {
                            popupWindowActive.setPopUpWindowFlickRight(
                                context, bubbleViewActive, it
                            )
                        }
                    }

                    GestureType.FlickBottom -> {
                        popTextActive.setTextFlickBottomNumber(it.id)
                        if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                        if (isLongPressed) {
                            popupWindowCenter.setPopUpWindowCenter(context, bubbleViewCenter, it)
                            popupWindowActive.setPopUpWindowBottom(context, bubbleViewActive, it)
                        } else {
                            popupWindowActive.setPopUpWindowFlickBottom(
                                context, bubbleViewActive, it
                            )
                        }
                    }

                    else -> {}
                }
                it.isPressed = false
            }
        }
    }

    /** Handle flick action when second finger goes down **/
    private fun setFlickActionPointerDown(keyInfo: KeyInfo, gestureType: GestureType) {
        if (keyInfo is KeyInfo.KeyTapFlickInfo) {
            val charToSend = when (gestureType) {
                GestureType.Tap -> keyInfo.tap
                GestureType.FlickLeft -> keyInfo.flickLeft
                GestureType.FlickTop -> keyInfo.flickTop
                GestureType.FlickRight -> keyInfo.flickRight
                GestureType.FlickBottom -> keyInfo.flickBottom
                else -> null
            }
            flickListener?.onFlick(
                gestureType = gestureType, key = pressedKey.key, char = charToSend
            )
            val button = getButtonFromKey(pressedKey.key)
            button?.let {
                if (it is AppCompatButton) {
                    if (it == binding.sideKeySymbol) return
                    when (currentInputMode.value) {
                        InputMode.ModeJapanese -> it.setTenKeyTextJapanese(it.id)
                        InputMode.ModeEnglish -> it.setTenKeyTextEnglish(it.id)
                        InputMode.ModeNumber -> it.setTenKeyTextNumber(it.id)
                    }
                }
            }
        }
    }

    /** Set default drawable for the small/dakuten key **/
    fun setBackgroundSmallLetterKey(
        drawable: Drawable? = ContextCompat.getDrawable(
            context, com.kazumaproject.core.R.drawable.logo_key
        )
    ) {
        binding.keySmallLetter.setImageDrawable(drawable)
    }

    /** Set custom drawable on the Enter key **/
    fun setSideKeyEnterDrawable(drawable: Drawable?) {
        binding.keyEnter.setImageDrawable(drawable)
    }

    /** Retrieve current Enter key drawable **/
    fun getCurrentEnterKeyDrawable(): Drawable? {
        return binding.keyEnter.drawable
    }

    /** Set custom drawable on the Space key **/
    fun setSideKeySpaceDrawable(drawable: Drawable?) {
        binding.keySpace.setImageDrawable(drawable)
    }

    /** Enable/disable the “previous character” key **/
    fun setSideKeyPreviousState(state: Boolean) {
        binding.keyReturn.isEnabled = state
    }

    /** Enable/disable the “previous character” key **/
    fun setSideKeyPreviousDrawable(drawable: Drawable?) {
        binding.keyReturn.setImageDrawable(drawable)
    }

    /** Cycle through input modes when the switch key is clicked **/
    private fun handleClickInputModeSwitch() {
        // ← READ from StateFlow.value:
        val newInputMode = when (currentInputMode.value) {
            InputMode.ModeJapanese -> InputMode.ModeEnglish
            InputMode.ModeEnglish -> InputMode.ModeNumber
            InputMode.ModeNumber -> InputMode.ModeJapanese
        }
        // ← WRITE to MutableStateFlow:
        _currentInputMode.update { newInputMode }
        // We don’t need to manually call setKeysInXXX or setInputMode(...) here,
        // because our collector in init { … } already calls `handleCurrentInputModeSwitch(...)`
        // and `binding.keySwitchKeyMode.setInputMode(...)`.
    }

    /** Sync UI to a specified input mode (called from collector) **/
    private fun handleCurrentInputModeSwitch(inputMode: InputMode) {
        when (inputMode) {
            InputMode.ModeJapanese -> setKeysInJapaneseText()
            InputMode.ModeEnglish -> setKeysInEnglishText()
            InputMode.ModeNumber -> setKeysInNumberText()
        }
    }

    /** Populate all main keys with Japanese labels **/
    private fun setKeysInEmptyText() {
        val copyIcon = ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.content_copy_24dp
        )
        copyIcon?.apply {
            setBounds(
                0,
                0,
                intrinsicWidth,
                intrinsicHeight
            )
        }

        val cutIcon = ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.content_cut_24dp
        )

        cutIcon?.apply {
            setBounds(
                0,
                0,
                intrinsicWidth,
                intrinsicHeight
            )
        }

        val shareIcon = ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.baseline_share_24
        )
        shareIcon?.apply {
            setBounds(
                0,
                0,
                intrinsicWidth,
                intrinsicHeight
            )
        }

        val selectAllIcon = ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.text_select_start_24dp
        )
        selectAllIcon?.apply {
            setBounds(
                0,
                0,
                intrinsicWidth,
                intrinsicHeight
            )
        }

        binding.apply {
            key1.apply {
                text = "コピー"
                textSize = 12f
                setCompoundDrawables(copyIcon, null, null, null)
            }
            key2.text = ""
            key3.apply {
                text = "切り取り"
                textSize = 12f
                setCompoundDrawables(cutIcon, null, null, null)
            }
            key4.text = ""
            key5.text = ""
            key6.text = ""
            key7.apply {
                text = "全て選択"
                textSize = 12f
                setCompoundDrawables(selectAllIcon, null, null, null)
            }
            key8.text = ""
            key9.apply {
                text = "共有"
                textSize = 12f
                setCompoundDrawables(shareIcon, null, null, null)
            }

            keyEnter.visibility = View.INVISIBLE
            keySwitchKeyMode.visibility = View.INVISIBLE
            key11.visibility = View.INVISIBLE
            key12.visibility = View.INVISIBLE
            keySmallLetter.visibility = View.INVISIBLE

            keyReturn.setImageDrawable(null)
            sideKeySymbol.setImageDrawable(null)
            keySpace.setImageDrawable(
                ContextCompat.getDrawable(
                    context,
                    com.kazumaproject.core.R.drawable.undo_24px
                )
            )
        }
    }

    /** Populate all main keys with Japanese labels **/
    private fun setKeysInJapaneseText() {
        binding.apply {
            key1.apply {
                setTenKeyTextJapanese(key1.id)
                setCompoundDrawables(null, null, null, null)
            }
            key2.setTenKeyTextJapanese(key2.id)
            key3.apply {
                setTenKeyTextJapanese(key3.id)
                setCompoundDrawables(null, null, null, null)
            }
            key4.setTenKeyTextJapanese(key4.id)
            key5.setTenKeyTextJapanese(key5.id)
            key6.setTenKeyTextJapanese(key6.id)
            key7.apply {
                setTenKeyTextJapanese(key7.id)
                setCompoundDrawables(null, null, null, null)
            }
            key8.setTenKeyTextJapanese(key8.id)
            key9.apply {
                setTenKeyTextJapanese(key9.id)
                setCompoundDrawables(null, null, null, null)
            }
            key11.setTenKeyTextJapanese(key11.id)
            key12.setTenKeyTextJapanese(key12.id)
            resetFromSelectMode(binding)
        }
    }

    /** Populate all main keys with English labels **/
    private fun setKeysInEnglishText() {
        binding.apply {
            key1.apply {
                setTenKeyTextEnglish(key1.id)
                setCompoundDrawables(null, null, null, null)
            }
            key2.apply {
                setTenKeyTextEnglish(key2.id)
                setCompoundDrawables(null, null, null, null)
            }
            key3.setTenKeyTextEnglish(key3.id)
            key4.setTenKeyTextEnglish(key4.id)
            key5.setTenKeyTextEnglish(key5.id)
            key6.setTenKeyTextEnglish(key6.id)
            key7.apply {
                setTenKeyTextEnglish(key7.id)
                setCompoundDrawables(null, null, null, null)
            }
            key8.setTenKeyTextEnglish(key8.id)
            key9.apply {
                setTenKeyTextEnglish(key9.id)
                setCompoundDrawables(null, null, null, null)
            }
            key11.setTenKeyTextEnglish(key11.id)
            key12.setTenKeyTextEnglish(key12.id)
            resetFromSelectMode(binding)
        }
    }

    /** Populate all main keys with Number labels **/
    private fun setKeysInNumberText() {
        binding.apply {
            key1.apply {
                setTenKeyTextNumber(key1.id)
                setCompoundDrawables(null, null, null, null)
            }
            key2.setTenKeyTextNumber(key2.id)
            key3.apply {
                setTenKeyTextNumber(key3.id)
                setCompoundDrawables(null, null, null, null)
            }
            key4.setTenKeyTextNumber(key4.id)
            key5.setTenKeyTextNumber(key5.id)
            key6.setTenKeyTextNumber(key6.id)
            key7.apply {
                setTenKeyTextNumber(key7.id)
                setCompoundDrawables(null, null, null, null)
            }
            key8.setTenKeyTextNumber(key8.id)
            key9.apply {
                setTenKeyTextNumber(key9.id)
                setCompoundDrawables(null, null, null, null)
            }
            key11.setTenKeyTextNumber(key11.id)
            key12.setTenKeyTextNumber(key12.id)

            resetFromSelectMode(binding)
        }
    }

    private fun resetFromSelectMode(binding: KeyboardLayoutBinding) {
        binding.apply {
            keyReturn.apply {
                visibility = View.VISIBLE
                setImageDrawable(
                    ContextCompat.getDrawable(
                        context,
                        com.kazumaproject.core.R.drawable.undo_24px
                    )
                )
            }
            sideKeySymbol.apply {
                visibility = View.VISIBLE
                setImageDrawable(
                    ContextCompat.getDrawable(
                        context,
                        com.kazumaproject.core.R.drawable.symbol
                    )
                )
            }
            keySpace.apply {
                visibility = View.VISIBLE
                setImageDrawable(
                    ContextCompat.getDrawable(
                        context,
                        com.kazumaproject.core.R.drawable.baseline_space_bar_24
                    )
                )
            }
            keyEnter.visibility = View.VISIBLE
            keySwitchKeyMode.visibility = View.VISIBLE
            key11.visibility = View.VISIBLE
            key12.visibility = View.VISIBLE
            keySmallLetter.visibility = View.VISIBLE
        }
    }

    /** Mark all key Views as non‐focusable so touches go directly to onTouch **/
    private fun setViewsNotFocusable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.key1.focusable = View.NOT_FOCUSABLE
            binding.key2.focusable = View.NOT_FOCUSABLE
            binding.key3.focusable = View.NOT_FOCUSABLE
            binding.key4.focusable = View.NOT_FOCUSABLE
            binding.key5.focusable = View.NOT_FOCUSABLE
            binding.key6.focusable = View.NOT_FOCUSABLE
            binding.key7.focusable = View.NOT_FOCUSABLE
            binding.key8.focusable = View.NOT_FOCUSABLE
            binding.key9.focusable = View.NOT_FOCUSABLE
            binding.key11.focusable = View.NOT_FOCUSABLE
            binding.key12.focusable = View.NOT_FOCUSABLE
            binding.keySmallLetter.focusable = View.NOT_FOCUSABLE

            binding.keyReturn.focusable = View.NOT_FOCUSABLE
            binding.keySoftLeft.focusable = View.NOT_FOCUSABLE
            binding.sideKeySymbol.focusable = View.NOT_FOCUSABLE
            binding.keySwitchKeyMode.focusable = View.NOT_FOCUSABLE
            binding.keyDelete.focusable = View.NOT_FOCUSABLE
            binding.keyMoveCursorRight.focusable = View.NOT_FOCUSABLE
            binding.keySpace.focusable = View.NOT_FOCUSABLE
            binding.keyEnter.focusable = View.NOT_FOCUSABLE
        } else {
            binding.key1.isFocusable = false
            binding.key2.isFocusable = false
            binding.key3.isFocusable = false
            binding.key4.isFocusable = false
            binding.key5.isFocusable = false
            binding.key6.isFocusable = false
            binding.key7.isFocusable = false
            binding.key8.isFocusable = false
            binding.key9.isFocusable = false
            binding.key11.isFocusable = false
            binding.key12.isFocusable = false
            binding.keySmallLetter.isFocusable = false

            binding.keyReturn.isFocusable = false
            binding.keySoftLeft.isFocusable = false
            binding.sideKeySymbol.isFocusable = false
            binding.keySwitchKeyMode.isFocusable = false
            binding.keyDelete.isFocusable = false
            binding.keyMoveCursorRight.isFocusable = false
            binding.keySpace.isFocusable = false
            binding.keyEnter.isFocusable = false
        }
    }
}
