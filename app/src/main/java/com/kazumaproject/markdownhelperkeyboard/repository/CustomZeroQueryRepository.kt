package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.markdownhelperkeyboard.zeroquery.custom.CustomZeroQueryDao
import com.kazumaproject.markdownhelperkeyboard.zeroquery.custom.CustomZeroQueryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class CustomZeroQueryGroup(
    val lookupKey: String,
    val displayKey: String,
    val entries: List<CustomZeroQueryEntry>,
    val updatedAt: Long,
) {
    val enabledCount: Int = entries.count { it.enabled }
}

data class CustomZeroQueryBackupEntry(
    val displayKey: String,
    val candidate: String,
    val enabled: Boolean = true,
    val rank: Int = 0,
)

data class CustomZeroQueryImportResult(
    val added: Int,
    val updated: Int,
    val skipped: Int,
)

enum class CustomZeroQueryImportMode {
    AppendSkipDuplicates,
    AppendReplaceDuplicates,
    ReplaceAll,
}

enum class CustomZeroQuerySaveResult {
    Saved,
    EmptyKey,
    EmptyCandidate,
    Duplicate,
}

object CustomZeroQueryKeyNormalizer {
    fun normalizeKey(rawKey: String): String {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) return ""

        val normalizedDigits = StringBuilder()
        var index = 0
        while (index < trimmed.length) {
            val codePoint = trimmed.codePointAt(index)
            val digit = Character.digit(codePoint, 10)
            if (digit < 0) {
                return trimmed
            }
            normalizedDigits.append(digit)
            index += Character.charCount(codePoint)
        }
        return normalizedDigits.toString()
    }
}

@Singleton
class CustomZeroQueryRepository @Inject constructor(
    private val dao: CustomZeroQueryDao,
) {
    fun observeGroups(): Flow<List<CustomZeroQueryGroup>> =
        dao.observeAll().map { entries -> entries.toGroups() }

    fun observeGroup(lookupKey: String): Flow<List<CustomZeroQueryEntry>> =
        dao.observeByLookupKey(lookupKey)

    suspend fun lookup(key: String): List<CustomZeroQueryEntry> {
        val lookupKey = CustomZeroQueryKeyNormalizer.normalizeKey(key)
        if (lookupKey.isEmpty()) return emptyList()
        return dao.lookupEnabled(lookupKey)
    }

    suspend fun getAll(): List<CustomZeroQueryEntry> = dao.getAll()

    suspend fun exportEntries(): List<CustomZeroQueryBackupEntry> =
        dao.getAll().map {
            CustomZeroQueryBackupEntry(
                displayKey = it.displayKey,
                candidate = it.candidate,
                enabled = it.enabled,
                rank = it.rank,
            )
        }

    suspend fun saveEntry(
        id: Long = 0,
        rawDisplayKey: String,
        rawCandidate: String,
        enabled: Boolean = true,
    ): CustomZeroQuerySaveResult {
        val displayKey = rawDisplayKey.trim()
        val lookupKey = CustomZeroQueryKeyNormalizer.normalizeKey(displayKey)
        val candidate = rawCandidate.trim()
        if (lookupKey.isEmpty()) return CustomZeroQuerySaveResult.EmptyKey
        if (candidate.isEmpty()) return CustomZeroQuerySaveResult.EmptyCandidate
        if (dao.existsDuplicate(lookupKey, candidate, excludeId = id)) {
            return CustomZeroQuerySaveResult.Duplicate
        }

        val now = System.currentTimeMillis()
        if (id == 0L) {
            val rank = dao.maxRankForLookupKey(lookupKey) + 1
            dao.insert(
                CustomZeroQueryEntry(
                    lookupKey = lookupKey,
                    displayKey = displayKey,
                    candidate = candidate,
                    rank = rank,
                    enabled = enabled,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        } else {
            val current = dao.getAll().firstOrNull { it.id == id }
                ?: return CustomZeroQuerySaveResult.EmptyKey
            val rank = if (current.lookupKey == lookupKey) {
                current.rank
            } else {
                dao.maxRankForLookupKey(lookupKey) + 1
            }
            dao.update(
                current.copy(
                    lookupKey = lookupKey,
                    displayKey = displayKey,
                    candidate = candidate,
                    rank = rank,
                    enabled = enabled,
                    updatedAt = now,
                )
            )
        }
        return CustomZeroQuerySaveResult.Saved
    }

    suspend fun renameGroup(
        oldLookupKey: String,
        rawDisplayKey: String,
    ): CustomZeroQuerySaveResult {
        val displayKey = rawDisplayKey.trim()
        val newLookupKey = CustomZeroQueryKeyNormalizer.normalizeKey(displayKey)
        if (newLookupKey.isEmpty()) return CustomZeroQuerySaveResult.EmptyKey

        val entries = dao.getByLookupKey(oldLookupKey)
        if (entries.isEmpty()) return CustomZeroQuerySaveResult.Saved
        val now = System.currentTimeMillis()
        var nextRank = dao.maxRankForLookupKey(newLookupKey)
        entries.forEach { entry ->
            if (entry.lookupKey != newLookupKey &&
                dao.existsDuplicate(newLookupKey, entry.candidate, excludeId = entry.id)
            ) {
                return CustomZeroQuerySaveResult.Duplicate
            }
        }
        entries.forEach { entry ->
            val rank = if (entry.lookupKey == newLookupKey) {
                entry.rank
            } else {
                nextRank += 1
                nextRank
            }
            dao.update(
                entry.copy(
                    lookupKey = newLookupKey,
                    displayKey = displayKey,
                    rank = rank,
                    updatedAt = now,
                )
            )
        }
        return CustomZeroQuerySaveResult.Saved
    }

    suspend fun updateEnabled(entry: CustomZeroQueryEntry, enabled: Boolean) {
        dao.update(entry.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
    }

    suspend fun reorder(lookupKey: String, orderedIds: List<Long>) {
        val entriesById = dao.getByLookupKey(lookupKey).associateBy { it.id }
        val now = System.currentTimeMillis()
        orderedIds.forEachIndexed { index, id ->
            entriesById[id]?.let { entry ->
                val rank = index + 1
                if (entry.rank != rank) {
                    dao.update(entry.copy(rank = rank, updatedAt = now))
                }
            }
        }
    }

    suspend fun deleteEntry(id: Long) = dao.deleteById(id)

    suspend fun deleteGroup(lookupKey: String) = dao.deleteByLookupKey(lookupKey)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun importEntries(
        backupEntries: List<CustomZeroQueryBackupEntry>,
        mode: CustomZeroQueryImportMode,
    ): CustomZeroQueryImportResult {
        val normalized = backupEntries.mapNotNull { entry ->
            val displayKey = entry.displayKey.trim()
            val lookupKey = CustomZeroQueryKeyNormalizer.normalizeKey(displayKey)
            val candidate = entry.candidate.trim()
            if (lookupKey.isEmpty() || candidate.isEmpty()) return@mapNotNull null
            entry.copy(
                displayKey = displayKey,
                candidate = candidate,
                rank = entry.rank.coerceAtLeast(0),
            )
        }
        if (normalized.isEmpty()) {
            if (mode == CustomZeroQueryImportMode.ReplaceAll) dao.deleteAll()
            return CustomZeroQueryImportResult(added = 0, updated = 0, skipped = 0)
        }

        if (mode == CustomZeroQueryImportMode.ReplaceAll) {
            val now = System.currentTimeMillis()
            val rows = normalized
                .groupBy { CustomZeroQueryKeyNormalizer.normalizeKey(it.displayKey) }
                .flatMap { (lookupKey, entries) ->
                    entries
                        .distinctBy { it.candidate }
                        .sortedWith(compareBy<CustomZeroQueryBackupEntry> {
                            if (it.rank == 0) Int.MAX_VALUE else it.rank
                        }.thenBy { it.candidate })
                        .mapIndexed { index, entry ->
                            CustomZeroQueryEntry(
                                lookupKey = lookupKey,
                                displayKey = entry.displayKey,
                                candidate = entry.candidate,
                                rank = index + 1,
                                enabled = entry.enabled,
                                createdAt = now,
                                updatedAt = now,
                            )
                        }
                }
            dao.replaceAll(rows)
            return CustomZeroQueryImportResult(
                added = rows.size,
                updated = 0,
                skipped = normalized.size - rows.size,
            )
        }

        var added = 0
        var updated = 0
        var skipped = 0
        val now = System.currentTimeMillis()
        normalized.forEach { entry ->
            val lookupKey = CustomZeroQueryKeyNormalizer.normalizeKey(entry.displayKey)
            val existing = dao.findByLookupKeyAndCandidate(lookupKey, entry.candidate)
            if (existing != null) {
                if (mode == CustomZeroQueryImportMode.AppendReplaceDuplicates) {
                    dao.update(
                        existing.copy(
                            displayKey = entry.displayKey,
                            enabled = entry.enabled,
                            updatedAt = now,
                        )
                    )
                    updated += 1
                } else {
                    skipped += 1
                }
            } else {
                val rank = if (entry.rank > 0) {
                    entry.rank
                } else {
                    dao.maxRankForLookupKey(lookupKey) + 1
                }
                dao.insert(
                    CustomZeroQueryEntry(
                        lookupKey = lookupKey,
                        displayKey = entry.displayKey,
                        candidate = entry.candidate,
                        rank = rank,
                        enabled = entry.enabled,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
                added += 1
            }
        }
        return CustomZeroQueryImportResult(added = added, updated = updated, skipped = skipped)
    }

    private fun List<CustomZeroQueryEntry>.toGroups(): List<CustomZeroQueryGroup> =
        groupBy { it.lookupKey }
            .map { (lookupKey, entries) ->
                val sorted = entries.sortedWith(compareBy<CustomZeroQueryEntry> { it.rank }.thenBy { it.id })
                CustomZeroQueryGroup(
                    lookupKey = lookupKey,
                    displayKey = sorted.firstOrNull()?.displayKey ?: lookupKey,
                    entries = sorted,
                    updatedAt = sorted.maxOfOrNull { it.updatedAt } ?: 0L,
                )
            }
            .sortedWith(
                compareBy<CustomZeroQueryGroup> { it.displayKey.lowercase() }
                    .thenBy { it.displayKey }
            )
}
