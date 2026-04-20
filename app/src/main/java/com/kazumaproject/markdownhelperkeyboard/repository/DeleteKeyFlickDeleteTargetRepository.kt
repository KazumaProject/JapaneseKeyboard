package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.markdownhelperkeyboard.delete_key_flick.database.DeleteKeyFlickDeleteTarget
import com.kazumaproject.markdownhelperkeyboard.delete_key_flick.database.DeleteKeyFlickDeleteTargetDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeleteKeyFlickDeleteTargetRepository @Inject constructor(
    private val dao: DeleteKeyFlickDeleteTargetDao
) {
    companion object {
        const val DEFAULT_TARGET_SYMBOLS = "。、！？「」『』ー."
    }

    fun observeAll(): Flow<List<DeleteKeyFlickDeleteTarget>> = dao.observeAll()

    suspend fun getAll(): List<DeleteKeyFlickDeleteTarget> = dao.getAll()

    suspend fun ensureDefaultTargets() {
        if (dao.count() == 0) {
            resetToDefault()
        }
    }

    suspend fun addSymbol(symbol: String): Boolean {
        val normalized = normalizeSymbol(symbol) ?: return false
        val nextOrder = dao.getAll().maxOfOrNull { it.sortOrder + 1 } ?: 0
        return dao.insert(
            DeleteKeyFlickDeleteTarget(
                symbol = normalized,
                sortOrder = nextOrder
            )
        ) != -1L
    }

    suspend fun updateSymbol(target: DeleteKeyFlickDeleteTarget, symbol: String): Boolean {
        val normalized = normalizeSymbol(symbol) ?: return false
        val existing = dao.findBySymbol(normalized)
        if (existing != null && existing.id != target.id) return false
        dao.update(target.copy(symbol = normalized))
        return true
    }

    suspend fun delete(target: DeleteKeyFlickDeleteTarget): Boolean {
        if (dao.count() <= 1) return false
        dao.delete(target)
        return true
    }

    suspend fun resetToDefault() {
        dao.deleteAll()
        dao.insertAll(defaultTargets())
    }

    private fun defaultTargets(): List<DeleteKeyFlickDeleteTarget> {
        return DEFAULT_TARGET_SYMBOLS.mapIndexed { index, char ->
            DeleteKeyFlickDeleteTarget(symbol = char.toString(), sortOrder = index)
        }
    }

    private fun normalizeSymbol(symbol: String): String? {
        val trimmed = symbol.trim()
        return if (trimmed.length == 1) trimmed else null
    }
}
