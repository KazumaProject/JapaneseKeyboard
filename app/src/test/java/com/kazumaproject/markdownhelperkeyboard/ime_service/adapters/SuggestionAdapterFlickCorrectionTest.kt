package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import android.os.Looper
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.*
import com.kazumaproject.markdownhelperkeyboard.converter.graph.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ja-rJP")
class SuggestionAdapterFlickCorrectionTest {
    @Test fun correctionLabelsUseTypeTextAndClearWhenHolderIsRecycled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = SuggestionAdapter()
        val holder = adapter.onCreateViewHolder(FrameLayout(context),
            SuggestionAdapter.VIEW_TYPE_SUGGESTION) as SuggestionAdapter.SuggestionViewHolder
        val edit = FlickCorrectionEdit(FlickCorrectionKind.MISSING, 2, 2, "に", 1800)
        try {
            for ((type, info, label) in listOf(
                Triple(CANDIDATE_TYPE_FLICK_TYPO_CORRECTION, FlickCorrectionInfo(listOf(edit)), "[補正]"),
                Triple(CANDIDATE_TYPE_FLICK_TYPO_CORRECTION, FlickCorrectionInfo(listOf(edit), 5), "[部][補正]"),
                Triple(1.toByte(), null, ""),
                Triple(5.toByte(), null, "[部]"),
            )) {
                adapter.suggestions = emptyList()
                awaitCount(adapter, 0)
                adapter.suggestions = listOf(Candidate("こんにちは", type, 4u, 3000,
                    yomi = "こんにちは", flickCorrection = info))
                awaitCount(adapter, 1)
                adapter.onBindViewHolder(holder, 0)
                assertEquals(label, holder.typeText.text.toString())
            }
        } finally { adapter.release() }
    }

    @Test fun rubyUsesCorrectedReadingsWithOriginalInputRanges() {
        val edit = FlickCorrectionEdit(FlickCorrectionKind.MISSING, 2, 2, "し", 1800)
        val annotations = resolveCandidateRubyAnnotations("明日行きます", "あしたいきます", listOf(
            CandidateConversionSegment(0, 2, "明日", "あした", FlickCorrectionInfo(listOf(edit))),
            CandidateConversionSegment(2, 6, "行きます", "いきます"),
        ))
        assertEquals(listOf(CandidateRubyAnnotation(0, 2, "あした"),
            CandidateRubyAnnotation(2, 3, "い")), annotations)
    }

    private fun awaitCount(adapter: SuggestionAdapter, count: Int) {
        repeat(100) {
            shadowOf(Looper.getMainLooper()).idle()
            if (adapter.itemCount == count) return
            Thread.sleep(5)
        }
        assertEquals(count, adapter.itemCount)
    }
}
