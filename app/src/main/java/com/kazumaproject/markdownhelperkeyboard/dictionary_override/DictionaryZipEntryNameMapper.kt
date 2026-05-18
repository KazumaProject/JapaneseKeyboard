package com.kazumaproject.markdownhelperkeyboard.dictionary_override

import java.util.Locale

sealed class DictionaryZipEntryNameResolution {
    data class Mapped(
        val key: DictionaryFileKey,
        val entryName: String,
        val basename: String,
    ) : DictionaryZipEntryNameResolution()

    data class Ignored(
        val entryName: String,
        val reason: String,
    ) : DictionaryZipEntryNameResolution()

    data class Unrecognized(
        val entryName: String,
        val basename: String,
    ) : DictionaryZipEntryNameResolution()
}

data class DictionaryZipEntryPlan(
    val totalEntries: Int,
    val recognizedEntries: Int,
    val mappedEntries: List<DictionaryZipMappedEntry>,
    val skipped: List<DictionaryZipSkippedEntry>,
    val duplicateKeys: List<DictionaryZipDuplicateKey>,
) {
    val importableEntries: List<DictionaryZipMappedEntry>
        get() {
            val duplicateKeySet = duplicateKeys.map { it.key }.toSet()
            return mappedEntries.filterNot { it.key in duplicateKeySet }
        }
}

data class DictionaryZipMappedEntry(
    val key: DictionaryFileKey,
    val entryName: String,
)

object DictionaryZipEntryNameMapper {
    private val filenameToKey: Map<String, DictionaryFileKey> = mapOf(
        "tango.dat" to DictionaryFileKey.SYSTEM_TANGO,
        "yomi.dat" to DictionaryFileKey.SYSTEM_YOMI,
        "token.dat" to DictionaryFileKey.SYSTEM_TOKEN,
        "tango_singlekanji.dat" to DictionaryFileKey.SINGLE_KANJI_TANGO,
        "yomi_singlekanji.dat" to DictionaryFileKey.SINGLE_KANJI_YOMI,
        "token_singlekanji.dat" to DictionaryFileKey.SINGLE_KANJI_TOKEN,
        "tango_emoji.dat" to DictionaryFileKey.EMOJI_TANGO,
        "yomi_emoji.dat" to DictionaryFileKey.EMOJI_YOMI,
        "token_emoji.dat" to DictionaryFileKey.EMOJI_TOKEN,
        "tango_emoticon.dat" to DictionaryFileKey.EMOTICON_TANGO,
        "yomi_emoticon.dat" to DictionaryFileKey.EMOTICON_YOMI,
        "token_emoticon.dat" to DictionaryFileKey.EMOTICON_TOKEN,
        "tango_symbol.dat" to DictionaryFileKey.SYMBOL_TANGO,
        "yomi_symbol.dat" to DictionaryFileKey.SYMBOL_YOMI,
        "token_symbol.dat" to DictionaryFileKey.SYMBOL_TOKEN,
        "tango_reading_correction.dat" to DictionaryFileKey.READING_CORRECTION_TANGO,
        "yomi_reading_correction.dat" to DictionaryFileKey.READING_CORRECTION_YOMI,
        "token_reading_correction.dat" to DictionaryFileKey.READING_CORRECTION_TOKEN,
        "tango_kotowaza.dat" to DictionaryFileKey.KOTOWAZA_TANGO,
        "yomi_kotowaza.dat" to DictionaryFileKey.KOTOWAZA_YOMI,
        "token_kotowaza.dat" to DictionaryFileKey.KOTOWAZA_TOKEN,
        "tango_places.dat" to DictionaryFileKey.PLACES_TANGO,
        "yomi_places.dat" to DictionaryFileKey.PLACES_YOMI,
        "token_places.dat" to DictionaryFileKey.PLACES_TOKEN,
        "tango_person_names.dat" to DictionaryFileKey.PERSON_NAME_TANGO,
        "yomi_person_names.dat" to DictionaryFileKey.PERSON_NAME_YOMI,
        "token_person_names.dat" to DictionaryFileKey.PERSON_NAME_TOKEN,
        "tango_wiki.dat" to DictionaryFileKey.WIKI_TANGO,
        "yomi_wiki.dat" to DictionaryFileKey.WIKI_YOMI,
        "token_wiki.dat" to DictionaryFileKey.WIKI_TOKEN,
        "tango_neologd.dat" to DictionaryFileKey.NEOLOGD_TANGO,
        "yomi_neologd.dat" to DictionaryFileKey.NEOLOGD_YOMI,
        "token_neologd.dat" to DictionaryFileKey.NEOLOGD_TOKEN,
        "tango_web.dat" to DictionaryFileKey.WEB_TANGO,
        "yomi_web.dat" to DictionaryFileKey.WEB_YOMI,
        "token_web.dat" to DictionaryFileKey.WEB_TOKEN,
        "connectionid.dat" to DictionaryFileKey.CONNECTION_ID,
        "connectionid.dat.zip" to DictionaryFileKey.CONNECTION_ID,
        "pos_table.dat" to DictionaryFileKey.POS_TABLE,
        "id.def" to DictionaryFileKey.ID_DEF,
    ).filterValues { key -> DictionaryFileKey.values().contains(key) }

    fun map(entryName: String): DictionaryFileKey? =
        (resolve(entryName) as? DictionaryZipEntryNameResolution.Mapped)?.key

    fun isIgnored(entryName: String): Boolean =
        resolve(entryName) is DictionaryZipEntryNameResolution.Ignored

    fun resolve(entryName: String): DictionaryZipEntryNameResolution {
        val trimmedName = entryName.trim()
        val pathSegments = trimmedName.split('/', '\\').filter { it.isNotEmpty() }
        if (pathSegments.firstOrNull()?.equals("__MACOSX", ignoreCase = true) == true) {
            return DictionaryZipEntryNameResolution.Ignored(entryName, "Ignored macOS metadata entry")
        }

        val basename = pathSegments.lastOrNull()?.trim().orEmpty()
        if (basename.isBlank()) {
            return DictionaryZipEntryNameResolution.Ignored(entryName, "Ignored empty entry name")
        }
        if (basename.equals(".DS_Store", ignoreCase = true)) {
            return DictionaryZipEntryNameResolution.Ignored(entryName, "Ignored macOS metadata entry")
        }

        val key = filenameToKey[basename.lowercase(Locale.ROOT)]
            ?: return DictionaryZipEntryNameResolution.Unrecognized(entryName, basename)
        return DictionaryZipEntryNameResolution.Mapped(key, entryName, basename)
    }
}

object DictionaryZipEntryPlanner {
    fun plan(entryNames: List<String>): DictionaryZipEntryPlan {
        val mapped = mutableListOf<DictionaryZipMappedEntry>()
        val skipped = mutableListOf<DictionaryZipSkippedEntry>()

        entryNames.forEach { entryName ->
            when (val resolution = DictionaryZipEntryNameMapper.resolve(entryName)) {
                is DictionaryZipEntryNameResolution.Ignored -> {
                    skipped += DictionaryZipSkippedEntry(entryName, resolution.reason)
                }
                is DictionaryZipEntryNameResolution.Mapped -> {
                    mapped += DictionaryZipMappedEntry(resolution.key, resolution.entryName)
                }
                is DictionaryZipEntryNameResolution.Unrecognized -> {
                    skipped += DictionaryZipSkippedEntry(
                        entryName = entryName,
                        reason = "No matching dictionary key for ${resolution.basename}",
                    )
                }
            }
        }

        val duplicateKeys = mapped
            .groupBy { it.key }
            .filterValues { it.size > 1 }
            .map { (key, entries) ->
                DictionaryZipDuplicateKey(
                    key = key,
                    entryNames = entries.map { it.entryName },
                )
            }

        return DictionaryZipEntryPlan(
            totalEntries = entryNames.size,
            recognizedEntries = mapped.size,
            mappedEntries = mapped,
            skipped = skipped,
            duplicateKeys = duplicateKeys,
        )
    }
}
