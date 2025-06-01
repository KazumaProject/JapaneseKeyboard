package com.kazumaproject.qwerty_keyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.PopupWindow
import android.widget.TextView
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

class QWERTYKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: QwertyLayoutBinding

    /** Map each active pointer ID → the QWERTYButton it’s currently “pressing” (or null). */
    private val pointerButtonMap = SparseArray<QWERTYButton?>()

    /** If a second finger cancels the first, we suppress that first pointer until it actually lifts. */
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

    // ─── Map each Button (any View) to a QWERTYKey ────────────────────────────────
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
        binding.keyShift to QWERTYKey.QWERTYKeyShift,         // e.g. AppCompatImageButton
        binding.keyDelete to QWERTYKey.QWERTYKeyDelete,       // e.g. AppCompatImageButton
        binding.key123 to QWERTYKey.QWERTYKeySwitchMode,      // e.g. AppCompatButton
        binding.keySpace to QWERTYKey.QWERTYKeySpace,         // e.g. AppCompatButton
        binding.keyReturn to QWERTYKey.QWERTYKeyReturn        // e.g. AppCompatButton
    )

    fun setOnQWERTYKeyListener(qwertyKeyListener: QWERTYKeyListener) {
        this.qwertyKeyListener = qwertyKeyListener
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return event.actionMasked == MotionEvent.ACTION_DOWN
    }

    // ─── A single PopupWindow used to show the “key preview” above whichever QWERTYButton is pressed ───────────────
    private var keyPreviewPopup: PopupWindow? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount > 1 || pointerButtonMap.isNotEmpty()) {
                    clearAllPressed()
                }
                // If a previous pointer was suppressed but never lifted, clear that:
                suppressedPointerId = null
                handlePointerDown(event, pointerIndex = 0)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // If exactly one pointer was being tracked so far, “cancel” it now:
                if (pointerButtonMap.size() == 1) {
                    val firstPointerId = pointerButtonMap.keyAt(0)
                    val firstBtn = pointerButtonMap.valueAt(0)
                    firstBtn?.let { btn ->
                        // Visually un‐press
                        btn.isPressed = false

                        // Fire “key‐up” callback on the first finger
                        val qwertyKey = qwertyButtonMap[btn] ?: QWERTYKey.QWERTYKeyNotSelect
                        val info: QWERTYKeyInfo = qwertyKeyMap.getKeyInfoDefault(qwertyKey)

                        // (Optional) log the variation data if you like:
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

                        // Dismiss any preview showing on that first key
                        dismissKeyPreview()
                    }
                    // Mark that first pointer as “suppressed” until it actually lifts:
                    suppressedPointerId = firstPointerId
                    pointerButtonMap.remove(firstPointerId)
                }
                // Now track the new (second) finger as the only pressed one:
                val newIndex = event.actionIndex
                handlePointerDown(event, pointerIndex = newIndex)
            }

            MotionEvent.ACTION_MOVE -> {
                // We only track one pointer at a time (after we clear on multi‐touch),
                // but MOVE will be called for each finger. Skip any “suppressed” one:
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    if (pid == suppressedPointerId) {
                        continue
                    }
                    handlePointerMove(event, pointerIndex = i, pointerId = pid)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // One finger lifted, but another might still be down:
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)

                // If that was our suppressed pointer, let it go now:
                if (suppressedPointerId == pointerId) {
                    suppressedPointerId = null
                }

                // If that lifted pointer was actually in pointerButtonMap, fire its “UP”
                val btn = pointerButtonMap[pointerId]
                btn?.let {
                    it.isPressed = false
                    dismissKeyPreview()

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
                // Don’t call clearAllPressed(): another (non‐suppressed) finger may still be down.
            }

            MotionEvent.ACTION_UP -> {
                // The final finger lifted. If it was suppressed, clear suppression; otherwise handle it normally:
                val liftedId = event.getPointerId(event.actionIndex)
                if (suppressedPointerId == liftedId) {
                    suppressedPointerId = null
                }

                // If there is exactly one tracked button, fire its “UP”:
                if (pointerButtonMap.size() == 1) {
                    val btn = pointerButtonMap.valueAt(0)
                    btn?.let {
                        it.isPressed = false
                        dismissKeyPreview()

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
                // Finally, clear everything so a lingering first finger can’t re‐activate:
                clearAllPressed()
            }

            MotionEvent.ACTION_CANCEL -> {
                clearAllPressed()
            }
        }
        return true
    }

    /**
     * Whenever a new pointer goes down (and is not suppressed), press that key and show a preview.
     */
    private fun handlePointerDown(event: MotionEvent, pointerIndex: Int) {
        val pid = event.getPointerId(pointerIndex)
        if (pid == suppressedPointerId) return

        val x = event.getX(pointerIndex).toInt()
        val y = event.getY(pointerIndex).toInt()
        val btn = findButtonUnder(x, y)
        btn?.let {
            it.isPressed = true
            pointerButtonMap.put(pid, it)
            if (it.id != binding.keySpace.id) {
                showKeyPreview(it)
            }
        }
    }

    /**
     * Whenever that single (tracked) pointer moves, move the pressed state if necessary.
     * If the finger has slid off the original button, remove preview from the old key and
     * show a preview on the new key.
     */
    private fun handlePointerMove(event: MotionEvent, pointerIndex: Int, pointerId: Int) {
        if (pointerId == suppressedPointerId) return

        val x = event.getX(pointerIndex).toInt()
        val y = event.getY(pointerIndex).toInt()
        val previousBtn = pointerButtonMap[pointerId]
        val currentBtn = findButtonUnder(x, y)

        if (currentBtn != previousBtn) {
            // Un‐press the old one
            previousBtn?.isPressed = false

            // Dismiss its preview
            dismissKeyPreview()

            // Press the new one and show preview
            currentBtn?.let {
                it.isPressed = true
                pointerButtonMap.put(pointerId, it)
                if (it.id != binding.keySpace.id) {
                    showKeyPreview(it)
                }
            } ?: run {
                // If finger moved off any key entirely, just clear out of the map
                pointerButtonMap.remove(pointerId)
            }
        }
    }

    /** Un‐press any tracked button (and dismiss any preview), then clear everything. */
    private fun clearAllPressed() {
        for (i in 0 until pointerButtonMap.size) {
            pointerButtonMap.valueAt(i)?.isPressed = false
        }
        pointerButtonMap.clear()
        dismissKeyPreview()
        suppressedPointerId = null
    }

    /**
     * Show a PopupWindow “preview” above the given QWERTYButton.  If one is already visible,
     * first dismiss it, then create a new PopupWindow anchored to `btn`.
     */
    private fun showKeyPreview(btn: QWERTYButton) {
        // 1) Dismiss any existing preview
        dismissKeyPreview()

        // 2) レイアウトを切り替える
        val layoutRes = if (btn.id == binding.keyQ.id) {
            R.layout.key_preview_left
        } else if (btn.id == binding.keyP.id) {
            R.layout.key_preview_right
        } else {
            R.layout.key_preview
        }

        // 3) Inflate the chosen layout
        val popupView = LayoutInflater.from(context).inflate(
            layoutRes,
            this,  // parent for inflation (not attached)
            false
        )

        // 4) Copy the button’s text into the preview’s TextView
        val tv = popupView.findViewById<TextView>(R.id.preview_text)
        tv.text = btn.text

        // 5) Create a PopupWindow; make it non‐focusable so it doesn’t steal input
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

        // 6) Measure the content so we know its width/height
        popupView.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val previewWidth = popupView.measuredWidth
        val previewHeight = popupView.measuredHeight

        // 7) Calculate horizontal offset: center the preview above the button
        val btnWidth = btn.width
        val xOffset = (btnWidth / 2) - (previewWidth / 2)

        // 8) Calculate vertical offset so that popup appears *above* the button.
        //    showAsDropDown(anchor, xOff, yOff) normally places it below anchor (yOff ≥ 0).
        //    To move it above, use a negative yOff equal to (anchor’s height + preview’s height).
        val yOffset = -(previewHeight - 24)

        // 9) Finally show it
        popup.showAsDropDown(btn, xOffset, yOffset)

        keyPreviewPopup = popup
    }

    /** Dismiss the popup if it’s still showing. */
    private fun dismissKeyPreview() {
        keyPreviewPopup?.dismiss()
        keyPreviewPopup = null
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
