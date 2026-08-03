package com.kazumaproject.custom_keyboard.controller

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver

/**
 * Draws keyboard popups in the same window and draw traversal as their key anchor.
 *
 * PopupWindow creates a child window whose relayout is committed independently from the IME
 * window. That can expose an intermediate frame when the IME height changes. ViewGroupOverlay
 * keeps popup drawing in the anchor's existing window, and this pre-draw callback places every
 * visible popup after the current traversal has laid out the keyboard.
 */
internal class KeyboardPopupOverlay(
    private val positionVisiblePopups: () -> Unit
) {
    private val visibleViews = LinkedHashSet<View>()
    private var host: ViewGroup? = null
    private var observedViewTree: ViewTreeObserver? = null

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (visibleViews.isEmpty()) {
            stopObserving()
        } else {
            positionVisiblePopups()
        }
        true
    }

    val currentHost: ViewGroup?
        get() = host

    fun show(
        anchor: View,
        preferredHost: View?,
        popupView: View,
        width: Int,
        height: Int
    ): Boolean {
        if (!anchor.isAttachedToWindow) return false
        val resolvedHost = resolveHost(anchor, preferredHost) ?: return false
        if (!resolvedHost.isAttachedToWindow) return false

        if (host !== resolvedHost) {
            removeAllViews()
            host = resolvedHost
        }

        measureExactly(popupView, width, height)
        if (visibleViews.add(popupView)) {
            resolvedHost.overlay.add(popupView)
        }
        startObserving(resolvedHost)

        // Place before the first invalidation as well as immediately before each later draw.
        positionVisiblePopups()
        resolvedHost.invalidate()
        return true
    }

    fun isShowing(popupView: View): Boolean = popupView in visibleViews

    fun place(
        popupView: View,
        left: Int,
        top: Int,
        width: Int = popupView.measuredWidth,
        height: Int = popupView.measuredHeight
    ) {
        if (popupView !in visibleViews) return
        val resolvedWidth = width.coerceAtLeast(1)
        val resolvedHeight = height.coerceAtLeast(1)
        if (
            popupView.measuredWidth != resolvedWidth ||
            popupView.measuredHeight != resolvedHeight
        ) {
            measureExactly(popupView, resolvedWidth, resolvedHeight)
        }
        popupView.layout(
            left,
            top,
            left + resolvedWidth,
            top + resolvedHeight
        )
    }

    fun dismiss(popupView: View) {
        if (!visibleViews.remove(popupView)) return
        host?.overlay?.remove(popupView)
        if (visibleViews.isEmpty()) {
            stopObserving()
            host = null
        }
    }

    fun dismissAll() {
        removeAllViews()
        host = null
    }

    private fun resolveHost(anchor: View, preferredHost: View?): ViewGroup? {
        val anchorRoot = anchor.rootView
        val preferredGroup = preferredHost as? ViewGroup
        if (preferredGroup != null && preferredGroup.rootView === anchorRoot) {
            return preferredGroup
        }
        return anchorRoot as? ViewGroup
            ?: generateSequence(anchor.parent) { parent ->
                (parent as? View)?.parent
            }.filterIsInstance<ViewGroup>().lastOrNull()
    }

    private fun startObserving(resolvedHost: ViewGroup) {
        val observer = resolvedHost.viewTreeObserver
        if (observedViewTree === observer && observer.isAlive) return

        stopObserving()
        if (!observer.isAlive) return
        observedViewTree = observer
        observer.addOnPreDrawListener(preDrawListener)
    }

    private fun stopObserving() {
        observedViewTree?.let { observer ->
            if (observer.isAlive) {
                observer.removeOnPreDrawListener(preDrawListener)
            }
        }
        observedViewTree = null
    }

    private fun removeAllViews() {
        val currentHost = host
        if (currentHost != null) {
            visibleViews.forEach(currentHost.overlay::remove)
        }
        visibleViews.clear()
        stopObserving()
    }

    private fun measureExactly(view: View, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width.coerceAtLeast(1), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height.coerceAtLeast(1), View.MeasureSpec.EXACTLY)
        )
    }
}

internal fun getLocationRelativeToOverlayHost(
    keyAnchor: View,
    overlayHost: View,
    outLocation: IntArray,
    keyLocationScratch: IntArray,
    hostLocationScratch: IntArray
) {
    require(outLocation.size >= 2)
    require(keyLocationScratch.size >= 2)
    require(hostLocationScratch.size >= 2)

    keyAnchor.getLocationOnScreen(keyLocationScratch)
    overlayHost.getLocationOnScreen(hostLocationScratch)
    outLocation[0] = keyLocationScratch[0] - hostLocationScratch[0]
    outLocation[1] = keyLocationScratch[1] - hostLocationScratch[1]
}
