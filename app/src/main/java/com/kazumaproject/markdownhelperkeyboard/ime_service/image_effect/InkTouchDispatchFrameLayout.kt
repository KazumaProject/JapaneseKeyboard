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

    var inkMotionEventListener: ((MotionEvent) -> Unit)? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        runCatching {
            inkMotionEventListener?.invoke(ev)
        }.onFailure {
            Timber.w(it, "Suminagashi ink touch forwarding failed.")
        }
        return super.dispatchTouchEvent(ev)
    }
}
