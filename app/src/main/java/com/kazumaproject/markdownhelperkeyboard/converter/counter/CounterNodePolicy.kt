package com.kazumaproject.markdownhelperkeyboard.converter.counter

import com.kazumaproject.counter.CounterCandidate

/** Context IDs are from the bundled Mozc id.def; a quantity is one atomic display node. */
internal object CounterNodePolicy {
    const val WORD_COST = 2000
    fun cost(candidateIndex: Int): Int = WORD_COST + candidateIndex * 200
    // FindPath adds 2,000 once to outputs containing digits. Compensate only rule-produced
    // numeric nodes so the explicit ASCII/kanji/fullwidth ordering survives sentence search.
    fun wordCost(candidateIndex: Int, candidate: CounterCandidate): Int =
        cost(candidateIndex) - if (candidate.notation != "kanji") 2000 else 0
    fun leftId(candidate: CounterCandidate): Short = if (candidate.counterId == "time") 1909 else 2043
    fun rightId(candidate: CounterCandidate): Short = when {
        candidate.counterId == "time" -> 1909
        candidate.value.endsWith("つ") -> 2012
        candidate.value.endsWith("周") -> 2013
        candidate.value.endsWith("回") -> 2014
        candidate.value.endsWith("時") -> 2015
        candidate.value.endsWith("月") -> 2016
        candidate.value.endsWith("次") -> 2017
        candidate.value.endsWith("階") -> 2018
        else -> 2011
    }
}
