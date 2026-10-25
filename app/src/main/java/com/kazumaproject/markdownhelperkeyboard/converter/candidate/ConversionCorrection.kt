package com.kazumaproject.markdownhelperkeyboard.converter.candidate

/** Provenance of the selected lattice path; flags never affect ranking. */
object ConversionCorrection {
    const val NONE = 0
    const val OMISSION = 1
    const val JAPANESE_FLICK = 2
    const val ENGLISH_QWERTY = 4
}

/** Captures N-best before prediction, utility candidates, or display ordering are applied. */
class NBestCandidateCollector(private val measureTime: Boolean = false) {
    var selectionNs: Long? = null
        private set
    var best: Candidate? = null
        private set

    fun collect(candidates: List<Candidate>) {
        val started = if (measureTime) System.nanoTime() else 0L
        best = candidates.minByOrNull { it.score }
        if (measureTime) selectionNs = System.nanoTime() - started
    }
}
