package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWordDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ConversionDictionarySnapshotTest {

    @Test
    fun userDictionary_loadsOnce_filtersPrefixes_andInvalidatesAfterWrite() = runTest {
        val dao = mock<UserWordDao>()
        val short = userWord(1, "私", "わたし")
        val long = userWord(2, "私の", "わたしの")
        whenever(dao.getAllSuspend()).thenReturn(listOf(short, long))
        val repository = UserDictionaryRepository(dao)

        assertEquals(listOf(long, short), repository.commonPrefixSearchInUserDict("わたしのなまえ"))
        assertEquals(listOf(short), repository.commonPrefixSearchInUserDict("わたしだけ"))
        verify(dao, times(1)).getAllSuspend()

        repository.insert(userWord(3, "名前", "なまえ"))
        repository.commonPrefixSearchInUserDict("わたし")
        verify(dao, times(2)).getAllSuspend()
    }

    @Test
    fun learnDictionary_loadsOnce_filtersPrefixes_andInvalidatesAfterWrite() = runTest {
        val dao = mock<LearnDao>()
        val short = LearnEntity(input = "なか", out = "中")
        val long = LearnEntity(input = "なかの", out = "中野")
        whenever(dao.getAllSuspend()).thenReturn(listOf(short, long))
        val repository = LearnRepository(dao)

        assertEquals(listOf(long, short), repository.findCommonPrefixes("なかのかも"))
        assertEquals(listOf(short), repository.findCommonPrefixes("なかだけ"))
        verify(dao, times(1)).getAllSuspend()

        repository.deleteAll()
        repository.findCommonPrefixes("なか")
        verify(dao, times(2)).getAllSuspend()
    }

    private fun userWord(id: Int, word: String, reading: String) = UserWord(
        id = id,
        word = word,
        reading = reading,
        posIndex = 0,
        posScore = 100,
    )
}
