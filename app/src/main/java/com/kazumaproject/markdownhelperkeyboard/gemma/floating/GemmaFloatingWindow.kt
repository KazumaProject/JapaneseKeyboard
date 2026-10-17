package com.kazumaproject.markdownhelperkeyboard.gemma.floating

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidatePanelColors
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.FloatingWindowCoordinates
import timber.log.Timber

/** Candidate-strip coordinates work even when the editor does not expose character bounds. */
internal class GemmaFloatingWindow(
    private val context: Context,
    private val anchor: () -> View?,
    private val eligible: () -> Boolean,
    private val colors: () -> CandidatePanelColors,
    private val validInput: (GemmaFloatingInput) -> Boolean,
    private val commit: (GemmaFloatingInput, String) -> Boolean,
) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val coordinates = FloatingWindowCoordinates(manager)
    private var host: View? = null
    private var surface: ScrollView? = null
    private var registered = false
    private var label: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var lastColors: CandidatePanelColors? = null
    private data class Content(val input: GemmaFloatingInput, val text: String, val processing: Boolean)
    private var result: Content? = null
    private val layoutListener = ViewTreeObserver.OnPreDrawListener { refresh(); true }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    fun show(input: GemmaFloatingInput?, text: String?) {
        if (input == null || text.isNullOrBlank()) { clear(); return }
        showContent(Content(input, text, processing = false))
    }

    fun showProcessing(input: GemmaFloatingInput) {
        showContent(Content(input, "…", processing = true))
    }

    private fun showContent(content: Content) {
        result = content
        val next = anchor()
        if (host !== next) {
            detach()
            host = next
            next?.viewTreeObserver?.addOnPreDrawListener(layoutListener)
        }
        refresh()
    }

    fun clear() {
        result = null
        detach()
    }

    private fun detach() {
        host?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnPreDrawListener(layoutListener)
        host = null
        dismiss()
    }

    private fun dismiss() {
        // WindowManager owns a view as soon as addView returns, before it is attached.
        // Always attempt removal, including a failed or not-yet-attached registration.
        surface?.let { view -> runCatching { manager.removeViewImmediate(view) } }
        registered = false
        surface = null; label = null; params = null; lastColors = null
    }

    private fun refresh() {
        val content = result ?: return
        if (!validInput(content.input)) { clear(); return }
        val view = anchor()
        if (!eligible() || view == null || !view.isShown || !view.isAttachedToWindow || view.windowToken == null) {
            dismiss(); return
        }
        if (view !== host) {
            detach()
            host = view
            view.viewTreeObserver.addOnPreDrawListener(layoutListener)
        }
        val area = coordinates.safeArea(view)
        val location = IntArray(2).also(view::getLocationOnScreen)
        val visible = Rect()
        if (!view.getGlobalVisibleRect(visible) || area.isEmpty) { dismiss(); return }
        val availableHeight = minOf(dp(180), location[1] - dp(8) - area.top)
        if (availableHeight < dp(40)) { dismiss(); return }
        val panelColors = colors()
        val panel = surface ?: ScrollView(context).apply {
            isFillViewport = false
            clipToOutline = true
            elevation = dp(8).toFloat()
            addView(TextView(context).apply {
                id = R.id.gemma_floating_result
                textSize = 18f
                minHeight = dp(48)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                KeyboardFontApplicator.apply(this, KeyboardFontApplicator.processSnapshot)
                setOnClickListener {
                    result?.let { if (!it.processing && commit(it.input, it.text)) clear() }
                }
                label = this
            })
            surface = this
        }
        label?.apply {
            if (text.toString() != content.text) { text = content.text; panel.scrollTo(0, 0) }
            isClickable = !content.processing
            contentDescription = if (content.processing) context.getString(R.string.gemma_floating_processing) else null
        }
        if (lastColors != panelColors) {
            lastColors = panelColors
            label?.setTextColor(panelColors.text)
            panel.background = GradientDrawable().apply {
                setColor(panelColors.background)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), panelColors.icon)
            }
        }
        val maxWidth = minOf(dp(320), area.width(), view.width.coerceAtLeast(dp(160)))
        panel.minimumWidth = minOf(dp(96), maxWidth)
        panel.measure(View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(availableHeight, View.MeasureSpec.AT_MOST))
        val width = panel.measuredWidth
        val left = location[0].coerceIn(area.left, area.right - width)
        val top = location[1] - dp(8) - panel.measuredHeight
        val layout = params ?: WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            gravity = Gravity.TOP or Gravity.LEFT
            token = view.windowToken
            setTitle("Gemma floating result")
            coordinates.configure(this)
        }.also { params = it }
        val previousX = layout.x; val previousY = layout.y
        val previousWidth = layout.width; val previousHeight = layout.height
        layout.width = width; layout.height = panel.measuredHeight
        coordinates.position(layout, left, top)
        val changed = layout.width != previousWidth || layout.height != previousHeight ||
            layout.x != previousX || layout.y != previousY
        try {
            if (!registered) {
                manager.addView(panel, layout)
                registered = true
            }
            else if (changed) manager.updateViewLayout(panel, layout)
        } catch (error: Exception) {
            Timber.w(error, "Gemma floating placement unavailable")
            dismiss()
        }
    }
}
