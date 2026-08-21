package com.kazumaproject.markdownhelperkeyboard.ime_service

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QwertyDownFlickInputContractTest {

    @Test
    fun qwertyDownFlickUsesAppendOnlyFlickPath() {
        val source = mainFile().readText()
        val callback = source
            .substringAfter("override fun onFlickDownQWERTYKey(")
            .substringBefore("\n            })")

        assertTrue(callback.contains("handleFlick(character, inputString.value"))
        assertFalse(callback.contains("handleTap(character, inputString.value"))
    }

    private fun mainFile(): File {
        return listOf(
            File("app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"),
            File("src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"),
        ).first { it.isFile }
    }
}
