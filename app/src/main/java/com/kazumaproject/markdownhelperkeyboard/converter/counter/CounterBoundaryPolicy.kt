package com.kazumaproject.markdownhelperkeyboard.converter.counter

import com.kazumaproject.counter.CounterConverter
import com.kazumaproject.graph.Node

/** Shared by forward DP, backward search and bunsetsu refinement. No candidate text is reparsed. */
class CounterBoundaryPolicy(private val converter: CounterConverter) {
    // A bounded syntax cache is independent of sentence position, so append can reuse old nodes.
    private val syntax = object : LinkedHashMap<String, Boolean>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > 512
    }

    private val numericTails = object : LinkedHashMap<Pair<String, String>, Boolean>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, String>, Boolean>?): Boolean = size > 512
    }

    private fun numericTailSyntax(reading: String, surface: String): Boolean = synchronized(numericTails) {
        numericTails.getOrPut(reading to surface) {
            converter.analyze(reading, includeOutOfRange = true).any {
                it.suffix == surface || it.forms.any { form -> form.value.endsWith(surface) }
            }
        }
    }

    private fun quantitySyntax(reading: String): Boolean = synchronized(syntax) {
        syntax.getOrPut(reading) { converter.hasQuantitySyntax(reading) }
    }

    fun canFollow(previous: Node, current: Node): Boolean {
        if (previous.tango == "BOS" || current.tango == "EOS") return true
        if (previous.sPos + previous.len != current.sPos) return true
        val quantity = current.counter
        if (previous.numberValue != null && quantity == null && current.numberValue == null &&
            converter.mayEndQuantitySurface(current.tango)) {
            if (numericTailSyntax(previous.yomiUsed + current.yomiUsed, current.tango)) return false
        }
        if (previous.numberValue != null && quantity != null) {
            // Includes out-of-range 24時; an absent full candidate must not license 2 + 14時.
            if (quantitySyntax(previous.yomiUsed + current.yomiUsed)) return false
        }
        val before = previous.counter ?: return true
        if (quantity == null && current.numberValue == null &&
            quantitySyntax(previous.yomiUsed + current.yomiUsed)) return false
        if (before.time != null && (quantity?.counterId == "minute" || quantity?.counterId == "second")) {
            if (quantity.number > 59) return false
            if (quantity.counterId == "minute" && before.time.hasMinute) return false
            if (quantity.counterId == "second" && before.time.second != null) return false
            if (quantitySyntax(previous.yomiUsed + current.yomiUsed)) return false
        }
        return true
    }
}
