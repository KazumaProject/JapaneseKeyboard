package com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry

import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Matrix
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

/** Maps a keyboard-local rectangle through floating-panel scaling into its host. */
internal fun geometryBoundsInHost(body: View, host: View, bounds: RectF): Rect? {
    val bodyToScreen = Matrix()
    val hostToScreen = Matrix()
    body.transformMatrixToGlobal(bodyToScreen)
    host.transformMatrixToGlobal(hostToScreen)
    val screenToHost = Matrix()
    if (!hostToScreen.invert(screenToHost)) return null
    val mapped = RectF(bounds)
    bodyToScreen.mapRect(mapped)
    screenToHost.mapRect(mapped)
    return Rect(mapped.left.roundToInt(), mapped.top.roundToInt(), mapped.right.roundToInt(), mapped.bottom.roundToInt())
}

/** Moves the existing candidate list into the keyboard's reserved space and restores it exactly. */
internal class GeometryCandidateHost(
    private val root: FrameLayout,
    private val recycler: RecyclerView,
    private val onRestore: () -> Unit = {},
) {
    private var parent: ViewGroup? = null
    private var index = -1
    private var originalParams: ViewGroup.LayoutParams? = null
    private var originalManager: RecyclerView.LayoutManager? = null
    private var originalClipToPadding = false
    private var originalPadding: Rect? = null
    private var panel: FrameLayout? = null
    val attached: Boolean get() = panel != null

    fun show(bounds: Rect) {
        if (bounds.width() <= 0 || bounds.height() <= 0) { detach(); return }
        if (!attached) {
            val originalParent = recycler.parent as? ViewGroup ?: return
            parent = originalParent
            index = originalParent.indexOfChild(recycler)
            originalParams = recycler.layoutParams
            originalManager = recycler.layoutManager
            originalClipToPadding = recycler.clipToPadding
            originalPadding = Rect(recycler.paddingLeft, recycler.paddingTop, recycler.paddingRight, recycler.paddingBottom)
            originalParent.removeView(recycler)
            val container = FrameLayout(root.context)
            container.addView(recycler, FrameLayout.LayoutParams(-1, -1))
            recycler.layoutManager = LinearLayoutManager(root.context, RecyclerView.VERTICAL, false)
            recycler.clipToPadding = true
            recycler.setPadding(0, 0, 0, 0)
            root.addView(container)
            panel = container
        }
        val container = panel ?: return
        val params = (container.layoutParams as? FrameLayout.LayoutParams) ?: FrameLayout.LayoutParams(bounds.width(), bounds.height())
        if (params.width != bounds.width() || params.height != bounds.height() || params.leftMargin != bounds.left || params.topMargin != bounds.top || params.gravity != (Gravity.TOP or Gravity.LEFT)) {
            params.width = bounds.width(); params.height = bounds.height()
            params.leftMargin = bounds.left; params.topMargin = bounds.top
            params.gravity = Gravity.TOP or Gravity.LEFT
            container.layoutParams = params
        }
        container.visibility = View.VISIBLE
    }

    fun containsScreenPoint(x: Float, y: Float): Boolean {
        val view = panel ?: return false
        if (!view.isShown) return false
        val location = IntArray(2).also(view::getLocationOnScreen)
        val localToGlobal = Matrix().also(view::transformMatrixToGlobal)
        val globalOrigin = floatArrayOf(0f, 0f).also(localToGlobal::mapPoints)
        val globalToLocal = Matrix()
        if (!localToGlobal.invert(globalToLocal)) return false
        // getLocationOnScreen includes the window offset, whereas the matrix is in
        // global view coordinates. Translate the event into that coordinate space first.
        val point = floatArrayOf(x - location[0] + globalOrigin[0], y - location[1] + globalOrigin[1])
        globalToLocal.mapPoints(point)
        return point[0] >= 0f && point[0] < view.width && point[1] >= 0f && point[1] < view.height
    }

    fun detach(): Boolean {
        val container = panel ?: return false
        container.removeView(recycler)
        root.removeView(container)
        recycler.layoutManager = originalManager
        recycler.recycledViewPool.clear()
        recycler.clipToPadding = originalClipToPadding
        originalPadding?.let { recycler.setPadding(it.left, it.top, it.right, it.bottom) }
        parent?.addView(recycler, index.coerceIn(0, parent!!.childCount), originalParams)
        panel = null; parent = null; originalParams = null; originalManager = null; originalPadding = null
        onRestore()
        return true
    }
}
