package com.kazumaproject.core.domain.flick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RuntimeGestureSettingsTest {

    @Test
    fun updatePublishesOneNormalizedAtomicSnapshotAndRevision() {
        val source = MutableRuntimeGestureSettingsSource()
        val initial = source.snapshot()

        assertSame(initial, source.update())

        val updated = source.update(
            flickSensitivity = -20,
            longPressTimeoutMillis = 9_000L
        )

        assertEquals(1, updated.flickSensitivity)
        assertEquals(2_000L, updated.longPressTimeoutMillis)
        assertEquals(initial.revision + 1L, updated.revision)
        assertSame(updated, source.snapshot())
    }

    @Test
    fun delegatingSourceSwitchesWithoutChangingConsumerReference() {
        val fallback = MutableRuntimeGestureSettingsSource()
        val shared = MutableRuntimeGestureSettingsSource(
            RuntimeGestureSettings(flickSensitivity = 25, longPressTimeoutMillis = 450L)
        )
        val source = DelegatingRuntimeGestureSettingsSource(fallback)

        source.bind(shared)
        assertEquals(25, source.snapshot().flickSensitivity)

        shared.update(flickSensitivity = 175)
        assertEquals(175, source.snapshot().flickSensitivity)

        source.bind(null)
        assertEquals(100, source.snapshot().flickSensitivity)
    }
}
