package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IMEServiceClipboardPreviewRegressionContractTest {

    @Test
    fun clipboardListenerRefreshesCandidateStripOnMainAfterPrimaryClipChanged() {
        val lines = imeServiceLines()
        val body = declarationBody(lines, "private val clipboardListener").joinToString("\n")

        assertTrue(body.contains("withContext(Dispatchers.Main.immediate)"))
        assertTrue(body.contains("markClipboardPreviewRefreshAfterPrimaryClipChanged()"))
        assertTrue(body.contains("updateClipboardPreview()"))
        assertTrue(body.contains("return@withLock"))
    }

    @Test
    fun onUpdateSelectionRefreshesClipboardPreviewBeforeSelectedTextEarlyReturnAfterCopy() {
        val body = functionBody(imeServiceLines(), "onUpdateSelection").joinToString("\n")

        val selectedCopyRefresh = body.indexOf("selectedTextClipboardPreviewRefreshText == selectedText")
        val updateClipboard = body.indexOf("updateClipboardPreview()", startIndex = selectedCopyRefresh)
        val earlyReturn = body.indexOf("return", startIndex = updateClipboard)

        assertTrue(selectedCopyRefresh >= 0)
        assertTrue(updateClipboard > selectedCopyRefresh)
        assertTrue(earlyReturn > updateClipboard)
    }

    @Test
    fun onUpdateSelectionDoesNotClearAllSuggestionsForPlainSelectedTextBranch() {
        val body = functionBody(imeServiceLines(), "onUpdateSelection").joinToString("\n")

        assertTrue(body.contains("clearSuggestions = hasSelectedTextGemmaActionCandidates()"))
        assertFalse(body.contains("clearSelectedTextGemmaSession(clearSuggestions = true)"))
    }

    @Test
    fun copySelectedTextRefreshesClipboardPreviewImmediately() {
        val body = functionBody(imeServiceLines(), "copySelectedTextToClipboard").joinToString("\n")

        assertTrue(body.contains("appPreference.last_pasted_clipboard_text_preference = \"\""))
        assertTrue(body.contains("markClipboardPreviewRefreshAfterPrimaryClipChanged()"))
        assertTrue(body.contains("updateClipboardPreview()"))
    }

    @Test
    fun clipboardPreviewRefreshFlagControlsEditorSelectionSuppression() {
        val body = functionBody(imeServiceLines(), "buildCandidateStripInputState").joinToString("\n")

        assertTrue(body.contains("selectedTextClipboardPreviewRefreshText != selectedEditorText"))
        assertTrue(body.contains("editorTextSelected = shouldSuppressClipboardPreviewForSelectedText"))
    }

    @Test
    fun clipboardPreviewSnapshotKeepsImageBitmapAndLastPastedCondition() {
        val body = functionBody(imeServiceLines(), "resolveClipboardPreviewSnapshot").joinToString("\n")

        assertTrue(body.contains("bitmap = item.bitmap"))
        assertTrue(body.contains("getClipboardPreviewText(item.text)"))
        assertTrue(body.contains("appPreference.last_pasted_clipboard_text_preference == item.text"))
    }

    private fun imeServiceLines(): List<String> =
        mainFile("java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt")
            .readLines()

    private fun functionBody(lines: List<String>, functionName: String): List<String> {
        val start = lines.indexOfFirst { it.contains("fun $functionName") }
        assertTrue("Missing function $functionName", start >= 0)
        return blockBody(lines, start)
    }

    private fun declarationBody(lines: List<String>, declaration: String): List<String> {
        val start = lines.indexOfFirst { it.contains(declaration) }
        assertTrue("Missing declaration $declaration", start >= 0)
        return blockBody(lines, start)
    }

    private fun blockBody(lines: List<String>, start: Int): List<String> {
        var depth = 0
        var seenBody = false
        for (index in start until lines.size) {
            val line = lines[index]
            depth += line.count { it == '{' }
            seenBody = seenBody || line.contains('{')
            depth -= line.count { it == '}' }
            if (seenBody && depth == 0) {
                return lines.subList(start, index + 1)
            }
        }
        error("Missing block body end from line ${start + 1}")
    }

    private fun mainFile(path: String): File {
        val moduleFile = File("src/main/$path")
        return if (moduleFile.exists()) moduleFile else File("app/src/main/$path")
    }
}
