package com.kazumaproject.morse_keyboard

sealed class MorseOutput {
    data class Reading(val text: String) : MorseOutput()
    data class Romaji(val text: String) : MorseOutput()
    data class LiteralText(val text: String) : MorseOutput()
    data class Command(val action: MorseCommand) : MorseOutput()
}

enum class MorseCommand {
    DELETE, CONVERT, COMMIT, SPACE, ENTER, CURSOR_LEFT, CURSOR_RIGHT,
    NEXT_MAP, NEXT_KEYBOARD, DAKUTEN, HANDAKUTEN, SMALL_KANA
}

data class MorseEntry(val signal: String, val output: MorseOutput)
data class MorseMap(val id: String, val name: String, val entries: List<MorseEntry>, val builtIn: Boolean = false) {
    private val lookup by lazy { entries.associate { it.signal to it.output } }
    fun decode(signal: String): MorseOutput? = lookup[signal]
    fun continuations(signal: String): List<MorseEntry> = entries.filter {
        it.signal.startsWith(signal) && it.signal.length > signal.length
    }.sortedBy { it.signal.length }

    fun validate() {
        require(id.isNotBlank() && name.isNotBlank()) { "Map ID and name must not be empty" }
        require(entries.map { it.signal }.distinct().size == entries.size) { "Duplicate signal" }
        entries.forEach {
            require(it.signal.matches(Regex("[.-]{1,64}"))) { "Use 1–64 dots and dashes" }
            val text = when (val output = it.output) {
                is MorseOutput.Reading -> output.text
                is MorseOutput.Romaji -> output.text
                is MorseOutput.LiteralText -> output.text
                is MorseOutput.Command -> null
            }
            require(text == null || text.isNotEmpty()) { "Output must not be empty" }
        }
    }
}

enum class MorseInputStyle { PADDLE_A, PADDLE_B, TAP, STRAIGHT }

data class MorseSettings(
    val beginnerGuide: Boolean = false,
    val controlLayout: MorseControlLayout = MorseControlLayout(),
    val style: MorseInputStyle = MorseInputStyle.PADDLE_A,
    val wpm: Int = 15,
    val autoBoundary: Boolean = true,
    val boundaryMs: Int = 600,
    val straightThresholdMs: Int = 160,
    val reverse: Boolean = false,
    val padWidthPercent: Int = 100,
    val padHeightPercent: Int = 100,
    val gapDp: Int = 8,
    val oneHand: Boolean = false,
    val sound: Boolean = true,
    val frequencyHz: Int = 780,
    val volumePercent: Int = 25,
    val vibration: Boolean = false,
) {
    val minimumHeightDp: Int get() = if (controlLayout.placement.isSide) 68 + controlLayout.rows.maxOf { it.size } * 48 else 220 + (controlLayout.rows.size - 1) * 48
    val unitMs: Long get() = 1200L / wpm.coerceIn(5, 60)
}

object MorsePresets {
    const val WABUN = "builtin_wabun"
    const val ROMAJI = "builtin_romaji"
    const val ENGLISH = "builtin_english"

    // ITU-R M.1677-1. Procedure signals are commands only when explicitly assigned.
    private val latin = listOf(
        "a" to ".-", "b" to "-...", "c" to "-.-.", "d" to "-..", "e" to ".",
        "é" to "..-..", "f" to "..-.", "g" to "--.", "h" to "....", "i" to "..", "j" to ".---",
        "k" to "-.-", "l" to ".-..", "m" to "--", "n" to "-.", "o" to "---",
        "p" to ".--.", "q" to "--.-", "r" to ".-.", "s" to "...", "t" to "-",
        "u" to "..-", "v" to "...-", "w" to ".--", "x" to "-..-", "y" to "-.--", "z" to "--..",
        "0" to "-----", "1" to ".----", "2" to "..---", "3" to "...--", "4" to "....-",
        "5" to ".....", "6" to "-....", "7" to "--...", "8" to "---..", "9" to "----.",
        "." to ".-.-.-", "," to "--..--", ":" to "---...", "?" to "..--..", "'" to ".----.",
        "-" to "-....-", "/" to "-..-.", "(" to "-.--.", ")" to "-.--.-", "\"" to ".-..-.",
        "=" to "-...-", "+" to ".-.-.", "@" to ".--.-."
    )
    // Radio Station Operating Regulations, appended table 1 (Wabun).
    private val kana = listOf(
        "い" to ".-", "ろ" to ".-.-", "は" to "-...", "に" to "-.-.", "ほ" to "-..",
        "へ" to ".", "と" to "..-..", "ち" to "..-.", "り" to "--.", "ぬ" to "....",
        "る" to "-.--.", "を" to ".---", "わ" to "-.-", "か" to ".-..", "よ" to "--",
        "た" to "-.", "れ" to "---", "そ" to "---.", "つ" to ".--.", "ね" to "--.-",
        "な" to ".-.", "ら" to "...", "む" to "-", "う" to "..-", "ゐ" to ".-..-",
        "の" to "..--", "お" to ".-...", "く" to "...-", "や" to ".--", "ま" to "-..-",
        "け" to "-.--", "ふ" to "--..", "こ" to "----", "え" to "-.---", "て" to ".-.--",
        "あ" to "--.--", "さ" to "-.-.-", "き" to "-.-..", "ゆ" to "-..--", "め" to "-...-",
        "み" to "..-.-", "し" to "--.-.", "ゑ" to ".--..", "ひ" to "--..-", "も" to "-..-.",
        "せ" to ".---.", "す" to "---.-", "ん" to ".-.-.", "ー" to ".--.-",
        "、" to ".-.-.-", "」" to ".-.-..", "（" to "-.--.-", "）" to ".-..-."
    )
    fun maps(japanese: Boolean = false): List<MorseMap> = listOf(
        MorseMap(WABUN, if (japanese) "日本語・和文" else "Japanese · Wabun",
            kana.map { (text, signal) -> MorseEntry(signal, MorseOutput.Reading(text)) } +
                latin.filter { it.first.singleOrNull()?.isDigit() == true }.map { (text, signal) -> MorseEntry(signal, MorseOutput.Reading(text)) } +
                listOf(MorseEntry("..", MorseOutput.Command(MorseCommand.DAKUTEN)),
                    MorseEntry("..--.", MorseOutput.Command(MorseCommand.HANDAKUTEN))), true),
        MorseMap(ROMAJI, if (japanese) "日本語・ローマ字" else "Japanese · Romaji",
            latin.map { (text, signal) -> MorseEntry(signal, MorseOutput.Romaji(text)) }, true),
        MorseMap(ENGLISH, if (japanese) "英語" else "English",
            latin.map { (text, signal) -> MorseEntry(signal, MorseOutput.LiteralText(text)) }, true)
    )
}
