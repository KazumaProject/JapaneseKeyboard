package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/** Moves the existing candidate views, retaining their adapters, listeners and autofill children. */
internal class CandidateSurfaceHost(
    private val toolbar: View,
    private val tabs: View,
    private val strip: View,
    private val candidates: View,
    private val fullCandidates: View,
) {
    private data class Origin(val view: View, val parent: ViewGroup, val index: Int, val params: ViewGroup.LayoutParams)
    private var origins = emptyList<Origin>()
    private var backgrounds = emptyList<Pair<View, android.graphics.drawable.Drawable?>>()
    private var colors = CandidatePanelColors.resolve(tabs.context)
    fun setColors(value: CandidatePanelColors) { colors = value; refreshAppearance() }
    private var tabViews = emptyList<View?>()
    private var tabPadding = emptyList<Pair<View, android.graphics.Rect>>()
    private var scrollbars = false to false
    private var tabIndicator: android.graphics.drawable.Drawable? = null
    private val spacing = object : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: androidx.recyclerview.widget.RecyclerView, state: androidx.recyclerview.widget.RecyclerView.State) {
            val gap = (4 * view.resources.displayMetrics.density).toInt()
            outRect.set(gap, gap, gap, gap)
        }
    }

    private fun styleSurface() {
        scrollbars = candidates.isVerticalScrollBarEnabled to candidates.isHorizontalScrollBarEnabled
        backgrounds = listOf(toolbar, tabs, strip).map { it to it.background }
        (candidates as? androidx.recyclerview.widget.RecyclerView)?.addItemDecoration(spacing)
        (tabs as? com.google.android.material.tabs.TabLayout)?.let { layout ->
            tabIndicator = layout.tabSelectedIndicator
            tabViews = (0 until layout.tabCount).map { layout.getTabAt(it)?.customView }
        }
        refreshAppearance()
    }

    fun refreshAppearance() {
        if (!attached) return
        backgrounds.forEach { (view, _) ->
            if ((view.background as? android.graphics.drawable.ColorDrawable)?.color != android.graphics.Color.TRANSPARENT) view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        val tabLayout = tabs as? com.google.android.material.tabs.TabLayout ?: return
        val ink = colors.text
        val accent = colors.selection
        if ((tabLayout.tabSelectedIndicator as? android.graphics.drawable.ColorDrawable)?.color != android.graphics.Color.TRANSPARENT) tabLayout.setSelectedTabIndicator(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        val tabStrip = tabLayout.getChildAt(0) as? ViewGroup
        if (tabStrip != null) for (index in 0 until tabStrip.childCount) {
            val view = tabStrip.getChildAt(index)
            if (tabPadding.none { it.first === view }) tabPadding = tabPadding + (view to android.graphics.Rect(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom))
            val padding = (4 * view.resources.displayMetrics.density).toInt()
            if (view.paddingLeft != padding || view.paddingRight != padding) view.setPadding(padding, 0, padding, 0)
        }
        for (index in 0 until tabLayout.tabCount) {
            val tab = tabLayout.getTabAt(index) ?: continue
            if (tab.customView?.tag == colors) continue
            tab.customView = android.widget.TextView(tabs.context).apply {
                tag = colors
                text = tab.text
                textSize = 13f
                maxLines = 1
                setHorizontallyScrolling(false)
                includeFontPadding = false
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 10, 13, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
                gravity = android.view.Gravity.CENTER
                setPadding(0, 0, 0, 0)
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                setTextColor(android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()), intArrayOf(colors.selectionText, ink)))
                background = android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_selected), android.graphics.drawable.GradientDrawable().apply { setColor(accent); cornerRadius = 10 * resources.displayMetrics.density })
                    addState(intArrayOf(), android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                }
            }
        }
    }

    private fun restoreSurface() {
        candidates.isVerticalScrollBarEnabled = scrollbars.first
        candidates.isHorizontalScrollBarEnabled = scrollbars.second
        (candidates as? androidx.recyclerview.widget.RecyclerView)?.removeItemDecoration(spacing)
        backgrounds.forEach { (view, background) -> view.background = background }
        (tabs as? com.google.android.material.tabs.TabLayout)?.let { layout ->
            tabViews.forEachIndexed { index, view -> layout.getTabAt(index)?.customView = view }
            layout.setSelectedTabIndicator(tabIndicator)
        }
        tabPadding.forEach { (view, padding) -> view.setPadding(padding.left, padding.top, padding.right, padding.bottom) }
        tabPadding = emptyList()
        backgrounds = emptyList()
        tabViews = emptyList()
    }
    private var originalCandidateHeight = ViewGroup.LayoutParams.WRAP_CONTENT
    var attached = false
        private set
    var expanded = false
        private set

    fun attach(target: LinearLayout) {
        if (attached) return
        originalCandidateHeight = candidates.layoutParams.height
        val views = listOf(toolbar, tabs, strip, fullCandidates)
        origins = views.map { Origin(it, it.parent as ViewGroup, (it.parent as ViewGroup).indexOfChild(it), it.layoutParams) }
        views.forEach { (it.parent as ViewGroup).removeView(it) }
        views.forEach { view ->
            target.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                if (view == strip || view == fullCandidates) 0 else view.layoutParams.height,
                if (view == strip) 1f else 0f))
        }
        attached = true
        styleSurface()
        setExpanded(false)
    }

    fun setExpanded(value: Boolean) {
        if (!attached) return
        expanded = value
        fullCandidates.animate().cancel()
        fullCandidates.translationY = 0f
        fullCandidates.visibility = if (value) View.VISIBLE else View.GONE
        candidates.visibility = if (value) View.INVISIBLE else View.VISIBLE
        val toggleHeight = (48 * strip.resources.displayMetrics.density).toInt()
        strip.layoutParams = LinearLayout.LayoutParams(-1, if (value) toggleHeight else 0, if (value) 0f else 1f)
        fullCandidates.layoutParams = LinearLayout.LayoutParams(-1, 0, if (value) 1f else 0f)
    }

    fun detach() {
        if (!attached) return
        setExpanded(false)
        restoreSurface()
        origins.forEach { (it.view.parent as? ViewGroup)?.removeView(it.view) }
        origins.sortedBy { it.index }.forEach { origin ->
            origin.parent.addView(origin.view, origin.index.coerceAtMost(origin.parent.childCount), origin.params)
        }
        candidates.layoutParams = candidates.layoutParams.apply { height = originalCandidateHeight }
        origins = emptyList()
        attached = false
    }
}

/** The bundled Flexbox manager needs conversion for holders recycled from a linear strip. */
internal fun candidatePanelLayoutManager(context: android.content.Context, vertical: Boolean): androidx.recyclerview.widget.RecyclerView.LayoutManager =
    if (vertical) object : com.kazumaproject.android.flexbox.FlexboxLayoutManager(context) {
        init {
            flexDirection = com.kazumaproject.android.flexbox.FlexDirection.ROW
            flexWrap = com.kazumaproject.android.flexbox.FlexWrap.WRAP
            justifyContent = com.kazumaproject.android.flexbox.JustifyContent.FLEX_START
        }
        override fun generateLayoutParams(params: ViewGroup.LayoutParams): androidx.recyclerview.widget.RecyclerView.LayoutParams =
            com.kazumaproject.android.flexbox.FlexboxLayoutManager.LayoutParams(params)
    } else androidx.recyclerview.widget.LinearLayoutManager(context, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
