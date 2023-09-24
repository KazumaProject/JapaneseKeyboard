package com.kazumaproject.markdownhelperkeyboard.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kazumaproject.markdownhelperkeyboard.db.models.EnglishWord
import kotlinx.coroutines.flow.Flow

@Dao
interface EnglishWordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWordFromDictionary(word: EnglishWord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWordsFromDictionary(words: Iterable<EnglishWord>)

    @Query("DELETE FROM dictionary_english_word_table")
    suspend fun deleteAllDictionaryWords()

    @Query("SELECT * FROM dictionary_english_word_table ORDER BY id DESC")
    fun getAllDictionaryWords(): Flow<List<EnglishWord>>
}