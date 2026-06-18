package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import kotlin.math.max

class MozcResegmenter(
    private val segmenter: MozcSegmenter,
    private val connector: MozcConnector,
    private val boundaryDetector: MozcBoundaryDetector,
    private val trace: MozcConverterTrace? = null,
) {
    private val posMatcher: MozcPosMatcherData = segmenter.posMatcher
    private val firstNameId: Short = posMatcher.getRequiredId("FirstName")
    private val lastNameId: Short = posMatcher.getRequiredId("LastName")
    private val numberId: Short = posMatcher.getRequiredId("Number")
    private val lastToFirstNameTransitionCost =
        connector.getTransitionCost(lastNameId, firstNameId)

    fun resegment(
        lattice: MozcLattice,
        options: MozcConversionOptions,
    ) {
        for (pos in 0 until lattice.key.length) {
            if (resegmentArabicNumberAndSuffix(pos, lattice)) continue
            if (resegmentPrefixAndArabicNumber(pos, lattice)) continue
            resegmentPersonalName(pos, lattice)
        }
    }

    private fun resegmentArabicNumberAndSuffix(pos: Int, lattice: MozcLattice): Boolean {
        val additions = mutableListOf<MozcNode>()
        for (compound in lattice.beginNodes(pos).toList()) {
            if (compound.nodeType != MozcNodeType.NORMAL) continue
            if (compound.value.isEmpty() || compound.key.isEmpty()) continue
            if (compound.leftId != numberId || compound.rightId == numberId) continue
            if (!compound.value.first().isDigit() || !compound.key.first().isDigit()) continue

            val numberValue = compound.value.takeWhile { it.isDigit() }
            val suffixValue = compound.value.drop(numberValue.length)
            val numberKey = compound.key.takeWhile { it.isDigit() }
            val suffixKey = compound.key.drop(numberKey.length)
            if (suffixValue.isEmpty() || suffixKey.isEmpty() || numberValue != numberKey) continue
            val wordCost = max(compound.wordCost / 2 - 1, 0)

            val numberNode = MozcNode().apply {
                key = numberKey
                value = numberValue
                leftId = compound.leftId
                rightId = 0
                this.wordCost = wordCost
                beginPos = pos
                endPos = pos + numberKey.length
                nodeType = MozcNodeType.NORMAL
            }
            val suffixNode = MozcNode().apply {
                key = suffixKey
                value = suffixValue
                leftId = 0
                rightId = compound.rightId
                this.wordCost = wordCost
                beginPos = pos + numberKey.length
                endPos = compound.endPos
                nodeType = MozcNodeType.NORMAL
                constrainedPrev = numberNode
            }
            additions += numberNode
            additions += suffixNode
        }
        additions.forEach(lattice::insert)
        trace?.resegmentInsertedNodeCount = (trace?.resegmentInsertedNodeCount ?: 0) + additions.size
        return additions.isNotEmpty()
    }

    private fun resegmentPrefixAndArabicNumber(pos: Int, lattice: MozcLattice): Boolean {
        val additions = mutableListOf<MozcNode>()
        for (compound in lattice.beginNodes(pos).toList()) {
            if (compound.nodeType != MozcNodeType.NORMAL) continue
            if (compound.value.length <= 1 || compound.key.length <= 1) continue
            if (compound.value.first().isDigit() || compound.key.first().isDigit()) continue
            if (!compound.value.last().isDigit() || !compound.key.last().isDigit()) continue

            val prefixValue = compound.value.dropLastWhile { it.isDigit() }
            val numberValue = compound.value.drop(prefixValue.length)
            val prefixKey = compound.key.dropLastWhile { it.isDigit() }
            val numberKey = compound.key.drop(prefixKey.length)
            if (prefixValue.isEmpty() || prefixKey.isEmpty() || numberValue != numberKey) continue
            val wordCost = max(compound.wordCost / 2 - 1, 0)

            val prefixNode = MozcNode().apply {
                key = prefixKey
                value = prefixValue
                leftId = compound.leftId
                rightId = 0
                this.wordCost = wordCost
                beginPos = pos
                endPos = pos + prefixKey.length
                nodeType = MozcNodeType.NORMAL
            }
            val numberNode = MozcNode().apply {
                key = numberKey
                value = numberValue
                leftId = 0
                rightId = compound.rightId
                this.wordCost = wordCost
                beginPos = pos + prefixKey.length
                endPos = compound.endPos
                nodeType = MozcNodeType.NORMAL
                constrainedPrev = prefixNode
            }
            additions += prefixNode
            additions += numberNode
        }
        additions.forEach(lattice::insert)
        trace?.resegmentInsertedNodeCount = (trace?.resegmentInsertedNodeCount ?: 0) + additions.size
        return additions.isNotEmpty()
    }

    private fun resegmentPersonalName(pos: Int, lattice: MozcLattice): Boolean {
        val additions = mutableListOf<MozcNode>()
        for (compound in lattice.beginNodes(pos).toList()) {
            if (compound.leftId != lastNameId || compound.rightId != firstNameId) continue
            val charLength = compound.value.codePointCount(0, compound.value.length)
            if (charLength <= 2 || compound.value.all { isKatakana(it) }) continue

            var bestLast: MozcNode? = null
            var bestFirst: MozcNode? = null
            var bestCost = Int.MAX_VALUE
            for (left in lattice.beginNodes(pos)) {
                if (left === compound) continue
                if (!compound.value.startsWith(left.value) || !compound.key.startsWith(left.key)) continue
                val rightPos = pos + left.key.length
                for (right in lattice.beginNodes(rightPos)) {
                    if (left.value + right.value != compound.value) continue
                    if (left.key + right.key != compound.key) continue
                    if (!boundaryDetector.isBoundary(left, right)) continue
                    val cost = left.wordCost + connector.getTransitionCost(left.rightId, right.leftId)
                    if (cost < bestCost) {
                        bestCost = cost
                        bestLast = left
                        bestFirst = right
                    }
                }
            }

            val last = bestLast ?: continue
            val first = bestFirst ?: continue
            if (charLength >= 4 && last.leftId != lastNameId && first.rightId != firstNameId) continue
            if (charLength == 3 && (last.leftId != lastNameId || first.rightId != firstNameId)) continue

            val wordCost = max((compound.wordCost - lastToFirstNameTransitionCost) / 2 - 1, 0)
            val lastNode = MozcNode().apply {
                key = last.key
                value = last.value
                leftId = compound.leftId
                rightId = lastNameId
                this.wordCost = wordCost
                beginPos = pos
                endPos = pos + last.key.length
                nodeType = MozcNodeType.NORMAL
            }
            val firstNode = MozcNode().apply {
                key = first.key
                value = first.value
                leftId = firstNameId
                rightId = compound.rightId
                this.wordCost = wordCost
                beginPos = pos + last.key.length
                endPos = compound.endPos
                nodeType = MozcNodeType.NORMAL
                constrainedPrev = lastNode
            }
            additions += lastNode
            additions += firstNode
        }
        additions.forEach(lattice::insert)
        trace?.resegmentInsertedNodeCount = (trace?.resegmentInsertedNodeCount ?: 0) + additions.size
        return additions.isNotEmpty()
    }

    private fun isKatakana(char: Char): Boolean =
        char.code in 0x30A0..0x30FF || char.code in 0xFF66..0xFF9D
}
