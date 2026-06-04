package com.kazumaproject.markdownhelperkeyboard.ime_service

internal fun buildBunsetsuZenzLeftContext(
    segmentDisplayTexts: List<String>,
    focusedIndex: Int
): String {
    if (segmentDisplayTexts.isEmpty()) return ""
    val safeFocusedIndex = focusedIndex.coerceIn(0, segmentDisplayTexts.lastIndex)
    return segmentDisplayTexts
        .take(safeFocusedIndex)
        .joinToString(separator = "")
}

internal fun isBunsetsuZenzTargetCurrent(
    conversionInput: String,
    segmentReadings: List<String>,
    segmentDisplayTexts: List<String>,
    focusedIndex: Int,
    targetConversionInput: String,
    targetSegmentIndex: Int,
    targetSegmentReading: String,
    targetLeftContext: String
): Boolean {
    if (segmentReadings.isEmpty()) return false
    if (segmentReadings.size != segmentDisplayTexts.size) return false
    val safeFocusedIndex = focusedIndex.coerceIn(0, segmentReadings.lastIndex)
    if (conversionInput != targetConversionInput) return false
    if (safeFocusedIndex != targetSegmentIndex) return false
    if (segmentReadings.getOrNull(safeFocusedIndex) != targetSegmentReading) return false
    return buildBunsetsuZenzLeftContext(
        segmentDisplayTexts = segmentDisplayTexts,
        focusedIndex = safeFocusedIndex
    ) == targetLeftContext
}
