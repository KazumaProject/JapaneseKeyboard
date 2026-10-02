package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import com.google.android.material.tabs.TabLayout
import java.util.WeakHashMap

/** Candidate surfaces follow the segmented gray controls in the iOS 6 Japanese keyboard. */
internal object CupertinoClassicCandidateChrome {
    const val panelColor: Int = 0xffc9cbd2.toInt()
    const val textColor: Int = 0xff25282d.toInt()
    const val dividerColor: Int = 0xff969ca6.toInt()
    const val candidatePressedColor: Int = 0xffb6bec9.toInt()
    const val selectedTabColor: Int = 0xff626975.toInt()
    const val minimumDockedStripHeightDp: Int = 58
    const val candidateDividerVerticalInsetDp: Int = 4

    fun resolveDockedStripHeightDp(configuredHeightDp: Int): Int =
        configuredHeightDp.coerceAtLeast(minimumDockedStripHeightDp)
    private const val panelBottom: Int = 0xffc9cbd2.toInt()
    private const val tabTop: Int = 0xfff0f0f2.toInt()
    private const val tabBottom: Int = 0xffc8cbd2.toInt()
    private const val tabSelectedTop: Int = 0xff858c98.toInt()
    private const val tabSelectedBottom: Int = selectedTabColor
    private const val tabEdge: Int = 0xff7d8490.toInt()
    private const val expandButtonPressedTop: Int = 0xffb6bec9.toInt()
    private const val expandButtonPressedBottom: Int = 0xff969ca6.toInt()

    private data class TabLayoutState(
        val mode: Int,
        val gravity: Int,
        val rippleColor: android.content.res.ColorStateList?,
        val indicator: android.graphics.drawable.Drawable?,
        val tabs: List<TabViewState>,
    )

    private data class TabViewState(
        val view: View,
        val background: android.graphics.drawable.Drawable?,
        val padding: android.graphics.Rect,
    )

    private val tabLayoutStates = WeakHashMap<TabLayout, TabLayoutState>()

    fun originalTabRippleColor(tabLayout: TabLayout): android.content.res.ColorStateList? =
        tabLayoutStates[tabLayout]?.rippleColor ?: tabLayout.tabRippleColor

    fun panelBackground(alpha: Int = 255) = ColorDrawable(panelColor).apply {
        this.alpha = alpha.coerceIn(0, 255)
    }

    fun toolbarBackground(resources: Resources) = gradient(tabTop, panelBottom).apply {
        setStroke(strokeWidth(resources), tabEdge)
    }

    fun tabsBackground() = ColorDrawable(panelColor)

    fun expandButtonBackground(resources: Resources) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), expandButtonDrawable(
            resources, expandButtonPressedTop, expandButtonPressedBottom
        ))
        addState(intArrayOf(android.R.attr.state_selected), expandButtonDrawable(
            resources, tabSelectedTop, tabSelectedBottom
        ))
        addState(intArrayOf(), expandButtonDrawable(resources, tabTop, tabBottom))
    }

    fun expandButtonTint() = android.content.res.ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
        intArrayOf(android.graphics.Color.WHITE, textColor),
    )

    private fun expandButtonDrawable(resources: Resources, top: Int, bottom: Int) =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16 * resources.displayMetrics.density
            setStroke(strokeWidth(resources), tabEdge)
        }

    fun applyTabs(tabLayout: TabLayout) {
        val resources = tabLayout.resources
        val strip = tabLayout.getChildAt(0) as? ViewGroup
        val original = tabLayoutStates.getOrPut(tabLayout) {
            TabLayoutState(
                mode = tabLayout.tabMode,
                gravity = tabLayout.tabGravity,
                rippleColor = tabLayout.tabRippleColor,
                indicator = tabLayout.tabSelectedIndicator,
                tabs = captureTabViews(strip),
            )
        }
        if (original.tabs.isEmpty() && strip != null && strip.childCount > 0) {
            // A cold input view can be styled before its tabs are created. Capture their
            // original ripple and padding once they exist, before applying Classic.
            tabLayout.tabRippleColor = original.rippleColor
            tabLayoutStates[tabLayout] = original.copy(tabs = captureTabViews(strip))
        }
        tabLayout.setTabTextColors(textColor, android.graphics.Color.WHITE)
        tabLayout.setSelectedTabIndicator(ColorDrawable(android.graphics.Color.TRANSPARENT))
        tabLayout.tabRippleColor = null
        tabLayout.tabMode = if (tabLayout.tabCount <= 3) TabLayout.MODE_FIXED else TabLayout.MODE_SCROLLABLE
        tabLayout.tabGravity = if (tabLayout.tabCount <= 3) TabLayout.GRAVITY_FILL else TabLayout.GRAVITY_START

        val tabViews = strip ?: return
        for (index in 0 until tabViews.childCount) {
            val tab = tabViews.getChildAt(index)
            tab.background = tabBackground(resources)
            val horizontalInset = (8f * resources.displayMetrics.density).toInt()
            tab.setPadding(horizontalInset, tab.paddingTop, horizontalInset, tab.paddingBottom)
        }
    }

    private fun captureTabViews(strip: ViewGroup?): List<TabViewState> =
        strip?.let { group -> (0 until group.childCount).map { index ->
            val tab = group.getChildAt(index)
            TabViewState(tab, tab.background, android.graphics.Rect(
                tab.paddingLeft, tab.paddingTop, tab.paddingRight, tab.paddingBottom))
        } } ?: emptyList()

    fun restoreTabs(tabLayout: TabLayout) {
        val state = tabLayoutStates.remove(tabLayout) ?: return
        tabLayout.tabMode = state.mode
        tabLayout.tabGravity = state.gravity
        tabLayout.tabRippleColor = state.rippleColor
        tabLayout.setSelectedTabIndicator(state.indicator)
        val strip = tabLayout.getChildAt(0) as? ViewGroup ?: return
        for (index in 0 until strip.childCount) {
            val view = strip.getChildAt(index)
            val original = state.tabs.getOrNull(index) ?: state.tabs.firstOrNull() ?: continue
            view.background = original.background?.constantState?.newDrawable(tabLayout.resources)?.mutate()
                ?: original.background
            view.setPadding(original.padding.left, original.padding.top,
                original.padding.right, original.padding.bottom)
        }
    }

    fun tabBackground(resources: Resources) = StateListDrawable().apply {
        val selected = gradient(tabSelectedTop, tabSelectedBottom).apply {
            setStroke(strokeWidth(resources), tabEdge)
        }
        val normal = gradient(tabTop, tabBottom).apply {
            setStroke(strokeWidth(resources), tabEdge)
        }
        addState(intArrayOf(android.R.attr.state_selected), selected)
        addState(intArrayOf(android.R.attr.state_activated), selected)
        addState(intArrayOf(), normal)
    }

    private fun gradient(top: Int, bottom: Int) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(top, bottom),
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 0f
    }

    private fun strokeWidth(resources: Resources) =
        resources.displayMetrics.density.toInt().coerceAtLeast(1)
}
