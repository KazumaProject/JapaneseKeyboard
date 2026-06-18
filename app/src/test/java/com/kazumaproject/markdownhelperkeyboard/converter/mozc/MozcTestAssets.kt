package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.connection_id.ConnectionIdBuilder
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryBinaryReader
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream

internal object MozcTestAssets {
    fun file(path: String): File =
        listOf(
            File("src/main/assets/$path"),
            File("app/src/main/assets/$path"),
        ).first { it.exists() }

    fun segmenterData(): MozcSegmenterData =
        MozcSegmenterData.fromInputStreams(
            prefixPenalty = FileInputStream(file(MozcSegmenterData.PREFIX_PENALTY_ASSET)),
            suffixPenalty = FileInputStream(file(MozcSegmenterData.SUFFIX_PENALTY_ASSET)),
            boundaryRule = FileInputStream(file(MozcSegmenterData.BOUNDARY_RULE_ASSET)),
            posGroup = FileInputStream(file(MozcSegmenterData.POS_GROUP_ASSET)),
        )

    fun systemDictionary(trace: MozcConverterTrace? = null): LoudsTokenArrayMozcDictionary {
        val yomiTrie = readZipAwareObject("system/yomi.dat.zip") {
            LOUDSWithTermId().readExternalNotCompress(it)
        }
        val tangoTrie = readZipAwareObject("system/tango.dat.zip") {
            LOUDS().readExternalNotCompress(it)
        }
        val tokenArray = TokenArray()
        readZipAwareObject("system/token.dat.zip") {
            tokenArray.readExternal(it)
        }
        readZipAwareObject("pos_table.dat") {
            tokenArray.readPOSTable(it)
        }
        return LoudsTokenArrayMozcDictionary(
            yomiTrie = yomiTrie,
            tangoTrie = tangoTrie,
            tokenArray = tokenArray,
            succinctBitVectorLBSYomi = SuccinctBitVector(yomiTrie.LBS),
            succinctBitVectorIsLeafYomi = SuccinctBitVector(yomiTrie.isLeaf),
            succinctBitVectorTokenArray = SuccinctBitVector(tokenArray.bitvector),
            succinctBitVectorTangoLBS = SuccinctBitVector(tangoTrie.LBS),
            trace = trace,
        )
    }

    fun connector(): MozcConnector {
        val ids = FileInputStream(file("connectionId.dat.zip")).use { input ->
            DictionaryBinaryReader.openZipAwareRaw(BufferedInputStream(input), "connectionId.dat.zip").use { raw ->
                ConnectionIdBuilder().readShortArrayFromBytes(raw)
            }
        }
        return MozcConnector(
            connectionIds = ids,
            matrixSize = ConnectionMatrix.inferMatrixSize(ids)
                ?: error("connectionId.dat.zip is not a square matrix"),
        )
    }

    private fun <T> readZipAwareObject(path: String, block: (ObjectInputStream) -> T): T =
        FileInputStream(file(path)).use { input ->
            DictionaryBinaryReader.openZipAwareObject(BufferedInputStream(input), path).use(block)
        }
}
