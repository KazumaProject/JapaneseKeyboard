package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.markdownhelperkeyboard.candidate_order.database.CandidateOrderOverrideDao
import com.kazumaproject.markdownhelperkeyboard.candidate_order.database.CandidateOrderOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.candidate_order.model.CandidateOrderScope
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedHashMap
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

    fun applyByConversionSegment(
        input: String,
        candidates: List<Candidate>,
        overridesByInput: Map<String, List<CandidateOrderOverrideEntity>>,
        candidateSegmentsByString: Map<String, List<CandidateConversionSegment>>,
    ): List<Candidate> {
        if (candidates.size <= 1 || overridesByInput.isEmpty()) return candidates

        val exactOverrides = overridesByInput[input]
            .orEmpty()
            .filter { it.scope == CandidateOrderScope.EXACT_INPUT.name }
        if (exactOverrides.isNotEmpty()) {
            return apply(candidates, exactOverrides)
        }
        if (candidateSegmentsByString.isEmpty()) return candidates

        val referenceSegments = candidateSegmentsByString[candidates.first().string]
            ?: return candidates
        val referenceBoundarySignature = referenceSegments.boundarySignature(input.length)
            ?: return candidates

        val savedInputPrefixes = overridesByInput.keys
            .asSequence()
            .filter { savedInput ->
                savedInput.isNotEmpty() &&
                        savedInput.length <= input.length &&
                        input.startsWith(savedInput)
            }
            .sortedByDescending { it.length }

        for (savedInput in savedInputPrefixes) {
            val rankBySegmentOutput = overridesByInput.getValue(savedInput)
                .filter { it.scope == CandidateOrderScope.LEXICAL_UNIT.name }
                .associate { it.candidate to it.rank }
            if (rankBySegmentOutput.isEmpty()) continue

            val referenceOutput = referenceSegments.leadingOutputAtInputEnd(savedInput.length)
                ?: continue
            if (referenceOutput !in rankBySegmentOutput) continue

            val cohortIndices = candidates.indices.filter { index ->
                candidateSegmentsByString[candidates[index].string]
                    ?.boundarySignature(input.length) == referenceBoundarySignature
            }
            if (cohortIndices.size <= 1) continue

            val reorderedCohort = cohortIndices
                .map { index -> index to candidates[index] }
                .sortedWith(
                    compareBy<Pair<Int, Candidate>> { (_, candidate) ->
                        candidateSegmentsByString.getValue(candidate.string)
                            .leadingOutputAtInputEnd(savedInput.length)
                            ?.let(rankBySegmentOutput::get)
                            ?: Int.MAX_VALUE
                    }.thenBy { it.first },
                )
                .map { it.second }

            val result = candidates.toMutableList()
            cohortIndices.forEachIndexed { cohortIndex, candidateIndex ->
                result[candidateIndex] = reorderedCohort[cohortIndex]
            }

            return result
        }

        return candidates
    }

    private fun List<CandidateConversionSegment>.leadingOutputAtInputEnd(
        targetInputEnd: Int,
    ): String? {
        if (targetInputEnd <= 0) return null

        var expectedInputStart = 0
        val output = StringBuilder()
        for (segment in this) {
            if (
                segment.inputStart != expectedInputStart ||
                segment.inputEnd <= segment.inputStart ||
                segment.inputEnd > targetInputEnd
            ) {
                return null
            }
            output.append(segment.output)
            expectedInputStart = segment.inputEnd
            if (expectedInputStart == targetInputEnd) {
                return output.toString()
            }
        }
        return null
    }

    private fun List<CandidateConversionSegment>.boundarySignature(
        inputLength: Int,
    ): List<Int>? {
        if (isEmpty()) return null
        var expectedStart = 0
        val boundaries = ArrayList<Int>(size)
        for (segment in this) {
            if (segment.inputStart != expectedStart || segment.inputEnd <= segment.inputStart) {
                return null
            }
            boundaries += segment.inputEnd
            expectedStart = segment.inputEnd
        }
        return boundaries.takeIf { expectedStart == inputLength }
    }
}

@Singleton
class CandidateOrderOverrideRepository @Inject constructor(
    private val dao: CandidateOrderOverrideDao
) {
    private val cacheMutex = Mutex()
    private val overridesByInputCache =
        LinkedHashMap<String, List<CandidateOrderOverrideEntity>>(
            MAX_CACHED_INPUTS,
            0.75f,
            true,
        )

    fun observeAll(): Flow<List<CandidateOrderOverrideEntity>> = dao.observeAll()

    suspend fun saveOrder(
        input: String,
        candidates: List<String>,
        scope: CandidateOrderScope = CandidateOrderScope.EXACT_INPUT,
    ) {
        val normalizedInput = input.trim()
        if (normalizedInput.isEmpty()) return

        val now = System.currentTimeMillis()
        val entities = candidates.mapIndexed { index, candidate ->
            CandidateOrderOverrideEntity(
                input = normalizedInput,
                scope = scope.name,
                candidate = candidate,
                rank = index + 1,
                createdAt = now,
                updatedAt = now
            )
        }
        dao.replaceForRule(normalizedInput, scope.name, entities)
        invalidateInput(normalizedInput)
    }

    suspend fun applyOrder(
        input: String,
        candidates: List<Candidate>,
        scope: CandidateOrderScope = CandidateOrderScope.EXACT_INPUT,
    ): List<Candidate> {
        val normalizedInput = input.trim()
        if (normalizedInput.isEmpty() || candidates.size <= 1) return candidates

        val overrides = loadOverridesForInputs(listOf(normalizedInput))[normalizedInput]
            .orEmpty()
            .filter { it.scope == scope.name }
        if (overrides.isEmpty()) return candidates

        return CandidateOrderOverrideSorter.apply(candidates, overrides)
    }

    suspend fun applyOrderFromSnapshot(
        input: String,
        candidates: List<Candidate>,
        candidateSegmentsByString: Map<String, List<CandidateConversionSegment>> = emptyMap(),
    ): List<Candidate> {
        val normalizedInput = input.trim()
        if (normalizedInput.isEmpty() || candidates.size <= 1) return candidates

        val relevantOverrides = loadOverridesForInputs(normalizedInput.leadingPrefixes())
        if (relevantOverrides.isEmpty()) return candidates

        return CandidateOrderOverrideSorter.applyByConversionSegment(
            input = normalizedInput,
            candidates = candidates,
            overridesByInput = relevantOverrides,
            candidateSegmentsByString = candidateSegmentsByString,
        )
    }

    suspend fun deleteByInput(input: String) {
        val normalizedInput = input.trim()
        if (normalizedInput.isNotEmpty()) {
            dao.deleteByInput(normalizedInput)
            invalidateInput(normalizedInput)
        }
    }

    suspend fun deleteRule(input: String, scope: CandidateOrderScope) {
        val normalizedInput = input.trim()
        if (normalizedInput.isNotEmpty()) {
            dao.deleteByRule(normalizedInput, scope.name)
            invalidateInput(normalizedInput)
        }
    }

    suspend fun deleteAll() {
        dao.deleteAll()
        cacheMutex.withLock { overridesByInputCache.clear() }
    }

    suspend fun deleteById(id: Int) {
        dao.deleteById(id)
        cacheMutex.withLock { overridesByInputCache.clear() }
    }

    private suspend fun loadOverridesForInputs(
        inputs: List<String>,
    ): Map<String, List<CandidateOrderOverrideEntity>> = cacheMutex.withLock {
        val distinctInputs = inputs.distinct()
        val missingInputs = distinctInputs.filterNot(overridesByInputCache::containsKey)
        if (missingInputs.isNotEmpty()) {
            val loadedByInput = dao.findByInputs(missingInputs)
                .groupBy { it.input.trim() }
                .mapValues { (_, entities) -> entities.sortedBy { it.rank } }
            for (input in missingInputs) {
                // Empty entries are cached too, avoiding a query on every conversion.
                overridesByInputCache[input] = loadedByInput[input].orEmpty()
            }
            trimCache()
        }

        buildMap {
            for (input in distinctInputs) {
                overridesByInputCache[input]
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { put(input, it) }
            }
        }
    }

    private suspend fun invalidateInput(input: String) {
        cacheMutex.withLock { overridesByInputCache.remove(input) }
    }

    private fun trimCache() {
        val iterator = overridesByInputCache.entries.iterator()
        while (overridesByInputCache.size > MAX_CACHED_INPUTS && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }

    private fun String.leadingPrefixes(): List<String> = buildList {
        var end = 0
        while (end < this@leadingPrefixes.length) {
            end = this@leadingPrefixes.offsetByCodePoints(end, 1)
            add(this@leadingPrefixes.substring(0, end))
        }
    }

    private companion object {
        const val MAX_CACHED_INPUTS = 256
    }
}
