package com.kazumaproject.markdownhelperkeyboard.english

import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ObjectInputStream
import java.util.zip.ZipInputStream

class LoadEnglishLOUDSTest {
    @Before
    fun setup() {

    }

    @Test
    fun testLoadEnglishLOUDS() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val zipInputStream = ZipInputStream(context.assets.open("english/reading.dat.zip"))
        zipInputStream.nextEntry
        val result = ObjectInputStream(BufferedInputStream(zipInputStream)).use { objectInput ->
            LOUDSWithTermId().readExternalNotCompress(objectInput)
        }
        val succinctBitVector = SuccinctBitVector(result.LBS)
        val leafBitVector = SuccinctBitVector(result.isLeaf)
        println("Loaded object: ${result.labels.size}")

        val text = "on"
        val commonPrefixSearch = result.commonPrefixSearch(text, succinctBitVector)
        val searchResult = commonPrefixSearch.map {
            Pair(it, result.getTermId(result.getNodeIndex(it, succinctBitVector), leafBitVector))
        }
        println(searchResult)
        val suggestions = result.predictiveSearch("i", succinctBitVector, 4)
        val pairs = suggestions.map { term ->
            term to result.getTermId(result.getNodeIndex(term, succinctBitVector), leafBitVector)
        }.toMutableList()
        pairs.sortBy { it.second }
        println(pairs)
    }

}
