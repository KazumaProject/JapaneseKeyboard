package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.markdownhelperkeyboard.candidate_order.database.CandidateOrderOverrideDao
import com.kazumaproject.markdownhelperkeyboard.candidate_order.database.CandidateOrderOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

object CandidateOrderOverrideSorter {
    fun apply(
        candidates: List<Candidate>,
        overrides: List<CandidateOrderOverrideEntity>
    ): List<Candidate> {
        if (candidates.size <= 1 || overrides.isEmpty()) return candidates

        val rankByCandidate = overrides.associate { it.candidate to it.rank }

        return candidates
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<Candidate>> {
                    rankByCandidate[it.value.string] ?: Int.MAX_VALUE
                }.thenBy {
                    it.index
                }
            )
            .map { it.value }
    }
}

@Singleton
class CandidateOrderOverrideRepository @Inject constructor(
    private val dao: CandidateOrderOverrideDao
) {
    @Volatile
    private var candidateOrderOverrideMap: Map<String, List<CandidateOrderOverrideEntity>>? = null

    private val snapshotMutex = Mutex()

    fun observeAll(): Flow<List<CandidateOrderOverrideEntity>> =
        dao.observeAll().onEach { entities ->
            updateSnapshot(entities)
        }

    suspend fun saveOrder(input: String, candidates: List<String>) {
        val normalizedInput = input.trim()
        if (normalizedInput.isEmpty()) return

        val now = System.currentTimeMillis()
        val entities = candidates.mapIndexed { index, candidate ->
            CandidateOrderOverrideEntity(
                input = normalizedInput,
                candidate = candidate,
                rank = index + 1,
                createdAt = now,
                updatedAt = now
            )
        }
        dao.replaceForInput(normalizedInput, entities)
        updateSnapshot(dao.getAll())
    }

    suspend fun applyOrder(
        input: String,
        candidates: List<Candidate>
    ): List<Candidate> {
        val normalizedInput = input.trim()
        if (normalizedInput.isEmpty() || candidates.size <= 1) return candidates

        val overrides = dao.findByInput(normalizedInput)
        if (overrides.isEmpty()) return candidates

        return CandidateOrderOverrideSorter.apply(candidates, overrides)
    }

    suspend fun applyOrderFromSnapshot(
        input: String,
        candidates: List<Candidate>
    ): List<Candidate> {
        val normalizedInput = input.trim()
        if (normalizedInput.isEmpty() || candidates.size <= 1) return candidates

        val overrides = currentSnapshot()[normalizedInput].orEmpty()
        if (overrides.isEmpty()) return candidates

        return CandidateOrderOverrideSorter.apply(candidates, overrides)
    }

    suspend fun deleteByInput(input: String) {
        val normalizedInput = input.trim()
        if (normalizedInput.isNotEmpty()) {
            dao.deleteByInput(normalizedInput)
            updateSnapshot(dao.getAll())
        }
    }

    suspend fun deleteAll() {
        dao.deleteAll()
        updateSnapshot(emptyList())
    }

    suspend fun deleteById(id: Int) {
        dao.deleteById(id)
        updateSnapshot(dao.getAll())
    }

    private suspend fun currentSnapshot(): Map<String, List<CandidateOrderOverrideEntity>> {
        candidateOrderOverrideMap?.let { return it }

        return snapshotMutex.withLock {
            candidateOrderOverrideMap ?: updateSnapshot(dao.getAll())
        }
    }

    private fun updateSnapshot(
        entities: List<CandidateOrderOverrideEntity>
    ): Map<String, List<CandidateOrderOverrideEntity>> {
        val snapshot = entities
            .groupBy { it.input.trim() }
            .mapValues { (_, values) -> values.sortedBy { it.rank } }
        candidateOrderOverrideMap = snapshot
        return snapshot
    }
}
