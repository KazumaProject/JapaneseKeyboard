package com.kazumaproject.morse_keyboard

/** The guide uses the map itself, including multi-character text and commands. */
class MorseGuide {
    var entries: List<MorseEntry> = emptyList()
        private set
    var selected: MorseEntry? = null
        private set
    private var completed = false
    fun setMap(map: MorseMap) {
        entries = map.entries.toList()
        selected = null
        completed = false
    }
    fun select(signal: String) {
        selected = entries.firstOrNull { it.signal == signal }
        completed = false
    }
    fun decoded(signal: String) { completed = selected?.signal == signal }
    fun reset() { completed = false }
    fun progress(pattern: String): Progress {
        val target = selected?.signal.orEmpty()
        if (target.isEmpty()) return Progress(0, 0, null, false, false)
        if (pattern.isNotEmpty()) completed = false
        val matched = pattern.zip(target).takeWhile { (actual, expected) -> actual == expected }.size
        val mismatch = pattern.isNotEmpty() && !target.startsWith(pattern)
        val count = if (pattern.isEmpty() && completed) target.length else matched
        return Progress(count, target.length, if (!mismatch) target.getOrNull(count) else null, mismatch, completed)
    }
    data class Progress(val count: Int, val total: Int, val next: Char?, val mismatch: Boolean, val completed: Boolean)
}
