package com.kazumaproject.markdownhelperkeyboard.clicked_symbol.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kazumaproject.data.clicked_symbol.ClickedSymbol

@Dao
interface ClickedSymbolDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(clickedSymbol: ClickedSymbol)

    @Transaction
    suspend fun replaceHistoryEntry(clickedSymbol: ClickedSymbol) {
        deleteByModeAndSymbol(clickedSymbol.mode.name, clickedSymbol.symbol)
        insert(clickedSymbol)
    }

    @Query("SELECT * FROM clicked_symbol_history ORDER BY timestamp DESC, id DESC")
    suspend fun getAll(): List<ClickedSymbol>

    @Query("DELETE FROM clicked_symbol_history WHERE mode = :mode AND symbol = :symbol")
    suspend fun deleteByModeAndSymbol(mode: String, symbol: String)
}
