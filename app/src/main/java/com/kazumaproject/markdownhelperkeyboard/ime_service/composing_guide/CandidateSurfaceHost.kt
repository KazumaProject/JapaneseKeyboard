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
