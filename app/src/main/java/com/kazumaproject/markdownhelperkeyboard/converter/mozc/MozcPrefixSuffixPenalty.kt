package com.kazumaproject.markdownhelperkeyboard.converter.mozc

class MozcPrefixSuffixPenalty(
    private val segmenter: MozcSegmenter,
    private val trace: MozcConverterTrace? = null,
) {
    fun apply(
        lattice: MozcLattice,
        options: MozcConversionOptions,
    ) {
        if (!options.disablePrefixPenalty) {
            for (node in lattice.beginNodes(0)) {
                if (node.nodeType == MozcNodeType.EOS) continue
                val penalty = segmenter.getPrefixPenalty(node.leftId)
                node.wordCost += penalty
                trace?.prefixPenaltyAppliedCount = (trace?.prefixPenaltyAppliedCount ?: 0) + 1
            }
        }

        for (node in lattice.endNodes(lattice.key.length)) {
            if (node.nodeType == MozcNodeType.BOS) continue
            val penalty = segmenter.getSuffixPenalty(node.rightId)
            node.wordCost += penalty
            trace?.suffixPenaltyAppliedCount = (trace?.suffixPenaltyAppliedCount ?: 0) + 1
        }
    }
}
