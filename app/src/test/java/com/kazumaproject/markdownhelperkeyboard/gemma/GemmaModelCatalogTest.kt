package com.kazumaproject.markdownhelperkeyboard.gemma

import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaMediaType
import org.junit.Assert.*
import org.junit.Test

class GemmaModelCatalogTest {
    @Test fun acceptsFormatsWithoutANameAllowlist() {
        assertEquals(AiModelFormat.LITERT_LM, AiModelFormat.fromFilename("renamed-model.LiteRTLM"))
        assertEquals(AiModelFormat.GGUF, AiModelFormat.fromFilename("Qwen3-0.6B.gguf"))
        assertEquals(AiModelFormat.GGUF, AiModelFormat.fromFilename("model.gguf?download=true"))
        assertNull(AiModelFormat.fromFilename("weights.safetensors"))
        assertNull(AiModelFormat.fromFilename("model.gguf.zip"))
    }

    @Test fun filenameDoesNotGrantImageOrAudioCapabilities() {
        val model = GemmaModelCatalog.descriptorFor("gemma-4-E2B-it.litertlm")
        assertTrue(model.supports(GemmaMediaType.TEXT))
        assertFalse(model.supports(GemmaMediaType.IMAGE))
        assertFalse(model.supports(GemmaMediaType.AUDIO))
    }

    @Test fun runtimeCapabilitiesWorkWithAnyModelName() {
        val model = GemmaModelCatalog.descriptorFor("my-model.gguf").withCapabilities(3)
        assertEquals("my-model", model.displayName)
        assertTrue(model.supports(GemmaMediaType.IMAGE))
        assertTrue(model.supports(GemmaMediaType.AUDIO))
    }
}
