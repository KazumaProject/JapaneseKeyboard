package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.content.Context
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
internal class ComposingGuideWindow(
    private val context: Context,
    private val eligible: () -> Boolean,
    private val onSurfaceChanged: (LinearLayout?) -> Unit,
    private val colors: () -> CandidatePanelColors,
    private val minimumCandidateHeight: () -> Int,
    private val profile: GuideProfile = GuideProfile.INTEGRATED,
    private val candidateBounds: () -> GuideBounds? = { null },
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
    private var routingInputWindowTouch = false
    private var lastMinimumHeight = 0
    private var mountedSurface: LinearLayout? = null
    private val minimumContentHeight get(): Int {
        val size = previewTextSize ?: settings.textSize
        val textHeight = if (profile.hasText) ComposingGuideView.composingLineHeight(context, if (content.text.isEmpty()) 14f else size) + dp(4) else 0
        val readingHeight = if (content.visibleReading(profile.hasText, settings.showReading).isNotEmpty())
            ComposingGuideView.readingLineHeight(context, size) + dp(4) else 0
        return kotlin.math.ceil((dp(60) + textHeight + readingHeight + (if (profile.hasCandidates) minimumCandidateHeight() else 0)) / density).toInt()
    }
    private val density get() = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()
    private val editExtraDp get() = if (profile.hasText) ComposingGuideView.EDIT_EXTRA_DP else 44
    private val extra get() = dp(ComposingGuideView.MOVE_BAND_DP) +
        if (editing) dp(editExtraDp) else 0
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { refresh() }
    fun start(view: View, initialContent: ComposingGuideContent = ComposingGuideContent()) {
        stop()
        content = initialContent
        anchor = view
        active = true
        view.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        refresh()
    }

    fun update(value: CharSequence?, reading: String = "", liveConversion: Boolean = false) {
        content = if (value.isNullOrEmpty()) ComposingGuideContent() else ComposingGuideContent(value.toString(), reading, liveConversion)
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
    }

    fun destroy() { stop(); guideView = null }

    fun refresh() {
        val allowed = profile in settings.profiles && eligible()
        val host = anchor
        if (!active || !allowed || host?.isAttachedToWindow != true) {
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
        if (profile != GuideProfile.INTEGRATED) settings.prepareCandidatePlacement(landscape)
        if (area.width() < dp(ComposingGuidePlacement.MIN_WIDTH_DP) || area.height() < dp(minimumContentHeight + ComposingGuideView.MOVE_BAND_DP)) { dismiss(); return }
        if (editing && area.height() < dp(minimumContentHeight + ComposingGuideView.MOVE_BAND_DP + editExtraDp)) leaveEditing()
        val view = guideView ?: ComposingGuideView(context,
            onEdit = ::toggleEditing,
            onTextSize = { size, commit ->
                previewTextSize = size
                if (commit) { settings.textSize = size; previewTextSize = null }
                refresh()
            },
            onHandleEvent = ::handleEvent,
        ).also { view ->
            guideView = view
            val minimumLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
                val minimum = minimumContentHeight
                if (minimum != lastMinimumHeight) {
                    lastMinimumHeight = minimum
                    view.post { refresh() }
                }
            }
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    lastMinimumHeight = 0
                    v.viewTreeObserver.addOnGlobalLayoutListener(minimumLayoutListener)
                }
                override fun onViewDetachedFromWindow(v: View) {
                    v.viewTreeObserver.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(minimumLayoutListener)
                }
            })
        }
        view.setColors(colors())
        if (view.editing != editing) view.setEditing(editing)
        view.setEditAvailable(editing || area.height() >= dp(minimumContentHeight + ComposingGuideView.MOVE_BAND_DP + editExtraDp))
        view.setShowComposing(profile.hasText)
        view.candidateContainer.visibility = if (profile.hasCandidates) View.VISIBLE else View.GONE
        view.setContent(content.text, previewTextSize ?: settings.textSize, content.visibleReading(profile.hasText, settings.showReading))
        if ((!editing && gesture == null) || bounds == null) {
            var placement = settings.load(landscape, profile)
            if (profile == GuideProfile.TEXT && !settings.usesScreenCoordinates(landscape, profile)) {
                val reference = candidateBounds() ?: run {
                    val referenceArea = if (settings.usesScreenCoordinates(landscape, GuideProfile.CANDIDATES)) area else legacyAvailableArea(host)
                    settings.load(landscape, GuideProfile.CANDIDATES).resolve(
                        referenceArea.left, referenceArea.top, referenceArea.width().coerceAtLeast(1),
                        (referenceArea.height() - dp(ComposingGuideView.MOVE_BAND_DP)).coerceAtLeast(1), density)
                        .let { it.copy(height = it.height + dp(ComposingGuideView.MOVE_BAND_DP)) }
                }
                val totalHeight = dp(minimumContentHeight + ComposingGuideView.MOVE_BAND_DP)
                val initial = placeTextGuide(reference, GuideBounds(area.left, area.top, area.width(), area.height()), totalHeight, dp(8))
                placement = ComposingGuidePlacement(
                    (initial.x - area.left).toFloat() / (area.width() - initial.width).coerceAtLeast(1),
                    (initial.y - area.top).toFloat() / (area.height() - initial.height).coerceAtLeast(1),
                    initial.width / density, minimumContentHeight.toFloat())
                settings.save(landscape, placement, profile)
            }
            if (!settings.usesScreenCoordinates(landscape, profile)) {
                val legacy = legacyAvailableArea(host)
                val oldArea = GuideBounds(legacy.left, legacy.top, legacy.width().coerceAtLeast(1), (legacy.height() - dp(ComposingGuideView.MOVE_BAND_DP)).coerceAtLeast(1))
                val newArea = GuideBounds(area.left, area.top, area.width(), (area.height() - dp(ComposingGuideView.MOVE_BAND_DP)).coerceAtLeast(1))
                val previousMinimum = if (profile.hasText) 232f else 168f
                placement = placement.copy(heightDp = placement.heightDp.coerceAtLeast(previousMinimum))
                    .rebase(oldArea, newArea, density, minimumContentHeight.toFloat())
                settings.save(landscape, placement, profile)
            }
            val normal = placement.copy(heightDp = placement.heightDp.coerceAtLeast(minimumContentHeight.toFloat())).resolve(area.left, area.top, area.width(), (area.height() - dp(ComposingGuideView.MOVE_BAND_DP)).coerceAtLeast(1), density, minimumContentHeight.toFloat())
            val height = (normal.height + extra).coerceAtMost(area.height())
            bounds = normal.copy(y = normal.y.coerceAtMost(area.bottom - height), height = height)
        }
        bounds = bounds?.let { current ->
            val height = current.height.coerceAtLeast(dp(minimumContentHeight) + extra).coerceAtMost(area.height())
            current.copy(height = height, y = current.y.coerceIn(area.top, area.bottom - height))
        }
        bounds?.let { updateWindow(it, host) }
    }

    /** InlineContentView transfers swipe focus to the IME window, not its hosting panel. */
    fun dispatchInputWindowTouch(event: MotionEvent): Boolean {
        val view = guideView?.takeIf { it.isAttachedToWindow && it.isShown }
        if (view == null && !routingInputWindowTouch) return false
        val location = IntArray(2)
        view?.getLocationOnScreen(location)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            routingInputWindowTouch = view != null && event.rawX >= location[0] &&
                event.rawX < location[0] + view.width && event.rawY >= location[1] &&
                event.rawY < location[1] + view.height
        }
        if (!routingInputWindowTouch) return false
        if (view != null) {
            val translated = MotionEvent.obtain(event)
            translated.offsetLocation(event.rawX - event.x - location[0], event.rawY - event.y - location[1])
            try { view.dispatchTouchEvent(translated) } finally { translated.recycle() }
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            routingInputWindowTouch = false
        }
        return true
    }

    fun currentBounds(): GuideBounds? = bounds?.takeIf { guideView?.isAttachedToWindow == true }

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
        else if (area.height() >= dp(minimumContentHeight + ComposingGuideView.MOVE_BAND_DP + editExtraDp)) { editing = true; bounds = null }
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
                    GuideBounds(area.left, area.top, area.width(), area.height()), dp(ComposingGuidePlacement.MIN_WIDTH_DP), dp(minimumContentHeight) + extra)
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
        ), profile)
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
            if (profile == GuideProfile.TEXT) WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL else WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { token = host.windowToken; gravity = Gravity.TOP or Gravity.LEFT; setTitle("Floating guide: ${profile.key}") }
        params.x = target.x; params.y = target.y; params.width = target.width; params.height = target.height
        try {
            if (view.parent == null) windowManager.addView(view, params) else windowManager.updateViewLayout(view, params)
            windowParams = params
            if (profile.hasCandidates) mountSurface(view.candidateContainer)
        } catch (exception: WindowManager.BadTokenException) {
            mountSurface(null)
            Timber.w(exception, "Composing guide lost its IME window")
        } catch (exception: IllegalArgumentException) {
            mountSurface(null)
            Timber.w(exception, "Composing guide host was detached")
        }
    }
}
