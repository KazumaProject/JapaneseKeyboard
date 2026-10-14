package com.kazumaproject.markdownhelperkeyboard.ime_service.fever

/** A rotating vocabulary, rather than the same jackpot stamp on every interaction. */
internal object FeverVisualStyle {
    val palette = intArrayOf(0xff62ecff.toInt(), 0xffff61ae.toInt(), 0xffffd35c.toInt(), 0xff9d8bff.toInt(), 0xff62ffd0.toInt())
    fun family(event: FeverEvent): Int = when {
        event.cue == FeverCue.DELETE -> 2
        event.cue == FeverCue.CONVERSION -> 3
        event.cue == FeverCue.CANDIDATE -> 4
        event.cue == FeverCue.CANDIDATES -> 0
        event.cue != null -> 2
        event.award == FeverAward.HOT -> 1
        event.award == FeverAward.JACKPOT -> 3
        else -> (event.combo - 1).coerceAtLeast(0) % palette.size
    }
    fun accent(event: FeverEvent): Int = event.cue?.color ?: palette[family(event)]
}
