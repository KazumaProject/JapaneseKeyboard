package com.kazumaproject.markdownhelperkeyboard.converter.mozc.converter

import com.kazumaproject.markdownhelperkeyboard.converter.mozc.dictionary.MozcDictionaryGroup
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.dictionary.MozcDictionaryToken
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.lattice.MozcLattice
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.model.MozcCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.model.MozcNode
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.model.MozcNodeType
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.model.MozcSegments
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.segmenter.MozcKotlinSegmenter

class MozcKotlinImmutableConverter(
    private val dictionaryGroup: MozcDictionaryGroup,
    private val connector: MozcKotlinConnector,
    private val segmenter: MozcKotlinSegmenter,
    private val nBestGenerator: MozcKotlinNBestGenerator,
    private val candidateFilter: MozcKotlinCandidateFilter,
) {
    suspend fun convert(
        request: MozcKotlinConversionRequest,
    ): MozcKotlinConversionResult {
        val lattice = MozcLattice()
        makeLattice(request, lattice)
        applyPrefixSuffixPenalty(
            lattice = lattice,
            inputLength = request.input.length,
            options = request.options,
        )
        val ok = viterbi(lattice)
        if (!ok) {
            val segments = MozcSegments()
            val segment = segments.addConversionSegment(request.input)
            val fallback = MozcCandidate(
                key = request.input,
                value = request.input,
                contentKey = request.input,
                contentValue = request.input,
                consumedKeySize = request.input.length,
                cost = Int.MAX_VALUE / 4,
                wcost = Int.MAX_VALUE / 4,
            )
            segment.candidates.add(fallback)
            return MozcKotlinConversionResult(
                segments = segments,
                candidates = listOf(fallback),
            )
        }

        val segments = makeSegmentsFromBestPath(
            lattice = lattice,
            request = request,
        )
        val candidates = candidateFilter.filter(
            nBestGenerator.generate(
                lattice = lattice,
                maxCandidates = request.nBest,
            )
        )
        segments.firstConversionSegment()?.candidates?.apply {
            clear()
            addAll(candidates)
        }
        return MozcKotlinConversionResult(
            segments = segments,
            candidates = candidates,
        )
    }

    private suspend fun makeLattice(
        request: MozcKotlinConversionRequest,
        lattice: MozcLattice,
    ) {
        val input = request.input
        lattice.setKey(input)
        for (begin in input.indices) {
            val suffix = input.substring(begin)
            dictionaryGroup.lookupPrefix(
                key = suffix,
                options = request.dictionaryOptions,
            ) { token ->
                lattice.insert(token.toNode(begin))
            }
            request.runtimeUserDictionary?.lookupPrefix(suffix) { token ->
                lattice.insert(token.toNode(begin))
            }
            request.runtimeLearnDictionary?.lookupPrefix(suffix) { token ->
                lattice.insert(token.toNode(begin))
            }
        }
    }

    private fun MozcDictionaryToken.toNode(begin: Int): MozcNode =
        MozcNode(
            rid = rid,
            lid = lid,
            beginPos = begin,
            endPos = begin + key.length,
            wcost = cost,
            key = key,
            value = value,
            nodeType = MozcNodeType.NOR_NODE,
        )

    private fun applyPrefixSuffixPenalty(
        lattice: MozcLattice,
        inputLength: Int,
        options: MozcKotlinConversionOptions,
    ) {
        for (pos in 0..inputLength) {
            for (node in lattice.beginNodes(pos)) {
                if (node.nodeType != MozcNodeType.NOR_NODE) continue
                if (!options.disablePrefixPenalty) {
                    node.wcost += segmenter.getPrefixPenalty(node.lid)
                }
                if (!options.disableSuffixPenalty) {
                    node.wcost += segmenter.getSuffixPenalty(node.rid)
                }
            }
        }
    }

    private fun viterbi(lattice: MozcLattice): Boolean {
        val inputLength = lattice.key.length
        val bos = lattice.bosNode
        for (rnode in lattice.beginNodes(0)) {
            if (rnode.nodeType != MozcNodeType.NOR_NODE) continue
            rnode.prev = bos
            rnode.cost =
                bos.cost +
                        connector.getTransitionCost(bos.rid, rnode.lid) +
                        rnode.wcost
        }
        for (pos in 1 until inputLength) {
            for (rnode in lattice.beginNodes(pos)) {
                if (rnode.nodeType != MozcNodeType.NOR_NODE) continue
                var bestPrev: MozcNode? = null
                var bestCost = Int.MAX_VALUE
                for (lnode in lattice.endNodes(pos)) {
                    if (lnode.prev == null && lnode.nodeType != MozcNodeType.BOS_NODE) continue
                    val cost =
                        lnode.cost +
                                connector.getTransitionCost(lnode.rid, rnode.lid) +
                                rnode.wcost
                    if (cost < bestCost) {
                        bestCost = cost
                        bestPrev = lnode
                    }
                }
                if (bestPrev != null) {
                    rnode.prev = bestPrev
                    rnode.cost = bestCost
                }
            }
        }
        val eos = lattice.eosNode
        var bestPrev: MozcNode? = null
        var bestCost = Int.MAX_VALUE
        for (lnode in lattice.endNodes(inputLength)) {
            if (lnode.prev == null) continue
            val cost =
                lnode.cost +
                        connector.getTransitionCost(lnode.rid, eos.lid)
            if (cost < bestCost) {
                bestCost = cost
                bestPrev = lnode
            }
        }
        if (bestPrev == null) return false
        eos.prev = bestPrev
        eos.cost = bestCost
        var node: MozcNode? = eos
        while (node?.prev != null) {
            val prev = node.prev
            prev?.next = node
            node = prev
        }
        return lattice.bosNode.next != null
    }

    private fun makeSegmentsFromBestPath(
        lattice: MozcLattice,
        request: MozcKotlinConversionRequest,
    ): MozcSegments {
        val segments = MozcSegments()
        val nodes = collectBestPath(lattice)
        if (nodes.isEmpty()) {
            segments.addConversionSegment(request.input)
            return segments
        }

        var currentKey = StringBuilder()
        var currentValue = StringBuilder()
        val currentNodes = mutableListOf<MozcNode>()
        for (i in nodes.indices) {
            val node = nodes[i]
            currentKey.append(node.key)
            currentValue.append(node.value)
            currentNodes.add(node)
            val next = nodes.getOrNull(i + 1)
            val shouldBreak =
                next == null || (!request.options.isSingleSegment && segmenter.isBoundary(
                    rid = node.rid,
                    lid = next.lid,
                ))
            if (shouldBreak) {
                val key = currentKey.toString()
                val value = currentValue.toString()
                val segment = segments.addConversionSegment(key)
                val wcost = currentNodes.sumOf { it.wcost }
                val structureCost = calculateStructureCost(currentNodes)
                segment.candidates.add(
                    MozcCandidate(
                        key = key,
                        value = value,
                        contentKey = key,
                        contentValue = value,
                        consumedKeySize = key.length,
                        cost = wcost + structureCost,
                        wcost = wcost,
                        structureCost = structureCost,
                        lid = currentNodes.first().lid,
                        rid = currentNodes.last().rid,
                    )
                )
                currentKey = StringBuilder()
                currentValue = StringBuilder()
                currentNodes.clear()
            }
        }
        return segments
    }

    private fun collectBestPath(lattice: MozcLattice): List<MozcNode> {
        val reversed = mutableListOf<MozcNode>()
        var node = lattice.eosNode.prev
        while (node != null && node.nodeType != MozcNodeType.BOS_NODE) {
            reversed.add(node)
            node = node.prev
        }
        return reversed.asReversed()
    }

    private fun calculateStructureCost(nodes: List<MozcNode>): Int {
        if (nodes.isEmpty()) return 0
        var cost = connector.getTransitionCost(0, nodes.first().lid)
        for (i in 0 until nodes.lastIndex) {
            cost += connector.getTransitionCost(nodes[i].rid, nodes[i + 1].lid)
        }
        cost += connector.getTransitionCost(nodes.last().rid, 0)
        return cost
    }
}
