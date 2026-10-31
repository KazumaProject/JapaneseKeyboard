package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.utility.FormulaCandidatePresentation
import com.kazumaproject.markdownhelperkeyboard.converter.utility.FormulaCandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.utility.FormulaNode
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
        val originalMinimumWidth = holder.itemView.minimumWidth
        var clicked: Candidate? = null
        adapter.setOnItemClickListener { selected, _ -> clicked = selected }
        adapter.setSideCandidateVerticalText(true)
        adapter.onBindViewHolder(holder, 0)
        val text = holder.itemView.findViewById<TextView>(R.id.suggestion_item_text_view)
        val reading = holder.itemView.findViewById<View>(R.id.suggestion_item_yomi_text_view)
        val label = holder.itemView.findViewById<TextView>(R.id.suggestion_item_type_text_view)
        val divider = holder.itemView.findViewById<View>(R.id.candidate_divider)
        assertEquals("今\n日", text.text.toString())
        assertEquals("今日", text.contentDescription.toString())
        assertEquals(View.GONE, divider.visibility)
        assertTrue(holder.itemView.minimumWidth >= (48 * parent.resources.displayMetrics.density).toInt())
        assertEquals(View.GONE, reading.visibility)
        assertTrue(label.text.toString().contains("き\nょ\nう"))
        holder.itemView.performClick()
        assertSame(candidate, clicked)
        adapter.setSideCandidateVerticalText(false)
        adapter.onBindViewHolder(holder, 0)
        assertEquals("今日", text.text.toString().trim())
        assertEquals(View.VISIBLE, divider.visibility)
        assertEquals(1, text.maxLines)
        assertEquals(originalMinimumWidth, holder.itemView.minimumWidth)
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

    @Test fun formulaCandidatesRetainTheirPresentationInAFullColumn() {
        val formula = FormulaCandidatePresentation(FormulaNode.Symbol("x"), "x", "x", FormulaCandidateType.UNICODE)
        val candidate = Candidate("x", 1, 1u, 0, presentation = formula)
        val adapter = SuggestionAdapter()
        adapter.submitContent(CandidateStripContent.Candidates(listOf(candidate)))
        awaitItems(adapter, 1)
        val parent = FrameLayout(ApplicationProvider.getApplicationContext<Context>())
        val holder = adapter.createViewHolder(parent, adapter.getItemViewType(0))
        val originalWidth = holder.itemView.minimumWidth
        val originalHeight = holder.itemView.minimumHeight
        adapter.setSideCandidateVerticalText(true, columnWidth = 80, panelHeight = 300, panelWidth = 220)
        adapter.onBindViewHolder(holder, 0)
        assertEquals(View.VISIBLE, holder.itemView.findViewById<View>(R.id.suggestion_item_formula_view).visibility)
        assertTrue(holder.itemView.minimumWidth >= 220)
        assertTrue(holder.itemView.minimumHeight > originalHeight)
        adapter.setSideCandidateVerticalText(false)
        adapter.onBindViewHolder(holder, 0)
        assertEquals(originalWidth, holder.itemView.minimumWidth)
        assertEquals(originalHeight, holder.itemView.minimumHeight)
        adapter.release()
    }

    @Test fun longVerticalCandidateFitsThePanelWithoutChangingWhatIsCommitted() {
        val candidate = Candidate("あ".repeat(30), 1, 2u, 0, "あ")
        val adapter = SuggestionAdapter()
        adapter.submitContent(CandidateStripContent.Candidates(listOf(candidate)))
        awaitItems(adapter, 1)
        val parent = FrameLayout(ApplicationProvider.getApplicationContext<Context>())
        val holder = adapter.createViewHolder(parent, adapter.getItemViewType(0))
        var clicked: Candidate? = null
        adapter.setOnItemClickListener { selected, _ -> clicked = selected }
        adapter.setSideCandidateVerticalText(true, columnWidth = 100, panelHeight = 180, panelWidth = 200)
        adapter.onBindViewHolder(holder, 0)
        val text = holder.itemView.findViewById<TextView>(R.id.suggestion_item_text_view)
        assertTrue(text.maxLines in 1..29)
        assertTrue(text.text.toString().endsWith("…"))
        assertEquals(android.text.TextUtils.TruncateAt.END, text.ellipsize)
        assertEquals(candidate.string, text.contentDescription.toString())
        assertTrue(holder.itemView.minimumWidth >= 100)
        holder.itemView.performClick()
        assertSame(candidate, clicked)
        adapter.release()
    }

    @Test fun longCandidateWrapsIntoOrderedColumnsAndRestoresTheOriginalView() {
        val candidate = Candidate("あいうえおかきくけこさしすせそたちつてと👨‍👩‍👧‍👦が", 1, 2u, 0, "あ")
        val adapter = SuggestionAdapter()
        adapter.submitContent(CandidateStripContent.Candidates(listOf(candidate)))
        awaitItems(adapter, 1)
        val parent = FrameLayout(ApplicationProvider.getApplicationContext<Context>())
        val holder = adapter.createViewHolder(parent, adapter.getItemViewType(0))
        var clicked: Candidate? = null
        adapter.setOnItemClickListener { selected, _ -> clicked = selected }
        adapter.setSideCandidateVerticalText(true, columnWidth = 100, panelHeight = 150, panelWidth = 200,
            wrapLongText = true, rightToLeft = true)
        adapter.onBindViewHolder(holder, 0)
        val text = holder.itemView.findViewById<TextView>(R.id.suggestion_item_text_view)
        val columns = text.parent as android.widget.LinearLayout
        assertTrue(columns.childCount > 1)
        assertEquals(View.LAYOUT_DIRECTION_RTL, columns.layoutDirection)
        assertEquals(candidate.string, (0 until columns.childCount).joinToString("") {
            (columns.getChildAt(it) as TextView).text.toString().replace("\n", "")
        })
        assertEquals(candidate.string, text.contentDescription.toString())
        assertNull(text.ellipsize)
        val compactWidth = columns.getChildAt(0).layoutParams.width
        assertTrue(holder.itemView.layoutParams.width >= compactWidth * columns.childCount)
        adapter.setSideCandidateVerticalText(true, columnWidth = 100, panelHeight = 150, panelWidth = 200,
            wrapLongText = true, rightToLeft = true, spacingDp = 24)
        adapter.onBindViewHolder(holder, 0)
        val widerColumns = text.parent as android.widget.LinearLayout
        assertTrue(widerColumns.getChildAt(0).layoutParams.width > compactWidth)
        holder.itemView.performClick()
        assertSame(candidate, clicked)
        adapter.setSideCandidateVerticalText(false)
        adapter.onBindViewHolder(holder, 0)
        assertEquals(candidate.string, text.text.toString().trim())
        assertEquals(View.VISIBLE, holder.itemView.findViewById<View>(R.id.candidate_divider).visibility)
        adapter.release()
    }

    @Test fun longZeroQueryCandidateAlsoWrapsWithoutChangingItsValue() {
        val candidate = Candidate("ながい候補".repeat(8), 1, 1u, 0)
        val adapter = SuggestionAdapter()
        adapter.submitContent(CandidateStripContent.ZeroQuerySuggestions(listOf(candidate)))
        awaitItems(adapter, 2)
        val parent = FrameLayout(ApplicationProvider.getApplicationContext<Context>())
        val holder = adapter.createViewHolder(parent, adapter.getItemViewType(1))
        var clicked: Candidate? = null
        adapter.setOnZeroQueryCandidateClickListener { clicked = it }
        adapter.setSideCandidateVerticalText(true, columnWidth = 100, panelHeight = 150, panelWidth = 200, wrapLongText = true)
        adapter.onBindViewHolder(holder, 1)
        val text = holder.itemView.findViewById<TextView>(R.id.zero_query_item_text_view)
        val columns = text.parent as android.widget.LinearLayout
        assertTrue(columns.childCount > 1)
        assertEquals(candidate.string, (0 until columns.childCount).joinToString("") {
            (columns.getChildAt(it) as TextView).text.toString().replace("\n", "")
        })
        holder.itemView.performClick()
        assertSame(candidate, clicked)
        adapter.setSideCandidateVerticalText(false)
        adapter.onBindViewHolder(holder, 1)
        assertEquals(candidate.string, text.text.toString().trim())
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
