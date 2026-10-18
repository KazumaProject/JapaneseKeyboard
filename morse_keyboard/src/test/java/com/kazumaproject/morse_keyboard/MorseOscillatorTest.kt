package com.kazumaproject.morse_keyboard

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class MorseOscillatorTest {
    @Test fun synthesizedToneHasRequestedFrequencyVolumeAndSilentRelease() {
        val oscillator = MorseOscillator(48000)
        val samples = ShortArray(48000)
        oscillator.fill(samples, 780, 0.25)
        val risingCrossings = (1 until samples.size).count { samples[it - 1] <= 0 && samples[it] > 0 }
        assertTrue("780 Hz at 48 kHz", risingCrossings in 779..781)
        assertTrue(samples.maxOf { abs(it.toInt()) } in 8100..8192)
        val release = ShortArray(480)
        oscillator.fill(release, 780, 0.0)
        assertTrue(release.takeLast(240).all { it == 0.toShort() })
        assertTrue(samples.first() == 0.toShort())
    }
}
