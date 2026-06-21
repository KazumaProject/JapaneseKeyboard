package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import android.os.Looper
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent
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
            CandidateStripContent.ClipboardPreview(
                text = "clip",
                bitmap = null,
                descriptionShown = true,
                tapToDelete = false,
                showShortcutEntry = true,
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

    private fun drainMainUntilItemCount(adapter: SuggestionAdapter, expectedItemCount: Int) {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            if (adapter.itemCount >= expectedItemCount) return
            Thread.sleep(10)
        }
    }
}
