package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_keyboard

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.PopupWindow
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.FloatingWindowCoordinates

/**
 * Keeps the service's popup lifecycle API while hosting the panel like a split pane.
 * PopupWindow's attached decor double-offsets accessibility bounds on older Android releases.
 */
internal class FloatingKeyboardWindow(private val panel: FloatingKeyboardPanel, initialWidth: Int) :
    PopupWindow(panel, initialWidth, ViewGroup.LayoutParams.WRAP_CONTENT) {
    private val manager = panel.context.getSystemService(WindowManager::class.java)
    private val coordinates = FloatingWindowCoordinates(manager)
    private var params: WindowManager.LayoutParams? = null
    private var dismissListener: OnDismissListener? = null
    private var screenX = 0
    private var screenY = 0
    private var rendering = false
    private var anchor: View? = null
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { render() }

    init {
        coordinates.observeLegacyOrigin(panel, { params }) { if (isShowing) render() }
    }

    override fun isShowing() = params != null
    override fun setOnDismissListener(listener: OnDismissListener?) { dismissListener = listener }

    override fun showAtLocation(parent: View, gravity: Int, x: Int, y: Int) {
        if (isShowing) return
        require(gravity and Gravity.LEFT == Gravity.LEFT && gravity and Gravity.TOP == Gravity.TOP)
        anchor = parent
        panel.applicationWindowView = parent
        screenX = x
        screenY = y
        val next = WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT).apply {
            token = parent.windowToken
            this.gravity = Gravity.TOP or Gravity.LEFT
            setTitle("Floating keyboard")
            coordinates.configure(this)
        }
        params = next
        try {
            render()
            manager.addView(panel, next)
            panel.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        } catch (error: RuntimeException) {
            params = null
            panel.applicationWindowView = null
            throw error
        }
    }

    override fun update(width: Int, height: Int) {
        this.width = width
        this.height = height
        render()
    }

    override fun update(x: Int, y: Int, width: Int, height: Int) {
        screenX = x
        screenY = y
        if (width != -1) this.width = width
        if (height != -1) this.height = height
        render()
    }

    override fun update() = render()

    private fun render() {
        val current = params ?: return
        if (rendering) return
        rendering = true
        try {
            val area = coordinates.safeArea(anchor ?: panel)
            if (area.isEmpty) return
            val measuredWidth = (if (width > 0) width else area.width()).coerceIn(1, area.width())
            panel.measure(View.MeasureSpec.makeMeasureSpec(measuredWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(area.height(), View.MeasureSpec.AT_MOST))
            val measuredHeight = panel.measuredHeight.coerceIn(1, area.height())
            screenX = screenX.coerceIn(area.left, area.right - measuredWidth)
            screenY = screenY.coerceIn(area.top, area.bottom - measuredHeight)
            val oldX = current.x; val oldY = current.y
            val oldWidth = current.width; val oldHeight = current.height
            coordinates.position(current, screenX, screenY)
            current.width = measuredWidth
            current.height = measuredHeight
            if (panel.parent != null && (oldX != current.x || oldY != current.y || oldWidth != current.width || oldHeight != current.height)) {
                manager.updateViewLayout(panel, current)
                panel.notifyWindowPositionChanged()
            }
        } finally {
            rendering = false
        }
    }

    override fun dismiss() {
        if (params == null) return
        panel.viewTreeObserver.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
        params = null
        if (panel.parent != null) manager.removeViewImmediate(panel)
        panel.applicationWindowView = null
        dismissListener?.onDismiss()
    }
}
