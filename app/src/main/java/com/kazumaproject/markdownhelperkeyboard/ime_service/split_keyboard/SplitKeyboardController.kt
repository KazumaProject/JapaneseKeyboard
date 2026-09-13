package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.*
import kotlin.math.roundToInt

/** Owns two IME-attached windows; input and keyboard content belong to the service. */
internal class SplitKeyboardController(
    private val context: Context,
    private val anchor: View,
    private val onActivate: (SplitSlot) -> Unit,
    private val onInputFinished: (SplitSlot) -> Unit,
    private val onEditing: () -> Unit,
    private val colors: () -> CandidatePanelColors = { CandidatePanelColors.resolve(context) },
    private val onWindowFailure: () -> Unit = {},
) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val settings = SplitKeyboardSettings(preferences)
    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SplitKeyboardSettings.CANDIDATES) anchor.post { refresh() }
    }
    private val density get() = context.resources.displayMetrics.density
    private val panes = linkedMapOf<SplitSlot, Pane>()
    var editing = false
        private set
    private var landscape = isLandscape()
    private var area = Rect()
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { refresh() }
    private var disposed = false

    private inner class InputFrame(val slot: SplitSlot) : FrameLayout(context) {
        private var keyboardGesture = false
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (editing) return true
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                val candidates = panes[slot]?.candidates
                keyboardGesture = candidates?.isVisible != true || event.y >= candidates.height
            }
            // Candidate selection uses the shared composing session, not this pane's mode.
            if (!keyboardGesture) return super.dispatchTouchEvent(event)
            onActivate(slot)
            return try { super.dispatchTouchEvent(event) } finally { onInputFinished(slot) }
        }
    }

    private inner class Pane(val slot: SplitSlot, val minWidth: Int, val minHeight: Int) {
        val root = FloatingPanelFrame(context, { setEditing(!editing) }, { gesture(this, it) }).apply {
            contentDescription = context.getString(if (slot == SplitSlot.MAIN) R.string.split_keyboard_main else R.string.split_keyboard_sub)
        }
        val input = InputFrame(slot)
        val contents = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        lateinit var candidates: RecyclerView
        var placement = settings.placement(slot, landscape)
        var params: WindowManager.LayoutParams? = null
        var gesture: ComposingGuideGesture? = null
        var gestureStart: SplitPlacement? = null
        var bounds = GuideBounds(0, 0, 1, 1)
        val horizontalChrome get() = root.contentInsets.let { it.left + it.right }
        val verticalChrome get() = root.contentInsets.let { it.top + it.bottom } + if (settings.candidates.shows(slot)) dp(58) else 0
        init {
            input.addView(contents, FrameLayout.LayoutParams(-1, -1))
            root.contentContainer.addView(input, FrameLayout.LayoutParams(-1, -1))
        }
    }

    fun add(slot: SplitSlot, body: View, candidates: RecyclerView, minimumWidthDp: Int, minimumHeightDp: Int = 180) {
        check(!disposed)
        val pane = Pane(slot, minimumWidthDp, minimumHeightDp)
        (body.parent as? ViewGroup)?.removeView(body)
        (candidates.parent as? ViewGroup)?.removeView(candidates)
        pane.candidates = candidates
        pane.contents.addView(candidates, LinearLayout.LayoutParams(-1, dp(58)))
        pane.contents.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        panes[slot] = pane
    }

    fun start() {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        anchor.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        refresh()
    }

    fun setEditing(value: Boolean) {
        if (disposed) return
        panes.values.forEach { pane ->
            pane.gestureStart?.let { pane.placement = it }
            pane.gestureStart = null
            pane.gesture = null
        }
        editing = value
        onEditing()
        refresh()
    }

    fun refresh() {
        if (disposed || !anchor.isAttachedToWindow) return
        val nextArea = availableArea()
        val nextLandscape = isLandscape()
        if (area != nextArea || nextLandscape != landscape) {
            editing = false
            landscape = nextLandscape
            area = nextArea
            panes.values.forEach {
                it.gesture = null
                it.gestureStart = null
                it.root.setEditing(false)
                it.placement = settings.placement(it.slot, landscape)
                if (!settings.hasPlacement(it.slot, landscape)) {
                    val halfWidth = ((area.width() - dp(8)) / 2f - it.horizontalChrome) / density
                    it.placement = it.placement.copy(widthDp = halfWidth.coerceAtLeast(it.minWidth.toFloat()).coerceAtMost(280f))
                }
            }
            val totalWidth = panes.values.sumOf { dp(it.placement.widthDp).coerceAtLeast(dp(it.minWidth)) + it.horizontalChrome }
            if (totalWidth > area.width() && !settings.hasPlacement(SplitSlot.MAIN, landscape)) {
                panes[SplitSlot.MAIN]?.let { it.placement = it.placement.copy(y = 0f) }
            }
        }
        if (area.width() <= 0 || area.height() <= 0) return
        // Same-type attached windows stack in insertion order. Never re-add a pane
        // when it is touched or edited: MAIN must stay above SUB throughout its lifetime.
        listOf(SplitSlot.SUB, SplitSlot.MAIN).forEach { panes[it]?.let(::render) }
    }

    private fun render(pane: Pane) {
        if (disposed) return
        pane.root.setColors(colors())
        pane.root.setEditing(editing)
        pane.candidates.isVisible = settings.candidates.shows(pane.slot)
        pane.input.importantForAccessibility = if (editing) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        val width = (dp(pane.placement.widthDp).coerceAtLeast(dp(pane.minWidth)) + pane.horizontalChrome).coerceAtMost(area.width())
        val height = (dp(pane.placement.heightDp).coerceAtLeast(dp(pane.minHeight)) + pane.verticalChrome).coerceAtMost(area.height())
        val x = area.left + ((area.width() - width) * pane.placement.x).roundToInt()
        val y = area.top + ((area.height() - height) * pane.placement.y).roundToInt()
        pane.bounds = GuideBounds(x, y, width, height)
        val params = pane.params ?: WindowManager.LayoutParams(width, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT).apply {
            token = anchor.windowToken
            gravity = Gravity.TOP or Gravity.LEFT
            setTitle("Split keyboard ${pane.slot}")
        }
        params.x = x; params.y = y; params.width = width; params.height = height
        try {
            if (pane.root.parent == null) manager.addView(pane.root, params) else manager.updateViewLayout(pane.root, params)
            pane.params = params
        } catch (_: WindowManager.BadTokenException) {
            stop(); onWindowFailure()
        } catch (_: IllegalArgumentException) {
            stop(); onWindowFailure()
        }
    }

    private fun gesture(pane: Pane, event: MotionEvent) {
        val index = event.actionIndex
        fun point(i: Int) = GuidePoint(event.getX(i) + event.rawX - event.x, event.getY(i) + event.rawY - event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val handle = pane.root.handleAt(event.x, event.y) ?: return
                pane.gestureStart = pane.placement
                pane.gesture = ComposingGuideGesture(pane.bounds, GuideBounds(area.left, area.top, area.width(), area.height()),
                    (dp(pane.minWidth) + pane.horizontalChrome).coerceAtMost(area.width()),
                    (dp(pane.minHeight) + pane.verticalChrome).coerceAtMost(area.height())).also {
                    it.add(event.getPointerId(index), handle, point(index))
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val handle = pane.root.handleAt(event.getX(index), event.getY(index)) ?: return
                pane.gesture?.add(event.getPointerId(index), handle, point(index))
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val gesture = pane.gesture ?: return
                val bounds = gesture.move((0 until event.pointerCount).associate { event.getPointerId(it) to point(it) })
                pane.placement = SplitPlacement(
                    ((bounds.x - area.left).toFloat() / (area.width() - bounds.width).coerceAtLeast(1)).coerceIn(0f, 1f),
                    ((bounds.y - area.top).toFloat() / (area.height() - bounds.height).coerceAtLeast(1)).coerceIn(0f, 1f),
                    (bounds.width - pane.horizontalChrome) / density, (bounds.height - pane.verticalChrome) / density)
                render(pane)
                if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) gesture.remove(event.getPointerId(index))
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    settings.savePlacement(pane.slot, landscape, pane.placement)
                    pane.gesture = null
                    pane.gestureStart = null
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pane.gestureStart?.let { pane.placement = it }
                pane.gesture = null
                pane.gestureStart = null
                render(pane)
            }
        }
    }

    fun stop() {
        if (disposed) return
        disposed = true
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        anchor.viewTreeObserver.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
        panes.values.forEach { pane ->
            pane.gesture = null
            pane.gestureStart = null
            if (pane.root.parent != null) manager.removeViewImmediate(pane.root)
            pane.candidates.adapter = null
        }
    }
    private fun dp(value: Int) = dp(value.toFloat())
    private fun dp(value: Float) = (value * density).roundToInt()
    private fun isLandscape() = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private fun availableArea(): Rect {
        val metrics = context.resources.displayMetrics
        if (Build.VERSION.SDK_INT >= 30) {
            val window = manager.currentWindowMetrics
            val insets = window.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return Rect(window.bounds).apply { left += insets.left; top += insets.top; right -= insets.right; bottom -= insets.bottom }
        }
        val insets = anchor.rootWindowInsets
        return Rect(insets?.stableInsetLeft ?: 0, insets?.stableInsetTop ?: 0,
            metrics.widthPixels - (insets?.stableInsetRight ?: 0), metrics.heightPixels - (insets?.stableInsetBottom ?: 0))
    }
}
