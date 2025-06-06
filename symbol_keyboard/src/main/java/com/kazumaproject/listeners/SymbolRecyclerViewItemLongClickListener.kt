package com.kazumaproject.listeners

import com.kazumaproject.data.clicked_symbol.ClickedSymbol

interface SymbolRecyclerViewItemLongClickListener {
    fun onLongClick(symbol: ClickedSymbol, position: Int)
}
