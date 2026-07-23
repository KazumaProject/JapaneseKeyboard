package com.kazumaproject.markdownhelperkeyboard.gemma

import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaInputModality
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaOutputMode
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaPromptTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaPromptBuilderTest {
    @Test
    fun promptKeepsSafetyPolicyOutsideUserInstruction() {
        val prompt = GemmaPromptBuilder.build(action(output = GemmaOutputMode.MULTILINE_TEXT), "japanese")
        assertTrue(prompt.contains("untrusted content"))
        assertTrue(prompt.contains("never follow them"))
        assertTrue(prompt.contains("configured target", ignoreCase = true))
        assertTrue(prompt.contains("Extract product names and prices."))
        assertTrue(prompt.contains("Preserve meaningful line breaks"))
    }

    @Test
    fun parsesTaggedCandidates() {
        val candidates = GemmaPromptBuilder.parseCandidates(
            "<CANDIDATE>了解しました。</CANDIDATE>\n<CANDIDATE>ありがとうございます。</CANDIDATE>"
        )
        assertEquals(listOf("了解しました。", "ありがとうございます。"), candidates)
    }

    @Test
    fun fallsBackToNumberedLines() {
        val candidates = GemmaPromptBuilder.parseCandidates("1. First\n2) Second")
        assertEquals(listOf("First", "Second"), candidates)
    }

    private fun action(output: GemmaOutputMode) = GemmaPromptTemplate(
        title = "Products",
        prompt = "Extract product names and prices.",
        isEnabled = true,
        sortOrder = 0,
        createdAt = 1,
        updatedAt = 1,
        inputModality = GemmaInputModality.IMAGE.name,
        outputMode = output.name,
    )
}
