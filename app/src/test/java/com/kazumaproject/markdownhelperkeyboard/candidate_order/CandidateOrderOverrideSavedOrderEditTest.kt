package com.kazumaproject.markdownhelperkeyboard.candidate_order

import com.kazumaproject.markdownhelperkeyboard.candidate_order.database.CandidateOrderOverrideDao
import com.kazumaproject.markdownhelperkeyboard.candidate_order.database.CandidateOrderOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.candidate_order.model.SavedCandidateOrderGroup
import com.kazumaproject.markdownhelperkeyboard.candidate_order.ui.toCandidateOrderEditingState
import com.kazumaproject.markdownhelperkeyboard.repository.CandidateOrderOverrideRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

    private class FakeCandidateOrderOverrideDao(
        initialRows: MutableList<CandidateOrderOverrideEntity>
    ) : CandidateOrderOverrideDao {
        private val rows = initialRows
        val deletedInputs = mutableListOf<String>()

        fun rowsForInput(input: String): List<CandidateOrderOverrideEntity> {
            return rows.filter { it.input == input }.sortedBy { it.rank }
        }

        override suspend fun findByInput(input: String): List<CandidateOrderOverrideEntity> {
            return rowsForInput(input)
        }

        override fun observeAll(): Flow<List<CandidateOrderOverrideEntity>> {
            return flowOf(rows)
        }

        override suspend fun deleteByInput(input: String) {
            deletedInputs += input
            rows.removeAll { it.input == input }
        }

        override suspend fun deleteAll() {
            rows.clear()
        }

        override suspend fun insertAll(entities: List<CandidateOrderOverrideEntity>) {
            rows += entities
        }

        override suspend fun deleteById(id: Int) {
            rows.removeAll { it.id == id }
        }
    }
}
