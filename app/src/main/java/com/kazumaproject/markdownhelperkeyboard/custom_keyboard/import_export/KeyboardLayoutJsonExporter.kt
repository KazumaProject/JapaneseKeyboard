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
        layout = KeyboardLayoutDto(
            layoutId = this.layout.layoutId,
            name = exportLayoutName(this.layout.name, this.layout.layoutId),
            columnCount = this.layout.columnCount,
            rowCount = this.layout.rowCount,
            isRomaji = this.layout.isRomaji,
            isDirectMode = this.layout.isDirectMode,
            createdAt = this.layout.createdAt,
            sortOrder = this.layout.sortOrder,
            stableId = this.layout.stableId,
            isFlexiblePlacementLayout = this.layout.isFlexiblePlacementLayout,
            usageMode = this.layout.usageMode.serializedName
        ),
        keysWithFlicks = this.keysWithFlicks.map { it.toExportDto() },
        spacers = this.spacers.map {
            SpacerDefinitionDto(
                spacerId = it.spacerId,
                ownerLayoutId = it.ownerLayoutId,
                itemIdentifier = it.itemIdentifier,
                rowUnits = it.rowUnits,
                columnUnits = it.columnUnits,
                rowSpanUnits = it.rowSpanUnits,
                columnSpanUnits = it.columnSpanUnits,
                sortOrder = it.sortOrder
            )
        }
    )
}

private fun exportLayoutName(rawName: String?, layoutId: Long): String {
    return rawName?.trim()?.takeIf { it.isNotEmpty() }
        ?: if (layoutId > 0) {
            "Keyboard $layoutId"
        } else {
            "Keyboard"
        }
}

internal fun KeyWithFlicks.toExportDto(): KeyWithFlicksExportDto {
    return KeyWithFlicksExportDto(
        key = KeyDefinitionDto(
            keyId = this.key.keyId,
            ownerLayoutId = this.key.ownerLayoutId,
            keyIdentifier = this.key.keyIdentifier,
            label = this.key.label,
            row = this.key.row,
            column = this.key.column,
            rowSpan = this.key.rowSpan,
            colSpan = this.key.colSpan,
            keyType = this.key.keyType.name,
            isSpecialKey = this.key.isSpecialKey,
            drawableResId = this.key.drawableResId,
            iconType = this.key.iconType,
            iconValue = this.key.iconValue,
            action = this.key.action,
            rowUnits = this.key.rowUnits,
            columnUnits = this.key.columnUnits,
            rowSpanUnits = this.key.rowSpanUnits,
            columnSpanUnits = this.key.columnSpanUnits
        ),
        flicks = this.flicks.map {
            FlickMappingDto(
                ownerKeyId = it.ownerKeyId,
                stateIndex = it.stateIndex,
                flickDirection = it.flickDirection.name,
                actionType = it.actionType,
                actionValue = it.actionValue,
                iconType = it.iconType,
                iconValue = it.iconValue
            )
        },
        circularFlicks = this.circularFlicks.map {
            CircularFlickMappingDto(
                ownerKeyId = it.ownerKeyId,
                stateIndex = it.stateIndex,
                circularDirection = it.circularDirection.name,
                actionType = it.actionType,
                actionValue = it.actionValue,
                iconType = it.iconType,
                iconValue = it.iconValue
            )
        },
        twoStepFlicks = this.twoStepFlicks.map {
            TwoStepFlickMappingDto(
                ownerKeyId = it.ownerKeyId,
                firstDirection = it.firstDirection.name,
                secondDirection = it.secondDirection.name,
                output = it.output
            )
        },
        longPressFlicks = this.longPressFlicks.map {
            LongPressFlickMappingDto(
                ownerKeyId = it.ownerKeyId,
                flickDirection = it.flickDirection.name,
                output = it.output
            )
        },
        twoStepLongPressFlicks = this.twoStepLongPressFlicks.map {
            TwoStepLongPressMappingDto(
                ownerKeyId = it.ownerKeyId,
                firstDirection = it.firstDirection.name,
                secondDirection = it.secondDirection.name,
                output = it.output
            )
        }
    )
}
