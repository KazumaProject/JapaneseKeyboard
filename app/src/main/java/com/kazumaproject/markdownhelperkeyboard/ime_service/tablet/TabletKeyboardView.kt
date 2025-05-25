package com.kazumaproject.markdownhelperkeyboard.ime_service.tablet

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.kazumaproject.core.key.Key
import com.kazumaproject.core.key.KeyInfo
import com.kazumaproject.core.key.KeyMap
import com.kazumaproject.core.key.KeyRect
import com.kazumaproject.core.listener.FlickListener
import com.kazumaproject.core.state.GestureType
import com.kazumaproject.core.state.InputMode
import com.kazumaproject.core.state.InputMode.ModeEnglish.next
import com.kazumaproject.core.state.PressedKey
import com.kazumaproject.markdownhelperkeyboard.databinding.TabletLayoutBinding
import com.kazumaproject.tenkey.extensions.layoutXPosition
import com.kazumaproject.tenkey.extensions.layoutYPosition
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * A custom view that wraps the tablet keyboard layout and provides easy access
 * to all of its key views via binding.
 */
@SuppressLint("ClickableViewAccessibility")
class TabletKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), View.OnTouchListener {

    private val binding: TabletLayoutBinding =
        TabletLayoutBinding.inflate(LayoutInflater.from(context), this, true)

    val currentInputMode = AtomicReference<InputMode>(InputMode.ModeJapanese)
    private lateinit var pressedKey: PressedKey

    // All AppCompatButton keys (all the character keys)
    private val allButtonKeys = listOf(
        binding.key1, binding.key2, binding.key3, binding.key4, binding.key5,
        binding.key6, binding.key7, binding.key8, binding.key9, binding.key10,
        binding.key11, binding.key12, binding.key13, binding.key14, binding.key15,
        binding.key16, binding.key17, binding.key18, binding.key19, binding.key20,
        binding.key21, binding.key22, binding.key23, binding.key24, binding.key25,
        binding.key26, binding.key27, binding.key28, binding.key29, binding.key30,
        binding.key31, binding.key32, binding.key33, binding.key34, binding.key35,
        binding.key36, binding.key37, binding.key38, binding.key39, binding.key40,
        binding.key41, binding.key42, binding.key43, binding.key44, binding.key45,
        binding.key46, binding.key47, binding.key48, binding.key49, binding.key50,
        binding.key51, binding.key52, binding.key53, binding.key54, binding.key55
    )

    // All AppCompatImageButton keys (side and utility keys)
    private val allImageButtonKeys = listOf(
        binding.keyKigou, binding.keyEnglish, binding.keyJapanese,
        binding.keyLeftCursor, binding.keyRightCursor,
        binding.keyDelete, binding.keySpace, binding.keyEnter
    )

    private var keyMap: KeyMap

    private var flickListener: FlickListener? = null

    init {
        (allButtonKeys + allImageButtonKeys).forEach { it.setOnTouchListener(this) }
        keyMap = KeyMap()
    }

    fun setOnFlickListener(flickListener: FlickListener) {
        this.flickListener = flickListener
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (v != null && event != null) {
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    val key = pressedKeyByMotionEvent(event, 0)
                    flickListener?.onFlick(
                        gestureType = GestureType.Down,
                        key = key,
                        char = null
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
                    return false
                }

                MotionEvent.ACTION_UP -> {
                    if (pressedKey.pointer == event.getPointerId(event.actionIndex)) {
                        val gestureType = getGestureType(event)
                        val keyInfo =
                            currentInputMode.get().next(
                                keyMap = keyMap,
                                key = pressedKey.key
                            )
                        if (keyInfo == KeyInfo.Null) {
                            flickListener?.onFlick(
                                gestureType = gestureType,
                                key = pressedKey.key,
                                char = null
                            )
//                            if (pressedKey.Key == KeyInfo.SideKeyInputMode) {
//                                handleClickInputModeSwitch()
//                            }
                        } else if (keyInfo is KeyInfo.KeyTapFlickInfo) {
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
                    }
                    resetAllKeys()
                }

                MotionEvent.ACTION_MOVE -> {

                }

                MotionEvent.ACTION_POINTER_DOWN -> {

                }

                MotionEvent.ACTION_POINTER_UP -> {

                }
            }
        }
        return false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
    }

    private fun release() {
        flickListener = null
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
            Key.KeyDakuten -> {
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

            Key.SideKeyEnglish -> {
                resetAllKeys()
                binding.keyEnglish.isPressed = true
            }

            Key.SideKeyJapanese -> {
                resetAllKeys()
                binding.keyJapanese.isPressed = true
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
        binding.keyEnglish.isPressed = false
        binding.keyJapanese.isPressed = false
        binding.keyLeftCursor.isPressed = false
        binding.keyRightCursor.isPressed = false
        binding.keyDelete.isPressed = false
        binding.keySpace.isPressed = false
        binding.keyEnter.isPressed = false
    }

    private fun pressedKeyByMotionEvent(event: MotionEvent, pointer: Int): Key {
        val (x, y) = getRawCoordinates(event, pointer)

        val keyRects = listOf(
            // ---- Side Keys ----
            KeyRect(
                Key.SideKeySymbol,
                binding.keyKigou.layoutXPosition(),
                binding.keyKigou.layoutYPosition(),
                binding.keyKigou.layoutXPosition() + binding.keyKigou.width,
                binding.keyKigou.layoutYPosition() + binding.keyKigou.height
            ),
            KeyRect(
                Key.SideKeyEnglish,
                binding.keyEnglish.layoutXPosition(),
                binding.keyEnglish.layoutYPosition(),
                binding.keyEnglish.layoutXPosition() + binding.keyEnglish.width,
                binding.keyEnglish.layoutYPosition() + binding.keyEnglish.height
            ),
            KeyRect(
                Key.SideKeyJapanese,
                binding.keyJapanese.layoutXPosition(),
                binding.keyJapanese.layoutYPosition(),
                binding.keyJapanese.layoutXPosition() + binding.keyJapanese.width,
                binding.keyJapanese.layoutYPosition() + binding.keyJapanese.height
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
                Key.KeyDakuten,
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

}