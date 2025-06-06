package com.kazumaproject.markdownhelperkeyboard.clicked_symbol.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ClickedSymbolDao {
    @Insert
    suspend fun insert(clickedSymbol: ClickedSymbol)

    @Query("SELECT * FROM clicked_symbol_history ORDER BY timestamp DESC")
    suspend fun getAll(): List<ClickedSymbol>
}
