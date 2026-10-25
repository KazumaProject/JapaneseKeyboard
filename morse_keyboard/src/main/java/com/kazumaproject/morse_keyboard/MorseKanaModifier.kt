package com.kazumaproject.morse_keyboard

import com.kazumaproject.core.domain.kana.KanaEdit
import com.kazumaproject.core.domain.kana.KanaModifier

object MorseKanaModifier {
    fun apply(reading: String, command: MorseCommand): String? = when (command) {
        MorseCommand.DAKUTEN -> KanaModifier.apply(reading, KanaEdit.DAKUTEN)
        MorseCommand.HANDAKUTEN -> KanaModifier.apply(reading, KanaEdit.HANDAKUTEN)
        MorseCommand.SMALL_KANA -> KanaModifier.apply(reading, KanaEdit.SMALL_KANA)
        else -> null
    }
}
