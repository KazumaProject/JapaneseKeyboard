package com.kazumaproject.markdownhelperkeyboard.ime_service.feedback

enum class VibrationFeedbackMoment {
    PRESS,
    RELEASE
}

object VibrationTimingPolicy {
    fun shouldVibrate(rawPreferenceValue: String?, moment: VibrationFeedbackMoment): Boolean {
        return when (rawPreferenceValue) {
            "press" -> moment == VibrationFeedbackMoment.PRESS
            "release" -> moment == VibrationFeedbackMoment.RELEASE
            "both" -> true
            else -> false
        }
    }
}
