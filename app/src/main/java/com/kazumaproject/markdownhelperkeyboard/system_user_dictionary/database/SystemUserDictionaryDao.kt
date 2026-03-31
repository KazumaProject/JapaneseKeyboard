package com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SystemUserDictionaryDao {

    @Query("SELECT * FROM system_user_dictionary_entry ORDER BY yomi ASC, tango ASC, id ASC")
    fun getAll(): LiveData<List<SystemUserDictionaryEntry>>

    @Query("SELECT * FROM system_user_dictionary_entry ORDER BY yomi ASC, tango ASC, id ASC")
    suspend fun getAllForBuild(): List<SystemUserDictionaryEntry>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: SystemUserDictionaryEntry)

    @Update
    suspend fun update(entry: SystemUserDictionaryEntry)

    @Query("DELETE FROM system_user_dictionary_entry WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM system_user_dictionary_entry")
    suspend fun deleteAll()
}
