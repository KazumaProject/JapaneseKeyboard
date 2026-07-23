package com.kazumaproject.markdownhelperkeyboard.gemma.media

import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaInputModality

internal enum class GemmaImeMediaPhase {
    SELECTING,
    RECORDING,
    READY,
    RUNNING,
    RESULT,
    ERROR,
}

internal enum class GemmaImeImageSource {
    CLIPBOARD,
    DEVICE,
}

internal data class GemmaImeMediaState(
    val visible: Boolean = false,
    val modality: GemmaInputModality = GemmaInputModality.IMAGE,
    val phase: GemmaImeMediaPhase = GemmaImeMediaPhase.SELECTING,
    val mediaPath: String? = null,
    val imageSource: GemmaImeImageSource? = null,
    val status: String = "",
    val result: String = "",
    val candidates: List<String> = emptyList(),
    val error: String? = null,
) {
    val hasMedia: Boolean
        get() = !mediaPath.isNullOrBlank()

    companion object {
        val Hidden = GemmaImeMediaState()
    }
}
