package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MozcDictionaryAndConverterTest {
    @Test
    fun systemDictionaryLookupCreatesMozcNodesFromLoudsAndTokenArray() {
        val trace = MozcConverterTrace()
        val dictionary = MozcTestAssets.systemDictionary(trace)
        val builder = MozcNodeListBuilder()

        dictionary.lookupPrefix(
            key = "きょう",
            beginPos = 0,
            options = MozcConversionOptions(),
            builder = builder,
        )

        val nodes = builder.result()
        assertTrue(nodes.isNotEmpty())
        assertTrue(nodes.any { it.key == "きょう" })
        val node = nodes.first()
        assertTrue(node.value.isNotEmpty())
        assertEquals(0, node.beginPos)
        assertTrue(node.endPos > node.beginPos)
        assertTrue(node.leftId.toInt() >= 0)
        assertTrue(node.rightId.toInt() >= 0)
        assertTrue(trace.dictionaryNodeCount > 0)
    }

    @Test
    fun compatibleConverterReturnsNonEmptySystemDictionaryCandidate() {
        val trace = MozcConverterTrace()
        val data = MozcTestAssets.segmenterData()
        val connector = MozcTestAssets.connector()
        val segmenter = MozcSegmenter(data, trace)
        val boundaryDetector = MozcBoundaryDetector(segmenter)
        val converter = MozcCompatibleConverter(
            dictionary = MozcTestAssets.systemDictionary(trace),
            unknownNodeGenerator = MozcUnknownNodeGenerator(data.posMatcher, trace),
            prefixSuffixPenalty = MozcPrefixSuffixPenalty(segmenter, trace),
            resegmenter = MozcResegmenter(segmenter, connector, boundaryDetector, trace),
            viterbi = MozcViterbi(connector, trace),
            nBestGenerator = MozcNBestGenerator(connector, boundaryDetector, trace),
            candidateFilter = MozcCandidateFilter(trace),
            trace = trace,
        )

        val candidates = converter.getCandidates("きょう", MozcConversionOptions(nBest = 5))

        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.any { it.yomi == "きょう" })
        assertTrue(trace.dictionaryNodeCount > 0)
        assertTrue(trace.unknownNodeCount > 0)
        assertTrue(trace.prefixPenaltyAppliedCount > 0)
        assertTrue(trace.suffixPenaltyAppliedCount > 0)
        assertTrue(trace.boundaryCheckCount > 0)
    }
}
