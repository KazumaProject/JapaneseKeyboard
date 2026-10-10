package com.kazumaproject.markdownhelperkeyboard.ime_service.flick_preview

import com.kazumaproject.core.domain.flick.FlickInputEvidence
import org.junit.Assert.*
import org.junit.Test

class FlickEvidenceTrackerTest {
    private val sample = FlickInputEvidence('か', mapOf('き' to 0.6f))

    @Test fun appendBackspaceAndModifierPreserveCorrespondingGestures() {
        val tracker = FlickEvidenceTracker()
        tracker.record("", "か", sample)
        tracker.record("か", "かき", FlickInputEvidence('き', emptyMap()))
        tracker.record("かき", "か", null)
        assertEquals(listOf(sample), tracker.snapshot("か").evidence)
        tracker.record("か", "が", null)
        assertEquals('が', tracker.snapshot("が").evidence.single()!!.observedChar)
        tracker.clear()
        assertTrue(tracker.snapshot("が").evidence.isEmpty())
    }

    @Test fun toggleInputDropsTheEvidenceForTheReplacedCharacter() {
        val tracker = FlickEvidenceTracker()
        tracker.record("", "か", sample)
        tracker.record("か", "かか", sample)
        tracker.record("かか", "かき", FlickInputEvidence('か', emptyMap()))
        assertEquals(listOf(sample, null), tracker.snapshot("かき").evidence)
    }

    @Test fun staleQueryNeverRewindsNewerGestureHistory() {
        val tracker = FlickEvidenceTracker()
        tracker.record("", "か", sample)
        tracker.record("か", "かき", FlickInputEvidence('き', emptyMap()))
        assertTrue(tracker.snapshot("か").evidence.isEmpty())
        assertEquals(2, tracker.snapshot("かき").evidence.size)
    }

    @Test fun CursorEditOrPartialCommitInvalidatesUncertainCorrespondence() {
        val tracker = FlickEvidenceTracker()
        tracker.record("", "か", sample)
        tracker.record("か", "かき", FlickInputEvidence('き', emptyMap()))
        tracker.record("かき", "き", null)
        assertTrue(tracker.snapshot("き").evidence.all { it == null })
    }
}
