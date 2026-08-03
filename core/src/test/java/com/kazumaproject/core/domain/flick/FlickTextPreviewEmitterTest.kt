package com.kazumaproject.core.domain.flick

import org.junit.Assert.assertEquals
import org.junit.Test

class FlickTextPreviewEmitterTest {
    @Test
    fun emitsLifecycleAndSuppressesDuplicateSelection() {
        val events = mutableListOf<FlickTextPreviewEvent>()
        val emitter = FlickTextPreviewEmitter().apply {
            listener = FlickTextPreviewListener(events::add)
        }

        emitter.begin(FlickTextSelection("あ", false))
        emitter.update(FlickTextSelection("あ", false))
        emitter.update(FlickTextSelection("い", true))
        emitter.commit(FlickTextSelection("い", true)) {
            events += FlickTextPreviewEvent.Finished(-1L)
        }

        assertEquals(5, events.size)
        assert(events[0] is FlickTextPreviewEvent.Started)
        assert(events[1] is FlickTextPreviewEvent.Changed)
        assert(events[2] is FlickTextPreviewEvent.CommitPending)
        assertEquals(-1L, events[3].gestureId)
        assert(events[4] is FlickTextPreviewEvent.Finished)
    }

    @Test
    fun cancelEndsGestureAndEmptyTextIsNormalized() {
        val events = mutableListOf<FlickTextPreviewEvent>()
        val emitter = FlickTextPreviewEmitter().apply {
            listener = FlickTextPreviewListener(events::add)
        }

        emitter.begin(FlickTextSelection("", false))
        emitter.cancel()
        emitter.update(FlickTextSelection("い", true))

        val started = events[0] as FlickTextPreviewEvent.Started
        assertEquals(null, started.selection.text)
        assert(events[1] is FlickTextPreviewEvent.Canceled)
        assertEquals(2, events.size)
    }
}
