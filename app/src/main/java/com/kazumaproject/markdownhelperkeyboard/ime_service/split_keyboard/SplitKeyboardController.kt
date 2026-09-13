package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.*
import android.widget.*
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import kotlin.math.roundToInt

/** Owns two IME-attached windows. The service owns input and the keyboard view instances. */
internal class SplitKeyboardController(
    private val context: Context,
    private val anchor: View,
    private val onActivate: (SplitSlot) -> Unit,
    private val onInputFinished: (SplitSlot) -> Unit,
    private val onEditing: () -> Unit,
    private val onNext: () -> Unit,
    private val onWindowFailure: () -> Unit = {},
) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val settings = SplitKeyboardSettings(preferences)
    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SplitKeyboardSettings.CANDIDATES) anchor.post { refresh() }
    }
    private val density = context.resources.displayMetrics.density
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
            // Candidates belong to the shared composing session, not the keyboard type
            // under this strip. Changing source at DOWN can also rebind the clicked item.
            if (!keyboardGesture) return super.dispatchTouchEvent(event)
            onActivate(slot)
            return try { super.dispatchTouchEvent(event) } finally { onInputFinished(slot) }
        }
    }
    private inner class Pane(val slot: SplitSlot, val minWidth: Int, val minHeight: Int) {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.DKGRAY) }
        val header = LinearLayout(context)
        val move = TextView(context).apply {
            text = context.getString(if (slot == SplitSlot.MAIN) R.string.split_keyboard_main_handle else R.string.split_keyboard_sub_handle)
            textSize = 14f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(4), 0, dp(4), 0)
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            contentDescription = context.getString(if (slot == SplitSlot.MAIN) R.string.split_keyboard_main else R.string.split_keyboard_sub)
        }
        val edit = Button(context).apply { textSize = 12f; minWidth = 0; minimumWidth = 0 }
        val resize = TextView(context).apply {
            text = "↘ " + context.getString(R.string.split_keyboard_resize)
            contentDescription = text; gravity = Gravity.END or Gravity.CENTER_VERTICAL; setTextColor(Color.WHITE)
        }
        val input = InputFrame(slot)
        val contents = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        lateinit var body: View
        lateinit var candidates: RecyclerView
        var placement = settings.placement(slot, landscape)
        var params: WindowManager.LayoutParams? = null
        var start: SplitPlacement? = null
        var downX = 0f
        var downY = 0f
        var bounds = Rect()
        init {
            header.addView(move, LinearLayout.LayoutParams(0, dp(44), 1f))
            header.addView(edit, LinearLayout.LayoutParams(dp(72), dp(44)))
            val next = Button(context).apply { text = "→"; contentDescription = context.getString(R.string.split_keyboard_next); setOnClickListener { onNext() } }
            header.addView(next, LinearLayout.LayoutParams(dp(48), dp(44)))
            root.addView(header)
            input.addView(contents)
            root.addView(input, LinearLayout.LayoutParams(-1, 0, 1f))
            root.addView(resize, LinearLayout.LayoutParams(-1, dp(36)))
            edit.setOnClickListener { setEditing(!editing) }
            bindGesture(move, this, false)
            bindGesture(resize, this, true)
        }
    }

    fun add(slot: SplitSlot, body: View, candidates: RecyclerView, minimumWidthDp: Int, minimumHeightDp: Int = 180) {
        check(!disposed)
        val pane = Pane(slot, minimumWidthDp, minimumHeightDp)
        (body.parent as? ViewGroup)?.removeView(body)
        (candidates.parent as? ViewGroup)?.removeView(candidates)
        pane.body = body
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
        panes.values.forEach { pane -> pane.start?.let { pane.placement = it }; pane.start = null }
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
                it.start = null
                it.placement = settings.placement(it.slot, landscape)
                if (!settings.hasPlacement(it.slot, landscape)) {
                    val halfWidth = ((area.width() / density - 8f) / 2f).coerceAtLeast(it.minWidth.toFloat())
                    it.placement = it.placement.copy(widthDp = halfWidth.coerceAtMost(280f))
                }
            }
            // Different layouts can have different minimum widths. Compare both
            // rendered widths rather than assuming two copies of the main pane.
            val totalWidth = panes.values.sumOf {
                dp(it.placement.widthDp.roundToInt()).coerceAtLeast(dp(it.minWidth))
            }
            if (totalWidth > area.width() && !settings.hasPlacement(SplitSlot.MAIN, landscape)) {
                panes[SplitSlot.MAIN]?.let { it.placement = it.placement.copy(y = 0f) }
            }
        }
        if (area.width() <= 0 || area.height() <= 0) return
        panes.values.forEach(::render)
    }
    private fun render(pane: Pane) {
        if (disposed) return
        val showCandidates = settings.candidates.shows(pane.slot)
        pane.candidates.isVisible = showCandidates
        pane.resize.isVisible = editing
        pane.input.importantForAccessibility = if (editing) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        pane.edit.setText(if (editing) R.string.split_keyboard_done else R.string.split_keyboard_edit)
        val extra = dp(44 + (if (showCandidates) 58 else 0) + (if (editing) 36 else 0))
        val width = dp(pane.placement.widthDp.roundToInt()).coerceIn(dp(pane.minWidth).coerceAtMost(area.width()), area.width())
        val bodyHeight = dp(pane.placement.heightDp.roundToInt()).coerceIn(dp(pane.minHeight).coerceAtMost((area.height() - extra).coerceAtLeast(1)), (area.height() - extra).coerceAtLeast(1))
        val height = (bodyHeight + extra).coerceAtMost(area.height())
        val x = area.left + ((area.width() - width) * pane.placement.x).roundToInt()
        val y = area.top + ((area.height() - height) * pane.placement.y).roundToInt()
        pane.bounds.set(x, y, x + width, y + height)
        val params = pane.params ?: WindowManager.LayoutParams(width, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT).apply {
            token = anchor.windowToken; gravity = Gravity.TOP or Gravity.LEFT
            setTitle("Split keyboard ${pane.slot}")
        }
        params.x = x; params.y = y; params.width = width; params.height = height
        try {
            if (pane.root.parent == null) manager.addView(pane.root, params) else manager.updateViewLayout(pane.root, params)
            pane.params = params
        } catch (_: WindowManager.BadTokenException) {
            stop()
            onWindowFailure()
        } catch (_: IllegalArgumentException) {
            stop()
            onWindowFailure()
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun bindGesture(handle: View, pane: Pane, resize: Boolean) {
        handle.setOnTouchListener { _, event ->
            if (resize != editing) return@setOnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val chrome = dp(44 + (if (settings.candidates.shows(pane.slot)) 58 else 0) + (if (editing) 36 else 0))
                    pane.placement = pane.placement.copy(widthDp = pane.bounds.width() / density,
                        heightDp = (pane.bounds.height() - chrome) / density)
                    pane.start = pane.placement; pane.downX = event.rawX; pane.downY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> pane.start?.let { start ->
                    val dx = event.rawX - pane.downX; val dy = event.rawY - pane.downY
                    pane.placement = if (resize) start.copy(
                        widthDp = (start.widthDp + dx / density).coerceIn(pane.minWidth.toFloat(), (area.width() / density).coerceAtLeast(pane.minWidth.toFloat())),
                        heightDp = (start.heightDp + dy / density).coerceAtLeast(pane.minHeight.toFloat())).normalized()
                    else start.copy(x = (start.x + dx / (area.width() - pane.bounds.width()).coerceAtLeast(1)).coerceIn(0f, 1f),
                        y = (start.y + dy / (area.height() - pane.bounds.height()).coerceAtLeast(1)).coerceIn(0f, 1f))
                    render(pane)
                }
                MotionEvent.ACTION_UP -> { if (pane.start != null) settings.savePlacement(pane.slot, landscape, pane.placement); pane.start = null }
                MotionEvent.ACTION_CANCEL -> { pane.start?.let { pane.placement = it }; pane.start = null; render(pane) }
            }
            true
        }
    }
    fun stop() {
        if (disposed) return
        disposed = true
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        anchor.viewTreeObserver.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
        panes.values.forEach { pane ->
            pane.start = null
            if (pane.root.parent != null) manager.removeViewImmediate(pane.root)
            pane.candidates.adapter = null
        }
    }
    private fun dp(value: Int) = (value * density).roundToInt()
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
