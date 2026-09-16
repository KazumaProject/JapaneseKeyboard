package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.graph.MozcNodeType
import com.kazumaproject.graph.Node
import com.kazumaproject.graph.CandidateSource
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.*
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.NgramRuleScorer
import com.kazumaproject.quantity.QuantityDictionary.Feature

/** Product of the unpruned lattice and a quantity-rule automaton, using ordinary path costs. */
internal class QuantityGuidedSearch(
    graph: Map<Int, List<Node>>,
    private val policy: NumberPathPolicy,
    private val matrix: ConnectionMatrix.CostTable,
    private val legal: (Node, Node) -> Boolean,
    private val scorer: NgramRuleScorer,
    private val cancellationCheck: () -> Unit = {},
) {
    private data class Target(val end: Int, val text: String)
    private data class Progress(val feature: Int, val target: Target? = null, val offset: Int = 0)
    private data class NodeClass(val word: Int, val left: Int, val right: Int)
    private data class Boundary(val left: Short, val right: Short, val attributes: Int, val type: MozcNodeType, val alphabet: Boolean)
    private data class State(val progress: Progress, val boundary: Boundary, val tail: List<NodeClass>, val digits: Boolean, val source: Int)
    private class Route(val node: Node, val previous: Route?, val cost: Int, val tail: List<Node>, val classes: List<NodeClass>, val digits: Boolean, val source: Int) {
        fun nodes(): List<Node> = generateSequence(this) { it.previous }.map { it.node }.toList().asReversed()
    }
    private data class ContextKey(val tail: List<NodeClass>, val node: NodeClass)
    private data class ContextStep(val score: Int, val tail: List<Node>, val classes: List<NodeClass>)
    private val contextSteps = object : LinkedHashMap<ContextKey, ContextStep>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ContextKey, ContextStep>?) = size > 4096
    }
    private fun contextStep(previous: Route?, node: Node): ContextStep {
        val key = ContextKey(previous?.classes.orEmpty(), NodeClass(scorer.wordClass(node), scorer.leftIdClass(node), scorer.rightIdClass(node)))
        return contextSteps.getOrPut(key) {
            val context = previous?.tail.orEmpty() + node
            val tail = scorer.futureContext(context)
            ContextStep(scorer.scoreEndingAt(context), tail,
                tail.map { NodeClass(scorer.wordClass(it), scorer.leftIdClass(it), scorer.rightIdClass(it)) })
        }
    }
    private data class ObservableNode(val start: Int, val length: Short, val left: Short, val right: Short,
        val attributes: Int, val type: MozcNodeType, val source: CandidateSource, val generated: Boolean,
        val wordClass: Int, val leftClass: Int, val rightClass: Int, val digits: Boolean, val text: String)
    private val nodes: List<Node> = run {
        val literalWords = QuantityRuntime.rules.flatten().filterIsInstance<Feature.Word>().mapTo(HashSet()) { it.text }
        val spans = policy.recognizedSpans()
        // Homophones unobservable by either automaton have exactly the same continuations.
        // Merge them before the product search, including in long unrelated prefixes.
        graph.values.flatten().filter { it.tango != "BOS" && it.tango != "EOS" }.groupBy { node ->
            val quantityPart = spans.any { node.sPos >= it.first && node.sPos + node.len <= it.second }
            ObservableNode(node.sPos, node.len, node.l, node.r, node.mozcAttributes, node.mozcNodeType,
                node.candidateSource, node.isGeneratedNumber, scorer.wordClass(node), scorer.leftIdClass(node),
                scorer.rightIdClass(node), node.tango.any(Char::isDigit), if (quantityPart || node.tango in literalWords) node.tango else "")
        }.values.map { it.minBy { node -> node.adjustedScore } }
    }

    private val starts = nodes.groupBy { it.sPos }
    private val bos = graph.values.flatten().first { it.tango == "BOS" }
    private val eos = graph.values.flatten().first { it.tango == "EOS" }
    private val length = policy.input.length
    private val targets = HashMap<Triple<Int, String, Boolean>, List<Target>>()
    private val numericSpans = policy.recognizedSpans().groupBy { it.first }

    private fun targets(start: Int, unit: String, contextual: Boolean): List<Target> = targets.getOrPut(Triple(start, unit, contextual)) {
        numericSpans[start].orEmpty().filter { (_, end) ->
            policy.permitsPreference(start, end, contextual)
        }.flatMap { (_, end) ->
            policy.parse(policy.input.substring(start, end)).filter {
                it.counter == unit || unit == "@registered" && it.customUnit != null
            }.flatMap { proof -> proof.basicForms.map { Target(end, it) } }
        }.distinct()
    }

    private fun advance(rule: List<Feature>, progress: Progress, node: Node): List<Progress> {
        if (progress.feature == rule.size) return listOf(progress)
        val feature = rule[progress.feature]
        if (feature is Feature.Word) return if (node.tango == feature.text) listOf(Progress(progress.feature + 1)) else emptyList()
        if (node.candidateSource == CandidateSource.USER_DICTIONARY || node.candidateSource == CandidateSource.LEARNED_DICTIONARY) return emptyList()
        if (progress.target == null && !policy.startsQuantity(node)) return emptyList()
        val available = progress.target?.let(::listOf) ?: targets(node.sPos, (feature as Feature.Quantity).unit, rule.first() is Feature.Word)
        return available.mapNotNull { target ->
            val end = node.sPos + node.len
            if (end > target.end || !target.text.startsWith(node.tango, progress.offset)) return@mapNotNull null
            val offset = progress.offset + node.tango.length
            if (end == target.end) {
                if (offset == target.text.length) Progress(progress.feature + 1) else null
            } else if (offset < target.text.length) Progress(progress.feature, target, offset) else null
        }
    }

    fun candidates(
        segments: MutableMap<String, List<CandidateConversionSegment>>?,
        splitPatterns: MutableMap<String, List<Int>>? = null,
        independent: (Short) -> Boolean = { false },
    ): List<Candidate> {
        val results = mutableListOf<Candidate>()
        val words = nodes.mapTo(HashSet()) { it.tango }
        for (rule in QuantityRuntime.rules) {
            if (rule.any { feature -> when (feature) {
                    is Feature.Word -> feature.text !in words
                    is Feature.Quantity -> numericSpans.keys.none { targets(it, feature.unit, rule.first() is Feature.Word).isNotEmpty() }
                } }) continue
            cancellationCheck()
            val first = rule.first()
            val lastStart = when (first) {
                is Feature.Word -> nodes.filter { it.tango == first.text }.maxOf { it.sPos }
                is Feature.Quantity -> numericSpans.keys.filter { targets(it, first.unit, false).isNotEmpty() }.max()
            }
            // A negative phase searches for the start of the rule; the final phase accepts its suffix.
            val states = Array(length + 1) { HashMap<State, Route>() }
            for (at in 0 until length) {
                if (at and 7 == 0) cancellationCheck()
                val incoming = if (at == 0) listOf<Pair<Progress, Route?>>(Progress(-1) to null)
                    else states[at].map { it.key.progress to it.value }
                for ((progress, previous) in incoming) for (node in starts[at].orEmpty()) {
                    val before = previous?.node ?: bos
                    if (!legal(before, node)) continue
                    val nextProgress = if (progress.feature == -1) (if (node.sPos + node.len <= lastStart) listOf(progress) else emptyList()) + advance(rule, Progress(0), node)
                        else advance(rule, progress, node)
                    if (nextProgress.isEmpty()) continue
                    val step = contextStep(previous, node)
                    val cost = (previous?.cost ?: 0) + matrix.cost(before.r.toInt(), node.l.toInt()) +
                        node.adjustedScore + step.score
                    val route = Route(node, previous, cost, step.tail, step.classes, previous?.digits == true || node.tango.any(Char::isDigit), (previous?.source ?: 0) or (1 shl node.candidateSource.ordinal))
                    val finish = node.sPos + node.len
                    for (next in nextProgress) {
                        val state = State(next, Boundary(node.l, node.r, node.mozcAttributes, node.mozcNodeType,
                            node.yomiUsed.lastOrNull()?.let { it in 'a'..'z' || it in 'A'..'Z' } == true), step.classes, route.digits, route.source)
                        if (cost < (states[finish][state]?.cost ?: Int.MAX_VALUE)) states[finish][state] = route
                    }
                }
                // Back-pointers own retained routes. Past state maps no longer participate in search.
                states[at].clear()
            }
            val best = states[length].filter { it.key.progress.feature == rule.size && legal(it.value.node, eos) }
                .values.minByOrNull { it.cost + matrix.cost(it.node.r.toInt(), eos.l.toInt()) + if (it.digits) 2000 else 0 } ?: continue
            val path = best.nodes()
            val strength = policy.matchStrength(path)
            if (strength == 0) continue
            val text = path.joinToString("") { it.tango }
            segments?.set(text, path.map { CandidateConversionSegment(it.sPos, it.sPos + it.len, it.tango) })
            splitPatterns?.set(text, path.drop(1).filter { independent(it.l) }.map { it.sPos })
            val type: Byte = when {
                path.any { it.candidateSource == CandidateSource.LEARNED_DICTIONARY } -> CANDIDATE_TYPE_LEARNED_DICTIONARY
                path.any { it.candidateSource == CandidateSource.USER_DICTIONARY } -> CANDIDATE_TYPE_USER_DICTIONARY
                else -> 1
            }
            results += Candidate(text, type, length.toUByte(),
                best.cost + matrix.cost(best.node.r.toInt(), eos.l.toInt()) + if (best.digits) 2000 else 0,
                yomi = policy.input, leftId = path.first().l, rightId = path.last().r,
                numberSpans = policy.spans(path), quantityPreference = strength)
        }
        return results
    }
}
