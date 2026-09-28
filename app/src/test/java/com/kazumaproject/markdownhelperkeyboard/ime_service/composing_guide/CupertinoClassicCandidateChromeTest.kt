package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.content.Context
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.tabs.TabLayout
import org.junit.Assert.assertEquals
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
}
