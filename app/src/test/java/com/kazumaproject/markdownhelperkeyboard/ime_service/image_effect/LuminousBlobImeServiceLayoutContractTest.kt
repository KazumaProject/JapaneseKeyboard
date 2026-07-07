package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LuminousBlobImeServiceLayoutContractTest {

    @Test
    fun luminousBlobUsesOwnStableViewAsTouchTarget() {
        val text = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"
        ).readText()

        assertTrue(text.contains("targetContainer = mainView.luminousBlobEffectView"))
        assertTrue(text.contains("targetContainer = floatingView.floatingLuminousBlobEffectView"))
    }

    @Test
    fun luminousBlobViewIsPinnedToKeyboardAreaNotBackgroundChrome() {
        val text = mainFile(
            "java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"
        ).readText()

        assertTrue(text.contains("private fun updateLuminousBlobEffectBounds"))
        assertTrue(text.contains("blobView = mainView.luminousBlobEffectView"))
        assertTrue(text.contains("blobView = floatingView.floatingLuminousBlobEffectView"))
        assertTrue(text.contains("params.height = heightPx"))
        assertTrue(text.contains("params.gravity = Gravity.BOTTOM"))
    }

    private fun mainFile(path: String): File {
        val moduleFile = File("src/main/$path")
        return if (moduleFile.exists()) moduleFile else File("app/src/main/$path")
    }
}
