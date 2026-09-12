package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.preference.PreferenceManager
import timber.log.Timber
import kotlin.math.roundToInt

/** Non-focusable candidate window; the service owns candidate placement and keyboard sizing. */
internal class ComposingGuideController(
    private val context: Context,
    private val eligible: () -> Boolean,
    private val onStateChanged: () -> Unit,
    private val onSurfaceChanged: (LinearLayout?) -> Unit,
    private val colors: () -> CandidatePanelColors,
) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val settings = ComposingGuideSettings(preferences)
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var anchor: View? = null
    private var active = false
    private var content = ComposingGuideContent()
    private var guideView: ComposingGuideView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var bounds: GuideBounds? = null
    private var area = Rect()
    private var landscape = false
    private var editing = false
    private var gesture: ComposingGuideGesture? = null
    private var previewTextSize: Float? = null
    private var mountedSurface: LinearLayout? = null
    private val minimumContentHeight get() = if (!settings.showComposing) 168 else {
        val textExtra = (ComposingGuideView.composingLineHeight(context, previewTextSize ?: settings.textSize) / density - 64).coerceAtLeast(0f).roundToInt()
        232 + textExtra + if (content.visibleReading(true, settings.showReading).isNotEmpty())
            (ComposingGuideView.readingLineHeight(context, previewTextSize ?: settings.textSize) / density).roundToInt() else 0
    }
    private var lastShortcutState: Pair<Boolean, Boolean>? = null
    private val density get() = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()
    private val extra get() = dp(ComposingGuideView.MOVE_BAND_DP) +
        if (editing) dp(ComposingGuideView.EDIT_EXTRA_DP) else 0
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { refresh() }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key.startsWith("composing_guide_")) anchor?.post { refresh(); onStateChanged() }
    }

    init { preferences.registerOnSharedPreferenceChangeListener(preferenceListener) }

    fun start(view: View) {
        stop()
        anchor = view
        active = true
        view.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        refresh()
    }

    fun update(value: CharSequence?, reading: String = "", liveConversion: Boolean = false) {
        content = if (value.isNullOrEmpty()) ComposingGuideContent() else ComposingGuideContent(value.toString(), reading, liveConversion)
        refresh()
    }

    fun toggleVisible() {
        finishGesture(commit = true)
        leaveEditing()
        settings.visible = !settings.visible
        refresh()
    }

    fun stop() {
        // Interrupted gestures are cancelled, not persisted during an editor/mode transition.
        gesture = null
        leaveEditing()
        active = false
        content = ComposingGuideContent()
        dismiss()
        anchor?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
        anchor = null
        lastShortcutState = null
    }

    fun destroy() { stop(); preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener); guideView = null }

    fun refresh() {
        val allowed = settings.enabled && eligible()
        val shortcutState = allowed to settings.visible
        if (lastShortcutState != shortcutState) { lastShortcutState = shortcutState; onStateChanged() }
        val host = anchor
        if (!active || !allowed || !settings.visible || host?.isAttachedToWindow != true) {
            gesture = null
            leaveEditing()
            dismiss()
            return
        }
        val nextLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val nextArea = availableArea(host)
        if (nextLandscape != landscape || nextArea != area) {
            gesture = null
            if (nextLandscape != landscape) leaveEditing()
            bounds = null
        }
        landscape = nextLandscape
        area = nextArea
        if (area.width() < dp(200) || area.height() < dp(minimumContentHeight + ComposingGuideView.MOVE_BAND_DP)) { dismiss(); return }
        if (editing && area.height() < dp(minimumContentHeight + ComposingGuideView.MOVE_BAND_DP + ComposingGuideView.EDIT_EXTRA_DP)) leaveEditing()
        val view = guideView ?: ComposingGuideView(context,
            onEdit = ::toggleEditing,
            onHide = ::toggleVisible,
            onTextSize = { size, commit ->
                previewTextSize = size
                if (commit) { settings.textSize = size; previewTextSize = null }
                refresh()
            },
            onHandleEvent = ::handleEvent,
        ).also { guideView = it }
        view.setColors(colors())
        if (view.editing != editing) view.setEditing(editing)
        view.setEditAvailable(editing || area.height() >= dp(minimumContentHeight + ComposingGuideView.MOVE_BAND_DP + ComposingGuideView.EDIT_EXTRA_DP))
        view.setShowComposing(settings.showComposing)
        view.setContent(content.text, previewTextSize ?: settings.textSize, content.visibleReading(settings.showComposing, settings.showReading))
        if ((!editing && gesture == null) || bounds == null) {
            var placement = settings.load(landscape)
            if (!settings.usesScreenCoordinates(landscape)) {
                val legacy = legacyAvailableArea(host)
                val oldArea = GuideBounds(legacy.left, legacy.top, legacy.width().coerceAtLeast(1), (legacy.height() - dp(ComposingGuideView.MOVE_BAND_DP)).coerceAtLeast(1))
                val newArea = GuideBounds(area.left, area.top, area.width(), (area.height() - dp(ComposingGuideView.MOVE_BAND_DP)).coerceAtLeast(1))
                val previousMinimum = if (settings.showComposing) 232f else 168f
                placement = placement.copy(heightDp = placement.heightDp.coerceAtLeast(previousMinimum))
                    .rebase(oldArea, newArea, density, minimumContentHeight.toFloat())
                settings.save(landscape, placement)
            }
            val normal = placement.copy(heightDp = placement.heightDp.coerceAtLeast(minimumContentHeight.toFloat())).resolve(area.left, area.top, area.width(), (area.height() - dp(ComposingGuideView.MOVE_BAND_DP)).coerceAtLeast(1), density)
            val height = (normal.height + extra).coerceAtMost(area.height())
            bounds = normal.copy(y = normal.y.coerceAtMost(area.bottom - height), height = height)
        }
        bounds?.let { updateWindow(it, host) }
    }

    private fun availableArea(host: View): Rect {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return Rect(metrics.bounds).apply {
                left += insets.left; top += insets.top; right -= insets.right; bottom -= insets.bottom
            }
        }
        val size = android.graphics.Point().also { windowManager.defaultDisplay.getRealSize(it) }
        val insets = host.rootWindowInsets
        val cutout = if (Build.VERSION.SDK_INT >= 28) insets?.displayCutout else null
        return Rect(maxOf(insets?.stableInsetLeft ?: 0, cutout?.safeInsetLeft ?: 0),
            maxOf(insets?.stableInsetTop ?: 0, cutout?.safeInsetTop ?: 0),
            size.x - maxOf(insets?.stableInsetRight ?: 0, cutout?.safeInsetRight ?: 0),
            size.y - maxOf(insets?.stableInsetBottom ?: 0, cutout?.safeInsetBottom ?: 0))
    }

    private fun legacyAvailableArea(host: View): Rect {
        val keyboardLocation = IntArray(2).also(host::getLocationOnScreen)
        val root = host.rootView
        val frame = Rect().also(root::getWindowVisibleDisplayFrame)
        val rootLocation = IntArray(2).also(root::getLocationOnScreen)
        val safe = if (Build.VERSION.SDK_INT >= 30) root.rootWindowInsets?.getInsets(
            WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()) else null
        return Rect(maxOf(rootLocation[0], frame.left, safe?.left ?: 0),
            maxOf(frame.top, safe?.top ?: 0),
            minOf(rootLocation[0] + root.width - (safe?.right ?: 0), frame.right),
            keyboardLocation[1] - dp(8))
    }

    private fun toggleEditing() {
        if (editing) { finishGesture(commit = true); leaveEditing() }
        else if (area.height() >= dp(minimumContentHeight + ComposingGuideView.MOVE_BAND_DP + ComposingGuideView.EDIT_EXTRA_DP)) { editing = true; bounds = null }
        refresh()
    }

    private fun leaveEditing() {
        editing = false
        previewTextSize = null
        bounds = null
        guideView?.takeIf { it.editing }?.setEditing(false)
    }

    private fun handleEvent(event: MotionEvent) {
        // rawX/Y for pointer zero provide the window offset on API 24 as well as newer releases.
        val offsetX = event.rawX - event.x
        val offsetY = event.rawY - event.y
        val points = (0 until event.pointerCount).associate { index ->
            event.getPointerId(index) to GuidePoint(event.getX(index) + offsetX, event.getY(index) + offsetY)
        }
        val index = event.actionIndex
        val id = event.getPointerId(index)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                gesture?.move(points)?.let { bounds = it }
                val handle = guideView?.handleAt(event.getX(index), event.getY(index)) ?: return
                if ((handle == GuideHandle.MOVE) == editing) return
                val current = bounds ?: return
                val reducer = gesture ?: ComposingGuideGesture(current,
                    GuideBounds(area.left, area.top, area.width(), area.height()), dp(200), dp(minimumContentHeight) + extra)
                    .also { gesture = it }
                reducer.add(id, handle, points.getValue(id))
            }
            MotionEvent.ACTION_MOVE -> gesture?.move(points)?.let { bounds = it }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                gesture?.move(points)?.let { bounds = it }
                gesture?.remove(id)
                if (gesture?.pointerCount == 0) finishGesture(commit = true)
            }
            MotionEvent.ACTION_CANCEL -> finishGesture(commit = false)
        }
        val host = anchor ?: return
        bounds?.let { updateWindow(it, host) }
    }

    private fun finishGesture(commit: Boolean) {
        val current = gesture ?: return
        bounds = if (commit) current.bounds else current.cancel()
        gesture = null
        if (commit) bounds?.let(::saveBounds)
    }

    private fun saveBounds(edited: GuideBounds) {
        val normal = edited.copy(height = edited.height - extra)
        settings.save(landscape, ComposingGuidePlacement(
            (normal.x - area.left).toFloat() / (area.width() - normal.width).coerceAtLeast(1),
            (normal.y - area.top).toFloat() / (area.height() - dp(ComposingGuideView.MOVE_BAND_DP) - normal.height).coerceAtLeast(1),
            normal.width / density, normal.height / density,
        ))
    }

    private fun mountSurface(surface: LinearLayout?) {
        if (mountedSurface === surface) return
        mountedSurface = surface
        onSurfaceChanged(surface)
    }

    private fun dismiss() {
        mountSurface(null)
        guideView?.takeIf { it.parent != null }?.let { windowManager.removeViewImmediate(it) }
        windowParams = null
    }

    private fun updateWindow(target: GuideBounds, host: View) {
        val view = guideView ?: return
        if (!host.isAttachedToWindow) return
        val params = windowParams ?: WindowManager.LayoutParams(target.width, target.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { token = host.windowToken; gravity = Gravity.TOP or Gravity.LEFT; setTitle("Composing text guide") }
        params.x = target.x; params.y = target.y; params.width = target.width; params.height = target.height
        try {
            if (view.parent == null) windowManager.addView(view, params) else windowManager.updateViewLayout(view, params)
            windowParams = params
            mountSurface(view.candidateContainer)
        } catch (exception: WindowManager.BadTokenException) {
            mountSurface(null)
            Timber.w(exception, "Composing guide lost its IME window")
        } catch (exception: IllegalArgumentException) {
            mountSurface(null)
            Timber.w(exception, "Composing guide host was detached")
        }
    }
}
