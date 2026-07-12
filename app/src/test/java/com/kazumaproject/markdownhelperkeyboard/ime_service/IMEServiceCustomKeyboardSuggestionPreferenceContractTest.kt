package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IMEServiceCustomKeyboardSuggestionPreferenceContractTest {

    @Test
    fun onStartInputViewReloadsCustomKeyboardSuggestionPreference() {
        val source = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"
        ).readText()
        val functionBody = source.substringAfter(
            "override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean)"
        ).substringBefore("override fun onFinishInputView")

        assertTrue(
            "onStartInputView must reload the preference because it can run without onStartInput",
            functionBody.contains("syncCustomKeyboardSuggestionPreference()")
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
