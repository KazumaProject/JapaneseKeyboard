package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.core.data.clicked_symbol.SymbolMode
import com.kazumaproject.data.clicked_symbol.ClickedSymbol
import com.kazumaproject.markdownhelperkeyboard.clicked_symbol.database.ClickedSymbolDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton


/**
 * com.kazumaproject.data.ClickedSymbolOrigin.ClickedSymbolOrigin の挿入・取得を行うリポジトリ
 */
@Singleton
class ClickedSymbolRepository @Inject constructor(
    private val dao: ClickedSymbolDao
) {
    /**
     * 引数の mode と symbol から com.kazumaproject.data.ClickedSymbolOrigin.ClickedSymbolOrigin を生成して DB に保存する
     */
    suspend fun insert(mode: SymbolMode, symbol: String) = withContext(Dispatchers.IO) {
        val entity = ClickedSymbol(
            mode = mode,
            symbol = symbol
        )
        dao.insert(entity)
    }

    /**
     * すべての com.kazumaproject.data.ClickedSymbolOrigin.ClickedSymbolOrigin を取得する
     * タイムスタンプ降順で返される
     */
    suspend fun getAll(): List<ClickedSymbol> = withContext(Dispatchers.IO) {
        dao.getAll()
    }

    suspend fun delete(mode: SymbolMode, symbol: String) = withContext(Dispatchers.IO) {
        dao.deleteByModeAndSymbol(mode.name, symbol)
    }
}
