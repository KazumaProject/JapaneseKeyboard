package com.kazumaproject.tenkey

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.util.Log
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
import com.daasuu.bl.BubbleLayout
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.tenkey.extensions.hide
import com.kazumaproject.tenkey.extensions.layoutXPosition
import com.kazumaproject.tenkey.extensions.layoutYPosition
import com.kazumaproject.tenkey.extensions.setIconColor
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
import com.kazumaproject.tenkey.image_effect.ImageEffects
import com.kazumaproject.tenkey.key.TenKeyInfo
import com.kazumaproject.tenkey.key.TenKeyMap
import com.kazumaproject.tenkey.listener.FlickListener
import com.kazumaproject.tenkey.listener.LongPressListener
import com.kazumaproject.tenkey.state.GestureType
import com.kazumaproject.tenkey.state.InputMode
import com.kazumaproject.tenkey.state.InputMode.ModeNumber.next
import com.kazumaproject.tenkey.state.Key
import com.kazumaproject.tenkey.state.PressedKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
class TenKey(context: Context, attributeSet: AttributeSet) :
    ConstraintLayout(context, attributeSet), View.OnTouchListener {

    private lateinit var keyA: AppCompatButton
    private lateinit var keyKA: AppCompatButton
    private lateinit var keySA: AppCompatButton
    private lateinit var keyTA: AppCompatButton
    private lateinit var keyNA: AppCompatButton
    private lateinit var keyHA: AppCompatButton
    private lateinit var keyMA: AppCompatButton
    private lateinit var keyYA: AppCompatButton
    private lateinit var keyRA: AppCompatButton
    private lateinit var keyDakutenSmall: AppCompatImageButton
    private lateinit var keyWA: AppCompatButton
    private lateinit var keyKutouten: AppCompatButton

    private lateinit var sideKeyPreviousChar: AppCompatImageButton
    private lateinit var sideKeyCursorLeft: AppCompatImageButton
    private lateinit var sideKeyCursorRight: AppCompatImageButton
    private lateinit var sideKeySymbol: AppCompatImageButton
    private lateinit var sideKeyInputModeSwitch: InputModeSwitch
    private lateinit var sideKeyDelete: AppCompatImageButton
    private lateinit var sideKeySpace: AppCompatImageButton
    private lateinit var sideKeyEnter: AppCompatImageButton

    private lateinit var popupWindowActive: PopupWindow
    private lateinit var bubbleViewActive: BubbleLayout
    private lateinit var popTextActive: MaterialTextView
    private lateinit var popupWindowLeft: PopupWindow
    private lateinit var bubbleViewLeft: BubbleLayout
    private lateinit var popTextLeft: MaterialTextView
    private lateinit var popupWindowTop: PopupWindow
    private lateinit var bubbleViewTop: BubbleLayout
    private lateinit var popTextTop: MaterialTextView
    private lateinit var popupWindowRight: PopupWindow
    private lateinit var bubbleViewRight: BubbleLayout
    private lateinit var popTextRight: MaterialTextView
    private lateinit var popupWindowBottom: PopupWindow
    private lateinit var bubbleViewBottom: BubbleLayout
    private lateinit var popTextBottom: MaterialTextView
    private lateinit var popupWindowCenter: PopupWindow
    private lateinit var bubbleViewCenter: BubbleLayout
    private lateinit var popTextCenter: MaterialTextView

    private lateinit var pressedKey: PressedKey

    private var flickListener: FlickListener? = null
    private var longPressListener: LongPressListener? = null

    private var tenKeyMap: TenKeyMap

    private var longPressJob: Job? = null
    private var isLongPressed = false

    val currentInputMode = AtomicReference<InputMode>(InputMode.ModeJapanese)

    fun setOnFlickListener(flickListener: FlickListener) {
        this.flickListener = flickListener
    }

    fun setOnLongPressListener(longPressListener: LongPressListener) {
        this.longPressListener = longPressListener
    }

    fun setPaddingToSideKeySymbol(paddingSize: Int) {
        sideKeySymbol.setPadding(paddingSize)
    }

    init {
        View.inflate(context, R.layout.keyboard_layout, this)
        declareKeys()
        declarePopupWindows()
        initialKeys()
        setViewsNotFocusable()
        tenKeyMap = TenKeyMap()
        setBackgroundSmallLetterKey()
        this.setOnTouchListener(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.focusable = View.NOT_FOCUSABLE
        } else {
            this.isFocusable = false
        }
    }

    private fun declareKeys() {
        keyA = findViewById(R.id.key_1)
        keyKA = findViewById(R.id.key_2)
        keySA = findViewById(R.id.key_3)
        keyTA = findViewById(R.id.key_4)
        keyNA = findViewById(R.id.key_5)
        keyHA = findViewById(R.id.key_6)
        keyMA = findViewById(R.id.key_7)
        keyYA = findViewById(R.id.key_8)
        keyRA = findViewById(R.id.key_9)
        keyDakutenSmall = findViewById(R.id.key_small_letter)
        keyWA = findViewById(R.id.key_11)
        keyKutouten = findViewById(R.id.key_12)

        sideKeyPreviousChar = findViewById(R.id.key_return)
        sideKeyCursorLeft = findViewById(R.id.key_soft_left)
        sideKeyCursorRight = findViewById(R.id.key_move_cursor_right)
        sideKeyEnter = findViewById(R.id.key_enter)
        sideKeyDelete = findViewById(R.id.key_delete)
        sideKeySpace = findViewById(R.id.key_space)
        sideKeySymbol = findViewById(R.id.sideKey_symbol)
        sideKeyInputModeSwitch = findViewById(R.id.key_switch_key_mode)
    }

    @SuppressLint("InflateParams")
    private fun declarePopupWindows() {
        val mPopWindowActive = PopupWindow(context)
        val popupViewActive =
            LayoutInflater.from(context).inflate(R.layout.popup_layout_active, null)
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

    private fun setViewsNotFocusable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyA.focusable = View.NOT_FOCUSABLE
            keyKA.focusable = View.NOT_FOCUSABLE
            keySA.focusable = View.NOT_FOCUSABLE
            keyTA.focusable = View.NOT_FOCUSABLE
            keyNA.focusable = View.NOT_FOCUSABLE
            keyHA.focusable = View.NOT_FOCUSABLE
            keyMA.focusable = View.NOT_FOCUSABLE
            keyYA.focusable = View.NOT_FOCUSABLE
            keyRA.focusable = View.NOT_FOCUSABLE
            keyDakutenSmall.focusable = View.NOT_FOCUSABLE
            keyWA.focusable = View.NOT_FOCUSABLE
            keyKutouten.focusable = View.NOT_FOCUSABLE

            sideKeyPreviousChar.focusable = View.NOT_FOCUSABLE
            sideKeyCursorLeft.focusable = View.NOT_FOCUSABLE
            sideKeyCursorRight.focusable = View.NOT_FOCUSABLE
            sideKeyEnter.focusable = View.NOT_FOCUSABLE
            sideKeyDelete.focusable = View.NOT_FOCUSABLE
            sideKeySpace.focusable = View.NOT_FOCUSABLE
            sideKeySymbol.focusable = View.NOT_FOCUSABLE
            sideKeyInputModeSwitch.focusable = View.NOT_FOCUSABLE
        } else {
            keyA.isFocusable = false
            keyKA.isFocusable = false
            keySA.isFocusable = false
            keyTA.isFocusable = false
            keyNA.isFocusable = false
            keyHA.isFocusable = false
            keyMA.isFocusable = false
            keyYA.isFocusable = false
            keyRA.isFocusable = false
            keyDakutenSmall.isFocusable = false
            keyWA.isFocusable = false
            keyKutouten.isFocusable = false

            sideKeyPreviousChar.isFocusable = false
            sideKeyCursorLeft.isFocusable = false
            sideKeyCursorRight.isFocusable = false
            sideKeyEnter.isFocusable = false
            sideKeyDelete.isFocusable = false
            sideKeySpace.isFocusable = false
            sideKeySymbol.isFocusable = false
            sideKeyInputModeSwitch.isFocusable = false
        }

    }

    private fun initialKeys() {
        keyKutouten.setTenKeyTextJapanese(keyKutouten.id)
    }

    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        return true
    }

    override fun onTouch(view: View?, event: MotionEvent?): Boolean {
        if (view != null && event != null) {
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    val key = pressedKeyByMotionEvent(event, 0)
                    flickListener?.onFlick(
                        GestureType.Down,
                        key,
                        null
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
                    Log.d("Touch Listener", "ACTION_DOWN called: $pressedKey")
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
                        val keyInfo =
                            currentInputMode.get().next(tenKeyMap = tenKeyMap, key = pressedKey.key)
                        if (keyInfo == TenKeyInfo.Null) {
                            flickListener?.onFlick(
                                gestureType = gestureType, key = pressedKey.key, char = null
                            )
                            if (pressedKey.key == Key.SideKeyInputMode) {
                                handleClickInputModeSwitch()
                            }
                        } else if (keyInfo is TenKeyInfo.TenKeyTapFlickInfo) {
                            when (gestureType) {
                                GestureType.Null -> {}
                                GestureType.Down -> {}
                                GestureType.Tap -> flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = pressedKey.key,
                                    char = keyInfo.tap,
                                )

                                GestureType.FlickLeft -> flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = pressedKey.key,
                                    char = keyInfo.flickLeft,
                                )

                                GestureType.FlickTop -> flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = pressedKey.key,
                                    char = keyInfo.flickTop,
                                )

                                GestureType.FlickRight -> flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = pressedKey.key,
                                    char = keyInfo.flickRight,
                                )

                                GestureType.FlickBottom -> flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = pressedKey.key,
                                    char = keyInfo.flickBottom,
                                )
                            }
                        }
                        Log.d(
                            "Touch Listener", "ACTION_UP called: $pressedKey $gestureType $keyInfo"
                        )
                    }
                    resetAllKeys()
                    popupWindowActive.hide()
                    val button = getButtonFromKey(pressedKey.key)
                    button?.let {
                        if (it is AppCompatButton) {
                            if (it == sideKeySymbol) return false
                            when (currentInputMode.get()) {
                                InputMode.ModeJapanese -> {
                                    it.setTenKeyTextJapanese(it.id)
                                }

                                InputMode.ModeEnglish -> {
                                    it.setTenKeyTextEnglish(it.id)
                                }

                                InputMode.ModeNumber -> {
                                    it.setTenKeyTextNumber(it.id)
                                }
                            }
                        }
                        if (it is AppCompatImageButton && currentInputMode.get() == InputMode.ModeNumber && it == keyDakutenSmall) {
                            it.setImageDrawable(
                                ContextCompat.getDrawable(
                                    context,
                                    R.drawable.number_small
                                )
                            )
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
                        ImageEffects.removeBlurEffect(this)
                    }
                    popupWindowActive.hide()
                    longPressJob?.cancel()
                    if (event.pointerCount == 2) {
                        isLongPressed = false
                        val pointer = event.getPointerId(event.actionIndex)
                        val key = pressedKeyByMotionEvent(event, pointer)
                        val gestureType2 = getGestureType(event, if (pointer == 0) 1 else 0)
                        if (pressedKey.key == Key.KeyDakutenSmall && currentInputMode.get() == InputMode.ModeNumber) {
                            keyDakutenSmall.setImageDrawable(
                                ContextCompat.getDrawable(
                                    context,
                                    R.drawable.number_small
                                )
                            )
                        }
                        val keyInfo =
                            currentInputMode.get().next(tenKeyMap = tenKeyMap, key = pressedKey.key)
                        if (keyInfo == TenKeyInfo.Null) {
                            flickListener?.onFlick(
                                gestureType = gestureType2, key = pressedKey.key, char = null
                            )
                        } else if (keyInfo is TenKeyInfo.TenKeyTapFlickInfo) {
                            when (gestureType2) {
                                GestureType.Null -> {}
                                GestureType.Down -> {}
                                GestureType.Tap -> {
                                    flickListener?.onFlick(
                                        gestureType = gestureType2,
                                        key = pressedKey.key,
                                        char = keyInfo.tap,
                                    )
                                    val button = getButtonFromKey(pressedKey.key)
                                    button?.let {
                                        if (it is AppCompatButton) {
                                            if (it == sideKeySymbol) return false
                                            when (currentInputMode.get()) {
                                                InputMode.ModeJapanese -> {
                                                    it.setTenKeyTextJapanese(it.id)
                                                }

                                                InputMode.ModeEnglish -> {
                                                    it.setTenKeyTextEnglish(it.id)
                                                }

                                                InputMode.ModeNumber -> {
                                                    it.setTenKeyTextNumber(it.id)
                                                }
                                            }
                                        }
                                        if (it is AppCompatImageButton && currentInputMode.get() == InputMode.ModeNumber && it == keyDakutenSmall) {
                                            it.setImageDrawable(
                                                ContextCompat.getDrawable(
                                                    context,
                                                    R.drawable.number_small
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
                        Log.d(
                            "Touch Listener",
                            "ACTION_POINTER_DOWN sendChar called: $pressedKey $gestureType2"
                        )
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
                        Log.d(
                            "Touch Listener",
                            "ACTION_POINTER_DOWN called: $pressedKey ${
                                event.getPointerId(
                                    event.actionIndex
                                )
                            }"
                        )
                    }

                    return false
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.pointerCount == 2) {
                        if (pressedKey.pointer == event.getPointerId(event.actionIndex)) {
                            resetLongPressAction()
                            val gestureType =
                                getGestureType(event, event.getPointerId(event.actionIndex))
                            val keyInfo =
                                currentInputMode.get()
                                    .next(tenKeyMap = tenKeyMap, key = pressedKey.key)
                            if (keyInfo == TenKeyInfo.Null) {
                                flickListener?.onFlick(
                                    gestureType = gestureType, key = pressedKey.key, char = null
                                )
                                if (pressedKey.key == Key.SideKeyInputMode) {
                                    handleClickInputModeSwitch()
                                }
                            } else if (keyInfo is TenKeyInfo.TenKeyTapFlickInfo) {
                                when (gestureType) {
                                    GestureType.Null -> {}
                                    GestureType.Down -> {}
                                    GestureType.Tap -> flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.tap,
                                    )

                                    GestureType.FlickLeft -> flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.flickLeft,
                                    )

                                    GestureType.FlickTop -> flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.flickTop,
                                    )

                                    GestureType.FlickRight -> flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.flickRight,
                                    )

                                    GestureType.FlickBottom -> flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.flickBottom,
                                    )
                                }
                            }
                            val button = getButtonFromKey(pressedKey.key)
                            button?.let {
                                if (it is AppCompatButton) {
                                    if (it == sideKeySymbol) return false
                                    it.isPressed = false
                                    when (currentInputMode.get()) {
                                        InputMode.ModeJapanese -> {
                                            it.setTenKeyTextJapanese(it.id)
                                        }

                                        InputMode.ModeEnglish -> {
                                            it.setTenKeyTextEnglish(it.id)
                                        }

                                        InputMode.ModeNumber -> {
                                            it.setTenKeyTextNumber(it.id)
                                        }
                                    }
                                }
                            }
                            pressedKey = pressedKey.copy(
                                key = Key.NotSelected,
                            )
                            popupWindowActive.hide()
                            Log.d("Touch Listener", "ACTION_POINTER_UP called: $pressedKey")
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

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        newConfig?.apply {
            initializeButtonTextOrientationChange(orientation)
        }
    }

    private fun initializeButtonTextOrientationChange(orientation: Int) {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            setTextToAllButtons()
        } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setTextToAllButtons()
        }
    }

    private fun setTextToAllButtons() {
        keyA.setTenKeyTextJapanese(keyA.id)
        keyKA.setTenKeyTextJapanese(keyKA.id)
        keySA.setTenKeyTextJapanese(keySA.id)
        keyTA.setTenKeyTextJapanese(keyTA.id)
        keyNA.setTenKeyTextJapanese(keyNA.id)
        keyHA.setTenKeyTextJapanese(keyHA.id)
        keyMA.setTenKeyTextJapanese(keyMA.id)
        keyYA.setTenKeyTextJapanese(keyYA.id)
        keyRA.setTenKeyTextJapanese(keyRA.id)
        keyWA.setTenKeyTextJapanese(keyWA.id)
    }

    private fun pressedKeyByMotionEvent(event: MotionEvent, pointer: Int): Key {
        val x: Float = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            event.getRawX(pointer)
        } else {
            val location = IntArray(2)
            this.getLocationOnScreen(location)
            event.getX(pointer) + location[0]
        }

        val y: Float = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            event.getRawY(pointer)
        } else {
            val location = IntArray(2)
            this.getLocationOnScreen(location)
            event.getY(pointer) + location[1]
        }
        val keyWidth = keyA.width
        val keyHeight = keyA.height
        when {
            x >= 0 && x <= sideKeyPreviousChar.layoutXPosition() + keyWidth && y >= 0 && y <= sideKeyPreviousChar.layoutYPosition() + keyHeight -> return Key.SideKeyPreviousChar
            x >= keyWidth + 1 && x <= keyA.layoutXPosition() + keyWidth && y <= keyA.layoutYPosition() + keyHeight -> return Key.KeyA
            x >= keyA.layoutXPosition() + keyWidth + 1 && x <= keyKA.layoutXPosition() + keyWidth && y <= keyKA.layoutYPosition() + keyHeight -> return Key.KeyKA
            x >= keyKA.layoutXPosition() + keyWidth + 1 && x <= keySA.layoutXPosition() + keyWidth && y <= keySA.layoutYPosition() + keyHeight -> return Key.KeySA
            x >= keySA.layoutXPosition() + keyWidth + 1 && x <= sideKeyDelete.layoutXPosition() + keyWidth && y <= sideKeyDelete.layoutYPosition() + keyHeight -> return Key.SideKeyDelete

            x >= 0 && x <= sideKeyCursorLeft.layoutXPosition() + keyWidth && y >= 0 && y <= sideKeyCursorLeft.layoutYPosition() + keyHeight -> return Key.SideKeyCursorLeft
            x >= keyWidth + 1 && x <= keyTA.layoutXPosition() + keyWidth && y <= keyTA.layoutYPosition() + keyHeight -> return Key.KeyTA
            x >= keyTA.layoutXPosition() + keyWidth + 1 && x <= keyNA.layoutXPosition() + keyWidth && y <= keyNA.layoutYPosition() + keyHeight -> return Key.KeyNA
            x >= keyNA.layoutXPosition() + keyWidth + 1 && x <= keyHA.layoutXPosition() + keyWidth && y <= keyHA.layoutYPosition() + keyHeight -> return Key.KeyHA
            x >= keyHA.layoutXPosition() + keyWidth + 1 && x <= sideKeyCursorRight.layoutXPosition() + keyWidth && y <= sideKeyCursorRight.layoutYPosition() + keyHeight -> return Key.SideKeyCursorRight

            x >= 0 && x <= sideKeySymbol.layoutXPosition() + keyWidth && y >= 0 && y <= sideKeySymbol.layoutYPosition() + keyHeight -> return Key.SideKeySymbol
            x >= keyWidth + 1 && x <= keyMA.layoutXPosition() + keyWidth && y <= keyMA.layoutYPosition() + keyHeight -> return Key.KeyMA
            x >= keyMA.layoutXPosition() + keyWidth + 1 && x <= keyYA.layoutXPosition() + keyWidth && y <= keyYA.layoutYPosition() + keyHeight -> return Key.KeyYA
            x >= keyYA.layoutXPosition() + keyWidth + 1 && x <= keyRA.layoutXPosition() + keyWidth && y <= keyRA.layoutYPosition() + keyHeight -> return Key.KeyRA
            x >= keyRA.layoutXPosition() + keyWidth + 1 && x <= sideKeySpace.layoutXPosition() + keyWidth && y <= sideKeySpace.layoutYPosition() + keyHeight -> return Key.SideKeySpace

            x >= 0 && x <= sideKeyInputModeSwitch.layoutXPosition() + keyWidth && y >= 0 && y <= sideKeyInputModeSwitch.layoutYPosition() + keyHeight -> return Key.SideKeyInputMode
            x >= keyWidth + 1 && x <= keyDakutenSmall.layoutXPosition() + keyWidth && y <= keyDakutenSmall.layoutYPosition() + keyHeight -> return Key.KeyDakutenSmall
            x >= keyDakutenSmall.layoutXPosition() + keyWidth + 1 && x <= keyWA.layoutXPosition() + keyWidth && y <= keyWA.layoutYPosition() + keyHeight -> return Key.KeyWA
            x >= keyWA.layoutXPosition() + keyWidth + 1 && x <= keyKutouten.layoutXPosition() + keyWidth && y <= keyKutouten.layoutYPosition() + keyHeight -> return Key.KeyKutouten
            x >= keyKutouten.layoutXPosition() + keyWidth + 1 && x <= sideKeyEnter.layoutXPosition() + keyWidth && y <= sideKeyEnter.layoutYPosition() + keyHeight -> return Key.SideKeyEnter

            else -> return Key.NotSelected
        }
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
            abs(distanceX) < 100 && abs(distanceY) < 100 -> GestureType.Tap
            abs(distanceX) > abs(distanceY) && pressedKey.initialX >= finalX -> GestureType.FlickLeft
            abs(distanceX) <= abs(distanceY) && pressedKey.initialY >= finalY -> GestureType.FlickTop
            abs(distanceX) > abs(distanceY) && pressedKey.initialX < finalX -> GestureType.FlickRight
            abs(distanceX) <= abs(distanceY) && pressedKey.initialY < finalY -> GestureType.FlickBottom
            else -> GestureType.Null
        }
    }

    private fun setKeyPressed() {
        when (pressedKey.key) {
            Key.KeyA -> {
                resetAllKeys()
                keyA.isPressed = true
            }

            Key.KeyKA -> {
                resetAllKeys()
                keyKA.isPressed = true
            }

            Key.KeySA -> {
                resetAllKeys()
                keySA.isPressed = true
            }

            Key.KeyTA -> {
                resetAllKeys()
                keyTA.isPressed = true
            }

            Key.KeyNA -> {
                resetAllKeys()
                keyNA.isPressed = true
            }

            Key.KeyHA -> {
                resetAllKeys()
                keyHA.isPressed = true
            }

            Key.KeyMA -> {
                resetAllKeys()
                keyMA.isPressed = true
            }

            Key.KeyYA -> {
                resetAllKeys()
                keyYA.isPressed = true
            }

            Key.KeyRA -> {
                resetAllKeys()
                keyRA.isPressed = true
            }

            Key.KeyWA -> {
                resetAllKeys()
                keyWA.isPressed = true
            }

            Key.KeyKutouten -> {
                resetAllKeys()
                keyKutouten.isPressed = true
            }

            Key.KeyDakutenSmall -> {
                resetAllKeys()
                keyDakutenSmall.isPressed = true
            }

            Key.SideKeyPreviousChar -> {
                resetAllKeys()
                sideKeyPreviousChar.isPressed = true
            }

            Key.SideKeyCursorLeft -> {
                resetAllKeys()
                sideKeyCursorLeft.isPressed = true
            }

            Key.SideKeyCursorRight -> {
                resetAllKeys()
                sideKeyCursorRight.isPressed = true
            }

            Key.SideKeyDelete -> {
                resetAllKeys()
                sideKeyDelete.isPressed = true
            }

            Key.SideKeyEnter -> {
                resetAllKeys()
                sideKeyEnter.isPressed = true
            }

            Key.SideKeyInputMode -> {
                resetAllKeys()
                sideKeyInputModeSwitch.isPressed = true
            }

            Key.SideKeySpace -> {
                resetAllKeys()
                sideKeySpace.isPressed = true
            }

            Key.SideKeySymbol -> {
                resetAllKeys()
                sideKeySymbol.isPressed = true
            }

            Key.NotSelected -> {}
        }
    }

    private fun resetLongPressAction() {
        if (isLongPressed) {
            hideAllPopWindow()
            ImageEffects.removeBlurEffect(this)
        }
        longPressJob?.cancel()
        isLongPressed = false
    }

    private fun resetAllKeys() {
        keyA.isPressed = false
        keyKA.isPressed = false
        keySA.isPressed = false
        keyTA.isPressed = false
        keyNA.isPressed = false
        keyHA.isPressed = false
        keyMA.isPressed = false
        keyYA.isPressed = false
        keyRA.isPressed = false
        keyWA.isPressed = false
        keyKutouten.isPressed = false
        keyDakutenSmall.isPressed = false
        sideKeyPreviousChar.isPressed = false
        sideKeyCursorLeft.isPressed = false
        sideKeyCursorRight.isPressed = false
        sideKeyDelete.isPressed = false
        sideKeyEnter.isPressed = false
        sideKeyInputModeSwitch.isPressed = false
        sideKeySpace.isPressed = false
        sideKeySymbol.isPressed = false
    }

    private val listKeys: Map<Key, Any> = mapOf(
        Key.KeyA to keyA,
        Key.KeyKA to keyKA,
        Key.KeySA to keySA,
        Key.KeyTA to keyTA,
        Key.KeyNA to keyNA,
        Key.KeyHA to keyHA,
        Key.KeyMA to keyMA,
        Key.KeyYA to keyYA,
        Key.KeyRA to keyRA,
        Key.KeyWA to keyWA,
        Key.KeyKutouten to keyKutouten,
        Key.KeyDakutenSmall to keyDakutenSmall,
        Key.SideKeyPreviousChar to sideKeyPreviousChar,
        Key.SideKeyCursorLeft to sideKeyCursorLeft,
        Key.SideKeyCursorRight to sideKeyCursorRight,
        Key.SideKeySymbol to sideKeySymbol,
        Key.SideKeyInputMode to sideKeyInputModeSwitch,
        Key.SideKeyDelete to sideKeyDelete,
        Key.SideKeySpace to sideKeySpace,
        Key.SideKeyEnter to sideKeyEnter
    )

    private fun getButtonFromKey(key: Key): Any? {
        return listKeys.getOrDefault(key, null)
    }

    private fun onLongPressed() {
        val button = getButtonFromKey(pressedKey.key)
        button?.let {
            if (it is AppCompatButton) {
                if (it == sideKeySymbol) return

                when (currentInputMode.get()) {
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
                popupWindowBottom.setPopUpWindowBottom(
                    context, bubbleViewBottom, it
                )
                popupWindowRight.setPopUpWindowRight(
                    context, bubbleViewRight, it
                )
                popupWindowActive.setPopUpWindowCenter(
                    context, bubbleViewActive, it
                )
                ImageEffects.applyBlurEffect(this, 8f)
            }

            if (it is AppCompatImageButton && currentInputMode.get() == InputMode.ModeNumber && it == keyDakutenSmall) {
                popTextTop.setTextFlickTopNumber(it.id)
                popTextLeft.setTextFlickLeftNumber(it.id)
                popTextBottom.setTextFlickBottomNumber(it.id)
                popTextRight.setTextFlickRightNumber(it.id)
                popupWindowTop.setPopUpWindowTop(context, bubbleViewTop, it)
                popupWindowLeft.setPopUpWindowLeft(context, bubbleViewLeft, it)
                popupWindowBottom.setPopUpWindowBottom(
                    context, bubbleViewBottom, it
                )
                popupWindowRight.setPopUpWindowRight(
                    context, bubbleViewRight, it
                )
                popupWindowActive.setPopUpWindowCenter(
                    context, bubbleViewActive, it
                )
                ImageEffects.applyBlurEffect(this, 8f)
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
        button?.let {
            if (it is AppCompatButton) {
                if (it == sideKeySymbol) return
                when (currentInputMode.get()) {
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
                if (isLongPressed) {
                    popupWindowActive.setPopUpWindowCenter(
                        context, bubbleViewActive, it
                    )
                }
            }
            if (it is AppCompatImageButton && currentInputMode.get() == InputMode.ModeNumber && it == keyDakutenSmall) {
                it.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.open_bracket))
                if (isLongPressed) popTextActive.setTextTapNumber(it.id)
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
        button?.let {
            if (it is AppCompatButton) {
                if (it == sideKeySymbol) return
                if (!isLongPressed) it.text = ""
                when (gestureType) {
                    GestureType.FlickLeft -> {
                        when (currentInputMode.get()) {
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
                            popupWindowCenter.setPopUpWindowCenter(
                                context, bubbleViewCenter, it
                            )
                            popupWindowActive.setPopUpWindowLeft(
                                context, bubbleViewActive, it
                            )
                        } else {
                            popupWindowActive.setPopUpWindowFlickLeft(
                                context, bubbleViewActive, it
                            )
                        }
                    }

                    GestureType.FlickTop -> {
                        when (currentInputMode.get()) {
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
                            popupWindowCenter.setPopUpWindowCenter(
                                context, bubbleViewCenter, it
                            )
                            popupWindowActive.setPopUpWindowTop(
                                context, bubbleViewActive, it
                            )
                        } else {
                            popupWindowActive.setPopUpWindowFlickTop(
                                context, bubbleViewActive, it
                            )
                        }
                    }

                    GestureType.FlickRight -> {
                        when (currentInputMode.get()) {
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
                            popupWindowCenter.setPopUpWindowCenter(
                                context, bubbleViewCenter, it
                            )
                            popupWindowActive.setPopUpWindowRight(
                                context, bubbleViewActive, it
                            )
                        } else {
                            popupWindowActive.setPopUpWindowFlickRight(
                                context, bubbleViewActive, it
                            )
                        }
                    }

                    GestureType.FlickBottom -> {
                        when (currentInputMode.get()) {
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
                            popupWindowCenter.setPopUpWindowCenter(
                                context, bubbleViewCenter, it
                            )
                            popupWindowActive.setPopUpWindowBottom(
                                context, bubbleViewActive, it
                            )
                        } else {
                            popupWindowActive.setPopUpWindowFlickBottom(
                                context, bubbleViewActive, it
                            )
                        }
                    }

                    else -> {

                    }
                }
                it.isPressed = false
            }
            if (it is AppCompatImageButton && currentInputMode.get() == InputMode.ModeNumber && it == keyDakutenSmall) {
                if (!isLongPressed) it.setImageDrawable(null)
                when (gestureType) {
                    GestureType.FlickLeft -> {
                        popTextActive.setTextFlickLeftNumber(it.id)
                        if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                        if (isLongPressed) {
                            popupWindowCenter.setPopUpWindowCenter(
                                context, bubbleViewCenter, it
                            )
                            popupWindowActive.setPopUpWindowLeft(
                                context, bubbleViewActive, it
                            )
                        } else {
                            popupWindowActive.setPopUpWindowFlickLeft(
                                context, bubbleViewActive, it
                            )
                        }
                    }

                    GestureType.FlickTop -> {
                        popTextActive.setTextFlickTopNumber(it.id)
                        if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                        if (isLongPressed) {
                            popupWindowCenter.setPopUpWindowCenter(
                                context, bubbleViewCenter, it
                            )
                            popupWindowActive.setPopUpWindowTop(
                                context, bubbleViewActive, it
                            )
                        } else {
                            popupWindowActive.setPopUpWindowFlickTop(
                                context, bubbleViewActive, it
                            )
                        }
                    }

                    GestureType.FlickRight -> {
                        popTextActive.setTextFlickRightNumber(it.id)
                        if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                        if (isLongPressed) {
                            popupWindowCenter.setPopUpWindowCenter(
                                context, bubbleViewCenter, it
                            )
                            popupWindowActive.setPopUpWindowRight(
                                context, bubbleViewActive, it
                            )
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
                            popupWindowCenter.setPopUpWindowCenter(
                                context, bubbleViewCenter, it
                            )
                            popupWindowActive.setPopUpWindowBottom(
                                context, bubbleViewActive, it
                            )
                        } else {
                            popupWindowActive.setPopUpWindowFlickBottom(
                                context, bubbleViewActive, it
                            )
                        }
                    }

                    else -> {

                    }
                }
                it.isPressed = false
            }
        }
    }

    private fun setFlickActionPointerDown(keyInfo: TenKeyInfo, gestureType: GestureType) {
        if (keyInfo is TenKeyInfo.TenKeyTapFlickInfo) {
            val charToSend = when (gestureType) {
                GestureType.Tap -> keyInfo.tap
                GestureType.FlickLeft -> keyInfo.flickLeft
                GestureType.FlickTop -> keyInfo.flickTop
                GestureType.FlickRight -> keyInfo.flickRight
                GestureType.FlickBottom -> keyInfo.flickBottom
                GestureType.Down -> null
                GestureType.Null -> null
            }
            flickListener?.onFlick(
                gestureType = gestureType,
                key = pressedKey.key,
                char = charToSend,
            )
            val button = getButtonFromKey(pressedKey.key)
            button?.let {
                if (it is AppCompatButton) {
                    if (it == sideKeySymbol) return
                    when (currentInputMode.get()) {
                        InputMode.ModeJapanese -> {
                            it.setTenKeyTextJapanese(it.id)
                        }

                        InputMode.ModeEnglish -> {
                            it.setTenKeyTextEnglish(it.id)
                        }

                        InputMode.ModeNumber -> {
                            it.setTenKeyTextNumber(it.id)
                        }
                    }
                }
            }
        }
    }

    fun setBackgroundSmallLetterKey(
        drawable: Drawable? = ContextCompat.getDrawable(
            context,
            R.drawable.logo_key
        )
    ) {
        keyDakutenSmall.setImageDrawable(drawable)
    }

    fun setSideKeyEnterDrawable(drawable: Drawable?) {
        sideKeyEnter.setImageDrawable(drawable)
    }

    fun getCurrentEnterKeyDrawable(): Drawable? {
        return sideKeyEnter.drawable
    }

    fun setSideKeySpaceDrawable(drawable: Drawable?) {
        sideKeySpace.setImageDrawable(drawable)
    }

    fun setSideKeyPreviousState(state: Boolean) {
        sideKeyPreviousChar.isEnabled = state
        val colorResId = if (state) {
            R.color.keyboard_icon_color
        } else {
            R.color.keyboard_icon_disable_color
        }
        sideKeyPreviousChar.setIconColor(colorResId)
    }

    private fun handleClickInputModeSwitch() {
        val inputMode = sideKeyInputModeSwitch.getCurrentInputMode()
        val newInputMode = when (inputMode) {
            InputMode.ModeJapanese -> {
                setKeysInEnglishText()
                InputMode.ModeEnglish
            }

            InputMode.ModeEnglish -> {
                setKeysInNumberText()
                InputMode.ModeNumber
            }

            InputMode.ModeNumber -> {
                setKeysInJapaneseText()
                InputMode.ModeJapanese
            }
        }
        currentInputMode.set(newInputMode)
        sideKeyInputModeSwitch.setInputMode(newInputMode)
    }

    private fun handleCurrentInputModeSwitch() {
        val inputMode = sideKeyInputModeSwitch.getCurrentInputMode()
        when (inputMode) {
            InputMode.ModeJapanese -> {
                setKeysInJapaneseText()
            }

            InputMode.ModeEnglish -> {
                setKeysInEnglishText()
            }

            InputMode.ModeNumber -> {
                setKeysInNumberText()
            }
        }
    }

    fun setInputModeSwitchState(inputMode: InputMode) {
        sideKeyInputModeSwitch.setInputMode(inputMode)
        handleCurrentInputModeSwitch()
    }

    private fun setKeysInJapaneseText() {
        keyA.setTenKeyTextJapanese(keyA.id)
        keyKA.setTenKeyTextJapanese(keyKA.id)
        keySA.setTenKeyTextJapanese(keySA.id)
        keyTA.setTenKeyTextJapanese(keyTA.id)
        keyNA.setTenKeyTextJapanese(keyNA.id)
        keyHA.setTenKeyTextJapanese(keyHA.id)
        keyMA.setTenKeyTextJapanese(keyMA.id)
        keyYA.setTenKeyTextJapanese(keyYA.id)
        keyRA.setTenKeyTextJapanese(keyRA.id)
        keyWA.setTenKeyTextJapanese(keyWA.id)
        keyKutouten.setTenKeyTextJapanese(keyKutouten.id)
        //keyDakutenSmall.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.logo_key))
    }

    private fun setKeysInEnglishText() {
        keyA.setTenKeyTextEnglish(keyA.id)
        keyKA.setTenKeyTextEnglish(keyKA.id)
        keySA.setTenKeyTextEnglish(keySA.id)
        keyTA.setTenKeyTextEnglish(keyTA.id)
        keyNA.setTenKeyTextEnglish(keyNA.id)
        keyHA.setTenKeyTextEnglish(keyHA.id)
        keyMA.setTenKeyTextEnglish(keyMA.id)
        keyYA.setTenKeyTextEnglish(keyYA.id)
        keyRA.setTenKeyTextEnglish(keyRA.id)
        keyWA.setTenKeyTextEnglish(keyWA.id)
        keyKutouten.setTenKeyTextEnglish(keyKutouten.id)
        //keyDakutenSmall.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.logo_key))
    }

    private fun setKeysInNumberText() {
        keyA.setTenKeyTextNumber(keyA.id)
        keyKA.setTenKeyTextNumber(keyKA.id)
        keySA.setTenKeyTextNumber(keySA.id)
        keyTA.setTenKeyTextNumber(keyTA.id)
        keyNA.setTenKeyTextNumber(keyNA.id)
        keyHA.setTenKeyTextNumber(keyHA.id)
        keyMA.setTenKeyTextNumber(keyMA.id)
        keyYA.setTenKeyTextNumber(keyYA.id)
        keyRA.setTenKeyTextNumber(keyRA.id)
        keyWA.setTenKeyTextNumber(keyWA.id)
        keyKutouten.setTenKeyTextNumber(keyKutouten.id)
//        keyDakutenSmall.setImageDrawable(
//            ContextCompat.getDrawable(
//                context,
//                R.drawable.number_small
//            )
//        )
    }

}