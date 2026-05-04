package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export

sealed class KeyboardLayoutImportResult {
    data class Success(
        val layouts: List<ImportableKeyboardLayout>,
        val warnings: List<KeyboardLayoutImportWarning> = emptyList()
    ) : KeyboardLayoutImportResult()

    data class PartialSuccess(
        val layouts: List<ImportableKeyboardLayout>,
        val errors: List<KeyboardLayoutImportError>,
        val warnings: List<KeyboardLayoutImportWarning> = emptyList()
    ) : KeyboardLayoutImportResult()

    data class Failure(
        val error: KeyboardLayoutImportError
    ) : KeyboardLayoutImportResult()
}

sealed class KeyboardLayoutImportError {
    data object EmptyInput : KeyboardLayoutImportError()
    data object UnsupportedFormat : KeyboardLayoutImportError()
    data class InvalidJson(
        val exceptionClass: String? = null,
        val message: String? = null
    ) : KeyboardLayoutImportError()

    data class InvalidXml(
        val exceptionClass: String? = null,
        val message: String? = null
    ) : KeyboardLayoutImportError()

    data object NoLayoutPayloadFound : KeyboardLayoutImportError()
    data object SchemaMismatch : KeyboardLayoutImportError()
    data object NoImportableLayouts : KeyboardLayoutImportError()
    data class ValidationFailed(
        val layoutIndex: Int? = null,
        val reason: String
    ) : KeyboardLayoutImportError()

    data class StorageFailed(
        val layoutIndex: Int? = null,
        val exceptionClass: String? = null,
        val message: String? = null
    ) : KeyboardLayoutImportError()
}

sealed class KeyboardLayoutImportWarning {
    data class MissingKeyIdentifierGenerated(val layoutIndex: Int, val keyIndex: Int) :
        KeyboardLayoutImportWarning()

    data class MissingLayoutIdentifierGenerated(val layoutIndex: Int) :
        KeyboardLayoutImportWarning()

    data class MissingFlickListTreatedAsEmpty(val layoutIndex: Int, val keyIndex: Int) :
        KeyboardLayoutImportWarning()

    data class MissingSpacerListTreatedAsEmpty(val layoutIndex: Int) :
        KeyboardLayoutImportWarning()

    data class InvalidSpanCorrected(
        val layoutIndex: Int,
        val itemKind: String,
        val itemIndex: Int
    ) : KeyboardLayoutImportWarning()

    data class InvalidRowColumnCorrected(
        val layoutIndex: Int,
        val itemKind: String,
        val itemIndex: Int
    ) : KeyboardLayoutImportWarning()

    data class LayoutSwitchReferenceCouldNotBeResolved(
        val layoutIndex: Int,
        val itemKind: String,
        val itemIndex: Int
    ) : KeyboardLayoutImportWarning()

    data class UnknownOptionalFieldIgnored(
        val layoutIndex: Int,
        val fieldName: String
    ) : KeyboardLayoutImportWarning()
}
