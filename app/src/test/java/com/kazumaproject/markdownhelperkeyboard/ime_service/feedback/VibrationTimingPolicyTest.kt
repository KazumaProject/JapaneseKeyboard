package com.kazumaproject.markdownhelperkeyboard.ime_service.feedback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VibrationTimingPolicyTest {
    @Test
    fun pressPreferenceVibratesOnPressOnly() {
        assertTrue(
            VibrationTimingPolicy.shouldVibrate(
                rawPreferenceValue = "press",
                moment = VibrationFeedbackMoment.PRESS
            )
        )
        assertFalse(
            VibrationTimingPolicy.shouldVibrate(
                rawPreferenceValue = "press",
                moment = VibrationFeedbackMoment.RELEASE
            )
        )
    }

    @Test
    fun releasePreferenceVibratesOnReleaseOnly() {
        assertFalse(
            VibrationTimingPolicy.shouldVibrate(
                rawPreferenceValue = "release",
                moment = VibrationFeedbackMoment.PRESS
            )
        )
        assertTrue(
            VibrationTimingPolicy.shouldVibrate(
                rawPreferenceValue = "release",
                moment = VibrationFeedbackMoment.RELEASE
            )
        )
    }

    @Test
    fun bothPreferenceVibratesOnPressAndRelease() {
        assertTrue(
            VibrationTimingPolicy.shouldVibrate(
                rawPreferenceValue = "both",
                moment = VibrationFeedbackMoment.PRESS
            )
        )
        assertTrue(
            VibrationTimingPolicy.shouldVibrate(
                rawPreferenceValue = "both",
                moment = VibrationFeedbackMoment.RELEASE
            )
        )
    }

    @Test
    fun nullPreferenceDoesNotVibrate() {
        assertFalse(
            VibrationTimingPolicy.shouldVibrate(
                rawPreferenceValue = null,
                moment = VibrationFeedbackMoment.PRESS
            )
        )
        assertFalse(
            VibrationTimingPolicy.shouldVibrate(
                rawPreferenceValue = null,
                moment = VibrationFeedbackMoment.RELEASE
            )
        )
    }

    @Test
    fun unknownPreferenceDoesNotVibrate() {
        assertFalse(
            VibrationTimingPolicy.shouldVibrate(
                rawPreferenceValue = "unexpected",
                moment = VibrationFeedbackMoment.PRESS
            )
        )
        assertFalse(
            VibrationTimingPolicy.shouldVibrate(
                rawPreferenceValue = "unexpected",
                moment = VibrationFeedbackMoment.RELEASE
            )
        )
    }
}
