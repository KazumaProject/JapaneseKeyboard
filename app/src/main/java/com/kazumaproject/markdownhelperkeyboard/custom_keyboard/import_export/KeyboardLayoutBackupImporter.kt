package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export

import android.util.Log
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

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
