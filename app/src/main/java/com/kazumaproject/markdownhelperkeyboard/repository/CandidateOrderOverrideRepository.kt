package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.markdownhelperkeyboard.candidate_order.database.CandidateOrderOverrideDao
import com.kazumaproject.markdownhelperkeyboard.candidate_order.database.CandidateOrderOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import kotlinx.coroutines.flow.Flow
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
    fun observeAll(): Flow<List<CandidateOrderOverrideEntity>> = dao.observeAll()

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

    suspend fun deleteByInput(input: String) {
        val normalizedInput = input.trim()
        if (normalizedInput.isNotEmpty()) {
            dao.deleteByInput(normalizedInput)
        }
    }

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun deleteById(id: Int) = dao.deleteById(id)
}
