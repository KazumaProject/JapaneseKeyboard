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

    @Test
    fun editorDeletionMutationsInvalidateZeroQueryState() {
        val source = imeServiceSource()

        listOf(
            "private fun deleteWordOrSymbolsBeforeCursor",
            "private fun deleteWordOrSymbolsAfterCursor",
            "private fun deleteLongPress",
            "private fun deleteLastGraphemeOrSelection",
            "private fun deleteStringCommon",
        ).forEach { functionStart ->
            assertTrue(
                "$functionStart must invalidate zero-query before mutating editor text",
                source.functionBody(
                    start = functionStart,
                    end = "\n    private fun"
                ).contains("invalidateZeroQueryForEditorMutation()")
            )
        }
    }

    @Test
    fun zeroQueryInvalidationForEditorMutationRefreshesCandidateStrip() {
        val function = imeServiceSource().functionBody(
            start = "private fun invalidateZeroQueryForEditorMutation",
            end = "\n    private fun toggleZeroQueryVisibility"
        )

        assertTrue(function.contains("clearZeroQueryAllState(refresh = true)"))
    }

    @Test
    fun candidateStripLayoutIsNotCachedBeforeAdapterIsAttached() {
        val function = imeServiceSource().functionBody(
            start = "private fun setMainSuggestionColumn",
            end = "\n    private fun toggleKeyboardLayoutEditMode"
        )
        val adapterGuard = function.indexOf("val adapter = recyclerView.adapter ?: run")
        val cacheWrite = function.indexOf("lastSuggestionLayoutKey = key")

        assertTrue(adapterGuard >= 0)
        assertTrue(cacheWrite > adapterGuard)
        assertTrue(function.contains("return@measureDebugSection"))
        assertTrue(function.contains("adapter.getItemViewType(position)"))
        assertFalse(function.contains("adapter?.getItemViewType(position)"))
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
