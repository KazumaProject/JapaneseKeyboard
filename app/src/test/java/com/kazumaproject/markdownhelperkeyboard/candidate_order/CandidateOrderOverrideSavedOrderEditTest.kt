package com.kazumaproject.markdownhelperkeyboard.candidate_order

import com.kazumaproject.markdownhelperkeyboard.candidate_order.database.CandidateOrderOverrideDao
import com.kazumaproject.markdownhelperkeyboard.candidate_order.database.CandidateOrderOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.candidate_order.model.CandidateOrderScope
import com.kazumaproject.markdownhelperkeyboard.candidate_order.model.SavedCandidateOrderGroup
import com.kazumaproject.markdownhelperkeyboard.candidate_order.ui.toCandidateOrderEditingState
import com.kazumaproject.markdownhelperkeyboard.repository.CandidateOrderOverrideRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CandidateOrderOverrideSavedOrderEditTest {

    @Test
    fun savedOrderRestoresInputAndCandidateOrderForEditing() {
        val savedOrder = SavedCandidateOrderGroup(
            input = " きょう ",
            candidates = listOf("今日", "京", "教"),
            updatedAt = 1L
        )

        val editingState = savedOrder.toCandidateOrderEditingState()

        assertEquals("きょう", editingState?.reading)
        assertEquals(CandidateOrderScope.EXACT_INPUT, editingState?.scope)
        assertEquals(listOf("今日", "京", "教"), editingState?.candidates?.map { it.candidate })
        assertEquals(listOf(0, 1, 2), editingState?.candidates?.map { it.originalIndex })
    }

    @Test
    fun blankInputSavedOrderDoesNotRestoreEditingState() {
        val savedOrder = SavedCandidateOrderGroup(
            input = "   ",
            candidates = listOf("今日"),
            updatedAt = 1L
        )

        assertNull(savedOrder.toCandidateOrderEditingState())
    }

    @Test
    fun emptyCandidateSavedOrderDoesNotRestoreEditingState() {
        val savedOrder = SavedCandidateOrderGroup(
            input = "きょう",
            candidates = emptyList(),
            updatedAt = 1L
        )

        assertNull(savedOrder.toCandidateOrderEditingState())
    }

    @Test
    fun saveOrderReplacesExistingRowsForSameInput() = runTest {
        val dao = FakeCandidateOrderOverrideDao(
            initialRows = mutableListOf(
                entity(input = "きょう", candidate = "旧", rank = 1),
                entity(input = "あす", candidate = "明日", rank = 1)
            )
        )
        val repository = CandidateOrderOverrideRepository(dao)

        repository.saveOrder(
            input = " きょう ",
            candidates = listOf("今日", "京")
        )

        assertEquals(
            listOf("きょう" to CandidateOrderScope.EXACT_INPUT.name),
            dao.deletedRules,
        )
        assertEquals(
            listOf("今日", "京"),
            dao.rowsForRule("きょう", CandidateOrderScope.EXACT_INPUT).map { it.candidate },
        )
        assertEquals(
            listOf(1, 2),
            dao.rowsForRule("きょう", CandidateOrderScope.EXACT_INPUT).map { it.rank },
        )
        assertEquals(listOf("明日"), dao.rowsForInput("あす").map { it.candidate })
    }

    @Test
    fun snapshotOrderMatchesDaoOrderAndKeepsUnrankedCandidatesStable() = runTest {
        val dao = FakeCandidateOrderOverrideDao(
            initialRows = mutableListOf(
                entity(input = "きょう", candidate = "京", rank = 1),
                entity(input = "きょう", candidate = "今日", rank = 2)
            )
        )
        val repository = CandidateOrderOverrideRepository(dao)
        val candidates = listOf(
            candidate("今日"),
            candidate("明日"),
            candidate("京"),
            candidate("教")
        )

        val orderedByDao = repository.applyOrder(" きょう ", candidates)
        val orderedBySnapshot = repository.applyOrderFromSnapshot(" きょう ", candidates)

        assertEquals(orderedByDao, orderedBySnapshot)
        assertEquals(listOf("京", "今日", "明日", "教"), orderedBySnapshot.map { it.string })
    }

    @Test
    fun snapshotOrderReflectsDaoUpdates() = runTest {
        val dao = FakeCandidateOrderOverrideDao(
            initialRows = mutableListOf(
                entity(input = "きょう", candidate = "今日", rank = 1)
            )
        )
        val repository = CandidateOrderOverrideRepository(dao)
        val candidates = listOf(candidate("今日"), candidate("京"))

        assertEquals(
            listOf("今日", "京"),
            repository.applyOrderFromSnapshot("きょう", candidates).map { it.string }
        )

        repository.saveOrder("きょう", listOf("京", "今日"))

        assertEquals(
            listOf("京", "今日"),
            repository.applyOrderFromSnapshot("きょう", candidates).map { it.string }
        )
    }

    @Test
    fun segmentOrderQueriesOnlyInputPrefixesAndReusesBoundedLookup() = runTest {
        val dao = FakeCandidateOrderOverrideDao(
            initialRows = mutableListOf(
                entity(
                    input = "ひ",
                    candidate = "火",
                    rank = 1,
                    scope = CandidateOrderScope.LEXICAL_UNIT,
                ),
                entity(
                    input = "ひ",
                    candidate = "日",
                    rank = 2,
                    scope = CandidateOrderScope.LEXICAL_UNIT,
                ),
                entity(input = "べんちまっぷ00001", candidate = "候補", rank = 1),
            ),
        )
        val repository = CandidateOrderOverrideRepository(dao)
        val candidates = listOf(candidate("日を"), candidate("火を"))
        val segments = mapOf(
            "日を" to listOf(
                candidateSegment(0, 1, "日"),
                candidateSegment(1, 2, "を"),
            ),
            "火を" to listOf(
                candidateSegment(0, 1, "火"),
                candidateSegment(1, 2, "を"),
            ),
        )

        repeat(2) {
            assertEquals(
                listOf("火を", "日を"),
                repository.applyOrderFromSnapshot("ひを", candidates, segments).map { it.string },
            )
        }

        assertEquals(1, dao.findByInputsCalls.size)
        assertEquals(setOf("ひ", "ひを"), dao.findByInputsCalls.single().toSet())
        assertFalse(dao.findByInputsCalls.single().contains("べんちまっぷ00001"))
        assertEquals(0, dao.getAllCallCount)
    }

    private fun entity(
        input: String,
        candidate: String,
        rank: Int,
        scope: CandidateOrderScope = CandidateOrderScope.EXACT_INPUT,
    ): CandidateOrderOverrideEntity {
        return CandidateOrderOverrideEntity(
            input = input,
            scope = scope.name,
            candidate = candidate,
            rank = rank,
            createdAt = 1L,
            updatedAt = 1L
        )
    }

    private fun candidate(string: String) =
        com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate(
            string = string,
            type = 1.toByte(),
            length = string.length.toUByte(),
            score = 0
        )

    private fun candidateSegment(inputStart: Int, inputEnd: Int, output: String) =
        com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment(
            inputStart = inputStart,
            inputEnd = inputEnd,
            output = output,
        )

    private class FakeCandidateOrderOverrideDao(
        initialRows: MutableList<CandidateOrderOverrideEntity>
    ) : CandidateOrderOverrideDao {
        private val rows = initialRows
        private val rowsFlow = MutableStateFlow(rows.toList())
        val deletedInputs = mutableListOf<String>()
        val deletedRules = mutableListOf<Pair<String, String>>()
        val findByInputsCalls = mutableListOf<List<String>>()
        var getAllCallCount = 0

        fun rowsForInput(input: String): List<CandidateOrderOverrideEntity> {
            return rows.filter { it.input == input }.sortedBy { it.rank }
        }

        fun rowsForRule(
            input: String,
            scope: CandidateOrderScope,
        ): List<CandidateOrderOverrideEntity> {
            return rows
                .filter { it.input == input && it.scope == scope.name }
                .sortedBy { it.rank }
        }

        override suspend fun findByRule(
            input: String,
            scope: String,
        ): List<CandidateOrderOverrideEntity> {
            return rows.filter { it.input == input && it.scope == scope }.sortedBy { it.rank }
        }

        override suspend fun findByInputs(
            inputs: List<String>,
        ): List<CandidateOrderOverrideEntity> {
            findByInputsCalls += inputs.toList()
            return rows
                .filter { it.input in inputs }
                .sortedWith(
                    compareBy<CandidateOrderOverrideEntity> { it.input }
                        .thenBy { it.scope }
                        .thenBy { it.rank },
                )
        }

        override fun observeAll(): Flow<List<CandidateOrderOverrideEntity>> {
            return rowsFlow
        }

        override suspend fun getAll(): List<CandidateOrderOverrideEntity> {
            getAllCallCount++
            return rows.sortedWith(
                compareBy<CandidateOrderOverrideEntity> { it.input }
                    .thenBy { it.scope }
                    .thenBy { it.rank },
            )
        }

        override suspend fun deleteByInput(input: String) {
            deletedInputs += input
            rows.removeAll { it.input == input }
            rowsFlow.value = rows.toList()
        }

        override suspend fun deleteByRule(input: String, scope: String) {
            deletedRules += input to scope
            rows.removeAll { it.input == input && it.scope == scope }
            rowsFlow.value = rows.toList()
        }

        override suspend fun deleteAll() {
            rows.clear()
            rowsFlow.value = emptyList()
        }

        override suspend fun insertAll(entities: List<CandidateOrderOverrideEntity>) {
            rows += entities
            rowsFlow.value = rows.toList()
        }

        override suspend fun deleteById(id: Int) {
            rows.removeAll { it.id == id }
            rowsFlow.value = rows.toList()
        }
    }
}
