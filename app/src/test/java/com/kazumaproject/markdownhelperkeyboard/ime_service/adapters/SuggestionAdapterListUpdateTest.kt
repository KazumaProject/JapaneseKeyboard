package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SuggestionAdapterListUpdateTest {

    @Test
    fun settingEqualSuggestionListDoesNotNotifyListUpdated() {
        val adapter = SuggestionAdapter()
        val candidates = listOf(candidate("今日"), candidate("京"))
        val firstUpdate = CountDownLatch(1)
        val updateCount = AtomicInteger(0)
        adapter.onListUpdated = {
            updateCount.incrementAndGet()
            firstUpdate.countDown()
        }

        adapter.submitContent(
            CandidateStripContent.Candidates(
                candidates = candidates,
                showShortcutEntry = false
            )
        )
        drainMainUntil(firstUpdate)
        assertTrue("first candidate update should be committed", firstUpdate.await(0, TimeUnit.MILLISECONDS))
        updateCount.set(0)

        adapter.submitContent(
            CandidateStripContent.Candidates(
                candidates = candidates.toList(),
                showShortcutEntry = false
            )
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, updateCount.get())
        adapter.release()
    }

    @Test
    fun incognitoQuickActionRequestsStartAnchorWithoutSuggestionListUpdate() {
        val adapter = SuggestionAdapter()
        val shortcuts = listOf(ShortcutType.SETTINGS, ShortcutType.EMOJI)
        adapter.submitContent(CandidateStripContent.IntegratedShortcuts(shortcuts))
        drainMainUntil { adapter.itemCount == 2 }

        val startAnchorCount = AtomicInteger(0)
        val listUpdateCount = AtomicInteger(0)
        val firstAnchor = CountDownLatch(1)
        adapter.onStartAnchoredContentCommitted = {
            startAnchorCount.incrementAndGet()
            firstAnchor.countDown()
        }
        adapter.onListUpdated = {
            listUpdateCount.incrementAndGet()
        }

        adapter.setIncognitoIcon(ColorDrawable(Color.BLACK))
        adapter.submitContent(
            CandidateStripContent.EmptyStateActions(
                incognitoVisible = true,
                undoEnabled = false,
                redoEnabled = false,
                reconvertEnabled = false,
                undoText = "",
                redoText = "",
                shortcutItems = shortcuts,
                showIntegratedShortcuts = true
            )
        )

        drainMainUntil(firstAnchor)
        assertTrue(
            "incognito leading action should request a start anchor",
            firstAnchor.await(0, TimeUnit.MILLISECONDS)
        )
        assertEquals(1, startAnchorCount.get())
        assertEquals(0, listUpdateCount.get())
        adapter.release()
    }

    private fun drainMainUntil(latch: CountDownLatch) {
        drainMainUntil { latch.count == 0L }
    }

    private fun drainMainUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private fun candidate(string: String): Candidate {
        return Candidate(
            string = string,
            type = 1.toByte(),
            length = string.length.toUByte(),
            score = 0,
            yomi = string
        )
    }
}
