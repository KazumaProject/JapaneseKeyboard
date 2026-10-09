package com.kazumaproject.graph

import com.kazumaproject.counter.CounterInterpretation
import com.kazumaproject.markdownhelperkeyboard.converter.counter.CounterCompetitor
import com.kazumaproject.markdownhelperkeyboard.converter.counter.CounterLexicalAlternative
import com.kazumaproject.markdownhelperkeyboard.converter.counter.CounterBoundaryPolicy
enum class MozcNodeType {
    NOR,
    BOS,
    EOS,
    CON,
    HIS,
}

enum class CandidateSource {
    SYSTEM,
    UNKNOWN,
    USER_DICTIONARY,
    LEARNED_DICTIONARY,
    COUNTER_RULE,
}

object MozcNodeAttributes {
    const val NONE = 0
    const val STARTS_WITH_PARTICLE = 1
}

data class Node(
    val l: Short,
    val r: Short,
    var score: Int,
    var f: Int,
    var g: Int = 0,
    val tango: String,
    val len: Short,
    val yomiUsed: String,
    var sPos: Int,
    var prev: Node? = null,
    var next: Node? = null,
    var adjustedScore: Int = score,
    val mozcNodeType: MozcNodeType = MozcNodeType.NOR,
    val mozcAttributes: Int = MozcNodeAttributes.NONE,
    val candidateSource: CandidateSource = CandidateSource.SYSTEM,
    val counter: CounterInterpretation? = null,
    val numberValue: Long? = null,
    val counterCompetitor: CounterCompetitor? = null,
    val counterAlternatives: List<CounterLexicalAlternative> = emptyList(),
    val counterNumericSupports: List<CounterLexicalAlternative> = emptyList(),
    val counterBoundary: CounterBoundaryPolicy? = null,
) {
    override fun toString(): String {
        return this.tango
    }
}
