package com.kazumaproject.markdownhelperkeyboard.clicked_symbol.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kazumaproject.core.data.clicked_symbol.SymbolMode


/**
 * クリックされた絵文字・顔文字・記号の履歴を表すテーブル
 */
@Entity(tableName = "clicked_symbol_history")
data class ClickedSymbol(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // どのモード(EMOJI / EMOTICON / SYMBOL)でタップされたか
    val mode: SymbolMode,

    // タップされた文字列
    val symbol: String,

    // タップ時刻(ミリ秒)
    val timestamp: Long = System.currentTimeMillis()
)
