package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.core.domain.flick.FlickInputEvidence

enum class FlickCorrectionKind { KEY, DIRECTION, KEY_AND_DIRECTION, MISSING, EXTRA, TRANSPOSE }

/** Original input offsets; an insertion has inputStart == inputEnd. */
data class FlickCorrectionEdit(
    val kind: FlickCorrectionKind,
    val inputStart: Int,
    val inputEnd: Int,
    val replacement: String,
    val costUnits: Int,
    val supportedByTouch: Boolean = false,
)

data class FlickCorrectionInfo(
    val edits: List<FlickCorrectionEdit>,
    val originalType: Byte = 1,
) {
    val costUnits: Int get() = edits.sumOf { it.costUnits }
}

/** Non-null only for the standard Japanese twelve-key keyboard. */
data class FlickCorrectionInput(
    val input: String,
    val evidence: List<FlickInputEvidence?> = emptyList(),
) {
    fun evidenceAt(index: Int): FlickInputEvidence? =
        evidence.getOrNull(index)?.takeIf { input.getOrNull(index) == it.observedChar }
}
