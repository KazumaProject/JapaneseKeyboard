package com.kazumaproject.markdownhelperkeyboard.user_dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DictionaryWordInputTest {
    @Test fun preservesLeadingTrailingAndRepeatedWhitespaceOnlyWhenEnabled() {
        for (word in listOf("き ", " き", "  き  ", "　き　", "\tき\n")) {
            assertEquals(word, DictionaryWordInput.normalize(word, true))
            assertEquals("き", DictionaryWordInput.normalize(word, false))
        }
    }

    @Test fun internalWhitespaceRemainsInBothModes() {
        for (enabled in listOf(false, true)) {
            for (word in listOf("き  く", "き　く", "き\tく", "き\nく")) {
                assertEquals(word, DictionaryWordInput.normalize(word, enabled))
            }
        }
    }

    @Test fun emptyAndWhitespaceOnlyWordsAreRejectedInBothModes() {
        for (enabled in listOf(false, true)) {
            for (word in listOf("", " ", "　", " 　\t\r\n")) {
                assertNull(DictionaryWordInput.normalize(word, enabled))
            }
        }
    }
}
