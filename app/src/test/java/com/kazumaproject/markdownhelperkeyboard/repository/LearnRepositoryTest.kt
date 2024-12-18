package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.repository.LearnRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class LearnRepositoryTest {

    private val mockDao = mock(LearnDao::class.java)
    private val repository = LearnRepository(mockDao)

    @Test
    fun insertLearnedData_callsDaoInsert() = runBlocking {
        val learnData = LearnEntity(input = "example", out = "output")

        repository.insertLearnedData(learnData)

        verify(mockDao).insert(learnData)
    }

    @Test
    fun getLearnedDataByInput_returnsCorrectResults() = runBlocking {
        val input = "example"
        val expectedResults = listOf("output", "output2")

        `when`(mockDao.getByInput(input)).thenReturn(expectedResults)

        val results = repository.getLearnedDataByInput(input)

        println(results)
        assertEquals(expectedResults, results)
    }
}