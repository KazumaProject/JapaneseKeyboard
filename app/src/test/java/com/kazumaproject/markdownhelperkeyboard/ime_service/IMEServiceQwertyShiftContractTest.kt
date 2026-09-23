package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IMEServiceQwertyShiftContractTest {

    @Test
    fun softwareQwertyShiftDoesNotOverwritePhysicalShiftState() {
        val source = imeServiceSource()
        val releaseHandler = source.functionBody(
            start = "override fun onReleasedQWERTYKey(",
            end = "override fun onQWERTYShiftStateChanged("
        )

        assertTrue(releaseHandler.contains("softwareQwertyShiftPressed = true"))
        assertFalse(releaseHandler.contains("QWERTYKey.QWERTYKeyShift -> {\n                            hardKeyboardShiftPressd = true"))
        assertTrue(source.contains("get() = hardKeyboardShiftPressd || softwareQwertyShiftPressed"))
    }

    @Test
    fun capsLockOffClearsOnlySoftwareQwertyShiftState() {
        val callback = imeServiceSource().functionBody(
            start = "override fun onQWERTYShiftStateChanged(",
            end = "override fun onLongPressQWERTYKey("
        )

        assertTrue(callback.contains("softwareQwertyCapsLockOn = capsLockOn"))
        assertTrue(callback.contains("if (!capsLockOn && !shiftOn)"))
        assertTrue(callback.contains("softwareQwertyShiftPressed = false"))
        assertFalse(callback.contains("hardKeyboardShiftPressd = false"))
    }

    @Test
    fun compositionAndDeleteBoundariesPreserveCapsLockButClearOneShotLatch() {
        val source = imeServiceSource()

        assertTrue(source.contains("private var softwareQwertyCapsLockOn = false"))
        assertTrue(source.contains("private fun resetSoftwareQwertyShiftAfterCompositionBoundary()"))
        assertTrue(source.contains("softwareQwertyShiftPressed = if (isDefaultRomajiHenkanMap)"))
        assertTrue(source.contains("resetSoftwareQwertyShiftAfterCompositionBoundary()"))

        val qwertyReleaseHandler = source.functionBody(
            start = "override fun onReleasedQWERTYKey(",
            end = "override fun onQWERTYShiftStateChanged("
        )
        val deleteStart = qwertyReleaseHandler.indexOf("QWERTYKey.QWERTYKeyDelete ->")
        val deleteEnd = qwertyReleaseHandler.indexOf(
            "QWERTYKey.QWERTYKeySwitchDefaultLayout ->",
            deleteStart
        )
        require(deleteStart >= 0 && deleteEnd > deleteStart)
        val deleteHandler = qwertyReleaseHandler.substring(deleteStart, deleteEnd)
        assertTrue(deleteHandler.contains("resetSoftwareQwertyShiftAfterCompositionBoundary()"))
        assertFalse(deleteHandler.contains("softwareQwertyShiftPressed = false"))
    }

    @Test
    fun splitStateStoresCapsLockAlongsideSoftwareShiftState() {
        val source = imeServiceSource()

        assertTrue(source.contains("var softwareQwertyCapsLock: Boolean = false"))
        assertTrue(source.contains("softwareQwertyCapsLock = softwareQwertyCapsLockOn"))
        assertTrue(source.contains("softwareQwertyCapsLockOn = state.softwareQwertyCapsLock"))
    }

    @Test
    fun physicalShiftStillSetsThePhysicalShiftState() {
        val handler = imeServiceSource().functionBody(
            start = "private fun handleJapaneseShiftPressed(",
            end = "private fun handleJapaneseCtrlPressed("
        )

        assertTrue(handler.contains("hardKeyboardShiftPressd = true"))
        assertFalse(handler.contains("softwareQwertyShiftPressed"))
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
