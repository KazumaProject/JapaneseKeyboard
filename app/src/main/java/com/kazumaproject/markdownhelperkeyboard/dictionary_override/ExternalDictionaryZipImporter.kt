package com.kazumaproject.markdownhelperkeyboard.dictionary_override

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream

class ExternalDictionaryZipImporter(
    private val context: Context,
    private val store: DictionaryOverrideStore,
) {
    fun importFromUri(zipUri: Uri): DictionaryZipImportResult {
        val entryNames = scanEntryNames(zipUri).getOrElse { error ->
            return DictionaryZipImportResult(
                failed = listOf(
                    DictionaryZipFailedEntry(
                        entryName = zipUri.toString(),
                        reason = error.message ?: error::class.java.simpleName,
                    )
                ),
            )
        }

        val plan = DictionaryZipEntryPlanner.plan(entryNames)
        val builder = DictionaryZipImportResultBuilder(
            totalEntries = plan.totalEntries,
            recognizedEntries = plan.recognizedEntries,
        )
        builder.skipped += plan.skipped
        builder.duplicateKeys += plan.duplicateKeys

        if (plan.importableEntries.isEmpty()) {
            return builder.build()
        }

        val importableByEntryName = plan.importableEntries.associateBy { it.entryName }
        val duplicateKeySet = plan.duplicateKeys.map { it.key }.toSet()
        context.contentResolver.openInputStream(zipUri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val mapped = importableByEntryName[entry.name]
                        if (mapped != null && mapped.key !in duplicateKeySet) {
                            if (entry.size == 0L) {
                                builder.failed += DictionaryZipFailedEntry(
                                    entryName = mapped.entryName,
                                    reason = "Empty file",
                                )
                            } else {
                                val result = store.saveOverrideFromZipEntryInputStream(
                                    key = mapped.key,
                                    inputStream = zip,
                                    entryName = mapped.entryName,
                                )
                                if (result.isValid) {
                                    builder.imported += DictionaryZipImportedEntry(
                                        key = mapped.key,
                                        entryName = mapped.entryName,
                                    )
                                } else {
                                    builder.failed += DictionaryZipFailedEntry(
                                        entryName = mapped.entryName,
                                        reason = result.message,
                                    )
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: run {
            builder.failed += DictionaryZipFailedEntry(
                entryName = zipUri.toString(),
                reason = "Could not open selected ZIP",
            )
        }

        return builder.build()
    }

    private fun scanEntryNames(zipUri: Uri): Result<List<String>> =
        runCatching {
            val input = context.contentResolver.openInputStream(zipUri)
                ?: error("Could not open selected ZIP")
            input.use {
                ZipInputStream(BufferedInputStream(it)).use { zip ->
                    val names = mutableListOf<String>()
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            names += entry.name
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                    names
                }
            }
        }
}
