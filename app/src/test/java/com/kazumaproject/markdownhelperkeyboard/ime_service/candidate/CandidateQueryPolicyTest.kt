package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.session.CandidateQueryMode
import com.kazumaproject.markdownhelperkeyboard.converter.session.ConversionBackend
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.CandidateTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateQueryPolicyTest {

    private val order = listOf(
        CandidateTab.PREDICTION,
        CandidateTab.CONVERSION,
        CandidateTab.EISUKANA,
    )

    @Test
    fun hiddenTabsAlwaysUseNoTabDefault() {
        order.indices.forEach { position ->
            assertEquals(
                CandidateQueryMode.NO_TAB_DEFAULT,
                CandidateQueryModeResolver.resolve(false, order, position),
            )
        }
    }

    @Test
    fun visibleTabsResolveConfiguredOrder() {
        assertEquals(
            CandidateQueryMode.PREDICTION,
            CandidateQueryModeResolver.resolve(true, order, 0),
        )
        assertEquals(
            CandidateQueryMode.CONVERSION,
            CandidateQueryModeResolver.resolve(true, order, 1),
        )
        assertEquals(
            CandidateQueryMode.EISUKANA,
            CandidateQueryModeResolver.resolve(true, order, 2),
        )
    }

    @Test
    fun missingOrInvalidSelectionFallsBackToPrediction() {
        assertEquals(
            CandidateQueryMode.PREDICTION,
            CandidateQueryModeResolver.resolve(true, order, -1),
        )
        assertEquals(
            CandidateQueryMode.PREDICTION,
            CandidateQueryModeResolver.resolve(true, emptyList(), 0),
        )
        assertEquals(
            CandidateQueryMode.PREDICTION,
            CandidateQueryModeResolver.resolve(true, order, order.size),
        )
    }

    @Test
    fun sameInputFromPreviousTabIsRejected() {
        val tracker = CandidateRequestTracker()
        tracker.restart(ConversionBackend.INCREMENTAL_SESSION)

        val prediction = tracker.begin(
            input = "きょう",
            mode = CandidateQueryMode.PREDICTION,
            backend = ConversionBackend.INCREMENTAL_SESSION,
        )
        val conversion = tracker.begin(
            input = "きょう",
            mode = CandidateQueryMode.CONVERSION,
            backend = ConversionBackend.INCREMENTAL_SESSION,
        )

        assertFalse(tracker.isCurrent(prediction))
        assertTrue(tracker.isCurrent(conversion))
    }

    @Test
    fun previousInputSessionIsRejected() {
        val tracker = CandidateRequestTracker()
        tracker.restart(ConversionBackend.LEGACY)
        val old = tracker.begin(
            input = "かな",
            mode = CandidateQueryMode.NO_TAB_DEFAULT,
            backend = ConversionBackend.LEGACY,
        )

        tracker.restart(ConversionBackend.INCREMENTAL_SESSION)

        assertFalse(tracker.isCurrent(old))
    }

    @Test
    fun sameInputFromAnEarlierEditorMutationIsRejected() {
        val tracker = CandidateRequestTracker()
        tracker.restart(
            backend = ConversionBackend.LEGACY,
            editorMutationRevision = 4L,
        )
        val beforeDelete = tracker.begin(
            input = "test",
            mode = CandidateQueryMode.NO_TAB_DEFAULT,
            backend = ConversionBackend.LEGACY,
            editorMutationRevision = 4L,
        )
        val afterDeleteAndRetype = tracker.begin(
            input = "test",
            mode = CandidateQueryMode.NO_TAB_DEFAULT,
            backend = ConversionBackend.LEGACY,
            editorMutationRevision = 6L,
        )

        assertFalse(tracker.isCurrent(beforeDelete))
        assertTrue(tracker.isCurrent(afterDeleteAndRetype))
        assertEquals(4L, beforeDelete.editorMutationRevision)
        assertEquals(6L, afterDeleteAndRetype.editorMutationRevision)
    }
}
