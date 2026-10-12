package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.preference.PreferenceManager
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.*
import timber.log.Timber

/** IME-attached, non-focusable controls. Geometry is independent of the keyboard and candidates. */
internal class FloatingCursorController(
    private val context: Context,
    private val eligible: () -> Boolean,
    private val colors: () -> CandidatePanelColors,
    private val onMove: (CursorDirection) -> Unit,
    private val onToggleSelection: () -> Unit,
    private val onCancelActions: () -> Unit,
    private val onHidden: () -> Unit = {},
) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val settings = FloatingCursorSettings(preferences)
    private val manager = context.getSystemService(WindowManager::class.java)
    private val coordinates = FloatingWindowCoordinates(manager)
    private var host: View? = null
    private var view: FloatingCursorView? = null
    private var params: WindowManager.LayoutParams? = null
    private var bounds: GuideBounds? = null
    private var area = Rect()
    private var landscape = false
    private var editing = false
    private var gesture: ComposingGuideGesture? = null
    private var routingTouch = false
    private var editAvailable: Boolean? = null
    private var selected = false
    private var selectionAvailable = false
    private var font = KeyboardFontApplicator.processSnapshot
    private val density get() = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    val acceptingInput get() = view?.isAttachedToWindow == true && !editing && gesture == null
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { refresh() }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key.startsWith("floating_cursor_")) {
            host?.post {
                bounds = null
                refresh()
            }
        }
    }

    init { preferences.registerOnSharedPreferenceChangeListener(preferenceListener) }

    fun start(anchor: View) {
        stop()
        host = anchor
        anchor.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        anchor.post { if (host === anchor) refresh() }
    }

    fun stop() {
        onCancelActions()
        gesture = null; editing = false; bounds = null
        dismiss()
        host?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
        host = null
    }

    fun destroy() { stop(); routingTouch = false; preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener); view = null }
    fun setKeyboardFont(snapshot: KeyboardFontSnapshot) { font = snapshot; view?.setKeyboardFont(snapshot) }
    fun updateSelection(selected: Boolean, available: Boolean) {
        this.selected = selected; selectionAvailable = available
        view?.updateSelection(selected, available)
    }

    fun refresh() {
        val anchor = host
        if (!settings.enabled || !eligible() || anchor?.isAttachedToWindow != true) { dismiss(); return }
        val nextArea = coordinates.safeArea(anchor)
        val nextLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (nextArea != area || nextLandscape != landscape) {
            onCancelActions(); view?.cancelInput(); gesture = null; bounds = null
            if (nextLandscape != landscape) editing = false
        }
        area = nextArea; landscape = nextLandscape
        if (editing && area.height() < dp(FloatingCursorView.MIN_HEIGHT_DP + FloatingCursorView.EDIT_EXTRA_DP)) {
            editing = false; bounds = null
        }
        val extra = if (editing) FloatingCursorView.EDIT_EXTRA_DP else 0
        if (area.width() < dp(FloatingCursorView.MIN_WIDTH_DP) ||
            area.height() < dp(FloatingCursorView.MIN_HEIGHT_DP + extra)) { dismiss(); return }
        val panel = view ?: FloatingCursorView(context,
            onEdit = {
                onCancelActions(); view?.cancelInput(); finishGesture(false)
                editing = !editing; bounds = null; refresh()
            },
            onHandle = ::handleEvent,
            onHide = { preferences.edit().putBoolean(FloatingCursorSettings.ENABLED, false).apply(); dismiss() },
            onMove = { if (acceptingInput) onMove(it) },
            onToggleSelection = { if (acceptingInput && selectionAvailable) onToggleSelection() },
        ).also {
            view = it
            it.setKeyboardFont(font)
            coordinates.observeLegacyOrigin(it, { params }, ::refresh)
        }
        panel.setEditing(editing)
        panel.setControlsEnabled(!editing && gesture == null)
        val canEdit = editing || area.height() >= dp(FloatingCursorView.MIN_HEIGHT_DP + FloatingCursorView.EDIT_EXTRA_DP)
        if (editAvailable != canEdit) {
            editAvailable = canEdit
            panel.setEditAvailable(canEdit)
        }
        panel.setColors(colors())
        panel.updateSelection(selected, selectionAvailable)
        if (bounds == null) {
            val saved = settings.load(landscape)
            val normal = saved.copy(widthDp = saved.widthDp.finiteOr(320f).coerceAtLeast(FloatingCursorView.MIN_WIDTH_DP.toFloat()),
                heightDp = saved.heightDp.finiteOr(300f).coerceAtLeast(FloatingCursorView.MIN_HEIGHT_DP.toFloat()))
                .resolve(area.left, area.top, area.width(), area.height(), density, FloatingCursorView.MIN_HEIGHT_DP.toFloat())
            val height = (normal.height + dp(extra)).coerceAtMost(area.height())
            bounds = normal.copy(height = height, y = normal.y.coerceAtMost(area.bottom - height))
        }
        bounds?.let { updateWindow(it, anchor) }
    }

    private fun handleEvent(event: MotionEvent) {
        val points = (0 until event.pointerCount).associate { index ->
            event.getPointerId(index) to GuidePoint(event.getX(index) + event.rawX - event.x,
                event.getY(index) + event.rawY - event.y)
        }
        val index = event.actionIndex
        val id = event.getPointerId(index)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val handle = view?.handleAt(event.getX(index), event.getY(index)) ?: return
                if ((handle == GuideHandle.MOVE) == editing) return
                onCancelActions(); view?.cancelInput(); view?.setControlsEnabled(false)
                val current = bounds ?: return
                val reducer = gesture ?: ComposingGuideGesture(current,
                    GuideBounds(area.left, area.top, area.width(), area.height()), dp(FloatingCursorView.MIN_WIDTH_DP),
                    dp(FloatingCursorView.MIN_HEIGHT_DP + if (editing) FloatingCursorView.EDIT_EXTRA_DP else 0)).also { gesture = it }
                reducer.move(points); reducer.add(id, handle, points.getValue(id))
            }
            MotionEvent.ACTION_MOVE -> gesture?.move(points)?.let { bounds = it }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                gesture?.move(points)?.let { bounds = it }; gesture?.remove(id)
                if (gesture?.pointerCount == 0) finishGesture(true)
            }
            MotionEvent.ACTION_CANCEL -> finishGesture(false)
        }
        bounds?.let { target -> host?.let { updateWindow(target, it) } }
    }

    private fun finishGesture(commit: Boolean) {
        val reducer = gesture ?: return
        bounds = if (commit) reducer.bounds else reducer.cancel()
        gesture = null
        if (commit) bounds?.let { target ->
            val normalHeight = target.height - dp(if (editing) FloatingCursorView.EDIT_EXTRA_DP else 0)
            settings.save(landscape, ComposingGuidePlacement(
                (target.x - area.left).toFloat() / (area.width() - target.width).coerceAtLeast(1),
                (target.y - area.top).toFloat() / (area.height() - normalHeight).coerceAtLeast(1),
                target.width / density,
                normalHeight / density,
            ))
        }
        view?.setControlsEnabled(!editing)
    }

    fun dispatchInputWindowTouch(event: MotionEvent): Boolean {
        val panel = view?.takeIf { it.isAttachedToWindow && it.isShown }
        val location = IntArray(2)
        panel?.getLocationOnScreen(location)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            routingTouch = panel != null && event.rawX >= location[0] && event.rawX < location[0] + panel.width &&
                event.rawY >= location[1] && event.rawY < location[1] + panel.height
        }
        if (!routingTouch) return false
        if (panel != null) {
            val translated = MotionEvent.obtain(event)
            translated.offsetLocation(event.rawX - event.x - location[0], event.rawY - event.y - location[1])
            try { panel.dispatchTouchEvent(translated) } finally { translated.recycle() }
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) routingTouch = false
        return true
    }

    private fun updateWindow(target: GuideBounds, anchor: View) {
        val panel = view ?: return
        val layout = params ?: WindowManager.LayoutParams(target.width, target.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply {
            token = anchor.windowToken; gravity = Gravity.TOP or Gravity.LEFT
            setTitle("Floating cursor"); coordinates.configure(this)
        }
        val previousX = layout.x; val previousY = layout.y
        val previousWidth = layout.width; val previousHeight = layout.height
        coordinates.position(layout, target.x, target.y)
        val changed = previousX != layout.x || previousY != layout.y || previousWidth != target.width || previousHeight != target.height
        layout.width = target.width; layout.height = target.height
        try {
            if (panel.parent == null) manager.addView(panel, layout) else if (changed) manager.updateViewLayout(panel, layout)
            params = layout
        } catch (e: WindowManager.BadTokenException) { dismiss(); Timber.w(e, "Floating cursor lost its IME window") }
        catch (e: IllegalArgumentException) { dismiss(); Timber.w(e, "Floating cursor host was detached") }
    }

    private fun dismiss() {
        if (view?.parent != null || params != null) { onCancelActions(); onHidden() }
        view?.cancelInput(); gesture = null
        editing = false; bounds = null; editAvailable = null
        view?.takeIf { it.parent != null }?.let { runCatching { manager.removeViewImmediate(it) } }
        params = null
    }
}
