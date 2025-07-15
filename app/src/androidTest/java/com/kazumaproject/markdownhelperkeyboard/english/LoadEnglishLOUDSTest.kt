package com.kazumaproject.markdownhelperkeyboard.english

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ObjectInputStream

class LoadEnglishLOUDSTest {
    @Before
    fun setup() {

    }

    @Test
    fun testLoadEnglishLOUDS() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val objectInput = ObjectInputStream(
            BufferedInputStream(context.assets.open("english/english.dat"))
        )
        val result = EnglishLOUDS().readExternal(objectInput)
        println("Loaded object: ${result.costListSave.size}")
        objectInput.close()

        val text = "on"
        val commonPrefixSearch = result.commonPrefixSearch(text)
        val searchResult = commonPrefixSearch.map {
            Pair(it, result.getTermId(result.getNodeIndex(it)))
        }
        println(searchResult)
        val suggestions = result.predictiveSearch("i", 4)
        val pairs = suggestions.map { term ->
            term to result.getTermId(result.getNodeIndex(term))
        }.toMutableList()
        pairs.sortBy { it.second }
        println(pairs)
    }

}
