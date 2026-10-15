package com.kazumaproject.markdownhelperkeyboard.ime_service.bunsetsu_diff

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.CursorAnchorInfo
import android.widget.TextView
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryResult
import com.kazumaproject.markdownhelperkeyboard.ime_service.BunsetsuSegmentState
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidatePanelColors
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.FloatingWindowCoordinates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/** Independent IME-owned bubbles. All entry points run on the main dispatcher. */
internal class BunsetsuDiffController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val eligible: () -> Boolean,
    private val query: suspend (String) -> KanaKanjiQueryResult,
    private val colors: () -> CandidatePanelColors,
    private val requestGeometry: (Boolean) -> Unit,
    private val onCycle: (BunsetsuDiffSnapshot, Int) -> Boolean,
    private val excludedBounds: () -> List<Rect> = { emptyList() },
    private val candidatePlacement: () -> Boolean = { false },
    private val candidateAnchors: (BunsetsuDiffSnapshot, String?) -> BunsetsuDiffCandidateAnchors? = { _, _ -> null },
    private val allowedSegment: (String, String) -> Boolean = { _, _ -> true },
) {
    companion object {
        const val ENABLED = "bunsetsu_diff_floating_enabled"
        const val PLACEMENT = "bunsetsu_diff_floating_placement"
        const val CANDIDATES = "candidates"
    }

    private val manager = context.getSystemService(WindowManager::class.java)
    private val coordinates = FloatingWindowCoordinates(manager)
    private var host: View? = null
    private var job: Job? = null
    private var generation = 0L
    private var reading = ""
    private var text = ""
    private var snapshot: BunsetsuDiffSnapshot? = null
    private var info: CursorAnchorInfo? = null
    private var frozen = false
    private var referenceCandidate: String? = null
    private var lastArea: Rect? = null
    private var lastOrigin: RectF? = null
    private val bubbles = linkedMapOf<Int, Bubble>()
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (candidatePlacement()) refresh()
        true
    }
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { refresh() }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    fun start(view: View) {
        if (host === view) return
        stop()
        host = view
        view.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
    }

    fun update(nextReading: String, nextText: String) {
        if (!eligible() || nextReading.isEmpty() || nextText.isEmpty()) { clear(); return }
        if (reading == nextReading && text == nextText) { refresh(); return }
        clear()
        reading = nextReading
        text = nextText
        val token = generation
        requestGeometry(!candidatePlacement())
        job = scope.launch {
            delay(120)
            try {
                val result = query(nextReading)
                if (generation != token || !eligible() || frozen) return@launch
                snapshot = buildBunsetsuDiffSnapshot(nextReading, nextText, result, allowedSegment)
                refresh()
                requestGeometry(!candidatePlacement() && snapshot?.differingIndices?.isNotEmpty() == true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.w(error, "Bunsetsu difference preview unavailable")
                if (generation == token) clear()
            }
        }
    }

    fun updateSelection(segments: List<BunsetsuSegmentState>) {
        val current = snapshot ?: return
        if (segments.map { it.reading } != current.segments.map { it.reading }) { clear(); return }
        frozen = true
        snapshot = current.copy(segments = segments)
        text = snapshot!!.text
        refresh()
        requestGeometry(!candidatePlacement())
    }

    fun onAnchor(value: CursorAnchorInfo?) {
        if (!eligible()) { clear(); return }
        if (candidatePlacement()) return
        if (value == null || value.composingTextStart < 0 || value.composingText.isNullOrEmpty()) {
            info = null
            dismissBubbles()
            return
        }
        // A reply queued before our last composing write cannot position the new text.
        if (value.composingText.toString() != text) return
        info = value
        refresh()
    }

    fun clear() {
        generation++
        job?.cancel(); job = null
        snapshot = null
        info = null
        reading = ""; text = ""
        frozen = false
        referenceCandidate = null
        lastArea = null; lastOrigin = null
        dismissBubbles()
        requestGeometry(false)
    }

    fun onPlacementChanged() {
        dismissBubbles()
        referenceCandidate = null
        lastArea = null; lastOrigin = null
        requestGeometry(!candidatePlacement() && snapshot != null)
        refresh()
    }

    fun stop() {
        clear()
        host?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
        host?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDrawListener)
        host = null
    }

    private fun dismissBubbles() {
        bubbles.values.forEach { it.dismiss() }
        bubbles.clear()
    }

    private fun refresh() {
        if (!eligible()) { clear(); return }
        val view = host?.takeIf { it.isAttachedToWindow } ?: return
        val current = snapshot ?: return
        val inCandidates = candidatePlacement()
        val candidateGeometry = if (inCandidates) candidateAnchors(current, referenceCandidate.takeIf { frozen }) else null
        if (inCandidates && candidateGeometry == null) { dismissBubbles(); return }
        if (candidateGeometry != null) referenceCandidate = candidateGeometry.candidate
        val anchors = if (inCandidates) candidateGeometry?.bounds else info?.let { bunsetsuDiffAnchors(it, current) }
        if (anchors == null) {
            // An owned candidate change can retain the frozen placements until its reply arrives.
            if (frozen) bubbles.forEach { (index, bubble) -> bubble.setContent(current.segments[index]) }
            else dismissBubbles()
            return
        }
        val area = coordinates.safeArea(view)
        val keyboardTop = IntArray(2).also(view::getLocationOnScreen)[1]
        if (inCandidates) {
            area.bottom = minOf(area.bottom, anchors.filterNotNull().maxOfOrNull { it.bottom.toInt() + 1 } ?: area.bottom)
        } else if (keyboardTop > area.top) area.bottom = minOf(area.bottom, keyboardTop)
        val origin = anchors.firstOrNull()
        // Scrolling/rotation invalidates positions, but never changes a locked candidate list.
        val geometryChanged = lastArea != area || (lastOrigin != null && origin != null &&
            (origin.left != lastOrigin!!.left || origin.top != lastOrigin!!.top))
        if (geometryChanged) bubbles.values.forEach { it.bounds = null }
        lastArea = Rect(area)
        lastOrigin = origin?.let(::RectF)
        val occupied = excludedBounds().toMutableList()
        anchors.filterNotNull().forEach { r -> occupied.add(Rect(r.left.toInt(), r.top.toInt(), r.right.toInt() + 1, r.bottom.toInt() + 1)) }
        val visible = mutableSetOf<Int>()
        for (index in current.differingIndices) {
            val anchor = anchors[index] ?: continue
            val bubble = bubbles.getOrPut(index) { Bubble(index, generation) }
            bubble.setContent(current.segments[index])
            val maxWidth = minOf(dp(300), area.width())
            if (maxWidth <= 0 || area.height() <= 0) continue
            bubble.view.measure(View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val fixed = bubble.bounds?.takeIf { frozen }
            val bounds = if (fixed != null) {
                // Keep the target still while choosing. Hide it if a reflow covers its old spot.
                fixed.takeIf { area.contains(it) && isBunsetsuDiffBubbleAdjacent(it, anchor, dp(8)) &&
                    occupied.none { other -> Rect.intersects(it, other) } }
            } else placeBunsetsuDiffBubble(anchor, bubble.view.measuredWidth, bubble.view.measuredHeight, area, occupied, dp(8), aboveOnly = inCandidates)
            if (bounds == null) { bubble.dismiss(); continue }
            bubble.show(bounds, view)
            occupied.add(bounds)
            visible.add(index)
        }
        (bubbles.keys - visible).forEach { bubbles[it]?.dismiss() }
    }

    private inner class Bubble(private val index: Int, private val token: Long) {
        var bounds: Rect? = null
        private var params: WindowManager.LayoutParams? = null
        val view = TextView(context).apply {
            layoutParams = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
            id = R.id.bunsetsu_diff_candidate
            textSize = 16f
            minHeight = dp(48)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN && token == generation) frozen = true
                false
            }
            setOnClickListener {
                val current = snapshot
                if (token == generation && eligible() && current != null) {
                    frozen = true
                    onCycle(current, index)
                }
            }
        }

        init { coordinates.observeLegacyOrigin(view, { params }, ::refresh) }

        private var content: Pair<BunsetsuSegmentState, CandidatePanelColors>? = null
        fun setContent(segment: BunsetsuSegmentState) {
            val palette = colors()
            if (content == (segment to palette)) return
            content = segment to palette
            val label = SpannableStringBuilder()
            segment.candidates.forEachIndexed { i, candidate ->
                if (i > 0) label.append("\n")
                val start = label.length
                label.append(candidate.string)
                if (i == segment.selectedIndex) {
                    label.setSpan(BackgroundColorSpan(palette.selection), start, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    label.setSpan(ForegroundColorSpan(palette.selectionText), start, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

            }
            view.text = label
            view.contentDescription = context.getString(R.string.bunsetsu_diff_cycle, segment.displayText, label.toString())
            view.setTextColor(palette.text)
            view.background = GradientDrawable().apply {
                setColor(palette.background)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1).coerceAtLeast(1), palette.icon)
            }
            KeyboardFontApplicator.apply(view, KeyboardFontApplicator.processSnapshot)
        }

        fun show(rect: Rect, host: View) {
            if (params != null && bounds == rect) return
            bounds = Rect(rect)
            val p = params ?: WindowManager.LayoutParams(rect.width(), rect.height(),
                WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                token = host.windowToken
                setTitle("Bunsetsu difference")
                coordinates.configure(this)
            }
            p.width = rect.width(); p.height = rect.height()
            coordinates.position(p, rect.left, rect.top)
            try {
                if (params == null) manager.addView(view, p) else manager.updateViewLayout(view, p)
                params = p
            } catch (error: WindowManager.BadTokenException) {
                Timber.w(error, "Difference bubble lost its IME token")
                dismiss()
            } catch (error: IllegalArgumentException) {
                Timber.w(error, "Difference bubble host was detached")
                dismiss()
            }
        }

        fun dismiss() {
            if (params != null) runCatching { manager.removeViewImmediate(view) }
            params = null
        }
    }
}
