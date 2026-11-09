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
        get() = "${descriptor.displayName} · ${file.extension.uppercase()} · ${file.readableSize()}"
}

object GemmaModelCatalog {
    fun descriptorFor(file: File): GemmaModelDescriptor = descriptorFor(file.name)

    // A filename is a label, never proof of a model's capabilities.
    fun descriptorFor(filename: String): GemmaModelDescriptor = GemmaModelDescriptor(
        id = "custom_${filename.lowercase().hashCode().toUInt().toString(16)}",
        displayName = File(filename).nameWithoutExtension,
        filenameTokens = emptyList(),
        modalities = setOf(GemmaModality.TEXT),
        approximateBytes = 0L,
        stability = GemmaModelStability.UNKNOWN,
    )
}

fun GemmaModelDescriptor.withCapabilities(flags: Int): GemmaModelDescriptor = copy(
    modalities = buildSet {
        add(GemmaModality.TEXT)
        if (flags and 1 != 0) add(GemmaModality.IMAGE)
        if (flags and 2 != 0) add(GemmaModality.AUDIO)
    },
)

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
