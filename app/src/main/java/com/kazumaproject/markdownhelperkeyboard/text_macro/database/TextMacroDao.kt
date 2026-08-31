package com.kazumaproject.markdownhelperkeyboard.text_macro.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TextMacroDao {
    @Query("SELECT * FROM text_macro ORDER BY name COLLATE NOCASE ASC, id ASC")
    fun observeAll(): Flow<List<TextMacro>>

    @Query(
        """
        SELECT * FROM text_macro
        WHERE name LIKE '%' || :query || '%' ESCAPE '\'
           OR IFNULL(reading, '') LIKE '%' || :query || '%' ESCAPE '\'
           OR body LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY name COLLATE NOCASE ASC, id ASC
        """
    )
    fun search(query: String): Flow<List<TextMacro>>

    @Query("SELECT * FROM text_macro ORDER BY name COLLATE NOCASE ASC, id ASC")
    suspend fun getAll(): List<TextMacro>

    @Query("SELECT * FROM text_macro WHERE enabled = 1 ORDER BY name COLLATE NOCASE ASC, id ASC")
    suspend fun getAllEnabled(): List<TextMacro>

    @Query("SELECT * FROM text_macro WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TextMacro?

    @Query("SELECT * FROM text_macro WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): TextMacro?

    @Query(
        """
        SELECT * FROM text_macro
        WHERE enabled = 1 AND reading = :reading
        ORDER BY name COLLATE NOCASE ASC, id ASC
        LIMIT :limit
        """
    )
    suspend fun getEnabledByReading(reading: String, limit: Int): List<TextMacro>

    @Query(
        """
        SELECT * FROM text_macro
        WHERE enabled = 1 AND body LIKE '%{selection}%'
        ORDER BY name COLLATE NOCASE ASC, id ASC
        LIMIT :limit
        """
    )
    suspend fun getEnabledSelectionMacros(limit: Int): List<TextMacro>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(macro: TextMacro): Long

    @Update
    suspend fun update(macro: TextMacro)

    @Delete
    suspend fun delete(macro: TextMacro)

    @Query("DELETE FROM text_macro WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM text_macro")
    suspend fun deleteAll()

    @Query("UPDATE text_macro SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Transaction
    suspend fun applyByName(macros: List<TextMacro>): TextMacroImportCounts {
        var added = 0
        var overwritten = 0
        macros.forEach { incoming ->
            val existing = getByName(incoming.name)
            if (existing == null) {
                insert(incoming.copy(id = 0))
                added += 1
            } else {
                update(incoming.copy(id = existing.id))
                overwritten += 1
            }
        }
        return TextMacroImportCounts(added = added, overwritten = overwritten)
    }
}

data class TextMacroImportCounts(
    val added: Int,
    val overwritten: Int,
)
