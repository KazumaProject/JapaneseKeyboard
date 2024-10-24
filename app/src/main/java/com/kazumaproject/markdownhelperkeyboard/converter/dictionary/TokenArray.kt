package com.kazumaproject.dictionary

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.bitset.rank1Common
import com.kazumaproject.bitset.rank1CommonShort
import com.kazumaproject.bitset.select0Common
import com.kazumaproject.bitset.select0CommonShort
import com.kazumaproject.dictionary.models.Dictionary
import com.kazumaproject.dictionary.models.TokenEntry
import com.kazumaproject.hiraToKata
import com.kazumaproject.toBitSet
import com.kazumaproject.toByteArray
import com.kazumaproject.toByteArrayFromListShort
import java.io.IOException
import java.io.ObjectInput
import java.io.ObjectInputStream
import java.io.ObjectOutput
import java.io.ObjectOutputStream
import java.util.BitSet

class TokenArray {
    private var posTableIndexList: ShortArray = shortArrayOf()
    private var wordCostList: ShortArray = shortArrayOf()
    private var nodeIdList: IntArray = intArrayOf()
    private val posTableIndexListTemp: MutableList<Short> = arrayListOf()
    private val wordCostListTemp: MutableList<Short> = arrayListOf()
    private val nodeIdListTemp: MutableList<Int> = arrayListOf()
    private var bitListTemp: MutableList<Boolean> = arrayListOf()
    var bitvector: BitSet = BitSet()
    var leftIds: List<Short> = listOf()
    var rightIds: List<Short> = listOf()

    fun getNodeIds(): IntArray{
        return nodeIdList
    }

    fun getListDictionaryByYomiTermId(
        nodeId: Int,
        rank0ArrayTokenArrayBitvector: IntArray,
        rank1ArrayTokenArrayBitvector: IntArray
    ): List<TokenEntry> {
        val startRank = bitvector.rank1Common(bitvector.select0Common(nodeId, rank0ArrayTokenArrayBitvector), rank1ArrayTokenArrayBitvector)
        val endRank = bitvector.rank1Common(bitvector.select0Common(nodeId + 1, rank0ArrayTokenArrayBitvector), rank1ArrayTokenArrayBitvector)

        val tempList2 = mutableListOf<TokenEntry>().apply {
            for (i in startRank until endRank) {
                add(
                    TokenEntry(
                        posTableIndex = posTableIndexList[i],
                        wordCost = wordCostList[i],
                        nodeId = nodeIdList[i]
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
        val b = bitvector.rank1CommonShort(bitvector.select0CommonShort(nodeId,rank0ArrayTokenArrayBitvector).toInt(),rank1ArrayTokenArrayBitvector)
        val c = bitvector.rank1CommonShort(bitvector.select0CommonShort((nodeId + 1).toShort(),rank0ArrayTokenArrayBitvector).toInt(),rank1ArrayTokenArrayBitvector)
        val tempList2 = mutableListOf<TokenEntry>()
        for (i in b until c){
            tempList2.add(
                TokenEntry(
                    posTableIndex = posTableIndexList[i],
                    wordCost = wordCostList[i],
                    nodeId = nodeIdList[i],
                )
            )
        }
        return tempList2
    }

    fun buildJunctionArray(
        dictionaries: MutableList<Dictionary>,
        tangoTrie: LOUDS,
        out: ObjectOutput,
        objectInputStream: ObjectInputStream
    ){
        val posTableWithIndex = readPOSTableWithIndex(objectInputStream)
        dictionaries.sortedBy { it.yomi.length } .groupBy { it.yomi }.onEachIndexed{ index, entry ->
            bitListTemp.add(false)

            entry.value.forEach { dictionary ->
                val key = Pair(dictionary.leftId, dictionary.rightId)
                val posIndex = posTableWithIndex[key]
                posIndex?.let {
                    println("build token array:$index ${entry.key} ${dictionary.tango}")
                    val posTableIndex = it.toShort()
                    bitListTemp.add(true)
                    posTableIndexListTemp.add(posTableIndex)
                    wordCostListTemp.add(dictionary.cost)
                    nodeIdListTemp.add(if (dictionary.yomi == dictionary.tango || entry.key.hiraToKata() == dictionary.tango) -1 else tangoTrie.getNodeIndex(dictionary.tango))
                }
            }
        }
        writeExternal(out)
    }

    private fun writeExternal(out: ObjectOutput){
        try {
            out.apply {
                writeInt(posTableIndexListTemp.toByteArrayFromListShort().size)
                writeInt(wordCostListTemp.toByteArrayFromListShort().size)
                writeInt(nodeIdListTemp.toByteArray().size)

                writeObject(posTableIndexListTemp.toShortArray())
                writeObject(wordCostListTemp.toShortArray())
                writeObject(nodeIdListTemp.toIntArray())
                writeObject(bitListTemp.toBitSet())
                flush()
                close()
            }
        }catch (e: IOException){
            println(e.stackTraceToString())
        }
    }

    fun readExternal(
        objectInput: ObjectInput,
    ): TokenArray {
        objectInput.apply {
            try {
                posTableIndexList = readObject() as ShortArray
                wordCostList = readObject() as ShortArray
                nodeIdList = readObject() as IntArray
                bitvector = readObject() as BitSet
                close()
            }catch (e: Exception){
                println(e.stackTraceToString())
            }
        }
        return TokenArray()
    }

    /**
     *
     * @param fileList dictionary00 ~ dictionary09
     *
     **/
    fun buildPOSTable(
        fileList: List<String>,
        objectOutputStream: ObjectOutputStream
    ){
        val tempMap: MutableMap<Pair<Short,Short>,Int> = mutableMapOf()
        fileList.forEach {
            val line = this::class.java.getResourceAsStream(it)
                ?.bufferedReader()
                ?.readLines()
            line?.forEach { str ->
                str.apply {
                    val leftId = split("\\t".toRegex())[1]
                    val rightId = split("\\t".toRegex())[2]
                    if (tempMap[Pair(leftId.toShort(),rightId.toShort())] == null){
                        tempMap[Pair(leftId.toShort(),rightId.toShort())] = 0
                    }else{
                        tempMap[Pair(leftId.toShort(),rightId.toShort())] = (tempMap[Pair(leftId.toShort(),rightId.toShort())]!!) + 1
                    }
                }
            }
        }

        val result = tempMap.toList().sortedByDescending { (_, value) -> value }.toMap()
        val objectToWrite = result.keys.toList()
        try {
            objectOutputStream.apply {
                writeObject(objectToWrite)
                flush()
                close()
            }
        }catch (e: Exception){
            println(e.stackTraceToString())
        }
    }

    /**
     *
     * @param fileList dictionary00 ~ dictionary09
     *
     **/
    fun buildPOSTableWithIndex(
        fileList: List<String>,
        objectOutputStream: ObjectOutputStream
    ){
        val tempMap: MutableMap<Pair<Short,Short>,Int> = mutableMapOf()
        fileList.forEach {
            val line = this::class.java.getResourceAsStream(it)
                ?.bufferedReader()
                ?.readLines()
            line?.forEach { str ->
                str.apply {
                    val leftId = split("\\t".toRegex())[1]
                    val rightId = split("\\t".toRegex())[2]
                    if (tempMap[Pair(leftId.toShort(),rightId.toShort())] == null){
                        tempMap[Pair(leftId.toShort(),rightId.toShort())] = 0
                    }else{
                        tempMap[Pair(leftId.toShort(),rightId.toShort())] = (tempMap[Pair(leftId.toShort(),rightId.toShort())]!!) + 1
                    }
                }
            }
        }

        val result = tempMap.toList().sortedByDescending { (_, value) -> value }.toMap()
        val mapToSave = result.keys.toList().mapIndexed { index, pair -> pair to index  }.toMap()
        try {
            objectOutputStream.apply {
                writeObject(mapToSave)
                flush()
                close()
            }
        }catch (e: Exception){
            println(e.stackTraceToString())
        }
    }

    fun readPOSTable(
        objectInputStream: ObjectInputStream
    ) {
        objectInputStream.apply {
            leftIds = (readObject() as ShortArray).toList()
            rightIds = (readObject() as ShortArray).toList()
        }
    }

    fun readPOSTableWithIndex(
        objectInputStream: ObjectInputStream
    ): Map<Pair<Short, Short>, Int> {
        var a:  Map<Pair<Short, Short>, Int>
        objectInputStream.apply {
            a = (readObject() as Map<Pair<Short, Short>, Int>)
        }
        return a
    }

}