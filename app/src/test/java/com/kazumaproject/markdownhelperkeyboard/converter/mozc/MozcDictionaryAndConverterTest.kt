package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val converter = createConverter(trace)

        val candidates = converter.getCandidates("きょう", MozcConversionOptions(nBest = 5))

        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.any { it.yomi == "きょう" })
        assertTrue(trace.dictionaryNodeCount > 0)
        assertTrue(trace.unknownNodeCount > 0)
        assertTrue(trace.prefixPenaltyAppliedCount > 0)
        assertTrue(trace.suffixPenaltyAppliedCount > 0)
        assertTrue(trace.boundaryCheckCount > 0)
    }

    @Test
    fun systemDictionaryLookupAddsOmissionSearchNodesWhenEnabled() {
        val trace = MozcConverterTrace()
        val dictionary = MozcTestAssets.systemDictionary(trace)
        val normalBuilder = MozcNodeListBuilder()
        val omissionBuilder = MozcNodeListBuilder()
        val offset = 1900

        dictionary.lookupPrefix(
            key = "がくせい",
            beginPos = 0,
            options = MozcConversionOptions(),
            builder = normalBuilder,
        )
        dictionary.lookupPrefix(
            key = "かくせい",
            beginPos = 0,
            options = MozcConversionOptions(
                isOmissionSearchEnabled = true,
                omissionSearchOffsetScore = offset,
            ),
            builder = omissionBuilder,
        )

        val baseStudentNode = normalBuilder.result().first { it.key == "がくせい" && it.value == "学生" }
        val omissionStudentNode = omissionBuilder.result().first { it.key == "がくせい" && it.value == "学生" }
        assertEquals(baseStudentNode.wordCost + offset, omissionStudentNode.wordCost)
        assertEquals(0, omissionStudentNode.beginPos)
        assertEquals("かくせい".length, omissionStudentNode.endPos)
        assertTrue(omissionStudentNode.attributes and MozcNodeAttribute.OMISSION_SEARCH != 0)
        assertTrue(trace.omissionLookupCount > 0)
        assertTrue(trace.omissionNodeCount > 0)
    }

    @Test
    fun systemDictionaryLookupDoesNotAddOmissionSearchNodesWhenDisabled() {
        val dictionary = MozcTestAssets.systemDictionary()
        val builder = MozcNodeListBuilder()

        dictionary.lookupPrefix(
            key = "かくせい",
            beginPos = 0,
            options = MozcConversionOptions(isOmissionSearchEnabled = false),
            builder = builder,
        )

        assertFalse(builder.result().any { it.key == "がくせい" && it.value == "学生" })
    }

    @Test
    fun compatibleConverterReturnsOmissionSearchCandidateWhenEnabled() {
        val trace = MozcConverterTrace()
        val converter = createConverter(trace)

        val candidates = converter.getCandidates(
            "かくせい",
            MozcConversionOptions(
                nBest = 20,
                isOmissionSearchEnabled = true,
                omissionSearchOffsetScore = 1900,
            ),
        )

        assertTrue(candidates.any { it.string == "学生" && it.yomi == "がくせい" })
        assertTrue(trace.omissionLookupCount > 0)
        assertTrue(trace.omissionNodeCount > 0)
    }

    private fun createConverter(trace: MozcConverterTrace): MozcCompatibleConverter {
        val data = MozcTestAssets.segmenterData()
        val connector = MozcTestAssets.connector()
        val segmenter = MozcSegmenter(data, trace)
        val boundaryDetector = MozcBoundaryDetector(segmenter)
        return MozcCompatibleConverter(
            dictionary = MozcTestAssets.systemDictionary(trace),
            unknownNodeGenerator = MozcUnknownNodeGenerator(data.posMatcher, trace),
            prefixSuffixPenalty = MozcPrefixSuffixPenalty(segmenter, trace),
            resegmenter = MozcResegmenter(segmenter, connector, boundaryDetector, trace),
            viterbi = MozcViterbi(connector, trace),
            nBestGenerator = MozcNBestGenerator(connector, boundaryDetector, trace),
            candidateFilter = MozcCandidateFilter(trace),
            trace = trace,
        )
    }
}
