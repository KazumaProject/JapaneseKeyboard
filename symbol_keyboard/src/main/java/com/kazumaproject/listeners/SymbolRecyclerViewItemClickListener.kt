package com.kazumaproject.listeners

import com.kazumaproject.data.clicked_symbol.ClickedSymbol

interface SymbolRecyclerViewItemClickListener {
    fun onClick(symbol: ClickedSymbol)
}
