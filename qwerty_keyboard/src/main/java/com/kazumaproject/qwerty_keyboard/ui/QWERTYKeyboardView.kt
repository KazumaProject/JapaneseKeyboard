package com.kazumaproject.qwerty_keyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.MotionEvent
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.util.isNotEmpty
import androidx.core.util.size
import androidx.core.view.isVisible
import com.kazumaproject.core.domain.listener.QWERTYKeyListener
import com.kazumaproject.core.domain.qwerty.QWERTYKey
import com.kazumaproject.core.domain.qwerty.QWERTYKeyInfo
import com.kazumaproject.core.domain.qwerty.QWERTYKeyMap
import com.kazumaproject.qwerty_keyboard.databinding.QwertyLayoutBinding

class QWERTYKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: QwertyLayoutBinding

    /** Map each active pointer ID → the QWERTYButton it’s currently “pressing” (or null). */
    private val pointerButtonMap = SparseArray<QWERTYButton?>()

    /** Once the first finger is canceled by a second finger, we “suppress” it until it actually lifts. */
    private var suppressedPointerId: Int? = null

    private var qwertyKeyListener: QWERTYKeyListener? = null

    /** Reusable Rect for hit‐testing a child’s bounds. */
    private val hitRect = Rect()

    private var qwertyKeyMap: QWERTYKeyMap

    init {
        isClickable = true
        isFocusable = true
        val inflater = LayoutInflater.from(context)
        binding = QwertyLayoutBinding.inflate(inflater, this)
        qwertyKeyMap = QWERTYKeyMap()
    }

    private val qwertyButtonMap: Map<Any, QWERTYKey> = mapOf(
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
        binding.keyShift to QWERTYKey.QWERTYKeyShift,
        binding.keyDelete to QWERTYKey.QWERTYKeyDelete,
        binding.key123 to QWERTYKey.QWERTYKeySwitchMode,
        binding.keySpace to QWERTYKey.QWERTYKeySpace,
        binding.keyReturn to QWERTYKey.QWERTYKeyReturn
    )

    fun setOnQWERTYKeyListener(qwertyKeyListener: QWERTYKeyListener) {
        this.qwertyKeyListener = qwertyKeyListener
    }

    /**
     * Intercept the entire touch stream so we can handle MULTI‐TOUCH ourselves.
     * Returning true on ACTION_DOWN ensures this view’s onTouchEvent(...) sees
     * all subsequent MOVE / POINTER_DOWN / UP events.
     */
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return event.actionMasked == MotionEvent.ACTION_DOWN
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // If two fingers somehow end up down simultaneously, or we already had a tracked pointer,
                // clear everything first. Also clear any leftover suppression.
                if (event.pointerCount > 1 || pointerButtonMap.isNotEmpty()) {
                    clearAllPressed()
                }
                suppressedPointerId = null
                handlePointerDown(event, pointerIndex = 0)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // A second (or third, etc.) finger went down.
                // If exactly one pointer was tracked so far, we “cancel” it as if it was an UP.
                if (pointerButtonMap.size == 1) {
                    val firstPointerId = pointerButtonMap.keyAt(0)
                    val firstBtn = pointerButtonMap.valueAt(0)
                    firstBtn?.let { btn ->
                        // Visually un‐press:
                        btn.isPressed = false
                        // Fire the “key‐up” callback for that first pointer:
                        val qwertyKey = qwertyButtonMap[btn] ?: QWERTYKey.QWERTYKeyNotSelect
                        val info: QWERTYKeyInfo = qwertyKeyMap.getKeyInfoDefault(qwertyKey)

                        // (Re-insert your variation‐logging here if desired)
                        if (info is QWERTYKeyInfo.QWERTYVariation) {
                            val tap = info.tap
                            val cap = info.capChar
                            val variations = info.variations
                            val capVariations = info.capVariations
                            Log.d(
                                "SIMULATED_ACTION_UP",
                                "KEY: $qwertyKey, tap: $tap, cap: $cap, " +
                                        "variations: $variations, capVariations: $capVariations"
                            )
                        }
                        qwertyKeyListener?.onTouchQWERTYKey(qwertyKey)
                    }

                    // Mark that first finger as “suppressed” until it actually lifts:
                    suppressedPointerId = firstPointerId
                    pointerButtonMap.remove(firstPointerId)
                }

                // Now track the new (second) finger as the only pressed one:
                val newIndex = event.actionIndex
                handlePointerDown(event, pointerIndex = newIndex)
            }

            MotionEvent.ACTION_MOVE -> {
                // We only track one pointer at a time (after clearing on multi‐touch),
                // but we still get MOVE events for each down finger. Skip any suppressed one:
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    if (pid == suppressedPointerId) {
                        // don’t re-activate a suppressed finger
                        continue
                    }
                    handlePointerMove(event, pointerIndex = i, pointerId = pid)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // One of the fingers lifted, but someone is still down.
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)

                // If that was the suppressed pointer, we can clear it now
                if (suppressedPointerId == pointerId) {
                    suppressedPointerId = null
                }

                // If that lifted pointer was in our map, fire its “UP” logic:
                val btn = pointerButtonMap[pointerId]
                btn?.let {
                    it.isPressed = false
                    val qwertyKey = qwertyButtonMap[it] ?: QWERTYKey.QWERTYKeyNotSelect
                    val info: QWERTYKeyInfo = qwertyKeyMap.getKeyInfoDefault(qwertyKey)

                    if (info is QWERTYKeyInfo.QWERTYVariation) {
                        val tap = info.tap
                        val cap = info.capChar
                        val variations = info.variations
                        val capVariations = info.capVariations
                        Log.d("ACTION_POINTER_UP", "UP: $qwertyKey, tap: $tap, cap: $cap")
                        Log.d(
                            "ACTION_POINTER_UP",
                            "variations: $variations, capVariations: $capVariations"
                        )
                    }
                    qwertyKeyListener?.onTouchQWERTYKey(qwertyKey)
                }
                pointerButtonMap.remove(pointerId)
                // Don’t clearAllPressed(), because there might still be another (non-suppressed) finger down.
            }

            MotionEvent.ACTION_UP -> {
                // The final finger lifted. If it was suppressed, clear suppression; otherwise
                // treat it as a normal “UP” for whatever is tracked.
                val liftedId = event.getPointerId(event.actionIndex)
                if (suppressedPointerId == liftedId) {
                    // The suppressed finger finally went up—just clear the flag
                    suppressedPointerId = null
                }

                // If there is exactly one tracked button, fire its UP:
                if (pointerButtonMap.size == 1) {
                    val btn = pointerButtonMap.valueAt(0)
                    btn?.let {
                        it.isPressed = false
                        val qwertyKey = qwertyButtonMap[it] ?: QWERTYKey.QWERTYKeyNotSelect
                        val info: QWERTYKeyInfo = qwertyKeyMap.getKeyInfoDefault(qwertyKey)

                        if (info is QWERTYKeyInfo.QWERTYVariation) {
                            val tap = info.tap
                            val cap = info.capChar
                            val variations = info.variations
                            val capVariations = info.capVariations
                            Log.d("ACTION_UP", "UP: $qwertyKey, tap: $tap, cap: $cap")
                            Log.d(
                                "ACTION_UP",
                                "variations: $variations, capVariations: $capVariations"
                            )
                        }
                        qwertyKeyListener?.onTouchQWERTYKey(qwertyKey)
                    }
                }

                // Finally clear everything, so a lingering first finger won’t “reactivate”:
                clearAllPressed()
            }

            MotionEvent.ACTION_CANCEL -> {
                clearAllPressed()
            }
        }
        return true
    }

    /**
     * Handle a pointer (finger) going down: press whichever key is under its (x,y).
     * We skip any pointer ID that is suppressed.
     */
    private fun handlePointerDown(event: MotionEvent, pointerIndex: Int) {
        val pid = event.getPointerId(pointerIndex)
        if (pid == suppressedPointerId) return
        val x = event.getX(pointerIndex).toInt()
        val y = event.getY(pointerIndex).toInt()
        val btn = findButtonUnder(x, y)
        btn?.isPressed = true
        pointerButtonMap.put(pid, btn)
    }

    /**
     * Handle pointer movement for the single tracked pointer (ignores suppressed).
     */
    private fun handlePointerMove(event: MotionEvent, pointerIndex: Int, pointerId: Int) {
        if (pointerId == suppressedPointerId) return
        val x = event.getX(pointerIndex).toInt()
        val y = event.getY(pointerIndex).toInt()
        val previousBtn = pointerButtonMap[pointerId]
        val currentBtn = findButtonUnder(x, y)

        if (currentBtn != previousBtn) {
            previousBtn?.isPressed = false
            currentBtn?.isPressed = true
            pointerButtonMap.put(pointerId, currentBtn)
        }
    }

    /** Un-press any tracked button, then clear the entire map and any suppression. */
    private fun clearAllPressed() {
        for (i in 0 until pointerButtonMap.size) {
            pointerButtonMap.valueAt(i)?.isPressed = false
        }
        pointerButtonMap.clear()
        suppressedPointerId = null
    }

    /**
     * Hit-test across all direct children. If a visible QWERTYButton’s bounds
     * contain (x,y), return that button. Otherwise return null.
     */
    private fun findButtonUnder(x: Int, y: Int): QWERTYButton? {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is QWERTYButton && child.isVisible) {
                child.getHitRect(hitRect)
                if (hitRect.contains(x, y)) {
                    return child
                }
            }
        }
        return null
    }
}
