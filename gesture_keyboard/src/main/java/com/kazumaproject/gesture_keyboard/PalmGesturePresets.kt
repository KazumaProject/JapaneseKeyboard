package com.kazumaproject.gesture_keyboard

import com.kazumaproject.custom_keyboard.data.KeyAction

/**
 * Independently specified Graffiti 1 paths, including alternate forms.
 * Reference: Palm V Handbook, chapter 2, printed pages 30–33.
 * https://palmdb.net/content/files/archive-docs/palm-v-handbook/49646723-Handbook-Palm-V.pdf
 * Coordinates describe strokes, not traced artwork or Palm recognition software.
 */
object PalmGesturePresets {
    const val LATIN = "builtin_palm_latin"
    const val ROMAJI = "builtin_palm_romaji"
    const val DIGITS = "builtin_palm_digits"
    const val PUNCTUATION = "builtin_palm_punctuation"

    private fun letter(text: String, vararg paths: String) = GestureEntry(
        text, GestureOutput.LiteralText(text), paths.map(GesturePresets::path)
    )
    private fun command(id: String, action: KeyAction, path: String) = GestureEntry(
        id, GestureOutput.Command(action), listOf(GesturePresets.path(path))
    )
    private val commands = listOf(
        command("palm_space", KeyAction.Space, "0,5 10,5"),
        command("palm_delete", KeyAction.Delete, "10,5 0,5"),
        command("palm_enter", KeyAction.Enter, "10,0 0,10")
    )
    private val alphabet = listOf(
        letter("a", "0,10 5,0 9,10"),
        letter("b", "0,0 0,10 5,10 8,8 7,6 1,5 6,4 7,2 5,0 1,1", "0,10 0,0 5,0 8,2 7,4 1,5 6,5 8,7 7,9 4,10 0,10"),
        letter("c", "9,1 6,0 3,1 1,4 1,7 3,10 7,10 9,9"),
        letter("d", "0,0 0,10 5,10 8,8 9,5 8,2 5,0 1,1", "0,10 0,0 5,0 8,2 9,5 8,8 5,10 0,10"),
        letter("e", "8,1 5,0 2,1 2,3 5,5 2,5 1,7 2,9 5,10 8,9"),
        letter("f", "9,0 2,0 1,2 1,10", "1,10 1,2 2,0 9,0"),
        letter("g", "8,1 5,0 2,2 1,5 2,8 5,10 8,9 9,6 6,6", "8,1 5,0 2,2 1,5 1,8 3,10 7,10 9,8 8,6 5,6 2,8"),
        letter("h", "1,0 1,10 1,6 3,5 6,5 8,7 8,10"),
        letter("i", "5,0 5,10"),
        letter("j", "9,0 9,7 8,9 6,10 2,10"),
        letter("k", "8,0 6,4 4,6 1,7 0,6 2,5 5,6 8,10"),
        letter("l", "0,0 0,9 1,10 9,10"),
        letter("m", "0,10 0,4 1,1 3,0 5,3 5,7 5,3 7,0 9,1 10,4 10,10", "0,10 0,0 0,4 3,1 5,2 5,10 5,4 8,1 10,3 10,10"),
        letter("n", "0,10 0,1 1,0 3,2 7,9 9,10 10,0"),
        letter("o", "5,0 2,1 0,4 0,7 2,10 6,10 9,8 10,4 8,1 5,0"),
        letter("p", "0,0 0,10 0,5 2,2 6,1 8,3 8,5 5,6 0,6", "0,10 0,0 5,0 8,2 8,4 6,6 0,6"),
        letter("q", "5,0 2,2 1,5 2,8 5,10 8,8 9,5 8,1 7,0 11,0"),
        letter("r", "0,0 0,10 0,5 2,2 6,1 8,3 7,5 1,6 5,6 8,10", "0,10 0,0 5,0 8,2 7,4 1,5 5,6 8,10"),
        letter("s", "8,1 5,0 2,1 1,3 3,5 7,6 9,8 7,10 3,10 1,9"),
        letter("t", "0,0 8,0 9,1 9,10"),
        letter("u", "0,0 0,6 1,9 4,10 7,9 8,6 8,0"),
        letter("v", "0,0 2,7 4,10 5,9 5,1 6,0 10,0", "10,0 7,8 5,10 3,8 1,0"),
        letter("w", "0,0 0,7 1,10 3,10 5,6 6,10 8,10 10,7 10,0"),
        letter("x", "0,0 10,10 5,5 10,0 0,10", "0,0 5,5 8,3 11,3 12,5 10,7 7,7 5,5 0,10"),
        letter("y", "0,0 0,4 2,5 6,3 8,1 5,9 3,12 1,12 1,10 4,7 9,5", "0,0 5,5 9,1 5,5 3,9 4,11 6,10 6,8 5,5"),
        letter("z", "0,0 9,0 0,10 10,10")
    )
    private val digits = listOf(
        letter("0", "5,0 2,1 0,4 0,7 2,10 6,10 9,8 10,4 8,1 5,0", "5,0 8,1 10,4 9,8 6,10 2,10 0,7 0,4 2,1 5,0"),
        letter("1", "5,0 5,10"),
        letter("2", "0,1 5,0 8,1 8,3 6,5 0,10 10,10"),
        letter("3", "0,1 3,0 7,1 8,3 6,5 4,5 7,6 8,8 6,10 3,10 0,8"),
        letter("4", "0,0 0,8 1,10 7,10"),
        letter("5", "9,0 0,0 0,5 5,4 8,5 9,7 8,9 5,10 1,9", "0,0 0,5 5,4 8,5 9,7 8,9 5,10 1,9"),
        letter("6", "8,0 4,1 1,4 0,7 1,9 4,10 7,9 8,7 6,5 3,5 1,7"),
        letter("7", "0,0 9,0 9,2 5,10"),
        letter("8", "5,0 2,1 1,3 3,5 7,7 8,9 6,11 3,11 0,9 1,7 5,4 8,2 7,0 5,0", "0,0 8,8 7,10 4,11 1,10 0,8 2,5 6,2 8,0"),
        letter("9", "8,0 4,1 1,3 0,5 2,7 5,7 8,4 8,0 8,10")
    )
    val punctuation = GestureSet(PUNCTUATION, "Palm · . , ? !", listOf(
        letter(",", "8,0 2,5"), letter("'", "5,0 5,5"),
        letter("?", "0,1 3,0 6,1 7,3 5,6 4,10"), letter("!", "5,10 5,0"),
        letter("-", "0,5 10,5"), letter("(", "8,0 4,1 2,4 2,7 4,10 8,10"),
        letter(")", "2,0 6,1 8,4 8,7 6,10 2,10"), letter("/", "0,10 10,0"),
        letter("$", "8,1 5,0 2,1 1,3 3,5 7,6 9,8 7,10 3,10 1,9")
    ), builtIn = true)

    fun sets(ja: Boolean) = listOf(
        GestureSet(LATIN, if (ja) "Palm英字" else "Palm Alphabet", alphabet + commands +
            command("palm_shift", KeyAction.ShiftKey, "5,10 5,0"), builtIn = true, palm = true),
        GestureSet(ROMAJI, if (ja) "Palmローマ字" else "Palm Romaji", alphabet + commands +
            command("palm_shift", KeyAction.ShiftKey, "5,10 5,0"), builtIn = true, isRomaji = true, palm = true),
        GestureSet(DIGITS, if (ja) "Palm数字" else "Palm Numbers", digits + commands, builtIn = true, palm = true)
    )
}
