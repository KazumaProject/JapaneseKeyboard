package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SideCandidateVerticalTextTest {
    @Test fun verticalPresentationKeepsTheCandidateAndRestoresHorizontalText() {
        val candidate = Candidate("今日", 1, 2u, 0, "きょう")
        val adapter = SuggestionAdapter()
        adapter.setShowCandidateYomiForLiveConversion(true)
        adapter.submitContent(CandidateStripContent.Candidates(listOf(candidate)))
        awaitItems(adapter, 1)
        val parent = FrameLayout(ApplicationProvider.getApplicationContext<Context>())
        val holder = adapter.createViewHolder(parent, adapter.getItemViewType(0))
        var clicked: Candidate? = null
        adapter.setOnItemClickListener { selected, _ -> clicked = selected }
        adapter.setSideCandidateVerticalText(true)
        adapter.onBindViewHolder(holder, 0)
        val text = holder.itemView.findViewById<TextView>(R.id.suggestion_item_text_view)
        val reading = holder.itemView.findViewById<View>(R.id.suggestion_item_yomi_text_view)
        val label = holder.itemView.findViewById<TextView>(R.id.suggestion_item_type_text_view)
        assertEquals("今\n日", text.text.toString())
        assertEquals("今日", text.contentDescription.toString())
        assertEquals(View.GONE, reading.visibility)
        assertTrue(label.text.toString().contains("き\nょ\nう"))
        holder.itemView.performClick()
        assertSame(candidate, clicked)
        adapter.setSideCandidateVerticalText(false)
        adapter.onBindViewHolder(holder, 0)
        assertEquals("今日", text.text.toString().trim())
        assertEquals(1, text.maxLines)
        assertEquals(View.VISIBLE, reading.visibility)
        adapter.release()
    }

    @Test fun zeroQueryItemsRestoreTheirOriginalHeightAndText() {
        val candidate = Candidate("今日", 1, 2u, 0, "きょう")
        val adapter = SuggestionAdapter()
        adapter.submitContent(CandidateStripContent.ZeroQuerySuggestions(listOf(candidate)))
        awaitItems(adapter, 2)
        val parent = FrameLayout(ApplicationProvider.getApplicationContext<Context>())
        val holder = adapter.createViewHolder(parent, adapter.getItemViewType(1))
        val height = holder.itemView.layoutParams.height
        adapter.setSideCandidateVerticalText(true)
        adapter.onBindViewHolder(holder, 1)
        val text = holder.itemView.findViewById<TextView>(R.id.zero_query_item_text_view)
        assertEquals("今\n日", text.text.toString())
        adapter.setSideCandidateVerticalText(false)
        adapter.onBindViewHolder(holder, 1)
        assertEquals("今日", text.text.toString().trim())
        assertEquals(height, holder.itemView.layoutParams.height)
        adapter.release()
    }

    private fun awaitItems(adapter: SuggestionAdapter, count: Int) {
        val deadline = System.nanoTime() + 2_000_000_000L
        while (adapter.itemCount < count && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue(adapter.itemCount >= count)
    }
}
