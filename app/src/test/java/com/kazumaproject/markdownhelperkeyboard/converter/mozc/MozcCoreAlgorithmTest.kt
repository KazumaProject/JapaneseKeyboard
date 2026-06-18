package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MozcCoreAlgorithmTest {
    private val segmenterData = MozcTestAssets.segmenterData()
    private val trace = MozcConverterTrace()
    private val segmenter = MozcSegmenter(segmenterData, trace)
    private val boundaryDetector = MozcBoundaryDetector(segmenter)
    private val connector = MozcConnector(ShortArray(3000 * 3000), 3000)

    @Test
    fun latticeStoresBosEosAndBeginEndNodesByPosition() {
        val lattice = MozcLattice("かな")
        val node = MozcNode().apply {
            key = "か"
            value = "香"
            beginPos = 0
            endPos = 1
        }
        lattice.insert(node)

        assertSame(lattice.bosNode, lattice.endNodes(0).single { it.nodeType == MozcNodeType.BOS })
        assertSame(lattice.eosNode, lattice.beginNodes(2).single { it.nodeType == MozcNodeType.EOS })
        assertSame(node, lattice.beginNodes(0).single { it.value == "香" })
        assertSame(node, lattice.endNodes(1).single { it.value == "香" })
    }

    @Test
    fun unknownNodesAreAddedIndependentOfDictionaryHitsAndByCharacterType() {
        val generator = MozcUnknownNodeGenerator(segmenterData.posMatcher, trace)

        val numberBuilder = MozcNodeListBuilder()
        generator.addCharacterTypeBasedNodes("123", 0, numberBuilder)
        assertEquals(segmenterData.posMatcher.getRequiredId("Number"), numberBuilder.result().single().leftId)
        assertEquals(MozcUnknownNodeGenerator.DEFAULT_NUMBER_COST, numberBuilder.result().single().wordCost)

        val alphabetBuilder = MozcNodeListBuilder()
        alphabetBuilder.add(MozcNode().apply {
            key = "abc"
            value = "ABC"
            beginPos = 0
            endPos = 3
        })
        generator.addCharacterTypeBasedNodes("abc", 0, alphabetBuilder)
        assertTrue(alphabetBuilder.result().any { it.value == "ABC" })
        assertTrue(alphabetBuilder.result().any { it.key == "a" && it.isUnknown })
        assertTrue(alphabetBuilder.result().any { it.key == "abc" && it.isUnknown })

        val katakanaBuilder = MozcNodeListBuilder()
        generator.addCharacterTypeBasedNodes("カタカナ", 0, katakanaBuilder)
        assertTrue(katakanaBuilder.result().any { it.key == "カタカナ" && it.isUnknown })
    }

    @Test
    fun prefixSuffixPenaltyAddsToWordCostBeforeViterbiOnly() {
        val lattice = MozcLattice("度")
        val node = MozcNode().apply {
            key = "度"
            value = "度"
            leftId = 2015
            rightId = 2015
            wordCost = 100
            totalCost = 77
            beginPos = 0
            endPos = 1
        }
        lattice.insert(node)

        MozcPrefixSuffixPenalty(segmenter, trace).apply(lattice, MozcConversionOptions())

        assertTrue(node.wordCost > 100)
        assertEquals(Int.MAX_VALUE, node.totalCost)
        assertTrue(trace.prefixPenaltyAppliedCount > 0)
        assertTrue(trace.suffixPenaltyAppliedCount > 0)
    }

    @Test
    fun viterbiSeparatesWordCostFromTotalCostAndHonorsConstrainedPrev() {
        val lattice = MozcLattice("ab")
        val preferredLeft = normalNode("a", "A", 0, 1, 1, 1, 100)
        val constrainedLeft = normalNode("a", "X", 0, 1, 2, 2, 500)
        val right = normalNode("b", "B", 1, 2, 1, 1, 10).apply {
            constrainedPrev = constrainedLeft
        }
        lattice.insert(preferredLeft)
        lattice.insert(constrainedLeft)
        lattice.insert(right)

        assertTrue(MozcViterbi(connector, trace).calculate(lattice))

        assertEquals(10, right.wordCost)
        assertSame(constrainedLeft, right.prev)
        assertSame(right, lattice.eosNode.prev)
        assertTrue(trace.constrainedViterbiCount > 0)
    }

    @Test
    fun resegmentRunsBeforeViterbiAndCreatesConstrainedNodes() {
        val lattice = MozcLattice("第1")
        lattice.insert(normalNode("第1", "第1", 0, 2, 1851, 2044, 1000))

        MozcResegmenter(segmenter, connector, boundaryDetector, trace)
            .resegment(lattice, MozcConversionOptions())

        val constrained = lattice.beginNodes(1).filter { it.constrainedPrev != null }
        assertTrue(constrained.isNotEmpty())
        assertNotNull(constrained.first().constrainedPrev)
        assertTrue(trace.resegmentInsertedNodeCount >= 2)
    }

    @Test
    fun nbestUsesQueueElementWithoutMutatingNodeLinksAndDeduplicatesSurface() {
        val lattice = MozcLattice("ab")
        val left = normalNode("a", "A", 0, 1, 1, 1, 10)
        val right = normalNode("b", "B", 1, 2, 1, 1, 20)
        lattice.insert(left)
        lattice.insert(right)
        assertTrue(MozcViterbi(connector).calculate(lattice))
        val leftNext = left.next
        val rightNext = right.next

        val paths = MozcNBestGenerator(connector, boundaryDetector, trace)
            .generate(lattice, MozcConversionOptions(nBest = 3))

        assertEquals("AB", paths.first().nodes.joinToString("") { it.value })
        assertEquals(paths.sortedBy { it.cost }, paths)
        assertSame(leftNext, left.next)
        assertSame(rightNext, right.next)
        assertTrue(trace.boundaryCheckCount > 0)
    }

    @Test
    fun candidateConversionAndFilterProduceExistingCandidateShape() {
        val path = MozcPath(
            nodes = listOf(
                normalNode("に", "日", 0, 1, 1851, 1851, 10),
                normalNode("ほん", "本", 1, 3, 1851, 1851, 10),
            ),
            cost = 123,
            structureCost = 20,
            wordCost = 20,
            innerBoundaryOffsets = listOf(1),
        )

        val converted = MozcCandidateConverter().convert(listOf(path))
        val filtered = MozcCandidateFilter().filter(converted)

        assertEquals("日本", filtered.single().string)
        assertEquals("にほん", filtered.single().yomi)
        assertEquals(123, filtered.single().score)
        assertEquals(1851.toShort(), filtered.single().leftId)
        assertEquals(1851.toShort(), filtered.single().rightId)
        assertEquals(3.toUByte(), filtered.single().length)
    }

    private fun normalNode(
        key: String,
        value: String,
        begin: Int,
        end: Int,
        left: Int,
        right: Int,
        wordCost: Int,
    ): MozcNode =
        MozcNode().apply {
            this.key = key
            this.value = value
            beginPos = begin
            endPos = end
            leftId = left.toShort()
            rightId = right.toShort()
            this.wordCost = wordCost
            nodeType = MozcNodeType.NORMAL
        }
}
