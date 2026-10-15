package com.kazumaproject.core.ui.skin

import android.view.View
import android.view.ViewGroup
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Drives cells drawn directly on Canvas as well as normal popup backgrounds. */
internal object FeverPopupMotion {
    private val loops = WeakHashMap<View, FrameLoop>()
    private class FrameLoop(view: View) : Runnable, View.OnAttachStateChangeListener {
        private val reference = WeakReference(view)
        override fun onViewAttachedToWindow(view: View) { view.postOnAnimation(this) }
        override fun onViewDetachedFromWindow(view: View) { clear(view) }
        override fun run() {
            val target = reference.get() ?: return
            if (!target.isAttachedToWindow) return
            fun redraw(view: View) {
                view.invalidate()
                if (view is ViewGroup) for (index in 0 until view.childCount) redraw(view.getChildAt(index))
            }
            // Invalidate child display lists too; parent invalidation alone can replay frozen cells.
            redraw(target)
            target.postOnAnimation(this)
        }
    }
    fun show(view: View) {
        view.animate().cancel(); view.alpha = 1f; view.scaleX = 1f; view.scaleY = 1f
        clear(view)
        val loop = FrameLoop(view)
        loops[view] = loop
        view.addOnAttachStateChangeListener(loop)
        // A reused guide is configured before it is attached to its overlay/window.
        if (view.isAttachedToWindow) view.postOnAnimation(loop)
    }
    fun clear(view: View) {
        loops.remove(view)?.let { loop ->
            view.removeCallbacks(loop)
            view.removeOnAttachStateChangeListener(loop)
        }
    }
}
