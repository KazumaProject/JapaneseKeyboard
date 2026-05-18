package com.kazumaproject.markdownhelperkeyboard.dictionary_override

data class DictionaryZipImportResult(
    val imported: List<DictionaryZipImportedEntry> = emptyList(),
    val skipped: List<DictionaryZipSkippedEntry> = emptyList(),
    val failed: List<DictionaryZipFailedEntry> = emptyList(),
    val duplicateKeys: List<DictionaryZipDuplicateKey> = emptyList(),
    val incompatible: List<String> = emptyList(),
    val totalEntries: Int = 0,
    val recognizedEntries: Int = 0,
) {
    val importedCount: Int
        get() = imported.size

    val failedCount: Int
        get() = failed.size + duplicateKeys.sumOf { it.entryNames.size }
}

data class DictionaryZipImportedEntry(
    val key: DictionaryFileKey,
    val entryName: String,
)

data class DictionaryZipSkippedEntry(
    val entryName: String,
    val reason: String,
)

data class DictionaryZipFailedEntry(
    val entryName: String,
    val reason: String,
)

data class DictionaryZipDuplicateKey(
    val key: DictionaryFileKey,
    val entryNames: List<String>,
)

internal class DictionaryZipImportResultBuilder(
    private val totalEntries: Int,
    private val recognizedEntries: Int,
) {
    val imported = mutableListOf<DictionaryZipImportedEntry>()
    val skipped = mutableListOf<DictionaryZipSkippedEntry>()
    val failed = mutableListOf<DictionaryZipFailedEntry>()
    val duplicateKeys = mutableListOf<DictionaryZipDuplicateKey>()
    val incompatible = mutableListOf<String>()

    fun build(): DictionaryZipImportResult =
        DictionaryZipImportResult(
            imported = imported.toList(),
            skipped = skipped.toList(),
            failed = failed.toList(),
            duplicateKeys = duplicateKeys.toList(),
            incompatible = incompatible.toList(),
            totalEntries = totalEntries,
            recognizedEntries = recognizedEntries,
        )
}
