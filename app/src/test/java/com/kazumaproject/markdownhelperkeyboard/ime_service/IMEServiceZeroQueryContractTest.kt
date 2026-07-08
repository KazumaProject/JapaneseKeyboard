package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IMEServiceZeroQueryContractTest {

    @Test
    fun zeroQueryAfterCommitDoesNotBlockIncognitoMode() {
        val function = imeServiceSource().functionBody(
            start = "private fun canShowZeroQueryAfterCommit",
            end = "private fun String.isZeroQueryNumberKey"
        )

        assertFalse(function.contains("isPrivateMode"))
    }

    @Test
    fun zeroQueryAfterCommitAllowsNumberKeysOutsideJapaneseMode() {
        val function = imeServiceSource().functionBody(
            start = "private fun canShowZeroQueryAfterCommit",
            end = "private fun String.isZeroQueryNumberKey"
        )

        assertTrue(function.contains("currentInputModeForSession != InputMode.ModeJapanese"))
        assertTrue(function.contains("!committedText.isZeroQueryNumberKey()"))
    }

    @Test
    fun pendingZeroQueryLookupUsesCommittedTextForModeDecision() {
        val function = imeServiceSource().functionBody(
            start = "private fun consumePendingZeroQueryAfterCommit",
            end = "private fun handleZeroQueryOnUpdateSelection"
        )

        assertTrue(function.contains("canShowZeroQueryAfterCommit(key)"))
    }

    @Test
    fun zeroQueryCloseItemTogglesVisibilityInsteadOfClearingCandidates() {
        val source = imeServiceSource()

        assertTrue(source.contains("adapter.setOnZeroQueryCloseClickListener"))
        assertTrue(source.contains("toggleZeroQueryVisibility()"))
        assertFalse(
            source.functionBody(
                start = "adapter.setOnZeroQueryCloseClickListener",
                end = "        }"
            ).contains("clearZeroQueryAllState")
        )
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
