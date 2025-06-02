package com.kazumaproject.qwerty_keyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
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
import com.kazumaproject.core.data.qwerty.CapsLockState
import com.kazumaproject.core.data.qwerty.VariationInfo
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

    private val _qwertyMode = MutableStateFlow<QWERTYMode>(QWERTYMode.Default)
    private val qwertyMode: StateFlow<QWERTYMode> = _qwertyMode.asStateFlow()

    init {
        isClickable = true
        isFocusable = true

        val inflater = LayoutInflater.from(context)
        binding = QwertyLayoutBinding.inflate(inflater, this)

        qwertyKeyMap = QWERTYKeyMap()

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
                    when (state) {
                        QWERTYMode.Default -> {
                            binding.apply {
                                keyAtMark.isVisible = false
                                keyV.isVisible = true
                                keyB.isVisible = true
                                keyA.setMarginStart(
                                    23f
                                )
                                keyL.setMarginEnd(
                                    23f
                                )
                            }
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
                            }
                        }
                    }
                }
            }
        }
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

            // Side and function keys
            binding.keyShift to QWERTYKey.QWERTYKeyShift,      // AppCompatImageButton
            binding.keyDelete to QWERTYKey.QWERTYKeyDelete,    // AppCompatImageButton
            binding.keySwitchDefault to QWERTYKey.QWERTYKeySwitchDefaultLayout,
            binding.key123 to QWERTYKey.QWERTYKeySwitchMode,   // AppCompatButton
            binding.keySpace to QWERTYKey.QWERTYKeySpace,      // QWERTYButton
            binding.keyReturn to QWERTYKey.QWERTYKeyReturn     // AppCompatButton
        )
    }

    private val qwertyButtons: List<QWERTYButton> by lazy {
        listOf(
            binding.keyA,
            binding.keyB,
            binding.keyC,
            binding.keyD,
            binding.keyE,
            binding.keyF,
            binding.keyG,
            binding.keyH,
            binding.keyI,
            binding.keyJ,
            binding.keyK,
            binding.keyL,
            binding.keyM,
            binding.keyN,
            binding.keyO,
            binding.keyP,
            binding.keyQ,
            binding.keyR,
            binding.keyS,
            binding.keyT,
            binding.keyU,
            binding.keyV,
            binding.keyW,
            binding.keyX,
            binding.keyY,
            binding.keyZ
        )
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
        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                // If multi‐touch or leftover map entries exist, clear everything
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

                // If that pointer was tracked, fire its “key up” or cancel long‐press
                val view = pointerButtonMap[pointerId]
                view?.let {
                    it.isPressed = false
                    dismissKeyPreview()
                    cancelLongPressForPointer(pointerId)

                    val wasShift = (it.id == binding.keyShift.id)
                    // ③ If Shift was double‐tapped, suppress this single‐tap event
                    if (wasShift && shiftDoubleTapped) {
                        // Consume without notifying
                        shiftDoubleTapped = false
                    } else {
                        val qwertyKey = qwertyButtonMap[it] ?: QWERTYKey.QWERTYKeyNotSelect
                        logVariationIfNeeded(qwertyKey)
                        setToggleShiftState(view)
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
                        Log.d("QWERTYKEY", "ACTION_UP: ${capsLockState.value}")
                        it.isPressed = false
                        dismissKeyPreview()
                        cancelLongPressForPointer(liftedId)

                        val wasShift = (it.id == binding.keyShift.id)
                        // ④ If Shift was double‐tapped, suppress this single‐tap event
                        if (wasShift && shiftDoubleTapped) {
                            shiftDoubleTapped = false
                        } else {
                            val qwertyKey = qwertyButtonMap[it] ?: QWERTYKey.QWERTYKeyNotSelect
                            logVariationIfNeeded(qwertyKey)
                            setToggleShiftState(view)
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
        } else if (view.id == binding.keyDelete.id ||
            view.id == binding.keySpace.id
        ) {
            /** empty body **/
        } else {
            disableShift()
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
            if (it.id != binding.keySpace.id && it.id != binding.keyDelete.id && it.id != binding.keyShift.id && it.id != binding.key123.id && it.id != binding.keyReturn.id && it.id != binding.keySwitchDefault.id) {
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
    }

    /**
     * Show a PopupWindow “preview” above the given key‐View.
     */
    private fun showKeyPreview(view: View) {
        dismissKeyPreview()

        val layoutRes = when (view.id) {
            binding.keyQ.id -> R.layout.key_preview_left
            binding.keyP.id -> R.layout.key_preview_right
            else -> R.layout.key_preview
        }

        val popupView = LayoutInflater.from(context).inflate(layoutRes, this, false)
        val tv = popupView.findViewById<TextView>(R.id.preview_text)

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
            popupView, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, false
        ).apply {
            isTouchable = false
            isFocusable = false
            elevation = 6f
        }

        popupView.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val previewWidth = popupView.measuredWidth
        val previewHeight = popupView.measuredHeight

        val viewWidth = view.width
        val xOffset =
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && binding.keyQ.id == view.id) {
                (viewWidth / 2) - (previewWidth / 2) + 16
            } else {
                (viewWidth / 2) - (previewWidth / 2)
            }
        val yOffset = -(previewHeight - 24)

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
                QWERTYMode.Default -> _qwertyMode.update { QWERTYMode.Number }
                QWERTYMode.Number, QWERTYMode.Symbol -> _qwertyMode.update { QWERTYMode.Default }
            }
            Log.d(
                "KEY_VARIATION",
                "KEY: $key, ${qwertyMode.value}"
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
        val info: QWERTYKeyInfo = qwertyKeyMap.getKeyInfoDefault(key)
        return if (info is QWERTYKeyInfo.QWERTYVariation) {
            VariationInfo(
                tap = info.tap,
                cap = info.capChar,
                variations = info.variations,
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
        // For example, toggle a caps‐lock state or notify listener:
        qwertyKeyListener?.onLongPressQWERTYKey(QWERTYKey.QWERTYKeyShift)
        enableCapsLock()
    }

    // ─────────────────────────────────────────────
    // Long‐press scheduling / cancellation (with coroutines)
    // ─────────────────────────────────────────────

    /**
     * Schedule a “long‐press” Job for the given pointer + view.
     */
    private fun scheduleLongPressForPointer(pointerId: Int, view: View) {
        // Cancel any existing Job for this pointer
        cancelLongPressForPointer(pointerId)

        // Launch a coroutine that waits longPressTimeout ms
        val job = scope.launch {
            delay(longPressTimeout)
            // After the delay, check that this pointer still “owns” the same view
            val currentView = pointerButtonMap[pointerId]
            if (currentView == view) {
                // Long‐press confirmed: just log
                val qwertyKey = qwertyButtonMap[view] ?: QWERTYKey.QWERTYKeyNotSelect
                qwertyKeyListener?.onLongPressQWERTYKey(qwertyKey)
                Log.d("QWERTYKEY", "Long‐press detected on $qwertyKey")
                val info = getVariationInfo(qwertyKey)
                info?.apply {
                    Log.d("QWERTYKEY", "Long‐press $variations")
                }
                // Note: we do NOT remove the pointer here, so ACTION_UP still triggers normal tap
            }
        }

        longPressJobs.put(pointerId, job)
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

}
