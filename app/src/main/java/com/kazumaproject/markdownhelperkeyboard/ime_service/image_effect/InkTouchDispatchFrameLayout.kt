package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import timber.log.Timber

class InkTouchDispatchFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var keyboardTouchEffectMotionEventListener: ((MotionEvent) -> Unit)? = null

    var touchEffectMotionEventListener: ((MotionEvent) -> Unit)?
        get() = keyboardTouchEffectMotionEventListener
        set(value) {
            keyboardTouchEffectMotionEventListener = value
        }

    var inkMotionEventListener: ((MotionEvent) -> Unit)?
        get() = keyboardTouchEffectMotionEventListener
        set(value) {
            keyboardTouchEffectMotionEventListener = value
        }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        runCatching {
            keyboardTouchEffectMotionEventListener?.invoke(ev)
        }.onFailure {
            Timber.w(it, "Keyboard touch effect forwarding failed.")
        }
        return super.dispatchTouchEvent(ev)
    }
}
