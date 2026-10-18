package com.kazumaproject.morse_keyboard

import java.text.Normalizer

object MorseKanaModifier {
    /** Null means no eligible uncommitted kana; the signal must remain editable. */
    fun apply(reading: String, command: MorseCommand): String? {
        if (reading.isEmpty()) return null
        val last = reading.last()
        val replacement = when (command) {
            MorseCommand.SMALL_KANA -> {
                val normal = "あいうえおつやゆよわかけアイウエオツヤユヨワカケ"
                val small = "ぁぃぅぇぉっゃゅょゎゕゖァィゥェォッャュョヮヵヶ"
                val index = normal.indexOf(last)
                if (index >= 0) small[index].toString() else small.indexOf(last).takeIf { it >= 0 }?.let { normal[it].toString() }
            }
            MorseCommand.DAKUTEN, MorseCommand.HANDAKUTEN -> {
                val base = Normalizer.normalize(last.toString(), Normalizer.Form.NFD).first()
                val eligible = if (command == MorseCommand.HANDAKUTEN) "はひふへほハヒフヘホ" else "うかきくけこさしすせそたちつてとはひふへほウカキクケコサシスセソタチツテトハヒフヘホ"
                if (base !in eligible) null else Normalizer.normalize("$base${if (command == MorseCommand.DAKUTEN) '\u3099' else '\u309a'}", Normalizer.Form.NFC)
            }
            else -> null
        } ?: return null
        return reading.dropLast(1) + replacement
    }
}
