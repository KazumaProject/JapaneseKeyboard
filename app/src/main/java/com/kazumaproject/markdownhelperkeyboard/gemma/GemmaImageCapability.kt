package com.kazumaproject.markdownhelperkeyboard.gemma

import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaMediaType
import java.io.File

enum class GemmaImageUnavailableReason {
    BUILD_UNSUPPORTED,
    DISABLED,
    MISSING_MODEL,
    UNSUPPORTED_ABI,
    LOADING,
    LOAD_FAILED,
    MODEL_FILE_MISSING,
    MODEL_NOT_IMAGE_CAPABLE,
}

sealed interface GemmaImageCapability {
    data class Available(
        val modelPath: String,
        val model: GemmaModelDescriptor,
    ) : GemmaImageCapability

    data class Unavailable(
        val reason: GemmaImageUnavailableReason,
    ) : GemmaImageCapability
}

object GemmaImageCapabilityResolver {
    fun resolve(
        hasGemma: Boolean,
        loadState: GemmaLoadState,
        fileExists: (String) -> Boolean = { path -> File(path).isFile },
    ): GemmaImageCapability {
        if (!hasGemma) {
            return GemmaImageCapability.Unavailable(
                GemmaImageUnavailableReason.BUILD_UNSUPPORTED
            )
        }
        val ready = when (loadState) {
            GemmaLoadState.Disabled ->
                return GemmaImageCapability.Unavailable(GemmaImageUnavailableReason.DISABLED)
            GemmaLoadState.MissingModel ->
                return GemmaImageCapability.Unavailable(GemmaImageUnavailableReason.MISSING_MODEL)
            GemmaLoadState.UnsupportedAbi ->
                return GemmaImageCapability.Unavailable(GemmaImageUnavailableReason.UNSUPPORTED_ABI)
            is GemmaLoadState.Loading ->
                return GemmaImageCapability.Unavailable(GemmaImageUnavailableReason.LOADING)
            is GemmaLoadState.Failed ->
                return GemmaImageCapability.Unavailable(GemmaImageUnavailableReason.LOAD_FAILED)
            is GemmaLoadState.Ready -> loadState
        }
        if (!fileExists(ready.modelPath)) {
            return GemmaImageCapability.Unavailable(
                GemmaImageUnavailableReason.MODEL_FILE_MISSING
            )
        }
        val descriptor = GemmaModelCatalog.descriptorFor(File(ready.modelPath))
        if (!descriptor.supports(GemmaMediaType.IMAGE)) {
            return GemmaImageCapability.Unavailable(
                GemmaImageUnavailableReason.MODEL_NOT_IMAGE_CAPABLE
            )
        }
        return GemmaImageCapability.Available(
            modelPath = ready.modelPath,
            model = descriptor,
        )
    }
}
