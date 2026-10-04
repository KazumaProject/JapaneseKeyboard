package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Typeface
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.FrameLayout
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting.SuggestionAdapter2
import org.robolectric.Shadows.shadowOf
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CandidateReadingLayoutTest {
    private val context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard
    )

    @org.junit.Before fun initializePreferences() {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference.init(context)
    }

    @Test fun fixedFrameHeadroomMakesReadingLargerWithoutMovingAnyCandidate() {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        for ((rows, heightDp, wrapped) in listOf(Triple(1, 60, false), Triple(2, 80, false),
            Triple(3, 100, false), Triple(1, 60, true))) {
            val prefs = com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
            prefs.setCandidateColumnAndSyncHeight(false, rows.toString())
            prefs.setCandidateColumnAndSyncHeight(true, rows.toString())
            val frame = androidx.constraintlayout.widget.ConstraintLayout(context)
            val recycler = androidx.recyclerview.widget.RecyclerView(context).apply {
                id = View.generateViewId()
                clipToPadding = false
                layoutManager = androidx.recyclerview.widget.GridLayoutManager(context, rows,
                    androidx.recyclerview.widget.RecyclerView.HORIZONTAL, false)
                if (rows > 1) addItemDecoration(GridSpacingItemDecoration(rows,
                    resources.getDimensionPixelSize(com.kazumaproject.core.R.dimen.grid_spacing), true))
            }
            var showReading = false
            recycler.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
                override fun getItemCount() = 6
                override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int) =
                    object : androidx.recyclerview.widget.RecyclerView.ViewHolder(
                        LayoutInflater.from(parent.context).inflate(R.layout.suggestion_item, parent, false)) {}
                override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                    holder.itemView.findViewById<TextView>(R.id.suggestion_item_text_view).text = "    感じ    "
                    holder.itemView.findViewById<CandidateReadingTextView>(R.id.suggestion_item_yomi_text_view).apply {
                        text = "かんじ"
                        textSize = CandidateReadingSizeLimits.maximumSp(context).toFloat()
                        visibility = if (showReading && position == 0) View.VISIBLE else View.GONE
                    }
                }
            }
            val area = if (wrapped) androidx.constraintlayout.widget.ConstraintLayout(context).also {
                frame.addView(it, androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(-1, -2).apply {
                    bottomToBottom = 0; startToStart = 0; endToEnd = 0
                })
            } else frame
            area.addView(recycler, androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(0, -2).apply {
                topToTop = 0; bottomToBottom = 0; startToStart = 0; endToEnd = 0
            })
            activity.setContentView(frame)
            fun measureFrame() {
                shadowOf(Looper.getMainLooper()).idle()
                frame.measure(View.MeasureSpec.makeMeasureSpec(dp(400), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dp(heightDp), View.MeasureSpec.EXACTLY))
                frame.layout(0, 0, frame.measuredWidth, frame.measuredHeight)
            }
            fun bodies() = (0 until recycler.childCount).map {
                val child = recycler.getChildAt(it)
                bounds(frame, child.findViewById<TextView>(R.id.suggestion_item_text_view))
            }
            measureFrame()
            val before = bodies()
            assertTrue(before.size >= rows)
            showReading = true
            recycler.adapter!!.notifyDataSetChanged()
            measureFrame()
            assertEquals("all candidate body vertical positions in $rows rows",
                before.map { it.top to it.bottom }, bodies().map { it.top to it.bottom })
            val first = recycler.findViewHolderForAdapterPosition(0)!!.itemView
            val reading = first.findViewById<CandidateReadingTextView>(R.id.suggestion_item_yomi_text_view)
            assertFalse(recycler.clipChildren)
            assertEquals(1f, reading.inkScale, .001f)
            val annotation = bounds(frame, reading)
            assertTrue(annotation.top >= 0)
            if (rows == 1) assertTrue("use space above the item", annotation.top < bounds(frame, first).top)
            val with = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
            frame.draw(Canvas(with))
            reading.visibility = View.GONE
            val without = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
            frame.draw(Canvas(without))
            val inkRows = (annotation.top until annotation.bottom).count { y ->
                (annotation.left until annotation.right).any { x -> with.getPixel(x, y) != without.getPixel(x, y) }
            }
            assertTrue("annotation actually draws in fixed frame: rows=$rows ink=$inkRows height=${reading.height} bounds=$annotation", inkRows >= reading.height * .8f)
        }
        activity.finish()
    }

    @Test fun readingDoesNotChangeBodyOrBadgePositionInOneTwoAndThreeRows() {
        for ((rows, heightDp) in listOf(1 to 60, 2 to 80, 3 to 100)) {
            val view = create()
            val body = view.findViewById<TextView>(R.id.suggestion_item_text_view)
            val badge = view.findViewById<TextView>(R.id.suggestion_item_type_text_view)
            val reading = view.findViewById<CandidateReadingTextView>(R.id.suggestion_item_yomi_text_view)
            body.text = "    感じ    "
            badge.text = "[学習]"
            reading.text = "かんじ"
            reading.textSize = 14f * .72f
            reading.visibility = View.GONE
            layout(view, dp(heightDp) / rows)
            val bodyBefore = bounds(view, body)
            val badgeBefore = bounds(view, badge)
            val baselineBefore = bodyBefore.top + body.baseline
            reading.visibility = View.VISIBLE
            layout(view, dp(heightDp) / rows)
            assertEquals("body in $rows rows", bodyBefore, bounds(view, body))
            assertEquals("badge in $rows rows", badgeBefore, bounds(view, badge))
            assertEquals(baselineBefore, bounds(view, body).top + body.baseline)
            val ink = Rect()
            body.paint.getTextBounds(body.text.toString(), 0, body.text.length, ink)
            val annotation = bounds(view, reading)
            assertTrue("reading above body in $rows rows", annotation.bottom < baselineBefore + ink.top)
            assertTrue("reading inside item in $rows rows", annotation.top >= 0)
            assertTrue(annotation.right <= view.width)
            assertEquals(0, reading.measuredHeight)
            assertTrue(reading.height <= reading.inkBounds.height())
            assertTrue(reading.height > 0)
        }
    }

    @Test fun annotationIsDrawnOutsideTheBodyContainerWithoutBeingClipped() {
        val view = create()
        val body = view.findViewById<TextView>(R.id.suggestion_item_text_view)
        val reading = view.findViewById<CandidateReadingTextView>(R.id.suggestion_item_yomi_text_view)
        body.text = "    感じ    "
        reading.text = "かんじ"
        reading.visibility = View.VISIBLE
        layout(view, dp(36))
        val annotation = bounds(view, reading)
        val withReading = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(withReading))
        reading.visibility = View.GONE
        layout(view, dp(36))
        val withoutReading = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(withoutReading))
        val changedRows = (annotation.top until annotation.bottom).filter { y ->
            (annotation.left until annotation.right).any { x ->
                withReading.getPixel(x, y) != withoutReading.getPixel(x, y)
            }
        }
        assertTrue("the full annotation ink should be visible", changedRows.size >= reading.height * .8f)
    }

    @Test fun previewBindingUsesTheSameLayoutAndOnlyAnnotatesTheFirstCandidate() {
        val adapter = SuggestionAdapter2()
        adapter.suggestions = listOf(
            Candidate(string = "感じ", type = 1, length = 3u, score = 0, yomi = "かんじ"),
            Candidate(string = "漢字", type = 1, length = 3u, score = 0, yomi = "かんじ")
        )
        val deadline = System.nanoTime() + 2_000_000_000L
        while (adapter.itemCount != 2 && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertEquals(2, adapter.itemCount)
        val parent = FrameLayout(context)
        for (position in 0..1) {
            val holder = adapter.createViewHolder(parent, adapter.getItemViewType(position))
            val root = holder.itemView as CandidateReadingLayout
            val body = root.findViewById<TextView>(R.id.suggestion_item_text_view)
            adapter.setShowCandidateYomiForLiveConversion(false)
            adapter.onBindViewHolder(holder, position)
            layout(root, dp(36))
            val before = bounds(root, body)
            adapter.setShowCandidateYomiForLiveConversion(true)
            adapter.onBindViewHolder(holder, position)
            layout(root, dp(36))
            assertEquals(before, bounds(root, body))
            assertEquals(if (position == 0) View.VISIBLE else View.GONE,
                root.findViewById<View>(R.id.suggestion_item_yomi_text_view).visibility)
        }
        adapter.release()
    }

    @Test fun readingSizeRebindsIndependentlyInImePreviewAndSplitCandidates() {
        val candidate = Candidate(string = "漢字", type = 1, length = 3u, score = 0, yomi = "かんじ")
        val ime = SuggestionAdapter()
        val preview = SuggestionAdapter2()
        val split = SuggestionAdapter()
        ime.submitContent(com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent.Candidates(listOf(candidate)))
        preview.suggestions = listOf(candidate)
        ime.setShowCandidateYomiForLiveConversion(true)
        preview.setShowCandidateYomiForLiveConversion(true)
        ime.setCandidateTextSize(32f)
        preview.setCandidateTextSize(32f)
        ime.setCandidateYomiTextSize(1f)
        preview.setCandidateYomiTextSize(1f)
        val deadline = System.nanoTime() + 2_000_000_000L
        while ((ime.itemCount != 1 || preview.itemCount != 1) && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        split.mirrorSplitContentFrom(ime)
        val splitDeadline = System.nanoTime() + 2_000_000_000L
        while (split.itemCount != 1 && System.nanoTime() < splitDeadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertEquals(1, split.itemCount)
        val parent = FrameLayout(context)
        for (adapter in listOf(ime, preview, split)) {
            val holder = adapter.createViewHolder(parent, adapter.getItemViewType(0))
            adapter.onBindViewHolder(holder, 0)
            val root = holder.itemView as CandidateReadingLayout
            layout(root, dp(160))
            val body = root.findViewById<TextView>(R.id.suggestion_item_text_view)
            val reading = root.findViewById<TextView>(R.id.suggestion_item_yomi_text_view)
            val before = bounds(root, body)
            assertEquals(1f * context.resources.displayMetrics.scaledDensity, reading.textSize, .01f)
            when (adapter) {
                is SuggestionAdapter -> adapter.setCandidateYomiTextSize(2f)
                is SuggestionAdapter2 -> adapter.setCandidateYomiTextSize(2f)
            }
            adapter.onBindViewHolder(holder, 0)
            layout(root, dp(160))
            assertEquals(2f * context.resources.displayMetrics.scaledDensity, reading.textSize, .01f)
            assertEquals(32f * context.resources.displayMetrics.scaledDensity, body.textSize, .01f)
            assertEquals(before, bounds(root, body))
        }
        ime.release()
        preview.release()
        split.release()
    }

    @Test fun floatingPaddingRemainsAvailableForTheAnnotation() {
        val view = create()
        view.setPadding(dp(12), dp(8), dp(12), dp(8))
        val body = view.findViewById<TextView>(R.id.suggestion_item_text_view)
        val reading = view.findViewById<CandidateReadingTextView>(R.id.suggestion_item_yomi_text_view)
        body.text = "漢字"
        body.textSize = 18f
        reading.text = "かんじ"
        reading.textSize = 14f * .72f
        reading.visibility = View.GONE
        layout(view, dp(44))
        val before = bounds(view, body)
        reading.visibility = View.VISIBLE
        layout(view, dp(44))
        assertEquals(before, bounds(view, body))
        assertTrue(bounds(view, reading).top >= 0)
        assertTrue(reading.height >= reading.inkBounds.height() * .8f)
    }

    @Test fun liveReadingUpdatesRequestMeasurementAndRefreshTheirWidth() {
        val view = create()
        val body = view.findViewById<TextView>(R.id.suggestion_item_text_view)
        val reading = view.findViewById<CandidateReadingTextView>(R.id.suggestion_item_yomi_text_view)
        body.text = "    漢字    "
        reading.text = "かんじ"
        reading.visibility = View.VISIBLE
        layout(view, dp(60))
        val before = bounds(view, body)
        val originalWidth = view.width
        reading.text = "とてもながいよみがなをひょうじする"
        assertTrue("live text updates must request measurement", reading.isLayoutRequested)
        layout(view, dp(60))
        assertTrue(view.width > originalWidth)
        assertTrue(reading.width >= reading.leadingInsetPx + reading.paint.measureText(reading.text.toString()))
        assertEquals(before, bounds(view, body))
        reading.textSize = 12f
        assertTrue("font size updates must request measurement", reading.isLayoutRequested)
        layout(view, dp(60))
        assertTrue(reading.width >= reading.leadingInsetPx + reading.paint.measureText(reading.text.toString()))
    }

    @Test fun longReadingContributesWidthWithoutChangingHeight() {
        val view = create()
        val body = view.findViewById<TextView>(R.id.suggestion_item_text_view)
        val reading = view.findViewById<CandidateReadingTextView>(R.id.suggestion_item_yomi_text_view)
        body.text = "    漢字    "
        reading.visibility = View.GONE
        layout(view, dp(60))
        val widthWithoutReading = view.measuredWidth
        val bodyBefore = bounds(view, body)
        reading.text = "とてもながいよみがなをひょうじする"
        reading.visibility = View.VISIBLE
        layout(view, dp(60))
        assertTrue(view.measuredWidth > widthWithoutReading)
        assertEquals(bodyBefore, bounds(view, body))
        assertTrue(bounds(view, reading).right <= view.width)
    }

    @Test fun recycledReadingRecomputesGlyphBoundsAndIndentAfterFontAndTextChanges() {
        val view = create()
        val body = view.findViewById<TextView>(R.id.suggestion_item_text_view)
        val reading = view.findViewById<CandidateReadingTextView>(R.id.suggestion_item_yomi_text_view)
        for (font in listOf(Typeface.DEFAULT, Typeface.MONOSPACE, Typeface.SERIF)) {
            body.typeface = font
            reading.typeface = font
            for (value in listOf("    漢字    ", "漢字")) {
                body.text = value
                reading.text = "かんじ"
                reading.visibility = View.GONE
                layout(view, dp(60))
                val before = bounds(view, body)
                reading.visibility = View.VISIBLE
                layout(view, dp(60))
                assertEquals(before, bounds(view, body))
                assertEquals(body.paint.measureText(value.takeWhile(Char::isWhitespace)), reading.leadingInsetPx, .01f)
                reading.visibility = View.GONE
                layout(view, dp(60))
                assertEquals(before, bounds(view, body))
            }
        }
    }

    private fun create() = LayoutInflater.from(context).inflate(R.layout.suggestion_item, null) as CandidateReadingLayout
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun layout(view: View, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
    private fun bounds(root: android.view.ViewGroup, view: View) = Rect(0, 0, view.width, view.height).also {
        root.offsetDescendantRectToMyCoords(view, it)
    }
}
