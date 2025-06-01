package com.kazumaproject.qwerty_keyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.util.isNotEmpty
import androidx.core.util.size
import androidx.core.view.isVisible
import com.kazumaproject.core.domain.listener.QWERTYKeyListener
import com.kazumaproject.core.domain.qwerty.QWERTYKey
import com.kazumaproject.core.domain.qwerty.QWERTYKeyInfo
import com.kazumaproject.core.domain.qwerty.QWERTYKeyMap
import com.kazumaproject.qwerty_keyboard.R
import com.kazumaproject.qwerty_keyboard.databinding.QwertyLayoutBinding

/**
 * A custom keyboard view that detects touches on multiple key types (QWERTYButton, AppCompatButton, AppCompatImageButton).
 * It shows a key preview as a PopupWindow above the pressed key, and notifies a QWERTYKeyListener of key events.
 * It also recognizes a double-tap on the Shift key and suppresses the subsequent single-tap event.
 */
class QWERTYKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: QwertyLayoutBinding

    /** Map each active pointer ID → the View (any key type) it’s currently “pressing” (or null). */
    private val pointerButtonMap = SparseArray<View?>()

    /** If a second finger cancels the first, we suppress that first pointer until it actually lifts. */
    private var suppressedPointerId: Int? = null

    private var keyPreviewPopup: PopupWindow? = null
    private val hitRect = Rect()

    private var qwertyKeyListener: QWERTYKeyListener? = null
    private var qwertyKeyMap: QWERTYKeyMap

    // ① Track the last time Shift was tapped (to detect double-tap)
    private var lastShiftTapTime = 0L
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

    // ② After detecting a double-tap, suppress the next single-tap for Shift.
    private var shiftDoubleTapped = false

    init {
        isClickable = true
        isFocusable = true

        val inflater = LayoutInflater.from(context)
        binding = QwertyLayoutBinding.inflate(inflater, this)

        qwertyKeyMap = QWERTYKeyMap()
    }

    /** Map each key‐View (any type) to its corresponding QWERTYKey. */
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

            // Side and function keys
            binding.keyShift to QWERTYKey.QWERTYKeyShift,      // AppCompatImageButton
            binding.keyDelete to QWERTYKey.QWERTYKeyDelete,    // AppCompatImageButton
            binding.key123 to QWERTYKey.QWERTYKeySwitchMode,   // AppCompatButton
            binding.keySpace to QWERTYKey.QWERTYKeySpace,      // QWERTYButton
            binding.keyReturn to QWERTYKey.QWERTYKeyReturn     // AppCompatButton
        )
    }

    /**
     * Set a listener that will receive QWERTYKey events (onTouchQWERTYKey).
     */
    fun setOnQWERTYKeyListener(listener: QWERTYKeyListener) {
        this.qwertyKeyListener = listener
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // Ensure we intercept the initial DOWN so that onTouchEvent receives it
        return event.actionMasked == MotionEvent.ACTION_DOWN
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
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
                // Cancel the first tracked pointer if exactly one was active
                if (pointerButtonMap.size == 1) {
                    val firstPointerId = pointerButtonMap.keyAt(0)
                    val firstView = pointerButtonMap.valueAt(0)
                    firstView?.let { view ->
                        view.isPressed = false
                        dismissKeyPreview()

                        val qwertyKey = qwertyButtonMap[view] ?: QWERTYKey.QWERTYKeyNotSelect
                        logVariationIfNeeded(qwertyKey)
                        qwertyKeyListener?.onTouchQWERTYKey(qwertyKey)
                    }
                    suppressedPointerId = firstPointerId
                    pointerButtonMap.remove(firstPointerId)
                }
                // Now track the new (second) finger
                val newIndex = event.actionIndex
                handlePointerDown(event, pointerIndex = newIndex)
            }

            MotionEvent.ACTION_MOVE -> {
                // On MOVE, process each pointer except any suppressed one
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    if (pid == suppressedPointerId) continue
                    handlePointerMove(event, pointerIndex = i, pointerId = pid)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)

                // If it was suppressed, clear suppression
                if (suppressedPointerId == pointerId) {
                    suppressedPointerId = null
                }

                // If that pointer was tracked, fire its “key up”
                val view = pointerButtonMap[pointerId]
                view?.let {
                    it.isPressed = false
                    dismissKeyPreview()

                    val wasShift = (it.id == binding.keyShift.id)
                    // ③ If Shift was double-tapped, suppress this single-tap event
                    if (wasShift && shiftDoubleTapped) {
                        // Consume the single-tap without notifying listener
                        shiftDoubleTapped = false
                    } else {
                        val qwertyKey = qwertyButtonMap[it] ?: QWERTYKey.QWERTYKeyNotSelect
                        logVariationIfNeeded(qwertyKey)
                        qwertyKeyListener?.onTouchQWERTYKey(qwertyKey)
                    }
                }
                pointerButtonMap.remove(pointerId)
            }

            MotionEvent.ACTION_UP -> {
                val liftedId = event.getPointerId(event.actionIndex)
                if (suppressedPointerId == liftedId) {
                    suppressedPointerId = null
                }
                if (pointerButtonMap.size == 1) {
                    val view = pointerButtonMap.valueAt(0)
                    view?.let {
                        it.isPressed = false
                        dismissKeyPreview()

                        val wasShift = (it.id == binding.keyShift.id)
                        // ④ If Shift was double-tapped, suppress this single-tap event
                        if (wasShift && shiftDoubleTapped) {
                            shiftDoubleTapped = false
                        } else {
                            val qwertyKey = qwertyButtonMap[it] ?: QWERTYKey.QWERTYKeyNotSelect
                            logVariationIfNeeded(qwertyKey)
                            qwertyKeyListener?.onTouchQWERTYKey(qwertyKey)
                        }
                    }
                }
                clearAllPressed()
            }

            MotionEvent.ACTION_CANCEL -> {
                clearAllPressed()
            }
        }
        return true
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
            it.isPressed = true
            pointerButtonMap.put(pid, it)

            // ⑤ If this is the Shift key, check for double-tap
            if (it.id == binding.keyShift.id) {
                val now = SystemClock.uptimeMillis()
                if (now - lastShiftTapTime <= doubleTapTimeout) {
                    // Double-tap detected
                    onShiftDoubleTapped()
                    // Prevent the next single-tap event
                    shiftDoubleTapped = true
                    // Reset so the next tap after this isn’t treated as “second” of a triple
                    lastShiftTapTime = 0L
                } else {
                    // Not a double-tap (yet) – record this tap time
                    lastShiftTapTime = now
                }
            }

            // ⑥ Show preview for non-edge, non-icon keys (same as before)
            if (it.id != binding.keySpace.id &&
                it.id != binding.keyDelete.id &&
                it.id != binding.keyShift.id &&
                it.id != binding.key123.id &&
                it.id != binding.keyReturn.id
            ) {
                showKeyPreview(it)
            }
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
            previousView?.isPressed = false
            dismissKeyPreview()

            currentView?.let {
                it.isPressed = true
                pointerButtonMap.put(pointerId, it)
                if (it.id != binding.keySpace.id &&
                    it.id != binding.keyDelete.id &&
                    it.id != binding.keyShift.id &&
                    it.id != binding.key123.id &&
                    it.id != binding.keyReturn.id
                ) {
                    showKeyPreview(it)
                }
            } ?: run {
                // Finger moved off any key entirely
                pointerButtonMap.remove(pointerId)
            }
        }
    }

    /**
     * Clear pressed state for all tracked keys, dismiss the preview, and reset suppression.
     */
    private fun clearAllPressed() {
        for (i in 0 until pointerButtonMap.size) {
            pointerButtonMap.valueAt(i)?.isPressed = false
        }
        pointerButtonMap.clear()
        dismissKeyPreview()
        suppressedPointerId = null
    }

    /**
     * Show a PopupWindow “preview” above the given key-View.
     */
    private fun showKeyPreview(view: View) {
        // 1) Dismiss any existing preview
        dismissKeyPreview()

        // 2) Choose layout based on edge keys (example: leftmost and rightmost)
        val layoutRes = when (view.id) {
            binding.keyQ.id -> R.layout.key_preview_left
            binding.keyP.id -> R.layout.key_preview_right
            else -> R.layout.key_preview
        }

        // 3) Inflate the chosen layout
        val popupView = LayoutInflater.from(context).inflate(layoutRes, this, false)
        val tv = popupView.findViewById<TextView>(R.id.preview_text)

        // 4) Copy the key’s label into the preview
        when (view) {
            is QWERTYButton -> tv.text = view.text
            is AppCompatButton -> tv.text = view.text
            is AppCompatImageButton -> {
                // If preview should show an icon, adjust your layout accordingly.
                tv.text = ""
            }

            else -> tv.text = ""
        }

        // 5) Create a PopupWindow (non-focusable, non-touchable)
        val popup = PopupWindow(
            popupView,
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isTouchable = false
            isFocusable = false
            elevation = 6f
        }

        // 6) Measure the content
        popupView.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val previewWidth = popupView.measuredWidth
        val previewHeight = popupView.measuredHeight

        // 7) Calculate horizontal offset: center above the key
        val viewWidth = view.width
        val xOffset = (viewWidth / 2) - (previewWidth / 2)

        // 8) Calculate vertical offset: place above (negative Y)
        val yOffset = -(previewHeight - 24)

        // 9) Show the popup
        popup.showAsDropDown(view, xOffset, yOffset)
        keyPreviewPopup = popup
    }

    /** Dismiss any visible key preview. */
    private fun dismissKeyPreview() {
        keyPreviewPopup?.dismiss()
        keyPreviewPopup = null
    }

    /**
     * Hit-test all direct children that are registered as keys. Return the child View if its bounds contain (x,y).
     */
    private fun findButtonUnder(x: Int, y: Int): View? {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.isVisible) {
                child.getHitRect(hitRect)
                if (hitRect.contains(x, y) && qwertyButtonMap.containsKey(child)) {
                    return child
                }
            }
        }
        return null
    }

    /**
     * If the key supports variations, log details for debugging.
     */
    private fun logVariationIfNeeded(key: QWERTYKey) {
        val info: QWERTYKeyInfo = qwertyKeyMap.getKeyInfoDefault(key)
        if (info is QWERTYKeyInfo.QWERTYVariation) {
            val tap = info.tap
            val cap = info.capChar
            val variations = info.variations
            val capVariations = info.capVariations
            Log.d(
                "KEY_VARIATION",
                "KEY: $key, tap: $tap, cap: $cap, " +
                        "variations: $variations, capVariations: $capVariations"
            )
        }
    }

    /**
     * Called when the Shift key is double-tapped.
     */
    private fun onShiftDoubleTapped() {
        // TODO: Do whatever you want on a Shift double-tap.
        // For example, toggle a caps-lock state or notify your listener:
        Log.d("QWERTYKEY", "Shift was double-tapped!")

        // If your listener interface has a method for double-tap, you could invoke it here:
        // qwertyKeyListener?.onShiftDoubleTap()
    }
}
