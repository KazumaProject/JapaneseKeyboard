package com.kazumaproject.markdownhelperkeyboard.clicked_symbol.database

import androidx.room.TypeConverter
import com.kazumaproject.core.data.clicked_symbol.SymbolMode


class Converters {
    @TypeConverter
    fun fromSymbolMode(mode: SymbolMode): String = mode.name

    @TypeConverter
    fun toSymbolMode(value: String): SymbolMode =
        SymbolMode.valueOf(value)
}
