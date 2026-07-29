package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.ime_service.models.CandidateShowFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteLongPressConversionGateTest {
    @Test
    fun repeat_rendersRawTextAndRejectsCandidateResults() {
        val gate = DeleteLongPressConversionGate()

        assertTrue(gate.beginRepeat())
        assertTrue(gate.shouldRenderRawComposing("たなか"))
        assertFalse(gate.shouldRenderRawComposing(""))
        assertFalse(gate.mayApplyCandidateResult())
    }

    @Test
    fun refreshObservedDuringRepeat_doesNotReopenCandidateGate() {
        val gate = DeleteLongPressConversionGate()
        gate.beginRepeat()

        gate.onCandidateRefreshStarted()

        assertFalse(gate.mayApplyCandidateResult())
    }

    @Test
    fun partialDelete_resumesWithExactlyOneUpdatingRefresh() {
        val gate = DeleteLongPressConversionGate()
        gate.beginRepeat()

        assertEquals(CandidateShowFlag.Updating, gate.finishRepeat("たな"))
        assertFalse(gate.mayApplyCandidateResult())
        assertNull(gate.finishRepeat("たな"))

        gate.onCandidateRefreshStarted()
        assertTrue(gate.mayApplyCandidateResult())
    }

    @Test
    fun fullDelete_resumesWithExactlyOneIdleRefresh() {
        val gate = DeleteLongPressConversionGate()
        gate.beginRepeat()

        assertEquals(CandidateShowFlag.Idle, gate.finishRepeat(""))
        assertFalse(gate.mayApplyCandidateResult())

        gate.onCandidateRefreshStarted()
        assertTrue(gate.mayApplyCandidateResult())
    }
}
