package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.QuickActionsState
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting.SuggestionAdapter2
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CandidateReadingViewportTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard)
    private val candidates = listOf(Candidate("学校", 1, 4u, 0, "がっこう"))

    @Before fun preferences() {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test fun readingToShortcutsRestoresViewportAcrossRowsModesAndFrameHeights() {
        for (ruby in listOf(false, true)) for (rows in 1..3) {
            val fixture = Fixture(rows, ruby)
            try {
                fixture.layout(400, 60 + (rows - 1) * 20)
                assertNotNull(fixture.recycler.clipBounds)
                assertFalse(fixture.frame.clipChildren)
                // Conversion cancellation empties candidates before the idle strip is restored.
                fixture.adapter.submitContent(CandidateStripContent.Empty)
                fixture.layout(400, 60)
                fixture.adapter.submitContent(shortcuts())
                fixture.layout(400, 60)
                assertNull("Stale reading viewport: rows=$rows ruby=$ruby", fixture.recycler.clipBounds)
                assertTrue(fixture.recycler.clipChildren)
                assertTrue(fixture.frame.clipChildren)
                val icon = fixture.recycler.getChildAt(0).findViewById<View>(R.id.item_image)
                val bounds = Rect(0, 0, icon.width, icon.height)
                fixture.frame.offsetDescendantRectToMyCoords(icon, bounds)
                assertTrue("Shortcut icon must fit the empty frame: $bounds", bounds.top >= 0 && bounds.bottom <= fixture.frame.height)
                // Re-entering composing must acquire a fresh viewport, not reuse old coordinates.
                fixture.adapter.submitContent(CandidateStripContent.Candidates(candidates))
                fixture.layout(600, 100)
                assertEquals(fixture.recycler.width, fixture.recycler.clipBounds!!.right)
                assertFalse(fixture.frame.clipChildren)
            } finally { fixture.close() }
        }
    }

    @Test fun resizedFrameRecomputesClipEvenWhenCandidateDoesNotRelayout() {
        val fixture = Fixture()
        try {
            fixture.layout(400, 60)
            val previous = Rect(fixture.recycler.clipBounds!!)
            fixture.layout(600, 120)
            val clip = fixture.recycler.clipBounds!!
            assertNotEquals(previous, clip)
            assertEquals(fixture.recycler.width, clip.right)
            assertEquals(fixture.frame.height - fixture.recycler.top, clip.bottom)
        } finally { fixture.close() }
    }

    @Test fun hidingReadingAndSwitchingToVerticalLayoutRestoreOriginalClipping() {
        val fixture = Fixture()
        try {
            fixture.layout(400, 60)
            fixture.adapter.setShowCandidateYomiForLiveConversion(false)
            fixture.layout(400, 60)
            assertNull(fixture.recycler.clipBounds)
            assertTrue(fixture.frame.clipChildren)
            fixture.adapter.setShowCandidateYomiForLiveConversion(true)
            fixture.layout(400, 60)
            assertNotNull(fixture.recycler.clipBounds)
            fixture.recycler.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            fixture.layout(400, 60)
            assertNull(fixture.recycler.clipBounds)
            assertTrue(fixture.frame.clipChildren)
        } finally { fixture.close() }
    }

    @Test fun adapterDetachAndReleaseRestoreAnExistingCustomViewport() {
        for (release in listOf(false, true)) {
            val fixture = Fixture()
            try {
                fixture.recycler.clipBounds = Rect(1, 2, 300, 50)
                fixture.recycler.clipChildren = false
                fixture.layout(400, 60)
                if (release) fixture.adapter.release() else fixture.recycler.adapter = null
                assertEquals(Rect(1, 2, 300, 50), fixture.recycler.clipBounds)
                assertFalse(fixture.recycler.clipChildren)
                assertTrue(fixture.frame.clipChildren)
            } finally { fixture.close() }
        }
    }

    @Test fun sharedFrameRemainsUnclippedUntilBothReadingStripsReleaseIt() {
        val fixture = Fixture()
        val second = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            itemAnimator = null
        }
        val adapter = SuggestionAdapter { it.run() }
        try {
            fixture.frame.addView(second, ConstraintLayout.LayoutParams(200, -2).apply {
                topToTop = 0; bottomToBottom = 0; endToEnd = 0
            })
            adapter.setShowCandidateYomiForLiveConversion(true)
            second.adapter = adapter
            adapter.submitContent(CandidateStripContent.Candidates(candidates))
            fixture.layout(400, 60)
            assertNotNull(second.clipBounds)
            fixture.adapter.release()
            assertFalse("Other strip still needs the shared frame", fixture.frame.clipChildren)
            second.adapter = null
            assertNull(second.clipBounds)
            assertTrue(fixture.frame.clipChildren)
        } finally {
            second.adapter = null
            adapter.release()
            fixture.close()
        }
    }

    @Test fun previewAdapterReleaseRestoresItsWrappedAncestors() {
        val fixture = Fixture(wrapped = true)
        val preview = SuggestionAdapter2()
        try {
            fixture.recycler.adapter = preview
            preview.setShowCandidateYomiForLiveConversion(true)
            preview.suggestions = candidates
            val deadline = System.nanoTime() + 2_000_000_000L
            while (preview.itemCount == 0 && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            fixture.layout(400, 60)
            assertNotNull(fixture.recycler.clipBounds)
            assertFalse(fixture.area.clipChildren)
            assertFalse(fixture.frame.clipChildren)
            preview.release()
            assertNull(fixture.recycler.clipBounds)
            assertTrue(fixture.area.clipChildren)
            assertTrue(fixture.frame.clipChildren)
        } finally { fixture.close() }
    }

    private fun shortcuts() = CandidateStripContent.EmptyState(false,
        QuickActionsState(false, false, false, false, "", ""), null,
        listOf(ShortcutType.SETTINGS, ShortcutType.EMOJI), true)

    private inner class Fixture(rows: Int = 1, ruby: Boolean = false, wrapped: Boolean = false) {
        private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val frame = ConstraintLayout(context)
        val area = if (wrapped) ConstraintLayout(context).also {
            frame.addView(it, ConstraintLayout.LayoutParams(-1, -2).apply {
                topToTop = 0; bottomToBottom = 0; startToStart = 0; endToEnd = 0
            })
        } else frame
        val recycler = RecyclerView(context).apply {
            id = View.generateViewId()
            itemAnimator = null
            layoutManager = GridLayoutManager(context, rows, RecyclerView.HORIZONTAL, false).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int) = if (adapter?.getItemViewType(position) == SuggestionAdapter.VIEW_TYPE_SHORTCUT) rows else 1
                }
            }
        }
        val adapter = SuggestionAdapter { it.run() }
        init {
            area.addView(recycler, ConstraintLayout.LayoutParams(0, -2).apply {
                topToTop = 0; bottomToBottom = 0; startToStart = 0; endToEnd = 0
            })
            adapter.setShowCandidateYomiForLiveConversion(true)
            adapter.setCandidateYomiMode(if (ruby) "ruby" else "whole")
            recycler.adapter = adapter
            adapter.submitContent(CandidateStripContent.Candidates(candidates))
            activity.setContentView(frame)
        }
        fun layout(widthDp: Int, heightDp: Int) {
            val density = context.resources.displayMetrics.density
            repeat(2) {
                shadowOf(Looper.getMainLooper()).idle()
                frame.measure(View.MeasureSpec.makeMeasureSpec((widthDp * density).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((heightDp * density).toInt(), View.MeasureSpec.EXACTLY))
                frame.layout(0, 0, frame.measuredWidth, frame.measuredHeight)
                frame.viewTreeObserver.dispatchOnGlobalLayout()
            }
        }
        fun close() {
            recycler.adapter = null
            adapter.release()
            activity.finish()
        }
    }
}
