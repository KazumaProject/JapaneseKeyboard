package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_clipboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.preference.PreferenceManager
import com.kazumaproject.core.data.clipboard.ClipboardItem
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.listeners.ClipboardItemAction
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.*

/** Independent IME sub-panel. Visibility lasts only for the current input-view session. */
internal class FloatingClipboardController(
    private val context: Context,
    private val eligible: () -> Boolean,
    private val colors: () -> CandidatePanelColors,
    private val onHistoryEnabled: (Boolean) -> Unit,
    private val onAction: (ClipboardItem, ClipboardItemAction, (Boolean) -> Unit) -> Unit,
    private val onStateChanged: () -> Unit,
) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val coordinates = FloatingWindowCoordinates(manager)
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val density get() = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private var anchor: View? = null
    private var window: WindowManager.LayoutParams? = null
    private var bounds: GuideBounds? = null
    private var area = Rect()
    private var landscape = false
    private var gesture: ComposingGuideGesture? = null
    private var editing = false
    private var operation = 0L
    private var busy = false
    private var view: FloatingClipboardView? = null
    private var font = KeyboardFontApplicator.processSnapshot
    private var items = emptyList<ClipboardItem>()
    private var historyEnabled = false
    var visible = false
        private set
    fun currentBounds(): GuideBounds? = bounds?.takeIf { view?.isAttachedToWindow == true }
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { refresh() }

    fun attach(host: View) {
        detach()
        anchor = host
        host.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        host.post { if (anchor === host) refresh() }
    }

    fun detach() {
        anchor?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
        anchor = null
        gesture = null
        editing = false
        bounds = null
        unmount()
    }

    fun toggle() { if (visible) hide() else { visible = true; refresh(); onStateChanged() } }

    fun hide() {
        visible = false
        detach()
        onStateChanged()
    }

    fun destroy() { hide(); view = null; items = emptyList() }

    fun setKeyboardFont(snapshot: KeyboardFontSnapshot) { font = snapshot; view?.setKeyboardFont(snapshot) }
    fun updateItems(value: List<ClipboardItem>) { items = value; view?.updateItems(value) }
    fun updateHistoryEnabled(value: Boolean) { historyEnabled = value; view?.updateHistoryEnabled(value) }

    fun refresh() {
        val host = anchor
        if (!visible || !eligible() || host?.isAttachedToWindow != true) { unmount(); return }
        val nextArea = coordinates.safeArea(host)
        val nextLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (nextArea.width() < dp(MIN_WIDTH) || nextArea.height() < dp(MIN_HEIGHT)) { unmount(); return }
        if (area != nextArea || landscape != nextLandscape) {
            gesture = null
            editing = false
            bounds = null
            area = nextArea
            landscape = nextLandscape
        }
        val root = view ?: FloatingClipboardView(context, ::toggleEditing, ::handle, ::hide,
            onHistoryEnabled, ::performAction).also {
            view = it
            coordinates.observeLegacyOrigin(it, { window }, ::refresh)
            it.setKeyboardFont(font)
            it.updateHistoryEnabled(historyEnabled)
            it.updateItems(items)
        }
        root.applicationWindowView = host.rootView
        root.setEditing(editing)
        root.setBusy(busy)
        root.setEditAvailable(!busy)
        root.setColors(colors())
        if (bounds == null) bounds = load().resolve(area.left, area.top, area.width(), area.height(), density, MIN_HEIGHT.toFloat())
        val b = bounds ?: return
        val params = window ?: WindowManager.LayoutParams(b.width, b.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT).apply {
            token = host.windowToken
            gravity = Gravity.TOP or Gravity.LEFT
            setTitle("Floating clipboard")
            coordinates.configure(this)
        }
        val previous = window?.let { listOf(it.x, it.y, it.width, it.height) }
        coordinates.position(params, b.x, b.y)
        params.width = b.width
        params.height = b.height
        try {
            if (root.parent == null) manager.addView(root, params)
            else if (previous != listOf(params.x, params.y, params.width, params.height)) manager.updateViewLayout(root, params)
            window = params
        } catch (_: WindowManager.BadTokenException) { hide() }
        catch (_: IllegalArgumentException) { hide() }
    }

    private fun unmount() {
        operation++
        busy = false
        view?.dismissMenu()
        view?.setBusy(false)
        view?.takeIf { it.parent != null }?.let { manager.removeViewImmediate(it) }
        window = null
    }

    private fun toggleEditing() {
        if (busy) return
        view?.dismissMenu()
        gesture = null
        editing = !editing
        refresh()
        view?.setBusy(false)
    }

    private fun performAction(item: ClipboardItem, action: ClipboardItemAction) {
        if (busy || editing) return
        val token = ++operation
        busy = true
        view?.setEditAvailable(false)
        view?.setBusy(true)
        onAction(item, action) { success ->
            if (token == operation && visible) {
                busy = false
                view?.setEditAvailable(true)
                view?.setBusy(false)
                if (!success) view?.showError()
            }
        }
    }

    private fun prefix() = "floating_clipboard_${if (landscape) "landscape" else "portrait"}_"
    private fun load(): ComposingGuidePlacement {
        val p = prefix()
        return ComposingGuidePlacement(
            preferences.getFloat(p + "x", .5f), preferences.getFloat(p + "y", .1f),
            preferences.getFloat(p + "width", 300f).finiteOr(300f).coerceAtLeast(MIN_WIDTH.toFloat()),
            preferences.getFloat(p + "height", 400f).finiteOr(400f))
    }

    private fun save() {
        val b = bounds ?: return
        val p = prefix()
        preferences.edit()
            .putFloat(p + "x", (b.x - area.left).toFloat() / (area.width() - b.width).coerceAtLeast(1))
            .putFloat(p + "y", (b.y - area.top).toFloat() / (area.height() - b.height).coerceAtLeast(1))
            .putFloat(p + "width", b.width / density).putFloat(p + "height", b.height / density).apply()
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
                val edge = view?.handleAt(event.getX(index), event.getY(index)) ?: return
                if ((edge == GuideHandle.MOVE) == editing) return
                val reducer = gesture ?: ComposingGuideGesture(b,
                    GuideBounds(area.left, area.top, area.width(), area.height()), dp(MIN_WIDTH), dp(MIN_HEIGHT))
                    .also { gesture = it }
                reducer.add(id, edge, points.getValue(id))
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

    companion object { const val MIN_WIDTH = 200; const val MIN_HEIGHT = 240 }
}
