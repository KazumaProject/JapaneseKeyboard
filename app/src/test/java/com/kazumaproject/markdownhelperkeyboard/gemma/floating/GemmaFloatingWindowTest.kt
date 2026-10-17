package com.kazumaproject.markdownhelperkeyboard.gemma.floating

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
class GemmaFloatingWindowTest {
    private fun surface(window: GemmaFloatingWindow) = GemmaFloatingWindow::class.java
        .getDeclaredField("surface").apply { isAccessible = true }.get(window) as ScrollView?

    @Test fun processingAndResultsShareTheCandidateLeftEdgeAndOnlyResultsCommit() {
        val activityController = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = activityController.get()
        val root = FrameLayout(activity)
        val anchor = View(activity)
        root.addView(anchor, FrameLayout.LayoutParams(400, 48).apply { leftMargin = 40; topMargin = 700 })
        activity.setContentView(root)
        root.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 500, 1000)
        val commits = mutableListOf<String>()
        val input = GemmaFloatingInput(1, 1, "hello", GemmaFloatingSettings.defaults().first())
        val window = GemmaFloatingWindow(activity, { anchor }, { true },
            { CandidatePanelColors(0, 0, 0, 0, 0, 0) }, { true },
            { _, text -> commits += text; true })
        try {
            window.showProcessing(input)
            val panel = requireNotNull(surface(window))
            val label = panel.getChildAt(0) as TextView
            val location = IntArray(2).also(anchor::getLocationOnScreen)
            val processingLayout = panel.layoutParams as WindowManager.LayoutParams
            assertEquals(location[0], processingLayout.x)
            assertTrue(processingLayout.y + processingLayout.height < location[1])
            assertEquals("…", label.text.toString())
            assertEquals(activity.getString(R.string.gemma_floating_processing), label.contentDescription)
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

            window.showProcessing(input)
            val pendingPanel = requireNotNull(surface(window))
            val staleLabel = pendingPanel.getChildAt(0)
            window.clear()
            shadowOf(Looper.getMainLooper()).idle()
            staleLabel.performClick()
            assertNull(surface(window))
            assertNull(pendingPanel.parent)
            assertEquals(1, commits.size)
        } finally {
            window.clear()
            activityController.pause().stop().destroy()
        }
    }
}
