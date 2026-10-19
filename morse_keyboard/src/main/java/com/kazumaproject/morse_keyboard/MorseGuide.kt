package com.kazumaproject.morse_keyboard

/** A target may span several decoded characters (romaji or kana followed by a voicing mark). */
class MorseGuide {
    enum class Page { BASIC, DAKUTEN, HANDAKUTEN }
    data class Target(val output: MorseOutput, val signals: List<String>, val page: Page = Page.BASIC)
    var entries: List<Target> = emptyList()
        private set
    var japanese = false
        private set
    var selected: Target? = null
        private set
    private var step = 0
    private var completed = false

    fun setMap(map: MorseMap) {
        japanese = map.entries.any { it.output is MorseOutput.Romaji || (it.output as? MorseOutput.Reading)?.text?.any { c -> c in 'ぁ'..'ヿ' } == true }
        val targets = map.entries.filterNot {
            (it.output as? MorseOutput.Command)?.action in listOf(MorseCommand.DAKUTEN, MorseCommand.HANDAKUTEN)
        }.map {
            val text = (it.output as? MorseOutput.Reading)?.text.orEmpty()
            val decomposed = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            val page = when {
                text.length == 1 && decomposed.endsWith('\u3099') -> Page.DAKUTEN
                text.length == 1 && decomposed.endsWith('\u309a') -> Page.HANDAKUTEN
                else -> Page.BASIC
            }
            Target(it.output, listOf(it.signal), page)
        }
        val romaji = map.entries.filter { it.output is MorseOutput.Romaji }.associate {
            (it.output as MorseOutput.Romaji).text to it.signal
        }
        val kana = "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわゐゑをん"
        val roman = "a i u e o ka ki ku ke ko sa shi su se so ta chi tsu te to na ni nu ne no ha hi fu he ho ma mi mu me mo ya yu yo ra ri ru re ro wa wyi wye wo nn".split(' ')
        val readings = if (romaji.isNotEmpty()) kana.toList().zip(roman).mapNotNull { (char, text) ->
            val signals = romaji[text]?.let(::listOf) ?: text.map { romaji[it.toString()] ?: return@mapNotNull null }
            Target(MorseOutput.Reading(char.toString()), signals)
        } else targets.filter { it.output is MorseOutput.Reading }
        val modified = listOf(Page.DAKUTEN to MorseCommand.DAKUTEN, Page.HANDAKUTEN to MorseCommand.HANDAKUTEN).flatMap { (page, command) ->
            val modifier = map.entries.firstOrNull { it.output == MorseOutput.Command(command) }
            readings.mapNotNull { base ->
                val text = (base.output as MorseOutput.Reading).text
                if (text.length != 1) return@mapNotNull null
                val voiced = MorseKanaModifier.apply(text, command) ?: return@mapNotNull null
                if (voiced == text) return@mapNotNull null
                val direct = targets.firstOrNull { (it.output as? MorseOutput.Reading)?.text == voiced }
                val signals = direct?.signals ?: if (romaji.isNotEmpty()) {
                    val textRomaji = when (voiced) {
                        "じ" -> "ji"; "ぢ" -> "di"; "づ" -> "du"; "ゔ" -> "vu"
                        else -> {
                            val baseRoman = roman[kana.indexOf(text.single())]
                            val initial = when (text.single()) { in "かきくけこ" -> "g"; in "さしすせそ" -> "z"; in "たちつてと" -> "d"; else -> if (page == Page.DAKUTEN) "b" else "p" }
                            initial + baseRoman.last()
                        }
                    }
                    romaji[textRomaji]?.let(::listOf) ?: textRomaji.map { romaji[it.toString()] ?: return@mapNotNull null }
                } else modifier?.let { base.signals + it.signal } ?: return@mapNotNull null
                Target(MorseOutput.Reading(voiced), signals, page)
            }
        }
        val basic = if (romaji.isNotEmpty()) readings + targets
            else targets.filterNot { target -> modified.any { it.output == target.output } }
        fun text(target: Target) = when (val output = target.output) {
            is MorseOutput.Reading -> output.text
            is MorseOutput.Romaji -> output.text
            is MorseOutput.LiteralText -> output.text
            is MorseOutput.Command -> "\uffff${output.action.ordinal}"
        }
        entries = (basic + modified).distinctBy { Triple(it.output, it.signals, it.page) }.sortedWith(compareBy<Target> {
            val first = text(it).firstOrNull()
            when { first in 'ぁ'..'ヿ' -> 0; first?.lowercaseChar() in 'a'..'z' -> 1; first?.isLetter() == true -> 2; first?.isDigit() == true -> 3; else -> 4 }
        }.thenBy { text(it).lowercase() })
        selected = null; reset()
    }
    fun select(target: Target) { selected = target; reset() }
    fun decoded(signal: String) {
        val target = selected ?: return
        if (completed) return
        if (target.signals.getOrNull(step) == signal) {
            step++
            completed = step == target.signals.size
        } else reset()
    }
    fun reset() { step = 0; completed = false }
    fun progress(pattern: String): Progress {
        val signals = selected?.signals ?: return Progress(0, 0, null, false, false)
        if (completed && pattern.isNotEmpty()) reset()
        val target = signals.getOrNull(step).orEmpty()
        val matched = pattern.zip(target).takeWhile { (actual, expected) -> actual == expected }.size
        val mismatch = pattern.isNotEmpty() && !target.startsWith(pattern)
        val count = signals.take(step).sumOf { it.length } + matched
        return Progress(count, signals.sumOf { it.length }, if (!mismatch) target.getOrNull(matched) else null, mismatch, completed,
            !completed && pattern == target && target.isNotEmpty())
    }
    data class Progress(val count: Int, val total: Int, val next: Char?, val mismatch: Boolean, val completed: Boolean, val divide: Boolean = false)
}
