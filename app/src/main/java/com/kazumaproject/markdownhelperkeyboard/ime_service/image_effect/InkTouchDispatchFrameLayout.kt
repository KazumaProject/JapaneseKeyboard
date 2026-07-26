package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import timber.log.Timber

class InkTouchDispatchFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var keyboardTouchEffectMotionEventListener: ((MotionEvent) -> Unit)? = null
    private var fallbackTouchTarget: View? = null
    private val rootLocationOnScreen = IntArray(2)
    private val targetLocationOnScreen = IntArray(2)
    private var fallbackTargetScreenX = 0
    private var fallbackTargetScreenY = 0
    private var lastStableTarget: View? = null
    private var lastStableTargetScreenX = 0
    private var lastStableTargetScreenY = 0
    private var lastStableTargetWidth = 0
    private var lastStableTargetHeight = 0

    /**
     * Returns the keyboard view that may receive a touch whose normal child hit test failed.
     *
     * The IME window is resized when the first conversion candidates appear. During that
     * relayout, ViewGroup's local child bounds can briefly represent a different layout pass
     * from the root event coordinates. The touch still reaches this root, but normal dispatch
     * can reject it or let a transiently overlapping sibling consume it before it reaches the
     * keyboard. A DOWN inside this target is therefore routed in screen coordinates whenever
     * the event and the root belong to different layout generations.
     */
    var fallbackTouchTargetProvider: (() -> View?)? = null

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

        fallbackTouchTarget?.let { target ->
            return dispatchToFallbackTarget(target, ev)
        }

        val target = fallbackTouchTargetProvider?.invoke()
            ?.takeIf { it.isAttachedToWindow && it.isShown && it.width > 0 && it.height > 0 }
        target?.getLocationOnScreen(targetLocationOnScreen)
        val currentTargetScreenX = targetLocationOnScreen[0]
        val currentTargetScreenY = targetLocationOnScreen[1]
        val screenX = ev.rawX
        val screenY = ev.rawY
        getLocationOnScreen(rootLocationOnScreen)
        val eventRootScreenX = ev.rawX - ev.x
        val eventRootScreenY = ev.rawY - ev.y
        val isRootCoordinateGenerationStale =
            kotlin.math.abs(eventRootScreenX - rootLocationOnScreen[0]) > 0.5f ||
                kotlin.math.abs(eventRootScreenY - rootLocationOnScreen[1]) > 0.5f
        val isInsideCurrentTarget = target != null &&
            isInside(
                x = screenX,
                y = screenY,
                left = currentTargetScreenX,
                top = currentTargetScreenY,
                width = target.width,
                height = target.height
            )
        val isInsideStableTarget = target != null &&
            lastStableTarget === target &&
            isInside(
                x = screenX,
                y = screenY,
                left = lastStableTargetScreenX,
                top = lastStableTargetScreenY,
                width = lastStableTargetWidth,
                height = lastStableTargetHeight
            )

        /*
         * During an IME relayout, MotionEvent's local coordinates can still use the previous root
         * origin. Do not run ViewGroup child hit testing across those two layout generations:
         * a transiently overlapping candidate/toolbar child can consume the DOWN before the
         * keyboard sees it. Screen coordinates and the target's screen bounds remain consistent.
         */
        if (ev.actionMasked == MotionEvent.ACTION_DOWN &&
            target != null &&
            isRootCoordinateGenerationStale &&
            (isInsideCurrentTarget || isInsideStableTarget)
        ) {
            fallbackTouchTarget = target
            fallbackTargetScreenX =
                if (isInsideCurrentTarget) currentTargetScreenX else lastStableTargetScreenX
            fallbackTargetScreenY =
                if (isInsideCurrentTarget) currentTargetScreenY else lastStableTargetScreenY
            return dispatchToFallbackTarget(target, ev)
        }

        val handledNormally = super.dispatchTouchEvent(ev)
        if (handledNormally) {
            if (target != null && isInsideCurrentTarget) {
                rememberStableTarget(
                    target = target,
                    screenX = currentTargetScreenX,
                    screenY = currentTargetScreenY
                )
            }
            return true
        }
        if (ev.actionMasked != MotionEvent.ACTION_DOWN || target == null) {
            return false
        }

        if (!isInsideStableTarget && !isInsideCurrentTarget) {
            return false
        }

        fallbackTouchTarget = target
        fallbackTargetScreenX =
            if (isInsideStableTarget) lastStableTargetScreenX else currentTargetScreenX
        fallbackTargetScreenY =
            if (isInsideStableTarget) lastStableTargetScreenY else currentTargetScreenY
        return dispatchToFallbackTarget(target, ev)
    }

    private fun dispatchToFallbackTarget(target: View, source: MotionEvent): Boolean {
        if (!target.isAttachedToWindow) {
            clearFallbackTarget()
            return true
        }

        val transformed = MotionEvent.obtain(source)
        transformed.offsetLocation(
            source.rawX - source.x - fallbackTargetScreenX,
            source.rawY - source.y - fallbackTargetScreenY
        )
        val handled = try {
            target.dispatchTouchEvent(transformed)
        } finally {
            transformed.recycle()
        }

        if ((!handled && source.actionMasked == MotionEvent.ACTION_DOWN) ||
            source.actionMasked == MotionEvent.ACTION_UP ||
            source.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            clearFallbackTarget()
        }
        return handled
    }

    private fun rememberStableTarget(target: View, screenX: Int, screenY: Int) {
        lastStableTarget = target
        lastStableTargetScreenX = screenX
        lastStableTargetScreenY = screenY
        lastStableTargetWidth = target.width
        lastStableTargetHeight = target.height
    }

    private fun isInside(
        x: Float,
        y: Float,
        left: Int,
        top: Int,
        width: Int,
        height: Int
    ): Boolean =
        x >= left &&
            x < left + width &&
            y >= top &&
            y < top + height

    private fun clearFallbackTarget() {
        fallbackTouchTarget = null
        fallbackTargetScreenX = 0
        fallbackTargetScreenY = 0
    }
}
