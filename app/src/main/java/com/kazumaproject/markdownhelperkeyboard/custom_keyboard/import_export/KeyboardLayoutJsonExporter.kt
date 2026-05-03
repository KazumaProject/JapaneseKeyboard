package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FullKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyWithFlicks

/**
 * カスタムキーボードレイアウトを schemaVersion 付き object 形式の JSON に
 * シリアライズする exporter。
 *
 * 重要:
 * - Room の Relation 用モデル [FullKeyboardLayout] を直接 Gson.toJson に
 *   渡さないようにすることで、Room モデルを変更しても export 形式に影響を
 *   与えにくくする(逆も然り)。
 * - 出力は常に schemaVersion = [KeyboardLayoutJsonImporter.LATEST_SCHEMA_VERSION]
 *   の object 形式。
 */
object KeyboardLayoutJsonExporter {

    private val gson: Gson by lazy {
        GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create()
    }

    /**
     * DB から取得した [FullKeyboardLayout] のリストを JSON 文字列に変換する。
     */
    fun toJson(fullLayouts: List<FullKeyboardLayout>): String {
        val fileDto = KeyboardLayoutExportFileDto(
            schemaVersion = KeyboardLayoutJsonImporter.LATEST_SCHEMA_VERSION,
            layouts = fullLayouts.map { it.toExportDto() }
        )
        return gson.toJson(fileDto)
    }
}

/**
 * Room モデル [FullKeyboardLayout] を export 用 DTO に変換する。
 *
 * - キー単位の flick 系 List は順序を維持したままコピー。
 * - spacers も維持。
 */
internal fun FullKeyboardLayout.toExportDto(): KeyboardLayoutExportDto {
    return KeyboardLayoutExportDto(
        layout = this.layout,
        keysWithFlicks = this.keysWithFlicks.map { it.toExportDto() },
        spacers = this.spacers
    )
}

internal fun KeyWithFlicks.toExportDto(): KeyWithFlicksExportDto {
    return KeyWithFlicksExportDto(
        key = this.key,
        flicks = this.flicks,
        circularFlicks = this.circularFlicks,
        twoStepFlicks = this.twoStepFlicks,
        longPressFlicks = this.longPressFlicks,
        twoStepLongPressFlicks = this.twoStepLongPressFlicks
    )
}
