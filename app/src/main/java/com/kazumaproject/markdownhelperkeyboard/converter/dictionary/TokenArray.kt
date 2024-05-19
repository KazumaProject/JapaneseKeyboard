package com.kazumaproject.dictionary

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.bitset.rank1
import com.kazumaproject.bitset.select0
import com.kazumaproject.byteArrayToShortList
import com.kazumaproject.connection_id.deflate
import com.kazumaproject.connection_id.inflate
import com.kazumaproject.dictionary.models.Dictionary
import com.kazumaproject.dictionary.models.TokenEntry
import com.kazumaproject.hiraToKata
import com.kazumaproject.toBitSet
import com.kazumaproject.toByteArray
import com.kazumaproject.toByteArrayFromListShort
import com.kazumaproject.toListInt
import java.io.IOException
import java.io.ObjectInput
import java.io.ObjectInputStream
import java.io.ObjectOutput
import java.io.ObjectOutputStream
import java.util.BitSet

class TokenArray {
    private var posTableIndexList: MutableList<Short> = arrayListOf()
    private var wordCostList: MutableList<Short> = arrayListOf()
    private var nodeIdList: MutableList<Int> = arrayListOf()
    private var bitListTemp: MutableList<Boolean> = arrayListOf()
    private var bitvector: BitSet = BitSet()
    var leftIds: List<Short> = listOf()
    var rightIds: List<Short> = listOf()

    fun getListDictionaryByYomiTermId(
        nodeId: Int,
    ): List<TokenEntry> {
        val b = bitvector.rank1(bitvector.select0(nodeId))
        val c = bitvector.rank1(bitvector.select0(nodeId + 1))
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
                    posTableIndexList.add(posTableIndex)
                    wordCostList.add(dictionary.cost)
                    nodeIdList.add(if (dictionary.yomi == dictionary.tango || entry.key.hiraToKata() == dictionary.tango) -1 else tangoTrie.getNodeIndex(dictionary.tango))
                }
            }
        }
        writeExternal(out)
    }

    private fun writeExternal(out: ObjectOutput){
        try {
            out.apply {
                writeInt(posTableIndexList.toByteArrayFromListShort().size)
                writeInt(wordCostList.toByteArrayFromListShort().size)
                writeInt(nodeIdList.toByteArray().size)

                writeObject(posTableIndexList.toByteArrayFromListShort().deflate())
                writeObject(wordCostList.toByteArrayFromListShort().deflate())
                writeObject(nodeIdList.toByteArray().deflate())
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
                val posTableIndexListSize = readInt()
                val wordCostListSize = readInt()
                val nodeIdListSize = readInt()
                posTableIndexList = (readObject() as ByteArray).inflate(posTableIndexListSize).byteArrayToShortList().toMutableList()
                wordCostList = (readObject() as ByteArray).inflate(wordCostListSize).byteArrayToShortList().toMutableList()
                nodeIdList = (readObject() as ByteArray).inflate(nodeIdListSize).toListInt().toMutableList()
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
            val leftIdSize = readInt()
            val rightIdSize = readInt()
            leftIds = (readObject() as ByteArray).inflate(leftIdSize).byteArrayToShortList()
            rightIds = (readObject() as ByteArray).inflate(rightIdSize).byteArrayToShortList()
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