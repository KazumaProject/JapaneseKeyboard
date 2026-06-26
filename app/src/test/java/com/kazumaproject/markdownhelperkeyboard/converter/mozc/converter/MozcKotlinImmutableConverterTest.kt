package com.kazumaproject.markdownhelperkeyboard.converter.mozc.converter

import com.kazumaproject.markdownhelperkeyboard.converter.mozc.dictionary.MozcDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.dictionary.MozcDictionaryGroup
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.dictionary.MozcDictionaryLookupOptions
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.dictionary.MozcDictionarySource
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.dictionary.MozcDictionaryToken
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.model.MozcCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.segmenter.MozcKotlinSegmenter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MozcKotlinImmutableConverterTest {

    @Test
    fun converterUsesViterbiCostInsteadOfLongestToken() = runTest {
        val converter = MozcKotlinImmutableConverter(
            dictionaryGroup = MozcDictionaryGroup(
                systemDictionary = ArtificialDictionary(
                    listOf(
                        token("あ", "A", 1, 10),
                        token("い", "I", 2, 10),
                        token("あい", "AI", 3, 100),
                    )
                ),
                systemUserDictionary = null,
                wikiDictionary = null,
                webDictionary = null,
                personDictionary = null,
                placesDictionary = null,
                neologdDictionary = null,
            ),
            connector = connectorWithCosts(),
            segmenter = zeroPenaltySegmenter(posSize = 4),
            nBestGenerator = MozcKotlinNBestGenerator(connectorWithCosts()),
            candidateFilter = MozcKotlinCandidateFilter(),
        )

        val result = converter.convert(
            MozcKotlinConversionRequest(
                input = "あい",
                nBest = 2,
                dictionaryOptions = MozcDictionaryLookupOptions(
                    enablePersonName = false,
                    enablePlaces = false,
                    enableWiki = false,
                    enableNeologd = false,
                    enableWeb = false,
                ),
                runtimeUserDictionary = null,
                runtimeLearnDictionary = null,
            )
        )

        assertEquals("AI", result.candidates.first().value)
        assertEquals(20, result.candidates.first().wcost)
    }

    @Test
    fun candidateFilterRemovesDuplicateValues() {
        val filtered = MozcKotlinCandidateFilter().filter(
            listOf(
                MozcCandidate(key = "a", value = "A"),
                MozcCandidate(key = "a", value = "A"),
                MozcCandidate(key = "b", value = ""),
                MozcCandidate(key = "c", value = "C"),
            )
        )

        assertEquals(listOf("A", "C"), filtered.map { it.value })
    }

    private class ArtificialDictionary(
        private val tokens: List<MozcDictionaryToken>,
    ) : MozcDictionary {
        override fun lookupPrefix(key: String, callback: (MozcDictionaryToken) -> Unit) {
            tokens.filter { key.startsWith(it.key) }.forEach(callback)
        }

        override fun lookupExact(key: String, callback: (MozcDictionaryToken) -> Unit) {
            tokens.filter { it.key == key }.forEach(callback)
        }
    }

    private fun token(key: String, value: String, id: Short, cost: Int): MozcDictionaryToken =
        MozcDictionaryToken(
            key = key,
            value = value,
            lid = id,
            rid = id,
            cost = cost,
            source = MozcDictionarySource.SYSTEM,
        )

    private fun connectorWithCosts(): MozcKotlinConnector {
        val matrixSize = 4
        val costs = ShortArray(matrixSize * matrixSize) { 1000 }
        costs[0 * matrixSize + 1] = 0
        costs[1 * matrixSize + 2] = 0
        costs[2 * matrixSize + 0] = 0
        costs[0 * matrixSize + 3] = 0
        costs[3 * matrixSize + 0] = 1000
        return MozcKotlinConnector(costs, matrixSize)
    }

    private fun zeroPenaltySegmenter(posSize: Int): MozcKotlinSegmenter =
        MozcKotlinSegmenter(
            compressedLSize = 1,
            compressedRSize = 1,
            lTable = ShortArray(posSize),
            rTable = ShortArray(posSize),
            bitarray = byteArrayOf(1),
            boundaryData = ShortArray(posSize * 2),
        )
}
