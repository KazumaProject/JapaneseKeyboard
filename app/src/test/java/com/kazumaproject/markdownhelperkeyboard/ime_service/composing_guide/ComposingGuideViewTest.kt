package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class ComposingGuideViewTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun px(dp: Int) = (dp * context.resources.displayMetrics.density).toInt()
    private fun view(editing: Boolean): ComposingGuideView = ComposingGuideView(
        context, onEdit = {}, onTextSize = { _, _ -> }, onHandleEvent = {},
    ).apply {
        setEditing(editing)
        measure(View.MeasureSpec.makeMeasureSpec(px(280), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(px(if (editing) 384 else 288), View.MeasureSpec.EXACTLY))
        layout(0, 0, measuredWidth, measuredHeight)
    }

    @Test fun narrowPanelKeepsLargeTextReadingAndTheEditorSliderInsideItsBounds() {
        val view = view(false)
        view.setContent("未確定文字", 56f, "みかくていもじ")
        val textHeight = ComposingGuideView.composingLineHeight(context, 56f)
        val readingHeight = ComposingGuideView.readingLineHeight(context, 56f)
        val height = px(60 + 8 + 48 + 24) + textHeight + readingHeight
        fun layout(extra: Int) {
            view.measure(View.MeasureSpec.makeMeasureSpec(px(160), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height + px(extra), View.MeasureSpec.EXACTLY))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
        layout(0)
        val composing = view.findViewById<android.widget.TextView>(com.kazumaproject.markdownhelperkeyboard.R.id.composing_guide_composing_text)
        val reading = view.findViewById<android.widget.TextView>(com.kazumaproject.markdownhelperkeyboard.R.id.composing_guide_reading_text)
        assertEquals(px(144), (composing.parent as View).width)
        org.junit.Assert.assertTrue(view.candidateContainer.height >= px(48))
        assertEquals(readingHeight, (reading.parent as View).height)
        view.setEditing(true)
        layout(ComposingGuideView.EDIT_EXTRA_DP)
        fun slider(group: android.view.ViewGroup): android.widget.SeekBar? {
            for (index in 0 until group.childCount) {
                val child = group.getChildAt(index)
                if (child is android.widget.SeekBar) return child
                if (child is android.view.ViewGroup) slider(child)?.let { return it }
            }
            return null
        }
        org.junit.Assert.assertTrue(slider(view)!!.width >= px(100))
        org.junit.Assert.assertTrue(view.candidateContainer.height >= px(48))
    }

    @Test fun normalModeMovesFromTheEntireBottomBandOnly() {
        val view = view(false)
        for (x in listOf(1f, view.width / 2f, view.width - 1f)) {
            for (y in listOf(view.height - px(24) + 1f, view.height - px(12).toFloat(), view.height - 1f)) {
                assertEquals(GuideHandle.MOVE, view.handleAt(x, y))
            }
        }
        assertNull(view.handleAt(view.width / 2f, view.height - px(24) - 1f))
        assertNull(view.handleAt(1f, view.height / 2f))
    }

    @Test fun editingDisablesTheWholeMoveBandAndSeparatesBottomResize() {
        val view = view(true)
        for (x in listOf(1f, view.width / 2f, view.width - 1f)) {
            for (y in listOf(view.height - px(24) + 1f, view.height - px(12).toFloat(), view.height - 1f)) {
                assertNull(view.handleAt(x, y))
            }
        }
        assertEquals(GuideHandle.BOTTOM, view.handleAt(view.width / 2f, view.height - px(36).toFloat()))
        assertEquals(GuideHandle.TOP, view.handleAt(view.width / 2f, px(12).toFloat()))
    }

    @Test fun finishingEditingRestoresMoveBand() {
        val view = view(true)
        view.setEditing(false)
        assertEquals(GuideHandle.MOVE, view.handleAt(view.width / 2f, view.height - px(12).toFloat()))
    }
    @Test fun composingAndReadingUseSeparateFullWidthSingleLinesWithoutResettingManualScroll() {
        val view = view(false)
        val text = "長い未確定文字を表示".repeat(10)
        view.setContent(text, 28f, "ながいよみ".repeat(10))
        view.measure(View.MeasureSpec.makeMeasureSpec(px(200), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(px(400), View.MeasureSpec.EXACTLY))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val composing = view.findViewById<android.widget.TextView>(com.kazumaproject.markdownhelperkeyboard.R.id.composing_guide_composing_text)
        val reading = view.findViewById<android.widget.TextView>(com.kazumaproject.markdownhelperkeyboard.R.id.composing_guide_reading_text)
        val scroller = composing.parent as android.widget.HorizontalScrollView
        org.junit.Assert.assertTrue(scroller.scrollX > 0)
        org.junit.Assert.assertTrue((reading.parent as android.widget.HorizontalScrollView).scrollX > 0)
        assertEquals(px(184), scroller.width)
        assertEquals(1, composing.lineCount)
        assertEquals(1, reading.lineCount)
        scroller.scrollTo(0, 0)
        view.setContent(text, 28f, reading.text.toString())
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(0, scroller.scrollX)
        view.setContent(text + "追記", 28f, reading.text.toString())
        view.measure(View.MeasureSpec.makeMeasureSpec(px(200), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(px(400), View.MeasureSpec.EXACTLY))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        org.junit.Assert.assertTrue(scroller.scrollX > 0)
        view.setShowComposing(false)
        assertEquals(View.GONE, scroller.visibility)
        assertEquals(View.GONE, (reading.parent as View).visibility)
    }

}
