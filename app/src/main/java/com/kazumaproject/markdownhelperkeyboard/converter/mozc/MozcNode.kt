package com.kazumaproject.markdownhelperkeyboard.converter.mozc

class MozcNode {
    var prev: MozcNode? = null
    var next: MozcNode? = null
    var constrainedPrev: MozcNode? = null
    var leftId: Short = 0
    var rightId: Short = 0
    var beginPos: Int = 0
    var endPos: Int = 0
    var wordCost: Int = 0
    var totalCost: Int = Int.MAX_VALUE
    var key: String = ""
    var value: String = ""
    var nodeType: MozcNodeType = MozcNodeType.NORMAL
    var attributes: Int = 0

    val isUnknown: Boolean
        get() = attributes and MozcNodeAttribute.UNKNOWN != 0

    fun clearPathState() {
        prev = null
        next = null
        totalCost = Int.MAX_VALUE
    }

    fun shallowCopyWithoutPath(): MozcNode =
        MozcNode().also { copy ->
            copy.constrainedPrev = constrainedPrev
            copy.leftId = leftId
            copy.rightId = rightId
            copy.beginPos = beginPos
            copy.endPos = endPos
            copy.wordCost = wordCost
            copy.key = key
            copy.value = value
            copy.nodeType = nodeType
            copy.attributes = attributes
        }
}
