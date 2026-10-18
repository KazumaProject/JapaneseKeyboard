package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidatePanelColors
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w500dp-h1000dp-mdpi")
class ZenzFloatingWindowTest {
    private fun surface(window: ZenzFloatingWindow) = ZenzFloatingWindow::class.java
        .getDeclaredField("surface").apply { isAccessible = true }.get(window) as ScrollView?

    @Test fun processingAndResultsShareTheCandidateLeftEdgeAndOnlyResultsCommit() {
        val activityController = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = activityController.get()
        val root = FrameLayout(activity)
        var anchor = View(activity)
        root.addView(anchor, FrameLayout.LayoutParams(400, 48).apply { leftMargin = 40; topMargin = 700 })
        activity.setContentView(root)
        root.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 500, 1000)
        val commits = mutableListOf<String>()
        val input = 1L
        var valid = true
        var eligible = true
        val window = ZenzFloatingWindow(activity, { anchor }, { activity.window.decorView.windowToken }, { eligible },
            { CandidatePanelColors(0, 0, 0, 0, 0, 0) }, { valid },
            { _, text -> commits += text; true })
        try {
            window.showProcessing(input)
            val panel = requireNotNull(surface(window))
            val label = panel.getChildAt(0) as TextView
            val location = IntArray(2).also(anchor::getLocationOnScreen)
            val processingLayout = panel.layoutParams as WindowManager.LayoutParams
            assertSame(activity.window.decorView.windowToken, processingLayout.token)
            assertEquals(location[0], processingLayout.x)
            assertTrue(processingLayout.y + processingLayout.height < location[1])
            assertEquals("…", label.text.toString())
            assertEquals(activity.getString(R.string.zenz_floating_loading), label.contentDescription)
            assertFalse(label.isClickable)
            label.performClick() // Also exercise an already queued click callback.
            assertTrue(commits.isEmpty())

            window.show(input, "こんにちは、世界")
            shadowOf(Looper.getMainLooper()).idle()
            assertSame(panel, surface(window))
            assertEquals(location[0], (panel.layoutParams as WindowManager.LayoutParams).x)
            assertEquals("こんにちは、世界", label.text.toString())
            assertNull(label.contentDescription)
            assertTrue(label.isClickable)
            label.performClick()
            assertEquals(listOf("こんにちは、世界"), commits)
            assertNull(surface(window))

            window.show(input, "長い候補".repeat(200))
            val longPanel = requireNotNull(surface(window))
            assertTrue(longPanel.measuredWidth <= 320)
            assertTrue(longPanel.measuredHeight <= 180)
            valid = false
            (longPanel.getChildAt(0) as TextView).performClick()
            assertEquals(1, commits.size)
            root.viewTreeObserver.dispatchOnPreDraw()
            assertNull(surface(window))
            valid = true
            window.show(input, "再表示")
            val disabledLabel = requireNotNull(surface(window)).getChildAt(0)
            eligible = false
            disabledLabel.performClick()
            assertEquals(1, commits.size)
            root.viewTreeObserver.dispatchOnPreDraw()
            assertNull(surface(window))
            eligible = true
            window.show(input, "")
            assertNull(surface(window))

            window.showProcessing(input)
            val pendingPanel = requireNotNull(surface(window))
            val staleLabel = pendingPanel.getChildAt(0)
            window.clear()
            shadowOf(Looper.getMainLooper()).idle()
            staleLabel.performClick()
            assertNull(surface(window))
            assertNull(pendingPanel.parent)
            assertEquals(1, commits.size)
            window.show(input + 1, "新しい要求")
            staleLabel.performClick()
            assertEquals(1, commits.size)
            val previousPanel = requireNotNull(surface(window))
            val nextAnchor = View(activity)
            root.addView(nextAnchor, FrameLayout.LayoutParams(300, 48).apply { leftMargin = 90; topMargin = 600 })
            root.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 500, 1000)
            anchor = nextAnchor
            root.viewTreeObserver.dispatchOnPreDraw()
            assertNotSame(previousPanel, surface(window))
            assertNull(previousPanel.parent)
            assertEquals(90, (requireNotNull(surface(window)).layoutParams as WindowManager.LayoutParams).x)
            root.removeView(nextAnchor)
            assertNull(surface(window))
            root.addView(nextAnchor, FrameLayout.LayoutParams(300, 48).apply { leftMargin = 90; topMargin = 600 })
            root.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 500, 1000)
            root.viewTreeObserver.dispatchOnPreDraw()
            assertNotNull(surface(window))
        } finally {
            window.clear()
            activityController.pause().stop().destroy()
        }
    }
}
