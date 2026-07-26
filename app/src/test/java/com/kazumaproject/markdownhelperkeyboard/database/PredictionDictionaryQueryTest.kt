package com.kazumaproject.markdownhelperkeyboard.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PredictionDictionaryQueryTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun userDictionaryAppliesLimitAfterOrderingByConversionCost() = runBlocking {
        database.userWordDao().insertAll(
            listOf(
                UserWord(word = "low", reading = "かなa", posIndex = 0, posScore = 100),
                UserWord(word = "high", reading = "かなb", posIndex = 0, posScore = 900),
                UserWord(word = "middle", reading = "かなc", posIndex = 0, posScore = 300),
            )
        )

        val result = database.userWordDao().searchByReadingPrefixSuspend("かな", limit = 2)

        assertEquals(listOf("low", "middle"), result.map { it.word })
    }

    @Test
    fun learningDictionaryTreatsLowerScoresAsHigherPriorityBeforeLimit() = runBlocking {
        database.learnDao().insertAll(
            listOf(
                LearnEntity(input = "かなa", out = "low", score = 100),
                LearnEntity(input = "かなb", out = "high", score = 900),
                LearnEntity(input = "かなc", out = "middle", score = 300),
            )
        )

        val result = database.learnDao().predictiveSearchByInput(
            prefix = "かな",
            prefixUpperBound = "かな\uFFFF",
            limit = 2,
        )

        assertEquals(listOf("low", "middle"), result.map { it.out })
    }
}
