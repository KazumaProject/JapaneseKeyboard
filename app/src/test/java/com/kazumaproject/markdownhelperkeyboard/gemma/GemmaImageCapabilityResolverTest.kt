package com.kazumaproject.markdownhelperkeyboard.gemma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaImageCapabilityResolverTest {
    @Test
    fun readyImageModelIsAvailable() {
        val result = GemmaImageCapabilityResolver.resolve(
            hasGemma = true,
            loadState = GemmaLoadState.Ready(
                backend = "GPU",
                modelPath = "/models/gemma-4-E2B-it.litertlm",
            ),
            fileExists = { true },
        )

        assertTrue(result is GemmaImageCapability.Available)
        assertEquals(
            "gemma4_e2b_it",
            (result as GemmaImageCapability.Available).model.id,
        )
    }

    @Test
    fun selectedButNotReadyModelIsUnavailable() {
        val result = GemmaImageCapabilityResolver.resolve(
            hasGemma = true,
            loadState = GemmaLoadState.Loading("gpu_if_available"),
            fileExists = { true },
        )

        assertEquals(
            GemmaImageUnavailableReason.LOADING,
            (result as GemmaImageCapability.Unavailable).reason,
        )
    }

    @Test
    fun readyTextOnlyModelIsUnavailable() {
        val result = GemmaImageCapabilityResolver.resolve(
            hasGemma = true,
            loadState = GemmaLoadState.Ready(
                backend = "CPU",
                modelPath = "/models/gemma-3-1b-it.litertlm",
            ),
            fileExists = { true },
        )

        assertEquals(
            GemmaImageUnavailableReason.MODEL_NOT_IMAGE_CAPABLE,
            (result as GemmaImageCapability.Unavailable).reason,
        )
    }

    @Test
    fun unknownCustomModelIsUnavailableByDefault() {
        val result = GemmaImageCapabilityResolver.resolve(
            hasGemma = true,
            loadState = GemmaLoadState.Ready(
                backend = "CPU",
                modelPath = "/models/custom-model.litertlm",
            ),
            fileExists = { true },
        )

        assertEquals(
            GemmaImageUnavailableReason.MODEL_NOT_IMAGE_CAPABLE,
            (result as GemmaImageCapability.Unavailable).reason,
        )
    }
}
