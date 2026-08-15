package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IMEServiceTfbiPreferenceContractTest {

    @Test
    fun tfbiStartPositionPreferenceTriggersRuntimeSync() {
        val source = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"
        ).readText()
        val runtimeKeys = source.substringAfter(
            "private val runtimeInputPreferenceKeys = setOf("
        ).substringBefore(")")

        assertTrue(
            "TFBi start-position changes must sync already-inflated keyboards",
            runtimeKeys.contains("AppPreference.FLICK_TFBI_FLICK_START_POSITION_KEY")
        )
    }

    private fun mainFile(relativePath: String): File {
        val candidates = listOf(
            File("app/src/main/$relativePath"),
            File("src/main/$relativePath")
        )
        return candidates.firstOrNull(File::exists)
            ?: error("Unable to locate $relativePath")
    }
}
