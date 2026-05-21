package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyIconBuiltInDrawable
import com.kazumaproject.custom_keyboard.data.KeyIconType
import com.kazumaproject.custom_keyboard.data.KeyboardLayoutUsageMode
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CircularFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.LongPressFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.SpacerDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepLongPressMappingEntity
import timber.log.Timber
import java.util.UUID
import kotlin.math.ceil

enum class KeyboardBackupFormat {
    LegacyV0Array,
    LegacyV0Object,
    VersionedV1,
    Unsupported
}

object KeyboardBackupFormatDetector {
    fun detect(root: JsonElement): KeyboardBackupFormat {
        return when {
            root.isJsonArray -> KeyboardBackupFormat.LegacyV0Array
            root.isJsonObject -> detectObject(root.asJsonObject)
            else -> KeyboardBackupFormat.Unsupported
        }
    }

    private fun detectObject(obj: JsonObject): KeyboardBackupFormat {
        val version = obj["schemaVersion"]?.takeIf { it.isJsonPrimitive }?.asIntOrNull()
        return when {
            version != null &&
                version in 1..KeyboardLayoutJsonImporter.LATEST_SCHEMA_VERSION &&
                obj["layouts"]?.isJsonArray == true -> KeyboardBackupFormat.VersionedV1

            version != null -> KeyboardBackupFormat.Unsupported
            obj["layouts"]?.isJsonArray == true -> KeyboardBackupFormat.LegacyV0Object
            obj.has("layout") || obj.has("keysWithFlicks") -> KeyboardBackupFormat.LegacyV0Object
            else -> KeyboardBackupFormat.Unsupported
        }
    }
}

object KeyboardBackupParser {
    fun parse(root: JsonElement, gson: Gson): KeyboardLayoutJsonParseResult {
        return when (KeyboardBackupFormatDetector.detect(root)) {
            KeyboardBackupFormat.LegacyV0Array -> parseLegacyArray(root, gson)
            KeyboardBackupFormat.LegacyV0Object -> parseLegacyObject(root.asJsonObject, gson)
            KeyboardBackupFormat.VersionedV1 -> parseVersionedV1(root.asJsonObject, gson)
            KeyboardBackupFormat.Unsupported ->
                KeyboardLayoutJsonParseResult.Failure(KeyboardLayoutImportError.UnsupportedFormat)
        }
    }

    private fun parseLegacyArray(root: JsonElement, gson: Gson): KeyboardLayoutJsonParseResult {
        val type = object : TypeToken<List<KeyboardLayoutExportDto>>() {}.type
        val layouts = gson.fromJson<List<KeyboardLayoutExportDto>?>(root, type) ?: emptyList()
        return KeyboardLayoutJsonParseResult.Success(layouts)
    }

    private fun parseLegacyObject(obj: JsonObject, gson: Gson): KeyboardLayoutJsonParseResult {
        val layoutsElement = obj["layouts"]
        if (layoutsElement?.isJsonArray == true) {
            val type = object : TypeToken<List<KeyboardLayoutExportDto>>() {}.type
            val layouts =
                gson.fromJson<List<KeyboardLayoutExportDto>?>(layoutsElement, type) ?: emptyList()
            return KeyboardLayoutJsonParseResult.Success(layouts)
        }

        val singleDto = gson.fromJson(obj, KeyboardLayoutExportDto::class.java)
        return KeyboardLayoutJsonParseResult.Success(
            if (singleDto != null) listOf(singleDto) else emptyList()
        )
    }

    private fun parseVersionedV1(obj: JsonObject, gson: Gson): KeyboardLayoutJsonParseResult {
        val fileDto = gson.fromJson(obj, KeyboardLayoutExportFileDto::class.java)
        return KeyboardLayoutJsonParseResult.Success(fileDto?.layouts ?: emptyList())
    }
}

object KeyboardBackupNormalizer {
    fun normalize(dtos: List<KeyboardLayoutExportDto>): KeyboardLayoutImportResult {
        if (dtos.isEmpty()) {
            return KeyboardLayoutImportResult.Failure(KeyboardLayoutImportError.NoImportableLayouts)
        }

        val warnings = mutableListOf<KeyboardLayoutImportWarning>()
        val errors = mutableListOf<KeyboardLayoutImportError>()
        val stableIdsByIndex = dtos.mapIndexed { index, dto ->
            val stableId = dto.layout?.stableId?.takeIf { it.isNotBlank() }
            stableId ?: if (dto.layout != null) {
                UUID.randomUUID().toString().also {
                    warnings += KeyboardLayoutImportWarning.MissingLayoutIdentifierGenerated(index)
                }
            } else {
                null
            }
        }
        val stableIdsByOldLayoutId = dtos.mapIndexedNotNull { index, dto ->
            val oldId = dto.layout?.layoutId ?: return@mapIndexedNotNull null
            val stableId = stableIdsByIndex[index] ?: return@mapIndexedNotNull null
            oldId.takeIf { it > 0 }?.let { it to stableId }
        }.toMap()

        val layouts = dtos.mapIndexedNotNull { layoutIndex, dto ->
            normalizeOne(
                layoutIndex = layoutIndex,
                dto = dto,
                generatedStableId = stableIdsByIndex[layoutIndex],
                stableIdsByOldLayoutId = stableIdsByOldLayoutId,
                warnings = warnings,
                errors = errors
            )
        }

        return when {
            layouts.isNotEmpty() && errors.isEmpty() ->
                KeyboardLayoutImportResult.Success(layouts, warnings)

            layouts.isNotEmpty() ->
                KeyboardLayoutImportResult.PartialSuccess(layouts, errors, warnings)

            errors.isNotEmpty() ->
                KeyboardLayoutImportResult.Failure(
                    KeyboardLayoutImportError.NoImportableLayouts,
                    errors + KeyboardLayoutImportError.NoImportableLayouts
                )

            else -> KeyboardLayoutImportResult.Failure(KeyboardLayoutImportError.NoImportableLayouts)
        }
    }

    private fun normalizeOne(
        layoutIndex: Int,
        dto: KeyboardLayoutExportDto,
        generatedStableId: String?,
        stableIdsByOldLayoutId: Map<Long, String>,
        warnings: MutableList<KeyboardLayoutImportWarning>,
        errors: MutableList<KeyboardLayoutImportError>
    ): ImportableKeyboardLayout? {
        val layoutDto = dto.layout ?: run {
            errors += KeyboardLayoutImportError.MissingLayout(layoutIndex)
            return null
        }
        if (dto.spacers == null) {
            warnings += KeyboardLayoutImportWarning.MissingSpacerListTreatedAsEmpty(layoutIndex)
        }
        if (dto.keysWithFlicks == null) {
            errors += KeyboardLayoutImportError.MissingKeys(layoutIndex)
        }

        val rawKeys = dto.keysWithFlicks ?: emptyList()
        val rawSpacers = dto.spacers ?: emptyList()
        val isFlexiblePlacementLayout = layoutDto.isFlexiblePlacementLayout
            ?: hasFlexiblePlacementData(rawKeys, rawSpacers)
        val derivedRowCount = deriveRowCount(rawKeys, rawSpacers)
        val derivedColumnCount = deriveColumnCount(rawKeys, rawSpacers)
        val rowCount = normalizeLayoutDimension(
            original = layoutDto.rowCount,
            derived = derivedRowCount,
            layoutIndex = layoutIndex,
            dimensionName = "rowCount",
            warnings = warnings,
            errors = errors
        ) ?: return null
        val columnCount = normalizeLayoutDimension(
            original = layoutDto.columnCount,
            derived = derivedColumnCount,
            layoutIndex = layoutIndex,
            dimensionName = "columnCount",
            warnings = warnings,
            errors = errors
        ) ?: return null

        val name = normalizeLayoutName(layoutIndex, layoutDto.name, warnings)

        val normalizedLayout = CustomKeyboardLayout(
            layoutId = 0,
            name = name,
            columnCount = columnCount,
            rowCount = rowCount,
            isRomaji = layoutDto.isRomaji ?: false,
            isDirectMode = layoutDto.isDirectMode ?: false,
            createdAt = layoutDto.createdAt?.takeIf { it > 0 } ?: System.currentTimeMillis(),
            sortOrder = 0,
            stableId = generatedStableId ?: UUID.randomUUID().toString(),
            isFlexiblePlacementLayout = isFlexiblePlacementLayout,
            usageMode = parseUsageMode(layoutDto.usageMode)
        )

        val normalizedKeys = normalizeKeys(
            layoutIndex = layoutIndex,
            layoutDto = layoutDto,
            keyDtos = rawKeys,
            rowCount = rowCount,
            columnCount = columnCount,
            stableIdsByOldLayoutId = stableIdsByOldLayoutId,
            warnings = warnings,
            errors = errors
        )
        val normalizedSpacers = normalizeSpacers(
            layoutIndex = layoutIndex,
            layoutDto = layoutDto,
            spacerDtos = rawSpacers,
            rowCount = rowCount,
            columnCount = columnCount,
            errors = errors
        )

        return ImportableKeyboardLayout(
            layout = normalizedLayout,
            keysWithFlicks = normalizedKeys,
            spacers = normalizedSpacers
        )
    }

    private fun parseUsageMode(rawValue: String?): KeyboardLayoutUsageMode {
        val normalized = rawValue?.trim()?.takeIf { it.isNotEmpty() }
            ?: return KeyboardLayoutUsageMode.Normal
        return KeyboardLayoutUsageMode.entries.firstOrNull { mode ->
            mode.serializedName == normalized || mode.name == normalized
        } ?: run {
            Timber.w("Unknown keyboard layout usageMode in backup: %s", normalized)
            KeyboardLayoutUsageMode.Normal
        }
    }

    private fun normalizeLayoutName(
        layoutIndex: Int,
        rawName: String?,
        warnings: MutableList<KeyboardLayoutImportWarning>
    ): String {
        rawName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

        val generatedName = "Imported Keyboard ${layoutIndex + 1}"
        warnings += KeyboardLayoutImportWarning.MissingLayoutNameGenerated(
            layoutIndex = layoutIndex,
            generatedName = generatedName
        )
        return generatedName
    }

    private fun normalizeKeys(
        layoutIndex: Int,
        layoutDto: KeyboardLayoutDto,
        keyDtos: List<KeyWithFlicksExportDto>,
        rowCount: Int,
        columnCount: Int,
        stableIdsByOldLayoutId: Map<Long, String>,
        warnings: MutableList<KeyboardLayoutImportWarning>,
        errors: MutableList<KeyboardLayoutImportError>
    ): List<ImportableKeyWithFlicks> {
        val usedKeyIdentifiers = mutableSetOf<String>()
        return keyDtos.mapIndexedNotNull { keyIndex, keyWithFlicksDto ->
            val keyDto = keyWithFlicksDto.key ?: run {
                errors += KeyboardLayoutImportError.MissingKeys(layoutIndex, keyIndex)
                return@mapIndexedNotNull null
            }
            val validationError = KeyboardBackupValidator.validateKey(
                layoutIndex = layoutIndex,
                keyIndex = keyIndex,
                layoutDto = layoutDto,
                keyDto = keyDto,
                rowCount = rowCount,
                columnCount = columnCount
            )
            if (validationError != null) {
                errors += validationError
                return@mapIndexedNotNull null
            }

            val identifier = keyDto.keyIdentifier
                ?.takeIf { it.isNotBlank() && it !in usedKeyIdentifiers }
                ?: UUID.randomUUID().toString().also {
                    warnings += KeyboardLayoutImportWarning.MissingKeyIdentifierGenerated(
                        layoutIndex,
                        keyIndex
                    )
                }
            usedKeyIdentifiers += identifier

            if (keyWithFlicksDto.flicks == null) {
                warnings += KeyboardLayoutImportWarning.MissingFlickListTreatedAsEmpty(
                    layoutIndex,
                    keyIndex
                )
            }

            val normalizedKey = KeyDefinition(
                keyId = 0,
                ownerLayoutId = 0,
                label = keyDto.label.orEmpty(),
                row = keyDto.row ?: 0,
                column = keyDto.column ?: 0,
                rowSpan = keyDto.rowSpan ?: 1,
                colSpan = keyDto.colSpan ?: 1,
                keyType = enumValueOrNull<KeyType>(keyDto.keyType) ?: KeyType.NORMAL,
                isSpecialKey = keyDto.isSpecialKey ?: false,
                drawableResId = keyDto.drawableResId,
                iconType = normalizedIconType(
                    iconType = keyDto.iconType,
                    iconValue = keyDto.iconValue,
                    layoutIndex = layoutIndex,
                    itemKind = "key",
                    itemIndex = keyIndex,
                    warnings = warnings
                ),
                iconValue = normalizedIconValue(
                    iconType = keyDto.iconType,
                    iconValue = keyDto.iconValue
                ),
                keyIdentifier = identifier,
                action = remapKeyAction(
                    action = keyDto.action,
                    stableIdsByOldLayoutId = stableIdsByOldLayoutId,
                    layoutIndex = layoutIndex,
                    keyIndex = keyIndex,
                    warnings = warnings
                ),
                rowUnits = keyDto.rowUnits?.coerceAtLeast(0),
                columnUnits = keyDto.columnUnits?.coerceAtLeast(0),
                rowSpanUnits = keyDto.rowSpanUnits?.coerceAtLeast(1),
                columnSpanUnits = keyDto.columnSpanUnits?.coerceAtLeast(1)
            )

            ImportableKeyWithFlicks(
                key = normalizedKey,
                flicks = keyWithFlicksDto.flicks.orEmpty().mapIndexedNotNull { flickIndex, flick ->
                    normalizeFlick(layoutIndex, keyIndex, flickIndex, keyDto, flick, stableIdsByOldLayoutId, warnings, errors)
                },
                circularFlicks = keyWithFlicksDto.circularFlicks.orEmpty()
                    .mapIndexedNotNull { flickIndex, flick ->
                        normalizeCircularFlick(layoutIndex, keyIndex, flickIndex, keyDto, flick, stableIdsByOldLayoutId, warnings, errors)
                    },
                twoStepFlicks = keyWithFlicksDto.twoStepFlicks.orEmpty()
                    .mapIndexedNotNull { mappingIndex, mapping ->
                        normalizeTwoStep(layoutIndex, keyIndex, mappingIndex, keyDto, mapping, errors)
                    },
                longPressFlicks = keyWithFlicksDto.longPressFlicks.orEmpty()
                    .mapIndexedNotNull { mappingIndex, mapping ->
                        normalizeLongPress(layoutIndex, keyIndex, mappingIndex, keyDto, mapping, errors)
                    },
                twoStepLongPressFlicks = keyWithFlicksDto.twoStepLongPressFlicks.orEmpty()
                    .mapIndexedNotNull { mappingIndex, mapping ->
                        normalizeTwoStepLongPress(layoutIndex, keyIndex, mappingIndex, keyDto, mapping, errors)
                    }
            )
        }
    }

    private fun normalizeSpacers(
        layoutIndex: Int,
        layoutDto: KeyboardLayoutDto,
        spacerDtos: List<SpacerDefinitionDto>,
        rowCount: Int,
        columnCount: Int,
        errors: MutableList<KeyboardLayoutImportError>
    ): List<SpacerDefinition> {
        return spacerDtos.mapIndexedNotNull { spacerIndex, spacer ->
            val ownerLayoutId = spacer.ownerLayoutId
            val layoutId = layoutDto.layoutId
            if (ownerLayoutId != null && ownerLayoutId > 0 && layoutId != null && layoutId > 0 &&
                ownerLayoutId != layoutId
            ) {
                errors += KeyboardLayoutImportError.BrokenOwnerReference(
                    layoutIndex = layoutIndex,
                    mappingIndex = spacerIndex,
                    reason = "spacer.ownerLayoutId does not match layout.layoutId"
                )
                return@mapIndexedNotNull null
            }
            val rowUnits = spacer.rowUnits ?: 0
            val columnUnits = spacer.columnUnits ?: 0
            val rowSpanUnits = spacer.rowSpanUnits ?: 1
            val columnSpanUnits = spacer.columnSpanUnits ?: 1
            if (rowUnits < 0 || columnUnits < 0 || rowSpanUnits <= 0 || columnSpanUnits <= 0) {
                errors += KeyboardLayoutImportError.InvalidKeyPlacement(
                    layoutIndex = layoutIndex,
                    keyIndex = spacerIndex,
                    reason = "spacer placement has negative position or non-positive span"
                )
                return@mapIndexedNotNull null
            }
            val rowEnd = ceil((rowUnits + rowSpanUnits) / 2.0).toInt()
            val columnEnd = ceil((columnUnits + columnSpanUnits) / 2.0).toInt()
            if (rowEnd > rowCount || columnEnd > columnCount) {
                errors += KeyboardLayoutImportError.InvalidKeyPlacement(
                    layoutIndex = layoutIndex,
                    keyIndex = spacerIndex,
                    reason = "spacer placement exceeds layout size"
                )
                return@mapIndexedNotNull null
            }
            SpacerDefinition(
                spacerId = 0,
                ownerLayoutId = 0,
                itemIdentifier = spacer.itemIdentifier?.takeIf { it.isNotBlank() }
                    ?: UUID.randomUUID().toString(),
                rowUnits = rowUnits,
                columnUnits = columnUnits,
                rowSpanUnits = rowSpanUnits,
                columnSpanUnits = columnSpanUnits,
                sortOrder = spacer.sortOrder ?: 0
            )
        }
    }

    private fun normalizeFlick(
        layoutIndex: Int,
        keyIndex: Int,
        flickIndex: Int,
        keyDto: KeyDefinitionDto,
        flick: FlickMappingDto,
        stableIdsByOldLayoutId: Map<Long, String>,
        warnings: MutableList<KeyboardLayoutImportWarning>,
        errors: MutableList<KeyboardLayoutImportError>
    ): FlickMapping? {
        if (!ownerKeyMatches(keyDto, flick.ownerKeyId)) {
            errors += KeyboardLayoutImportError.BrokenOwnerReference(
                layoutIndex,
                keyIndex,
                flickIndex,
                "flick.ownerKeyId does not match key.keyId"
            )
            return null
        }
        val direction = enumValueOrNull<FlickDirection>(flick.flickDirection) ?: run {
            errors += KeyboardLayoutImportError.InvalidKeyPlacement(
                layoutIndex,
                keyIndex,
                "unknown flickDirection at flick index $flickIndex"
            )
            return null
        }
        val remapped = remapLayoutSwitchFlickAction(
            actionType = flick.actionType ?: "INPUT_TEXT",
            actionValue = flick.actionValue,
            stableIdsByOldLayoutId = stableIdsByOldLayoutId,
            layoutIndex = layoutIndex,
            itemKind = "flick",
            itemIndex = flickIndex,
            warnings = warnings
        )
        return FlickMapping(
            ownerKeyId = 0,
            stateIndex = flick.stateIndex ?: 0,
            flickDirection = direction,
            actionType = remapped.first,
            actionValue = remapped.second,
            iconType = normalizedIconType(
                iconType = flick.iconType,
                iconValue = flick.iconValue,
                layoutIndex = layoutIndex,
                itemKind = "flick",
                itemIndex = flickIndex,
                warnings = warnings
            ),
            iconValue = normalizedIconValue(flick.iconType, flick.iconValue)
        )
    }

    private fun normalizeCircularFlick(
        layoutIndex: Int,
        keyIndex: Int,
        flickIndex: Int,
        keyDto: KeyDefinitionDto,
        flick: CircularFlickMappingDto,
        stableIdsByOldLayoutId: Map<Long, String>,
        warnings: MutableList<KeyboardLayoutImportWarning>,
        errors: MutableList<KeyboardLayoutImportError>
    ): CircularFlickMapping? {
        if (!ownerKeyMatches(keyDto, flick.ownerKeyId)) {
            errors += KeyboardLayoutImportError.BrokenOwnerReference(
                layoutIndex,
                keyIndex,
                flickIndex,
                "circularFlick.ownerKeyId does not match key.keyId"
            )
            return null
        }
        val direction = enumValueOrNull<CircularFlickDirection>(flick.circularDirection) ?: run {
            errors += KeyboardLayoutImportError.InvalidKeyPlacement(
                layoutIndex,
                keyIndex,
                "unknown circularDirection at circular flick index $flickIndex"
            )
            return null
        }
        val remapped = remapLayoutSwitchFlickAction(
            actionType = flick.actionType ?: "INPUT_TEXT",
            actionValue = flick.actionValue,
            stableIdsByOldLayoutId = stableIdsByOldLayoutId,
            layoutIndex = layoutIndex,
            itemKind = "circularFlick",
            itemIndex = flickIndex,
            warnings = warnings
        )
        return CircularFlickMapping(
            ownerKeyId = 0,
            stateIndex = flick.stateIndex ?: 0,
            circularDirection = direction,
            actionType = remapped.first,
            actionValue = remapped.second,
            iconType = normalizedIconType(
                iconType = flick.iconType,
                iconValue = flick.iconValue,
                layoutIndex = layoutIndex,
                itemKind = "circularFlick",
                itemIndex = flickIndex,
                warnings = warnings
            ),
            iconValue = normalizedIconValue(flick.iconType, flick.iconValue)
        )
    }

    private fun normalizedIconType(
        iconType: String?,
        iconValue: String?,
        layoutIndex: Int,
        itemKind: String,
        itemIndex: Int,
        warnings: MutableList<KeyboardLayoutImportWarning>
    ): String? {
        val type = KeyIconType.fromDbValue(iconType) ?: return null
        return when (type) {
            KeyIconType.ACTION_DEFAULT -> type.dbValue
            KeyIconType.DRAWABLE_RESOURCE_NAME -> {
                if (KeyIconBuiltInDrawable.isAllowed(iconValue)) {
                    type.dbValue
                } else {
                    warnings += KeyboardLayoutImportWarning.IconOverrideIgnored(
                        layoutIndex = layoutIndex,
                        itemKind = itemKind,
                        itemIndex = itemIndex,
                        reason = "unknown drawable resource name"
                    )
                    null
                }
            }
            KeyIconType.USER_IMAGE_FILE -> {
                warnings += KeyboardLayoutImportWarning.IconOverrideIgnored(
                    layoutIndex = layoutIndex,
                    itemKind = itemKind,
                    itemIndex = itemIndex,
                    reason = "user image files are not embedded in JSON backups"
                )
                null
            }
        }
    }

    private fun normalizedIconValue(iconType: String?, iconValue: String?): String? {
        val type = KeyIconType.fromDbValue(iconType) ?: return null
        return when (type) {
            KeyIconType.ACTION_DEFAULT -> null
            KeyIconType.DRAWABLE_RESOURCE_NAME ->
                iconValue?.takeIf { KeyIconBuiltInDrawable.isAllowed(it) }
            KeyIconType.USER_IMAGE_FILE -> null
        }
    }

    private fun normalizeTwoStep(
        layoutIndex: Int,
        keyIndex: Int,
        mappingIndex: Int,
        keyDto: KeyDefinitionDto,
        mapping: TwoStepFlickMappingDto,
        errors: MutableList<KeyboardLayoutImportError>
    ): TwoStepFlickMapping? {
        if (!ownerKeyMatches(keyDto, mapping.ownerKeyId)) {
            errors += KeyboardLayoutImportError.BrokenOwnerReference(
                layoutIndex,
                keyIndex,
                mappingIndex,
                "twoStepFlick.ownerKeyId does not match key.keyId"
            )
            return null
        }
        val first = enumValueOrNull<TfbiFlickDirection>(mapping.firstDirection)
        val second = enumValueOrNull<TfbiFlickDirection>(mapping.secondDirection)
        if (first == null || second == null) {
            errors += KeyboardLayoutImportError.InvalidKeyPlacement(
                layoutIndex,
                keyIndex,
                "unknown two-step direction at mapping index $mappingIndex"
            )
            return null
        }
        return TwoStepFlickMapping(0, first, second, mapping.output.orEmpty())
    }

    private fun normalizeLongPress(
        layoutIndex: Int,
        keyIndex: Int,
        mappingIndex: Int,
        keyDto: KeyDefinitionDto,
        mapping: LongPressFlickMappingDto,
        errors: MutableList<KeyboardLayoutImportError>
    ): LongPressFlickMapping? {
        if (!ownerKeyMatches(keyDto, mapping.ownerKeyId)) {
            errors += KeyboardLayoutImportError.BrokenOwnerReference(
                layoutIndex,
                keyIndex,
                mappingIndex,
                "longPressFlick.ownerKeyId does not match key.keyId"
            )
            return null
        }
        val direction = enumValueOrNull<FlickDirection>(mapping.flickDirection) ?: run {
            errors += KeyboardLayoutImportError.InvalidKeyPlacement(
                layoutIndex,
                keyIndex,
                "unknown long-press direction at mapping index $mappingIndex"
            )
            return null
        }
        return LongPressFlickMapping(0, direction, mapping.output.orEmpty())
    }

    private fun normalizeTwoStepLongPress(
        layoutIndex: Int,
        keyIndex: Int,
        mappingIndex: Int,
        keyDto: KeyDefinitionDto,
        mapping: TwoStepLongPressMappingDto,
        errors: MutableList<KeyboardLayoutImportError>
    ): TwoStepLongPressMappingEntity? {
        if (!ownerKeyMatches(keyDto, mapping.ownerKeyId)) {
            errors += KeyboardLayoutImportError.BrokenOwnerReference(
                layoutIndex,
                keyIndex,
                mappingIndex,
                "twoStepLongPressFlick.ownerKeyId does not match key.keyId"
            )
            return null
        }
        val first = enumValueOrNull<TfbiFlickDirection>(mapping.firstDirection)
        val second = enumValueOrNull<TfbiFlickDirection>(mapping.secondDirection)
        if (first == null || second == null) {
            errors += KeyboardLayoutImportError.InvalidKeyPlacement(
                layoutIndex,
                keyIndex,
                "unknown two-step long-press direction at mapping index $mappingIndex"
            )
            return null
        }
        return TwoStepLongPressMappingEntity(0, first, second, mapping.output.orEmpty())
    }

    private fun normalizeLayoutDimension(
        original: Int?,
        derived: Int,
        layoutIndex: Int,
        dimensionName: String,
        warnings: MutableList<KeyboardLayoutImportWarning>,
        errors: MutableList<KeyboardLayoutImportError>
    ): Int? {
        if (original != null && original > 0) return original
        if (derived > 0) {
            warnings += KeyboardLayoutImportWarning.InvalidRowColumnCorrected(
                layoutIndex,
                dimensionName,
                0
            )
            return derived
        }
        errors += KeyboardLayoutImportError.InvalidLayoutSize(
            layoutIndex,
            "$dimensionName is missing and cannot be derived"
        )
        return null
    }

    private fun deriveRowCount(
        keys: List<KeyWithFlicksExportDto>,
        spacers: List<SpacerDefinitionDto>
    ): Int {
        val keyRows = keys.maxOfOrNull { keyWithFlicks ->
            val key = keyWithFlicks.key ?: return@maxOfOrNull 0
            val row = key.row ?: 0
            val rowSpan = key.rowSpan ?: 1
            if (row < 0 || rowSpan <= 0) 0 else row + rowSpan
        } ?: 0
        val spacerRows = spacers.maxOfOrNull {
            val rowUnits = it.rowUnits ?: 0
            val rowSpanUnits = it.rowSpanUnits ?: 1
            if (rowUnits < 0 || rowSpanUnits <= 0) 0 else ceil((rowUnits + rowSpanUnits) / 2.0).toInt()
        } ?: 0
        return maxOf(keyRows, spacerRows)
    }

    private fun deriveColumnCount(
        keys: List<KeyWithFlicksExportDto>,
        spacers: List<SpacerDefinitionDto>
    ): Int {
        val keyColumns = keys.maxOfOrNull { keyWithFlicks ->
            val key = keyWithFlicks.key ?: return@maxOfOrNull 0
            val column = key.column ?: 0
            val colSpan = key.colSpan ?: 1
            if (column < 0 || colSpan <= 0) 0 else column + colSpan
        } ?: 0
        val spacerColumns = spacers.maxOfOrNull {
            val columnUnits = it.columnUnits ?: 0
            val columnSpanUnits = it.columnSpanUnits ?: 1
            if (columnUnits < 0 || columnSpanUnits <= 0) 0 else ceil((columnUnits + columnSpanUnits) / 2.0).toInt()
        } ?: 0
        return maxOf(keyColumns, spacerColumns)
    }

    private fun hasFlexiblePlacementData(
        keys: List<KeyWithFlicksExportDto>,
        spacers: List<SpacerDefinitionDto>
    ): Boolean {
        val alphabetPrefixes = listOf("qwerty_", "azerty_", "dvorak_", "colemak_")
        if (spacers.any { spacer ->
                alphabetPrefixes.any { prefix -> spacer.itemIdentifier?.startsWith(prefix) == true }
            }
        ) {
            return true
        }
        return keys.any { keyWithFlicks ->
            val key = keyWithFlicks.key ?: return@any false
            if (alphabetPrefixes.any { prefix -> key.keyIdentifier?.startsWith(prefix) == true }) {
                return@any true
            }
            val row = key.row ?: 0
            val column = key.column ?: 0
            val rowSpan = key.rowSpan ?: 1
            val colSpan = key.colSpan ?: 1
            val rowUnits = key.rowUnits ?: return@any false
            val columnUnits = key.columnUnits ?: return@any false
            val rowSpanUnits = key.rowSpanUnits ?: return@any false
            val columnSpanUnits = key.columnSpanUnits ?: return@any false
            rowUnits != row * 2 ||
                    columnUnits != column * 2 ||
                    rowSpanUnits != rowSpan * 2 ||
                    columnSpanUnits != colSpan * 2
        }
    }

    private fun remapKeyAction(
        action: String?,
        stableIdsByOldLayoutId: Map<Long, String>,
        layoutIndex: Int,
        keyIndex: Int,
        warnings: MutableList<KeyboardLayoutImportWarning>
    ): String? {
        if (action.isNullOrBlank()) return action
        val movePrefix = "MoveToCustomKeyboard:"
        if (action.startsWith(movePrefix)) {
            val rawTarget = action.removePrefix(movePrefix)
            val oldId = rawTarget.toLongOrNull()
            if (oldId != null) {
                val stableId = stableIdsByOldLayoutId[oldId]
                if (stableId != null) return "$movePrefix$stableId"
                warnings += KeyboardLayoutImportWarning.LayoutSwitchReferenceCouldNotBeResolved(
                    layoutIndex,
                    "key",
                    keyIndex
                )
                return null
            }
        }
        if (action.toLongOrNull() != null) {
            warnings += KeyboardLayoutImportWarning.LayoutSwitchReferenceCouldNotBeResolved(
                layoutIndex,
                "key",
                keyIndex
            )
            return null
        }
        return action
    }

    private fun remapLayoutSwitchFlickAction(
        actionType: String,
        actionValue: String?,
        stableIdsByOldLayoutId: Map<Long, String>,
        layoutIndex: Int,
        itemKind: String,
        itemIndex: Int,
        warnings: MutableList<KeyboardLayoutImportWarning>
    ): Pair<String, String?> {
        if (actionType != "MoveToCustomKeyboard") return actionType to actionValue
        val oldId = actionValue?.toLongOrNull()
        if (oldId == null) return actionType to actionValue
        val stableId = stableIdsByOldLayoutId[oldId]
        if (stableId != null) return actionType to stableId
        warnings += KeyboardLayoutImportWarning.LayoutSwitchReferenceCouldNotBeResolved(
            layoutIndex,
            itemKind,
            itemIndex
        )
        return "INPUT_TEXT" to ""
    }

    private fun ownerKeyMatches(keyDto: KeyDefinitionDto, ownerKeyId: Long?): Boolean {
        val keyId = keyDto.keyId
        return keyId == null || keyId <= 0 || ownerKeyId == null || ownerKeyId <= 0 || ownerKeyId == keyId
    }
}

object KeyboardBackupValidator {
    fun isLayoutBackupRoot(root: JsonElement): Boolean {
        return when (KeyboardBackupFormatDetector.detect(root)) {
            KeyboardBackupFormat.LegacyV0Array ->
                root.asJsonArray.any { isLayoutDtoObject(it) }

            KeyboardBackupFormat.LegacyV0Object,
            KeyboardBackupFormat.VersionedV1 -> {
                val obj = root.asJsonObject
                obj["layouts"]?.takeIf { it.isJsonArray }?.asJsonArray?.any {
                    isLayoutDtoObject(it)
                } == true || isLayoutDtoObject(obj)
            }

            KeyboardBackupFormat.Unsupported -> false
        }
    }

    fun validateKey(
        layoutIndex: Int,
        keyIndex: Int,
        layoutDto: KeyboardLayoutDto,
        keyDto: KeyDefinitionDto,
        rowCount: Int,
        columnCount: Int
    ): KeyboardLayoutImportError? {
        val ownerLayoutId = keyDto.ownerLayoutId
        val layoutId = layoutDto.layoutId
        if (ownerLayoutId != null && ownerLayoutId > 0 && layoutId != null && layoutId > 0 &&
            ownerLayoutId != layoutId
        ) {
            return KeyboardLayoutImportError.BrokenOwnerReference(
                layoutIndex = layoutIndex,
                keyIndex = keyIndex,
                reason = "key.ownerLayoutId does not match layout.layoutId"
            )
        }

        val row = keyDto.row ?: 0
        val column = keyDto.column ?: 0
        val rowSpan = keyDto.rowSpan ?: 1
        val colSpan = keyDto.colSpan ?: 1
        return when {
            row < 0 || column < 0 ->
                KeyboardLayoutImportError.InvalidKeyPlacement(
                    layoutIndex,
                    keyIndex,
                    "row and column must be >= 0"
                )

            rowSpan <= 0 || colSpan <= 0 ->
                KeyboardLayoutImportError.InvalidKeyPlacement(
                    layoutIndex,
                    keyIndex,
                    "rowSpan and colSpan must be > 0"
                )

            row + rowSpan > rowCount || column + colSpan > columnCount ->
                KeyboardLayoutImportError.InvalidKeyPlacement(
                    layoutIndex,
                    keyIndex,
                    "key placement exceeds layout size"
                )

            else -> null
        }
    }

    private fun isLayoutDtoObject(element: JsonElement): Boolean {
        if (!element.isJsonObject) return false
        val obj: JsonObject = element.asJsonObject
        return obj.has("layout") && (obj.has("keysWithFlicks") || obj.has("spacers"))
    }
}

object KeyboardLayoutImportNormalizer {
    fun normalize(dtos: List<KeyboardLayoutExportDto>): KeyboardLayoutImportResult =
        KeyboardBackupNormalizer.normalize(dtos)
}

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? {
    if (value.isNullOrBlank()) return null
    return enumValues<T>().firstOrNull { it.name == value }
}

private fun JsonElement.asIntOrNull(): Int? = runCatching { asInt }.getOrNull()
