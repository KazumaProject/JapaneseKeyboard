package com.kazumaproject.markdownhelperkeyboard.ime_service

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenzRequestDispatchContractTest {

    @Test
    fun manualLongPressBypassesAutoDebounceFlow() {
        val lines = imeServiceLines()
        val immediateBody = functionBody(lines, "performImmediateZenzLiveRequest")
            .joinToString("\n")

        assertTrue(immediateBody.contains("performZenzRequest("))
        assertFalse(immediateBody.contains("_zenzRequest.emit("))
        assertFalse(immediateBody.contains(".debounce("))
    }

    @Test
    fun automaticRequestsUseDynamicDebounceAndLatestCancellation() {
        val text = imeServiceLines().joinToString("\n")

        assertTrue(
            text.contains(
                ".debounce { resolveZenzDebounceMillis(zenzDebounceTimePreference) }"
            )
        )
        assertTrue(text.contains(".collectLatest { request ->"))
        assertTrue(text.contains("zenzRuntimeClient.cancelActive()"))
    }

    @Test
    fun spaceLongPressKeepsTheManualRequestSource() {
        val text = imeServiceLines().joinToString("\n")

        assertTrue(text.contains("source = ZenzRequestSource.ManualConvertLongPress"))
        assertTrue(text.contains("performImmediateZenzLiveRequest("))
    }

    private fun imeServiceLines(): List<String> =
        mainFile("java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt")
            .readLines()

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
