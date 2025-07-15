package com.kazumaproject.markdownhelperkeyboard.converter.english.tokenArray

import com.kazumaproject.bitset.rank1Common
import com.kazumaproject.bitset.rank1CommonShort
import com.kazumaproject.bitset.select0Common
import com.kazumaproject.bitset.select0CommonShort
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.english.dictionary.Dictionary
import com.kazumaproject.markdownhelperkeyboard.converter.english.louds.LOUDS
import com.kazumaproject.toBitSet
import timber.log.Timber
import java.io.IOException
import java.io.ObjectInput
import java.io.ObjectOutput
import java.util.BitSet

class TokenArray {
    private var wordCostList: ShortArray = shortArrayOf()
    private var nodeIdList: IntArray = intArrayOf()
    private val wordCostListTemp: MutableList<Short> = arrayListOf()
    private val nodeIdListTemp: MutableList<Int> = arrayListOf()
    private var bitListTemp: MutableList<Boolean> = arrayListOf()
    var bitvector: BitSet = BitSet()

    fun getNodeIds(): IntArray {
        return nodeIdList
    }

    fun getListDictionaryByYomiTermId(
        nodeId: Int, rank0ArrayTokenArrayBitvector: IntArray, rank1ArrayTokenArrayBitvector: IntArray
    ): List<TokenEntry> {
        val startRank = bitvector.rank1Common(
            bitvector.select0Common(nodeId, rank0ArrayTokenArrayBitvector), rank1ArrayTokenArrayBitvector
        )
        val endRank = bitvector.rank1Common(
            bitvector.select0Common(
                nodeId + 1, rank0ArrayTokenArrayBitvector
            ), rank1ArrayTokenArrayBitvector
        )

        val tempList2 = mutableListOf<TokenEntry>().apply {
            for (i in startRank until endRank) {
                add(
                    TokenEntry(
                        wordCost = wordCostList[i], nodeId = nodeIdList[i]
                    )
                )
            }
        }
        return tempList2
    }

    fun buildTokenArray(
        dictionaries: List<Dictionary>,
        wordTrie: LOUDS,
        out: ObjectOutput,
    ) {
        for ((index, dictionary) in dictionaries.withIndex()) {
            bitListTemp.add(false)
            bitListTemp.add(true)
            wordCostListTemp.add(dictionary.cost)
            val nodeId = if (dictionary.withUpperCase) wordTrie.getNodeIndex(dictionary.word) else -1
            if (index % 5000 == 0) {
                println("build token array: $index  ${dictionary.word} $nodeId")
            }
            nodeIdListTemp.add(nodeId)
        }
        writeExternalNotCompress(out)
    }

    fun getListDictionaryByYomiTermId(
        nodeId: Int, succinctBitVector: SuccinctBitVector
    ): List<TokenEntry> {
        val startSelect0 = succinctBitVector.select0(nodeId)
        val endSelect0 = succinctBitVector.select0(nodeId + 1)
        val startRank1 = succinctBitVector.rank1(startSelect0)
        val endRank1 = succinctBitVector.rank1(endSelect0)
        Timber.d("predictiveSearchReading: $startRank1 $endRank1")
        val tempList2 = mutableListOf<TokenEntry>().apply {
            for (i in startRank1 until endRank1) {
                add(
                    TokenEntry(
                        wordCost = wordCostList[i], nodeId = nodeIdList[i]
                    )
                )
            }
        }
        return tempList2
    }

    fun getListDictionaryByYomiTermIdShortArray(
        nodeId: Short,
        rank0ArrayTokenArrayBitvector: ShortArray,
        rank1ArrayTokenArrayBitvector: ShortArray,
    ): List<TokenEntry> {
        val b = bitvector.rank1CommonShort(
            bitvector.select0CommonShort(
                nodeId, rank0ArrayTokenArrayBitvector
            ).toInt(), rank1ArrayTokenArrayBitvector
        )
        val c = bitvector.rank1CommonShort(
            bitvector.select0CommonShort(
                (nodeId + 1).toShort(), rank0ArrayTokenArrayBitvector
            ).toInt(), rank1ArrayTokenArrayBitvector
        )
        val tempList2 = mutableListOf<TokenEntry>()
        for (i in b until c) {
            tempList2.add(
                TokenEntry(
                    wordCost = wordCostList[i],
                    nodeId = nodeIdList[i],
                )
            )
        }
        return tempList2
    }

    fun getListDictionaryByYomiTermIdShortArray(
        nodeId: Short, succinctBitVector: SuccinctBitVector
    ): List<TokenEntry> {
        val startSelect0 = succinctBitVector.select0(nodeId.toInt())
        val startRank1 = succinctBitVector.rank1(startSelect0)
        val endSelect0 = succinctBitVector.select0(nodeId + 1)
        val endRank1 = succinctBitVector.rank1(endSelect0)
        val tempList2 = mutableListOf<TokenEntry>()
        for (i in startRank1 until endRank1) {
            tempList2.add(
                TokenEntry(
                    wordCost = wordCostList[i],
                    nodeId = nodeIdList[i],
                )
            )
        }
        return tempList2
    }

    private fun writeExternalNotCompress(
        out: ObjectOutput
    ) {
        try {
            out.apply {
                writeObject(wordCostListTemp.toShortArray())
                writeObject(nodeIdListTemp.toIntArray())
                writeObject(bitListTemp.toBitSet())
                flush()
                close()
            }
        } catch (e: IOException) {
            println(e.stackTraceToString())
        }
    }

    /**
     * 読み込んで自分自身を返すように修正。
     * 呼び出し側はこのインスタンスを受け取って使う。
     */
    fun readExternal(objectInput: ObjectInput): TokenArray {
        objectInput.apply {
            try {
                wordCostList = readObject() as ShortArray
                nodeIdList = readObject() as IntArray
                bitvector   = readObject() as BitSet
            } finally {
                try { close() } catch (_: Exception) { }
            }
        }
        return this
    }

}
