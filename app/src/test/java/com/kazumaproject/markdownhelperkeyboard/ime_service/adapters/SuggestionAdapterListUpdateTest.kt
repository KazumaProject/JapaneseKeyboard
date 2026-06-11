package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.os.Looper
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
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

        adapter.suggestions = candidates
        drainMainUntil(firstUpdate)
        assertTrue("first candidate update should be committed", firstUpdate.await(0, TimeUnit.MILLISECONDS))
        updateCount.set(0)

        adapter.suggestions = candidates.toList()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, updateCount.get())
        adapter.release()
    }

    private fun drainMainUntil(latch: CountDownLatch) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (latch.count > 0 && System.nanoTime() < deadline) {
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
