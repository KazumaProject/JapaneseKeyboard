package com.kazumaproject.markdownhelperkeyboard.gemma.runtime

import com.kazumaproject.markdownhelperkeyboard.ai.AiRuntimeBackend
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GemmaRuntimeOutputTest {
    private fun generate(
        text: String, preview: Boolean, input: String = "input", instruction: String = "",
        verifyBackend: (AiRuntimeBackend) -> Unit = {},
    ): IGemmaRuntimeCallback {
        val service = Robolectric.buildService(GemmaRuntimeService::class.java).get()
        val backend = mock<AiRuntimeBackend>()
        whenever(backend.generate(any(), any(), any(), any(), any())).thenReturn(text)
        val callback = mock<IGemmaRuntimeCallback>()
        ReflectionHelpers.setField(service, "runtimeBackend", backend)
        try {
            GemmaRuntimeService::class.java.getDeclaredMethod(
                "generateResponse", Long::class.javaPrimitiveType, String::class.java,
                String::class.java, Int::class.javaPrimitiveType, IGemmaRuntimeCallback::class.java,
                Boolean::class.javaPrimitiveType, String::class.java, String::class.java,
            ).apply { isAccessible = true }.invoke(service, 1L, input, "", 0, callback, preview, instruction, "")
            verifyBackend(backend)
            return callback
        } finally { service.onDestroy() }
    }

    @Test fun previewKeepsIndentationTabsAndFinalNewlines() {
        val text = "    Markdown code\n\tsecond line 😀\n\n"
        verify(generate(text, true)).onResult(1L, text)
    }
    @Test fun foregroundTextKeepsSurroundingWhitespace() {
        val result = "  Translation\n\n"
        verify(generate(result, false)).onResult(1L, result)
    }
    @Test fun whitespaceOnlyPreviewIsRejected() {
        val callback = generate(" \t\n\n", true)
        verify(callback).onError(eq(1L), any())
        verify(callback, never()).onResult(any(), any())
    }

    @Test fun instructionAndInputReachBothTextPathsSeparatelyAndUnmodified() {
        val input = "    one\n\ttwo 😀 日本語 <input>\n\n"
        val instruction = "Convert every Latin letter to uppercase."
        for (preview in listOf(true, false)) {
            generate("RESULT", preview, input, instruction) { backend ->
                verify(backend).generate(input, "", 0, instruction, "")
            }
        }
    }
}
