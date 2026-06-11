package com.kazumaproject.markdownhelperkeyboard.candidate_order

import com.kazumaproject.markdownhelperkeyboard.candidate_order.database.CandidateOrderOverrideDao
import com.kazumaproject.markdownhelperkeyboard.candidate_order.database.CandidateOrderOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.candidate_order.model.SavedCandidateOrderGroup
import com.kazumaproject.markdownhelperkeyboard.candidate_order.ui.toCandidateOrderEditingState
import com.kazumaproject.markdownhelperkeyboard.repository.CandidateOrderOverrideRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

        assertEquals(listOf("きょう"), dao.deletedInputs)
        assertEquals(listOf("今日", "京"), dao.rowsForInput("きょう").map { it.candidate })
        assertEquals(listOf(1, 2), dao.rowsForInput("きょう").map { it.rank })
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

    private fun entity(
        input: String,
        candidate: String,
        rank: Int
    ): CandidateOrderOverrideEntity {
        return CandidateOrderOverrideEntity(
            input = input,
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

    private class FakeCandidateOrderOverrideDao(
        initialRows: MutableList<CandidateOrderOverrideEntity>
    ) : CandidateOrderOverrideDao {
        private val rows = initialRows
        private val rowsFlow = MutableStateFlow(rows.toList())
        val deletedInputs = mutableListOf<String>()

        fun rowsForInput(input: String): List<CandidateOrderOverrideEntity> {
            return rows.filter { it.input == input }.sortedBy { it.rank }
        }

        override suspend fun findByInput(input: String): List<CandidateOrderOverrideEntity> {
            return rowsForInput(input)
        }

        override fun observeAll(): Flow<List<CandidateOrderOverrideEntity>> {
            return rowsFlow
        }

        override suspend fun getAll(): List<CandidateOrderOverrideEntity> {
            return rows.sortedWith(compareBy<CandidateOrderOverrideEntity> { it.input }.thenBy { it.rank })
        }

        override suspend fun deleteByInput(input: String) {
            deletedInputs += input
            rows.removeAll { it.input == input }
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
