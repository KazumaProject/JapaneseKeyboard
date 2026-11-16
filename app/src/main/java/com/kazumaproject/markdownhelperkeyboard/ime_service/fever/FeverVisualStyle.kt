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
    fun accent(event: FeverEvent): Int = when (motion(event)) {
        FeverMotion.HEARTS, FeverMotion.LOVE -> 0xffff4d97.toInt()
        FeverMotion.ROCKETS, FeverMotion.FEVER -> 0xff65a6ff.toInt()
        FeverMotion.CASCADE -> 0xffc790ff.toInt()
        FeverMotion.IMPLODE -> 0xffff4965.toInt()
        FeverMotion.FOCUS -> 0xffff9e3d.toInt()
        FeverMotion.SCAN, FeverMotion.JACKPOT -> 0xffffd657.toInt()
        FeverMotion.RIBBON -> 0xff60ffbd.toInt()
        else -> 0xff69eaff.toInt()
    }
    fun motion(event: FeverEvent): FeverMotion = when {
        event.award == FeverAward.COMBO -> FeverMotion.COMBO
        event.award == FeverAward.FEVER -> FeverMotion.FEVER
        event.award == FeverAward.HOT -> FeverMotion.LOVE
        event.award == FeverAward.JACKPOT -> FeverMotion.JACKPOT
        event.cue == FeverCue.DELETE -> FeverMotion.IMPLODE
        event.cue == FeverCue.CONVERSION -> FeverMotion.FOCUS
        event.cue == FeverCue.CANDIDATES -> FeverMotion.REVEAL
        event.cue == FeverCue.CANDIDATE -> FeverMotion.SCAN
        event.cue != null -> FeverMotion.CURSOR
        else -> arrayOf(FeverMotion.BURST, FeverMotion.RIBBON, FeverMotion.HEARTS,
            FeverMotion.ROCKETS, FeverMotion.CASCADE)[(event.combo - 1).coerceAtLeast(0) % 5]
    }
}

/** Motion families have distinct origins and trajectories, not just different captions. */
internal enum class FeverMotion { BURST, RIBBON, HEARTS, ROCKETS, CASCADE, IMPLODE, CURSOR,
    REVEAL, SCAN, FOCUS, COMBO, FEVER, LOVE, JACKPOT }
