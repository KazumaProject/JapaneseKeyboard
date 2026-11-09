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
                modelPath = "/models/renamed-vision-model.gguf",
                modalityFlags = 1,
            ),
            fileExists = { true },
        )

        assertTrue(result is GemmaImageCapability.Available)
        assertEquals(
            "renamed-vision-model",
            (result as GemmaImageCapability.Available).model.displayName,
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
