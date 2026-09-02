package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IMEServiceInlineSuggestionToggleIconContractTest {

    @Test
    fun toggleUsesMoreHorizForInlineAndKeyForNormalCandidates() {
        val function = imeServiceSource().functionBody(
            start = "private fun inlineSuggestionToggleForCandidateStrip",
            end = "private fun buildCandidateStripInputState",
        )

        assertTrue(
            function.contains(
                "InlineSuggestionSurface.Inline -> R.drawable.more_horiz_24px"
            )
        )
        assertTrue(
            function.contains(
                "InlineSuggestionSurface.NormalCandidates -> R.drawable.inline_suggestion_key_24"
            )
        )
        assertFalse(function.contains("swap_horiz_24px"))
        assertFalse(function.contains("keyboard_24px"))
        assertFalse(function.contains("henkan"))
        assertFalse(function.contains("arrows_output"))
    }

    @Test
    fun toggleUsesOnlyTheOuterCandidateBackground() {
        val layout = inlineToggleLayoutSource()

        assertTrue(layout.contains("android:background=\"@drawable/recyclerview_item_bg\""))
        assertFalse(layout.contains("android:background=\"@drawable/suggestion_icon_bg\""))
    }

    private fun imeServiceSource(): String =
        listOf(
            File("app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"),
            File("src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"),
        ).first { it.isFile }.readText()

    private fun inlineToggleLayoutSource(): String =
        listOf(
            File("app/src/main/res/layout/suggestion_inline_toggle_item.xml"),
            File("src/main/res/layout/suggestion_inline_toggle_item.xml"),
        ).first { it.isFile }.readText()

    private fun String.functionBody(start: String, end: String): String {
        val startIndex = indexOf(start)
        require(startIndex >= 0) { "Missing start marker: $start" }
        val endIndex = indexOf(end, startIndex + start.length)
        require(endIndex >= 0) { "Missing end marker: $end" }
        return substring(startIndex, endIndex)
    }
}
