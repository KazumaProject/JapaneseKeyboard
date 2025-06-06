package com.kazumaproject.markdownhelperkeyboard.learning.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LearnDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: LearnDao

    @Before
    fun setUp() {
        // Create in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        dao = database.learnDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveOutByInput() = runBlocking {
        // Arrange: Insert a new entity
        val learnEntity = LearnEntity(input = "example", out = "output")
        val learnEntity2 = LearnEntity(input = "example", out = "output2")
        val learnEntity3 = LearnEntity(input = "red", out = "apple")
        dao.insert(learnEntity)
        dao.insert(learnEntity2)
        dao.insert(learnEntity3)

        // Act: Retrieve `out` by `input`
        val results = dao.findByInput("example")

        // Assert: Verify the results
        assertEquals(2, results?.size)
        assertEquals("output", results?.get(0))
        assertEquals("output2", results?.get(1))

        println(results)
    }

    @Test
    fun insertMultipleAndRetrieveOuts() = runBlocking {
        // Arrange: Insert multiple entities with different inputs
        val learnEntity1 = LearnEntity(input = "example1", out = "output1")
        val learnEntity2 = LearnEntity(input = "example2", out = "output2")

        dao.insert(learnEntity1)
        dao.insert(learnEntity2)

        // Act: Retrieve `out` by `input`
        val result1 = dao.findByInput("example1")
        val result2 = dao.findByInput("example2")

        // Assert: Verify that each input maps to the correct output
        assertEquals(1, result1?.size)
        assertEquals("output1", result1?.get(0))

        assertEquals(1, result2?.size)
        assertEquals("output2", result2?.get(0))
    }
}
