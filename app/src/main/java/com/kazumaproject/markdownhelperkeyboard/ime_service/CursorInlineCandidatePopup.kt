package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.CursorAnchorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidatePanelColors
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.FloatingWindowCoordinates
import kotlin.math.roundToInt
import timber.log.Timber

internal fun CursorAnchorInfo.inlineCandidateAnchor(
    expectedComposingText: String,
    focusedSegment: IntRange?,
): PhysicalKeyboardCursorAnchor? {
    if (composingTextStart < 0 || composingText?.toString() != expectedComposingText ||
        selectionStart != selectionEnd || selectionStart !in composingTextStart..(composingTextStart + expectedComposingText.length)
    ) return null
    if (focusedSegment == null) return insertionMarkerAnchor()

    // Character indices in CursorAnchorInfo are UTF-16 offsets in the editor.
    for (offset in focusedSegment) {
        val index = composingTextStart + offset
        val bounds = getCharacterBounds(index) ?: continue
        val flags = getCharacterBoundsFlags(index)
        val x = if (flags and CursorAnchorInfo.FLAG_IS_RTL != 0) bounds.right else bounds.left
        screenCursorAnchor(x, bounds.top, bounds.bottom, flags)?.let { return it }
    }
    return insertionMarkerAnchor().takeIf { selectionStart == composingTextStart + focusedSegment.first }
}

internal fun CandidatePanelColors.withReadableSelectionText(): CandidatePanelColors {
    val panel = ColorUtils.setAlphaComponent(background, 255)
    val fill = ColorUtils.compositeColors(selection, panel)
    if (ColorUtils.calculateContrast(selectionText, fill) >= 4.5) return this

    val label = when {
        ColorUtils.calculateContrast(text, fill) >= 4.5 -> text
        ColorUtils.calculateLuminance(fill) > 0.179 -> Color.BLACK
        else -> Color.WHITE
    }
    return copy(selectionText = label)
}

/** Keeps the editor focused and lets touches outside the measured panel reach the editor or IME. */
internal class CursorInlineCandidatePopup(
    context: Context,
    private val onCandidate: (Int) -> Unit,
    onMoveSegment: (Int) -> Unit,
    refreshPosition: () -> Unit,
) {
    data class Content(
        val labels: List<String>,
        val selected: Int,
        val canMoveBack: Boolean,
        val canMoveForward: Boolean,
        val textSize: Float,
        val colors: CandidatePanelColors,
    )

    private val manager = context.getSystemService(WindowManager::class.java)
    private val coordinates = FloatingWindowCoordinates(manager)
    private var params: WindowManager.LayoutParams? = null
    private var content: Content? = null
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()
    private val previous = segmentButton(context, "‹", R.string.cursor_inline_previous_segment) { onMoveSegment(-1) }
    private val next = segmentButton(context, "›", R.string.cursor_inline_next_segment) { onMoveSegment(1) }
    private val candidateAdapter = CandidateAdapter()
    private val candidates = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        adapter = candidateAdapter
        itemAnimator = null
        isHorizontalScrollBarEnabled = true
        overScrollMode = View.OVER_SCROLL_NEVER
    }
    private val panel = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        elevation = dp(6).toFloat()
        setPadding(dp(3), dp(3), dp(3), dp(3))
        addView(previous, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(candidates, LinearLayout.LayoutParams(0, dp(48), 1f))
        addView(next, LinearLayout.LayoutParams(dp(48), dp(48)))
    }

    fun submit(next: Content, resetScroll: Boolean) {
        val old = content
        if (next == old && !resetScroll) return
        content = next
        if (old?.colors != next.colors) {
            panel.background = shape(next.colors.background, 12).apply {
                setStroke(dp(1), next.colors.text and 0x33ffffff)
            }
        }
        val hasSegments = next.canMoveBack || next.canMoveForward
        for ((button, enabled) in listOf(previous to next.canMoveBack, this.next to next.canMoveForward)) {
            button.visibility = if (hasSegments) View.VISIBLE else View.GONE
            button.isEnabled = enabled
            button.alpha = if (enabled) 1f else 0.35f
            button.setTextColor(next.colors.icon)
            button.background = ripple(next.colors.pressed, next.colors.background)
        }
        candidateAdapter.notifyDataSetChanged()
        if (resetScroll || old?.selected != next.selected) {
            candidates.scrollToPosition(next.selected.coerceAtLeast(0))
        }
    }

    fun position(host: View, anchor: PhysicalKeyboardCursorAnchor, keyboardBounds: Rect, floating: Boolean): Boolean {
        val area = coordinates.safeArea(host)
        if (!floating) area.bottom = minOf(area.bottom, keyboardBounds.top)
        val width = minOf(dp(344), area.width() - dp(12))
        val minimumWidth = if (previous.visibility == View.VISIBLE) dp(150) else dp(80)
        if (width < minimumWidth) return false
        val height = dp(54)
        val gap = dp(6)
        fun place() = PhysicalKeyboardPopupPlacement.resolve(anchor, width, height, area, gap, height)?.let {
            it.copy(x = it.x.coerceIn(area.left + dp(6), area.right - dp(6) - width))
        }
        var position = place() ?: return false
        if (floating && Rect.intersects(keyboardBounds, Rect(position.x, position.y, position.x + width, position.y + height))) {
            if (anchor.bottom <= keyboardBounds.top) area.bottom = keyboardBounds.top
            else if (anchor.top >= keyboardBounds.bottom) area.top = keyboardBounds.bottom
            else return false
            position = place() ?: return false
        }

        val current = params
        val layout = current ?: WindowManager.LayoutParams(
            width, height, WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            token = host.windowToken
            gravity = Gravity.TOP or Gravity.LEFT
            setTitle("Inline conversion candidates")
            coordinates.configure(this)
        }
        val oldX = layout.x
        val oldY = layout.y
        val oldWidth = layout.width
        coordinates.position(layout, position.x, position.y)
        layout.width = width
        return runCatching {
            if (current == null) {
                manager.addView(panel, layout)
                params = layout
            } else if (oldX != layout.x || oldY != layout.y || oldWidth != width) {
                manager.updateViewLayout(panel, layout)
            }
            true
        }.onFailure { Timber.w(it, "Could not position inline conversion candidates") }.getOrDefault(false)
    }

    init {
        coordinates.observeLegacyOrigin(panel, { params }, refreshPosition)
    }

    fun dismiss() {
        if (params != null && panel.parent != null) manager.removeViewImmediate(panel)
        params = null
    }

    private fun shape(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun ripple(pressedColor: Int, surfaceColor: Int, outlineColor: Int? = null): RippleDrawable {
        val background = shape(surfaceColor, 8)
        outlineColor?.let { background.setStroke(dp(1), it) }
        return RippleDrawable(ColorStateList.valueOf(pressedColor), background, null)
    }

    private fun segmentButton(context: Context, glyph: String, description: Int, click: () -> Unit) =
        TextView(context).apply {
            text = glyph
            textSize = 26f
            gravity = Gravity.CENTER
            contentDescription = context.getString(description)
            setOnClickListener { click() }
        }

    private inner class CandidateAdapter : RecyclerView.Adapter<CandidateViewHolder>() {
        override fun getItemCount(): Int = content?.labels?.size ?: 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CandidateViewHolder {
            val label = TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.CENTER
                minWidth = dp(48)
                maxWidth = dp(220)
                setPadding(dp(12), 0, dp(12), 0)
                setSingleLine()
                ellipsize = TextUtils.TruncateAt.END
            }
            return CandidateViewHolder(label).also { holder ->
                label.setOnClickListener {
                    val index = holder.bindingAdapterPosition
                    if (index != RecyclerView.NO_POSITION) onCandidate(index)
                }
            }
        }

        override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
            val state = requireNotNull(content)
            val selected = position == state.selected
            val colors = state.colors
            holder.label.apply {
                text = state.labels[position]
                textSize = state.textSize
                isSelected = selected
                paint.isFakeBoldText = selected
                setTextColor(if (selected) colors.selectionText else colors.text)
                background = ripple(
                    pressedColor = colors.pressed,
                    surfaceColor = if (selected) colors.selection else colors.background,
                    outlineColor = colors.selectionText.takeIf { selected },
                )
                KeyboardFontApplicator.apply(this, KeyboardFontApplicator.processSnapshot)
            }
        }
    }

    private class CandidateViewHolder(val label: TextView) : RecyclerView.ViewHolder(label)
}
