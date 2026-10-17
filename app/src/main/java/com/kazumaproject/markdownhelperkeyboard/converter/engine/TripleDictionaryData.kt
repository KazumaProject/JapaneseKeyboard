package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector

data class TripleDictionaryData(
    val tangoTrie: LOUDS,
    val yomiTrie: LOUDSWithTermId,
    val tokenArray: TokenArray,
    val succinctBitVectorLBSYomi: SuccinctBitVector,
    val succinctBitVectorIsLeafYomi: SuccinctBitVector,
    val succinctBitVectorTokenArray: SuccinctBitVector,
    val succinctBitVectorTangoLBS: SuccinctBitVector,
) {
    fun surfaceFor(nodeId: Int, reading: String): String = when (nodeId) {
        -2 -> reading
        -1 -> reading.hiraToKata()
        else -> tangoTrie.getLetter(nodeId, succinctBitVectorTangoLBS)
    }
}
