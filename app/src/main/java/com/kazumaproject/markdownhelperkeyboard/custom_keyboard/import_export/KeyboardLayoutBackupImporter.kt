package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CircularFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.LongPressFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.SpacerDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepLongPressMappingEntity
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.ceil

private const val TAG = "KeyboardBackupImport"

enum class KeyboardLayoutBackupFormat {
    JSON_ROOT_ARRAY,
    JSON_ROOT_OBJECT,
    SHARED_PREFERENCES_XML,
    UNSUPPORTED_TEXT
}

object KeyboardLayoutBackupKeys {
    /*
     * The custom keyboard exporter has historically used
     * "keyboard_layouts_backup.json" as the document title. SharedPreferences
     * XML exports store the same logical payload under the extension-less
     * string key shown in legacy backup files.
     */
    const val KEYBOARD_LAYOUTS_BACKUP = "keyboard_layouts_backup"
    val KNOWN_XML_PAYLOAD_KEYS: Set<String> = setOf(KEYBOARD_LAYOUTS_BACKUP)
}

object KeyboardLayoutBackupImporter {
    fun importText(rawText: String): KeyboardLayoutImportResult {
        val sanitized = KeyboardLayoutBackupFormatDetector.sanitize(rawText)
        val format = KeyboardLayoutBackupFormatDetector.detect(sanitized)

        if (format == KeyboardLayoutBackupFormat.UNSUPPORTED_TEXT && sanitized.isBlank()) {
            val error = KeyboardLayoutImportError.EmptyInput
            logFailure(format, error, sanitized.length, null)
            return KeyboardLayoutImportResult.Failure(error)
        }

        val payloadResult = when (format) {
            KeyboardLayoutBackupFormat.JSON_ROOT_ARRAY,
            KeyboardLayoutBackupFormat.JSON_ROOT_OBJECT -> PayloadExtractionResult.Success(
                payload = sanitized,
                format = format,
                selectedXmlKeyName = null
            )

            KeyboardLayoutBackupFormat.SHARED_PREFERENCES_XML ->
                KeyboardLayoutBackupPayloadExtractor.extractFromXml(sanitized)

            KeyboardLayoutBackupFormat.UNSUPPORTED_TEXT -> {
                val error = KeyboardLayoutImportError.UnsupportedFormat
                logFailure(format, error, sanitized.length, null)
                return KeyboardLayoutImportResult.Failure(error)
            }
        }

        val payload = when (payloadResult) {
            is PayloadExtractionResult.Success -> payloadResult.payload
            is PayloadExtractionResult.Failure -> {
                logFailure(format, payloadResult.error, sanitized.length, null)
                return KeyboardLayoutImportResult.Failure(payloadResult.error)
            }
        }

        val parseResult = KeyboardLayoutJsonImporter.parse(payload)
        logResult(
            format = format,
            payloadLength = payload.length,
            selectedXmlKeyName = (payloadResult as? PayloadExtractionResult.Success)?.selectedXmlKeyName,
            result = parseResult
        )
        return parseResult
    }

    private fun logResult(
        format: KeyboardLayoutBackupFormat,
        payloadLength: Int,
        selectedXmlKeyName: String?,
        result: KeyboardLayoutImportResult
    ) {
        when (result) {
            is KeyboardLayoutImportResult.Success -> safeLog(
                priority = Log.WARN,
                message = "import success format=$format layouts=${result.layouts.size} warnings=${result.warnings.size} payloadLength=$payloadLength selectedXmlKey=$selectedXmlKeyName"
            )

            is KeyboardLayoutImportResult.PartialSuccess -> safeLog(
                priority = Log.WARN,
                message = "import partial format=$format layouts=${result.layouts.size} errors=${result.errors.size} warnings=${result.warnings.size} payloadLength=$payloadLength selectedXmlKey=$selectedXmlKeyName"
            )

            is KeyboardLayoutImportResult.Failure ->
                logFailure(format, result.error, payloadLength, selectedXmlKeyName)
        }
    }

    private fun logFailure(
        format: KeyboardLayoutBackupFormat,
        error: KeyboardLayoutImportError,
        payloadLength: Int,
        selectedXmlKeyName: String?
    ) {
        val message =
            "import failure format=$format error=${error::class.simpleName} payloadLength=$payloadLength selectedXmlKey=$selectedXmlKeyName"
        safeLog(priority = Log.ERROR, message = message)
    }

    private fun safeLog(priority: Int, message: String) {
        try {
            if (priority >= Log.ERROR) {
                Log.e(TAG, message)
            } else {
                Log.w(TAG, message)
            }
        } catch (_: Throwable) {
            // JVM unit tests run without Android's Log implementation.
        }
    }
}

object KeyboardLayoutBackupFormatDetector {
    private val BOM_CHAR: Char = 0xFEFF.toChar()
    private val NULL_CHAR: String = 0.toChar().toString()

    fun sanitize(rawText: String): String {
        return rawText
            .trimStart(BOM_CHAR)
            .replace(NULL_CHAR, "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
    }

    fun detect(sanitizedText: String): KeyboardLayoutBackupFormat {
        if (sanitizedText.isBlank()) return KeyboardLayoutBackupFormat.UNSUPPORTED_TEXT
        return when {
            sanitizedText.startsWith("[") -> KeyboardLayoutBackupFormat.JSON_ROOT_ARRAY
            sanitizedText.startsWith("{") -> KeyboardLayoutBackupFormat.JSON_ROOT_OBJECT
            sanitizedText.startsWith("<?xml") || sanitizedText.startsWith("<map") ->
                KeyboardLayoutBackupFormat.SHARED_PREFERENCES_XML

            else -> KeyboardLayoutBackupFormat.UNSUPPORTED_TEXT
        }
    }
}

sealed class PayloadExtractionResult {
    data class Success(
        val payload: String,
        val format: KeyboardLayoutBackupFormat,
        val selectedXmlKeyName: String?
    ) : PayloadExtractionResult()

    data class Failure(val error: KeyboardLayoutImportError) : PayloadExtractionResult()
}

object KeyboardLayoutBackupPayloadExtractor {
    fun extractFromXml(xmlText: String): PayloadExtractionResult {
        val strings = try {
            parseSharedPreferencesStrings(xmlText)
        } catch (e: Exception) {
            return PayloadExtractionResult.Failure(
                KeyboardLayoutImportError.InvalidXml(
                    exceptionClass = e::class.java.simpleName,
                    message = e.message
                )
            )
        }

        KeyboardLayoutBackupKeys.KNOWN_XML_PAYLOAD_KEYS.forEach { knownKey ->
            strings.firstOrNull { it.name == knownKey }?.let { entry ->
                return PayloadExtractionResult.Success(
                    payload = KeyboardLayoutBackupFormatDetector.sanitize(entry.value),
                    format = KeyboardLayoutBackupFormat.SHARED_PREFERENCES_XML,
                    selectedXmlKeyName = entry.name
                )
            }
        }

        strings.firstOrNull { entry ->
            KeyboardLayoutJsonImporter.looksLikeLayoutBackup(entry.value)
        }?.let { entry ->
            return PayloadExtractionResult.Success(
                payload = KeyboardLayoutBackupFormatDetector.sanitize(entry.value),
                format = KeyboardLayoutBackupFormat.SHARED_PREFERENCES_XML,
                selectedXmlKeyName = entry.name
            )
        }

        return PayloadExtractionResult.Failure(KeyboardLayoutImportError.NoLayoutPayloadFound)
    }

    private fun parseSharedPreferencesStrings(xmlText: String): List<SharedPreferencesStringEntry> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeatureIfSupported("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeatureIfSupported("http://xml.org/sax/features/external-general-entities", false)
            setFeatureIfSupported("http://xml.org/sax/features/external-parameter-entities", false)
            setFeatureIfSupported("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        val document = factory.newDocumentBuilder()
            .parse(InputSource(StringReader(xmlText)))

        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).mapNotNull { index ->
            val element = nodes.item(index) as? Element ?: return@mapNotNull null
            val name = element.getAttribute("name").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            SharedPreferencesStringEntry(name = name, value = element.textContent.orEmpty())
        }
    }

    private fun DocumentBuilderFactory.setFeatureIfSupported(feature: String, enabled: Boolean) {
        try {
            setFeature(feature, enabled)
        } catch (_: Exception) {
            // Some Android XML parsers do not expose every feature.
        }
    }
}

private data class SharedPreferencesStringEntry(
    val name: String,
    val value: String
)

object KeyboardLayoutImportNormalizer {
    fun normalize(dtos: List<KeyboardLayoutExportDto>): KeyboardLayoutImportResult {
        if (dtos.isEmpty()) {
            return KeyboardLayoutImportResult.Failure(KeyboardLayoutImportError.NoImportableLayouts)
        }

        val warnings = mutableListOf<KeyboardLayoutImportWarning>()
        val errors = mutableListOf<KeyboardLayoutImportError>()
        val stableIdsByIndex = dtos.mapIndexed { index, dto ->
            val layout = dto.layout
            if (layout == null) {
                null
            } else {
                val stableId: String? = layout.stableId
                stableId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().also {
                    warnings += KeyboardLayoutImportWarning.MissingLayoutIdentifierGenerated(index)
                }
            }
        }
        val stableIdsByOldLayoutId = dtos.mapIndexedNotNull { index, dto ->
            val oldId = dto.layout?.layoutId ?: return@mapIndexedNotNull null
            val stableId = stableIdsByIndex[index] ?: return@mapIndexedNotNull null
            if (oldId > 0) oldId to stableId else null
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
                    KeyboardLayoutImportError.ValidationFailed(
                        reason = errors.joinToString { it::class.simpleName ?: "ValidationFailed" }
                    )
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
        val layout = dto.layout
        if (layout == null) {
            errors += KeyboardLayoutImportError.ValidationFailed(layoutIndex, "layout is missing")
            return null
        }
        if (dto.spacers == null) {
            warnings += KeyboardLayoutImportWarning.MissingSpacerListTreatedAsEmpty(layoutIndex)
        }

        val usedKeyIdentifiers = mutableSetOf<String>()
        val keys = (dto.keysWithFlicks ?: emptyList()).mapIndexedNotNull { keyIndex, keyDto ->
            val key = keyDto.key ?: return@mapIndexedNotNull null
            val normalizedKey = normalizeKey(
                layoutIndex = layoutIndex,
                keyIndex = keyIndex,
                key = key,
                usedKeyIdentifiers = usedKeyIdentifiers,
                stableIdsByOldLayoutId = stableIdsByOldLayoutId,
                warnings = warnings
            )

            if (keyDto.flicks == null) {
                warnings += KeyboardLayoutImportWarning.MissingFlickListTreatedAsEmpty(
                    layoutIndex,
                    keyIndex
                )
            }

            ImportableKeyWithFlicks(
                key = normalizedKey,
                flicks = (keyDto.flicks ?: emptyList()).mapNotNullIndexed { flickIndex, flick ->
                    normalizeFlick(layoutIndex, flickIndex, flick, stableIdsByOldLayoutId, warnings)
                },
                circularFlicks = (keyDto.circularFlicks ?: emptyList()).mapNotNullIndexed { flickIndex, flick ->
                    normalizeCircularFlick(layoutIndex, flickIndex, flick, stableIdsByOldLayoutId, warnings)
                },
                twoStepFlicks = (keyDto.twoStepFlicks ?: emptyList()).map { it.copy(ownerKeyId = 0) },
                longPressFlicks = (keyDto.longPressFlicks ?: emptyList()).map { it.copy(ownerKeyId = 0) },
                twoStepLongPressFlicks = (keyDto.twoStepLongPressFlicks ?: emptyList()).map {
                    it.copy(ownerKeyId = 0)
                }
            )
        }

        val spacers = (dto.spacers ?: emptyList()).mapIndexed { spacerIndex, spacer ->
            normalizeSpacer(layoutIndex, spacerIndex, spacer, warnings)
        }

        val derivedRowCount = deriveRowCount(keys, spacers)
        val derivedColumnCount = deriveColumnCount(keys, spacers)
        val rowCount = correctedLayoutDimension(
            original = layout.rowCount,
            derived = derivedRowCount,
            layoutIndex = layoutIndex,
            dimensionName = "rowCount",
            warnings = warnings,
            errors = errors
        ) ?: return null
        val columnCount = correctedLayoutDimension(
            original = layout.columnCount,
            derived = derivedColumnCount,
            layoutIndex = layoutIndex,
            dimensionName = "columnCount",
            warnings = warnings,
            errors = errors
        ) ?: return null

        val name: String? = layout.name
        val normalizedLayout = CustomKeyboardLayout(
            layoutId = 0,
            name = name?.takeIf { it.isNotBlank() } ?: "Imported Keyboard",
            columnCount = columnCount,
            rowCount = rowCount,
            isRomaji = layout.isRomaji,
            isDirectMode = layout.isDirectMode,
            createdAt = layout.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            sortOrder = 0,
            stableId = generatedStableId ?: UUID.randomUUID().toString()
        )

        return ImportableKeyboardLayout(
            layout = normalizedLayout,
            keysWithFlicks = keys,
            spacers = spacers
        )
    }

    private fun normalizeKey(
        layoutIndex: Int,
        keyIndex: Int,
        key: KeyDefinition,
        usedKeyIdentifiers: MutableSet<String>,
        stableIdsByOldLayoutId: Map<Long, String>,
        warnings: MutableList<KeyboardLayoutImportWarning>
    ): KeyDefinition {
        val importedIdentifier: String? = key.keyIdentifier
        val identifier = importedIdentifier
            ?.takeIf { it.isNotBlank() && it !in usedKeyIdentifiers }
            ?: UUID.randomUUID().toString().also {
                warnings += KeyboardLayoutImportWarning.MissingKeyIdentifierGenerated(
                    layoutIndex,
                    keyIndex
                )
            }
        usedKeyIdentifiers += identifier

        var row = key.row
        var column = key.column
        if (row < 0 || column < 0) {
            row = row.coerceAtLeast(0)
            column = column.coerceAtLeast(0)
            warnings += KeyboardLayoutImportWarning.InvalidRowColumnCorrected(
                layoutIndex,
                "key",
                keyIndex
            )
        }

        var rowSpan = key.rowSpan
        var colSpan = key.colSpan
        if (rowSpan <= 0 || colSpan <= 0) {
            rowSpan = rowSpan.coerceAtLeast(1)
            colSpan = colSpan.coerceAtLeast(1)
            warnings += KeyboardLayoutImportWarning.InvalidSpanCorrected(
                layoutIndex,
                "key",
                keyIndex
            )
        }

        val keyType: KeyType? = key.keyType
        val action = remapKeyAction(
            action = key.action,
            stableIdsByOldLayoutId = stableIdsByOldLayoutId,
            layoutIndex = layoutIndex,
            keyIndex = keyIndex,
            warnings = warnings
        )

        return KeyDefinition(
            keyId = 0,
            ownerLayoutId = 0,
            label = key.label.orEmpty(),
            row = row,
            column = column,
            rowSpan = rowSpan,
            colSpan = colSpan,
            keyType = keyType ?: KeyType.NORMAL,
            isSpecialKey = key.isSpecialKey,
            drawableResId = key.drawableResId,
            keyIdentifier = identifier,
            action = action,
            rowUnits = key.rowUnits?.coerceAtLeast(0),
            columnUnits = key.columnUnits?.coerceAtLeast(0),
            rowSpanUnits = key.rowSpanUnits?.coerceAtLeast(1),
            columnSpanUnits = key.columnSpanUnits?.coerceAtLeast(1)
        )
    }

    private fun normalizeSpacer(
        layoutIndex: Int,
        spacerIndex: Int,
        spacer: SpacerDefinition,
        warnings: MutableList<KeyboardLayoutImportWarning>
    ): SpacerDefinition {
        var rowUnits = spacer.rowUnits
        var columnUnits = spacer.columnUnits
        if (rowUnits < 0 || columnUnits < 0) {
            rowUnits = rowUnits.coerceAtLeast(0)
            columnUnits = columnUnits.coerceAtLeast(0)
            warnings += KeyboardLayoutImportWarning.InvalidRowColumnCorrected(
                layoutIndex,
                "spacer",
                spacerIndex
            )
        }

        var rowSpanUnits = spacer.rowSpanUnits
        var columnSpanUnits = spacer.columnSpanUnits
        if (rowSpanUnits <= 0 || columnSpanUnits <= 0) {
            rowSpanUnits = rowSpanUnits.coerceAtLeast(1)
            columnSpanUnits = columnSpanUnits.coerceAtLeast(1)
            warnings += KeyboardLayoutImportWarning.InvalidSpanCorrected(
                layoutIndex,
                "spacer",
                spacerIndex
            )
        }

        val itemIdentifier: String? = spacer.itemIdentifier
        return SpacerDefinition(
            spacerId = 0,
            ownerLayoutId = 0,
            itemIdentifier = itemIdentifier?.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString(),
            rowUnits = rowUnits,
            columnUnits = columnUnits,
            rowSpanUnits = rowSpanUnits,
            columnSpanUnits = columnSpanUnits,
            sortOrder = spacer.sortOrder
        )
    }

    private fun normalizeFlick(
        layoutIndex: Int,
        flickIndex: Int,
        flick: FlickMapping,
        stableIdsByOldLayoutId: Map<Long, String>,
        warnings: MutableList<KeyboardLayoutImportWarning>
    ): FlickMapping? {
        val direction = (flick.flickDirection as FlickDirection?) ?: run {
            warnings += KeyboardLayoutImportWarning.UnknownOptionalFieldIgnored(
                layoutIndex,
                "flickDirection"
            )
            return null
        }
        val remapped = remapLayoutSwitchFlickAction(
            actionType = flick.actionType,
            actionValue = flick.actionValue,
            stableIdsByOldLayoutId = stableIdsByOldLayoutId,
            layoutIndex = layoutIndex,
            itemKind = "flick",
            itemIndex = flickIndex,
            warnings = warnings
        )
        return flick.copy(
            ownerKeyId = 0,
            flickDirection = direction,
            actionType = remapped.first,
            actionValue = remapped.second
        )
    }

    private fun normalizeCircularFlick(
        layoutIndex: Int,
        flickIndex: Int,
        flick: CircularFlickMapping,
        stableIdsByOldLayoutId: Map<Long, String>,
        warnings: MutableList<KeyboardLayoutImportWarning>
    ): CircularFlickMapping? {
        val direction = (flick.circularDirection as CircularFlickDirection?) ?: run {
            warnings += KeyboardLayoutImportWarning.UnknownOptionalFieldIgnored(
                layoutIndex,
                "circularDirection"
            )
            return null
        }
        val remapped = remapLayoutSwitchFlickAction(
            actionType = flick.actionType,
            actionValue = flick.actionValue,
            stableIdsByOldLayoutId = stableIdsByOldLayoutId,
            layoutIndex = layoutIndex,
            itemKind = "circularFlick",
            itemIndex = flickIndex,
            warnings = warnings
        )
        return flick.copy(
            ownerKeyId = 0,
            circularDirection = direction,
            actionType = remapped.first,
            actionValue = remapped.second
        )
    }

    private fun correctedLayoutDimension(
        original: Int,
        derived: Int,
        layoutIndex: Int,
        dimensionName: String,
        warnings: MutableList<KeyboardLayoutImportWarning>,
        errors: MutableList<KeyboardLayoutImportError>
    ): Int? {
        if (original > 0) return original
        if (derived > 0) {
            warnings += KeyboardLayoutImportWarning.InvalidRowColumnCorrected(
                layoutIndex,
                dimensionName,
                0
            )
            return derived
        }
        errors += KeyboardLayoutImportError.ValidationFailed(
            layoutIndex,
            "$dimensionName is missing and cannot be derived"
        )
        return null
    }

    private fun deriveRowCount(
        keys: List<ImportableKeyWithFlicks>,
        spacers: List<SpacerDefinition>
    ): Int {
        val keyRows = keys.maxOfOrNull { it.key.row + it.key.rowSpan } ?: 0
        val spacerRows = spacers.maxOfOrNull {
            ceil((it.rowUnits + it.rowSpanUnits) / 2.0).toInt()
        } ?: 0
        return maxOf(keyRows, spacerRows)
    }

    private fun deriveColumnCount(
        keys: List<ImportableKeyWithFlicks>,
        spacers: List<SpacerDefinition>
    ): Int {
        val keyColumns = keys.maxOfOrNull { it.key.column + it.key.colSpan } ?: 0
        val spacerColumns = spacers.maxOfOrNull {
            ceil((it.columnUnits + it.columnSpanUnits) / 2.0).toInt()
        } ?: 0
        return maxOf(keyColumns, spacerColumns)
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
}

object KeyboardLayoutImportValidator {
    fun isLayoutBackupRoot(root: JsonElement): Boolean {
        return when {
            root.isJsonArray -> root.asJsonArray.any { isLayoutDtoObject(it) }
            root.isJsonObject -> {
                val obj = root.asJsonObject
                obj["layouts"]?.takeIf { it.isJsonArray }?.asJsonArray?.any {
                    isLayoutDtoObject(it)
                } == true || isLayoutDtoObject(obj)
            }

            else -> false
        }
    }

    private fun isLayoutDtoObject(element: JsonElement): Boolean {
        if (!element.isJsonObject) return false
        val obj: JsonObject = element.asJsonObject
        return obj.has("layout") && (obj.has("keysWithFlicks") || obj.has("spacers"))
    }
}

private inline fun <T, R : Any> Iterable<T>.mapNotNullIndexed(
    transform: (index: Int, T) -> R?
): List<R> {
    val destination = ArrayList<R>()
    forEachIndexed { index, item ->
        transform(index, item)?.let(destination::add)
    }
    return destination
}
