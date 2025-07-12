package com.kazumaproject.markdownhelperkeyboard.ng_word.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NgWordDao {
    @Query("SELECT * FROM ng_word ORDER BY id DESC")
    suspend fun getAll(): List<NgWord>

    @Query("SELECT * FROM ng_word ORDER BY id DESC")
    fun getAllFlow(): Flow<List<NgWord>>

    @Query("SELECT * FROM ng_word WHERE yomi = :yomi AND tango = :tango LIMIT 1")
    suspend fun find(yomi: String, tango: String): NgWord?

    @Query("SELECT * FROM ng_word WHERE yomi = :yomi ORDER BY id DESC")
    suspend fun getByYomi(yomi: String): List<NgWord>

    @Query(
        """
        SELECT * 
        FROM ng_word 
        WHERE :searchYomi LIKE yomi || '%' 
        ORDER BY LENGTH(yomi) DESC
    """
    )
    suspend fun findCommonPrefixes(searchYomi: String): List<NgWord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NgWord): Long

    @Delete
    suspend fun delete(entity: NgWord)

    @Query("DELETE FROM ng_word")
    suspend fun deleteAll()
}
