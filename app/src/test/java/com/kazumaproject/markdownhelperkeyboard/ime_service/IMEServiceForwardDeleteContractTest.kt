package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IMEServiceForwardDeleteContractTest {

    @Test
    fun customActionDispatchesToDedicatedForwardDeleteHandler() {
        val source = imeServiceSource()
        val actionDispatch = source.functionBody(
            start = "override fun onAction(action: KeyAction, isFlick: Boolean)",
            end = "private fun handleCustomKeyboardShiftTap"
        )

        assertTrue(actionDispatch.contains("KeyAction.DeleteAfterCursor ->"))
        assertTrue(actionDispatch.contains("handleDeleteAfterCursor()"))
    }

    @Test
    fun committedTextUsesForwardDeleteAndRecordsAfterCursorHistory() {
        val function = imeServiceSource().functionBody(
            start = "private fun performForwardDelete(",
            end = "private fun deleteLastGraphemeOrSelection"
        )

        assertTrue(function.contains("KeyEvent.KEYCODE_FORWARD_DEL"))
        assertTrue(function.contains("DeleteDirection.AfterCursor"))
        assertTrue(function.contains("resetEditorSelectionSnapshot()"))
    }

    @Test
    fun composingTailUsesUnicodeGraphemeBoundariesAndCompositionHistory() {
        val source = imeServiceSource()
        val function = source.functionBody(
            start = "private fun deleteAfterCursorInComposition(",
            end = "private fun performForwardDelete"
        )

        assertTrue(function.contains("nextUnicodeGraphemeOffset(beforeTail, 0)"))
        assertTrue(function.contains("val afterTail = beforeTail.substring(nextOffset)"))
        assertTrue(function.contains("createCompositionHistoryEntry"))
        assertTrue(source.contains("private fun nextUnicodeGraphemeOffset"))
    }

    @Test
    fun forwardDeleteDoesNotStartLongPressRepeat() {
        val source = imeServiceSource()
        listOf(
            "private fun cancelOngoingLongPressForAction",
            "override fun onActionLongPress",
            "override fun onActionUpAfterLongPress",
            "override fun onFlickActionLongPress",
            "override fun onFlickActionUpAfterLongPress",
        ).forEach { marker ->
            val function = source.substring(source.indexOf(marker))
            assertTrue("$marker must handle the new action", function.contains("KeyAction.DeleteAfterCursor"))
        }
        assertFalse(
            source.functionBody(
                start = "override fun onActionLongPress",
                end = "override fun onActionUpAfterLongPress"
            ).contains("handleDeleteAfterCursor()")
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
