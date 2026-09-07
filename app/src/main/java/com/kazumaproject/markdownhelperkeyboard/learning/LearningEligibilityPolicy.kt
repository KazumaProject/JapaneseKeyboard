package com.kazumaproject.markdownhelperkeyboard.learning

import com.kazumaproject.markdownhelperkeyboard.converter.engine.NumericCandidateProvider
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.containsSymbolNumberOrEmoji

object LearningEligibilityPolicy {
    fun isEligible(
        input: String,
        output: String,
        allowJapaneseWithSymbolsAndNumbers: Boolean,
    ): Boolean {
        if (input.isBlank() || output.isBlank()) return false
        // Supplementary-plane emoji occupy two UTF-16 chars; inspect their code points too.
        val containsSymbolsOrNumbers = output.containsSymbolNumberOrEmoji() ||
            output.codePoints().anyMatch { Character.getType(it) == Character.OTHER_SYMBOL.toInt() }
        if (!containsSymbolsOrNumbers) return true
        if (!allowJapaneseWithSymbolsAndNumbers) return false

        // Keep mixed phrases and recognized numeric conversions, but not arbitrary symbols.
        return output.codePoints().anyMatch { Character.isLetter(it) } ||
            NumericCandidateProvider.isLearnableConversion(input, output)
    }
}
