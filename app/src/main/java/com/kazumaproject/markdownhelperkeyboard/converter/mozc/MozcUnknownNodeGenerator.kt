package com.kazumaproject.markdownhelperkeyboard.converter.mozc

class MozcUnknownNodeGenerator(
    private val posMatcher: MozcPosMatcherData,
    private val trace: MozcConverterTrace? = null,
) {
    private val unknownId: Short = posMatcher.getRequiredId("Unknown")
    private val numberId: Short = posMatcher.getRequiredId("Number")

    fun addCharacterTypeBasedNodes(
        key: String,
        beginPos: Int,
        builder: MozcNodeListBuilder,
    ) {
        if (beginPos !in key.indices) return
        val firstEnd = beginPos + Character.charCount(key.codePointAt(beginPos))
        val first = key.substring(beginPos, firstEnd)
        val firstCodePoint = key.codePointAt(beginPos)
        val firstType = characterType(firstCodePoint)

        builder.add(
            MozcNode().apply {
                this.key = first
                value = first
                if (firstType == CharacterType.NUMBER) {
                    leftId = numberId
                    rightId = numberId
                    wordCost = DEFAULT_NUMBER_COST
                } else {
                    leftId = unknownId
                    rightId = unknownId
                    wordCost = MAX_COST
                }
                this.beginPos = beginPos
                endPos = firstEnd
                nodeType = MozcNodeType.NORMAL
                attributes = MozcNodeAttribute.UNKNOWN
            },
        )
        trace?.unknownNodeCount = (trace?.unknownNodeCount ?: 0) + 1

        if (firstType != CharacterType.ALPHABET && firstType != CharacterType.KATAKANA) {
            return
        }

        var end = firstEnd
        while (end < key.length) {
            val codePoint = key.codePointAt(end)
            if (characterType(codePoint) != firstType) break
            end += Character.charCount(codePoint)
        }
        if (end <= firstEnd) return

        val grouped = key.substring(beginPos, end)
        builder.add(
            MozcNode().apply {
                this.key = grouped
                value = grouped
                leftId = unknownId
                rightId = unknownId
                wordCost = MAX_COST / 2
                this.beginPos = beginPos
                endPos = end
                nodeType = MozcNodeType.NORMAL
                attributes = MozcNodeAttribute.UNKNOWN
            },
        )
        trace?.unknownNodeCount = (trace?.unknownNodeCount ?: 0) + 1
    }

    private fun characterType(codePoint: Int): CharacterType =
        when {
            Character.getType(codePoint) == Character.DECIMAL_DIGIT_NUMBER.toInt() -> CharacterType.NUMBER
            isAlphabet(codePoint) -> CharacterType.ALPHABET
            isKatakana(codePoint) -> CharacterType.KATAKANA
            else -> CharacterType.OTHER
        }

    private fun isAlphabet(codePoint: Int): Boolean =
        codePoint in 'A'.code..'Z'.code ||
            codePoint in 'a'.code..'z'.code ||
            codePoint in 0xFF21..0xFF3A ||
            codePoint in 0xFF41..0xFF5A

    private fun isKatakana(codePoint: Int): Boolean =
        codePoint in 0x30A0..0x30FF || codePoint in 0xFF66..0xFF9D

    private enum class CharacterType {
        NUMBER,
        ALPHABET,
        KATAKANA,
        OTHER,
    }

    companion object {
        const val MAX_COST = 32767
        const val MIN_COST = -32767
        const val DEFAULT_NUMBER_COST = 3000
    }
}
