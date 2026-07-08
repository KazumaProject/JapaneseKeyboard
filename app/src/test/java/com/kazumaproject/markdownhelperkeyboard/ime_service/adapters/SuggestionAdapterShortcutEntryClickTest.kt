package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import android.os.Looper
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
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
class SuggestionAdapterShortcutEntryClickTest {

    @Test
    fun shortcutEntryClickNotifiesListener() {
        val adapter = SuggestionAdapter()
        adapter.submitContent(
            CandidateStripContent.EmptyState(
                showShortcutEntry = true,
                quickActions = QuickActionsState(
                    incognitoVisible = false,
                    undoEnabled = false,
                    redoEnabled = false,
                    reconvertEnabled = false,
                    undoText = "",
                    redoText = "",
                ),
                clipboardPreview = ClipboardPreviewState(
                    text = "clip",
                    bitmap = null,
                    descriptionShown = true,
                    tapToDelete = false,
                ),
                shortcutItems = emptyList(),
                showIntegratedShortcuts = false,
            )
        )
        drainMainUntilItemCount(adapter, expectedItemCount = 2)

        var clicked = false
        adapter.setOnShortcutEntryClickListener {
            clicked = true
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = FrameLayout(context)
        val holder = adapter.onCreateViewHolder(
            parent,
            SuggestionAdapter.VIEW_TYPE_SHORTCUT_ENTRY
        )
        adapter.onBindViewHolder(holder, 0)

        holder.itemView.performClick()

        assertTrue(clicked)
        adapter.release()
    }

    @Test
    fun zeroQueryCandidateClickNotifiesDedicatedListenerOnly() {
        val adapter = SuggestionAdapter()
        val zeroQueryCandidate = candidate("おめでとうございます")
        adapter.submitContent(
            CandidateStripContent.ZeroQuerySuggestions(
                candidates = listOf(zeroQueryCandidate)
            )
        )
        drainMainUntilItemCount(adapter, expectedItemCount = 2)

        var normalCandidateClicked = false
        var zeroQueryClicked: Candidate? = null
        adapter.setOnItemClickListener { _, _ ->
            normalCandidateClicked = true
        }
        adapter.setOnZeroQueryCandidateClickListener { candidate ->
            zeroQueryClicked = candidate
        }

        val holder = createSuggestionHolder(adapter)
        adapter.onBindViewHolder(holder, 1)

        holder.itemView.performClick()

        assertEquals(zeroQueryCandidate, zeroQueryClicked)
        assertFalse(normalCandidateClicked)
        adapter.release()
    }

    @Test
    fun zeroQueryCloseClickNotifiesDedicatedListenerOnly() {
        val adapter = SuggestionAdapter()
        adapter.submitContent(
            CandidateStripContent.ZeroQuerySuggestions(
                candidates = listOf(candidate("おめでとうございます"))
            )
        )
        drainMainUntilItemCount(adapter, expectedItemCount = 2)

        var normalCandidateClicked = false
        var closeClicked = false
        adapter.setOnItemClickListener { _, _ ->
            normalCandidateClicked = true
        }
        adapter.setOnZeroQueryCloseClickListener {
            closeClicked = true
        }

        val holder = createSuggestionHolder(adapter)
        adapter.onBindViewHolder(holder, 0)

        holder.itemView.performClick()

        assertTrue(closeClicked)
        assertFalse(normalCandidateClicked)
        adapter.release()
    }

    @Test
    fun hiddenZeroQueryToggleClickNotifiesDedicatedListenerOnly() {
        val adapter = SuggestionAdapter()
        adapter.submitContent(
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
                clipboardPreview = null,
                shortcutItems = emptyList(),
                showIntegratedShortcuts = false,
                showZeroQueryToggle = true,
            )
        )
        drainMainUntilItemCount(adapter, expectedItemCount = 1)

        var normalCandidateClicked = false
        var closeClicked = false
        adapter.setOnItemClickListener { _, _ ->
            normalCandidateClicked = true
        }
        adapter.setOnZeroQueryCloseClickListener {
            closeClicked = true
        }

        val holder = createSuggestionHolder(adapter)
        adapter.onBindViewHolder(holder, 0)

        holder.itemView.performClick()

        assertTrue(closeClicked)
        assertFalse(normalCandidateClicked)
        adapter.release()
    }

    @Test
    fun zeroQueryCandidateLongPressDoesNothing() {
        val adapter = SuggestionAdapter()
        adapter.submitContent(
            CandidateStripContent.ZeroQuerySuggestions(
                candidates = listOf(candidate("おめでとうございます"))
            )
        )
        drainMainUntilItemCount(adapter, expectedItemCount = 2)

        var normalCandidateLongClicked = false
        adapter.setOnItemLongClickListener { _, _ ->
            normalCandidateLongClicked = true
        }

        val holder = createSuggestionHolder(adapter)
        adapter.onBindViewHolder(holder, 1)

        assertTrue(holder.itemView.performLongClick())
        assertFalse(normalCandidateLongClicked)
        adapter.release()
    }

    private fun createSuggestionHolder(adapter: SuggestionAdapter): androidx.recyclerview.widget.RecyclerView.ViewHolder {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = FrameLayout(context)
        return adapter.onCreateViewHolder(
            parent,
            SuggestionAdapter.VIEW_TYPE_SUGGESTION
        )
    }

    private fun candidate(text: String): Candidate =
        Candidate(
            string = text,
            type = 9.toByte(),
            length = text.length.toUByte(),
            score = 0,
            yomi = text
        )

    private fun drainMainUntilItemCount(adapter: SuggestionAdapter, expectedItemCount: Int) {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            if (adapter.itemCount >= expectedItemCount) return
            Thread.sleep(10)
        }
    }
}
