package com.kazumaproject.markdownhelperkeyboard.ime_service

/**
 * Controls only candidate conversion and composing presentation during delete repeat.
 *
 * The repeat interval and the actual deletion algorithm are shared by both behaviors.
 */
internal enum class DeleteLongPressConversionBehavior(
    val preferenceValue: String,
    val suspendsCandidateResultsDuringRepeat: Boolean,
) {
    Deferred(
        preferenceValue = "deferred",
        suspendsCandidateResultsDuringRepeat = true,
    ),
    Continuous(
        preferenceValue = "continuous",
        suspendsCandidateResultsDuringRepeat = false,
    );

    companion object {
        val Default = Deferred

        fun fromPreferenceValue(value: String?): DeleteLongPressConversionBehavior {
            return entries.firstOrNull { it.preferenceValue == value } ?: Default
        }
    }
}
