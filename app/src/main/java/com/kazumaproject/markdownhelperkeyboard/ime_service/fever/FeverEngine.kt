package com.kazumaproject.markdownhelperkeyboard.ime_service.fever

import java.text.Normalizer
import java.util.Locale

internal enum class FeverAward(val priority: Int, val label: String) {
    COMBO(1, "COMBO UP!"), FEVER(2, "FEVER"), HOT(3, "激アツ"), JACKPOT(4, "大当たり")
}
internal enum class FeverCue(val label: String, val color: Int, val priority: Int) {
    CURSOR_LEFT("◀ MOVE", 0xff75caff.toInt(), 1), CURSOR_RIGHT("MOVE ▶", 0xff75caff.toInt(), 1),
    CURSOR_UP("▲ MOVE", 0xff75caff.toInt(), 1), CURSOR_DOWN("MOVE ▼", 0xff75caff.toInt(), 1),
    DELETE("BREAK!", 0xffff5998.toInt(), 2), CANDIDATE("CHANCE", 0xffffd66e.toInt(), 3),
    CONVERSION("リーチ！", 0xffffb94f.toInt(), 4)
}
internal data class FeverEvent(val combo: Int, val award: FeverAward? = null, val cue: FeverCue? = null)

/** Runs on the UI thread. Injected monotonic time and RNG make every outcome reproducible. */
internal class FeverEngine(
    var settings: FeverSettings = FeverSettings(),
    private val now: () -> Long = { android.os.SystemClock.uptimeMillis() },
    private val roll: () -> Int = { kotlin.random.Random.nextInt(100) },
) {
    var combo: Int = 0; private set
    private var lastCharacter: Long? = null
    private var lastDraw: Long? = null
    private var lastOperation: Long? = null
    private var lastCommit: Long? = null
    private var awardUntil = 0L
    private var awardPriority = 0
    private var normalizedWords = settings.keywords.map(::normalize).filter(String::isNotEmpty)
    fun configure(value: FeverSettings) {
        settings = value
        normalizedWords = value.keywords.map(::normalize).filter(String::isNotEmpty)
    }
    fun reset() {
        combo = 0; lastCharacter = null; lastDraw = null; lastOperation = null; lastCommit = null
        awardUntil = 0; awardPriority = 0
    }
    fun character(operation: Long): FeverEvent? {
        if (lastOperation == operation) return null
        lastOperation = operation
        val time = now()
        if (lastCharacter?.let { time - it >= settings.comboTimeoutMillis } != false) combo = 0
        lastCharacter = time
        combo++
        val award = when {
            combo >= 100 && combo % 50 == 0 -> FeverAward.JACKPOT
            combo == 50 -> FeverAward.HOT
            combo == 25 -> FeverAward.FEVER
            combo == 10 -> FeverAward.COMBO
            else -> null
        }
        return FeverEvent(combo, accept(award, time))
    }
    fun committed(operation: Long, text: String, success: Boolean): FeverEvent? {
        if (!success || text.isBlank() || lastCommit == operation) return null
        lastCommit = operation
        val time = now()
        val normalizedText = if (settings.keywordsEnabled) normalize(text) else ""
        val keyword = settings.keywordsEnabled && normalizedWords.any { normalizedText.contains(it) }
        val drawn = !keyword && (lastDraw?.let { time - it >= 3000 } != false) && settings.hitRate > 0
        val win = if (drawn) {
            lastDraw = time
            roll().coerceIn(0, 99) < settings.hitRate
        } else false
        val award = when {
            keyword -> FeverAward.HOT
            win -> FeverAward.JACKPOT
            else -> null
        }
        return accept(award, time)?.let { FeverEvent(combo, it) }
    }
    fun expireCombo(): Boolean {
        if (combo > 0 && lastCharacter?.let { now() - it >= settings.comboTimeoutMillis } == true) {
            combo = 0; return true
        }
        return false
    }
    private fun accept(award: FeverAward?, time: Long): FeverAward? {
        if (award == null || (time < awardUntil && award.priority <= awardPriority)) return null
        awardUntil = time + 2000; awardPriority = award.priority
        return award
    }
    private fun normalize(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
}
