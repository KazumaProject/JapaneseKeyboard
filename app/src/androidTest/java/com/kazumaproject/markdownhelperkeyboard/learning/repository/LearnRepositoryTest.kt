package com.kazumaproject.markdownhelperkeyboard.learning.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDatabase
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LearnRepositoryTest {

    private lateinit var database: LearnDatabase
    private lateinit var learnDao: LearnDao
    private lateinit var repository: LearnRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, LearnDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        learnDao = database.learnDao()
        repository = LearnRepository(learnDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertLearnedData_andRetrieve() = runBlocking {
        val learnData = LearnEntity(input = "example", out = "output")
        repository.insertLearnedData(learnData)

        val result = repository.findLearnDataByInputAndOutput("example", "output")
        assertEquals(learnData.copy(learnData.input, learnData.out, 1), result)
    }

    @Test
    fun insertLearnedData_multipleEntries() = runBlocking {
        val learnData1 = LearnEntity(input = "example", out = "output1")
        val learnData2 = LearnEntity(input = "example", out = "output2")
        val learnData3 = LearnEntity(input = "example", out = "output3")
        val learnData4 = LearnEntity(input = "example", out = "output4")

        repository.apply {
            insertLearnedData(learnData1)
            insertLearnedData(learnData2)
            insertLearnedData(learnData3)
            insertLearnedData(learnData4)
        }

        val results = repository.findLearnDataByInput("example")
        assertEquals(listOf("output1", "output2", "output3", "output4"), results)
    }

    @Test
    fun upsertLearnedData_callsInsertOrUpdateCorrectly() = runBlocking {
        val learnData = LearnEntity(input = "example", out = "output", id = 1)

        println("${repository.findLearnDataByInputAndOutput(learnData.input, learnData.out)}")

        repository.upsertLearnedData(learnData)

        // Check insert
        val result1 = repository.findLearnDataByInputAndOutput("example", "output")
        assertEquals(learnData, result1)
        println("result1: $result1")

        // Update data
        val updatedData = LearnEntity(input = "example", out = "output2", id = 2)
        repository.upsertLearnedData(updatedData)

        // Verify update
        val result2 = repository.findLearnDataByInputAndOutput("example", "output2")
        assertEquals(updatedData, result2)
        println("result2: $result2")
    }

    @Test
    fun all_returnsInitialEntities() = runBlocking {
        // Prepare test data
        val learnData1 = LearnEntity(input = "example1", out = "output1", id = 1)
        val learnData2 = LearnEntity(input = "example2", out = "output2", id = 2)
        val learnData3 = LearnEntity(input = "example3", out = "output3", id = 3)

        repository.upsertLearnedData(learnData1)
        repository.upsertLearnedData(learnData2)
        repository.upsertLearnedData(learnData3)

        repository.delete(learnData2)

        // Collect only the first emission
        val result = repository.all().first()
        assertEquals(2, result.size)
        assertTrue(result.contains(learnData1))
        assertTrue(result.contains(learnData3))
        println("First emission: $result")
    }

}