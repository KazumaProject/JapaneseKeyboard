package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IMEServiceSelectionCleanupContractTest {

    @Test
    fun emptySelectionUpdateClearsCandidateStateAfterComposingTextFinishes() {
        val source = imeServiceSource()
        val updateSelection = source.functionBody(
            start = "override fun onUpdateSelection(",
            end = "override fun onKeyDown("
        )

        assertTrue(
            updateSelection.contains("clearSuggestionStateAfterEditorSelectionChange()")
        )

        val cleanup = source.functionBody(
            start = "private fun clearSuggestionStateAfterEditorSelectionChange",
            end = "private fun updateBunsetsuSpaceKeyIfNeeded"
        )
        assertTrue(cleanup.contains("currentCandidateStripFullCandidates.isNotEmpty()"))
        assertTrue(cleanup.contains("candidateRefreshRequests.value.flag"))
        assertTrue(cleanup.contains("clearSuggestionStateAfterCommit()"))
    }

    private fun imeServiceSource(): String =
        listOf(
            File("app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"),
            File("src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt")
        ).first { it.isFile }.readText()

    private fun String.functionBody(start: String, end: String): String {
        val startIndex = indexOf(start)
        require(startIndex >= 0) { "Missing start marker: $start" }
        val endIndex = indexOf(end, startIndex + start.length)
        require(endIndex >= 0) { "Missing end marker: $end" }
        return substring(startIndex, endIndex)
    }
}
