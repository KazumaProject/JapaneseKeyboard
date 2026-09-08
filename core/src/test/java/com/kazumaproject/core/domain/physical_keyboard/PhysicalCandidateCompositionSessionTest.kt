package com.kazumaproject.core.domain.physical_keyboard

import org.junit.Assert.*
import org.junit.Test

class PhysicalCandidateCompositionSessionTest {
    @Test fun cursorTailIsPartOfSourceBeforeFirstPreview() {
        val session = PhysicalCandidateCompositionSession("あした", "は", 1)
        assertEquals("明日は", session.resolve("明日", 3)!!.text)
        assertEquals("あしたは", session.sourceText)
    }

    @Test fun repeatedSuffixIsNotMistakenForAlreadyIncludedTail() {
        val session = PhysicalCandidateCompositionSession("ここ", "こ", 1)
        assertEquals("此処こ", session.resolve("此処", 2)!!.text)
    }

    @Test fun shorteningThenExpandingCandidateKeepsOriginalReading() {
        val session = PhysicalCandidateCompositionSession("あしたはれるといいですね", "はれた", 1)
        assertEquals("明日はれるといいですねはれた", session.resolve("明日", 3)!!.text)
        val expanded = session.resolve("明日晴れると良いですね", session.queryText.length)!!
        assertEquals("明日晴れると良いですねはれた", expanded.text)
        assertEquals("はれた", expanded.tail)
        assertEquals("あしたはれるといいですねはれた", session.sourceText)
    }

    @Test fun enterCommitsOnlySelectedReading() {
        val session = PhysicalCandidateCompositionSession("あしたはれる", "", 1)
        val result = session.commit(session.resolve("明日", 3)!!, false)
        assertEquals("明日", result.committedText)
        assertEquals("はれる", result.remainingReading)
        val next = PhysicalCandidateCompositionSession(result.remainingReading, "", 2)
        assertEquals("晴れる", next.commit(next.resolve("晴れる", 3)!!, false).committedText)
    }

    @Test fun typingCommitsCandidateAndAllRemainingReading() {
        val session = PhysicalCandidateCompositionSession("あしたはれる", "かな", 1)
        val result = session.commit(session.resolve("明日", 3)!!, true)
        assertEquals("明日はれるかな", result.committedText)
        assertEquals("", result.remainingReading)
    }

    @Test fun previewNeverReplacesReadingUsedToCancel() {
        val session = PhysicalCandidateCompositionSession("あした", "は", 1)
        session.resolve("明日", 3)
        session.resolve("足", 2)
        assertEquals("あしたは", session.sourceText)
    }

    @Test fun candidateCannotConsumeUnqueriedTail() {
        val session = PhysicalCandidateCompositionSession("あした", "は", 1)
        assertNull(session.resolve("明日は", 4))
        assertNull(session.resolve("", 0))
        assertNull(session.resolve("明日", -1))
    }

    @Test fun sourceOffsetsAreUtf16() {
        val session = PhysicalCandidateCompositionSession("😀かな", "！", 1)
        assertEquals("絵かな！", session.resolve("絵", 2)!!.text)
    }
}
