package com.kazumaproject.markdownhelperkeyboard.zeroquery.custom

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomZeroQueryDao {

    @Query(
        """
        SELECT * FROM custom_zero_query_entries
        ORDER BY displayKey COLLATE NOCASE ASC, rank ASC, id ASC
        """
    )
    fun observeAll(): Flow<List<CustomZeroQueryEntry>>

    @Query(
        """
        SELECT * FROM custom_zero_query_entries
        WHERE lookupKey = :lookupKey
        ORDER BY rank ASC, id ASC
        """
    )
    fun observeByLookupKey(lookupKey: String): Flow<List<CustomZeroQueryEntry>>

    @Query(
        """
        SELECT * FROM custom_zero_query_entries
        WHERE lookupKey = :lookupKey AND enabled = 1
        ORDER BY rank ASC, id ASC
        """
    )
    suspend fun lookupEnabled(lookupKey: String): List<CustomZeroQueryEntry>

    @Query(
        """
        SELECT * FROM custom_zero_query_entries
        WHERE lookupKey = :lookupKey
        ORDER BY rank ASC, id ASC
        """
    )
    suspend fun getByLookupKey(lookupKey: String): List<CustomZeroQueryEntry>

    @Query(
        """
        SELECT * FROM custom_zero_query_entries
        ORDER BY displayKey COLLATE NOCASE ASC, rank ASC, id ASC
        """
    )
    suspend fun getAll(): List<CustomZeroQueryEntry>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM custom_zero_query_entries
            WHERE lookupKey = :lookupKey
              AND candidate = :candidate
              AND id != :excludeId
        )
        """
    )
    suspend fun existsDuplicate(
        lookupKey: String,
        candidate: String,
        excludeId: Long = 0,
    ): Boolean

    @Query(
        """
        SELECT * FROM custom_zero_query_entries
        WHERE lookupKey = :lookupKey AND candidate = :candidate
        LIMIT 1
        """
    )
    suspend fun findByLookupKeyAndCandidate(
        lookupKey: String,
        candidate: String,
    ): CustomZeroQueryEntry?

    @Query(
        """
        SELECT COALESCE(MAX(rank), 0) FROM custom_zero_query_entries
        WHERE lookupKey = :lookupKey
        """
    )
    suspend fun maxRankForLookupKey(lookupKey: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: CustomZeroQueryEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplacing(entries: List<CustomZeroQueryEntry>)

    @Update
    suspend fun update(entry: CustomZeroQueryEntry)

    @Query("DELETE FROM custom_zero_query_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM custom_zero_query_entries WHERE lookupKey = :lookupKey")
    suspend fun deleteByLookupKey(lookupKey: String)

    @Query("DELETE FROM custom_zero_query_entries")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(entries: List<CustomZeroQueryEntry>) {
        deleteAll()
        if (entries.isNotEmpty()) {
            insertReplacing(entries)
        }
    }
}
