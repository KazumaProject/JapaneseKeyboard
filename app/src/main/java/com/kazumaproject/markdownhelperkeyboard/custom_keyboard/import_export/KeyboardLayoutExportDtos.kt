package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export

import com.google.gson.annotations.SerializedName

/**
 * External JSON schema for custom keyboard backup files.
 *
 * These DTOs are intentionally separate from Room entities and runtime models.
 * Every external JSON key is pinned with @SerializedName so R8 field renaming
 * cannot change the backup contract.
 */
data class KeyboardLayoutExportFileDto(
    @SerializedName("schemaVersion")
    val schemaVersion: Int? = null,
    @SerializedName("layouts")
    val layouts: List<KeyboardLayoutExportDto>? = null
)

data class KeyboardLayoutExportDto(
    @SerializedName("layout")
    val layout: KeyboardLayoutDto? = null,
    @SerializedName("keysWithFlicks")
    val keysWithFlicks: List<KeyWithFlicksExportDto>? = null,
    @SerializedName("spacers")
    val spacers: List<SpacerDefinitionDto>? = null
)

data class KeyboardLayoutDto(
    @SerializedName("layoutId")
    val layoutId: Long? = null,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("columnCount")
    val columnCount: Int? = null,
    @SerializedName("rowCount")
    val rowCount: Int? = null,
    @SerializedName("isRomaji")
    val isRomaji: Boolean? = null,
    @SerializedName("isDirectMode")
    val isDirectMode: Boolean? = null,
    @SerializedName("createdAt")
    val createdAt: Long? = null,
    @SerializedName("sortOrder")
    val sortOrder: Int? = null,
    @SerializedName("stableId")
    val stableId: String? = null,
    @SerializedName("isFlexiblePlacementLayout")
    val isFlexiblePlacementLayout: Boolean? = null,
    @SerializedName("usageMode")
    val usageMode: String? = null
)

data class KeyWithFlicksExportDto(
    @SerializedName("key")
    val key: KeyDefinitionDto? = null,
    @SerializedName("flicks")
    val flicks: List<FlickMappingDto>? = null,
    @SerializedName("circularFlicks")
    val circularFlicks: List<CircularFlickMappingDto>? = null,
    @SerializedName("twoStepFlicks")
    val twoStepFlicks: List<TwoStepFlickMappingDto>? = null,
    @SerializedName("longPressFlicks")
    val longPressFlicks: List<LongPressFlickMappingDto>? = null,
    @SerializedName("twoStepLongPressFlicks")
    val twoStepLongPressFlicks: List<TwoStepLongPressMappingDto>? = null
)

data class KeyDefinitionDto(
    @SerializedName("keyId")
    val keyId: Long? = null,
    @SerializedName("ownerLayoutId")
    val ownerLayoutId: Long? = null,
    @SerializedName("keyIdentifier")
    val keyIdentifier: String? = null,
    @SerializedName("label")
    val label: String? = null,
    @SerializedName("row")
    val row: Int? = null,
    @SerializedName("column")
    val column: Int? = null,
    @SerializedName("rowSpan")
    val rowSpan: Int? = null,
    @SerializedName("colSpan")
    val colSpan: Int? = null,
    @SerializedName("keyType")
    val keyType: String? = null,
    @SerializedName("isSpecialKey")
    val isSpecialKey: Boolean? = null,
    @SerializedName("drawableResId")
    val drawableResId: Int? = null,
    @SerializedName("action")
    val action: String? = null,
    @SerializedName("rowUnits")
    val rowUnits: Int? = null,
    @SerializedName("columnUnits")
    val columnUnits: Int? = null,
    @SerializedName("rowSpanUnits")
    val rowSpanUnits: Int? = null,
    @SerializedName("columnSpanUnits")
    val columnSpanUnits: Int? = null
)

data class FlickMappingDto(
    @SerializedName("ownerKeyId")
    val ownerKeyId: Long? = null,
    @SerializedName("stateIndex")
    val stateIndex: Int? = null,
    @SerializedName("flickDirection")
    val flickDirection: String? = null,
    @SerializedName("actionType")
    val actionType: String? = null,
    @SerializedName("actionValue")
    val actionValue: String? = null
)

data class CircularFlickMappingDto(
    @SerializedName("ownerKeyId")
    val ownerKeyId: Long? = null,
    @SerializedName("stateIndex")
    val stateIndex: Int? = null,
    @SerializedName("circularDirection")
    val circularDirection: String? = null,
    @SerializedName("actionType")
    val actionType: String? = null,
    @SerializedName("actionValue")
    val actionValue: String? = null
)

data class TwoStepFlickMappingDto(
    @SerializedName("ownerKeyId")
    val ownerKeyId: Long? = null,
    @SerializedName("firstDirection")
    val firstDirection: String? = null,
    @SerializedName("secondDirection")
    val secondDirection: String? = null,
    @SerializedName("output")
    val output: String? = null
)

data class LongPressFlickMappingDto(
    @SerializedName("ownerKeyId")
    val ownerKeyId: Long? = null,
    @SerializedName("flickDirection")
    val flickDirection: String? = null,
    @SerializedName("output")
    val output: String? = null
)

data class TwoStepLongPressMappingDto(
    @SerializedName("ownerKeyId")
    val ownerKeyId: Long? = null,
    @SerializedName("firstDirection")
    val firstDirection: String? = null,
    @SerializedName("secondDirection")
    val secondDirection: String? = null,
    @SerializedName("output")
    val output: String? = null
)

data class SpacerDefinitionDto(
    @SerializedName("spacerId")
    val spacerId: Long? = null,
    @SerializedName("ownerLayoutId")
    val ownerLayoutId: Long? = null,
    @SerializedName("itemIdentifier")
    val itemIdentifier: String? = null,
    @SerializedName("rowUnits")
    val rowUnits: Int? = null,
    @SerializedName("columnUnits")
    val columnUnits: Int? = null,
    @SerializedName("rowSpanUnits")
    val rowSpanUnits: Int? = null,
    @SerializedName("columnSpanUnits")
    val columnSpanUnits: Int? = null,
    @SerializedName("sortOrder")
    val sortOrder: Int? = null
)
