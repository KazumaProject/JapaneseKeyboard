package com.kazumaproject.markdownhelperkeyboard.gemma

import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaModelCatalogTest {
    @Test
    fun recognizesAllSupportedFamilies() {
        val cases = mapOf(
            "gemma-3-270m-it-q8.litertlm" to "gemma3_270m_it",
            "Gemma3-1B-IT-int4.litertlm" to "gemma3_1b_it",
            "gemma-3n-E2B-it-litert-lm.litertlm" to "gemma3n_e2b_it",
            "gemma-3n-E4B-it-litert-lm.litertlm" to "gemma3n_e4b_it",
            "gemma-4-E2B-it.litertlm" to "gemma4_e2b_it",
            "gemma-4-E4B-it.litertlm" to "gemma4_e4b_it",
            "functiongemma-270m-ft-mobile-actions.litertlm" to "functiongemma_270m",
        )
        cases.forEach { (filename, expectedId) ->
            assertEquals(expectedId, GemmaModelCatalog.descriptorFor(filename).id)
        }
    }

    @Test
    fun multimodalModelsAdvertiseImageAndAudio() {
        val model = GemmaModelCatalog.descriptorFor("gemma-4-E2B-it.litertlm")
        assertTrue(model.supports(GemmaMediaType.TEXT))
        assertTrue(model.supports(GemmaMediaType.IMAGE))
        assertTrue(model.supports(GemmaMediaType.AUDIO))
    }

    @Test
    fun textModelDoesNotAdvertiseMedia() {
        val model = GemmaModelCatalog.descriptorFor("gemma-3-1b-it.litertlm")
        assertTrue(model.supports(GemmaMediaType.TEXT))
        assertFalse(model.supports(GemmaMediaType.IMAGE))
        assertFalse(model.supports(GemmaMediaType.AUDIO))
    }
}
