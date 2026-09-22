package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.Louds.with_term_id.ConverterWithTermId
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.prefix.with_term_id.PrefixTreeWithTermId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class DictionaryCandidateReadingsTest {

    @Test
    fun exactReadingIsKeptWhenJapanesePredictionIsDisabled() {
        val input = "ひらもとれん"
        val dictionary = buildDictionary(input, "${input}ご")

        val readings = dictionaryCandidateReadings(
            input = input,
            yomiTrie = dictionary.trie,
            succinctBitVector = dictionary.index,
            predictionConfig = PredictionConfig(
                japanesePredictionEnabled = false,
            ),
        )

        assertEquals(listOf(input), readings)
    }

    @Test
    fun exactReadingAndAcceptedCompletionsAreReturnedTogether() {
        val input = "ひらもとれん"
        val dictionary = buildDictionary(input, "${input}ご")

        val readings = dictionaryCandidateReadings(
            input = input,
            yomiTrie = dictionary.trie,
            succinctBitVector = dictionary.index,
            predictionConfig = PredictionConfig(),
        )

        assertTrue(readings.contains(input))
        assertTrue(readings.contains("${input}ご"))
    }

    @Test
    fun nonTerminalPrefixIsNotTreatedAsAnExactReading() {
        val input = "ふい"
        val dictionary = buildDictionary("${input}んき")

        val readings = dictionaryCandidateReadings(
            input = input,
            yomiTrie = dictionary.trie,
            succinctBitVector = dictionary.index,
            predictionConfig = PredictionConfig(japanesePredictionEnabled = false),
        )

        assertEquals(emptyList<String>(), readings)
    }

    @Test
    fun exactReadingSurvivesShortInputAndDisabledDictionaryCompletionSource() {
        val input = "ふい"
        val dictionary = buildDictionary(input, "${input}んき")

        val readings = dictionaryCandidateReadings(
            input = input,
            yomiTrie = dictionary.trie,
            succinctBitVector = dictionary.index,
            predictionConfig = PredictionConfig(),
            completionEnabled = false,
        )

        assertEquals(listOf(input), readings)
    }

    @Test
    fun bundledWebDictionaryRetainsHiramotoRenWhenPredictionIsDisabled() {
        val input = "ひらもとれん"
        val assets = findAssetsDirectory()
        val yomiTrie = readAssetObject(assets, "web/yomi_web.dat.zip") {
            LOUDSWithTermId().readExternalNotCompress(it)
        }
        val yomiIndex = SuccinctBitVector(yomiTrie.LBS)
        val readings = dictionaryCandidateReadings(
            input = input,
            yomiTrie = yomiTrie,
            succinctBitVector = yomiIndex,
            predictionConfig = PredictionConfig(
                japanesePredictionEnabled = false,
            ),
        )
        assertEquals(listOf(input), readings)

        val tangoTrie = readAssetObject(assets, "web/tango_web.dat.zip") {
            LOUDS().readExternalNotCompress(it)
        }
        val tokenArray = readAssetObject(assets, "web/token_web.dat.zip") {
            TokenArray().also { tokenArray -> tokenArray.readExternal(it) }
        }
        val termId = yomiTrie.getTermId(
            yomiTrie.getNodeIndex(input, yomiIndex),
            SuccinctBitVector(yomiTrie.isLeaf),
        )
        val candidateStrings = tokenArray.getListDictionaryByYomiTermId(
            termId,
            SuccinctBitVector(tokenArray.bitvector),
        ).map { entry ->
            when (entry.nodeId) {
                -2 -> input
                -1 -> input
                else -> tangoTrie.getLetter(entry.nodeId, SuccinctBitVector(tangoTrie.LBS))
            }
        }

        assertTrue(candidateStrings.contains("平本蓮"))
    }

    private fun buildDictionary(vararg readings: String): DictionaryFixture {
        val tree = PrefixTreeWithTermId().apply {
            readings.forEach(::insert)
        }
        val converted = ConverterWithTermId().convert(tree.root).apply {
            convertListToBitSet()
        }
        val trie = LOUDSWithTermId(
            converted.LBS,
            converted.getAllLabels(),
            converted.isLeaf,
            converted.termIds.toIntArray(),
        )
        return DictionaryFixture(trie, SuccinctBitVector(trie.LBS))
    }

    private fun findAssetsDirectory(): File = listOf(
        File("app/src/main/assets"),
        File("src/main/assets"),
    ).firstOrNull(File::exists) ?: error("Missing app assets")

    private fun <T> readAssetObject(
        assets: File,
        path: String,
        read: (ObjectInputStream) -> T,
    ): T {
        val fileInput = FileInputStream(File(assets, path))
        val input: InputStream = if (path.endsWith(".zip")) {
            ZipInputStream(BufferedInputStream(fileInput)).also {
                checkNotNull(it.nextEntry) { "Zip asset has no entries: $path" }
            }
        } else {
            BufferedInputStream(fileInput)
        }
        return ObjectInputStream(BufferedInputStream(input)).use(read)
    }

    private data class DictionaryFixture(
        val trie: LOUDSWithTermId,
        val index: SuccinctBitVector,
    )
}
