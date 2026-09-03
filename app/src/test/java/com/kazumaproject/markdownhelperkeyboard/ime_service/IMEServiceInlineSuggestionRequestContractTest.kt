package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IMEServiceInlineSuggestionRequestContractTest {

    @Test
    fun disabledInlineSuggestionsDoNotCreateAnAutofillRequest() {
        val source = imeServiceSource()
        val start = source.indexOf("override fun onCreateInlineSuggestionsRequest")
        val end = source.indexOf("override fun onInlineSuggestionsResponse", start)

        assertTrue("Missing inline suggestions request callback", start >= 0)
        assertTrue("Missing inline suggestions response callback", end > start)

        val function = source.substring(start, end)
        val disabledGuard = "if (!inlineSuggestionEnabled) return null"
        val factoryCall = "return InlineSuggestionsRequestFactory.create(this)"

        assertTrue(function.contains("): InlineSuggestionsRequest?"))
        assertTrue(function.contains(disabledGuard))
        assertTrue(function.indexOf(disabledGuard) < function.indexOf(factoryCall))
    }

    private fun imeServiceSource(): String =
        listOf(
            File("app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"),
            File("src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"),
        ).first { it.isFile }.readText()
}
