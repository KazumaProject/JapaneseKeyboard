package com.kazumaproject.markdownhelperkeyboard.clicked_symbol.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kazumaproject.data.clicked_symbol.ClickedSymbol

@Dao
interface ClickedSymbolDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(clickedSymbol: ClickedSymbol)

    @Query("SELECT * FROM clicked_symbol_history ORDER BY timestamp DESC")
    suspend fun getAll(): List<ClickedSymbol>

    @Query("DELETE FROM clicked_symbol_history WHERE mode = :mode AND symbol = :symbol")
    suspend fun deleteByModeAndSymbol(mode: String, symbol: String)
}
