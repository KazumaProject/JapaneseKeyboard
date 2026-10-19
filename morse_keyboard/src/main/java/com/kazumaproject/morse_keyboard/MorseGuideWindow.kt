package com.kazumaproject.morse_keyboard

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.PopupWindow

/** Attached to the IME window, with no overlay permission and no editor focus changes. */
internal class MorseGuideWindow(private val owner: View, private val anchorProvider: () -> View, private val avoidProvider: () -> View?, val panel: MorseGuidePanel, private val inputTopProvider: () -> Int, private val refresh: () -> Unit) {
    private val viewport = android.widget.ScrollView(owner.context).apply {
        isFillViewport = false
        addView(panel)
    }
    private val popup = PopupWindow(viewport, 1, 1, false).apply {
        inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        isOutsideTouchable = false
        windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
        elevation = 8 * owner.resources.displayMetrics.density
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= 29) { setIsLaidOutInScreen(true); setIsClippedToScreen(true) }
    }
    private var previous = Rect()
    private var observedRoot: View? = null
    private val layoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener { refresh() }
    fun show() {
        val anchor = anchorProvider()
        if (!owner.isShown || owner.windowVisibility != View.VISIBLE || !owner.isAttachedToWindow || !anchor.isAttachedToWindow) return
        val density = owner.resources.displayMetrics.density
        val frame = Rect().also(anchor::getWindowVisibleDisplayFrame)
        if (frame.width() <= 0) return
        val margin = (8 * density).toInt()
        val width = minOf((360 * density).toInt(), frame.width() - margin * 2).coerceAtLeast(1)
        val location = IntArray(2).also(owner::getLocationOnScreen)
        val candidateTop = avoidProvider()?.takeIf { it.isShown && it.height > 0 }
            ?.let { IntArray(2).also(it::getLocationOnScreen)[1] } ?: location[1]
        val preferredTop = minOf(location[1], candidateTop)
        // In landscape, the map/candidate strip can provide space while the paddles stay usable.
        val top = if (preferredTop - frame.top < 180 * density) inputTopProvider() else preferredTop
        val above = (top - frame.top - margin).coerceAtLeast(0)
        val below = (frame.bottom - location[1] - owner.height - margin).coerceAtLeast(0)
        val available = maxOf(above, below)
        if (available <= 0) { dismiss(); return }
        viewport.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(available, View.MeasureSpec.AT_MOST))
        val height = viewport.measuredHeight.coerceAtLeast(1)
        val x = (location[0] + (owner.width - width) / 2).coerceIn(frame.left + margin, maxOf(frame.left + margin, frame.right - width - margin))
        val y = if (above >= below) top - height - margin else location[1] + owner.height + margin
        val bounds = Rect(x, y, x + width, y + height)
        if (popup.isShowing && bounds == previous) return
        previous = bounds
        try {
            if (popup.isShowing) popup.update(x, y, width, height)
            else {
                viewport.scrollTo(0, 0)
                popup.width = width; popup.height = height
                // API 24–28 coordinates are relative to the parent window's origin.
                val screen = IntArray(2).also(anchor::getLocationOnScreen)
                val window = IntArray(2).also(anchor::getLocationInWindow)
                popup.showAtLocation(anchor, Gravity.TOP or Gravity.LEFT,
                    if (Build.VERSION.SDK_INT >= 29) x else x - screen[0] + window[0],
                    if (Build.VERSION.SDK_INT >= 29) y else y - screen[1] + window[1])
                observedRoot = anchor.rootView.also { it.viewTreeObserver.addOnGlobalLayoutListener(layoutListener) }
            }
        } catch (_: WindowManager.BadTokenException) { dismiss() }
          catch (_: IllegalStateException) { dismiss() }
    }
    fun revealProgress() {
        viewport.post { if (panel.height > viewport.height) viewport.smoothScrollTo(0, panel.height) }
    }
    fun dismiss() {
        observedRoot?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
        observedRoot = null
        popup.dismiss(); previous = Rect()
    }
}
