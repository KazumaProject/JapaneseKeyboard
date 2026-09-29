package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import android.graphics.Typeface
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.ClipboardPreviewState
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.QuickActionsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SuggestionAdapterFontRebindTest {
    @Test
    fun everyTextHolderUsesFontChangesWhenTheSameHolderIsRebound() {
        val scenarios = listOf(
            CandidateStripContent.Candidates(listOf(candidate("candidate"))) to 0,
            CandidateStripContent.ZeroQuerySuggestions(listOf(candidate("zero query"))) to 1,
            CandidateStripContent.SelectionActions(
                actions = listOf(candidate("selection action")),
                showShortcutEntry = false,
            ) to 0,
            CandidateStripContent.EmptyState(
                showShortcutEntry = false,
                quickActions = QuickActionsState(
                    incognitoVisible = false,
                    undoEnabled = false,
                    redoEnabled = false,
                    reconvertEnabled = false,
                    undoText = "",
                    redoText = "",
                ),
                clipboardPreview = ClipboardPreviewState(
                    text = "clipboard",
                    bitmap = null,
                    descriptionShown = true,
                    tapToDelete = false,
                ),
                shortcutItems = emptyList(),
                showIntegratedShortcuts = false,
            ) to 0,
        )

        scenarios.forEach { (content, position) -> assertFontCycle(content, position) }
    }

    private fun assertFontCycle(content: CandidateStripContent, position: Int) {
        val adapter = SuggestionAdapter()
        adapter.submitContent(content)
        drainMainUntil { adapter.itemCount > position }
        assertTrue("content should contain the requested holder", adapter.itemCount > position)

        val parent = FrameLayout(ApplicationProvider.getApplicationContext<Context>())
        val holder = adapter.createViewHolder(parent, adapter.getItemViewType(position))
        adapter.onBindViewHolder(holder, position)
        val textViews = collectTextViews(holder.itemView)
        assertFalse("holder should have text targets", textViews.isEmpty())
        val standardTypefaces = textViews.map { it.typeface }
        val baseRevision = System.nanoTime()
        val fontA = Typeface.create("serif", Typeface.NORMAL)
        val fontB = Typeface.create("monospace", Typeface.NORMAL)

        adapter.setKeyboardFont(KeyboardFontSnapshot(fontA, baseRevision))
        adapter.onBindViewHolder(holder, position)
        assertEquals(
            standardTypefaces.map { Typeface.create(fontA, it?.style ?: Typeface.NORMAL) },
            textViews.map { it.typeface },
        )

        adapter.setKeyboardFont(KeyboardFontSnapshot(fontB, baseRevision + 1))
        adapter.onBindViewHolder(holder, position)
        assertEquals(
            standardTypefaces.map { Typeface.create(fontB, it?.style ?: Typeface.NORMAL) },
            textViews.map { it.typeface },
        )

        adapter.setKeyboardFont(KeyboardFontSnapshot(null, baseRevision + 2))
        adapter.onBindViewHolder(holder, position)
        assertEquals(standardTypefaces, textViews.map { it.typeface })
        adapter.release()
    }

    private fun collectTextViews(root: View): List<TextView> = buildList {
        if (root is TextView) add(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) addAll(collectTextViews(root.getChildAt(index)))
        }
    }

    private fun drainMainUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 2_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun candidate(text: String) = Candidate(
        string = text,
        type = 1,
        length = text.length.toUByte(),
        score = 0,
        yomi = text,
    )
}
