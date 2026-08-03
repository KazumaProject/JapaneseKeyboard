package com.kazumaproject.custom_keyboard.data

/**
 * Presentation case for text-producing custom keyboard keys.
 *
 * [AS_DEFINED] preserves the canonical label and input maps exactly as stored. [UPPERCASE]
 * changes only ASCII letters so the visual representation stays consistent with the IME's
 * Shift/CapsLock input semantics without introducing locale-dependent transformations.
 */
enum class KeyCharacterCase {
    AS_DEFINED,
    UPPERCASE;

    fun transformAsciiLetters(text: String): String {
        if (this == AS_DEFINED || text.none { it in 'a'..'z' }) return text
        val characters = text.toCharArray()
        characters.indices.forEach { index ->
            val char = characters[index]
            if (char in 'a'..'z') {
                characters[index] = char.uppercaseChar()
            }
        }
        return characters.concatToString()
    }
}
