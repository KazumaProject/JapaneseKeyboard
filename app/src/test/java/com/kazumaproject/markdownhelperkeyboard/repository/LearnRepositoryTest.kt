package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.repository.LearnRepository
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

class LearnRepositoryTest {

    private val mockDao = mock(LearnDao::class.java)
    private lateinit var repository: LearnRepository

    @Before
    fun setUp() {
        repository = LearnRepository(mockDao)
    }

    @Test
    fun insertLearnedData_callsDaoInsert() = runBlocking {
        val learnData = LearnEntity(input = "example", out = "output")

        `when`(mockDao.getByInput("example")).thenReturn(listOf("output"))

        repository.insertLearnedData(learnData)

        verify(mockDao).insert(learnData)

        val results = repository.getLearnedDataByInput("example")
        println(results)
    }

    @Test
    fun insertLearnedData_callsDaoInsert_Multiple() = runBlocking {
        val learnData1 = LearnEntity(input = "example", out = "output")
        val learnData2 = LearnEntity(input = "example", out = "output2")
        val learnData3 = LearnEntity(input = "example", out = "output3")

        val learnedDataList = mutableListOf<String>()

        // Mock the insert function to add data to the list
        doAnswer {
            val insertedData = it.getArgument<LearnEntity>(0)
            learnedDataList.add(insertedData.out)
            null // insert method is void
        }.`when`(mockDao).insert(any())

        // Mock the getByInput function to return the accumulated list
        `when`(mockDao.getByInput("example")).thenReturn(learnedDataList)

        repository.apply {
            insertLearnedData(learnData1)
            insertLearnedData(learnData2)
            insertLearnedData(learnData3)
        }

        // Verify that insert was called with the expected data
        verify(mockDao).insert(learnData1)
        verify(mockDao).insert(learnData2)
        verify(mockDao).insert(learnData3)

        // Fetch the data and print results
        val results = repository.getLearnedDataByInput("example")
        println(results) // Should print [output, output2, output3]
    }

}