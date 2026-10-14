package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.*

internal class FloatingCursorController(
    private val context: Context,
    private val settings: FloatingCursorSettings,
    private val eligible: () -> Boolean,
    private val colors: () -> CandidatePanelColors,
    private val onMove: (CursorDirection) -> Unit,
    private val onSelect: () -> Unit,
    private val onClose: () -> Unit,
    private val onCancel: () -> Unit,
) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val coordinates = FloatingWindowCoordinates(manager)
    private val density get() = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private var anchor: View? = null
    private var root: FloatingCursorView? = null
    private var params: WindowManager.LayoutParams? = null
    private var area = Rect()
    private var landscape = false
    private var bounds: GuideBounds? = null
    private var gesture: ComposingGuideGesture? = null
    private var editing = false
    private var font = KeyboardFontApplicator.processSnapshot
    private var selected = false
    private var canSelect = true
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { refresh() }
    val acceptingInput get() = params != null && root?.isAttachedToWindow == true && !editing && eligible()

    fun attach(host: View) {
        if (anchor === host) { refresh(); return }
        stop()
        anchor = host
        host.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        host.post { if (anchor === host) refresh() }
    }

    fun stop() {
        anchor?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
        anchor = null
        editing = false
        gesture = null
        bounds = null
        unmount()
    }

    fun destroy() { stop(); root = null }
    fun cancelInput() { root?.cancelInput() }
    fun setKeyboardFont(snapshot: KeyboardFontSnapshot) { font = snapshot; root?.setKeyboardFont(snapshot) }
    fun updateSelection(selected: Boolean, available: Boolean) {
        this.selected = selected; canSelect = available
        root?.updateSelection(selected, available)
    }
    fun resetPlacement() { gesture = null; bounds = null; editing = false; refresh() }

    fun refresh() {
        val host = anchor
        if (!settings.enabled || !eligible() || host?.isAttachedToWindow != true) { unmount(); return }
        val nextArea = coordinates.safeArea(host)
        val nextLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (nextArea.width() < dp(FloatingCursorView.MIN_WIDTH) || nextArea.height() < dp(FloatingCursorView.MIN_HEIGHT)) {
            unmount(); return
        }
        if (nextArea != area || landscape != nextLandscape) {
            cancelInput(); onCancel()
            bounds = null; gesture = null; editing = false
            area = nextArea; landscape = nextLandscape
        }
        if (editing && area.height() < dp(FloatingCursorView.MIN_HEIGHT + FloatingCursorView.EDIT_EXTRA)) {
            editing = false; bounds = null; gesture = null
        }
        val view = root ?: FloatingCursorView(context, ::toggleEditing, ::handle,
            { cancelInput(); onCancel(); onClose() },
            { if (acceptingInput) onMove(it) },
            { if (acceptingInput) onSelect() }).also {
            root = it
            it.setKeyboardFont(font)
            coordinates.observeLegacyOrigin(it, { params }, ::refresh)
        }
        view.applicationWindowView = host.rootView
        view.setColors(colors())
        view.setEditing(editing)
        view.setControlsEnabled(!editing)
        view.setEditAvailable(editing || area.height() >= dp(FloatingCursorView.MIN_HEIGHT + FloatingCursorView.EDIT_EXTRA))
        view.updateSelection(selected, canSelect)
        val extra = if (editing) FloatingCursorView.EDIT_EXTRA else 0
        if (bounds == null) {
            val placement = settings.load(landscape)
            bounds = placement.copy(heightDp = placement.heightDp + extra).resolve(
                area.left, area.top, area.width(), area.height(), density,
                (FloatingCursorView.MIN_HEIGHT + extra).toFloat())
        }
        val b = bounds ?: return
        val previous = params?.let { listOf(it.x, it.y, it.width, it.height) }
        val p = params ?: WindowManager.LayoutParams(b.width, b.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT).apply {
            token = host.windowToken; gravity = Gravity.TOP or Gravity.LEFT
            setTitle("Floating cursor controls"); coordinates.configure(this)
        }
        coordinates.position(p, b.x, b.y)
        p.width = b.width; p.height = b.height
        try {
            if (view.parent == null) manager.addView(view, p)
            else if (previous != listOf(p.x, p.y, p.width, p.height)) manager.updateViewLayout(view, p)
            params = p
        } catch (_: WindowManager.BadTokenException) { unmount() }
        catch (_: IllegalArgumentException) { unmount() }
    }

    private fun unmount() {
        if (params != null) onCancel()
        cancelInput()
        root?.takeIf { it.parent != null }?.let { manager.removeViewImmediate(it) }
        params = null
    }

    private fun toggleEditing() {
        cancelInput(); onCancel(); gesture = null
        editing = !editing
        bounds = null
        refresh()
    }

    private fun save() {
        val b = bounds ?: return
        val extra = if (editing) dp(FloatingCursorView.EDIT_EXTRA) else 0
        val normal = b.copy(height = b.height - extra)
        settings.save(landscape, normal, GuideBounds(area.left, area.top, area.width(), area.height()), density)
    }

    private fun handle(event: MotionEvent) {
        val points = (0 until event.pointerCount).associate { i ->
            event.getPointerId(i) to GuidePoint(event.rawX - event.x + event.getX(i), event.rawY - event.y + event.getY(i))
        }
        val index = event.actionIndex
        val id = event.getPointerId(index)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val b = bounds ?: return
                val handle = root?.handleAt(event.getX(index), event.getY(index)) ?: return
                if ((handle == GuideHandle.MOVE) == editing) return
                cancelInput(); onCancel()
                val reducer = gesture ?: ComposingGuideGesture(b,
                    GuideBounds(area.left, area.top, area.width(), area.height()), dp(FloatingCursorView.MIN_WIDTH),
                    dp(FloatingCursorView.MIN_HEIGHT + if (editing) FloatingCursorView.EDIT_EXTRA else 0)).also { gesture = it }
                reducer.add(id, handle, points.getValue(id))
            }
            MotionEvent.ACTION_MOVE -> bounds = gesture?.move(points) ?: return
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                bounds = gesture?.move(points) ?: return
                gesture?.remove(id)
                if (gesture?.pointerCount == 0) { save(); gesture = null }
            }
            MotionEvent.ACTION_CANCEL -> { bounds = gesture?.cancel() ?: bounds; gesture = null }
        }
        refresh()
    }
}
