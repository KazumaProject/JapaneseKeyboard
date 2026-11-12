package com.kazumaproject.custom_keyboard.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/** A slot stays in the grid when its output is empty. Strings may contain multiple code points. */
data class QwertyPopupSlot(
    @SerializedName("output") val output: String = "",
    @SerializedName("label") val label: String = ""
) {
    val displayLabel: String get() = label.ifEmpty { output }
}

data class QwertyKeyLayer(
    @SerializedName("output") val output: String = "",
    @SerializedName("label") val label: String = "",
    @SerializedName("preview") val preview: String = "",
    @SerializedName("columns") val columns: Int = 3,
    @SerializedName("slots") val slots: List<QwertyPopupSlot> = emptyList()
) {
    val displayLabel: String get() = label.ifEmpty { output }
    val previewLabel: String get() = preview.ifEmpty { output }
    val rowCount: Int get() = (slots.size + columns - 1) / columns

    fun uppercase(): QwertyKeyLayer {
        val transform = KeyCharacterCase.UPPERCASE::transformAsciiLetters
        return copy(output = transform(output), label = transform(label), preview = transform(preview),
            slots = slots.map { it.copy(output = transform(it.output), label = transform(it.label)) })
    }
}

data class QwertyKeyConfig(
    @SerializedName("normal") val normal: QwertyKeyLayer = QwertyKeyLayer(),
    @SerializedName("shifted") val shifted: QwertyKeyLayer? = null
) {
    fun layer(characterCase: KeyCharacterCase): QwertyKeyLayer =
        if (characterCase == KeyCharacterCase.UPPERCASE) shifted ?: normal.uppercase() else normal

    fun isValid(requireOutput: Boolean = true): Boolean = runCatching {
        (listOf(normal) + listOfNotNull(shifted)).all { layer ->
            (!requireOutput || layer.output.isNotEmpty()) && layer.columns in 1..MAX_COLUMNS &&
                layer.slots.size <= MAX_SLOTS && layer.label.length <= MAX_TEXT_LENGTH &&
                layer.preview.length <= MAX_TEXT_LENGTH && layer.output.length <= MAX_TEXT_LENGTH &&
                layer.slots.all { it.output.length <= MAX_TEXT_LENGTH && it.label.length <= MAX_TEXT_LENGTH }
        } && (!requireOutput || normal.output.isNotEmpty())
    }.getOrDefault(false)

    companion object {
        const val MAX_COLUMNS = 12
        const val MAX_SLOTS = 120
        const val MAX_TEXT_LENGTH = 10000
    }
}

/** Explicit names above are also the external backup contract under R8. */
object QwertyKeyConfigCodec {
    private val gson = Gson()
    fun encode(config: QwertyKeyConfig): String = gson.toJson(config)
    fun decode(json: String?, requireOutput: Boolean = true): QwertyKeyConfig? = json?.let {
        runCatching { gson.fromJson(it, QwertyKeyConfig::class.java) }.getOrNull()
            ?.takeIf { config -> config.isValid(requireOutput) }
    }
}
