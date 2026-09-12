package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CandidateSurfaceHostTest {
    @Test fun verticalLayoutAcceptsHoldersRecycledFromHorizontalLayout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val params = androidx.recyclerview.widget.RecyclerView.LayoutParams(123, 58)
        val manager = candidatePanelLayoutManager(context, true)
        val converted = manager.generateLayoutParams(params)
        assertTrue(converted is com.kazumaproject.android.flexbox.FlexboxLayoutManager.LayoutParams)
        assertEquals(123, converted.width)
        assertEquals(58, converted.height)
    }

    @Test fun restoresTabAppearanceAndRecyclerDecorationWhenDocking() {
        val context = android.view.ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(),
            com.kazumaproject.markdownhelperkeyboard.R.style.Theme_MarkdownKeyboard)
        val root = FrameLayout(context)
        val toolbar = View(context)
        val tabs = com.google.android.material.tabs.TabLayout(context)
        listOf("予測", "変換", "英数カナ").forEach { tabs.addTab(tabs.newTab().setText(it)) }
        val indicator = tabs.tabSelectedIndicator
        val strip = FrameLayout(context)
        val candidates = androidx.recyclerview.widget.RecyclerView(context)
        strip.addView(candidates)
        val full = View(context)
        listOf(toolbar, tabs, strip, full).forEach { root.addView(it) }
        val host = CandidateSurfaceHost(toolbar, tabs, strip, candidates, full)
        host.attach(LinearLayout(context))
        assertEquals(1, candidates.itemDecorationCount)
        val label = tabs.getTabAt(2)?.customView as android.widget.TextView
        assertEquals("英数カナ", label.text.toString())
        assertEquals(1, label.maxLines)
        val colors = CandidatePanelColors(1, 2, 3, 4, 5, 6)
        host.setColors(colors)
        val recolored = tabs.getTabAt(2)?.customView as android.widget.TextView
        assertEquals(colors.text, recolored.currentTextColor)
        assertEquals(colors.selectionText, recolored.textColors.getColorForState(intArrayOf(android.R.attr.state_selected), 0))
        tabs.removeAllTabs()
        listOf("予測", "変換", "英数カナ").forEach { tabs.addTab(tabs.newTab().setText(it)) }
        host.refreshAppearance()
        assertEquals("英数カナ", (tabs.getTabAt(2)?.customView as android.widget.TextView).text.toString())
        host.detach()
        assertEquals(0, candidates.itemDecorationCount)
        assertNull(tabs.getTabAt(2)?.customView)
        assertSame(indicator, tabs.tabSelectedIndicator)
    }

    @Test fun movesAllExistingViewsAndRestoresOriginalOrderAndParameters() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = FrameLayout(context)
        val keyboard = View(context)
        val toolbar = View(context)
        val tabs = View(context)
        val strip = FrameLayout(context)
        val candidates = View(context)
        val full = View(context)
        strip.addView(candidates, FrameLayout.LayoutParams(-1, -2))
        val original = listOf(keyboard, strip, tabs, toolbar, full)
        original.forEach { root.addView(it, FrameLayout.LayoutParams(-1, 72).apply { bottomMargin = 300 }) }
        val params = original.map { it.layoutParams }
        val floating = LinearLayout(context)
        val host = CandidateSurfaceHost(toolbar, tabs, strip, candidates, full)
        repeat(2) {
            host.attach(floating)
            host.attach(floating)
            assertTrue(host.attached)
            assertSame(root, keyboard.parent)
            assertEquals(1, root.childCount)
            assertSame(floating, toolbar.parent)
            assertSame(floating, tabs.parent)
            assertSame(floating, strip.parent)
            assertSame(strip, candidates.parent)
            host.setExpanded(true)
            assertEquals(View.VISIBLE, full.visibility)
            assertEquals(View.INVISIBLE, candidates.visibility)
            assertEquals(View.VISIBLE, keyboard.visibility)
            host.setExpanded(false)
            assertEquals(View.GONE, full.visibility)
            assertEquals(View.VISIBLE, candidates.visibility)
            candidates.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            host.detach()
            assertFalse(host.attached)
            assertEquals(0, floating.childCount)
            original.forEachIndexed { index, view ->
                assertSame(view, root.getChildAt(index))
                assertSame(params[index], view.layoutParams)
            }
            assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, candidates.layoutParams.height)
        }
    }
}
