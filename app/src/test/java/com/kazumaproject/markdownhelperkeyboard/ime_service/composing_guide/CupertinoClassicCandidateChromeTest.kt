package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.tabs.TabLayout
import com.kazumaproject.tenkey.view.SideKeySymbolModeContainerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CupertinoClassicCandidateChromeTest {
    private val context = android.view.ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(),
        com.kazumaproject.markdownhelperkeyboard.R.style.Theme_MarkdownKeyboard,
    )

    @Test fun dockedStripKeepsItsMinimumControlsVisibleWithoutShrinkingLargerSettings() {
        assertEquals(58, CupertinoClassicCandidateChrome.resolveDockedStripHeightDp(30))
        assertEquals(110, CupertinoClassicCandidateChrome.resolveDockedStripHeightDp(110))
    }

    @Test fun candidateAndExpandedSurfacesUseTheSameUniformBackground() {
        val panel = CupertinoClassicCandidateChrome.panelBackground()
        val tabs = CupertinoClassicCandidateChrome.tabsBackground()

        assertEquals(CupertinoClassicCandidateChrome.panelColor, panel.color)
        assertEquals(panel.color, tabs.color)
        assertEquals(255, panel.alpha)
    }

    @Test fun expandedCandidateBackgroundCanFollowLiquidGlassTransparency() {
        assertEquals(0, CupertinoClassicCandidateChrome.panelBackground(alpha = 0).alpha)
        assertEquals(255, CupertinoClassicCandidateChrome.panelBackground(alpha = 255).alpha)
    }

    @Test fun candidateTabsUseSegmentedSelectionAndRestoreTheirPreviousAppearance() {
        val layout = TabLayout(context).apply {
            addTab(newTab().setText("予測"))
            addTab(newTab().setText("変換"))
            addTab(newTab().setText("英数カナ"))
        }
        val oldMode = layout.tabMode
        val oldGravity = layout.tabGravity
        val strip = layout.getChildAt(0) as ViewGroup
        val firstTab = strip.getChildAt(0)
        val oldBackgroundType = firstTab.background?.javaClass

        CupertinoClassicCandidateChrome.applyTabs(layout)

        assertEquals(TabLayout.MODE_FIXED, layout.tabMode)
        assertEquals(TabLayout.GRAVITY_FILL, layout.tabGravity)
        val tabColors = requireNotNull(layout.tabTextColors)
        assertEquals(CupertinoClassicCandidateChrome.textColor,
            tabColors.getColorForState(intArrayOf(), 0))
        assertEquals(android.graphics.Color.WHITE,
            tabColors.getColorForState(intArrayOf(android.R.attr.state_selected), 0))
        assertTrue(firstTab.background is android.graphics.drawable.StateListDrawable)
        assertEquals(android.graphics.Color.TRANSPARENT,
            (layout.tabSelectedIndicator as android.graphics.drawable.ColorDrawable).color)

        CupertinoClassicCandidateChrome.restoreTabs(layout)

        assertEquals(oldMode, layout.tabMode)
        assertEquals(oldGravity, layout.tabGravity)
        assertEquals(oldBackgroundType, strip.getChildAt(0).background?.javaClass)
    }

    @Test fun candidateExpandButtonUsesClassicColorsAndChangesForSelectedAndPressedStates() {
        val background = CupertinoClassicCandidateChrome.expandButtonBackground(context.resources)
        val normal = background.current

        background.state = intArrayOf(android.R.attr.state_selected)
        val selected = background.current
        assertNotSame(normal, selected)

        background.state = intArrayOf(android.R.attr.state_pressed)
        assertNotSame(selected, background.current)
        assertEquals(CupertinoClassicCandidateChrome.textColor,
            CupertinoClassicCandidateChrome.expandButtonTint().getColorForState(
                intArrayOf(), android.graphics.Color.TRANSPARENT))
        assertEquals(android.graphics.Color.WHITE,
            CupertinoClassicCandidateChrome.expandButtonTint().getColorForState(
                intArrayOf(android.R.attr.state_selected), android.graphics.Color.TRANSPARENT))
    }

    @Test fun tenkeyNumberAndSymbolButtonsHaveGapOnlyWhenThreeStateIsDisabled() {
        val view = SideKeySymbolModeContainerView(context)
        val numberParams = view.getChildAt(0).layoutParams as android.widget.LinearLayout.LayoutParams
        val symbolParams = view.getChildAt(1).layoutParams as android.widget.LinearLayout.LayoutParams
        val expectedGapPx = (4 * context.resources.displayMetrics.density).toInt()

        assertEquals(View.GONE, view.getChildAt(0).visibility)
        assertEquals(0, symbolParams.marginStart)

        view.setUseThreeStateKeyboard(false, 9)
        assertEquals(View.VISIBLE, view.getChildAt(0).visibility)
        assertEquals((9 * context.resources.displayMetrics.density).toInt(), symbolParams.marginStart)
        assertEquals(1f, numberParams.weight, 0f)

        view.setUseThreeStateKeyboard(false, 0)
        assertEquals(0, symbolParams.marginStart)

        view.setUseThreeStateKeyboard(false)
        assertEquals(expectedGapPx, symbolParams.marginStart)

        view.setUseThreeStateKeyboard(false, 24)
        assertEquals((16 * context.resources.displayMetrics.density).toInt(), symbolParams.marginStart)

        view.setUseThreeStateKeyboard(true)
        assertEquals(View.GONE, view.getChildAt(0).visibility)
        assertEquals(0, symbolParams.marginStart)
    }
}
