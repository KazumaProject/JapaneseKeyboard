package com.kazumaproject.markdownhelperkeyboard.converter.mozc.dictionary

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryBinaryReader
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileKey

class AssetsMozcSystemDictionary(
    yomiTrie: LOUDSWithTermId,
    tangoTrie: LOUDS,
    tokenArray: TokenArray,
    yomiLbs: SuccinctBitVector,
    yomiIsLeaf: SuccinctBitVector,
    tokenBitVector: SuccinctBitVector,
    tangoLbs: SuccinctBitVector,
) : AssetsMozcLoudsDictionary(
    source = MozcDictionarySource.SYSTEM,
    yomiTrie = yomiTrie,
    tangoTrie = tangoTrie,
    tokenArray = tokenArray,
    yomiLbs = yomiLbs,
    yomiIsLeaf = yomiIsLeaf,
    tokenBitVector = tokenBitVector,
    tangoLbs = tangoLbs,
)

open class AssetsMozcLoudsDictionary(
    private val source: MozcDictionarySource,
    private val yomiTrie: LOUDSWithTermId,
    private val tangoTrie: LOUDS,
    private val tokenArray: TokenArray,
    private val yomiLbs: SuccinctBitVector,
    private val yomiIsLeaf: SuccinctBitVector,
    private val tokenBitVector: SuccinctBitVector,
    private val tangoLbs: SuccinctBitVector,
) : MozcDictionary {
    override fun lookupPrefix(
        key: String,
        callback: (MozcDictionaryToken) -> Unit,
    ) {
        val prefixes = yomiTrie.commonPrefixSearch(
            str = key,
            succinctBitVector = yomiLbs,
        )
        for (yomi in prefixes) {
            emitTokens(yomi, callback)
        }
    }

    override fun lookupExact(
        key: String,
        callback: (MozcDictionaryToken) -> Unit,
    ) {
        val nodeIndex = yomiTrie.getNodeIndex(key, yomiLbs)
        if (nodeIndex <= 0) return
        emitTokens(key, callback)
    }

    private fun emitTokens(
        yomi: String,
        callback: (MozcDictionaryToken) -> Unit,
    ) {
        val nodeIndex = yomiTrie.getNodeIndex(yomi, yomiLbs)
        if (nodeIndex <= 0) return
        val termId = yomiTrie.getTermId(nodeIndex, yomiIsLeaf)
        val tokens = tokenArray.getListDictionaryByYomiTermId(
            termId,
            tokenBitVector,
        )
        for (token in tokens) {
            val value = when (token.nodeId) {
                -2 -> yomi
                -1 -> yomi.hiraToKata()
                else -> tangoTrie.getLetter(token.nodeId, tangoLbs)
            }
            val posIndex = token.posTableIndex.toInt()
            callback(
                MozcDictionaryToken(
                    key = yomi,
                    value = value,
                    lid = tokenArray.leftIds[posIndex],
                    rid = tokenArray.rightIds[posIndex],
                    cost = token.wordCost.toInt(),
                    source = source,
                )
            )
        }
    }
}

class LazyAssetsMozcLoudsDictionary(
    private val source: MozcDictionarySource,
    private val reader: DictionaryBinaryReader,
    private val yomiKey: DictionaryFileKey,
    private val tangoKey: DictionaryFileKey,
    private val tokenKey: DictionaryFileKey,
) : MozcDictionary {
    private val delegate: MozcDictionary by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val yomiTrie = reader.loadLoudsWithTermId(yomiKey)
        val tangoTrie = reader.loadLouds(tangoKey)
        val tokenArray = reader.loadTokenArray(tokenKey)
        AssetsMozcLoudsDictionary(
            source = source,
            yomiTrie = yomiTrie,
            tangoTrie = tangoTrie,
            tokenArray = tokenArray,
            yomiLbs = SuccinctBitVector(yomiTrie.LBS),
            yomiIsLeaf = SuccinctBitVector(yomiTrie.isLeaf),
            tokenBitVector = SuccinctBitVector(tokenArray.bitvector),
            tangoLbs = SuccinctBitVector(tangoTrie.LBS),
        )
    }

    override fun lookupPrefix(key: String, callback: (MozcDictionaryToken) -> Unit) {
        delegate.lookupPrefix(key, callback)
    }

    override fun lookupExact(key: String, callback: (MozcDictionaryToken) -> Unit) {
        delegate.lookupExact(key, callback)
    }
}
