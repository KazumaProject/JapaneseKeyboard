package com.kazumaproject.markdownhelperkeyboard.ime_service.tablet

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.kazumaproject.markdownhelperkeyboard.databinding.TabletLayoutBinding
import com.kazumaproject.tenkey.extensions.layoutXPosition
import com.kazumaproject.tenkey.extensions.layoutYPosition
import com.kazumaproject.tenkey.state.GestureType
import com.kazumaproject.tenkey.state.InputMode
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
    private lateinit var pressedKey: TabletPressedKey

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

    private var tabletKeyMap: TabletKeyMap

    private var flickListener: TabletFlickListener? = null

    init {
        (allButtonKeys + allImageButtonKeys).forEach { it.setOnTouchListener(this) }
        tabletKeyMap = TabletKeyMap()
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (v != null && event != null) {
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    val key = pressedKeyByMotionEvent(event, 0)
                    flickListener?.onFlick(
                        gestureType = GestureType.Down,
                        tabletKey = key,
                        char = null
                    )
                    pressedKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        TabletPressedKey(
                            tabletKey = key,
                            pointer = 0,
                            initialX = event.getRawX(event.actionIndex),
                            initialY = event.getRawY(event.actionIndex),
                        )
                    } else {
                        TabletPressedKey(
                            tabletKey = key,
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
        when (pressedKey.tabletKey) {
            // --- あ row ---
            TabletKey.KeyA -> {
                resetAllKeys()
                binding.key51.isPressed = true
            }

            TabletKey.KeyI -> {
                resetAllKeys()
                binding.key52.isPressed = true
            }

            TabletKey.KeyU -> {
                resetAllKeys()
                binding.key53.isPressed = true
            }

            TabletKey.KeyE -> {
                resetAllKeys()
                binding.key54.isPressed = true
            }

            TabletKey.KeyO -> {
                resetAllKeys()
                binding.key55.isPressed = true
            }

            // --- か row ---
            TabletKey.KeyKA -> {
                resetAllKeys()
                binding.key46.isPressed = true
            }

            TabletKey.KeyKI -> {
                resetAllKeys()
                binding.key47.isPressed = true
            }

            TabletKey.KeyKU -> {
                resetAllKeys()
                binding.key48.isPressed = true
            }

            TabletKey.KeyKE -> {
                resetAllKeys()
                binding.key49.isPressed = true
            }

            TabletKey.KeyKO -> {
                resetAllKeys()
                binding.key50.isPressed = true
            }

            // --- さ row ---
            TabletKey.KeySA -> {
                resetAllKeys()
                binding.key41.isPressed = true
            }

            TabletKey.KeySHI -> {
                resetAllKeys()
                binding.key42.isPressed = true
            }

            TabletKey.KeySU -> {
                resetAllKeys()
                binding.key43.isPressed = true
            }

            TabletKey.KeySE -> {
                resetAllKeys()
                binding.key44.isPressed = true
            }

            TabletKey.KeySO -> {
                resetAllKeys()
                binding.key45.isPressed = true
            }

            // --- た row ---
            TabletKey.KeyTA -> {
                resetAllKeys()
                binding.key36.isPressed = true
            }

            TabletKey.KeyCHI -> {
                resetAllKeys()
                binding.key37.isPressed = true
            }

            TabletKey.KeyTSU -> {
                resetAllKeys()
                binding.key38.isPressed = true
            }

            TabletKey.KeyTE -> {
                resetAllKeys()
                binding.key39.isPressed = true
            }

            TabletKey.KeyTO -> {
                resetAllKeys()
                binding.key40.isPressed = true
            }

            // --- な row ---
            TabletKey.KeyNA -> {
                resetAllKeys()
                binding.key31.isPressed = true
            }

            TabletKey.KeyNI -> {
                resetAllKeys()
                binding.key32.isPressed = true
            }

            TabletKey.KeyNU -> {
                resetAllKeys()
                binding.key33.isPressed = true
            }

            TabletKey.KeyNE -> {
                resetAllKeys()
                binding.key34.isPressed = true
            }

            TabletKey.KeyNO -> {
                resetAllKeys()
                binding.key35.isPressed = true
            }

            // --- は row ---
            TabletKey.KeyHA -> {
                resetAllKeys()
                binding.key26.isPressed = true
            }

            TabletKey.KeyHI -> {
                resetAllKeys()
                binding.key27.isPressed = true
            }

            TabletKey.KeyFU -> {
                resetAllKeys()
                binding.key28.isPressed = true
            }

            TabletKey.KeyHE -> {
                resetAllKeys()
                binding.key29.isPressed = true
            }

            TabletKey.KeyHO -> {
                resetAllKeys()
                binding.key30.isPressed = true
            }

            // --- ま row ---
            TabletKey.KeyMA -> {
                resetAllKeys()
                binding.key21.isPressed = true
            }

            TabletKey.KeyMI -> {
                resetAllKeys()
                binding.key22.isPressed = true
            }

            TabletKey.KeyMU -> {
                resetAllKeys()
                binding.key23.isPressed = true
            }

            TabletKey.KeyME -> {
                resetAllKeys()
                binding.key24.isPressed = true
            }

            TabletKey.KeyMO -> {
                resetAllKeys()
                binding.key25.isPressed = true
            }

            // --- や row ---
            TabletKey.KeyYA -> {
                resetAllKeys()
                binding.key16.isPressed = true
            }

            TabletKey.KeyYU -> {
                resetAllKeys()
                binding.key18.isPressed = true
            }

            TabletKey.KeyYO -> {
                resetAllKeys()
                binding.key20.isPressed = true
            }

            TabletKey.KeySPACE1 -> {
                resetAllKeys()
                binding.key17.isPressed = true
            }

            TabletKey.KeySPACE2 -> {
                resetAllKeys()
                binding.key19.isPressed = true
            }

            // --- ら row ---
            TabletKey.KeyRA -> {
                resetAllKeys()
                binding.key11.isPressed = true
            }

            TabletKey.KeyRI -> {
                resetAllKeys()
                binding.key12.isPressed = true
            }

            TabletKey.KeyRU -> {
                resetAllKeys()
                binding.key13.isPressed = true
            }

            TabletKey.KeyRE -> {
                resetAllKeys()
                binding.key14.isPressed = true
            }

            TabletKey.KeyRO -> {
                resetAllKeys()
                binding.key15.isPressed = true
            }

            // --- わ row + ん + minus ---
            TabletKey.KeyWA -> {
                resetAllKeys()
                binding.key6.isPressed = true
            }

            TabletKey.KeyWO -> {
                resetAllKeys()
                binding.key7.isPressed = true
            }

            TabletKey.KeyN -> {
                resetAllKeys()
                binding.key8.isPressed = true
            }

            TabletKey.KeyMinus -> {
                resetAllKeys()
                binding.key9.isPressed = true
            }

            // --- Modifiers & punctuation ---
            TabletKey.KeyDakuten -> {
                resetAllKeys()
                binding.key10.isPressed = true
            }

            TabletKey.KeyKagikakko -> {
                resetAllKeys()
                binding.key1.isPressed = true
            }

            TabletKey.KeyQuestion -> {
                resetAllKeys()
                binding.key2.isPressed = true
            }

            TabletKey.KeyCaution -> {
                resetAllKeys()
                binding.key3.isPressed = true
            }

            TabletKey.KeyTouten -> {
                resetAllKeys()
                binding.key4.isPressed = true
            }

            TabletKey.KeyKuten -> {
                resetAllKeys()
                binding.key5.isPressed = true
            }

            // --- Side-row keys ---
            TabletKey.SideKeySymbol -> {
                resetAllKeys()
                binding.keyKigou.isPressed = true
            }

            TabletKey.SideKeyEnglish -> {
                resetAllKeys()
                binding.keyEnglish.isPressed = true
            }

            TabletKey.SideKeyJapanese -> {
                resetAllKeys()
                binding.keyJapanese.isPressed = true
            }

            TabletKey.SideKeyCursorLeft -> {
                resetAllKeys()
                binding.keyLeftCursor.isPressed = true
            }

            TabletKey.SideKeyCursorRight -> {
                resetAllKeys()
                binding.keyRightCursor.isPressed = true
            }

            TabletKey.SideKeyDelete -> {
                resetAllKeys()
                binding.keyDelete.isPressed = true
            }

            TabletKey.SideKeySpace -> {
                resetAllKeys()
                binding.keySpace.isPressed = true
            }

            TabletKey.SideKeyEnter -> {
                resetAllKeys()
                binding.keyEnter.isPressed = true
            }

            TabletKey.NotSelected -> {
                // no key pressed
            }
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

    private fun pressedKeyByMotionEvent(event: MotionEvent, pointer: Int): TabletKey {
        val (x, y) = getRawCoordinates(event, pointer)

        val keyRects = listOf(
            // ---- Side Keys ----
            TabletKeyRect(
                TabletKey.SideKeySymbol,
                binding.keyKigou.layoutXPosition(),
                binding.keyKigou.layoutYPosition(),
                binding.keyKigou.layoutXPosition() + binding.keyKigou.width,
                binding.keyKigou.layoutYPosition() + binding.keyKigou.height
            ),
            TabletKeyRect(
                TabletKey.SideKeyEnglish,
                binding.keyEnglish.layoutXPosition(),
                binding.keyEnglish.layoutYPosition(),
                binding.keyEnglish.layoutXPosition() + binding.keyEnglish.width,
                binding.keyEnglish.layoutYPosition() + binding.keyEnglish.height
            ),
            TabletKeyRect(
                TabletKey.SideKeyJapanese,
                binding.keyJapanese.layoutXPosition(),
                binding.keyJapanese.layoutYPosition(),
                binding.keyJapanese.layoutXPosition() + binding.keyJapanese.width,
                binding.keyJapanese.layoutYPosition() + binding.keyJapanese.height
            ),
            TabletKeyRect(
                TabletKey.SideKeyCursorLeft,
                binding.keyLeftCursor.layoutXPosition(),
                binding.keyLeftCursor.layoutYPosition(),
                binding.keyLeftCursor.layoutXPosition() + binding.keyLeftCursor.width,
                binding.keyLeftCursor.layoutYPosition() + binding.keyLeftCursor.height
            ),
            TabletKeyRect(
                TabletKey.SideKeyCursorRight,
                binding.keyRightCursor.layoutXPosition(),
                binding.keyRightCursor.layoutYPosition(),
                binding.keyRightCursor.layoutXPosition() + binding.keyRightCursor.width,
                binding.keyRightCursor.layoutYPosition() + binding.keyRightCursor.height
            ),
            TabletKeyRect(
                TabletKey.SideKeyDelete,
                binding.keyDelete.layoutXPosition(),
                binding.keyDelete.layoutYPosition(),
                binding.keyDelete.layoutXPosition() + binding.keyDelete.width,
                binding.keyDelete.layoutYPosition() + binding.keyDelete.height
            ),
            TabletKeyRect(
                TabletKey.SideKeySpace,
                binding.keySpace.layoutXPosition(),
                binding.keySpace.layoutYPosition(),
                binding.keySpace.layoutXPosition() + binding.keySpace.width,
                binding.keySpace.layoutYPosition() + binding.keySpace.height
            ),
            TabletKeyRect(
                TabletKey.SideKeyEnter,
                binding.keyEnter.layoutXPosition(),
                binding.keyEnter.layoutYPosition(),
                binding.keyEnter.layoutXPosition() + binding.keyEnter.width,
                binding.keyEnter.layoutYPosition() + binding.keyEnter.height
            ),

            // ---- Character Keys ----
            TabletKeyRect(
                TabletKey.KeyKagikakko,
                binding.key1.layoutXPosition(),
                binding.key1.layoutYPosition(),
                binding.key1.layoutXPosition() + binding.key1.width,
                binding.key1.layoutYPosition() + binding.key1.height
            ),
            TabletKeyRect(
                TabletKey.KeyQuestion,
                binding.key2.layoutXPosition(),
                binding.key2.layoutYPosition(),
                binding.key2.layoutXPosition() + binding.key2.width,
                binding.key2.layoutYPosition() + binding.key2.height
            ),
            TabletKeyRect(
                TabletKey.KeyCaution,
                binding.key3.layoutXPosition(),
                binding.key3.layoutYPosition(),
                binding.key3.layoutXPosition() + binding.key3.width,
                binding.key3.layoutYPosition() + binding.key3.height
            ),
            TabletKeyRect(
                TabletKey.KeyTouten,
                binding.key4.layoutXPosition(),
                binding.key4.layoutYPosition(),
                binding.key4.layoutXPosition() + binding.key4.width,
                binding.key4.layoutYPosition() + binding.key4.height
            ),
            TabletKeyRect(
                TabletKey.KeyKuten,
                binding.key5.layoutXPosition(),
                binding.key5.layoutYPosition(),
                binding.key5.layoutXPosition() + binding.key5.width,
                binding.key5.layoutYPosition() + binding.key5.height
            ),

            // わ row and punctuation
            TabletKeyRect(
                TabletKey.KeyWA,
                binding.key6.layoutXPosition(),
                binding.key6.layoutYPosition(),
                binding.key6.layoutXPosition() + binding.key6.width,
                binding.key6.layoutYPosition() + binding.key6.height
            ),
            TabletKeyRect(
                TabletKey.KeyWO,
                binding.key7.layoutXPosition(),
                binding.key7.layoutYPosition(),
                binding.key7.layoutXPosition() + binding.key7.width,
                binding.key7.layoutYPosition() + binding.key7.height
            ),
            TabletKeyRect(
                TabletKey.KeyN,
                binding.key8.layoutXPosition(),
                binding.key8.layoutYPosition(),
                binding.key8.layoutXPosition() + binding.key8.width,
                binding.key8.layoutYPosition() + binding.key8.height
            ),
            TabletKeyRect(
                TabletKey.KeyMinus,
                binding.key9.layoutXPosition(),
                binding.key9.layoutYPosition(),
                binding.key9.layoutXPosition() + binding.key9.width,
                binding.key9.layoutYPosition() + binding.key9.height
            ),
            TabletKeyRect(
                TabletKey.KeyDakuten,
                binding.key10.layoutXPosition(),
                binding.key10.layoutYPosition(),
                binding.key10.layoutXPosition() + binding.key10.width,
                binding.key10.layoutYPosition() + binding.key10.height
            ),

            // ら row
            TabletKeyRect(
                TabletKey.KeyRA,
                binding.key11.layoutXPosition(),
                binding.key11.layoutYPosition(),
                binding.key11.layoutXPosition() + binding.key11.width,
                binding.key11.layoutYPosition() + binding.key11.height
            ),
            TabletKeyRect(
                TabletKey.KeyRI,
                binding.key12.layoutXPosition(),
                binding.key12.layoutYPosition(),
                binding.key12.layoutXPosition() + binding.key12.width,
                binding.key12.layoutYPosition() + binding.key12.height
            ),
            TabletKeyRect(
                TabletKey.KeyRU,
                binding.key13.layoutXPosition(),
                binding.key13.layoutYPosition(),
                binding.key13.layoutXPosition() + binding.key13.width,
                binding.key13.layoutYPosition() + binding.key13.height
            ),
            TabletKeyRect(
                TabletKey.KeyRE,
                binding.key14.layoutXPosition(),
                binding.key14.layoutYPosition(),
                binding.key14.layoutXPosition() + binding.key14.width,
                binding.key14.layoutYPosition() + binding.key14.height
            ),
            TabletKeyRect(
                TabletKey.KeyRO,
                binding.key15.layoutXPosition(),
                binding.key15.layoutYPosition(),
                binding.key15.layoutXPosition() + binding.key15.width,
                binding.key15.layoutYPosition() + binding.key15.height
            ),

            // や row
            TabletKeyRect(
                TabletKey.KeyYA,
                binding.key16.layoutXPosition(),
                binding.key16.layoutYPosition(),
                binding.key16.layoutXPosition() + binding.key16.width,
                binding.key16.layoutYPosition() + binding.key16.height
            ),
            // key17 = (empty)
            TabletKeyRect(
                TabletKey.KeySPACE1,
                binding.key17.layoutXPosition(),
                binding.key17.layoutYPosition(),
                binding.key17.layoutXPosition() + binding.key17.width,
                binding.key17.layoutYPosition() + binding.key17.height
            ),
            TabletKeyRect(
                TabletKey.KeyYU,
                binding.key18.layoutXPosition(),
                binding.key18.layoutYPosition(),
                binding.key18.layoutXPosition() + binding.key18.width,
                binding.key18.layoutYPosition() + binding.key18.height
            ),
            // key19 = (empty)
            TabletKeyRect(
                TabletKey.KeySPACE2,
                binding.key19.layoutXPosition(),
                binding.key19.layoutYPosition(),
                binding.key19.layoutXPosition() + binding.key19.width,
                binding.key19.layoutYPosition() + binding.key19.height
            ),
            TabletKeyRect(
                TabletKey.KeyYO,
                binding.key20.layoutXPosition(),
                binding.key20.layoutYPosition(),
                binding.key20.layoutXPosition() + binding.key20.width,
                binding.key20.layoutYPosition() + binding.key20.height
            ),

            // ま row
            TabletKeyRect(
                TabletKey.KeyMA,
                binding.key21.layoutXPosition(),
                binding.key21.layoutYPosition(),
                binding.key21.layoutXPosition() + binding.key21.width,
                binding.key21.layoutYPosition() + binding.key21.height
            ),
            TabletKeyRect(
                TabletKey.KeyMI,
                binding.key22.layoutXPosition(),
                binding.key22.layoutYPosition(),
                binding.key22.layoutXPosition() + binding.key22.width,
                binding.key22.layoutYPosition() + binding.key22.height
            ),
            TabletKeyRect(
                TabletKey.KeyMU,
                binding.key23.layoutXPosition(),
                binding.key23.layoutYPosition(),
                binding.key23.layoutXPosition() + binding.key23.width,
                binding.key23.layoutYPosition() + binding.key23.height
            ),
            TabletKeyRect(
                TabletKey.KeyME,
                binding.key24.layoutXPosition(),
                binding.key24.layoutYPosition(),
                binding.key24.layoutXPosition() + binding.key24.width,
                binding.key24.layoutYPosition() + binding.key24.height
            ),
            TabletKeyRect(
                TabletKey.KeyMO,
                binding.key25.layoutXPosition(),
                binding.key25.layoutYPosition(),
                binding.key25.layoutXPosition() + binding.key25.width,
                binding.key25.layoutYPosition() + binding.key25.height
            ),

            // は row
            TabletKeyRect(
                TabletKey.KeyHA,
                binding.key26.layoutXPosition(),
                binding.key26.layoutYPosition(),
                binding.key26.layoutXPosition() + binding.key26.width,
                binding.key26.layoutYPosition() + binding.key26.height
            ),
            TabletKeyRect(
                TabletKey.KeyHI,
                binding.key27.layoutXPosition(),
                binding.key27.layoutYPosition(),
                binding.key27.layoutXPosition() + binding.key27.width,
                binding.key27.layoutYPosition() + binding.key27.height
            ),
            TabletKeyRect(
                TabletKey.KeyFU,
                binding.key28.layoutXPosition(),
                binding.key28.layoutYPosition(),
                binding.key28.layoutXPosition() + binding.key28.width,
                binding.key28.layoutYPosition() + binding.key28.height
            ),
            TabletKeyRect(
                TabletKey.KeyHE,
                binding.key29.layoutXPosition(),
                binding.key29.layoutYPosition(),
                binding.key29.layoutXPosition() + binding.key29.width,
                binding.key29.layoutYPosition() + binding.key29.height
            ),
            TabletKeyRect(
                TabletKey.KeyHO,
                binding.key30.layoutXPosition(),
                binding.key30.layoutYPosition(),
                binding.key30.layoutXPosition() + binding.key30.width,
                binding.key30.layoutYPosition() + binding.key30.height
            ),

            // な row
            TabletKeyRect(
                TabletKey.KeyNA,
                binding.key31.layoutXPosition(),
                binding.key31.layoutYPosition(),
                binding.key31.layoutXPosition() + binding.key31.width,
                binding.key31.layoutYPosition() + binding.key31.height
            ),
            TabletKeyRect(
                TabletKey.KeyNI,
                binding.key32.layoutXPosition(),
                binding.key32.layoutYPosition(),
                binding.key32.layoutXPosition() + binding.key32.width,
                binding.key32.layoutYPosition() + binding.key32.height
            ),
            TabletKeyRect(
                TabletKey.KeyNU,
                binding.key33.layoutXPosition(),
                binding.key33.layoutYPosition(),
                binding.key33.layoutXPosition() + binding.key33.width,
                binding.key33.layoutYPosition() + binding.key33.height
            ),
            TabletKeyRect(
                TabletKey.KeyNE,
                binding.key34.layoutXPosition(),
                binding.key34.layoutYPosition(),
                binding.key34.layoutXPosition() + binding.key34.width,
                binding.key34.layoutYPosition() + binding.key34.height
            ),
            TabletKeyRect(
                TabletKey.KeyNO,
                binding.key35.layoutXPosition(),
                binding.key35.layoutYPosition(),
                binding.key35.layoutXPosition() + binding.key35.width,
                binding.key35.layoutYPosition() + binding.key35.height
            ),

            // た row
            TabletKeyRect(
                TabletKey.KeyTA,
                binding.key36.layoutXPosition(),
                binding.key36.layoutYPosition(),
                binding.key36.layoutXPosition() + binding.key36.width,
                binding.key36.layoutYPosition() + binding.key36.height
            ),
            TabletKeyRect(
                TabletKey.KeyCHI,
                binding.key37.layoutXPosition(),
                binding.key37.layoutYPosition(),
                binding.key37.layoutXPosition() + binding.key37.width,
                binding.key37.layoutYPosition() + binding.key37.height
            ),
            TabletKeyRect(
                TabletKey.KeyTSU,
                binding.key38.layoutXPosition(),
                binding.key38.layoutYPosition(),
                binding.key38.layoutXPosition() + binding.key38.width,
                binding.key38.layoutYPosition() + binding.key38.height
            ),
            TabletKeyRect(
                TabletKey.KeyTE,
                binding.key39.layoutXPosition(),
                binding.key39.layoutYPosition(),
                binding.key39.layoutXPosition() + binding.key39.width,
                binding.key39.layoutYPosition() + binding.key39.height
            ),
            TabletKeyRect(
                TabletKey.KeyTO,
                binding.key40.layoutXPosition(),
                binding.key40.layoutYPosition(),
                binding.key40.layoutXPosition() + binding.key40.width,
                binding.key40.layoutYPosition() + binding.key40.height
            ),

            // さ row
            TabletKeyRect(
                TabletKey.KeySA,
                binding.key41.layoutXPosition(),
                binding.key41.layoutYPosition(),
                binding.key41.layoutXPosition() + binding.key41.width,
                binding.key41.layoutYPosition() + binding.key41.height
            ),
            TabletKeyRect(
                TabletKey.KeySHI,
                binding.key42.layoutXPosition(),
                binding.key42.layoutYPosition(),
                binding.key42.layoutXPosition() + binding.key42.width,
                binding.key42.layoutYPosition() + binding.key42.height
            ),
            TabletKeyRect(
                TabletKey.KeySU,
                binding.key43.layoutXPosition(),
                binding.key43.layoutYPosition(),
                binding.key43.layoutXPosition() + binding.key43.width,
                binding.key43.layoutYPosition() + binding.key43.height
            ),
            TabletKeyRect(
                TabletKey.KeySE,
                binding.key44.layoutXPosition(),
                binding.key44.layoutYPosition(),
                binding.key44.layoutXPosition() + binding.key44.width,
                binding.key44.layoutYPosition() + binding.key44.height
            ),
            TabletKeyRect(
                TabletKey.KeySO,
                binding.key45.layoutXPosition(),
                binding.key45.layoutYPosition(),
                binding.key45.layoutXPosition() + binding.key45.width,
                binding.key45.layoutYPosition() + binding.key45.height
            ),

            // か row
            TabletKeyRect(
                TabletKey.KeyKA,
                binding.key46.layoutXPosition(),
                binding.key46.layoutYPosition(),
                binding.key46.layoutXPosition() + binding.key46.width,
                binding.key46.layoutYPosition() + binding.key46.height
            ),
            TabletKeyRect(
                TabletKey.KeyKI,
                binding.key47.layoutXPosition(),
                binding.key47.layoutYPosition(),
                binding.key47.layoutXPosition() + binding.key47.width,
                binding.key47.layoutYPosition() + binding.key47.height
            ),
            TabletKeyRect(
                TabletKey.KeyKU,
                binding.key48.layoutXPosition(),
                binding.key48.layoutYPosition(),
                binding.key48.layoutXPosition() + binding.key48.width,
                binding.key48.layoutYPosition() + binding.key48.height
            ),
            TabletKeyRect(
                TabletKey.KeyKE,
                binding.key49.layoutXPosition(),
                binding.key49.layoutYPosition(),
                binding.key49.layoutXPosition() + binding.key49.width,
                binding.key49.layoutYPosition() + binding.key49.height
            ),
            TabletKeyRect(
                TabletKey.KeyKO,
                binding.key50.layoutXPosition(),
                binding.key50.layoutYPosition(),
                binding.key50.layoutXPosition() + binding.key50.width,
                binding.key50.layoutYPosition() + binding.key50.height
            ),

            // あ row
            TabletKeyRect(
                TabletKey.KeyA,
                binding.key51.layoutXPosition(),
                binding.key51.layoutYPosition(),
                binding.key51.layoutXPosition() + binding.key51.width,
                binding.key51.layoutYPosition() + binding.key51.height
            ),
            TabletKeyRect(
                TabletKey.KeyI,
                binding.key52.layoutXPosition(),
                binding.key52.layoutYPosition(),
                binding.key52.layoutXPosition() + binding.key52.width,
                binding.key52.layoutYPosition() + binding.key52.height
            ),
            TabletKeyRect(
                TabletKey.KeyU,
                binding.key53.layoutXPosition(),
                binding.key53.layoutYPosition(),
                binding.key53.layoutXPosition() + binding.key53.width,
                binding.key53.layoutYPosition() + binding.key53.height
            ),
            TabletKeyRect(
                TabletKey.KeyE,
                binding.key54.layoutXPosition(),
                binding.key54.layoutYPosition(),
                binding.key54.layoutXPosition() + binding.key54.width,
                binding.key54.layoutYPosition() + binding.key54.height
            ),
            TabletKeyRect(
                TabletKey.KeyO,
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
        return nearest?.key ?: TabletKey.NotSelected
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