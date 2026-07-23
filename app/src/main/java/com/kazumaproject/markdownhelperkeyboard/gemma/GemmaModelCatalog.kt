package com.kazumaproject.markdownhelperkeyboard.gemma

import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaMediaType
import java.io.File

enum class GemmaModality {
    TEXT,
    IMAGE,
    AUDIO,
    TOOLS,
}

data class GemmaModelDescriptor(
    val id: String,
    val displayName: String,
    val filenameTokens: List<String>,
    val modalities: Set<GemmaModality>,
    val approximateBytes: Long,
    val stability: GemmaModelStability = GemmaModelStability.STABLE,
) {
    val runtimeModalityFlags: Int
        get() {
            var flags = 0
            if (GemmaModality.IMAGE in modalities) flags = flags or MODALITY_IMAGE
            if (GemmaModality.AUDIO in modalities) flags = flags or MODALITY_AUDIO
            return flags
        }

    fun supports(mediaType: GemmaMediaType): Boolean = when (mediaType) {
        GemmaMediaType.TEXT -> GemmaModality.TEXT in modalities
        GemmaMediaType.IMAGE -> GemmaModality.IMAGE in modalities
        GemmaMediaType.AUDIO -> GemmaModality.AUDIO in modalities
    }

    companion object {
        private const val MODALITY_IMAGE = 1
        private const val MODALITY_AUDIO = 1 shl 1
    }
}

enum class GemmaModelStability {
    STABLE,
    SPECIALIZED,
    UNKNOWN,
}

data class InstalledGemmaModel(
    val file: File,
    val descriptor: GemmaModelDescriptor,
) {
    val selectionLabel: String
        get() = "${descriptor.displayName} · ${descriptor.modalityLabel()} · ${file.readableSize()}"
}

object GemmaModelCatalog {
    val supportedModels: List<GemmaModelDescriptor> = listOf(
        GemmaModelDescriptor(
            id = "gemma3_270m_it",
            displayName = "Gemma 3 270M IT",
            filenameTokens = listOf("gemma-3-270m", "gemma3-270m"),
            modalities = setOf(GemmaModality.TEXT),
            approximateBytes = 304_005_120L,
        ),
        GemmaModelDescriptor(
            id = "gemma3_1b_it",
            displayName = "Gemma 3 1B IT",
            filenameTokens = listOf("gemma-3-1b", "gemma3-1b"),
            modalities = setOf(GemmaModality.TEXT),
            approximateBytes = 584_417_280L,
        ),
        GemmaModelDescriptor(
            id = "gemma3n_e2b_it",
            displayName = "Gemma 3n E2B IT",
            filenameTokens = listOf("gemma-3n-e2b", "gemma3n-e2b"),
            modalities = setOf(GemmaModality.TEXT, GemmaModality.IMAGE, GemmaModality.AUDIO),
            approximateBytes = 3_655_827_456L,
        ),
        GemmaModelDescriptor(
            id = "gemma3n_e4b_it",
            displayName = "Gemma 3n E4B IT",
            filenameTokens = listOf("gemma-3n-e4b", "gemma3n-e4b"),
            modalities = setOf(GemmaModality.TEXT, GemmaModality.IMAGE, GemmaModality.AUDIO),
            approximateBytes = 4_919_541_760L,
        ),
        GemmaModelDescriptor(
            id = "gemma4_e2b_it",
            displayName = "Gemma 4 E2B IT",
            filenameTokens = listOf("gemma-4-e2b", "gemma4-e2b"),
            modalities = setOf(GemmaModality.TEXT, GemmaModality.IMAGE, GemmaModality.AUDIO),
            approximateBytes = 2_588_147_712L,
        ),
        GemmaModelDescriptor(
            id = "gemma4_e4b_it",
            displayName = "Gemma 4 E4B IT",
            filenameTokens = listOf("gemma-4-e4b", "gemma4-e4b"),
            modalities = setOf(GemmaModality.TEXT, GemmaModality.IMAGE, GemmaModality.AUDIO),
            approximateBytes = 3_659_530_240L,
        ),
        GemmaModelDescriptor(
            id = "functiongemma_270m",
            displayName = "FunctionGemma 270M",
            filenameTokens = listOf("functiongemma-270m", "function-gemma-270m"),
            modalities = setOf(GemmaModality.TEXT, GemmaModality.TOOLS),
            approximateBytes = 288_964_608L,
            stability = GemmaModelStability.SPECIALIZED,
        ),
    )

    fun descriptorFor(file: File): GemmaModelDescriptor = descriptorFor(file.name)

    fun descriptorFor(filename: String): GemmaModelDescriptor {
        val normalized = filename.lowercase()
        return supportedModels.firstOrNull { descriptor ->
            descriptor.filenameTokens.any(normalized::contains)
        } ?: GemmaModelDescriptor(
            id = "custom_${normalized.hashCode().toUInt().toString(16)}",
            displayName = "Custom LiteRT-LM model",
            filenameTokens = emptyList(),
            modalities = setOf(GemmaModality.TEXT),
            approximateBytes = 0L,
            stability = GemmaModelStability.UNKNOWN,
        )
    }

    fun supportedModelsSummary(): String = supportedModels.joinToString(separator = "\n") { model ->
        "${model.displayName} · ${model.modalityLabel()} · ${model.approximateBytes.readableSize()}"
    }
}

fun GemmaModelDescriptor.modalityLabel(): String = buildList {
    if (GemmaModality.TEXT in modalities) add("Text")
    if (GemmaModality.IMAGE in modalities) add("Image")
    if (GemmaModality.AUDIO in modalities) add("Audio")
    if (GemmaModality.TOOLS in modalities) add("Tools")
}.joinToString("/")

fun File.readableSize(): String = length().readableSize()

fun Long.readableSize(): String = when {
    this >= 1_000_000_000L -> String.format("%.2f GB", this / 1_000_000_000.0)
    this >= 1_000_000L -> String.format("%.0f MB", this / 1_000_000.0)
    else -> "$this B"
}
