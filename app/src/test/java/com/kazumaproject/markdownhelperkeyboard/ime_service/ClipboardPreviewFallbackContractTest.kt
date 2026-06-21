package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ClipboardPreviewFallbackContractTest {

    @Test
    fun bitmapClipboardFallbackStoresImageUriAsNonSensitiveClip() {
        val lines = mainFile("java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt")
            .readLines()
        val body = functionBody(lines, "commitBitmapViaClipboard").joinToString("\n")

        assertTrue(body.contains("clipboardUtil.setClipBoardUri("))
        assertTrue(body.contains("uri = contentUri"))
        assertTrue(body.contains("label = \"Image\""))
        assertTrue(body.contains("isSensitive = false"))
    }

    @Test
    fun clipboardUriDefaultSensitivityRemainsUnchanged() {
        val text = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/clipboard/ClipboardUtil.kt"
        ).readText()

        assertTrue(
            text.contains(
                "fun setClipBoardUri(uri: Uri, label: String = \"Image\", isSensitive: Boolean = true)"
            )
        )
    }

    private fun functionBody(lines: List<String>, functionName: String): List<String> {
        val start = lines.indexOfFirst { it.contains("fun $functionName") }
        assertTrue("Missing function $functionName", start >= 0)

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
        error("Missing function body end for $functionName")
    }

    private fun mainFile(path: String): File {
        val moduleFile = File("src/main/$path")
        return if (moduleFile.exists()) moduleFile else File("app/src/main/$path")
    }
}
