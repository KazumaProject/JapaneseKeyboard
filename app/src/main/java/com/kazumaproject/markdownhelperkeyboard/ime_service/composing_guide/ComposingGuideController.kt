package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.preference.PreferenceManager

/** Coordinates mutually exclusive hosts of the single candidate surface. */
internal class ComposingGuideController(
    context: Context,
    eligible: () -> Boolean,
    private val onStateChanged: () -> Unit,
    onSurfaceChanged: (LinearLayout?) -> Unit,
    colors: () -> CandidatePanelColors,
    minimumCandidateHeight: () -> Int,
) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val settings = ComposingGuideSettings(preferences)
    private var anchor: View? = null
    private var content = ComposingGuideContent()
    private var activeProfiles = emptyList<GuideProfile>()
    private var refreshing = false
    private var touchOwner: ComposingGuideWindow? = null
    private val windows = mutableMapOf<GuideProfile, ComposingGuideWindow>()
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key.startsWith("composing_guide_")) anchor?.post { refresh(); onStateChanged() }
    }

    init {
        GuideProfile.entries.forEach { profile ->
            windows[profile] = ComposingGuideWindow(context, eligible,
                onSurfaceChanged, colors, minimumCandidateHeight, profile,
                candidateBounds = { windows[GuideProfile.CANDIDATES]?.currentBounds() })
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun start(view: View) { stop(); anchor = view; refresh() }

    fun update(value: CharSequence?, reading: String = "", liveConversion: Boolean = false) {
        content = if (value.isNullOrEmpty()) ComposingGuideContent() else ComposingGuideContent(value.toString(), reading, liveConversion)
        refresh()
    }

    fun refresh() {
        val host = anchor ?: return
        if (refreshing) return
        refreshing = true
        try {
            val next = settings.profiles
            // All outgoing candidate hosts detach before an incoming host can attach.
            (activeProfiles - next.toSet()).forEach { windows.getValue(it).stop() }
            (next - activeProfiles.toSet()).forEach { windows.getValue(it).start(host, content) }
            activeProfiles = next
            next.forEach { windows.getValue(it).update(content.text, content.reading, content.liveConversion) }
        } finally { refreshing = false }
    }

    fun stop() {
        activeProfiles.forEach { windows.getValue(it).stop() }
        activeProfiles = emptyList()
        anchor = null
        content = ComposingGuideContent()
        // Keep an in-flight transferred gesture captured until UP/CANCEL, even after dismissal.
    }

    fun destroy() { stop(); windows.values.forEach { it.destroy() }; preferences.unregisterOnSharedPreferenceChangeListener(listener) }

    fun dispatchInputWindowTouch(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            touchOwner = activeProfiles.asReversed().map(windows::getValue)
                .firstOrNull { it.dispatchInputWindowTouch(event) }
            return touchOwner != null
        }
        val owner = touchOwner ?: return false
        owner.dispatchInputWindowTouch(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) touchOwner = null
        return true
    }
}

/** Try above, below, left and right before using a clamped offset. */
internal fun placeTextGuide(reference: GuideBounds, area: GuideBounds, height: Int, gap: Int): GuideBounds {
    val width = reference.width.coerceAtMost(area.width)
    val safeHeight = height.coerceAtMost(area.height)
    val choices = listOf(
        GuideBounds(reference.x, reference.y - safeHeight - gap, width, safeHeight),
        GuideBounds(reference.x, reference.bottom + gap, width, safeHeight),
        GuideBounds(reference.x - width - gap, reference.y, width, safeHeight),
        GuideBounds(reference.right + gap, reference.y, width, safeHeight),
    )
    return choices.firstOrNull { it.x >= area.x && it.y >= area.y && it.right <= area.right && it.bottom <= area.bottom }
        ?: GuideBounds((reference.x + gap).coerceIn(area.x, area.right - width),
            (reference.y - safeHeight - gap).coerceIn(area.y, area.bottom - safeHeight), width, safeHeight)
}
