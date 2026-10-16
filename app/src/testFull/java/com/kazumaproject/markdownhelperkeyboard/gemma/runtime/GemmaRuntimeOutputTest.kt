package com.kazumaproject.markdownhelperkeyboard.gemma.runtime

import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.stubbing.OngoingStubbing
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GemmaRuntimeOutputTest {
    // LiteRT-LM is runtimeOnly; exercise the same reflective API used by the service.
    private fun stub(instance: Any, name: String, vararg parameters: Class<*>): OngoingStubbing<Any?> {
        val arguments = parameters.map { Mockito.any(it) }.toTypedArray()
        return whenever(instance.javaClass.getMethod(name, *parameters).invoke(instance, *arguments))
    }

    private fun generate(text: String, preview: Boolean): IGemmaRuntimeCallback {
        val service = Robolectric.buildService(GemmaRuntimeService::class.java).get()
        val prefix = "com.google.ai.edge.litertlm."
        val engine = Mockito.mock(Class.forName(prefix + "Engine"))
        val conversation = Mockito.mock(Class.forName(prefix + "Conversation"))
        val response = Mockito.mock(Class.forName(prefix + "Message"))
        val callback = mock<IGemmaRuntimeCallback>()
        val contentsClass = Class.forName(prefix + "Contents")
        val companion = contentsClass.getField("Companion").get(null)
        val contents = companion.javaClass.getMethod("of", String::class.java).invoke(companion, text)
        stub(engine, "isInitialized").thenReturn(true)
        stub(engine, "createConversation", Class.forName(prefix + "ConversationConfig")).thenReturn(conversation)
        stub(response, "getContents").thenReturn(contents)
        stub(conversation, "sendMessage", contentsClass, Map::class.java).thenReturn(response)
        ReflectionHelpers.setField(service, "engine", engine)
        try {
            GemmaRuntimeService::class.java.getDeclaredMethod(
                "generateResponse", Long::class.javaPrimitiveType, String::class.java,
                String::class.java, Int::class.javaPrimitiveType, IGemmaRuntimeCallback::class.java,
                Boolean::class.javaPrimitiveType, String::class.java,
            ).apply { isAccessible = true }.invoke(service, 1L, "input", "", 0, callback, preview, "Preserve formatting.")
            conversation.javaClass.getMethod("close").invoke(verify(conversation))
            return callback
        } finally {
            service.onDestroy()
        }
    }

    @Test fun previewKeepsIndentationTabsAndFinalNewlinesThroughResponseExtraction() {
        val text = "    Markdown code\n\tsecond line 😀\n\n"
        val callback = generate(text, preview = true)
        verify(callback).onResult(1L, text)
        verify(callback, never()).onError(any(), any())
    }

    @Test fun foregroundTranslationStillTrimsSurroundingWhitespace() {
        val callback = generate("  Translation\n\n", preview = false)
        verify(callback).onResult(1L, "Translation")
        verify(callback, never()).onError(any(), any())
    }

    @Test fun whitespaceOnlyPreviewIsStillRejected() {
        val callback = generate(" \t\n\n", preview = true)
        verify(callback).onError(eq(1L), any())
        verify(callback, never()).onResult(any(), any())
    }
}
